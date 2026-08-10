package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

internal data class PlaybackStreamCandidate<T>(
    val stream: T,
    val height: Int,
    val adaptive: Boolean
)

/** Choose a one-request stream first so desktop can render before adaptive audio is ready. */
internal fun <T> selectFastStartStream(
    progressive: List<PlaybackStreamCandidate<T>>,
    adaptive: List<PlaybackStreamCandidate<T>>
): PlaybackStreamCandidate<T>? {
    fun preferred(candidates: List<PlaybackStreamCandidate<T>>): PlaybackStreamCandidate<T>? =
        candidates.firstOrNull { it.height in 1..720 } ?: candidates.firstOrNull()
    return preferred(progressive) ?: preferred(adaptive)
}

sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    data class Playing(
        val title: String,
        val originalUrl: String,
        val streamUrl: String,
        val audioUrl: String? = null,
        val uploaderName: String,
        val uploaderSubscriberCount: Long,
        val viewCount: Long,
        val relatedItems: List<StreamInfoItem>,
        /** Progressive streams contain both video and audio. */
        val videoStreams: List<VideoStream>,
        /** Adaptive streams contain video only and are paired with [audioStreams]. */
        val videoOnlyStreams: List<VideoStream> = emptyList(),
        val audioStreams: List<AudioStream>,
        val resumePositionMs: Long = 0
    ) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

