// Small shared topbar buttons (ambient quick toggle). Kept separate so Shell
// stays readable.
import { AmbientIcon } from '../icons';
import { library, useLibrary } from '../library';
import { toast } from './Toast';

export function AmbientButton() {
  const { settings } = useLibrary();
  const on = settings.ambientMode;
  return (
    <button
      type="button"
      className={`icon-button ${on ? 'active' : ''}`}
      title={on ? 'Ambient mode is on — turn it off' : 'Ambient mode: float the interface over the video'}
      aria-label={on ? 'Turn ambient mode off' : 'Turn ambient mode on'}
      aria-pressed={on}
      onClick={() => {
        library.setSettings({ ambientMode: !on });
        toast(on ? 'Ambient mode off' : 'Ambient mode on — visible while watching a video');
      }}
    >
      <AmbientIcon />
    </button>
  );
}
