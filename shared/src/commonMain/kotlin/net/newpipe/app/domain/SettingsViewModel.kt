package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.newpipe.app.Constants.KEY_STREAMING_SERVICE
import net.newpipe.app.theme.Service

class SettingsViewModel(private val settings: Settings) : ViewModel() {

    private val _currentService = MutableStateFlow(
        Service.entries.find { 
            it.serviceName == settings.getString(KEY_STREAMING_SERVICE, Service.YOUTUBE.serviceName) 
        } ?: Service.YOUTUBE
    )
    val currentService: StateFlow<Service> = _currentService.asStateFlow()

    fun setService(service: Service) {
        settings.putString(KEY_STREAMING_SERVICE, service.serviceName)
        _currentService.value = service
    }
}
