// ONewPipe account: sign in to this server or to another ONewPipe server, then
// keep subscriptions, playlists, watch later, history and playback positions
// in sync with the apps through /api/library and /api/watchstate.
import { useSyncExternalStore } from 'react';
import {
  getLibrary,
  library,
  MAX_HISTORY,
  subscribeLibrary,
  type HistoryEntry,
  type LibraryState,
  type Playlist,
  type SavedVideo,
  type SyncedLibrary
} from './library';

export interface AccountState {
  /** Server origin, or '' for the server that serves this page. */
  server: string;
  username: string;
  token: string;
  syncing: boolean;
  lastSyncedAt: number;
  error: string | null;
}

// ---- Server DTOs (server/src/main/kotlin/.../Dtos.kt) --------------------------

interface PlaylistItemDto {
  url: string;
  title: string;
  uploaderName: string;
  thumbnailUrl: string;
  durationText: string;
}

interface HistoryEntryDto extends PlaylistItemDto {
  positionMs: number;
  durationMs: number;
  watchedAt: number;
}

interface LibraryDto {
  subscriptions: { url: string; name: string; thumbnailUrl: string }[];
  playlists: { id: string; name: string; items: PlaylistItemDto[] }[];
  watchLater: PlaylistItemDto[];
  history: HistoryEntryDto[];
  updatedAt: number;
}

interface WatchStateItem {
  url: string;
  title: string;
  positionMs: number;
  durationMs: number;
  updatedAt: number;
}

// ---- Store ----------------------------------------------------------------------

const STORAGE_KEY = 'onewpipe-account-v1';

function loadAccount(): AccountState {
  const empty: AccountState = { server: '', username: '', token: '', syncing: false, lastSyncedAt: 0, error: null };
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Partial<AccountState> | null;
    return saved ? { ...empty, server: saved.server ?? '', username: saved.username ?? '', token: saved.token ?? '' } : empty;
  } catch {
    return empty;
  }
}

let state = loadAccount();
const listeners = new Set<() => void>();

function set(change: Partial<AccountState>) {
  state = { ...state, ...change };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ server: state.server, username: state.username, token: state.token }));
  } catch {
    // Storage unavailable: the account lasts for this session only.
  }
  listeners.forEach((listener) => listener());
}

export function useAccount(): AccountState {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => state
  );
}

/** The token, when the account belongs to the server that serves this page. */
export function localAccountToken(): string {
  return state.server === '' ? state.token : '';
}

/**
 * Accepts what the apps accept: a bare IP or host gets http:// and the default
 * port 8080; a full URL is kept as typed. Empty means this page's server.
 */
export function normalizeServer(input: string): string {
  const trimmed = input.trim().replace(/\/+$/, '');
  if (!trimmed) return '';
  const hasScheme = /^https?:\/\//i.test(trimmed);
  const url = new URL(hasScheme ? trimmed : `http://${trimmed}`);
  if (!hasScheme && !url.port) url.port = '8080';
  return url.origin === window.location.origin ? '' : url.origin;
}

class SessionExpired extends Error {}

