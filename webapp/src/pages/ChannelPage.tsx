import { useState } from 'react';
import { api, formatCount } from '../api';
import { Description } from '../components/Description';
import { Avatar } from '../components/MediaCard';
import { ErrorState, PagedGrid, SkeletonGrid } from '../components/MediaGrid';
import { toast } from '../components/Toast';
import { useAsync } from '../hooks/useAsync';
import { VerifiedIcon } from '../icons';
import { library, useLibrary } from '../library';
import { player } from '../player';
import './ChannelPage.css';

export function ChannelPage({ url }: { url: string }) {
  const result = useAsync((signal) => api.channel(url, signal), [url]);
  const { subscriptions } = useLibrary();
  const [aboutOpen, setAboutOpen] = useState(false);

  if (result.status === 'error') {
    return (
      <div className="page">
        <ErrorState message={`This channel could not be opened: ${result.error}`} onRetry={result.retry} />
      </div>
    );
  }

  if (result.status === 'loading') {
    return (
      <div className="page">
        <div className="channel-banner skeleton" />
        <div className="channel-header" aria-hidden="true">
          <div className="skeleton" style={{ width: 88, height: 88, borderRadius: '50%' }} />
          <div style={{ flex: 1 }}>
            <div className="skeleton skeleton-line" style={{ width: 240, height: 24 }} />
            <div className="skeleton skeleton-line" style={{ width: 140 }} />
          </div>
        </div>
        <SkeletonGrid />
      </div>
    );
  }

  const channel = result.data;
  const subscribed = subscriptions.some((s) => s.url === channel.url);
  const videos = channel.videos.items.filter((i) => i.kind === 'video');

  return (
    <div className="page">
      {channel.bannerUrl && (
        <div className="channel-banner">
          <img src={channel.bannerUrl} alt="" />
        </div>
      )}
      <header className="channel-header">
        <Avatar src={channel.avatarUrl} name={channel.name} size={88} />
        <div className="channel-info">
          <h1>
            {channel.name}
            {channel.verified && <VerifiedIcon size={20} aria-label="Verified" />}
          </h1>
          <div className="muted">
            {channel.subscriberCount >= 0 && `${formatCount(channel.subscriberCount)} subscribers`}
          </div>
          {channel.description && (
            <button type="button" className="link-button channel-about" onClick={() => setAboutOpen((v) => !v)}>
              {aboutOpen ? 'Hide description' : 'About this channel'}
            </button>
          )}
        </div>
        <div className="channel-actions">
          {videos.length > 0 && (
            <button
              type="button"
              className="button"
              onClick={() =>
                player.playAll(
                  videos.map((v) => ({
                    url: v.url,
                    title: v.title,
                    uploaderName: channel.name,
                    uploaderUrl: channel.url,
                    thumbnailUrl: v.thumbnailUrl,
                    durationText: v.durationText,
                    durationSeconds: v.durationSeconds
                  }))
                )
              }
            >
              Play all
            </button>
          )}
          <button
            type="button"
            className={`button ${subscribed ? 'tonal' : 'inverse'}`}
            onClick={() => {
              library.toggleSubscription({ url: channel.url, name: channel.name, thumbnailUrl: channel.avatarUrl });
              toast(subscribed ? `Unsubscribed from ${channel.name}` : `Subscribed to ${channel.name}`);
            }}
          >
            {subscribed ? 'Subscribed' : 'Subscribe'}
          </button>
        </div>
      </header>
      {aboutOpen && (
        <div className="description open" style={{ marginBottom: 24 }}>
          <div className="description-text">
            <Description text={channel.description} format="text" />
          </div>
        </div>
      )}
      <h2 className="section-title">Videos</h2>
      {channel.videos.items.length ? (
        <PagedGrid first={channel.videos} />
      ) : (
        <div className="empty-state">This channel has no videos.</div>
      )}
    </div>
  );
}
