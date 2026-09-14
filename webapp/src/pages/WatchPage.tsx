import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, formatCount, type Comments, type Item, type Subtitle, type Watch } from '../api';
import { Description } from '../components/Description';
import { AddToPlaylistDialog } from '../components/Dialogs';
import { Avatar, metaLine } from '../components/MediaCard';
import { ErrorState } from '../components/MediaGrid';
import { toast } from '../components/Toast';
import { VideoPlayer, type QualityOption, type VideoPlayerHandle } from '../components/VideoPlayer';
import { CircularWavyProgress } from '../components/WavyProgress';
import { useAsync } from '../hooks/useAsync';
import {
  CheckIcon,
  CloseIcon,
  CommentIcon,
  OpenInNewIcon,
  PlaylistAddIcon,
  QueueIcon,
  RepeatIcon,
  RepeatOneIcon,
  ShareIcon,
  SpeedIcon,
  SubtitlesIcon,
  ThumbUpIcon,
  TuneIcon,
  VerifiedIcon,
  WatchLaterIcon
} from '../icons';
import { library, toSaved, useLibrary, type SavedVideo } from '../library';
import { player, usePlayer } from '../player';
import { navigate, routeHref } from '../router';
import './WatchPage.css';

const SPEEDS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2];

function Menu({ label, icon, children, open, setOpen }: { label: string; icon: ReactNode; children: ReactNode; open: boolean; setOpen: (v: boolean) => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open, setOpen]);
  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button type="button" className={`button ${open ? 'active' : ''}`} aria-expanded={open} onClick={() => setOpen(!open)}>
        {icon}
        {label}
      </button>
      {open && (
        <div className="popover menu-up" role="menu">
          {children}
        </div>
      )}
    </div>
  );
}

function watchToSaved(watch: Watch): SavedVideo {
  return {
    url: watch.url,
    title: watch.title,
    uploaderName: watch.uploaderName,
    uploaderUrl: watch.uploaderUrl,
    thumbnailUrl: watch.thumbnailUrl,
    durationText: '',
    durationSeconds: watch.durationSeconds
  };
}

function CommentsSection({ url }: { url: string }) {
  const [data, setData] = useState<Comments | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async (more?: string) => {
    setLoading(true);
    setError(null);
    try {
      const page = more ? await api.moreComments(more) : await api.comments(url);
      setData((current) => (more && current ? { ...page, comments: [...current.comments, ...page.comments] } : page));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load comments');
    } finally {
      setLoading(false);
    }
  };

  if (!data) {
    return (
      <div className="comments">
        {error ? (
          <ErrorState message={error} onRetry={() => void load()} />
        ) : (
          <button type="button" className="button" disabled={loading} onClick={() => void load()}>
            {loading ? <CircularWavyProgress size={20} /> : <CommentIcon size={20} />} Show comments
          </button>
        )}
      </div>
    );
  }

  return (
    <section className="comments" aria-label="Comments">
      <h2>Comments</h2>
      {data.disabled && <p className="muted">Comments are turned off for this video.</p>}
      {!data.disabled && data.comments.length === 0 && <p className="muted">No comments yet.</p>}
      {data.comments.map((comment, index) => (
        <article key={index} className="comment">
          <Avatar src={comment.authorAvatarUrl} name={comment.author || '?'} size={40} />
          <div>
            <div className="comment-head">
              <strong>{comment.author}</strong>
              <span className="muted">{comment.publishedText}</span>
              {comment.pinned && <span className="pill">Pinned</span>}
            </div>
            <div className="comment-text">
              <Description text={comment.text} format="html" />
            </div>
            <div className="comment-foot muted">
              <ThumbUpIcon size={16} /> {formatCount(comment.likeCount)}
              {comment.replyCount > 0 && <span> · {comment.replyCount} replies</span>}
            </div>
          </div>
        </article>
      ))}
      {data.nextPage && (
        <div className="load-more">
          {error ? (
            <button type="button" className="button" onClick={() => void load(data.nextPage!)}>
              {error} · Try again
            </button>
          ) : (
            <button type="button" className="button" disabled={loading} onClick={() => void load(data.nextPage!)}>
              {loading ? <CircularWavyProgress size={20} /> : null} More comments
            </button>
          )}
        </div>
      )}
    </section>
  );
}

