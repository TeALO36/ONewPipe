// Local library kept in the browser (localStorage), so history, watch later,
// playlists and subscriptions work without an account, like the apps.
// With an account (account.ts) the same data is synchronized with the server.
import { useSyncExternalStore } from 'react';
import type { Item } from './api';

export interface SavedVideo {
  url: string;
  title: string;
  uploaderName: string;
  uploaderUrl?: string;
  thumbnailUrl: string;
  durationText: string;
  durationSeconds?: number;
}

export interface HistoryEntry extends SavedVideo {
  positionSeconds: number;
  watchedAt: number;
}

export interface Playlist {
  id: string;
  name: string;
  items: SavedVideo[];
}

export interface Subscription {
  url: string;
  name: string;
  thumbnailUrl: string;
}

export type ThemeMode = 'system' | 'light' | 'dark';

export interface Settings {
  theme: ThemeMode;
  autoplayNext: boolean;
  resumePlayback: boolean;
  keepHistory: boolean;
  preferredQuality: 'auto' | '1080' | '720' | '480' | '360';
}

export interface LibraryState {
  history: HistoryEntry[];
  watchLater: SavedVideo[];
  playlists: Playlist[];
  subscriptions: Subscription[];
  searchHistory: string[];
  settings: Settings;
  /** Last change of the synchronized parts (history, watch later, playlists, subscriptions). */
  updatedAt: number;
}

/** The parts of the library shared with the server. */
export type SyncedLibrary = Pick<LibraryState, 'history' | 'watchLater' | 'playlists' | 'subscriptions'>;

const STORAGE_KEY = 'onewpipe-library-v1';
export const MAX_HISTORY = 500;
const MAX_SEARCHES = 30;

const defaultState: LibraryState = {
  history: [],
  watchLater: [],
  playlists: [],
  subscriptions: [],
  searchHistory: [],
  settings: {
    theme: 'system',
    autoplayNext: true,
    resumePlayback: true,
    keepHistory: true,
    preferredQuality: 'auto'
  },
  updatedAt: 0
};

function load(): LibraryState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaultState;
    const parsed = JSON.parse(raw) as Partial<LibraryState>;
    return {
      ...defaultState,
      ...parsed,
      settings: { ...defaultState.settings, ...(parsed.settings ?? {}) }
    };
  } catch {
    return defaultState;
  }
}

let state: LibraryState = load();
const listeners = new Set<() => void>();

/**
 * Applies a change. [synced] marks changes to the synchronized parts, which
 * the account sync pushes to the server; settings, searches and playback
 * positions (sent through their own endpoint) do not.
 */
function update(change: (current: LibraryState) => LibraryState, synced = true) {
  state = change(state);
  if (synced) state = { ...state, updatedAt: Date.now() };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Storage full or unavailable: keep the in-memory state.
  }
  listeners.forEach((listener) => listener());
}

// Another window (a second Electron window or browser tab) changed the library.
window.addEventListener('storage', (event) => {
  if (event.key !== STORAGE_KEY) return;
  state = load();
  listeners.forEach((listener) => listener());
});

