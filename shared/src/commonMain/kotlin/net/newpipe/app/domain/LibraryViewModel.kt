package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.newpipe.app.currentTimeMillis

/**
 * A video that was opened in the player.
 *
 * NewPipe keeps the watch history, local playlists and the "watch later"
 * bookmark list in a local database. ONewPipe stores the same information in
 * the multiplatform settings store so desktop, Android and iOS all share one
 * implementation.
 */
@Serializable
data class HistoryEntry(
    val url: String,
    val title: String,
    val uploaderName: String = "",
    val thumbnailUrl: String = "",
    val durationText: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val watchedAt: Long = 0
)

/** One entry of a local playlist. */
@Serializable
data class PlaylistItem(
    val url: String,
    val title: String,
    val uploaderName: String = "",
    val thumbnailUrl: String = "",
    val durationText: String = ""
)

/** A playlist created by the user on this device. */
@Serializable
data class LocalPlaylist(
    val id: String,
    val name: String,
    val items: List<PlaylistItem> = emptyList()
)

/** A file the user downloaded through the download dialog. */
@Serializable
data class DownloadRecord(
    val fileName: String,
    val sourceUrl: String,
    val title: String,
    val isAudioOnly: Boolean = false,
    val startedAt: Long = 0
)

/** Everything the backup/restore buttons write and read. */
@Serializable
data class LibraryBackup(
    val history: List<HistoryEntry> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val watchLater: List<PlaylistItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val subscriptions: List<Subscription> = emptyList()
)

/**
 * Watch history, local playlists, the watch-later list, the download list and
 * the search history.
 *
 * Everything is persisted immediately so the entries survive a restart, and
 * every list is exposed as a [StateFlow] so the UI updates without a manual
 * refresh.
 */