/**
 * Controls the currently played video. Adaptive streams are paired with the
 * best available audio stream on desktop, so YouTube videos are not limited to
 * the old ~360p progressive format.
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
    private val infoCache = mutableMapOf<String, Deferred<StreamInfo>>()
    private val qualityLoads = mutableSetOf<String>()

    /** Start extraction while the pointer is already over a card, before click. */
    fun prefetch(url: String) {
        if (url.isBlank()) return
        synchronized(infoCache) {
            if (infoCache[url]?.isActive == true || infoCache[url]?.isCompleted == true) return
            infoCache[url] = viewModelScope.async(Dispatchers.IO) {
                val service = NewPipe.getServiceByUrl(url) ?: throw Exception("Service not found for URL")
                StreamInfo.getInfo(service, url)
            }.also { deferred ->
                deferred.invokeOnCompletion { cause ->
                    if (cause != null) synchronized(infoCache) { infoCache.remove(url) }
                }
            }
        }
    }

    fun loadVideo(url: String, title: String) {
        _state.value = PlayerState.Loading
        viewModelScope.launch {
            try {
                val info = synchronized(infoCache) { infoCache[url] }
                    ?.await()
                    ?: withContext(Dispatchers.IO) {
                        val service = NewPipe.getServiceByUrl(url) ?: throw Exception("Service not found for URL")
                        StreamInfo.getInfo(service, url)
                    }

                currentUrl = info.url ?: url
                currentTitle = info.name ?: title
                lastPositionMs = 0L
                lastDurationMs = info.duration ?: 0L

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

    /**
     * Load the optional high-quality formats after playback has already begun.
     * Desktop disables the extra iOS request on the critical path; opening the
     * quality menu is the explicit opt-in for 1080p/4K formats.
     */
    fun loadFullQuality(url: String) {
        if (url.isBlank()) return
        synchronized(qualityLoads) {
            if (!qualityLoads.add(url)) return
        }
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
                        .setFetchIosClient(true)
                    try {
                        val service = NewPipe.getServiceByUrl(url)
                            ?: throw Exception("Service not found for URL")
                        StreamInfo.getInfo(service, url)
                    } finally {
                        org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
                            .setFetchIosClient(false)
                    }
                }
                val current = _state.value
                if (current is PlayerState.Playing &&
                    (current.originalUrl == url || current.originalUrl == info.url)
                ) {
                    _state.value = current.copy(
                        videoStreams = info.videoStreams ?: emptyList(),
                        videoOnlyStreams = info.videoOnlyStreams ?: emptyList(),
                        audioStreams = info.audioStreams ?: emptyList()
                    )
                }
            } catch (_: Exception) {
                // The fast progressive stream is already playing; keep it if
                // optional high-quality extraction is blocked or unavailable.
            } finally {
                synchronized(qualityLoads) { qualityLoads.remove(url) }
            }
        }
    }

    /** Switch quality without dropping the selected audio track. */
    fun changeQuality(videoUrl: String, audioUrl: String? = null) {
        val currentState = _state.value
        if (currentState is PlayerState.Playing && videoUrl.isNotBlank()) {
            _state.value = currentState.copy(streamUrl = videoUrl, audioUrl = audioUrl)
        }
    }

    /** Called by the video player roughly every 500 ms. */
    fun onPositionUpdate(positionMs: Long, durationMs: Long) {
        lastPositionMs = positionMs
        if (durationMs > 0) lastDurationMs = durationMs
    }

    fun rememberPlaybackPosition() {
        val current = _state.value
        if (current is PlayerState.Playing && lastPositionMs > 0) {
            _state.value = current.copy(resumePositionMs = lastPositionMs)
        }
    }

    private fun setInitialQuality(info: StreamInfo, resumePositionMs: Long) {
        val progressive = (info.videoStreams ?: emptyList())
            .filter { !it.content.isNullOrEmpty() }
            .sortedByDescending { resolutionHeight(it.resolution) }
        val adaptive = (info.videoOnlyStreams ?: emptyList())
            .filter { !it.content.isNullOrEmpty() }
            .sortedByDescending { resolutionHeight(it.resolution) }
        val audioStreams = (info.audioStreams ?: emptyList())
            .filter { !it.content.isNullOrEmpty() }
            .sortedByDescending { it.averageBitrate }

        // Start with a muxed stream whenever possible. It needs one HTTP
        // request, while an adaptive video plus an input-slave audio stream
        // makes VLC wait for two requests before it can render its first frame.
        // Keep the initial stream at 720p or below for a fast first frame; the
        // quality menu still exposes adaptive 1080p/4K streams.
        val progressiveCandidates = progressive.map {
            PlaybackStreamCandidate(it, resolutionHeight(it.resolution), adaptive = false)
        }
        val adaptiveCandidates = adaptive.map {
            PlaybackStreamCandidate(it, resolutionHeight(it.resolution), adaptive = true)
        }
        val selectedCandidate = selectFastStartStream(progressiveCandidates, adaptiveCandidates)
            ?: throw Exception("No video stream found")
        val selectedVideo = selectedCandidate.stream
        val finalUrl = selectedVideo.content ?: selectedVideo.url
            ?: throw Exception("Selected video stream has no URL")
        val preferredAudio = audioStreams.firstOrNull()
        val finalAudioUrl = if (selectedCandidate.adaptive) {
            preferredAudio?.content ?: preferredAudio?.url
        } else {
            null
        }

        val relatedItems = try {
            info.relatedItems?.filterIsInstance<StreamInfoItem>() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        _state.value = PlayerState.Playing(
            title = info.name ?: "",
            originalUrl = info.url ?: "",
            streamUrl = finalUrl,
            audioUrl = finalAudioUrl,
            uploaderName = info.uploaderName ?: "",
            uploaderSubscriberCount = info.uploaderSubscriberCount ?: 0L,
            viewCount = info.viewCount ?: 0L,
            relatedItems = relatedItems,
            videoStreams = progressive,
            videoOnlyStreams = adaptive,
            audioStreams = audioStreams,
            resumePositionMs = resumePositionMs
        )
    }

    fun stop() {
        val wasPlaying = _state.value is PlayerState.Playing
        _state.value = PlayerState.Idle
        if (wasPlaying) pushPosition()
    }

    private fun resolutionHeight(resolution: String): Int =
        resolution.filter { it.isDigit() }.toIntOrNull() ?: 0

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
