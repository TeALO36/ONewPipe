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
import net.newpipe.app.backend.NativeDownloadRequest
import net.newpipe.app.backend.downloadWithNativeManager
import net.newpipe.app.domain.DownloadState
import net.newpipe.app.domain.DownloadViewModel

/**
 * Overlay shown while streams are loading or when the user picks a format to
 * download. Android uses NewPipe's native mission manager for pause, resume,
 * notifications, recovery and WebM/MP4 post-processing.
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
                    val videoUrl = stream.content ?: stream.url
                    if (!videoUrl.isNullOrBlank()) {
                        downloadWithNativeManager(
                            NativeDownloadRequest(
                                sourceUrl = state.sourceUrl,
                                streamInfo = state.streamInfo,
                                videoUrl = videoUrl,
                                defaultName = safeDownloadName(state.title),
                                videoResolution = stream.resolution,
                                videoFormat = stream.format,
                                videoFormatName = stream.format?.name
                            )
                        )
                    }
                    downloadViewModel.dismiss()
                },
                onDownloadVideoWithAudio = { videoStream, audioStream ->
                    val videoUrl = videoStream.content ?: videoStream.url
                    val audioUrl = audioStream.content ?: audioStream.url
                    if (!videoUrl.isNullOrBlank() && !audioUrl.isNullOrBlank()) {
                        downloadWithNativeManager(
                            NativeDownloadRequest(
                                sourceUrl = state.sourceUrl,
                                streamInfo = state.streamInfo,
                                videoUrl = videoUrl,
                                audioUrl = audioUrl,
                                defaultName = safeDownloadName(state.title),
                                videoResolution = videoStream.resolution,
                                videoFormat = videoStream.format,
                                audioFormat = audioStream.format,
                                videoFormatName = videoStream.format?.name,
                                audioFormatName = audioStream.format?.name,
                                audioBitrate = audioStream.averageBitrate
                            )
                        )
                    }
                    downloadViewModel.dismiss()
                },
                onDownloadAudio = { stream ->
                    val audioUrl = stream.content ?: stream.url
                    if (!audioUrl.isNullOrBlank()) {
                        downloadWithNativeManager(
                            NativeDownloadRequest(
                                sourceUrl = state.sourceUrl,
                                streamInfo = state.streamInfo,
                                audioUrl = audioUrl,
                                defaultName = safeDownloadName(state.title),
                                audioFormat = stream.format,
                                audioFormatName = stream.format?.name,
                                audioBitrate = stream.averageBitrate
                            )
                        )
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
        else -> Unit
    }
}

private fun safeDownloadName(title: String): String =
    title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "ONewPipe-download" }
