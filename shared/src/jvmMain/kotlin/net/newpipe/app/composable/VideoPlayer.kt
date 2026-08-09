package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
    startPositionMs: Long,
    onPlaybackEnded: () -> Unit,
    onPositionChange: (Long) -> Unit,
    playerActions: PlayerActions
) {
    // libvlc options that make video output reliable on Windows desktops:
    // - --avcodec-hw=none: force software decoding. The callback video surface
    //   used by vlcj often stays black (sound but no image) with hardware
    //   acceleration enabled, which was the "black video for seconds" bug.
    // - --network-caching=400: cap the network buffer so playback starts faster.
    // - --no-video-title-show: remove libvlc's own overlay text.
    val mediaPlayerComponent = remember {
        CallbackMediaPlayerComponent(
            "--no-video-title-show",
            "--avcodec-hw=none",
            "--network-caching=400",
            "--drop-late-frames"
        )
    }
    // True once the first video frame has been rendered; drives the buffering
    // spinner so the user sees feedback instead of a silent black rectangle.
    var hasVideoFrame by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var currentTime by remember { mutableStateOf("0:00") }
    var totalTime by remember { mutableStateOf("0:00") }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(videoUrl) {
        val player = mediaPlayerComponent.mediaPlayer()
        val hasSeeked = java.util.concurrent.atomic.AtomicBoolean(false)

        // libvlc is NOT thread-safe: every native call must happen on the same thread
        // (the AWT event dispatch thread, on which Compose Desktop runs). Calling into
        // the native player from other threads - e.g. a background executor - raises
        // "Invalid memory access" java.lang.Errors that kill the whole JVM.
        fun safe(block: () -> Unit) {
            try {
                block()
            } catch (_: Throwable) {
                // The player may already have been stopped/ended natively.
            }
        }

        // New video: show the buffering spinner until the first frame arrives.
        hasVideoFrame = false

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

        // Wire the shared player actions (keyboard shortcuts, click-to-toggle)
        // to this player instance while this video is attached.
        playerActions.togglePlayPause = {
            safe { if (player.status().isPlaying) player.controls().pause() else player.controls().play() }
        }
        playerActions.seekBy = { seconds ->
            safe {
                val length = player.status().length()
                if (length > 0) {
                    val target = (player.status().time() + seconds * 1000).coerceIn(0L, length)
                    player.controls().setTime(target)
                }
            }
        }
        playerActions.seekToFraction = { fraction ->
            safe {
                val length = player.status().length()
                if (length > 0) {
                    player.controls().setTime((fraction * length).toLong().coerceIn(0L, length))
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

        safe { player.media().play(videoUrl) }

        val job = coroutineScope.launch {
            while (true) {
                val isPlayingNow = try {
                    player.status().isPlaying
                } catch (_: Throwable) {
                    false
                }
                if (isPlayingNow) {
                    try {
                        val time = player.status().time()
                        if (time > 0) hasVideoFrame = true
                        val length = player.status().length()
                        onPositionChange(time)
                        if (length > 0) {
                            progress = time.toFloat() / length.toFloat()
                            currentTime = formatTime(time)
                            totalTime = formatTime(length)
                        }
                    } catch (_: Throwable) {
                        // Ignore transient native errors; the player may be stopping.
                    }
                }
                delay(500)
            }
        }

        onDispose {
            job.cancel()
            // Unwire the shared actions so a stale video never controls the UI.
            playerActions.togglePlayPause = {}
            playerActions.seekBy = {}
            playerActions.seekToFraction = {}
            playerActions.adjustVolume = {}
            playerActions.toggleMute = {}
            // Only stop playback here: the player is deliberately NOT released so that
            // switching videos (or quality) can reuse the same native player instance.
            safe { player.controls().stop() }
        }
    }

    // Click on the video toggles play/pause, double-click toggles fullscreen
    // (YouTube behaviour). The native surface is an AWT component, so the
    // listener goes directly on it.
    DisposableEffect(Unit) {
        val mouseListener = object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount >= 2) {
                    playerActions.toggleFullscreen()
                } else if (e.clickCount == 1) {
                    playerActions.togglePlayPause()
                }
            }
        }
        mediaPlayerComponent.addMouseListener(mouseListener)
        onDispose { mediaPlayerComponent.removeMouseListener(mouseListener) }
    }

    // Release the native player once, when this composable leaves composition for good.
    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerComponent.mediaPlayer().controls().stop()
            } catch (_: Throwable) {
            }
            try {
                mediaPlayerComponent.mediaPlayer().release()
            } catch (_: Throwable) {
            }
            // Intentionally NOT releasing mediaPlayerComponent here to avoid fatal JVM
            // crash inside libvlc_media_player_set_equalizer when running on background
            // threads.
        }
    }

    Column(modifier = modifier.background(Color.Black)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SwingPanel(
                factory = { mediaPlayerComponent },
                modifier = Modifier.fillMaxSize()
            )

            // Buffering spinner while audio plays but no frame is visible yet.
            if (!hasVideoFrame) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = Color.White,
                        strokeWidth = 4.dp
                    )
                }
            }
        }
        
        // Video Controls
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Proper media button (YouTube-style): a translucent circle with a real
            // Material vector icon — never a text emoji.
            IconButton(
                onClick = {
                    val player = mediaPlayerComponent.mediaPlayer()
                    if (player.status().isPlaying) player.controls().pause() else player.controls().play()
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
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
                    val length = player.status().length()
                    if (length > 0) {
                        player.controls().setTime((newProgress * length).toLong())
                    }
                },
                modifier = Modifier.weight(1f)
            )
            
            Text(totalTime, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
