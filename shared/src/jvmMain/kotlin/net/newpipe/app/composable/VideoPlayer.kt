package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent

@Composable
actual fun VideoPlayer(
    modifier: Modifier,
    videoUrl: String,
    audioUrl: String?,
    title: String,
    artistName: String,
    thumbnailUrl: String?,
    startPositionMs: Long,
    onPlaybackEnded: () -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    onPositionChange: (Long) -> Unit,
    playerActions: PlayerActions
) {
    // Software decoding avoids the black-frame/audio-only issue seen with the
    // vlcj callback surface on Windows. A modest network cache gets playback
    // started without the old multi-second buffer.
    val mediaPlayerComponent = remember {
        CallbackMediaPlayerComponent(
            "--no-video-title-show",
            "--avcodec-hw=none",
            "--network-caching=400",
            "--drop-late-frames"
        ).also { component ->
            // AWT heavyweight surfaces otherwise flash their default white
            // background during a Windows resize/repaint.
            component.background = java.awt.Color.BLACK
            component.isOpaque = true
            component.videoSurfaceComponent().background = java.awt.Color.BLACK
            component.videoSurfaceComponent().isOpaque = true
        }
    }
    var hasVideoFrame by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var currentTime by remember { mutableStateOf("0:00") }
    var totalTime by remember { mutableStateOf("0:00") }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(videoUrl, audioUrl) {
        val player = mediaPlayerComponent.mediaPlayer()
        val hasSeeked = java.util.concurrent.atomic.AtomicBoolean(false)

        fun safe(block: () -> Unit) {
            try {
                block()
            } catch (_: Throwable) {
                // Native player can be stopping while Compose disposes the surface.
            }
        }

        hasVideoFrame = false
        progress = 0f
        currentTime = "0:00"

        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer?) {
                isPlaying = true
                hasVideoFrame = true
                if (startPositionMs > 0 && hasSeeked.compareAndSet(false, true)) {
                    safe { player.controls().setTime(startPositionMs) }
                }
            }
            override fun paused(mediaPlayer: MediaPlayer?) { isPlaying = false }
            override fun stopped(mediaPlayer: MediaPlayer?) { isPlaying = false }
            override fun finished(mediaPlayer: MediaPlayer?) {
                isPlaying = false
                onPlaybackEnded()
            }
        })

        playerActions.togglePlayPause = {
            safe {
                if (player.status().isPlaying) player.controls().pause() else player.controls().play()
            }
        }
        playerActions.seekBy = { seconds ->
            safe {
                val length = player.status().length()
                if (length > 0) {
                    val target = (player.status().time() + seconds * 1000).coerceIn(0L, length)
                    player.controls().setTime(target)
                    progress = target.toFloat() / length.toFloat()
                    currentTime = formatTime(target)
                    playerActions.reportSeek(seconds)
                }
            }
        }
        playerActions.seekToFraction = { fraction ->
            safe {
                val length = player.status().length()
                if (length > 0) {
                    val target = (fraction * length).toLong().coerceIn(0L, length)
                    player.controls().setTime(target)
                    progress = target.toFloat() / length.toFloat()
                    currentTime = formatTime(target)
                }
            }
        }
        playerActions.adjustVolume = { delta ->
            safe {
                val current = player.audio().volume()
                player.audio().setVolume((current + delta).coerceIn(0, 100))
            }
        }
        playerActions.toggleMute = {
            safe { player.audio().setMute(!player.audio().isMute) }
        }

        // VLC accepts a second media as an input slave. This pairs a 720p/1080p
        // adaptive video-only URL with the best audio URL from NewPipe.
        safe {
            if (!audioUrl.isNullOrBlank()) {
                player.media().play(videoUrl, ":input-slave=$audioUrl")
            } else {
                player.media().play(videoUrl)
            }
        }

        val job = coroutineScope.launch {
            while (true) {
                try {
                    // Read position even while paused, so a keyboard seek or a
                    // click on the timeline is immediately reflected in the UI.
                    val time = player.status().time().coerceAtLeast(0L)
                    val length = player.status().length()
                    if (time > 0) hasVideoFrame = true
                    onPositionChange(time)
                    if (length > 0) {
                        progress = (time.toFloat() / length.toFloat()).coerceIn(0f, 1f)
                        currentTime = formatTime(time)
                        totalTime = formatTime(length)
                    }
                } catch (_: Throwable) {
                    // Ignore transient native errors during load/stop.
                }
                delay(250)
            }
        }

        onDispose {
            job.cancel()
            playerActions.togglePlayPause = {}
            playerActions.seekBy = {}
            playerActions.seekToFraction = {}
            playerActions.adjustVolume = {}
            playerActions.toggleMute = {}
            playerActions.reportSeek = {}
            safe { player.controls().stop() }
        }
    }

    // The callback component itself is not the video child on every VLCJ
    // backend. Attach to the actual video surface so a center click always
    // toggles play/pause; double-click remains the fullscreen gesture.
    DisposableEffect(Unit) {
        val surface = mediaPlayerComponent.videoSurfaceComponent()
        val mouseListener = object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount >= 2) playerActions.toggleFullscreen()
                else if (e.clickCount == 1) playerActions.togglePlayPause()
            }
        }
        surface.addMouseListener(mouseListener)
        onDispose { surface.removeMouseListener(mouseListener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayerComponent.mediaPlayer().controls().stop() } catch (_: Throwable) { }
            try { mediaPlayerComponent.mediaPlayer().release() } catch (_: Throwable) { }
        }
    }

    Column(modifier = modifier.background(Color.Black)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SwingPanel(
                factory = { mediaPlayerComponent },
                modifier = Modifier.fillMaxSize()
            )
            if (!hasVideoFrame) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White,
                        strokeWidth = 4.dp
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { playerActions.togglePlayPause() },
                modifier = Modifier.size(44.dp).background(Color.White.copy(alpha = 0.14f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(currentTime, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    val player = mediaPlayerComponent.mediaPlayer()
                    try {
                        val length = player.status().length()
                        if (length > 0) {
                            val target = (newProgress * length).toLong()
                            player.controls().setTime(target)
                            progress = newProgress
                            currentTime = formatTime(target)
                        }
                    } catch (_: Throwable) { }
                },
                modifier = Modifier.weight(1f)
            )
            Text(totalTime, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = onNextVideo) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next video", tint = Color.White)
            }
            IconButton(onClick = { playerActions.toggleCinema() }) {
                Icon(Icons.Filled.Theaters, contentDescription = "Cinema mode", tint = Color.White)
            }
            IconButton(onClick = { playerActions.toggleFullscreen() }) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = millis / (1000 * 60 * 60)
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}
