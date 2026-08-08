package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import net.newpipe.app.domain.DownloadState
import net.newpipe.app.domain.DownloadViewModel

/**
 * Overlay shown while streams are loading or when the user picks a format to
 * download. Uses the platform-specific [downloadFile] helper under the hood.
 */
@Composable
fun DownloadOverlay(
    state: DownloadState,
    downloadViewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    when (state) {
        is DownloadState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is DownloadState.Ready -> {
            DownloadDialog(
                videoStreams = state.videoStreams,
                videoOnlyStreams = state.videoOnlyStreams,
                audioStreams = state.audioStreams,
                title = state.title,
                onDismiss = { downloadViewModel.dismiss() },
                onDownloadVideo = { stream ->
                    // The actual media URL lives in `content`; `url` is often null
                    // for direct streams, which silently skipped downloads before.
                    val url = stream.content ?: stream.url
                    if (url != null) {
                        val safeTitle = state.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val ext = if (stream.format?.name?.contains("webm", ignoreCase = true) == true) {
                            ".webm"
                        } else {
                            ".mp4"
                        }
                        net.newpipe.app.backend.downloadFile(url, safeTitle + ext)
                    }
                    downloadViewModel.dismiss()
                },
                onDownloadAudio = { stream ->
                    val url = stream.content ?: stream.url
                    if (url != null) {
                        val safeTitle = state.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val ext = when {
                            stream.format?.name?.contains("webm", ignoreCase = true) == true -> ".webm"
                            stream.format?.name?.contains("m4a", ignoreCase = true) == true -> ".m4a"
                            else -> ".mp3"
                        }
                        net.newpipe.app.backend.downloadFile(url, safeTitle + ext)
                    }
                    downloadViewModel.dismiss()
                }
            )
        }
        is DownloadState.Error -> {
            AlertDialog(
                onDismissRequest = { downloadViewModel.dismiss() },
                title = { Text("Download Error") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { downloadViewModel.dismiss() }) {
                        Text("Close")
                    }
                }
            )
        }
        else -> {}
    }
}
