import { useEffect, useRef, useState, type ReactNode } from 'react';
import { AddIcon, CheckIcon } from '../icons';
import { library, useLibrary, type SavedVideo } from '../library';
import { toast } from './Toast';

export function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="dialog-scrim" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div className="dialog" role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function TextInputDialog({
  title,
  label,
  initial = '',
  confirm,
  onConfirm,
  onClose
}: {
  title: string;
  label: string;
  initial?: string;
  confirm: string;
  onConfirm: (value: string) => void;
  onClose: () => void;
}) {
  const [value, setValue] = useState(initial);
  const inputRef = useRef<HTMLInputElement>(null);
  useEffect(() => inputRef.current?.focus(), []);
  return (
    <Dialog title={title} onClose={onClose}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (!value.trim()) return;
          onConfirm(value.trim());
          onClose();
        }}
      >
        <input ref={inputRef} className="text-field" aria-label={label} placeholder={label} value={value} onChange={(e) => setValue(e.target.value)} />
        <div className="dialog-actions">
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="button filled" disabled={!value.trim()}>
            {confirm}
          </button>
        </div>
      </form>
    </Dialog>
  );
}

export function AddToPlaylistDialog({ video, onClose }: { video: SavedVideo; onClose: () => void }) {
  const { playlists } = useLibrary();
  const [creating, setCreating] = useState(false);

  if (creating) {
    return (
      <TextInputDialog
        title="New playlist"
        label="Playlist name"
        confirm="Create"
        onClose={onClose}
        onConfirm={(name) => {
          library.createPlaylist(name, video);
          toast(`Saved to ${name}`);
        }}
      />
    );
  }

  return (
    <Dialog title="Save to playlist" onClose={onClose}>
      <div style={{ display: 'grid', gap: 4 }}>
        {playlists.length === 0 && <p style={{ color: 'var(--on-surface-variant)', margin: 0 }}>You have no playlists yet.</p>}
        {playlists.map((playlist) => {
          const contains = playlist.items.some((v) => v.url === video.url);
          return (
            <button
              key={playlist.id}
              type="button"
              className="menu-item"
              onClick={() => {
                if (contains) library.removeFromPlaylist(playlist.id, video.url);
                else library.addToPlaylist(playlist.id, video);
                toast(contains ? `Removed from ${playlist.name}` : `Saved to ${playlist.name}`);
              }}
            >
              <span style={{ width: 20, display: 'grid', placeItems: 'center' }}>{contains && <CheckIcon size={20} />}</span>
              <span style={{ flex: 1 }}>{playlist.name}</span>
              <span style={{ color: 'var(--on-surface-variant)' }}>{playlist.items.length}</span>
            </button>
          );
        })}
      </div>
      <div className="dialog-actions" style={{ justifyContent: 'space-between' }}>
        <button type="button" className="button" onClick={() => setCreating(true)}>
          <AddIcon size={20} /> New playlist
        </button>
        <button type="button" className="button filled" onClick={onClose}>
          Done
        </button>
      </div>
    </Dialog>
  );
}
