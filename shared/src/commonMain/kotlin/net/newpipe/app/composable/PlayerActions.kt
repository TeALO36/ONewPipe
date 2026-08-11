package net.newpipe.app.composable

/**
 * Live bridge between the player UI (keyboard shortcuts, fullscreen and
 * quality controls) and the platform video player instance.
 */
class PlayerActions {
    var togglePlayPause: () -> Unit = {}
    var seekBy: (Long) -> Unit = {}          // seconds, negative = backwards
    var seekToFraction: (Float) -> Unit = {} // 0f..1f of the video
    var adjustVolume: (Int) -> Unit = {}     // delta, clamped to 0..100
    var toggleMute: () -> Unit = {}
    var toggleFullscreen: () -> Unit = {}
    var togglePictureInPicture: () -> Unit = {}
    var toggleCinema: () -> Unit = {}
    var reportSeek: (Long) -> Unit = {}     // signed seconds, for on-screen feedback
}
