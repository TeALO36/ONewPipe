package net.newpipe.app.composable

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun VideoPlayer(
    modifier: Modifier,
    videoUrl: String,
    startPositionMs: Long,
    onPlaybackEnded: () -> Unit,
    onPositionChange: (Long) -> Unit,
    playerActions: PlayerActions
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            setMediaItem(mediaItem)
            if (startPositionMs > 0) seekTo(startPositionMs)
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onPlaybackEnded()
                }
            })
        }
    }

    DisposableEffect(Unit) {
        // Wire the shared player actions (e.g. keyboard shortcuts on devices
        // with a keyboard) to this ExoPlayer instance.
        playerActions.togglePlayPause = {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
        playerActions.seekBy = { seconds ->
            val duration = exoPlayer.duration.coerceAtLeast(0L)
            if (duration > 0) {
                exoPlayer.seekTo((exoPlayer.currentPosition + seconds * 1000).coerceIn(0L, duration))
            }
        }
        playerActions.seekToFraction = { fraction ->
            val duration = exoPlayer.duration.coerceAtLeast(0L)
            if (duration > 0) {
                exoPlayer.seekTo((fraction * duration).toLong().coerceIn(0L, duration))
            }
        }
        playerActions.adjustVolume = { delta ->
            val target = exoPlayer.volume + delta / 100f
            exoPlayer.volume = target.coerceIn(0f, 1f)
        }
        playerActions.toggleMute = {
            exoPlayer.volume = if (exoPlayer.volume > 0f) 0f else 1f
        }

        val job = coroutineScope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    onPositionChange(exoPlayer.currentPosition)
                }
                delay(500)
            }
        }
        onDispose {
            job.cancel()
            playerActions.togglePlayPause = {}
            playerActions.seekBy = {}
            playerActions.seekToFraction = {}
            playerActions.adjustVolume = {}
            playerActions.toggleMute = {}
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}
