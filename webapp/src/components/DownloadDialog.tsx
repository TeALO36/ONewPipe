import { useState } from 'react';
import { ApiError, api, formatBytes, type DownloadOption } from '../api';
import { startDownload } from '../downloads';
import { useAsync } from '../hooks/useAsync';
import { DownloadIcon, HeadphonesIcon } from '../icons';
import { routeHref } from '../router';
import { Dialog } from './Dialogs';
import { ErrorState } from './MediaGrid';
import { toast } from './Toast';
import { CircularWavyProgress } from './WavyProgress';

/** Lists the qualities of a video; the chosen download then runs in the background. */
export function DownloadDialog({ url, onClose }: { url: string; onClose: () => void }) {
  const result = useAsync((signal) => api.downloadOptions(url, signal), [url]);
  const [starting, setStarting] = useState<string | null>(null);
  const [error, setError] = useState<{ message: string; signIn: boolean } | null>(null);

  const choose = async (option: DownloadOption) => {
    setStarting(option.id);
    setError(null);
    try {
      await startDownload(url, option);
      toast('Download started · follow it in the notifications');
      onClose();
    } catch (e) {
      setStarting(null);
      const signIn = e instanceof ApiError && e.status === 401;
      setError({ message: e instanceof Error ? e.message : 'The download could not start', signIn });
    }
  };

  const group = (kind: DownloadOption['kind'], title: string) => {
    if (result.status !== 'ready') return null;
    const options = result.data.options.filter((option) => option.kind === kind);
    if (!options.length) return null;
    return (
      <div className="download-group">
        <h3>{title}</h3>
        {options.map((option) => (
          <button key={option.id} type="button" className="menu-item" disabled={starting !== null} onClick={() => void choose(option)}>
            {starting === option.id ? (
              <CircularWavyProgress size={20} />
            ) : kind === 'audio' ? (
              <HeadphonesIcon size={20} />
            ) : (
              <DownloadIcon size={20} />
            )}
            <span style={{ flex: 1 }}>{option.label}</span>
            <span className="muted">{formatBytes(option.sizeBytes)}</span>
          </button>
        ))}
      </div>
    );
  };

  return (
    <Dialog title="Download" onClose={onClose}>
      {result.status === 'loading' && (
        <div style={{ display: 'grid', placeItems: 'center', padding: 24 }}>
          <CircularWavyProgress size={48} />
        </div>
      )}
      {result.status === 'error' && <ErrorState message={`The qualities could not be loaded: ${result.error}`} onRetry={result.retry} />}
      {result.status === 'ready' && result.data.options.length === 0 && <p className="muted">This video cannot be downloaded.</p>}
      {group('video', 'Video with sound')}
      {group('audio', 'Audio only')}
      {error && (
        <p role="alert" className="form-error">
          {error.message}
          {error.signIn && (
            <>
              {' '}
              <a href={routeHref({ name: 'settings' })} onClick={onClose}>
                Open settings
              </a>
            </>
          )}
        </p>
      )}
      <div className="dialog-actions">
        <button type="button" className="button" onClick={onClose}>
          Close
        </button>
      </div>
    </Dialog>
  );
}
