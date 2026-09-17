import { useState, type ReactNode } from 'react';
import { TextInputDialog } from '../components/Dialogs';
import { toast } from '../components/Toast';
import { CloseIcon, DeleteIcon, HistoryIcon, LibraryIcon, PlayIcon, WatchLaterIcon } from '../icons';
import { library, useLibrary, type SavedVideo } from '../library';
import { player } from '../player';
import { navigate, routeHref } from '../router';
import './LibraryPage.css';

const TABS = [
  { id: 'history', label: 'History' },
  { id: 'watch-later', label: 'Watch later' },
  { id: 'playlists', label: 'Playlists' }
];

function VideoRow({ video, onRemove, progress }: { video: SavedVideo; onRemove: () => void; progress?: number }) {
  return (
    <div className="library-row">
      <a className="library-row-link" href={routeHref({ name: 'watch', url: video.url, time: 0 })}>
        <div className="thumb">
          {video.thumbnailUrl && <img src={video.thumbnailUrl} alt="" loading="lazy" />}
          {video.durationText && <span className="duration">{video.durationText}</span>}
          {progress !== undefined && progress > 0 && (
            <div className="progress">
              <span style={{ width: `${Math.min(100, progress * 100)}%` }} />
            </div>
          )}
        </div>
        <div className="library-row-text">
          <h3 className="card-title">{video.title}</h3>
          <div className="card-meta">{video.uploaderName}</div>
        </div>
      </a>
      <button type="button" className="icon-button" aria-label={`Remove ${video.title}`} onClick={onRemove}>
        <CloseIcon size={20} />
      </button>
    </div>
  );
}

function Empty({ icon, title, text }: { icon: ReactNode; title: string; text: string }) {
  return (
    <div className="empty-state">
      {icon}
      <h3>{title}</h3>
      <div>{text}</div>
    </div>
  );
}

export function LibraryPage({ tab }: { tab: string }) {
  const { history, watchLater, playlists, settings } = useLibrary();
  const current = TABS.some((t) => t.id === tab) ? tab : 'history';
  const [dialog, setDialog] = useState<{ kind: 'create' } | { kind: 'rename'; id: string; name: string } | null>(null);
  const [openPlaylist, setOpenPlaylist] = useState<string | null>(null);

  return (
    <div className="page">
      <h1 className="page-title">Library</h1>
      <div className="chips" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={t.id === current}
            className={`chip ${t.id === current ? 'selected' : ''}`}
            onClick={() => navigate({ name: 'library', tab: t.id }, { replace: true })}
          >
            {t.label}
          </button>
        ))}
      </div>

      {current === 'history' && (
        <>
          {history.length > 0 && (
            <div className="library-actions">
              <button type="button" className="button" onClick={() => player.playAll(history)}>
                <PlayIcon size={18} /> Play all
              </button>
              <button
                type="button"
                className="button"
                onClick={() => {
                  library.clearHistory();
                  toast('Watch history cleared');
                }}
              >
                <DeleteIcon size={18} /> Clear history
              </button>
            </div>
          )}
          {!settings.keepHistory && <p className="muted">History is turned off in Settings.</p>}
          {history.length === 0 ? (
            <Empty icon={<HistoryIcon size={48} />} title="No watch history" text="Videos you watch show up here, with the position to resume from." />
          ) : (
            history.map((entry) => (
              <VideoRow
                key={entry.url}
                video={entry}
                progress={entry.durationSeconds ? entry.positionSeconds / entry.durationSeconds : undefined}
                onRemove={() => library.removeFromHistory(entry.url)}
              />
            ))
          )}
        </>
      )}

      {current === 'watch-later' && (
        <>
          {watchLater.length > 0 && (
            <div className="library-actions">
              <button type="button" className="button" onClick={() => player.playAll(watchLater)}>
                <PlayIcon size={18} /> Play all
              </button>
              <button type="button" className="button" onClick={() => library.clearWatchLater()}>
                <DeleteIcon size={18} /> Clear list
              </button>
            </div>
          )}
          {watchLater.length === 0 ? (
            <Empty icon={<WatchLaterIcon size={48} />} title="Nothing saved" text="Save videos to watch later from their menu or the video page." />
          ) : (
            watchLater.map((video) => <VideoRow key={video.url} video={video} onRemove={() => library.toggleWatchLater(video)} />)
          )}
        </>
      )}

      {current === 'playlists' && (
        <>
          <div className="library-actions">
            <button type="button" className="button filled" onClick={() => setDialog({ kind: 'create' })}>
              New playlist
            </button>
          </div>
          {playlists.length === 0 && <Empty icon={<LibraryIcon size={48} />} title="No playlists" text="Create a playlist, then save videos to it." />}
          {playlists.map((playlist) => (
            <section key={playlist.id} className="playlist">
              <div className="playlist-head">
                <button type="button" className="link-button playlist-name" onClick={() => setOpenPlaylist(openPlaylist === playlist.id ? null : playlist.id)} aria-expanded={openPlaylist === playlist.id}>
                  {playlist.name} <span className="muted">· {playlist.items.length} videos</span>
                </button>
                <div className="library-actions" style={{ margin: 0 }}>
                  <button type="button" className="button" disabled={!playlist.items.length} onClick={() => player.playAll(playlist.items)}>
                    <PlayIcon size={18} /> Play all
                  </button>
                  <button type="button" className="button" onClick={() => setDialog({ kind: 'rename', id: playlist.id, name: playlist.name })}>
                    Rename
                  </button>
                  <button
                    type="button"
                    className="button"
                    onClick={() => {
                      library.deletePlaylist(playlist.id);
                      toast(`Deleted ${playlist.name}`);
                    }}
                  >
                    <DeleteIcon size={18} /> Delete
                  </button>
                </div>
              </div>
              {openPlaylist === playlist.id &&
                (playlist.items.length ? (
                  playlist.items.map((video) => <VideoRow key={video.url} video={video} onRemove={() => library.removeFromPlaylist(playlist.id, video.url)} />)
                ) : (
                  <p className="muted">This playlist is empty.</p>
                ))}
            </section>
          ))}
        </>
      )}

      {dialog?.kind === 'create' && (
        <TextInputDialog title="New playlist" label="Playlist name" confirm="Create" onClose={() => setDialog(null)} onConfirm={(name) => library.createPlaylist(name)} />
      )}
      {dialog?.kind === 'rename' && (
        <TextInputDialog
          title="Rename playlist"
          label="Playlist name"
          initial={dialog.name}
          confirm="Rename"
          onClose={() => setDialog(null)}
          onConfirm={(name) => library.renamePlaylist(dialog.id, name)}
        />
      )}
    </div>
  );
}
