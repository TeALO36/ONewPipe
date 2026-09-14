// Notification centre: long-running work (downloads, exports) reports here
// instead of blocking the page. The bell at the top right lists it.
import { useSyncExternalStore } from 'react';

export type TaskStatus = 'running' | 'done' | 'failed' | 'cancelled';

export interface Task {
  id: string;
  title: string;
  detail?: string;
  /** 0..1, or undefined while the size is unknown. */
  progress?: number;
  status: TaskStatus;
  createdAt: number;
  cancel?: () => void;
}

let tasks: Task[] = [];
let unseen = 0;
const listeners = new Set<() => void>();
let snapshot = { tasks, unseen };

function emit() {
  snapshot = { tasks, unseen };
  listeners.forEach((listener) => listener());
}

export function useNotifications() {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => snapshot
  );
}

export const notifications = {
  start(title: string, detail?: string, cancel?: () => void): string {
    const id = Math.random().toString(36).slice(2, 10);
    const task: Task = { id, title, detail, status: 'running', createdAt: Date.now(), cancel };
    tasks = [task, ...tasks].slice(0, 50);
    unseen++;
    emit();
    return id;
  },
  progress(id: string, progress: number | undefined, detail?: string) {
    tasks = tasks.map((t) => (t.id === id ? { ...t, progress, detail: detail ?? t.detail } : t));
    emit();
  },
  finish(id: string, status: Exclude<TaskStatus, 'running'>, detail?: string) {
    tasks = tasks.map((t) => (t.id === id ? { ...t, status, detail: detail ?? t.detail, cancel: undefined } : t));
    if (status !== 'cancelled') unseen++;
    emit();
  },
  dismiss(id: string) {
    tasks = tasks.filter((t) => t.id !== id);
    emit();
  },
  clearFinished() {
    tasks = tasks.filter((t) => t.status === 'running');
    emit();
  },
  markSeen() {
    if (unseen === 0) return;
    unseen = 0;
    emit();
  }
};
