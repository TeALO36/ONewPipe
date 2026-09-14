// Loading indicators in the style of Android's recent Material 3 "expressive"
// progress: a wave travels along the track instead of a flat bar.
import { useId } from 'react';
import './WavyProgress.css';

interface LinearProps {
  /** 0..1 for a determinate bar; omit for an indeterminate one. */
  value?: number;
  label?: string;
  className?: string;
}

const WAVE_LENGTH = 20;
const AMPLITUDE = 3;

function wavePath(width: number, height: number): string {
  const mid = height / 2;
  let d = `M0 ${mid}`;
  for (let x = 0; x < width; x += WAVE_LENGTH) {
    d += ` q ${WAVE_LENGTH / 4} ${-AMPLITUDE} ${WAVE_LENGTH / 2} 0 t ${WAVE_LENGTH / 2} 0`;
  }
  return d;
}

/**
 * Linear wavy progress. The active part is a travelling wave ending with a dot;
 * the remaining track stays flat, as on Android.
 */
export function LinearWavyProgress({ value, label, className }: LinearProps) {
  const clipId = useId();
  const determinate = typeof value === 'number';
  const fraction = determinate ? Math.min(1, Math.max(0, value!)) : 0;
  const width = 400;
  const height = 12;
  const activeWidth = determinate ? width * fraction : width;
  return (
    <div
      className={`wavy-linear ${determinate ? '' : 'indeterminate'} ${className ?? ''}`}
      role="progressbar"
      aria-label={label ?? 'Loading'}
      aria-valuemin={determinate ? 0 : undefined}
      aria-valuemax={determinate ? 100 : undefined}
      aria-valuenow={determinate ? Math.round(fraction * 100) : undefined}
    >
      <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden="true">
        <defs>
          <clipPath id={clipId}>
            <rect className="wavy-clip" x="0" y="0" width={activeWidth} height={height} />
          </clipPath>
        </defs>
        {determinate && fraction < 1 && (
          <line className="wavy-track" x1={Math.min(width, activeWidth + 6)} y1={height / 2} x2={width} y2={height / 2} />
        )}
        <g clipPath={`url(#${clipId})`}>
          <path className="wavy-wave" d={wavePath(width + WAVE_LENGTH * 2, height)} />
        </g>
        {determinate && fraction > 0 && fraction < 1 && (
          <circle className="wavy-dot" cx={activeWidth} cy={height / 2} r={3} />
        )}
      </svg>
    </div>
  );
}

interface CircularProps {
  size?: number;
  label?: string;
  className?: string;
}

/** Indeterminate circular indicator whose ring ripples while it rotates. */
export function CircularWavyProgress({ size = 48, label, className }: CircularProps) {
  const points = 180;
  const radius = 18;
  const waves = 9;
  const amplitude = 1.6;
  let d = '';
  for (let i = 0; i <= points; i++) {
    const angle = (i / points) * Math.PI * 2;
    const r = radius + Math.sin(angle * waves) * amplitude;
    const x = 24 + r * Math.cos(angle);
    const y = 24 + r * Math.sin(angle);
    d += `${i === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)} `;
  }
  return (
    <svg
      className={`wavy-circular ${className ?? ''}`}
      width={size}
      height={size}
      viewBox="0 0 48 48"
      role="progressbar"
      aria-label={label ?? 'Loading'}
    >
      <path className="wavy-circular-track" d={d} />
      <path className="wavy-circular-arc" d={d} pathLength={100} />
    </svg>
  );
}
