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
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

sealed class DownloadState {
    object Idle : DownloadState()
    object Loading : DownloadState()
    data class Ready(
        /** Mixed streams (video + audio in one file), e.g. 360p MPEG-4. */
        val videoStreams: List<VideoStream>,
        /** Video-only streams (no audio), e.g. 1080p WebM — the high resolutions. */
        val videoOnlyStreams: List<VideoStream>,
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
                val (videos, videoOnly, audios) = withContext(Dispatchers.IO) {
                    val service = NewPipe.getServiceByUrl(url) ?: throw Exception("Service not found")
                    // StreamInfo (not the raw extractor) resolves all video/audio formats
                    // with their real content URLs, including higher resolutions.
                    val info = StreamInfo.getInfo(service, url)
                    // Keep mixed and video-only streams separate: on YouTube the WEB
                    // client only lists ~360p mixed; the higher resolutions
                    // (720p/1080p/4K) are all video-only streams.
                    Triple(
                        info.videoStreams ?: emptyList(),
                        info.videoOnlyStreams ?: emptyList(),
                        info.audioStreams ?: emptyList()
                    )
                }
                _state.value = DownloadState.Ready(videos, videoOnly, audios, title)
            } catch (e: Exception) {
                _state.value = DownloadState.Error(e.message ?: "Failed to fetch download links")
            }
        }
    }

    fun dismiss() {
        _state.value = DownloadState.Idle
    }
}
