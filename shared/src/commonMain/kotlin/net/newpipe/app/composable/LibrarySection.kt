package net.newpipe.app.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.newpipe.app.backend.DownloadProgress
import net.newpipe.app.backend.DownloadProgressBus
import net.newpipe.app.domain.HistoryEntry
import net.newpipe.app.domain.LibraryViewModel
import net.newpipe.app.domain.LocalPlaylist
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.PlaylistItem

/** The tabs of the library, mirroring the NewPipe "Bookmarked playlists / History" sections. */
enum class LibraryTab(val label: String) {
    HISTORY("History"),
    PLAYLISTS("Playlists"),
    WATCH_LATER("Watch later"),
    DOWNLOADS("Downloads")
}

/**
 * Library screen: watch history, local playlists, the watch-later list and the
 * downloads started from the download dialog.
 *
 * Every row is playable and every list has the destructive actions NewPipe
 * offers (remove one entry, clear the whole list).
 */
@Composable
fun LibrarySection(
    libraryViewModel: LibraryViewModel,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.HISTORY) }
    val history by libraryViewModel.history.collectAsState()
    val playlists by libraryViewModel.playlists.collectAsState()
    val watchLater by libraryViewModel.watchLater.collectAsState()
    val downloads by libraryViewModel.downloads.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        when (selectedTab) {
            LibraryTab.HISTORY -> HistoryTab(
                history = history,
                onPlay = onPlay,
                onDownload = onDownload,
                onRemove = libraryViewModel::removeFromHistory,
                onClear = libraryViewModel::clearHistory
            )

            LibraryTab.PLAYLISTS -> PlaylistsTab(
                playlists = playlists,
                onPlay = onPlay,
                onCreate = { libraryViewModel.createPlaylist(it) },
                onRename = libraryViewModel::renamePlaylist,
                onDelete = libraryViewModel::deletePlaylist,
                onRemoveItem = libraryViewModel::removeFromPlaylist
            )

            LibraryTab.WATCH_LATER -> WatchLaterTab(
                items = watchLater,
                onPlay = onPlay,
                onDownload = onDownload,
                onRemove = { libraryViewModel.toggleWatchLater(it) },
                onClear = libraryViewModel::clearWatchLater
            )

            LibraryTab.DOWNLOADS -> DownloadsTab(
                downloads = downloads.map { it },
                onRemove = libraryViewModel::removeDownloadRecord,
                onClear = libraryViewModel::clearDownloads
            )
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<HistoryEntry>,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    if (history.isEmpty()) {
        LibraryEmptyState(
            icon = Icons.Filled.History,
            title = "No watch history yet",
            message = "Videos you play appear here, with the position where you stopped."
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ListActionBar(count = history.size, unit = "video", onClear = onClear, clearLabel = "Clear history")
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(history, key = { it.url }) { entry ->
                val item = MediaItem(
                    url = entry.url,
                    title = entry.title,
                    uploaderName = entry.uploaderName,
                    thumbnailUrl = entry.thumbnailUrl,
                    durationText = entry.durationText
                )
                LibraryRow(
                    title = entry.title,
                    subtitle = entry.uploaderName,
                    thumbnailUrl = entry.thumbnailUrl,
                    durationText = entry.durationText,
                    progress = if (entry.durationMs > 0) {
                        (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    },
                    onClick = { onPlay(item) },
                    actions = {
                        RowOverflowMenu(
                            entries = listOf(
                                "Play" to { onPlay(item) },
                                "Download" to { onDownload(item) },
                                "Remove from history" to { onRemove(entry.url) }
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun WatchLaterTab(
    items: List<PlaylistItem>,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onRemove: (PlaylistItem) -> Unit,
    onClear: () -> Unit
) {
    if (items.isEmpty()) {
        LibraryEmptyState(
            icon = Icons.Filled.WatchLater,
            title = "Nothing saved for later",
            message = "Use the clock button on a video to keep it here."
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ListActionBar(count = items.size, unit = "video", onClear = onClear, clearLabel = "Clear list")
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(items, key = { it.url }) { entry ->
                val item = entry.toMediaItem()
                LibraryRow(
                    title = entry.title,
                    subtitle = entry.uploaderName,
                    thumbnailUrl = entry.thumbnailUrl,
                    durationText = entry.durationText,
                    onClick = { onPlay(item) },
                    actions = {
                        RowOverflowMenu(
                            entries = listOf(
                                "Play" to { onPlay(item) },
                                "Download" to { onDownload(item) },
                                "Remove" to { onRemove(entry) }
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<LocalPlaylist>,
    onPlay: (MediaItem) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRemoveItem: (String, String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LocalPlaylist?>(null) }
    var expandedPlaylistId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${playlists.size} playlist${if (playlists.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New playlist")
            }
        }

        if (playlists.isEmpty()) {
            LibraryEmptyState(
                icon = Icons.Filled.PlaylistPlay,
                title = "No playlists yet",
                message = "Create a playlist, then use \"Add to playlist\" on any video."
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    val isExpanded = expandedPlaylistId == playlist.id
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedPlaylistId = if (isExpanded) null else playlist.id }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${playlist.items.size} video${if (playlist.items.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RowOverflowMenu(
                                entries = listOfNotNull(
                                    playlist.items.firstOrNull()?.let { first ->
                                        "Play all" to { onPlay(first.toMediaItem()) }
                                    },
                                    "Rename" to { renameTarget = playlist },
                                    "Delete playlist" to { onDelete(playlist.id) }
                                )
                            )
                        }
                        if (isExpanded) {
                            playlist.items.forEach { entry ->
                                LibraryRow(
                                    title = entry.title,
                                    subtitle = entry.uploaderName,
                                    thumbnailUrl = entry.thumbnailUrl,
                                    durationText = entry.durationText,
                                    onClick = { onPlay(entry.toMediaItem()) },
                                    actions = {
                                        IconButton(onClick = { onRemoveItem(playlist.id, entry.url) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remove from playlist")
                                        }
                                    }
                                )
                            }
                            if (playlist.items.isEmpty()) {
                                Text(
                                    text = "This playlist is empty.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 36.dp, bottom = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        TextInputDialog(
            title = "New playlist",
            label = "Playlist name",
            initialValue = "",
            confirmLabel = "Create",
            onConfirm = {
                onCreate(it)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    renameTarget?.let { playlist ->
        TextInputDialog(
            title = "Rename playlist",
            label = "Playlist name",
            initialValue = playlist.name,
            confirmLabel = "Rename",
            onConfirm = {
                onRename(playlist.id, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
}

@Composable
private fun DownloadsTab(
    downloads: List<net.newpipe.app.domain.DownloadRecord>,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    if (downloads.isEmpty()) {
        LibraryEmptyState(
            icon = Icons.Filled.Download,
            title = "No downloads yet",
            message = "Files you download are listed here with their name and source."
        )
        return
    }

    val progress by DownloadProgressBus.downloads.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        ListActionBar(count = downloads.size, unit = "file", onClear = onClear, clearLabel = "Clear list")
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(downloads, key = { it.fileName }) { record ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(record.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val status = progress[record.fileName]
                        Text(
                            text = buildString {
                                append(if (record.isAudioOnly) "Audio" else "Video")
                                append(" • ")
                                append(
                                    when (status) {
                                        is DownloadProgress.Running -> "downloading ${status.percent}%"
                                        DownloadProgress.Completed -> "downloaded"
                                        is DownloadProgress.Failed -> "failed: ${status.message}"
                                        null -> record.title
                                    }
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (status is DownloadProgress.Running) {
                            LinearProgressIndicator(
                                progress = { status.percent / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(record.fileName) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove from list")
                    }
                }
            }
        }
    }
}

@Composable
private fun ListActionBar(count: Int, unit: String, clearLabel: String, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$count $unit${if (count == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = { confirmClear = true }) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(clearLabel)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(clearLabel) },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String,
    durationText: String,
    progress: Float? = null,
    onClick: () -> Unit,
    actions: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(subtitle, durationText).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        actions()
    }
}

/** Overflow menu used by the library rows; every entry runs a real action. */
@Composable
fun RowOverflowMenu(entries: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        action()
                    }
                )
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LibraryEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}

internal fun PlaylistItem.toMediaItem(): MediaItem = MediaItem(
    url = url,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationText = durationText
)

internal fun MediaItem.toPlaylistItem(): PlaylistItem = PlaylistItem(
    url = url,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationText = durationText
)
