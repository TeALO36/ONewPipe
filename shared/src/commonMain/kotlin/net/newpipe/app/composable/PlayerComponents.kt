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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.domain.CommentsState
import net.newpipe.app.domain.DownloadViewModel
import net.newpipe.app.domain.LibraryViewModel
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.PlayerState
import net.newpipe.app.domain.PlayerViewModel
import net.newpipe.app.domain.PlaylistItem
import net.newpipe.app.domain.RepeatMode
import net.newpipe.app.domain.Subscription
import net.newpipe.app.openExternalUrl
import net.newpipe.app.subtitlesSupported
import net.newpipe.app.shareLink

@Composable
fun VideoDetailsContent(
    state: PlayerState.Playing,
    playerViewModel: PlayerViewModel,
    downloadViewModel: DownloadViewModel,
    onChannelClick: (String) -> Unit = {},
    isSubscribed: Boolean = false,
    onToggleSubscription: (Subscription) -> Unit = {},
    libraryViewModel: LibraryViewModel? = null
) {
    // Title & Views
    Text(text = state.title, color = Color.White, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = "${formatCount(state.viewCount)} views", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Channel Info
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (state.uploaderUrl.isNotBlank()) Modifier.clickable { onChannelClick(state.uploaderUrl) } else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).background(Color.DarkGray, shape = CircleShape), contentAlignment = Alignment.Center) {
            if (state.uploaderAvatarUrl.isNotBlank()) {
                AsyncImage(
                    model = state.uploaderAvatarUrl,
                    contentDescription = state.uploaderName,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(state.uploaderName.take(1).uppercase(), color = Color.White)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = state.uploaderName, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(text = "${formatCount(state.uploaderSubscriberCount)} subscribers", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                if (state.uploaderUrl.isNotBlank()) {
                    onToggleSubscription(
                        Subscription(
                            url = state.uploaderUrl,
                            name = state.uploaderName,
                            thumbnailUrl = state.uploaderAvatarUrl
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
                // Android opens the system share sheet; elsewhere the link is
                // copied, which is the closest thing the platform offers.
                if (!shareLink(state.originalUrl, state.title)) {
                    clipboardManager.setText(AnnotatedString(state.originalUrl))
                    shareText = "Copied!"
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(shareText)
        }
        
        OutlinedButton(
            onClick = { openExternalUrl(state.originalUrl) },
            enabled = state.originalUrl.isNotBlank(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open in browser")
        }

        OutlinedButton(
            onClick = { downloadViewModel.loadStreams(state.originalUrl, state.title) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download")
        }

        val mediaItem = MediaItem(
            url = state.originalUrl,
            title = state.title,
            uploaderName = state.uploaderName,
            thumbnailUrl = state.thumbnailUrl,
            durationText = state.durationText
        )

        OutlinedButton(
            onClick = { playerViewModel.enqueue(mediaItem) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add to queue")
        }

        if (libraryViewModel != null) {
            val watchLater by libraryViewModel.watchLater.collectAsState()
            val saved = watchLater.any { it.url == state.originalUrl }
            OutlinedButton(
                onClick = {
                    libraryViewModel.toggleWatchLater(
                        PlaylistItem(
                            url = state.originalUrl,
                            title = state.title,
                            uploaderName = state.uploaderName,
                            thumbnailUrl = state.thumbnailUrl,
                            durationText = state.durationText
                        )
                    )
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(imageVector = Icons.Default.WatchLater, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (saved) "Saved" else "Watch later")
            }

            var showPlaylistPicker by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showPlaylistPicker = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to playlist")
            }
            if (showPlaylistPicker) {
                AddToPlaylistDialog(
                    libraryViewModel = libraryViewModel,
                    item = PlaylistItem(
                        url = state.originalUrl,
                        title = state.title,
                        uploaderName = state.uploaderName,
                        thumbnailUrl = state.thumbnailUrl,
                        durationText = state.durationText
                    ),
                    onDismiss = { showPlaylistPicker = false }
                )
            }
        }

        OutlinedButton(
            onClick = { playerViewModel.toggleAudioOnly() },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (state.audioOnly) MaterialTheme.colorScheme.primary else Color.White
            )
        ) {
            Icon(imageVector = Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.audioOnly) "Audio only" else "Audio mode")
        }

        // Audio tracks: YouTube exposes dubbed tracks as separate audio
        // streams, and the player already pairs a video stream with a chosen
        // audio stream, so switching language is a matter of picking one.
        val audioTracks = remember(state.audioStreams) { buildAudioTracks(state) }
        if (audioTracks.size > 1) {
            var expandedAudio by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expandedAudio = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Audio track")
                }
                DropdownMenu(
                    expanded = expandedAudio,
                    onDismissRequest = { expandedAudio = false },
                    modifier = Modifier.heightIn(max = 320.dp).background(Color(0xFF2D2D2D))
                ) {
                    audioTracks.forEach { track ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = track.label,
                                    color = if (track.url == state.audioUrl) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.White
                                    }
                                )
                            },
                            onClick = {
                                expandedAudio = false
                                playerViewModel.selectAudioTrack(track.url)
                            }
                        )
                    }
                }
            }
        }

        if (subtitlesSupported && state.subtitles.isNotEmpty()) {
            var expandedSubtitles by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expandedSubtitles = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (state.selectedSubtitle != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        }
                    )
                ) {
                    Icon(imageVector = Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.selectedSubtitle?.label ?: "Subtitles")
                }
                DropdownMenu(
                    expanded = expandedSubtitles,
                    onDismissRequest = { expandedSubtitles = false },
                    modifier = Modifier.heightIn(max = 320.dp).background(Color(0xFF2D2D2D))
                ) {
                    DropdownMenuItem(
                        text = { Text("Off", color = Color.White) },
                        onClick = {
                            expandedSubtitles = false
                            playerViewModel.selectSubtitle(null)
                        }
                    )
                    state.subtitles.forEach { track ->
                        DropdownMenuItem(
                            text = { Text(track.label, color = Color.White) },
                            onClick = {
                                expandedSubtitles = false
                                playerViewModel.selectSubtitle(track)
                            }
                        )
                    }
                }
            }
        }

        val repeatMode by playerViewModel.repeatMode.collectAsState()
        OutlinedButton(
            onClick = { playerViewModel.cycleRepeatMode() },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (repeatMode == RepeatMode.OFF) Color.White else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(repeatMode.label)
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

/** Compact "1,2M" style formatting, shared by every platform. */
internal fun formatCount(count: Long): String = when {
    count >= 1_000_000_000 -> "${count / 100_000_000 / 10.0}B"
    count >= 1_000_000 -> "${count / 100_000 / 10.0}M"
    count >= 1_000 -> "${count / 100 / 10.0}K"
    else -> count.toString()
}

/**
 * Picks the playlist a video should be added to, and allows creating a new one
 * without leaving the dialog.
 */
@Composable
fun AddToPlaylistDialog(
    libraryViewModel: LibraryViewModel,
    item: PlaylistItem,
    onDismiss: () -> Unit
) {
    val playlists by libraryViewModel.playlists.collectAsState()
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (playlists.isEmpty()) {
                    Text(
                        text = "You have no playlist yet. Create one below.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                playlists.forEach { playlist ->
                    val alreadyIn = playlist.items.any { it.url == item.url }
                    TextButton(
                        onClick = {
                            libraryViewModel.addToPlaylist(playlist.id, item)
                            onDismiss()
                        },
                        enabled = !alreadyIn,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (alreadyIn) "${playlist.name} — already added" else playlist.name,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                HorizontalDivider()
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("New playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        val created = libraryViewModel.createPlaylist(newPlaylistName)
                        if (created.id.isNotBlank()) {
                            libraryViewModel.addToPlaylist(created.id, item)
                        }
                        onDismiss()
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) { Text("Create and add") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * Collapsible description and on-demand comments, the two detail sections
 * NewPipe shows below a video.
 */
@Composable
fun VideoExtrasContent(
    state: PlayerState.Playing,
    playerViewModel: PlayerViewModel
) {
    var showDescription by remember(state.originalUrl) { mutableStateOf(false) }
    var showComments by remember(state.originalUrl) { mutableStateOf(false) }
    val commentsState by playerViewModel.comments.collectAsState()

    if (state.description.isNotBlank()) {
        OutlinedButton(
            onClick = { showDescription = !showDescription },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(if (showDescription) "Hide description" else "Show description")
        }
        if (showDescription) {
            Spacer(Modifier.height(8.dp))
            if (state.uploadDate.isNotBlank()) {
                Text(
                    text = state.uploadDate,
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = state.description,
                color = Color.LightGray,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    OutlinedButton(
        onClick = {
            showComments = !showComments
            if (showComments) playerViewModel.loadComments(state.originalUrl)
        },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Text(if (showComments) "Hide comments" else "Show comments")
    }

    if (showComments) {
        Spacer(Modifier.height(8.dp))
        when (val comments = commentsState) {
            CommentsState.Idle, CommentsState.Loading -> {
                Text("Loading comments…", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            is CommentsState.Error -> {
                Text(comments.message, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            is CommentsState.Loaded -> {
                comments.comments.forEach { comment ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            text = buildString {
                                append(comment.author)
                                if (comment.pinned) append(" • pinned")
                                if (comment.publishedAt.isNotBlank()) append(" • ${comment.publishedAt}")
                            },
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = comment.text,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (comment.likeCount > 0) {
                            Text(
                                text = "${formatCount(comment.likeCount)} likes",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class AudioTrackOption(val label: String, val url: String)

/**
 * One entry per audio language/track, keeping the highest bitrate of each.
 * A video with a single track shows no menu.
 */
private fun buildAudioTracks(state: PlayerState.Playing): List<AudioTrackOption> =
    state.audioStreams
        .mapNotNull { stream ->
            val url = stream.content ?: stream.url ?: return@mapNotNull null
            val name = stream.audioTrackName
                ?: stream.audioLocale?.displayLanguage
                ?: stream.audioTrackId
            Triple(name ?: "Default", stream.averageBitrate, url)
        }
        .groupBy { it.first }
        .map { (name, streams) ->
            val best = streams.maxByOrNull { it.second } ?: streams.first()
            AudioTrackOption(label = name, url = best.third)
        }
