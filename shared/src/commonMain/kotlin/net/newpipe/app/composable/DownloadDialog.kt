package net.newpipe.app.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

import androidx.compose.ui.window.Dialog

@Composable
fun DownloadDialog(
    videoStreams: List<VideoStream>,
    videoOnlyStreams: List<VideoStream>,
    audioStreams: List<AudioStream>,
    title: String,
    onDismiss: () -> Unit,
    onDownloadVideo: (VideoStream) -> Unit,
    onDownloadAudio: (AudioStream) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.padding(16.dp).widthIn(max = 420.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Download: $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    if (videoStreams.isNotEmpty()) {
                        Text("Video (with audio)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(videoStreams) { stream ->
                                FormatRow(
                                    label = "${stream.resolution} - ${stream.format?.name}",
                                    hint = "Ready to play",
                                    onClick = { onDownloadVideo(stream) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    if (videoOnlyStreams.isNotEmpty()) {
                        Text("Video only (no audio)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(videoOnlyStreams) { stream ->
                                FormatRow(
                                    label = "${stream.resolution} - ${stream.format?.name}",
                                    hint = "Combine with an audio track below",
                                    onClick = { onDownloadVideo(stream) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    if (audioStreams.isNotEmpty()) {
                        Text("Audio", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(audioStreams) { stream ->
                                FormatRow(
                                    label = "${stream.averageBitrate}kbps - ${stream.format?.name}",
                                    hint = "Audio only",
                                    onClick = { onDownloadAudio(stream) }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
private fun FormatRow(
    label: String,
    hint: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text("Download", color = MaterialTheme.colorScheme.secondary)
    }
}
