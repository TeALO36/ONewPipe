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
            modifier = Modifier.padding(16.dp).widthIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(text = "Download: $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    Text("Video Formats", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(videoStreams) { stream ->
                            val res = stream.resolution
                            val format = stream.format?.name ?: "Unknown"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDownloadVideo(stream) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$res - $format")
                                Text("Download", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Audio Formats", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(audioStreams) { stream ->
                            val kbps = stream.averageBitrate
                            val format = stream.format?.name ?: "Unknown"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDownloadAudio(stream) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${kbps}kbps - $format")
                                Text("Download", color = MaterialTheme.colorScheme.secondary)
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
