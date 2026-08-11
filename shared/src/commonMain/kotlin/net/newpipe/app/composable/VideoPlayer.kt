package net.newpipe.app.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayer(
    modifier: Modifier = Modifier,
    videoUrl: String,
    audioUrl: String? = null,
    title: String = "",
    artistName: String = "",
    thumbnailUrl: String? = null,
    startPositionMs: Long = 0L,
    onPlaybackEnded: () -> Unit = {},
    onPreviousVideo: () -> Unit = {},
    onNextVideo: () -> Unit = {},
    onPositionChange: (Long) -> Unit = {},
    playerActions: PlayerActions = PlayerActions()
)