async function call<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${state.server}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${state.token}`, ...(init.headers ?? {}) }
    });
  } catch {
    throw new Error(`Could not reach ${state.server || 'the server'}`);
  }
  if (response.status === 401) throw new SessionExpired('Your session has expired. Sign in again.');
  if (!response.ok) throw new Error(`The server answered ${response.status}`);
  return response.json() as Promise<T>;
}

// ---- Mapping --------------------------------------------------------------------

const toItemDto = (video: SavedVideo): PlaylistItemDto => ({
  url: video.url,
  title: video.title,
  uploaderName: video.uploaderName,
  thumbnailUrl: video.thumbnailUrl,
  durationText: video.durationText
});

function toDto(local: LibraryState): LibraryDto {
  return {
    subscriptions: local.subscriptions,
    playlists: local.playlists.map((p) => ({ id: p.id, name: p.name, items: p.items.map(toItemDto) })),
    watchLater: local.watchLater.map(toItemDto),
    history: local.history.map((h) => ({
      ...toItemDto(h),
      positionMs: Math.round(h.positionSeconds * 1000),
      durationMs: Math.round((h.durationSeconds ?? 0) * 1000),
      watchedAt: h.watchedAt
    })),
    updatedAt: local.updatedAt
  };
}

/**
 * The server leaves out empty lists and zero values (kotlinx.serialization
 * skips defaults), so a new account's library can be just `{}`.
 */
function complete(dto: Partial<LibraryDto>): LibraryDto {
  return {
    subscriptions: dto.subscriptions ?? [],
    playlists: (dto.playlists ?? []).map((p) => ({ ...p, items: p.items ?? [] })),
    watchLater: dto.watchLater ?? [],
    history: dto.history ?? [],
    updatedAt: dto.updatedAt ?? 0
  };
}

/** Server copy → local shape, keeping details only this device knows (channel link, duration). */
function fromDto(partial: Partial<LibraryDto>, local: LibraryState): SyncedLibrary {
  const dto = complete(partial);
  const known = new Map<string, SavedVideo>();
  [...local.history, ...local.watchLater, ...local.playlists.flatMap((p) => p.items)].forEach((v) => known.set(v.url, v));
  const video = (item: PlaylistItemDto): SavedVideo => ({
    url: item.url,
    title: item.title ?? '',
    uploaderName: item.uploaderName ?? '',
    thumbnailUrl: item.thumbnailUrl ?? '',
    durationText: item.durationText ?? '',
    uploaderUrl: known.get(item.url)?.uploaderUrl,
    durationSeconds: known.get(item.url)?.durationSeconds
  });
  return {
    subscriptions: dto.subscriptions.map((s) => ({ url: s.url, name: s.name, thumbnailUrl: s.thumbnailUrl })),
    playlists: dto.playlists.map((p) => ({ id: p.id, name: p.name, items: p.items.map(video) })),
    watchLater: dto.watchLater.map(video),
    history: dto.history.map((h) => ({
      ...video(h),
      durationSeconds: (h.durationMs ?? 0) > 0 ? h.durationMs / 1000 : known.get(h.url)?.durationSeconds,
      positionSeconds: (h.positionMs ?? 0) / 1000,
      watchedAt: h.watchedAt ?? 0
    }))
  };
}

function unionBy<T>(first: T[], second: T[], key: (item: T) => string): T[] {
  const seen = new Set(first.map(key));
  return [...first, ...second.filter((item) => !seen.has(key(item)))];
}

/** First sign-in on a device: keep what the device and the server both have. */
function merge(local: LibraryState, remote: SyncedLibrary): SyncedLibrary {
  const playlists: Playlist[] = local.playlists.map((p) => {
    const other = remote.playlists.find((r) => r.id === p.id);
    return other ? { ...p, items: unionBy(p.items, other.items, (v) => v.url) } : p;
  });
  playlists.push(...remote.playlists.filter((r) => !local.playlists.some((p) => p.id === r.id)));

  const history = new Map<string, HistoryEntry>();
  [...remote.history, ...local.history].forEach((entry) => {
    const current = history.get(entry.url);
    if (!current || entry.watchedAt > current.watchedAt) history.set(entry.url, entry);
  });

  return {
    subscriptions: unionBy(local.subscriptions, remote.subscriptions, (s) => s.url),
    watchLater: unionBy(local.watchLater, remote.watchLater, (v) => v.url),
    playlists,
    history: [...history.values()].sort((a, b) => b.watchedAt - a.watchedAt).slice(0, MAX_HISTORY)
  };
}

// ---- Sync -----------------------------------------------------------------------

let lastPushed = 0;
let pushTimer: number | undefined;

export async function syncNow({ merge: mergeFirst = false } = {}): Promise<void> {
  if (!state.token || state.syncing) return;
  set({ syncing: true, error: null });
  try {
    const remote = complete(await call<Partial<LibraryDto>>('/api/library'));
    const local = getLibrary();
    let push = false;
    if (mergeFirst) {
      library.applySynced(merge(local, fromDto(remote, local)), Date.now());
      push = true;
    } else if (remote.updatedAt > local.updatedAt) {
      library.applySynced(fromDto(remote, local), remote.updatedAt);
    } else if (local.updatedAt > remote.updatedAt) {
      push = true;
    }
    if (push) {
      // The server keeps the newest copy and returns what it stored.
      const stored = complete(await call<Partial<LibraryDto>>('/api/library', { method: 'POST', body: JSON.stringify(toDto(getLibrary())) }));
      if (stored.updatedAt > getLibrary().updatedAt) library.applySynced(fromDto(stored, getLibrary()), stored.updatedAt);
    }
    lastPushed = getLibrary().updatedAt;

    const positions = await call<WatchStateItem[]>('/api/watchstate');
    library.applyPositions(positions.map((p) => ({ url: p.url, positionSeconds: (p.positionMs ?? 0) / 1000, updatedAt: p.updatedAt ?? 0 })));
    set({ syncing: false, lastSyncedAt: Date.now() });
  } catch (error) {
    if (error instanceof SessionExpired) {
      set({ syncing: false, token: '', error: error.message });
    } else {
      set({ syncing: false, error: error instanceof Error ? error.message : 'Synchronization failed' });
    }
  }
}

export async function signIn(server: string, username: string, password: string, create: boolean): Promise<void> {
  const origin = normalizeServer(server);
  let response: Response;
  try {
    response = await fetch(`${origin}/api/${create ? 'register' : 'login'}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.trim(), password })
    });
  } catch {
    throw new Error(`Could not reach ${origin || 'the server'}. Check the address and that the server is running.`);
  }
  const body = (await response.json().catch(() => ({}))) as { token?: string; username?: string; error?: string };
  if (!response.ok || !body.token) throw new Error(body.error || `The server answered ${response.status}`);
  set({ server: origin, username: body.username ?? username.trim(), token: body.token, error: null, lastSyncedAt: 0 });
  await syncNow({ merge: true });
}

