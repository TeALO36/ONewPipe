package net.newpipe.app.composable

/**
 * Live bridge between the player UI (keyboard shortcuts, the fullscreen
 * button) and the platform video player instance. Each platform actual of
 * [VideoPlayer] wires the callbacks it supports; [PlayerOverlay] wires the
 * fullscreen toggle. Unwired callbacks are no-ops so shortcuts are safe at
 * any point in the player lifecycle.
 */
class PlayerActions {
    var togglePlayPause: () -> Unit = {}
    var seekBy: (Long) -> Unit = {}          // seconds, negative = backwards
    var seekToFraction: (Float) -> Unit = {} // 0f..1f of the video
    var adjustVolume: (Int) -> Unit = {}     // delta, clamped to 0..100
    var toggleMute: () -> Unit = {}
    var toggleFullscreen: () -> Unit = {}
}