export function subscribeLibrary(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useLibrary(): LibraryState {
  return useSyncExternalStore(subscribeLibrary, () => state);
}

export const getLibrary = () => state;

export function toSaved(item: Item | SavedVideo): SavedVideo {
  return {
    url: item.url,
    title: item.title,
    uploaderName: item.uploaderName,
    uploaderUrl: item.uploaderUrl,
    thumbnailUrl: item.thumbnailUrl,
    durationText: item.durationText,
    durationSeconds: item.durationSeconds
  };
}

const newId = () => Math.random().toString(36).slice(2, 10);

export const library = {
  recordWatch(video: SavedVideo, positionSeconds = 0) {
    if (!state.settings.keepHistory) return;
    update((s) => ({
      ...s,
      history: [
        { ...video, positionSeconds, watchedAt: Date.now() },
        ...s.history.filter((entry) => entry.url !== video.url)
      ].slice(0, MAX_HISTORY)
    }));
  },
  updatePosition(url: string, positionSeconds: number) {
    if (!state.settings.keepHistory) return;
    const entry = state.history.find((h) => h.url === url);
    if (!entry || Math.abs(entry.positionSeconds - positionSeconds) < 5) return;
    update(
      (s) => ({
        ...s,
        history: s.history.map((h) => (h.url === url ? { ...h, positionSeconds, watchedAt: Date.now() } : h))
      }),
      false
    );
  },
  resumePosition(url: string, durationSeconds: number): number {
    if (!state.settings.resumePlayback) return 0;
    const position = state.history.find((h) => h.url === url)?.positionSeconds ?? 0;
    const duration = durationSeconds || Number.MAX_SAFE_INTEGER;
    return position > 5 && position < duration - 10 ? position : 0;
  },
  removeFromHistory(url: string) {
    update((s) => ({ ...s, history: s.history.filter((h) => h.url !== url) }));
  },
  clearHistory() {
    update((s) => ({ ...s, history: [] }));
  },
  isInWatchLater: (url: string) => state.watchLater.some((v) => v.url === url),
  toggleWatchLater(video: SavedVideo) {
    update((s) => ({
      ...s,
      watchLater: s.watchLater.some((v) => v.url === video.url)
        ? s.watchLater.filter((v) => v.url !== video.url)
        : [video, ...s.watchLater]
    }));
  },
  clearWatchLater() {
    update((s) => ({ ...s, watchLater: [] }));
  },
  createPlaylist(name: string, first?: SavedVideo): string {
    const id = newId();
    update((s) => ({ ...s, playlists: [...s.playlists, { id, name, items: first ? [first] : [] }] }));
    return id;
  },
  renamePlaylist(id: string, name: string) {
    update((s) => ({ ...s, playlists: s.playlists.map((p) => (p.id === id ? { ...p, name } : p)) }));
  },
  deletePlaylist(id: string) {
    update((s) => ({ ...s, playlists: s.playlists.filter((p) => p.id !== id) }));
  },
  addToPlaylist(id: string, video: SavedVideo) {
    update((s) => ({
      ...s,
      playlists: s.playlists.map((p) =>
        p.id === id && !p.items.some((v) => v.url === video.url) ? { ...p, items: [...p.items, video] } : p
      )
    }));
  },
  removeFromPlaylist(id: string, url: string) {
    update((s) => ({
      ...s,
      playlists: s.playlists.map((p) => (p.id === id ? { ...p, items: p.items.filter((v) => v.url !== url) } : p))
    }));
  },
  isSubscribed: (url: string) => state.subscriptions.some((c) => c.url === url),
  toggleSubscription(channel: Subscription) {
    update((s) => ({
      ...s,
      subscriptions: s.subscriptions.some((c) => c.url === channel.url)
        ? s.subscriptions.filter((c) => c.url !== channel.url)
        : [...s.subscriptions, channel]
    }));
  },
  recordSearch(query: string) {
    const trimmed = query.trim();
    if (!trimmed || !state.settings.keepHistory) return;
    update(
      (s) => ({
        ...s,
        searchHistory: [trimmed, ...s.searchHistory.filter((q) => q.toLowerCase() !== trimmed.toLowerCase())].slice(0, MAX_SEARCHES)
      }),
      false
    );
  },
  removeSearch(query: string) {
    update((s) => ({ ...s, searchHistory: s.searchHistory.filter((q) => q !== query) }), false);
  },
  clearSearchHistory() {
    update((s) => ({ ...s, searchHistory: [] }), false);
  },
  setSettings(change: Partial<Settings>) {
    update((s) => ({ ...s, settings: { ...s.settings, ...change } }), false);
  },
  /** Replaces the synchronized parts with the server's copy, stamped with its time. */
  applySynced(parts: SyncedLibrary, updatedAt: number) {
    update((s) => ({ ...s, ...parts, updatedAt }), false);
  },
  /** Playback positions from other devices, applied when they are newer. */
  applyPositions(items: { url: string; positionSeconds: number; updatedAt: number }[]) {
    const newer = new Map(items.map((item) => [item.url, item]));
    if (!state.history.some((h) => (newer.get(h.url)?.updatedAt ?? 0) > h.watchedAt)) return;
    update(
      (s) => ({
        ...s,
        history: s.history.map((h) => {
          const item = newer.get(h.url);
          return item && item.updatedAt > h.watchedAt ? { ...h, positionSeconds: item.positionSeconds, watchedAt: item.updatedAt } : h;
        })
      }),
      false
    );
  },
  exportBackup: () => JSON.stringify(state, null, 2),
  importBackup(json: string) {
    const parsed = JSON.parse(json) as Partial<LibraryState>;
    if (typeof parsed !== 'object' || parsed === null) throw new Error('Not an ONewPipe backup');
    update(() => ({ ...defaultState, ...parsed, settings: { ...defaultState.settings, ...(parsed.settings ?? {}) } }));
  }
};
