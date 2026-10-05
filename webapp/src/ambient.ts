// Video ambient mode: the whole interface becomes a translucent window over a
// softly blown-up copy of the video frame, like the apps' ambient mode. The
// video itself stays opaque. Painting is throttled (~20 fps) and paused
// automatically so it stays cheap: the frame is sampled at reduced resolution,
// skipped while the page is hidden or the video paused, and skipped while the
// user scrolls fast, so the backdrop never competes with interaction.
import { useEffect } from 'react';
import { useLibrary } from './library';

// Sample at reduced resolution; the CSS blur hides the missing detail and the
// drawImage + filter cost stays tiny even for a 4K video.
const SAMPLE_MAX_WIDTH = 320;
const FRAME_INTERVAL_MS = 50; // ~20 fps is plenty for a blurred backdrop.
// While scrolling faster than this (px/s), painting is skipped for a moment.
const SCROLL_SPEED_THRESHOLD = 1200;
const SCROLL_IDLE_MS = 200;

export function useAmbientVideo(video: HTMLVideoElement | null, active: boolean) {
  const { settings } = useLibrary();
  const enabled = active && settings.ambientMode && !!video;

  useEffect(() => {
    if (!enabled || !video) return;

    const canvas = document.createElement('canvas');
    canvas.className = 'ambient-canvas';
    canvas.setAttribute('aria-hidden', 'true');
    document.body.append(canvas);
    const context = canvas.getContext('2d', { alpha: false });
    // The CSS turns the interface backgrounds translucent while it runs.
    document.documentElement.dataset.ambient = 'on';
    if (!context) {
      canvas.remove();
      delete document.documentElement.dataset.ambient;
      return;
    }

    let raf = 0;
    let lastDraw = 0;
    let lastY = window.scrollY;
    let lastScrollAt = performance.now();
    let scrollSpeed = 0;

    const onScroll = () => {
      const now = performance.now();
      const dt = Math.max(16, now - lastScrollAt);
      const dy = Math.abs(window.scrollY - lastY);
      scrollSpeed = scrollSpeed * 0.5 + (dy / dt) * 1000 * 0.5;
      lastY = window.scrollY;
      lastScrollAt = now;
    };
    window.addEventListener('scroll', onScroll, { passive: true });

    const loop = (now: number) => {
      raf = requestAnimationFrame(loop);
      if (now - lastDraw < FRAME_INTERVAL_MS) return;
      if (document.hidden || video.paused || video.readyState < 2 || !video.videoWidth) return;
      // Give scrolling priority; the backdrop catches up right after.
      if (now - lastScrollAt < SCROLL_IDLE_MS && scrollSpeed > SCROLL_SPEED_THRESHOLD) return;
      lastDraw = now;

      const width = Math.min(SAMPLE_MAX_WIDTH, video.videoWidth);
      const height = Math.round((width * video.videoHeight) / video.videoWidth);
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
      try {
        context.drawImage(video, 0, 0, width, height);
      } catch {
        // The frame is not decodable yet; the next tick retries.
      }
    };
    raf = requestAnimationFrame(loop);

    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener('scroll', onScroll);
      canvas.remove();
      delete document.documentElement.dataset.ambient;
    };
  }, [enabled, video]);
}
