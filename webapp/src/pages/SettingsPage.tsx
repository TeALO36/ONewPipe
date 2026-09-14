import { useRef } from 'react';
import { toast } from '../components/Toast';
import { library, useLibrary, type Settings, type ThemeMode } from '../library';
import './SettingsPage.css';

function Switch({ checked, onChange, label, description }: { checked: boolean; onChange: (value: boolean) => void; label: string; description: string }) {
  return (
    <label className="setting">
      <span className="setting-text">
        <span className="setting-label">{label}</span>
        <span className="setting-description">{description}</span>
      </span>
      <input type="checkbox" role="switch" className="switch" checked={checked} onChange={(event) => onChange(event.target.checked)} />
    </label>
  );
}

function Choice<T extends string>({ value, options, onChange, label }: { value: T; options: { id: T; label: string }[]; onChange: (value: T) => void; label: string }) {
  return (
    <div className="setting">
      <span className="setting-text">
        <span className="setting-label">{label}</span>
      </span>
      <div className="chips" style={{ padding: 0 }}>
        {options.map((option) => (
          <button key={option.id} type="button" className={`chip ${value === option.id ? 'selected' : ''}`} aria-pressed={value === option.id} onClick={() => onChange(option.id)}>
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}

export function SettingsPage() {
  const state = useLibrary();
  const { settings } = state;
  const fileInput = useRef<HTMLInputElement>(null);
  const set = (change: Partial<Settings>) => library.setSettings(change);

  const exportBackup = () => {
    const blob = new Blob([library.exportBackup()], { type: 'application/json' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `onewpipe-backup-${new Date().toISOString().slice(0, 10)}.json`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  };

  const importBackup = async (file: File) => {
    try {
      library.importBackup(await file.text());
      toast('Backup restored');
    } catch {
      toast('This file is not an ONewPipe backup');
    }
  };

  return (
    <div className="page settings">
      <h1 className="page-title">Settings</h1>

      <section className="settings-section">
        <h2>Appearance</h2>
        <Choice<ThemeMode>
          label="Theme"
          value={settings.theme}
          onChange={(theme) => set({ theme })}
          options={[
            { id: 'system', label: 'System' },
            { id: 'light', label: 'Light' },
            { id: 'dark', label: 'Dark' }
          ]}
        />
      </section>

      <section className="settings-section">
        <h2>Playback</h2>
        <Switch label="Autoplay" description="Play the next video of the queue, or the first related video, when a video ends." checked={settings.autoplayNext} onChange={(autoplayNext) => set({ autoplayNext })} />
        <Switch label="Resume playback" description="Start a video where you stopped watching it." checked={settings.resumePlayback} onChange={(resumePlayback) => set({ resumePlayback })} />
        <Choice<Settings['preferredQuality']>
          label="Preferred quality"
          value={settings.preferredQuality}
          onChange={(preferredQuality) => set({ preferredQuality })}
          options={[
            { id: 'auto', label: 'Auto' },
            { id: '1080', label: '1080p' },
            { id: '720', label: '720p' },
            { id: '480', label: '480p' },
            { id: '360', label: '360p' }
          ]}
        />
      </section>

      <section className="settings-section">
        <h2>History</h2>
        <Switch label="Keep watch and search history" description="Stored on this device only." checked={settings.keepHistory} onChange={(keepHistory) => set({ keepHistory })} />
        <div className="setting-buttons">
          <button type="button" className="button" disabled={!state.history.length} onClick={() => { library.clearHistory(); toast('Watch history cleared'); }}>
            Clear watch history ({state.history.length})
          </button>
          <button type="button" className="button" disabled={!state.searchHistory.length} onClick={() => { library.clearSearchHistory(); toast('Search history cleared'); }}>
            Clear search history ({state.searchHistory.length})
          </button>
        </div>
      </section>

      <section className="settings-section">
        <h2>Backup</h2>
        <p className="setting-description">History, playlists, watch later, subscriptions and settings, as one JSON file.</p>
        <div className="setting-buttons">
          <button type="button" className="button" onClick={exportBackup}>
            Export backup
          </button>
          <button type="button" className="button" onClick={() => fileInput.current?.click()}>
            Restore backup
          </button>
          <input
            ref={fileInput}
            type="file"
            accept="application/json,.json"
            hidden
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) void importBackup(file);
              event.target.value = '';
            }}
          />
        </div>
      </section>

      <section className="settings-section">
        <h2>About</h2>
        <p className="setting-description">
          ONewPipe — a free, ad-free media front-end built on the NewPipe core. Released under the GNU GPL v3 or later.
        </p>
        <div className="setting-buttons">
          <a className="button" href="https://github.com/TeALO36/ONewPipe" target="_blank" rel="noreferrer">
            Project page
          </a>
          <a className="button" href="https://github.com/TeALO36/ONewPipe/blob/main/LICENSE" target="_blank" rel="noreferrer">
            License
          </a>
        </div>
      </section>
    </div>
  );
}
