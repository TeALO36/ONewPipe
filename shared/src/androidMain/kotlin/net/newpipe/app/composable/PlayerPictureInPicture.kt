package net.newpipe.app.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AndroidPlayerWindowState {
    var isPictureInPicture by mutableStateOf(false)
}

@Composable
actual fun PlatformPictureInPictureMode(): Boolean =
    AndroidPlayerWindowState.isPictureInPicture