class LibraryViewModel(
    private val settings: Settings,
    private val clock: () -> Long = { currentTimeMillis() }
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _history = MutableStateFlow(read(KEY_HISTORY, emptyList<HistoryEntry>()))
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _playlists = MutableStateFlow(read(KEY_PLAYLISTS, emptyList<LocalPlaylist>()))
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    private val _watchLater = MutableStateFlow(read(KEY_WATCH_LATER, emptyList<PlaylistItem>()))
    val watchLater: StateFlow<List<PlaylistItem>> = _watchLater.asStateFlow()

    private val _downloads = MutableStateFlow(read(KEY_DOWNLOADS, emptyList<DownloadRecord>()))
    val downloads: StateFlow<List<DownloadRecord>> = _downloads.asStateFlow()

    private val _searchHistory = MutableStateFlow(read(KEY_SEARCH_HISTORY, emptyList<String>()))
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _historyEnabled = MutableStateFlow(settings.getBoolean(KEY_HISTORY_ENABLED, true))
    val historyEnabled: StateFlow<Boolean> = _historyEnabled.asStateFlow()

    // ---------------------------------------------------------------- history

    /** Records (or refreshes) a watched video. Newest entries come first. */
    fun recordWatch(
        url: String,
        title: String,
        uploaderName: String = "",
        thumbnailUrl: String = "",
        durationText: String = ""
    ) {
        if (url.isBlank() || !_historyEnabled.value) return
        val existing = _history.value.firstOrNull { it.url == url }
        val entry = HistoryEntry(
            url = url,
            title = title.ifBlank { existing?.title.orEmpty() },
            uploaderName = uploaderName.ifBlank { existing?.uploaderName.orEmpty() },
            thumbnailUrl = thumbnailUrl.ifBlank { existing?.thumbnailUrl.orEmpty() },
            durationText = durationText.ifBlank { existing?.durationText.orEmpty() },
            positionMs = existing?.positionMs ?: 0,
            durationMs = existing?.durationMs ?: 0,
            watchedAt = clock()
        )
        update(_history, KEY_HISTORY, (listOf(entry) + _history.value.filterNot { it.url == url }).take(MAX_HISTORY))
    }

    /** Keeps the resume position of a watched video up to date. */
    fun updatePosition(url: String, positionMs: Long, durationMs: Long) {
        if (url.isBlank() || positionMs <= 0 || !_historyEnabled.value) return
        val entries = _history.value
        val index = entries.indexOfFirst { it.url == url }
        if (index < 0) return
        val updated = entries.toMutableList()
        updated[index] = updated[index].copy(
            positionMs = positionMs,
            durationMs = if (durationMs > 0) durationMs else updated[index].durationMs,
            watchedAt = clock()
        )
        update(_history, KEY_HISTORY, updated)
    }

    fun removeFromHistory(url: String) {
        update(_history, KEY_HISTORY, _history.value.filterNot { it.url == url })
    }

    fun clearHistory() {
        update(_history, KEY_HISTORY, emptyList())
    }

    fun setHistoryEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_HISTORY_ENABLED, enabled)
        _historyEnabled.value = enabled
    }

    // -------------------------------------------------------------- playlists

    /** Creates a playlist and returns it, or returns the existing one with the same name. */
    fun createPlaylist(name: String): LocalPlaylist {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return LocalPlaylist(id = "", name = "")
        _playlists.value.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        val playlist = LocalPlaylist(id = "pl-${clock()}-${_playlists.value.size}", name = trimmed)
        update(_playlists, KEY_PLAYLISTS, _playlists.value + playlist)
        return playlist
    }

    fun renamePlaylist(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update(_playlists, KEY_PLAYLISTS, _playlists.value.map { if (it.id == id) it.copy(name = trimmed) else it })
    }

    fun deletePlaylist(id: String) {
        update(_playlists, KEY_PLAYLISTS, _playlists.value.filterNot { it.id == id })
    }

    /** Adds an item to a playlist, ignoring duplicates. */
    fun addToPlaylist(id: String, item: PlaylistItem) {
        if (item.url.isBlank()) return
        update(
            _playlists,
            KEY_PLAYLISTS,
            _playlists.value.map { playlist ->
                if (playlist.id != id || playlist.items.any { it.url == item.url }) {
                    playlist
                } else {
                    playlist.copy(items = playlist.items + item)
                }
            }
        )
    }

    fun removeFromPlaylist(id: String, url: String) {
        update(
            _playlists,
            KEY_PLAYLISTS,
            _playlists.value.map { playlist ->
                if (playlist.id != id) playlist else playlist.copy(items = playlist.items.filterNot { it.url == url })
            }
        )
    }

    // ------------------------------------------------------------ watch later

    fun isInWatchLater(url: String): Boolean = _watchLater.value.any { it.url == url }

    /** Adds the item to "watch later", or removes it when it is already there. */
    fun toggleWatchLater(item: PlaylistItem) {
        if (item.url.isBlank()) return
        val updated = if (isInWatchLater(item.url)) {
            _watchLater.value.filterNot { it.url == item.url }
        } else {
            _watchLater.value + item
        }
        update(_watchLater, KEY_WATCH_LATER, updated)
    }

    fun clearWatchLater() {
        update(_watchLater, KEY_WATCH_LATER, emptyList())
    }

    // -------------------------------------------------------------- downloads

    fun recordDownload(fileName: String, sourceUrl: String, title: String, isAudioOnly: Boolean) {
        if (fileName.isBlank()) return
        val record = DownloadRecord(
            fileName = fileName,
            sourceUrl = sourceUrl,
            title = title,
            isAudioOnly = isAudioOnly,
            startedAt = clock()
        )
        update(
            _downloads,
            KEY_DOWNLOADS,
            (listOf(record) + _downloads.value.filterNot { it.fileName == fileName }).take(MAX_DOWNLOADS)
        )
    }

    fun removeDownloadRecord(fileName: String) {
        update(_downloads, KEY_DOWNLOADS, _downloads.value.filterNot { it.fileName == fileName })
    }

    fun clearDownloads() {
        update(_downloads, KEY_DOWNLOADS, emptyList())
    }

    // --------------------------------------------------------- search history

    fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || !_historyEnabled.value) return
        update(
            _searchHistory,
            KEY_SEARCH_HISTORY,
            (listOf(trimmed) + _searchHistory.value.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(MAX_SEARCH_HISTORY)
        )
    }

    fun removeSearch(query: String) {
        update(_searchHistory, KEY_SEARCH_HISTORY, _searchHistory.value.filterNot { it == query })
    }

    fun clearSearchHistory() {
        update(_searchHistory, KEY_SEARCH_HISTORY, emptyList())
    }

    // ------------------------------------------------------- backup / restore

    /**
     * Serializes the whole local library, so it can be copied somewhere safe and
     * restored later (NewPipe calls this "backup and restore").
     */
    fun exportBackup(subscriptions: List<Subscription> = emptyList()): String =
        json.encodeToString(
            LibraryBackup(
                history = _history.value,
                playlists = _playlists.value,
                watchLater = _watchLater.value,
                searchHistory = _searchHistory.value,
                subscriptions = subscriptions
            )
        )

    /**
     * Restores a backup produced by [exportBackup] and returns the subscriptions
     * it contained, or null when the text is not a valid backup. Nothing is
     * written when parsing fails.
     */
    fun importBackup(backupJson: String): List<Subscription>? {
        val backup = runCatching { json.decodeFromString<LibraryBackup>(backupJson) }.getOrNull()
            ?: return null
        update(_history, KEY_HISTORY, backup.history.take(MAX_HISTORY))
        update(_playlists, KEY_PLAYLISTS, backup.playlists)
        update(_watchLater, KEY_WATCH_LATER, backup.watchLater)
        update(_searchHistory, KEY_SEARCH_HISTORY, backup.searchHistory.take(MAX_SEARCH_HISTORY))
        return backup.subscriptions
    }

    /** Replaces the synchronized parts of the library after a server sync. */
    fun replaceLibrary(
        playlists: List<LocalPlaylist>,
        watchLater: List<PlaylistItem>,
        history: List<HistoryEntry> = _history.value
    ) {
        update(_playlists, KEY_PLAYLISTS, playlists)
        update(_watchLater, KEY_WATCH_LATER, watchLater.distinctBy { it.url })
        update(
            _history,
            KEY_HISTORY,
            history.distinctBy { it.url }.sortedByDescending { it.watchedAt }.take(MAX_HISTORY)
        )
    }

    // ----------------------------------------------------------------- shared

    private inline fun <reified T> read(key: String, fallback: List<T>): List<T> =
        runCatching { json.decodeFromString<List<T>>(settings.getString(key, "[]")) }.getOrDefault(fallback)

    private inline fun <reified T> update(flow: MutableStateFlow<List<T>>, key: String, value: List<T>) {
        settings.putString(key, json.encodeToString(value))
        flow.value = value
    }

    companion object {
        const val KEY_HISTORY = "library_history"
        const val KEY_PLAYLISTS = "library_playlists"
        const val KEY_WATCH_LATER = "library_watch_later"
        const val KEY_DOWNLOADS = "library_downloads"
        const val KEY_SEARCH_HISTORY = "library_search_history"
        const val KEY_HISTORY_ENABLED = "library_history_enabled"
        const val MAX_HISTORY = 500
        const val MAX_DOWNLOADS = 200
        const val MAX_SEARCH_HISTORY = 30
    }
}
