package net.newpipe.app.composable

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.newpipe.app.domain.DownloadViewModel
import net.newpipe.app.domain.PlayerState
import net.newpipe.app.domain.PlayerViewModel

/**
 * Full-screen overlay shown while a video is loading, playing or in error state.
 * Contains the native video player, the details section and the related videos.
 */
@Composable
fun PlayerOverlay(
    state: PlayerState,
    playerViewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel,
    isCovered: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Shared bridge between the UI (keyboard shortcuts, double-click, the
    // fullscreen button) and the platform video player.
    val playerActions = remember { PlayerActions() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .onPreviewKeyEvent { event ->
                if (state is PlayerState.Playing) handlePlayerKey(event, playerActions) else false
            }
    ) {
        // Smooth crossfade between loading / playing / error states
        Crossfade(
            targetState = state,
            animationSpec = tween(260),
            label = "playerState"
        ) { state ->
        when (state) {
            is PlayerState.Loading -> {
                // YouTube-style skeleton while the stream is being extracted.
                PlayerSkeleton(modifier = Modifier.fillMaxSize())
            }
            is PlayerState.Playing -> {
                var isFullscreen by remember { mutableStateOf(false) }
                val scrollState = rememberScrollState()

                // Wire the shared fullscreen action (double-click / F key) to
                // this branch's fullscreen state.
                playerActions.toggleFullscreen = { isFullscreen = !isFullscreen }

                // When another overlay (e.g. the download dialog) is on top, the native
                // AWT video surface would paint above it. Hide the video surface and
                // remember the position so playback resumes where it left off.
                LaunchedEffect(isCovered) {
                    if (isCovered) playerViewModel.rememberPlaybackPosition()
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
                    val isWide = maxWidth > 1000.dp
                    val scrollModifier = if (isFullscreen) Modifier else Modifier.verticalScroll(scrollState)

                    Row(modifier = Modifier.fillMaxSize().then(scrollModifier)) {

                        // Left Side (or Full Width)
                        Column(
                            modifier = Modifier
                                .weight(if (isWide && !isFullscreen) 0.65f else 1f)
                                .padding(if (isFullscreen) 0.dp else 16.dp)
                        ) {

                            // Header
                            if (!isFullscreen) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color.Black).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { playerViewModel.stop() }) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color.White
                                        )
                                    }
                                    Text(
                                        text = "Now Playing",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Video Player
                            val playerModifier = if (isFullscreen) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            }
                            Box(modifier = playerModifier.background(Color.Black)) {
                                if (!isCovered) {
                                    VideoPlayer(
                                        modifier = Modifier.fillMaxSize(),
                                        videoUrl = state.streamUrl,
                                        startPositionMs = state.resumePositionMs,
                                        onPlaybackEnded = { playerViewModel.stop() },
                                        onPositionChange = { positionMs -> playerViewModel.onPositionUpdate(positionMs, 0L) },
                                        playerActions = playerActions
                                    )
                                } else {
                                    // Placeholder while the download dialog is open
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Download in progress…",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }

                                // Fullscreen Toggle Button — prominent, always visible
                                IconButton(
                                    onClick = { isFullscreen = !isFullscreen },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape)
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }

                            // Details & Mobile Related
                            if (!isFullscreen) {
                                Spacer(modifier = Modifier.height(16.dp))
                                VideoDetailsContent(state, playerViewModel, downloadViewModel)

                                if (!isWide) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Divider(color = Color.DarkGray)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    RelatedVideosContent(state, playerViewModel)
                                }
                            }
                        }

                        // Right Side (Desktop Related)
                        if (isWide && !isFullscreen) {
                            Column(
                                modifier = Modifier
                                    .weight(0.35f)
                                    .padding(vertical = 16.dp, horizontal = 16.dp)
                            ) {
                                RelatedVideosContent(state, playerViewModel)
                            }
                        }
                    }
                }
            }
            is PlayerState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Error: ${state.message}", color = Color.Red)
                    Button(onClick = { playerViewModel.stop() }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Close")
                    }
                }
            }
            else -> {}
        }
        }
    }
}

/**
 * YouTube-style keyboard shortcuts for the player: Space play/pause, arrows
 * (5s seek, volume), 0-9 jump to a tenth of the video, F fullscreen, M mute.
 * Returns true when the event was consumed.
 */
private fun handlePlayerKey(event: KeyEvent, actions: PlayerActions): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false

    return when (event.key) {
        Key.Spacebar -> { actions.togglePlayPause(); true }
        Key.DirectionLeft -> { actions.seekBy(-5); true }
        Key.DirectionRight -> { actions.seekBy(5); true }
        Key.DirectionUp -> { actions.adjustVolume(5); true }
        Key.DirectionDown -> { actions.adjustVolume(-5); true }
        Key.F -> { actions.toggleFullscreen(); true }
        Key.M -> { actions.toggleMute(); true }
        Key.Zero -> { actions.seekToFraction(0f); true }
        Key.One -> { actions.seekToFraction(0.1f); true }
        Key.Two -> { actions.seekToFraction(0.2f); true }
        Key.Three -> { actions.seekToFraction(0.3f); true }
        Key.Four -> { actions.seekToFraction(0.4f); true }
        Key.Five -> { actions.seekToFraction(0.5f); true }
        Key.Six -> { actions.seekToFraction(0.6f); true }
        Key.Seven -> { actions.seekToFraction(0.7f); true }
        Key.Eight -> { actions.seekToFraction(0.8f); true }
        Key.Nine -> { actions.seekToFraction(0.9f); true }
        else -> false
    }
}
