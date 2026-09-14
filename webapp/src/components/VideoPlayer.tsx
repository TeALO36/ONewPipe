import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import shaka from 'shaka-player';
import { api, type Subtitle } from '../api';
import { CircularWavyProgress } from './WavyProgress';

export interface QualityOption {
  height: number;
  label: string;
}

export interface VideoPlayerHandle {
  video: HTMLVideoElement | null;
  qualities: () => QualityOption[];
  activeHeight: () => number;
  setQuality: (height: number | 'auto') => void;
  setSubtitle: (subtitle: Subtitle | null) => Promise<void>;
}

interface Props {
  url: string;
  poster: string;
  startTime: number;
  speed: number;
  loop: boolean;
  onReady: () => void;
  onTimeUpdate: (seconds: number) => void;
  onEnded: () => void;
  onError: (message: string) => void;
}

shaka.polyfill.installAll();

/**
 * DASH video player: the server builds the manifest from YouTube's separate
 * video and audio streams, and Shaka Player plays them in the native <video>.
 */
export const VideoPlayer = forwardRef<VideoPlayerHandle, Props>(function VideoPlayer(
  { url, poster, startTime, speed, loop, onReady, onTimeUpdate, onEnded, onError },
  ref
) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const shakaRef = useRef<shaka.Player | null>(null);
  const [loading, setLoading] = useState(true);
  const callbacks = useRef({ onReady, onTimeUpdate, onEnded, onError });
  callbacks.current = { onReady, onTimeUpdate, onEnded, onError };

  useImperativeHandle(ref, () => ({
    get video() {
      return videoRef.current;
    },
    qualities() {
      const tracks = shakaRef.current?.getVariantTracks() ?? [];
      const heights = [...new Set(tracks.map((t) => t.height ?? 0).filter(Boolean))].sort((a, b) => b - a);
      return heights.map((height) => ({ height, label: `${height}p` }));
    },
    activeHeight() {
      return shakaRef.current?.getVariantTracks().find((t) => t.active)?.height ?? 0;
    },
    setQuality(height) {
      const instance = shakaRef.current;
      if (!instance) return;
      if (height === 'auto') {
        instance.configure({ abr: { enabled: true } });
        return;
      }
      const track = instance
        .getVariantTracks()
        .filter((t) => t.height === height)
        .sort((a, b) => b.bandwidth - a.bandwidth)[0];
      if (!track) return;
      instance.configure({ abr: { enabled: false } });
      instance.selectVariantTrack(track, true);
    },
    async setSubtitle(subtitle) {
      const instance = shakaRef.current;
      if (!instance) return;
      // Shaka 5 shows the selected text track; selecting null turns subtitles off.
      if (!subtitle) {
        instance.selectTextTrack(null);
        return;
      }
      const existing = instance.getTextTracks().find((t) => t.language === subtitle.languageTag && t.label === subtitle.label);
      const track = existing ?? (await instance.addTextTrackAsync(subtitle.url, subtitle.languageTag, 'subtitle', subtitle.mimeType, undefined, subtitle.label));
      instance.selectTextTrack(track);
    }
  }));

  useEffect(() => {
    const video = videoRef.current!;
    const instance = new shaka.Player();
    shakaRef.current = instance;
    let cancelled = false;
    setLoading(true);

    instance.configure({ streaming: { bufferingGoal: 30, rebufferingGoal: 1.5 } });
    instance.addEventListener('error', (event) => {
      const detail = (event as unknown as { detail?: shaka.util.Error }).detail;
      callbacks.current.onError(`Playback error ${detail?.code ?? ''}`.trim());
    });

    (async () => {
      try {
        await instance.attach(video);
        await instance.load(api.manifestUrl(url), startTime > 0 ? startTime : null, 'application/dash+xml');
        if (cancelled) return;
        setLoading(false);
        callbacks.current.onReady();
        video.play().catch(() => {
          // Autoplay can be refused until the user interacts; the controls stay available.
        });
      } catch (error) {
        if (cancelled) return;
        setLoading(false);
        const code = (error as shaka.util.Error)?.code;
        callbacks.current.onError(code === 7000 ? 'Loading was interrupted' : `This video could not be played${code ? ` (error ${code})` : ''}`);
      }
    })();

    return () => {
      cancelled = true;
      shakaRef.current = null;
      void instance.destroy();
    };
  }, [url, startTime]);

  useEffect(() => {
    if (videoRef.current) videoRef.current.playbackRate = speed;
  }, [speed, loading]);

  return (
    <div className="video-frame">
      <video
        ref={videoRef}
        poster={poster}
        controls
        playsInline
        loop={loop}
        onTimeUpdate={(event) => callbacks.current.onTimeUpdate(event.currentTarget.currentTime)}
        onEnded={() => callbacks.current.onEnded()}
        onRateChange={(event) => {
          if (Math.abs(event.currentTarget.playbackRate - speed) > 0.01) event.currentTarget.playbackRate = speed;
        }}
      />
      {loading && (
        <div className="video-loading" aria-hidden="true">
          <CircularWavyProgress size={64} />
        </div>
      )}
    </div>
  );
});
