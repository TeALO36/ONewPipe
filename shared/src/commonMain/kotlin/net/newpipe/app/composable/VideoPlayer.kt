package net.newpipe.app.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayer(
    modifier: Modifier = Modifier,
    videoUrl: String,
    audioUrl: String? = null,
    startPositionMs: Long = 0L,
    onPlaybackEnded: () -> Unit = {},
    onPositionChange: (Long) -> Unit = {},
    playerActions: PlayerActions = PlayerActions()
)
