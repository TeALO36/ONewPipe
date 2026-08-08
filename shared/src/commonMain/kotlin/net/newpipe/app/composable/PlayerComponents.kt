package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.domain.DownloadViewModel
import net.newpipe.app.domain.PlayerState
import net.newpipe.app.domain.PlayerViewModel

@Composable
fun VideoDetailsContent(
    state: PlayerState.Playing,
    playerViewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel
) {
    // Title & Views
    Text(text = state.title, color = Color.White, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = "${java.text.NumberFormat.getInstance().format(state.viewCount)} views", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Channel Info
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).background(Color.DarkGray, shape = CircleShape), contentAlignment = Alignment.Center) {
            Text(state.uploaderName.take(1).uppercase(), color = Color.White)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = state.uploaderName, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(text = "${java.text.NumberFormat.getInstance().format(state.uploaderSubscriberCount)} subscribers", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
        ) {
            Text("Subscribe")
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Action Bar
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val clipboardManager = LocalClipboardManager.current
        var shareText by remember { mutableStateOf("Share") }
        
        OutlinedButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(state.originalUrl))
                shareText = "Copied!"
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(shareText)
        }
        
        OutlinedButton(
            onClick = { downloadViewModel.loadStreams(state.originalUrl, state.title) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download")
        }
        
        var expandedQuality by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { expandedQuality = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Quality")
            }
            DropdownMenu(
                expanded = expandedQuality,
                onDismissRequest = { expandedQuality = false },
                modifier = Modifier.background(Color.DarkGray)
            ) {
                state.videoStreams.forEach { stream ->
                    DropdownMenuItem(
                        text = { Text("${stream.resolution} (${stream.format?.name})", color = Color.White) },
                        onClick = {
                            playerViewModel.changeQuality(stream.content ?: "")
                            expandedQuality = false
                        }
                    )
                }
                state.audioStreams.forEach { stream ->
                    DropdownMenuItem(
                        text = { Text("Audio Only (${stream.format?.name})", color = Color.White) },
                        onClick = {
                            playerViewModel.changeQuality(stream.content ?: "")
                            expandedQuality = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RelatedVideosContent(
    state: PlayerState.Playing,
    playerViewModel: PlayerViewModel
) {
    Text("Related Videos", color = Color.White, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(12.dp))
    
    state.relatedItems.forEach { item ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable {
                playerViewModel.loadVideo(item.url ?: "", item.name ?: "")
            }
        ) {
            Box(modifier = Modifier.width(160.dp).aspectRatio(16f/9f).background(Color.DarkGray, shape = RoundedCornerShape(8.dp))) {
                AsyncImage(
                    model = item.thumbnails?.firstOrNull()?.url ?: "",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name ?: "", color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Text(item.uploaderName ?: "", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
