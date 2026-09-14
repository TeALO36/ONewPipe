import type { ReactNode } from 'react';
import {
  BackIcon,
  DarkModeIcon,
  HomeIcon,
  LibraryIcon,
  LightModeIcon,
  SettingsIcon,
  SubscriptionsIcon,
  TrendingIcon
} from '../icons';
import { library, useLibrary } from '../library';
import { goBack, routeHref, type Route } from '../router';
import { NotificationCenter } from './NotificationCenter';
import { SearchBox } from './SearchBox';
import { Toaster } from './Toast';

const NAV: { route: Route; label: string; Icon: typeof HomeIcon; matches: Route['name'][] }[] = [
  { route: { name: 'home' }, label: 'Home', Icon: HomeIcon, matches: ['home'] },
  { route: { name: 'trending', category: 'all' }, label: 'Trending', Icon: TrendingIcon, matches: ['trending'] },
  { route: { name: 'subscriptions' }, label: 'Subscriptions', Icon: SubscriptionsIcon, matches: ['subscriptions', 'channel'] },
  { route: { name: 'library', tab: 'history' }, label: 'Library', Icon: LibraryIcon, matches: ['library'] },
  { route: { name: 'settings' }, label: 'Settings', Icon: SettingsIcon, matches: ['settings'] }
];

export function BrandMark({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 128 128" aria-hidden="true">
      <rect width="128" height="128" rx="30" fill="#101827" />
      <circle cx="64" cy="64" r="39" fill="none" stroke="#50E3C2" strokeWidth="13" strokeLinecap="round" strokeDasharray="205 40" transform="rotate(-38 64 64)" />
      <path d="M54 46 85 64 54 82Z" fill="#7C5CFC" />
    </svg>
  );
}

function ThemeToggle() {
  const { settings } = useLibrary();
  const dark = document.documentElement.dataset.theme !== 'light';
  return (
    <button
      type="button"
      className="icon-button"
      title={dark ? 'Use the light theme' : 'Use the dark theme'}
      aria-label={dark ? 'Use the light theme' : 'Use the dark theme'}
      onClick={() => library.setSettings({ theme: dark ? 'light' : 'dark' })}
      data-theme-setting={settings.theme}
    >
      {dark ? <LightModeIcon /> : <DarkModeIcon />}
    </button>
  );
}

export function Shell({ route, children }: { route: Route; children: ReactNode }) {
  // The video page uses the whole width, like YouTube; back is at the top left.
  const immersive = route.name === 'watch';
  const showBack = route.name === 'watch' || route.name === 'channel' || route.name === 'search';

  return (
    <div className={`shell ${immersive ? 'no-sidebar' : ''}`}>
      <div className="brand">
        <a href={routeHref({ name: 'home' })} aria-label="ONewPipe home">
          <BrandMark />
        </a>
      </div>

      <header className="topbar">
        {showBack && (
          <button type="button" className="icon-button" aria-label="Back" title="Back" onClick={() => goBack()}>
            <BackIcon />
          </button>
        )}
        {(immersive || !showBack) && (
          <a className="topbar-brand" href={routeHref({ name: 'home' })}>
            {immersive && <BrandMark size={28} />}
            <span>ONewPipe</span>
          </a>
        )}
        <SearchBox initialQuery={route.name === 'search' ? route.query : ''} />
        <div className="topbar-actions" style={{ display: 'flex', gap: 4 }}>
          <NotificationCenter />
          <ThemeToggle />
        </div>
      </header>

      <nav className="sidebar" aria-label="Main">
        {NAV.map(({ route: target, label, Icon, matches }) => (
          <a
            key={label}
            href={routeHref(target)}
            className={`sidebar-item ${matches.includes(route.name) ? 'active' : ''}`}
            aria-current={matches.includes(route.name) ? 'page' : undefined}
          >
            <span className="sidebar-pill">
              <Icon />
            </span>
            {label}
          </a>
        ))}
      </nav>

      <main className="main" id="main">
        {children}
      </main>
      <Toaster />
    </div>
  );
}
