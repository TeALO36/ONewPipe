import { api, type Category } from '../api';
import { ErrorState, MediaGrid, SkeletonGrid } from '../components/MediaGrid';
import { useAsync } from '../hooks/useAsync';
import { navigate } from '../router';

const CATEGORIES: { id: Category; label: string }[] = [
  { id: 'all', label: 'All' },
  { id: 'music', label: 'Music' },
  { id: 'gaming', label: 'Gaming' },
  { id: 'movies', label: 'Movies & Shows' },
  { id: 'podcasts', label: 'Podcasts' }
];

export function TrendingPage({ category }: { category: string }) {
  const current = (CATEGORIES.find((c) => c.id === category)?.id ?? 'all') as Category;
  const result = useAsync((signal) => api.trending(current, signal), [current]);

  return (
    <div className="page">
      <h1 className="page-title">Trending</h1>
      <div className="chips" role="tablist" aria-label="Categories">
        {CATEGORIES.map((c) => (
          <button
            key={c.id}
            type="button"
            role="tab"
            aria-selected={c.id === current}
            className={`chip ${c.id === current ? 'selected' : ''}`}
            onClick={() => navigate({ name: 'trending', category: c.id }, { replace: true })}
          >
            {c.label}
          </button>
        ))}
      </div>
      {result.status === 'loading' && <SkeletonGrid />}
      {result.status === 'error' && <ErrorState message={result.error} onRetry={result.retry} />}
      {result.status === 'ready' &&
        (result.data.items.length ? (
          <MediaGrid items={result.data.items} />
        ) : (
          <ErrorState message="Nothing is trending in this category right now." onRetry={result.retry} />
        ))}
    </div>
  );
}
