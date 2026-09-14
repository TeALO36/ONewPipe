import { useEffect, useRef, useState } from 'react';
import { formatCount, type Item } from '../api';
import { CheckIcon, MoreIcon, OpenInNewIcon, QueueIcon, ShareIcon, SubscriptionsIcon, WatchLaterIcon } from '../icons';
import { library, toSaved, useLibrary } from '../library';
import { player } from '../player';
import { routeHref } from '../router';
import { toast } from './Toast';

function Avatar({ src, name, size = 36 }: { src?: string; name: string; size?: number }) {
  const [failed, setFailed] = useState(false);
  if (!src || failed) {
    return (
      <span className="avatar placeholder" style={{ width: size, height: size }} aria-hidden="true">
        {name.slice(0, 1).toUpperCase()}
      </span>
    );
  }
  return <img className="avatar" src={src} alt="" width={size} height={size} loading="lazy" onError={() => setFailed(true)} />;
}

export { Avatar };

export function metaLine(item: Item): string {
  return [item.viewCount >= 0 ? `${formatCount(item.viewCount)} views` : '', item.uploadedText].filter(Boolean).join(' · ');
}

function CardMenu({ item }: { item: Item }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const { watchLater } = useLibrary();
  const saved = watchLater.some((v) => v.url === item.url);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  const run = (action: () => void) => () => {
    action();
    setOpen(false);
  };

  return (
    <div className="card-menu" ref={ref}>
      <button type="button" className="icon-button" aria-label="More actions" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <MoreIcon size={20} />
      </button>
      {open && (
        <div className="popover" role="menu">
          <button
            type="button"
            role="menuitem"
            className="menu-item"
            onClick={run(() => {
              player.enqueue(toSaved(item));
              toast('Added to the queue');
            })}
          >
            <QueueIcon size={20} /> Add to queue
          </button>
          <button
            type="button"
            role="menuitem"
            className="menu-item"
            onClick={run(() => {
              library.toggleWatchLater(toSaved(item));
              toast(saved ? 'Removed from Watch later' : 'Saved to Watch later');
            })}
          >
            {saved ? <CheckIcon size={20} /> : <WatchLaterIcon size={20} />} {saved ? 'Saved to Watch later' : 'Save to Watch later'}
          </button>
          {item.uploaderUrl && (
            <a role="menuitem" className="menu-item" href={routeHref({ name: 'channel', url: item.uploaderUrl })} onClick={() => setOpen(false)}>
              <SubscriptionsIcon size={20} /> Go to channel
            </a>
          )}
          <div className="menu-separator" />
          <button
            type="button"
            role="menuitem"
            className="menu-item"
            onClick={run(() => {
              navigator.clipboard.writeText(item.url).then(
                () => toast('Link copied'),
                () => toast('Could not copy the link')
              );
            })}
          >
            <ShareIcon size={20} /> Copy link
          </button>
          <a role="menuitem" className="menu-item" href={item.url} target="_blank" rel="noreferrer" onClick={() => setOpen(false)}>
            <OpenInNewIcon size={20} /> Open on the website
          </a>
        </div>
      )}
    </div>
  );
}

export function MediaCard({ item, progress }: { item: Item; progress?: number }) {
  if (item.kind === 'channel') {
    return (
      <article className="card channel">
        <a className="card-link" href={routeHref({ name: 'channel', url: item.url })}>
          <div className="thumb">
            {item.thumbnailUrl && <img src={item.thumbnailUrl} alt="" loading="lazy" />}
          </div>
        </a>
        <div className="card-body" style={{ gridTemplateColumns: '1fr' }}>
          <a className="card-link" href={routeHref({ name: 'channel', url: item.url })}>
            <h3 className="card-title">{item.title}</h3>
            <div className="card-meta">
              Channel{item.subscriberCount >= 0 ? ` · ${formatCount(item.subscriberCount)} subscribers` : ''}
            </div>
          </a>
        </div>
      </article>
    );
  }

  const href = item.kind === 'video' ? routeHref({ name: 'watch', url: item.url, time: 0 }) : item.url;
  const external = item.kind !== 'video';

  return (
    <article className="card">
      <a className="card-link" href={href} target={external ? '_blank' : undefined} rel={external ? 'noreferrer' : undefined} title={item.title}>
        <div className="thumb">
          {item.thumbnailUrl && <img src={item.thumbnailUrl} alt="" loading="lazy" />}
          {item.isLive ? <span className="live">LIVE</span> : item.durationText && <span className="duration">{item.durationText}</span>}
          {item.kind === 'playlist' && <span className="duration">Playlist</span>}
          {progress !== undefined && progress > 0 && (
            <div className="progress">
              <span style={{ width: `${Math.min(100, progress * 100)}%` }} />
            </div>
          )}
        </div>
      </a>
      <div className="card-body">
        {item.uploaderAvatarUrl ? (
          <a href={item.uploaderUrl ? routeHref({ name: 'channel', url: item.uploaderUrl }) : undefined} aria-label={item.uploaderName}>
            <Avatar src={item.uploaderAvatarUrl} name={item.uploaderName} />
          </a>
        ) : (
          <span style={{ width: 0 }} />
        )}
        <div style={{ minWidth: 0 }}>
          <a className="card-link" href={href} target={external ? '_blank' : undefined} rel={external ? 'noreferrer' : undefined}>
            <h3 className="card-title">{item.title}</h3>
          </a>
          <div className="card-meta">
            {item.uploaderUrl ? <a href={routeHref({ name: 'channel', url: item.uploaderUrl })}>{item.uploaderName}</a> : item.uploaderName}
            {metaLine(item) && <div>{metaLine(item)}</div>}
          </div>
        </div>
        {item.kind === 'video' ? <CardMenu item={item} /> : <span />}
      </div>
    </article>
  );
}
