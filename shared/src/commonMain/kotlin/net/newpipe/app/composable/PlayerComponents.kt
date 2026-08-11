package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.domain.DownloadViewModel
import net.newpipe.app.domain.PlayerState
import net.newpipe.app.domain.PlayerViewModel
import net.newpipe.app.domain.Subscription

@Composable
fun VideoDetailsContent(
    state: PlayerState.Playing,
    playerViewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel,
    onChannelClick: (String) -> Unit = {},
    isSubscribed: Boolean = false,
    onToggleSubscription: (Subscription) -> Unit = {}
) {
    // Title & Views
    Text(text = state.title, color = Color.White, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = "${java.text.NumberFormat.getInstance().format(state.viewCount)} views", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Channel Info
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (state.uploaderUrl.isNotBlank()) Modifier.clickable { onChannelClick(state.uploaderUrl) } else Modifier),
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
            onClick = {
                if (state.uploaderUrl.isNotBlank()) {
                    onToggleSubscription(
                        Subscription(
                            url = state.uploaderUrl,
                            name = state.uploaderName,
                            thumbnailUrl = ""
                        )
                    )
                }
            },
            enabled = state.uploaderUrl.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSubscribed) MaterialTheme.colorScheme.primary else Color.White,
                contentColor = if (isSubscribed) MaterialTheme.colorScheme.onPrimary else Color.Black
            )
        ) {
            Text(if (isSubscribed) "Subscribed" else "Subscribe")
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
        val qualityProfiles = remember(state.videoStreams, state.videoOnlyStreams, state.audioStreams) {
            buildQualityProfiles(state)
        }

        // Fetch optional HD/4K formats after the popup is visible. Starting
        // extractor work in the button callback made the Android popup race
        // with a state update and could crash the Compose window.
        LaunchedEffect(expandedQuality, state.originalUrl) {
            if (expandedQuality) {
                kotlinx.coroutines.delay(150)
                playerViewModel.loadFullQuality(state.originalUrl)
            }
        }
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
                offset = DpOffset(0.dp, 8.dp),
                modifier = Modifier
                    .width(250.dp)
                    .heightIn(max = 360.dp)
                    .background(Color(0xFF2D2D2D))
            ) {
                if (qualityProfiles.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No video quality available", color = Color.LightGray) },
                        onClick = { expandedQuality = false }
                    )
                } else {
                    qualityProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(profile.label, color = Color.White)
                                    Text(
                                        profile.description,
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            onClick = {
                                playerViewModel.changeQuality(profile.videoUrl, profile.audioUrl)
                                expandedQuality = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class QualityCandidate(
    val bucket: Int,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String?,
    val adaptive: Boolean
)

private data class QualityProfile(
    val label: String,
    val description: String,
    val videoUrl: String,
    val audioUrl: String?
)

/**
 * Collapse every extractor format into at most five user-facing profiles.
 * YouTube exposes many codecs/bitrates for the same resolution; users should
 * choose 360p/480p/720p/1080p, not inspect every raw stream.
 */
private fun buildQualityProfiles(state: PlayerState.Playing): List<QualityProfile> = runCatching {
    val bestAudio = state.audioStreams.maxByOrNull { it.averageBitrate }
    val candidates = buildList {
        state.videoOnlyStreams.forEach { stream ->
            val videoUrl = stream.content ?: stream.url
            if (!videoUrl.isNullOrBlank()) {
                val height = resolutionHeight(stream.resolution)
                add(
                    QualityCandidate(
                        bucket = qualityBucket(height),
                        height = height,
                        videoUrl = videoUrl,
                        audioUrl = bestAudio?.content ?: bestAudio?.url,
                        adaptive = true
                    )
                )
            }
        }
        state.videoStreams.forEach { stream ->
            val videoUrl = stream.content ?: stream.url
            if (!videoUrl.isNullOrBlank()) {
                val height = resolutionHeight(stream.resolution)
                add(
                    QualityCandidate(
                        bucket = qualityBucket(height),
                        height = height,
                        videoUrl = videoUrl,
                        audioUrl = null,
                        adaptive = false
                    )
                )
            }
        }
    }

    candidates
        .filter { it.height > 0 }
        .groupBy { it.bucket }
        .values
        .mapNotNull { group ->
            // Prefer adaptive streams for HD, then the highest resolution in
            // the profile. This removes duplicate 1080p/360p codec variants.
            val selected = group.maxWithOrNull(
                compareBy<QualityCandidate> { it.height }
                    .thenBy { if (it.adaptive) 1 else 0 }
            ) ?: return@mapNotNull null
            val profileName = when (selected.bucket) {
                240 -> "Low"
                360 -> "Standard"
                480 -> "Enhanced"
                720 -> "HD"
                else -> "Full HD"
            }
            QualityProfile(
                label = "${selected.height}p · $profileName",
                description = if (selected.adaptive) "Video + audio" else "Progressive video",
                videoUrl = selected.videoUrl,
                audioUrl = selected.audioUrl
            )
        }
        .sortedBy { qualityBucket(resolutionHeight(it.label)) }
        .take(5)
}.getOrDefault(emptyList())

private fun qualityBucket(height: Int): Int = when {
    height <= 240 -> 240
    height <= 360 -> 360
    height <= 480 -> 480
    height <= 720 -> 720
    else -> 1080
}

private fun resolutionHeight(value: String?): Int =
    value.orEmpty().filter { it.isDigit() }.toIntOrNull() ?: 0

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
