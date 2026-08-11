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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
    onChannelClick: (String) -> Unit = {},
    isSubscribed: (String) -> Boolean = { false },
    onToggleSubscription: (net.newpipe.app.domain.Subscription) -> Unit = {},
    isCovered: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Shared bridge between the UI (keyboard shortcuts, double-click, the
    // fullscreen button) and the platform video player.
    val playerActions = remember { PlayerActions() }

    // AWT's global dispatcher keeps shortcuts working while the native VLC
    // surface owns focus. The Compose preview handler remains as a fallback
    // for non-desktop surfaces.
    if (state is PlayerState.Playing) {
        DisposableEffect(Unit) {
            val removeHandler = installGlobalPlayerKeyHandler { key ->
                handlePlayerShortcut(key.name, playerActions)
            }
            onDispose { removeHandler() }
        }
    }

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
                var isCinema by remember { mutableStateOf(false) }
                var seekNotice by remember { mutableStateOf<String?>(null) }
                val pictureInPictureMode = PlatformPictureInPictureMode()
                val scrollState = rememberScrollState()

                PlatformPlayerBackHandler(enabled = true) {
                    if (isFullscreen) {
                        playerActions.toggleFullscreen()
                    } else {
                        playerViewModel.stop()
                    }
                }

                LaunchedEffect(seekNotice) {
                    if (seekNotice != null) {
                        kotlinx.coroutines.delay(900)
                        seekNotice = null
                    }
                }

                // Wire the shared fullscreen action (double-click / F key) to
                // this branch's fullscreen state.
                playerActions.toggleFullscreen = { isFullscreen = !isFullscreen }
                playerActions.toggleCinema = { isCinema = !isCinema }
                playerActions.reportSeek = { seconds ->
                    seekNotice = if (seconds < 0) "${-seconds}s" else "+${seconds}s"
                }

                val playNextVideo: () -> Unit = {
                    state.relatedItems.firstOrNull()?.let { next ->
                        playerViewModel.loadVideo(next.url ?: "", next.name ?: "")
                    } ?: playerViewModel.stop()
                }

                // When another overlay (e.g. the download dialog) is on top, the native
                // AWT video surface would paint above it. Hide the video surface and
                // remember the position so playback resumes where it left off.
                LaunchedEffect(isCovered) {
                    if (isCovered) playerViewModel.rememberPlaybackPosition()
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
                    val isWide = maxWidth > 1000.dp
                    val showRelated = isWide && !isFullscreen && !isCinema && !pictureInPictureMode
                    val scrollModifier = if (isFullscreen || pictureInPictureMode) {
                        Modifier
                    } else {
                        Modifier.verticalScroll(scrollState)
                    }

                    Row(modifier = Modifier.fillMaxSize().then(scrollModifier)) {

                        // Left Side (or Full Width)
                        Column(
                            modifier = Modifier
                                .weight(if (showRelated) 0.65f else 1f)
                                .padding(if (isFullscreen || pictureInPictureMode) 0.dp else 16.dp)
                        ) {

                            // Video Player. In fullscreen and PiP the video owns
                            // the entire available window; no "Now Playing" bar
                            // or details are rendered around it.
                            val playerModifier = if (isFullscreen || pictureInPictureMode) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                            }
                            Box(modifier = playerModifier.background(Color.Black)) {
                                if (!isCovered) {
                                    VideoPlayer(
                                        modifier = Modifier.fillMaxSize(),
                                        videoUrl = state.streamUrl,
                                        audioUrl = state.audioUrl,
                                        title = state.title,
                                        artistName = state.uploaderName,
                                        thumbnailUrl = state.thumbnailUrl,
                                        startPositionMs = state.resumePositionMs,
                                        onPlaybackEnded = playNextVideo,
                                        onPreviousVideo = playerViewModel::playPrevious,
                                        onNextVideo = playNextVideo,
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

                            }

                            if (!isFullscreen && !pictureInPictureMode) {
                                IconButton(
                                    onClick = { playerViewModel.stop() },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }

                                // Details & Mobile Related
                                Spacer(modifier = Modifier.height(16.dp))
                                VideoDetailsContent(
                                    state = state,
                                    playerViewModel = playerViewModel,
                                    downloadViewModel = downloadViewModel,
                                    onChannelClick = onChannelClick,
                                    isSubscribed = isSubscribed(state.uploaderUrl),
                                    onToggleSubscription = onToggleSubscription
                                )

                                if (!isWide && !isCinema) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Divider(color = Color.DarkGray)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    RelatedVideosContent(state, playerViewModel)
                                }
                            }
                        }

                        // Right Side (Desktop Related)
                        if (showRelated) {
                            Column(
                                modifier = Modifier
                                    .weight(0.35f)
                                    .padding(vertical = 16.dp, horizontal = 16.dp)
                            ) {
                                RelatedVideosContent(state, playerViewModel)
                            }
                        }
                    }

                    // YouTube-style seek feedback, visible for a short time
                    // after Left/Right or a numeric jump.
                    seekNotice?.let { notice ->
                        Text(
                            text = notice,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = if (isFullscreen) 20.dp else 84.dp)
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        )
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
        Key.Spacebar -> handlePlayerShortcut("SPACE", actions)
        Key.DirectionLeft -> handlePlayerShortcut("LEFT", actions)
        Key.DirectionRight -> handlePlayerShortcut("RIGHT", actions)
        Key.DirectionUp -> handlePlayerShortcut("UP", actions)
        Key.DirectionDown -> handlePlayerShortcut("DOWN", actions)
        Key.F -> handlePlayerShortcut("F", actions)
        Key.M -> handlePlayerShortcut("M", actions)
        Key.Zero -> handlePlayerShortcut("0", actions)
        Key.One -> handlePlayerShortcut("1", actions)
        Key.Two -> handlePlayerShortcut("2", actions)
        Key.Three -> handlePlayerShortcut("3", actions)
        Key.Four -> handlePlayerShortcut("4", actions)
        Key.Five -> handlePlayerShortcut("5", actions)
        Key.Six -> handlePlayerShortcut("6", actions)
        Key.Seven -> handlePlayerShortcut("7", actions)
        Key.Eight -> handlePlayerShortcut("8", actions)
        Key.Nine -> handlePlayerShortcut("9", actions)
        else -> false
    }
}

private fun handlePlayerShortcut(name: String, actions: PlayerActions): Boolean = when (name) {
    "SPACE" -> { actions.togglePlayPause(); true }
    "LEFT" -> { actions.seekBy(-5); true }
    "RIGHT" -> { actions.seekBy(5); true }
    "UP" -> { actions.adjustVolume(5); true }
    "DOWN" -> { actions.adjustVolume(-5); true }
    "F" -> { actions.toggleFullscreen(); true }
    "M" -> { actions.toggleMute(); true }
    "0" -> { actions.seekToFraction(0f); true }
    "1" -> { actions.seekToFraction(0.1f); true }
    "2" -> { actions.seekToFraction(0.2f); true }
    "3" -> { actions.seekToFraction(0.3f); true }
    "4" -> { actions.seekToFraction(0.4f); true }
    "5" -> { actions.seekToFraction(0.5f); true }
    "6" -> { actions.seekToFraction(0.6f); true }
    "7" -> { actions.seekToFraction(0.7f); true }
    "8" -> { actions.seekToFraction(0.8f); true }
    "9" -> { actions.seekToFraction(0.9f); true }
    else -> false
}
