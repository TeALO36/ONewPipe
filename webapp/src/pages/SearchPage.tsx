import { api, type SearchFilter } from '../api';
import { ErrorState, PagedGrid, SkeletonGrid } from '../components/MediaGrid';
import { useAsync } from '../hooks/useAsync';
import { SearchIcon } from '../icons';
import { navigate } from '../router';

const FILTERS: { id: SearchFilter; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'videos', label: 'Videos' },
  { id: 'channels', label: 'Channels' },
  { id: 'playlists', label: 'Playlists' }
];

export function SearchPage({ query, filter }: { query: string; filter: string }) {
  const current = (FILTERS.find((f) => f.id === filter)?.id ?? 'all') as SearchFilter;
  const result = useAsync((signal) => (query ? api.search(query, current, signal) : Promise.resolve({ items: [] })), [query, current]);

  return (
    <div className="page">
      <div className="chips" role="tablist" aria-label="Result type">
        {FILTERS.map((f) => (
          <button
            key={f.id}
            type="button"
            role="tab"
            aria-selected={f.id === current}
            className={`chip ${f.id === current ? 'selected' : ''}`}
            onClick={() => navigate({ name: 'search', query, filter: f.id }, { replace: true })}
          >
            {f.label}
          </button>
        ))}
      </div>
      {!query && (
        <div className="empty-state">
          <SearchIcon size={40} />
          <h3>Search ONewPipe</h3>
          <div>Type something in the search bar above.</div>
        </div>
      )}
      {query && result.status === 'loading' && <SkeletonGrid />}
      {query && result.status === 'error' && <ErrorState message={result.error} onRetry={result.retry} />}
      {query && result.status === 'ready' &&
        (result.data.items.length ? (
          <PagedGrid first={result.data} />
        ) : (
          <div className="empty-state">
            <SearchIcon size={40} />
            <h3>No results for “{query}”</h3>
            <div>Try other words or another result type.</div>
          </div>
        ))}
    </div>
  );
}
