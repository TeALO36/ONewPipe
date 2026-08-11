package net.newpipe.app.composable

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import net.newpipe.app.backend.MediaNotificationController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val textureView = remember(videoUrl, audioUrl) { TextureView(context) }
    var isPlaying by remember(videoUrl, audioUrl) { mutableStateOf(true) }
    var controlsVisible by remember(videoUrl, audioUrl) { mutableStateOf(true) }
    var positionMs by remember(videoUrl, audioUrl) { mutableStateOf(startPositionMs) }
    var durationMs by remember(videoUrl, audioUrl) { mutableStateOf(0L) }
    var seekFeedback by remember(videoUrl, audioUrl) { mutableStateOf<String?>(null) }
    var nativeFullscreen by remember(videoUrl, audioUrl) { mutableStateOf(false) }

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
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onPlaybackEnded()
                }
            })
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, seekFeedback) {
        if (controlsVisible && isPlaying) {
            delay(3_500)
            controlsVisible = false
            seekFeedback = null
        }
    }

    DisposableEffect(exoPlayer, textureView) {
        // Do not use Media3 PlayerView here: the legacy Android module also
        // contains com.google.android.exoplayer2 resources with the same names,
        // which makes PlayerView inflate the wrong AspectRatioFrameLayout.
        exoPlayer.setVideoTextureView(textureView)

        playerActions.togglePlayPause = {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            controlsVisible = true
        }
        playerActions.seekBy = { seconds ->
            val duration = exoPlayer.duration.coerceAtLeast(0L)
            if (duration > 0) {
                exoPlayer.seekTo(
                    (exoPlayer.currentPosition + seconds * 1000)
                        .coerceIn(0L, duration)
                )
                seekFeedback = if (seconds < 0) "${-seconds}s" else "+${seconds}s"
                controlsVisible = true
                playerActions.reportSeek(seconds)
            }
        }
        playerActions.seekToFraction = { fraction ->
            val duration = exoPlayer.duration.coerceAtLeast(0L)
            if (duration > 0) {
                exoPlayer.seekTo((fraction * duration).toLong().coerceIn(0L, duration))
                controlsVisible = true
            }
        }
        playerActions.adjustVolume = { delta ->
            exoPlayer.volume = (exoPlayer.volume + delta / 100f).coerceIn(0f, 1f)
        }
        playerActions.toggleMute = {
            exoPlayer.volume = if (exoPlayer.volume > 0f) 0f else 1f
        }
        val activity = context.findActivity()
        val parentFullscreenAction = playerActions.toggleFullscreen
        playerActions.togglePictureInPicture = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Keep only the player visible in the small PiP window.
                parentFullscreenAction()
                activity?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
        playerActions.toggleFullscreen = {
            nativeFullscreen = !nativeFullscreen
            parentFullscreenAction()
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (nativeFullscreen) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        val notificationController = MediaNotificationController(
            context = context,
            player = exoPlayer,
            onPrevious = onPreviousVideo,
            onNext = onNextVideo
        )
        notificationController.updateMetadata(title, artistName, thumbnailUrl)

        val job = coroutineScope.launch {
            while (true) {
                val currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                val currentDuration = exoPlayer.duration.coerceAtLeast(0L)
                positionMs = currentPosition
                durationMs = currentDuration
                onPositionChange(currentPosition)
                notificationController.updatePlaybackState()
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
            playerActions.togglePictureInPicture = {}
            playerActions.toggleFullscreen = {}
            notificationController.release()
            playerActions.reportSeek = {}
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { textureView },
            modifier = Modifier.fillMaxSize()
        )

        // This transparent gesture layer sits above TextureView, which otherwise
        // consumes all taps before Compose can show its controls.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(exoPlayer) {
                    val videoWidth = size.width
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val seconds = if (offset.x < videoWidth / 2f) -10L else 10L
                            playerActions.seekBy(seconds)
                            controlsVisible = true
                        },
                        onTap = {
                            // A tap on the picture is both an obvious play/pause
                            // gesture and a request to reveal the controls. The
                            // double-tap branch above wins over this callback.
                            playerActions.togglePlayPause()
                            controlsVisible = true
                        }
                    )
                }
        )

        seekFeedback?.let { feedback ->
            Text(
                text = feedback,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(if (feedback.startsWith("-")) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 28.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
                        )
                    )
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            ) {
                Slider(
                    value = if (durationMs > 0) {
                        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    onValueChange = { fraction ->
                        if (durationMs > 0) {
                            val target = (fraction * durationMs).toLong()
                            positionMs = target
                            playerActions.seekToFraction(fraction)
                        }
                        controlsVisible = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { playerActions.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = { playerActions.seekBy(-10) }) {
                            Text("−10", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { playerActions.seekBy(10) }) {
                            Text("+10", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onNextVideo) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next video",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(
                            onClick = { playerActions.togglePictureInPicture() },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ) {
                            Text("PiP", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${formatPlayerTime(positionMs)} / ${formatPlayerTime(durationMs)}",
                            color = Color.White,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    IconButton(onClick = { playerActions.toggleFullscreen() }) {
                        Icon(
                            imageVector = if (nativeFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatPlayerTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1_000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
