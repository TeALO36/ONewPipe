package net.newpipe.app.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

@Composable
fun DownloadDialog(
    videoStreams: List<VideoStream>,
    videoOnlyStreams: List<VideoStream>,
    audioStreams: List<AudioStream>,
    title: String,
    onDismiss: () -> Unit,
    onDownloadVideo: (VideoStream) -> Unit,
    onDownloadVideoWithAudio: (VideoStream, AudioStream) -> Unit,
    onDownloadAudio: (AudioStream) -> Unit,
    showCombinedVideoOptions: Boolean = net.newpipe.app.backend.supportsCombinedVideoDownload()
) {
    val bestAudio = audioStreams
        .filter { !(it.content ?: it.url).isNullOrBlank() }
        .maxByOrNull { it.averageBitrate }
    val highQualityVideos = if (showCombinedVideoOptions) {
        videoOnlyStreams.filter { resolutionHeight(it.resolution) >= 720 }
    } else {
        emptyList()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 420.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Download: $title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (videoStreams.isNotEmpty()) {
                        item {
                            SectionTitle("Video + audio")
                        }
                        items(videoStreams) { stream ->
                            FormatRow(
                                label = "${stream.resolution} · ${stream.format?.name ?: "video"}",
                                hint = "Ready to play",
                                onClick = { onDownloadVideo(stream) }
                            )
                        }
                    }

                    if (highQualityVideos.isNotEmpty() && bestAudio != null) {
                        item {
                            SectionTitle("High quality · packaged MP4")
                        }
                        items(highQualityVideos) { stream ->
                            FormatRow(
                                label = "${stream.resolution} · video + audio",
                                hint = "The audio track will be combined automatically",
                                onClick = { onDownloadVideoWithAudio(stream, bestAudio) }
                            )
                        }
                    }

                    if (audioStreams.isNotEmpty()) {
                        item {
                            SectionTitle("Audio only")
                        }
                        items(audioStreams) { stream ->
                            FormatRow(
                                label = "${stream.averageBitrate} kbps · ${stream.format?.name ?: "audio"}",
                                hint = "Music file",
                                onClick = { onDownloadAudio(stream) }
                            )
                        }
                    }

                    if (videoStreams.isEmpty() && highQualityVideos.isEmpty() && audioStreams.isEmpty()) {
                        item {
                            Text(
                                text = "No compatible download format was found.",
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
    )
}

@Composable
private fun FormatRow(
    label: String,
    hint: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text("Download", color = MaterialTheme.colorScheme.secondary)
    }
}

private fun resolutionHeight(value: String?): Int =
    value.orEmpty().filter { it.isDigit() }.toIntOrNull() ?: 0