function RelatedList({ items }: { items: Item[] }) {
  return (
    <div className="related">
      {items
        .filter((item) => item.kind === 'video')
        .map((item) => (
          <a key={item.url} className="related-item" href={routeHref({ name: 'watch', url: item.url, time: 0 })}>
            <div className="thumb">
              {item.thumbnailUrl && <img src={item.thumbnailUrl} alt="" loading="lazy" />}
              {item.durationText && <span className="duration">{item.durationText}</span>}
            </div>
            <div className="related-text">
              <h3 className="card-title" style={{ fontSize: 14 }}>
                {item.title}
              </h3>
              <div className="card-meta">
                <div>{item.uploaderName}</div>
                <div>{metaLine(item)}</div>
              </div>
            </div>
          </a>
        ))}
    </div>
  );
}

export function WatchPage({ url, startTime }: { url: string; startTime: number }) {
  const result = useAsync((signal) => api.watch(url, signal), [url]);
  const { settings, subscriptions, watchLater } = useLibrary();
  const { queue, repeat, speed } = usePlayer();
  const playerRef = useRef<VideoPlayerHandle>(null);
  const [playbackError, setPlaybackError] = useState<string | null>(null);
  const [qualities, setQualities] = useState<QualityOption[]>([]);
  const [quality, setQuality] = useState<number | 'auto'>('auto');
  const [subtitle, setSubtitle] = useState<Subtitle | null>(null);
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const [descriptionOpen, setDescriptionOpen] = useState(false);
  const [playlistDialog, setPlaylistDialog] = useState(false);

  const watch = result.status === 'ready' ? result.data : null;
  const saved = useMemo(() => (watch ? watchToSaved(watch) : null), [watch]);

  // Resume where the video was left off unless the link asks for a time.
  const initialTime = useMemo(() => {
    if (startTime > 0) return startTime;
    return watch ? library.resumePosition(watch.url, watch.durationSeconds) : 0;
    // Only decided once per video.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [watch?.url]);

  useEffect(() => {
    if (saved) library.recordWatch(saved, initialTime);
  }, [saved, initialTime]);

  useEffect(() => {
    if (watch) document.title = `${watch.title} · ONewPipe`;
    return () => {
      document.title = 'ONewPipe';
    };
  }, [watch]);

  const onReady = useCallback(() => {
    setPlaybackError(null);
    const options = playerRef.current?.qualities() ?? [];
    setQualities(options);
    // Preferred quality: the highest available height that does not exceed it.
    const limit = Number(settings.preferredQuality);
    if (limit > 0) {
      const match = options.find((option) => option.height <= limit);
      if (match) {
        playerRef.current?.setQuality(match.height);
        setQuality(match.height);
      }
    }
  }, [settings.preferredQuality]);

  const onEnded = useCallback(() => {
    if (!saved || !watch) return;
    if (player.next(saved)) return;
    if (settings.autoplayNext) {
      const next = watch.related.find((item) => item.kind === 'video');
      if (next) navigate({ name: 'watch', url: next.url, time: 0 });
    }
  }, [saved, watch, settings.autoplayNext]);

  // Keyboard shortcuts, YouTube style.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      if (target.closest('input, textarea, select, [contenteditable="true"]') || event.ctrlKey || event.metaKey || event.altKey) return;
      const video = playerRef.current?.video;
      if (!video) return;
      switch (event.key.toLowerCase()) {
        case ' ':
        case 'k':
          if (target.tagName === 'VIDEO' && event.key === ' ') return;
          event.preventDefault();
          if (video.paused) void video.play();
          else video.pause();
          break;
        case 'j':
          video.currentTime = Math.max(0, video.currentTime - 10);
          break;
        case 'l':
          video.currentTime = Math.min(video.duration || Infinity, video.currentTime + 10);
          break;
        case 'arrowleft':
          if (target.tagName === 'VIDEO') return;
          video.currentTime = Math.max(0, video.currentTime - 5);
          break;
        case 'arrowright':
          if (target.tagName === 'VIDEO') return;
          video.currentTime = Math.min(video.duration || Infinity, video.currentTime + 5);
          break;
        case 'f':
          if (document.fullscreenElement) void document.exitFullscreen();
          else void video.requestFullscreen();
          break;
        case 'm':
          video.muted = !video.muted;
          break;
        default:
          if (/^[0-9]$/.test(event.key) && video.duration) video.currentTime = (Number(event.key) / 10) * video.duration;
      }
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, []);

  if (result.status === 'error') {
    return (
      <div className="page">
        <ErrorState message={`This video could not be opened: ${result.error}`} onRetry={result.retry} />
      </div>
    );
  }

  const subscribed = watch ? subscriptions.some((s) => s.url === watch.uploaderUrl) : false;
  const inWatchLater = watch ? watchLater.some((v) => v.url === watch.url) : false;

  return (
    <div className="watch">
      <div className="watch-primary">
        {watch ? (
          <>
            <VideoPlayer
              ref={playerRef}
              url={watch.url}
              poster={watch.thumbnailUrl}
              startTime={initialTime}
              speed={speed}
              loop={repeat === 'one'}
              onReady={onReady}
              onTimeUpdate={(seconds) => library.updatePosition(watch.url, seconds)}
              onEnded={onEnded}
              onError={setPlaybackError}
            />
            {playbackError && (
              <div className="playback-error" role="alert">
                {playbackError}
                <a className="button" href={watch.url} target="_blank" rel="noreferrer">
                  <OpenInNewIcon size={18} /> Open on the website
                </a>
              </div>
            )}
          </>
        ) : (
          <div className="video-frame skeleton" aria-hidden="true">
            <div className="video-loading">
              <CircularWavyProgress size={64} />
            </div>
          </div>
        )}

        <div className="player-toolbar" role="toolbar" aria-label="Player settings">
          <Menu
            label={quality === 'auto' ? 'Auto' : `${quality}p`}
            icon={<TuneIcon size={18} />}
            open={openMenu === 'quality'}
            setOpen={(v) => setOpenMenu(v ? 'quality' : null)}
          >
            <button type="button" className={`menu-item ${quality === 'auto' ? 'selected' : ''}`} onClick={() => { playerRef.current?.setQuality('auto'); setQuality('auto'); setOpenMenu(null); }}>
              Auto
            </button>
            {qualities.map((q) => (
              <button key={q.height} type="button" className={`menu-item ${quality === q.height ? 'selected' : ''}`} onClick={() => { playerRef.current?.setQuality(q.height); setQuality(q.height); setOpenMenu(null); }}>
                {q.label}
              </button>
            ))}
          </Menu>

          <Menu label={speed === 1 ? 'Normal' : `${speed}×`} icon={<SpeedIcon size={18} />} open={openMenu === 'speed'} setOpen={(v) => setOpenMenu(v ? 'speed' : null)}>
            {SPEEDS.map((s) => (
              <button key={s} type="button" className={`menu-item ${s === speed ? 'selected' : ''}`} onClick={() => { player.setSpeed(s); setOpenMenu(null); }}>
                {s === 1 ? 'Normal' : `${s}×`}
              </button>
            ))}
          </Menu>

          {watch && watch.subtitles.length > 0 && (
            <Menu label={subtitle ? subtitle.label : 'Subtitles'} icon={<SubtitlesIcon size={18} />} open={openMenu === 'subtitles'} setOpen={(v) => setOpenMenu(v ? 'subtitles' : null)}>
              <button type="button" className={`menu-item ${!subtitle ? 'selected' : ''}`} onClick={() => { void playerRef.current?.setSubtitle(null); setSubtitle(null); setOpenMenu(null); }}>
                Off
              </button>
              {watch.subtitles.map((s) => (
                <button
                  key={s.url}
                  type="button"
                  className={`menu-item ${subtitle?.url === s.url ? 'selected' : ''}`}
                  onClick={() => {
                    setOpenMenu(null);
                    playerRef.current?.setSubtitle(s).then(
                      () => setSubtitle(s),
                      () => toast('These subtitles could not be loaded')
                    );
                  }}
                >
                  {s.label}
                </button>
              ))}
            </Menu>
          )}

          <button
            type="button"
            className={`button ${repeat !== 'off' ? 'active' : ''}`}
            onClick={() => player.cycleRepeat()}
            title="Repeat"
          >
            {repeat === 'one' ? <RepeatOneIcon size={18} /> : <RepeatIcon size={18} />}
            {repeat === 'off' ? 'Repeat off' : repeat === 'all' ? 'Repeat queue' : 'Repeat video'}
          </button>
        </div>

        {watch ? (
          <>
            <h1 className="watch-title">{watch.title}</h1>
            <div className="watch-owner">
              <a className="owner" href={watch.uploaderUrl ? routeHref({ name: 'channel', url: watch.uploaderUrl }) : undefined}>
                <Avatar src={watch.uploaderAvatarUrl} name={watch.uploaderName} size={44} />
                <div>
                  <div className="owner-name">
                    {watch.uploaderName}
                    {watch.uploaderVerified && <VerifiedIcon size={16} aria-label="Verified" />}
                  </div>
                  {watch.uploaderSubscriberCount >= 0 && <div className="muted">{formatCount(watch.uploaderSubscriberCount)} subscribers</div>}
                </div>
              </a>
              {watch.uploaderUrl && (
                <button
                  type="button"
                  className={`button ${subscribed ? 'tonal' : 'inverse'}`}
                  onClick={() => {
                    library.toggleSubscription({ url: watch.uploaderUrl, name: watch.uploaderName, thumbnailUrl: watch.uploaderAvatarUrl });
                    toast(subscribed ? `Unsubscribed from ${watch.uploaderName}` : `Subscribed to ${watch.uploaderName}`);
                  }}
                >
                  {subscribed ? 'Subscribed' : 'Subscribe'}
                </button>
              )}
              <div className="watch-actions">
                {watch.likeCount >= 0 && (
                  <span className="button tonal" style={{ cursor: 'default' }}>
                    <ThumbUpIcon size={18} /> {formatCount(watch.likeCount)}
                  </span>
                )}
                <button type="button" className="button tonal" onClick={() => saved && (library.toggleWatchLater(saved), toast(inWatchLater ? 'Removed from Watch later' : 'Saved to Watch later'))}>
                  {inWatchLater ? <CheckIcon size={18} /> : <WatchLaterIcon size={18} />} Watch later
                </button>
                <button type="button" className="button tonal" onClick={() => setPlaylistDialog(true)}>
                  <PlaylistAddIcon size={18} /> Save
                </button>
                <button
                  type="button"
                  className="button tonal"
                  onClick={() =>
                    navigator.clipboard.writeText(watch.url).then(
                      () => toast('Link copied'),
                      () => toast('Could not copy the link')
                    )
                  }
                >
                  <ShareIcon size={18} /> Share
                </button>
                <a className="button tonal" href={watch.url} target="_blank" rel="noreferrer">
                  <OpenInNewIcon size={18} /> Website
                </a>
              </div>
            </div>

            <div className={`description ${descriptionOpen ? 'open' : ''}`}>
              <div className="description-meta">
                {watch.viewCount >= 0 && `${formatCount(watch.viewCount)} views`}
                {watch.uploadedText && ` · ${watch.uploadedText}`}
              </div>
              <div className="description-text">
                <Description text={watch.description} format={watch.descriptionFormat} />
              </div>
              {watch.description && (
                <button type="button" className="link-button" onClick={() => setDescriptionOpen((v) => !v)}>
                  {descriptionOpen ? 'Show less' : 'Show more'}
                </button>
              )}
            </div>

            <CommentsSection key={watch.url} url={watch.url} />
          </>
        ) : (
          <div aria-hidden="true">
            <div className="skeleton skeleton-line" style={{ height: 24, width: '70%', marginTop: 16 }} />
            <div className="skeleton skeleton-line" style={{ width: '35%', marginTop: 16 }} />
          </div>
        )}
      </div>

      <aside className="watch-secondary">
        {queue.length > 0 && (
          <section className="queue" aria-label="Queue">
            <div className="queue-head">
              <h2>
                <QueueIcon size={20} /> Queue · {queue.length}
              </h2>
              <button type="button" className="button" onClick={() => player.clear()}>
                Clear
              </button>
            </div>
            {queue.map((video) => (
              <div key={video.url} className="queue-item">
                <a href={routeHref({ name: 'watch', url: video.url, time: 0 })} onClick={() => player.remove(video.url)}>
                  {video.title}
                </a>
                <button type="button" className="icon-button" aria-label={`Remove ${video.title} from the queue`} onClick={() => player.remove(video.url)}>
                  <CloseIcon size={18} />
                </button>
              </div>
            ))}
          </section>
        )}
        {watch ? (
          <RelatedList items={watch.related} />
        ) : (
          Array.from({ length: 8 }, (_, index) => (
            <div key={index} className="related-item" aria-hidden="true">
              <div className="thumb skeleton" />
              <div style={{ flex: 1 }}>
                <div className="skeleton skeleton-line" />
                <div className="skeleton skeleton-line" style={{ width: '60%' }} />
              </div>
            </div>
          ))
        )}
      </aside>

      {playlistDialog && saved && <AddToPlaylistDialog video={saved} onClose={() => setPlaylistDialog(false)} />}
    </div>
  );
}

export { toSaved };
