package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ServerStatus {
    object Disconnected : ServerStatus()
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

    fun connect(serverUrl: String, username: String, password: String, register: Boolean) {
        viewModelScope.launch {
            _status.value = ServerStatus.Disconnected
            try {
                val result = if (register) {
                    client.register(serverUrl, username, password)
                } else {
                    client.login(serverUrl, username, password)
                }
                settingsViewModel.setServerConfig(
                    ServerConfig(
                        serverUrl = serverUrl.trim().trimEnd('/'),
                        username = result.username,
                        token = result.token
                    )
                )
                _status.value = ServerStatus.Connected(result.username, serverUrl.trim().trimEnd('/'))
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

    /** Pull the watch position for one video URL, or null when not connected / unknown. */
    suspend fun resumePositionFor(url: String): WatchStateItem? {
        val config = settingsViewModel.serverConfig.value
        if (!config.isConnected) return null
        return runCatching { client.pullWatchState(config) }
            .getOrNull()
            ?.firstOrNull { it.url == url }
    }
}
