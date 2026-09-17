// Hash routing: the app is served as static files by the ONewPipe server and
// loaded by Electron, so routes live after "#" and the browser/Electron back
// button keeps working without server-side rewrites.
import { useEffect, useState } from 'react';

export type Route =
  | { name: 'home' }
  | { name: 'trending'; category: string }
  | { name: 'subscriptions' }
  | { name: 'library'; tab: string }
  | { name: 'settings' }
  | { name: 'search'; query: string; filter: string }
  | { name: 'channel'; url: string }
  | { name: 'watch'; url: string; time: number };

export function parseRoute(hash: string): Route {
  const [path, rawQuery = ''] = hash.replace(/^#\/?/, '').split('?');
  const params = new URLSearchParams(rawQuery);
  switch (path) {
    case 'trending':
      return { name: 'trending', category: params.get('category') || 'all' };
    case 'subscriptions':
      return { name: 'subscriptions' };
    case 'library':
      return { name: 'library', tab: params.get('tab') || 'history' };
    case 'settings':
      return { name: 'settings' };
    case 'search':
      return { name: 'search', query: params.get('q') || '', filter: params.get('filter') || 'all' };
    case 'channel':
      return { name: 'channel', url: params.get('url') || '' };
    case 'watch':
      return { name: 'watch', url: params.get('v') || '', time: Number(params.get('t') || 0) };
    default:
      return { name: 'home' };
  }
}

export function routeHref(route: Route): string {
  const q = (params: Record<string, string | number | undefined>) => {
    const search = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '' && value !== 0) search.set(key, String(value));
    });
    const text = search.toString();
    return text ? `?${text}` : '';
  };
  switch (route.name) {
    case 'home':
      return '#/';
    case 'trending':
      return `#/trending${q({ category: route.category === 'all' ? undefined : route.category })}`;
    case 'subscriptions':
      return '#/subscriptions';
    case 'library':
      return `#/library${q({ tab: route.tab === 'history' ? undefined : route.tab })}`;
    case 'settings':
      return '#/settings';
    case 'search':
      return `#/search${q({ q: route.query, filter: route.filter === 'all' ? undefined : route.filter })}`;
    case 'channel':
      return `#/channel${q({ url: route.url })}`;
    case 'watch':
      return `#/watch${q({ v: route.url, t: route.time ? Math.floor(route.time) : undefined })}`;
  }
}

export function navigate(route: Route, options: { replace?: boolean } = {}) {
  const href = routeHref(route);
  if (options.replace) {
    history.replaceState(null, '', href);
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  } else {
    window.location.hash = href;
  }
}

export function goBack(fallback: Route = { name: 'home' }) {
  if (history.length > 1) history.back();
  else navigate(fallback, { replace: true });
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => parseRoute(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(parseRoute(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}
