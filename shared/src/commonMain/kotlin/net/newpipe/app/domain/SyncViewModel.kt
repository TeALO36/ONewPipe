package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.newpipe.app.currentTimeMillis

/** Result of a manual library synchronization, shown in the settings dialog. */
sealed class LibrarySyncState {
    object Idle : LibrarySyncState()
    object Syncing : LibrarySyncState()
    data class Done(
        val subscriptions: Int,
        val playlists: Int,
        val watchLater: Int,
        val history: Int
    ) : LibrarySyncState()
    data class Failed(val message: String) : LibrarySyncState()
}

sealed class ServerStatus {
    object Disconnected : ServerStatus()
    object Connecting : ServerStatus()
    data class Connected(val username: String, val serverUrl: String) : ServerStatus()
    data class Error(val message: String) : ServerStatus()
}

/**
 * Owns the connection to a self-hosted ONewPipe server (accounts + watch sync).
 * The token/username/serverUrl are persisted via [SettingsViewModel].
 */
class SyncViewModel(
    private val settingsViewModel: SettingsViewModel,
    private val client: ServerClient = ServerClient()
) : ViewModel() {

    private val _status = MutableStateFlow<ServerStatus>(
        if (settingsViewModel.serverConfig.value.isConnected) {
            ServerStatus.Connected(settingsViewModel.serverConfig.value.username, settingsViewModel.serverConfig.value.serverUrl)
        } else {
            ServerStatus.Disconnected
        }
    )
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _librarySync = MutableStateFlow<LibrarySyncState>(LibrarySyncState.Idle)
    val librarySync: StateFlow<LibrarySyncState> = _librarySync.asStateFlow()

    fun connect(serverUrl: String, username: String, password: String, register: Boolean) {
        if (_status.value is ServerStatus.Connecting) return
        viewModelScope.launch {
            _status.value = ServerStatus.Connecting
            try {
                val normalizedUrl = client.normalizeServerUrl(serverUrl)
                val result = if (register) {
                    client.register(normalizedUrl, username, password)
                } else {
                    client.login(normalizedUrl, username, password)
                }
                settingsViewModel.setServerConfig(
                    ServerConfig(
                        serverUrl = normalizedUrl,
                        username = result.username,
                        token = result.token
                    )
                )
                _status.value = ServerStatus.Connected(result.username, normalizedUrl)
            } catch (e: Exception) {
                _status.value = ServerStatus.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun disconnect() {
        settingsViewModel.setServerConfig(ServerConfig())
        _status.value = ServerStatus.Disconnected
    }

    /** Push local watch positions to the server (no-op when not connected). */
    suspend fun pushWatchState(items: List<WatchStateItem>): Int {
        val config = settingsViewModel.serverConfig.value
        if (!config.isConnected || items.isEmpty()) return 0
        return client.pushWatchState(config, items)
    }

    /**
     * Merges the local library with the one stored on the server and writes the
     * result on both sides. Entries are merged by URL, so a device that was
     * offline never loses what it added while the other device was in use.
     */
    fun syncLibrary(libraryViewModel: LibraryViewModel, settings: SettingsViewModel) {
        val config = settingsViewModel.serverConfig.value
        if (!config.isConnected) {
            _librarySync.value = LibrarySyncState.Failed("Connect a server account first")
            return
        }
        if (_librarySync.value is LibrarySyncState.Syncing) return
        _librarySync.value = LibrarySyncState.Syncing
        viewModelScope.launch {
            try {
                val remote = client.pullLibrary(config)
                val merged = LibrarySnapshot(
                    subscriptions = (settings.subscriptions.value + remote.subscriptions).distinctBy { it.url },
                    playlists = mergePlaylists(libraryViewModel.playlists.value, remote.playlists),
                    watchLater = (libraryViewModel.watchLater.value + remote.watchLater).distinctBy { it.url },
                    history = mergeHistory(libraryViewModel.history.value, remote.history),
                    updatedAt = currentTimeMillis()
                )
                val stored = client.pushLibrary(config, merged)
                settings.replaceSubscriptions(stored.subscriptions)
                libraryViewModel.replaceLibrary(stored.playlists, stored.watchLater, stored.history)
                _librarySync.value = LibrarySyncState.Done(
                    subscriptions = stored.subscriptions.size,
                    playlists = stored.playlists.size,
                    watchLater = stored.watchLater.size,
                    history = stored.history.size
                )
            } catch (e: Exception) {
                _librarySync.value = LibrarySyncState.Failed(e.message ?: "Library sync failed")
            }
        }
    }

    /**
     * History entries are merged by URL, keeping the most recently watched
     * copy, so the resume position of the device used last wins.
     */
    private fun mergeHistory(
        local: List<HistoryEntry>,
        remote: List<HistoryEntry>
    ): List<HistoryEntry> = (local + remote)
        .groupBy { it.url }
        .map { (_, entries) -> entries.maxByOrNull { it.watchedAt } ?: entries.first() }
        .sortedByDescending { it.watchedAt }
        .take(MAX_SYNCED_HISTORY)

    /** Playlists with the same name are one playlist; their entries are merged. */
    private fun mergePlaylists(
        local: List<LocalPlaylist>,
        remote: List<LocalPlaylist>
    ): List<LocalPlaylist> {
        val byName = linkedMapOf<String, LocalPlaylist>()
        (local + remote).forEach { playlist ->
            val key = playlist.name.lowercase()
            val existing = byName[key]
            byName[key] = if (existing == null) {
                playlist
            } else {
                existing.copy(items = (existing.items + playlist.items).distinctBy { it.url })
            }
        }
        return byName.values.toList()
    }

    private companion object {
        /** Keep the synchronized history small enough for a self-hosted server. */
        const val MAX_SYNCED_HISTORY = 300
    }

    /** Pull the watch position for one video URL, or null when not connected / unknown. */
    suspend fun resumePositionFor(url: String): WatchStateItem? {
        val config = settingsViewModel.serverConfig.value
        if (!config.isConnected) return null
        return runCatching { client.pullWatchState(config) }
            .getOrNull()
            ?.firstOrNull { it.url == url }
    }
}
