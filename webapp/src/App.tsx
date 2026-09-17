import { useEffect } from 'react';
import './App.css';
import { Shell } from './components/Shell';
import { useLibrary } from './library';
import { useRoute } from './router';
import { ChannelPage } from './pages/ChannelPage';
import { HomePage } from './pages/HomePage';
import { LibraryPage } from './pages/LibraryPage';
import { SearchPage } from './pages/SearchPage';
import { SettingsPage } from './pages/SettingsPage';
import { SubscriptionsPage } from './pages/SubscriptionsPage';
import { TrendingPage } from './pages/TrendingPage';
import { WatchPage } from './pages/WatchPage';

function useTheme() {
  const { settings } = useLibrary();
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => {
      const dark = settings.theme === 'dark' || (settings.theme === 'system' && media.matches);
      document.documentElement.dataset.theme = dark ? 'dark' : 'light';
      document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0d1320' : '#f6f7fb');
    };
    apply();
    media.addEventListener('change', apply);
    return () => media.removeEventListener('change', apply);
  }, [settings.theme]);
}

export default function App() {
  const route = useRoute();
  useTheme();

  let page;
  switch (route.name) {
    case 'home':
      page = <HomePage />;
      break;
    case 'trending':
      page = <TrendingPage category={route.category} />;
      break;
    case 'subscriptions':
      page = <SubscriptionsPage />;
      break;
    case 'library':
      page = <LibraryPage tab={route.tab} />;
      break;
    case 'settings':
      page = <SettingsPage />;
      break;
    case 'search':
      page = <SearchPage key={`${route.query}|${route.filter}`} query={route.query} filter={route.filter} />;
      break;
    case 'channel':
      page = <ChannelPage key={route.url} url={route.url} />;
      break;
    case 'watch':
      page = <WatchPage key={route.url} url={route.url} startTime={route.time} />;
      break;
  }

  return <Shell route={route}>{page}</Shell>;
}
