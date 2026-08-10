package net.newpipe.app.composable

import android.net.Uri
import android.view.TextureView
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun VideoPlayer(
    modifier: Modifier,
    videoUrl: String,
    audioUrl: String?,
    startPositionMs: Long,
    onPlaybackEnded: () -> Unit,
    onPositionChange: (Long) -> Unit,
    playerActions: PlayerActions
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val textureView = remember(videoUrl, audioUrl) { TextureView(context) }

    val exoPlayer = remember(videoUrl, audioUrl) {
        ExoPlayer.Builder(context).build().apply {
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
            val videoSource = mediaSourceFactory.createMediaSource(
                MediaItem.fromUri(Uri.parse(videoUrl))
            )
            val source = if (!audioUrl.isNullOrBlank()) {
                val audioSource = mediaSourceFactory.createMediaSource(
                    MediaItem.fromUri(Uri.parse(audioUrl))
                )
                MergingMediaSource(videoSource, audioSource)
            } else {
                videoSource
            }
            setMediaSource(source)
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

    DisposableEffect(exoPlayer, textureView) {
        // Do not use Media3 PlayerView here: the legacy Android module also
        // contains com.google.android.exoplayer2 resources with the same names,
        // which makes PlayerView inflate the wrong AspectRatioFrameLayout.
        exoPlayer.setVideoTextureView(textureView)

        playerActions.togglePlayPause = {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
        playerActions.seekBy = { seconds ->
            val duration = exoPlayer.duration.coerceAtLeast(0L)
            if (duration > 0) {
                exoPlayer.seekTo(
                    (exoPlayer.currentPosition + seconds * 1000)
                        .coerceIn(0L, duration)
                )
                playerActions.reportSeek(seconds)
            }
        }
        playerActions.seekToFraction = { fraction ->
            val duration = exoPlayer.duration.coerceAtLeast(0L)
            if (duration > 0) {
                exoPlayer.seekTo((fraction * duration).toLong().coerceIn(0L, duration))
            }
        }
        playerActions.adjustVolume = { delta ->
            exoPlayer.volume = (exoPlayer.volume + delta / 100f).coerceIn(0f, 1f)
        }
        playerActions.toggleMute = {
            exoPlayer.volume = if (exoPlayer.volume > 0f) 0f else 1f
        }

        val job = coroutineScope.launch {
            while (true) {
                onPositionChange(exoPlayer.currentPosition)
                delay(250)
            }
        }
        onDispose {
            job.cancel()
            exoPlayer.clearVideoTextureView(textureView)
            playerActions.togglePlayPause = {}
            playerActions.seekBy = {}
            playerActions.seekToFraction = {}
            playerActions.adjustVolume = {}
            playerActions.toggleMute = {}
            playerActions.reportSeek = {}
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { textureView },
        modifier = modifier
    )
}
