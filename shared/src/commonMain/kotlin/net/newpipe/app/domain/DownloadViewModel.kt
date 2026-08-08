package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

sealed class DownloadState {
    object Idle : DownloadState()
    object Loading : DownloadState()
    data class Ready(
        val videoStreams: List<VideoStream>,
        val audioStreams: List<AudioStream>,
        val title: String
    ) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class DownloadViewModel : ViewModel() {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun loadStreams(url: String, title: String) {
        _state.value = DownloadState.Loading
        viewModelScope.launch {
            try {
                val (videos, audios) = withContext(Dispatchers.IO) {
                    val service = NewPipe.getServiceByUrl(url) ?: throw Exception("Service not found")
                    val extractor = service.getStreamExtractor(url)
                    extractor.fetchPage()
                    Pair(extractor.videoStreams ?: emptyList(), extractor.audioStreams ?: emptyList())
                }
                _state.value = DownloadState.Ready(videos, audios, title)
            } catch (e: Exception) {
                _state.value = DownloadState.Error(e.message ?: "Failed to fetch download links")
            }
        }
    }

    fun dismiss() {
        _state.value = DownloadState.Idle
    }
}
