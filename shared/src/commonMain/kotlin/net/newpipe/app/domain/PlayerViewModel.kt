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
        val audioStreams: List<AudioStream>,
        val resumePositionMs: Long = 0
    ) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

/**
 * Controls the currently played video. When a server connection is configured
 * (see [SyncViewModel]), the play position is synced: we resume from the saved
 * position when a video opens and push the position back when it closes.
 */
class PlayerViewModel(
    private val syncViewModel: SyncViewModel? = null
) : ViewModel() {
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var currentUrl = ""
    private var currentTitle = ""
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L

    fun loadVideo(url: String, title: String) {
        _state.value = PlayerState.Loading
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val service = NewPipe.getServiceByUrl(url) ?: throw Exception("Service not found for URL")
                    StreamInfo.getInfo(service, url)
                }

                currentUrl = info.url ?: url
                currentTitle = info.name ?: title
                lastPositionMs = 0L
                lastDurationMs = info.duration ?: 0L

                // Resume from the position saved on the server (if any).
                val resumePosition = syncViewModel?.resumePositionFor(currentUrl)
                    ?.takeIf { it.positionMs in 5_000..(it.durationMs - 10_000).coerceAtLeast(5_000) }
                    ?.positionMs
                    ?: 0L

                setInitialQuality(info, resumePosition)
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

    /** Called by the video player ~every 500 ms while playing. */
    fun onPositionUpdate(positionMs: Long, durationMs: Long) {
        lastPositionMs = positionMs
        if (durationMs > 0) lastDurationMs = durationMs
    }

    /**
     * Remember the current play position so the video resumes from here when its
     * surface is hidden (e.g. while a download dialog is on top) and re-shown.
     */
    fun rememberPlaybackPosition() {
        val current = _state.value
        if (current is PlayerState.Playing && lastPositionMs > 0) {
            _state.value = current.copy(resumePositionMs = lastPositionMs)
        }
    }

    private fun setInitialQuality(info: StreamInfo, resumePositionMs: Long) {
        // Prefer the highest-resolution mixed stream (video + audio in one URL):
        // video-only streams (720p/1080p/4K) have no audio track, so they are
        // only offered for downloads, not for direct playback.
        val videoStreams = (info.videoStreams ?: emptyList())
            .filter { !it.content.isNullOrEmpty() }
            .sortedByDescending { resolutionHeight(it.resolution) }
        val audioStreams = info.audioStreams ?: emptyList()
        
        val streamToPlay = videoStreams.firstOrNull()
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
            audioStreams = audioStreams,
            resumePositionMs = resumePositionMs
        )
    }

    fun stop() {
        val wasPlaying = _state.value is PlayerState.Playing
        _state.value = PlayerState.Idle
        if (wasPlaying) {
            pushPosition()
        }
    }

    /** Parse "1080p60" / "720p" into a comparable height; 0 when unknown. */
    private fun resolutionHeight(resolution: String): Int =
        resolution.filter { it.isDigit() }.toIntOrNull() ?: 0

    /** Push the last known play position to the server (no-op when not connected). */
    private fun pushPosition() {
        val position = lastPositionMs
        val duration = lastDurationMs
        if (position <= 0 || duration <= 0) return
        val url = currentUrl
        val title = currentTitle
        viewModelScope.launch {
            runCatching {
                syncViewModel?.pushWatchState(
                    listOf(
                        WatchStateItem(
                            url = url,
                            title = title,
                            positionMs = position,
                            durationMs = duration,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                )
            }
        }
    }
}
