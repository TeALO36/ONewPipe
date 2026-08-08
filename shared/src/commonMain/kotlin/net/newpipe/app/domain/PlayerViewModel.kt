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
import org.schabi.newpipe.extractor.stream.StreamExtractor

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    data class Playing(
        val title: String,
        val originalUrl: String,
        val streamUrl: String,
        val uploaderName: String,
        val uploaderSubscriberCount: Long,
        val viewCount: Long,
        val relatedItems: List<StreamInfoItem>,
        val videoStreams: List<VideoStream>,
        val audioStreams: List<AudioStream>
    ) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

class PlayerViewModel : ViewModel() {
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun loadVideo(url: String, title: String) {
        _state.value = PlayerState.Loading
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val service = NewPipe.getServiceByUrl(url) ?: throw Exception("Service not found for URL")
                    StreamInfo.getInfo(service, url)
                }
                
                setInitialQuality(info)
            } catch (e: Exception) {
                _state.value = PlayerState.Error(e.message ?: "Failed to load video")
            }
        }
    }
    
    fun changeQuality(targetStreamUrl: String) {
        val currentState = _state.value
        if (currentState is PlayerState.Playing) {
            _state.value = currentState.copy(streamUrl = targetStreamUrl)
        }
    }

    private fun setInitialQuality(info: StreamInfo) {
        val videoStreams = info.videoStreams ?: emptyList()
        val audioStreams = info.audioStreams ?: emptyList()
        
        val streamToPlay = videoStreams.firstOrNull { !it.content.isNullOrEmpty() }
                ?: audioStreams.firstOrNull { !it.content.isNullOrEmpty() }
            
        val finalUrl = streamToPlay?.content ?: throw Exception("No stream found")
        
        val relatedItems = try {
            info.relatedItems?.filterIsInstance<StreamInfoItem>() ?: emptyList()
        } catch(e: Exception) { emptyList() }
        
        _state.value = PlayerState.Playing(
            title = info.name ?: "",
            originalUrl = info.url ?: "",
            streamUrl = finalUrl,
            uploaderName = info.uploaderName ?: "",
            uploaderSubscriberCount = info.uploaderSubscriberCount ?: 0L,
            viewCount = info.viewCount ?: 0L,
            relatedItems = relatedItems,
            videoStreams = videoStreams,
            audioStreams = audioStreams
        )
    }

    fun stop() {
        _state.value = PlayerState.Idle
    }
}
