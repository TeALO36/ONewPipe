import { useEffect, useRef, useState } from 'react';
import { api, type Item, type Page } from '../api';
import { ErrorIcon } from '../icons';
import { useLibrary } from '../library';
import { MediaCard } from './MediaCard';
import { CircularWavyProgress } from './WavyProgress';

export function SkeletonGrid({ count = 12 }: { count?: number }) {
  return (
    <div className="grid" aria-hidden="true">
      {Array.from({ length: count }, (_, index) => (
        <div key={index} className="card skeleton-card">
          <div className="thumb skeleton" />
          <div className="skeleton skeleton-line" style={{ width: '90%' }} />
          <div className="skeleton skeleton-line" style={{ width: '55%' }} />
        </div>
      ))}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="error-state" role="alert">
      <ErrorIcon size={40} />
      <div>{message}</div>
      {onRetry && (
        <button type="button" className="button filled" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

/** Watched progress of each video, from the local history. */
export function useWatchProgress() {
  const { history } = useLibrary();
  return (item: Item) => {
    const entry = history.find((h) => h.url === item.url);
    const duration = item.durationSeconds || entry?.durationSeconds || 0;
    return entry && duration > 0 ? entry.positionSeconds / duration : undefined;
  };
}

export function MediaGrid({ items }: { items: Item[] }) {
  const progressOf = useWatchProgress();
  return (
    <div className="grid">
      {items.map((item) => (
        <MediaCard key={`${item.kind}:${item.url}`} item={item} progress={progressOf(item)} />
      ))}
    </div>
  );
}

/**
 * A grid that loads the next page when its end scrolls into view. [first] is
 * the already loaded first page.
 */
export function PagedGrid({ first }: { first: Page }) {
  const [items, setItems] = useState<Item[]>(first.items);
  const [next, setNext] = useState(first.nextPage ?? null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sentinel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setItems(first.items);
    setNext(first.nextPage ?? null);
    setError(null);
  }, [first]);

  const loadMore = async () => {
    if (!next || loading) return;
    setLoading(true);
    setError(null);
    try {
      const page = await api.more(next);
      setItems((current) => {
        const seen = new Set(current.map((i) => i.url));
        return [...current, ...page.items.filter((i) => !seen.has(i.url))];
      });
      setNext(page.nextPage ?? null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load more');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const node = sentinel.current;
    if (!node || !next || error) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) void loadMore();
    }, { rootMargin: '600px' });
    observer.observe(node);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [next, error, loading]);

  return (
    <>
      <MediaGrid items={items} />
      {next && (
        <div className="load-more" ref={sentinel}>
          {error ? (
            <button type="button" className="button" onClick={() => void loadMore()}>
              {error} · Try again
            </button>
          ) : (
            <CircularWavyProgress label="Loading more" />
          )}
        </div>
      )}
    </>
  );
}
