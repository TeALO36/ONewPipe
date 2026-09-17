import { api, type Item } from '../api';
import { Avatar } from '../components/MediaCard';
import { ErrorState, MediaGrid, SkeletonGrid } from '../components/MediaGrid';
import { useAsync } from '../hooks/useAsync';
import { SubscriptionsIcon } from '../icons';
import { useLibrary } from '../library';
import { routeHref } from '../router';

const MAX_CHANNELS = 20;
const VIDEOS_PER_CHANNEL = 6;

/** Newest videos of every followed channel, merged into one feed. */
async function loadFeed(urls: string[], signal: AbortSignal): Promise<{ items: Item[]; failed: number }> {
  const results = await Promise.allSettled(urls.slice(0, MAX_CHANNELS).map((url) => api.channel(url, signal)));
  if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
  const items: Item[] = [];
  let failed = 0;
  results.forEach((result) => {
    if (result.status === 'fulfilled') {
      items.push(
        ...result.value.videos.items
          .filter((i) => i.kind === 'video')
          .slice(0, VIDEOS_PER_CHANNEL)
          .map((i) => ({
            ...i,
            uploaderName: i.uploaderName || result.value.name,
            uploaderUrl: i.uploaderUrl || result.value.url,
            uploaderAvatarUrl: i.uploaderAvatarUrl || result.value.avatarUrl
          }))
      );
    } else {
      failed++;
    }
  });
  return { items, failed };
}

export function SubscriptionsPage() {
  const { subscriptions } = useLibrary();
  const urls = subscriptions.map((s) => s.url);
  const feed = useAsync((signal) => (urls.length ? loadFeed(urls, signal) : Promise.resolve({ items: [], failed: 0 })), [urls.join('|')]);

  if (subscriptions.length === 0) {
    return (
      <div className="page">
        <h1 className="page-title">Subscriptions</h1>
        <div className="empty-state">
          <SubscriptionsIcon size={48} />
          <h3>No subscriptions yet</h3>
          <div>Subscribe to channels from a video or a channel page, without any account.</div>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <h1 className="page-title">Subscriptions</h1>
      <div className="row-scroller" style={{ gridAutoColumns: 'max-content', marginBottom: 24 }}>
        {subscriptions.map((channel) => (
          <a key={channel.url} href={routeHref({ name: 'channel', url: channel.url })} className="sidebar-item" style={{ width: 96, textAlign: 'center' }}>
            <Avatar src={channel.thumbnailUrl} name={channel.name} size={64} />
            <span style={{ width: 90, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{channel.name}</span>
          </a>
        ))}
      </div>
      <h2 className="page-title" style={{ fontSize: 20 }}>
        Latest videos
      </h2>
      {feed.status === 'loading' && <SkeletonGrid />}
      {feed.status === 'error' && <ErrorState message={feed.error} onRetry={feed.retry} />}
      {feed.status === 'ready' && (
        <>
          {feed.data.failed > 0 && (
            <p className="muted" style={{ marginTop: 0 }}>
              {feed.data.failed} channel{feed.data.failed > 1 ? 's' : ''} could not be loaded.
              <button type="button" className="button" style={{ marginLeft: 12, height: 30 }} onClick={feed.retry}>
                Try again
              </button>
            </p>
          )}
          {feed.data.items.length ? <MediaGrid items={feed.data.items} /> : <div className="empty-state">No videos found for your subscriptions.</div>}
        </>
      )}
    </div>
  );
}
