// Play queue and player preferences shared by the cards and the video page.
import { useSyncExternalStore } from 'react';
import type { SavedVideo } from './library';
import { navigate } from './router';

export type RepeatMode = 'off' | 'all' | 'one';

interface PlayerState {
  queue: SavedVideo[];
  repeat: RepeatMode;
  speed: number;
}

const SPEED_KEY = 'onewpipe-speed';

let state: PlayerState = {
  queue: [],
  repeat: 'off',
  speed: Number(localStorage.getItem(SPEED_KEY)) || 1
};
const listeners = new Set<() => void>();

function set(change: Partial<PlayerState>) {
  state = { ...state, ...change };
  listeners.forEach((listener) => listener());
}

export function usePlayer(): PlayerState {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => state
  );
}

export const player = {
  enqueue(video: SavedVideo) {
    if (state.queue.some((v) => v.url === video.url)) return;
    set({ queue: [...state.queue, video] });
  },
  playNext(video: SavedVideo) {
    set({ queue: [video, ...state.queue.filter((v) => v.url !== video.url)] });
  },
  playAll(videos: SavedVideo[]) {
    const [first, ...rest] = videos;
    if (!first) return;
    set({ queue: rest });
    navigate({ name: 'watch', url: first.url, time: 0 });
  },
  remove(url: string) {
    set({ queue: state.queue.filter((v) => v.url !== url) });
  },
  clear() {
    set({ queue: [] });
  },
  /** Opens the next queued video; returns false when the queue is empty. */
  next(current?: SavedVideo): boolean {
    const [next, ...rest] = state.queue;
    if (!next) return false;
    // With "repeat all" the finished video goes back to the end of the queue.
    set({ queue: state.repeat === 'all' && current ? [...rest, current] : rest });
    navigate({ name: 'watch', url: next.url, time: 0 });
    return true;
  },
  cycleRepeat() {
    set({ repeat: state.repeat === 'off' ? 'all' : state.repeat === 'all' ? 'one' : 'off' });
  },
  setSpeed(speed: number) {
    localStorage.setItem(SPEED_KEY, String(speed));
    set({ speed });
  }
};
