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
import androidx.compose.material3.CircularProgressIndicator
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
    Box(modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))) {
        // Smooth crossfade between loading / playing / error states
        Crossfade(
            targetState = state,
            animationSpec = tween(260),
            label = "playerState"
        ) { state ->
        when (state) {
            is PlayerState.Loading -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading video…",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            is PlayerState.Playing -> {
                var isFullscreen by remember { mutableStateOf(false) }
                val scrollState = rememberScrollState()

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
                                        onPositionChange = { positionMs -> playerViewModel.onPositionUpdate(positionMs, 0L) }
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

                                // Fullscreen Toggle Button (real Material icon, not text)
                                IconButton(
                                    onClick = { isFullscreen = !isFullscreen },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(16.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                        contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                                        tint = Color.White
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
