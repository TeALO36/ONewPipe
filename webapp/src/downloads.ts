// Downloads run on the server (it combines video and audio). The page starts a
// job, follows it in the notification centre and saves the file when it is
// ready. Everything here is asynchronous: the page never waits on a download.
import { api, ApiError, formatBytes, type DownloadJob, type DownloadOption } from './api';
import { notifications } from './notifications';

/** Job id → notification task id, for the jobs this page follows. */
const followed = new Map<string, string>();

function saveFile(href: string) {
  const link = document.createElement('a');
  link.href = href;
  link.download = '';
  document.body.append(link);
  link.click();
  link.remove();
}

function cancel(jobId: string) {
  const taskId = followed.get(jobId);
  followed.delete(jobId);
  if (taskId) notifications.finish(taskId, 'cancelled', 'Cancelled');
  api.cancelDownload(jobId).catch(() => {
    // Already finished or expired on the server: nothing left to stop.
  });
}

function follow(job: DownloadJob, saveWhenDone: boolean) {
  if (followed.has(job.id)) return;
  const taskId = notifications.start(job.fileName, 'Starting…', () => cancel(job.id));
  followed.set(job.id, taskId);
  let failures = 0;

  /** Returns true once the job is finished. */
  const show = (current: DownloadJob): boolean => {
    switch (current.status) {
      case 'queued':
        notifications.progress(taskId, undefined, 'Waiting for another download to finish');
        return false;
      case 'downloading':
        notifications.progress(taskId, current.progress, `Downloading · ${Math.round(current.progress * 100)}%`);
        return false;
      case 'muxing':
        notifications.progress(taskId, undefined, 'Combining video and audio');
        return false;
      case 'done': {
        const href = api.downloadFileUrl(current.id);
        notifications.finish(taskId, 'done', `${formatBytes(current.sizeBytes)} · saved to your downloads`, { label: 'Save again', href });
        if (saveWhenDone) saveFile(href);
        return true;
      }
      case 'failed':
        notifications.finish(taskId, 'failed', current.error || 'Download failed');
        return true;
      case 'cancelled':
        notifications.finish(taskId, 'cancelled', 'Cancelled');
        return true;
    }
  };

  const poll = async () => {
    if (followed.get(job.id) !== taskId) return; // Cancelled from the page.
    try {
      const current = await api.download(job.id);
      failures = 0;
      if (show(current)) {
        followed.delete(job.id);
        return;
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        notifications.finish(taskId, 'failed', 'This download is no longer available');
        followed.delete(job.id);
        return;
      }
      if (++failures > 60) {
        notifications.finish(taskId, 'failed', 'Lost contact with the server');
        followed.delete(job.id);
        return;
      }
    }
    window.setTimeout(poll, 1000);
  };
  show(job);
  window.setTimeout(poll, 500);
}

export async function startDownload(url: string, option: DownloadOption): Promise<void> {
  const job = await api.startDownload(url, option.id);
  follow(job, true);
}

/** After a reload, keep following the downloads that are still running. */
export function resumeDownloads() {
  api
    .downloads()
    .then((jobs) => jobs.filter((job) => job.status === 'queued' || job.status === 'downloading' || job.status === 'muxing').forEach((job) => follow(job, true)))
    .catch(() => {
      // Not allowed to download from this server (not signed in): nothing to resume.
    });
}
