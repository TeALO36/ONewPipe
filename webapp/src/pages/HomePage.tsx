import { api, type Category } from '../api';
import { MediaCard } from '../components/MediaCard';
import { ErrorState, useWatchProgress } from '../components/MediaGrid';
import { useAsync } from '../hooks/useAsync';
import { routeHref } from '../router';

const ROWS: { category: Category; title: string }[] = [
  { category: 'all', title: 'Trending' },
  { category: 'music', title: 'Music' },
  { category: 'gaming', title: 'Gaming' },
  { category: 'movies', title: 'Movies & Shows' },
  { category: 'podcasts', title: 'Podcasts' }
];

function Row({ category, title }: { category: Category; title: string }) {
  const result = useAsync((signal) => api.trending(category, signal), [category]);
  const progressOf = useWatchProgress();
  return (
    <section className="row" aria-labelledby={`row-${category}`}>
      <div className="row-header">
        <h2 id={`row-${category}`}>{title}</h2>
        <a className="button" href={routeHref({ name: 'trending', category })}>
          See all
        </a>
      </div>
      {result.status === 'error' ? (
        <ErrorState message={`${title} could not be loaded: ${result.error}`} onRetry={result.retry} />
      ) : (
        <div className="row-scroller">
          {result.status === 'loading'
            ? Array.from({ length: 6 }, (_, index) => (
                <div key={index} className="card skeleton-card" aria-hidden="true">
                  <div className="thumb skeleton" />
                  <div className="skeleton skeleton-line" style={{ width: '90%' }} />
                  <div className="skeleton skeleton-line" style={{ width: '55%' }} />
                </div>
              ))
            : result.data.items
                .filter((item) => item.kind === 'video')
                .slice(0, 12)
                .map((item) => <MediaCard key={item.url} item={item} progress={progressOf(item)} />)}
        </div>
      )}
    </section>
  );
}

export function HomePage() {
  return (
    <div className="page">
      {ROWS.map((row) => (
        <Row key={row.category} {...row} />
      ))}
    </div>
  );
}
