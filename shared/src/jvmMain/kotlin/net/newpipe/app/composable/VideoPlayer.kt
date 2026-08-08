package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    onPlaybackEnded: () -> Unit
) {
    val mediaPlayerComponent = remember { CallbackMediaPlayerComponent() }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var currentTime by remember { mutableStateOf("0:00") }
    var totalTime by remember { mutableStateOf("0:00") }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(videoUrl) {
        val player = mediaPlayerComponent.mediaPlayer()

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

        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer?) { isPlaying = true }
            override fun paused(mediaPlayer: MediaPlayer?) { isPlaying = false }
            override fun stopped(mediaPlayer: MediaPlayer?) { isPlaying = false }
            override fun finished(mediaPlayer: MediaPlayer?) { 
                isPlaying = false 
                onPlaybackEnded()
            }
        })

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
                        val length = player.status().length()
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
            // Only stop playback here: the player is deliberately NOT released so that
            // switching videos (or quality) can reuse the same native player instance.
            safe { player.controls().stop() }
        }
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
        SwingPanel(
            factory = { mediaPlayerComponent },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        
        // Video Controls
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                val player = mediaPlayerComponent.mediaPlayer()
                if (player.status().isPlaying) player.controls().pause() else player.controls().play()
            }) {
                Text(if (isPlaying) "⏸" else "▶", color = Color.White, style = MaterialTheme.typography.titleLarge)
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
