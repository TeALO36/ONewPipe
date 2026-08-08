package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.newpipe.app.Constants.KEY_STREAMING_SERVICE
import net.newpipe.app.theme.Service

class SettingsViewModel(private val settings: Settings) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _currentService = MutableStateFlow(
        Service.entries.find { 
            it.serviceName == settings.getString(KEY_STREAMING_SERVICE, Service.YOUTUBE.serviceName) 
        } ?: Service.YOUTUBE
    )
    val currentService: StateFlow<Service> = _currentService.asStateFlow()

    private val _serverConfig = MutableStateFlow(
        runCatching {
            json.decodeFromString<ServerConfig>(settings.getString(KEY_SERVER_CONFIG, ""))
        }.getOrDefault(ServerConfig())
    )
    val serverConfig: StateFlow<ServerConfig> = _serverConfig.asStateFlow()

    fun setService(service: Service) {
        settings.putString(KEY_STREAMING_SERVICE, service.serviceName)
        _currentService.value = service
    }

    fun setServerConfig(config: ServerConfig) {
        settings.putString(KEY_SERVER_CONFIG, json.encodeToString(config))
        _serverConfig.value = config
    }

    companion object {
        const val KEY_SERVER_CONFIG = "server_config"
    }
}