export function signOut() {
  window.clearTimeout(pushTimer);
  set({ token: '', username: '', error: null, lastSyncedAt: 0 });
}

const lastReported = new Map<string, number>();

/** Sends the playback position every 15 seconds, like the apps. */
export function reportPosition(url: string, title: string, positionSeconds: number, durationSeconds: number, force = false) {
  if (!state.token || positionSeconds <= 0) return;
  const now = Date.now();
  if (!force && now - (lastReported.get(url) ?? 0) < 15_000) return;
  lastReported.set(url, now);
  const item: WatchStateItem = {
    url,
    title,
    positionMs: Math.round(positionSeconds * 1000),
    durationMs: Math.round(durationSeconds * 1000),
    updatedAt: now
  };
  call('/api/watchstate', { method: 'POST', body: JSON.stringify({ items: [item] }), keepalive: true }).catch(() => {
    // Positions are best effort; the next report or the next sync catches up.
  });
}

let started = false;

/** Starts the background sync: at start-up, after local changes, on focus and every five minutes. */
export function startAccountSync() {
  if (started) return;
  started = true;
  subscribeLibrary(() => {
    if (!state.token || getLibrary().updatedAt <= lastPushed) return;
    window.clearTimeout(pushTimer);
    pushTimer = window.setTimeout(() => void syncNow(), 4_000);
  });
  const pullIfStale = () => {
    if (state.token && Date.now() - state.lastSyncedAt > 60_000) void syncNow();
  };
  window.addEventListener('focus', pullIfStale);
  window.setInterval(pullIfStale, 5 * 60_000);
  if (state.token) void syncNow();
}
