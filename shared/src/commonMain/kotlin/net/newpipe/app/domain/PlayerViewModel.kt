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
import org.schabi.newpipe.extractor.comments.CommentsInfo
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

/** One comment of the currently played video. */
data class VideoComment(
    val author: String,
    val text: String,
    val likeCount: Long = 0,
    val publishedAt: String = "",
    val pinned: Boolean = false
)

sealed class CommentsState {
    object Idle : CommentsState()
    object Loading : CommentsState()
    data class Loaded(val comments: List<VideoComment>) : CommentsState()
    data class Error(val message: String) : CommentsState()
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
        val resumePositionMs: Long = 0,
        val uploaderUrl: String = "",
        val thumbnailUrl: String = "",
        /** True while the player is restricted to the audio track (background/music mode). */
        val audioOnly: Boolean = false,
        val durationText: String = "",
        /** Plain-text video description, shown under the title. */
        val description: String = "",
        val uploadDate: String = ""
    ) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

/**
 * Controls the currently played video. Adaptive streams are paired with the
 * best available audio stream on desktop, so YouTube videos are not limited to
 * the old ~360p progressive format.
 */
/** Repeat behaviour of the player, mirroring the NewPipe player repeat button. */
enum class RepeatMode(val label: String) {
    OFF("Repeat off"),
    ONE("Repeat one"),
    ALL("Repeat queue")
}

class PlayerViewModel(
    private val syncViewModel: SyncViewModel? = null,
    private val libraryViewModel: LibraryViewModel? = null,
    private val settingsViewModel: SettingsViewModel? = null
) : ViewModel() {
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    /** Videos queued with "Play next"/"Add to queue". */
    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> = _queue.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _comments = MutableStateFlow<CommentsState>(CommentsState.Idle)
    val comments: StateFlow<CommentsState> = _comments.asStateFlow()

    private var currentUrl = ""
    private var currentTitle = ""
    private val playbackHistory = mutableListOf<Pair<String, String>>()
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L
    private var lastHistoryWriteMs = 0L
    private var fullVideoUrl: String? = null
    private var fullAudioUrl: String? = null
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
        if (currentUrl.isNotBlank() && currentUrl != url) {
            playbackHistory += currentUrl to currentTitle
            if (playbackHistory.size > 50) playbackHistory.removeAt(0)
        }
        _state.value = PlayerState.Loading
        _comments.value = CommentsState.Idle
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
                lastDurationMs = (info.duration ?: 0L) * 1000L

                val resumePosition = if (settingsViewModel?.resumePlayback?.value == false) {
                    0L
                } else {
                    localResumePosition(currentUrl) ?: serverResumePosition(currentUrl)
                }

                setInitialQuality(info, resumePosition)
                libraryViewModel?.recordWatch(
                    url = currentUrl,
                    title = currentTitle,
                    uploaderName = info.uploaderName.orEmpty(),
                    thumbnailUrl = info.thumbnails?.firstOrNull()?.url.orEmpty(),
                    durationText = formatDuration(info.duration ?: 0L)
                )
            } catch (e: Exception) {
                _state.value = PlayerState.Error(e.message ?: "Failed to load video")
            }
        }
    }

    fun playPrevious() {
        val previous = playbackHistory.removeLastOrNull() ?: return
        loadVideo(previous.first, previous.second)
    }

    /**
     * Refresh the full format list after playback has already begun.
     *
     * The first extraction is optimized for a fast first frame, so opening the
     * quality menu is the explicit opt-in for re-reading every 1080p/4K format
     * the service exposes.
     */
    fun loadFullQuality(url: String) {
        if (url.isBlank()) return
        synchronized(qualityLoads) {
            if (!qualityLoads.add(url)) return
        }
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val service = NewPipe.getServiceByUrl(url)
                        ?: throw Exception("Service not found for URL")
                    StreamInfo.getInfo(service, url)
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
        // Persisting on every tick would rewrite the whole history list several
        // times per second, so the local history is only refreshed every 5s.
        if (positionMs - lastHistoryWriteMs >= HISTORY_WRITE_INTERVAL_MS ||
            lastHistoryWriteMs - positionMs >= HISTORY_WRITE_INTERVAL_MS
        ) {
            lastHistoryWriteMs = positionMs
            libraryViewModel?.updatePosition(currentUrl, positionMs, lastDurationMs)
        }
    }

    // ------------------------------------------------------------------ queue

    /** Adds a video to the end of the queue. */
    fun enqueue(item: MediaItem) {
        if (item.url.isBlank() || _queue.value.any { it.url == item.url }) return
        _queue.value = _queue.value + item
    }

    /** Puts a video directly after the one currently playing. */
    fun playNext(item: MediaItem) {
        if (item.url.isBlank()) return
        _queue.value = listOf(item) + _queue.value.filterNot { it.url == item.url }
    }

    fun removeFromQueue(url: String) {
        _queue.value = _queue.value.filterNot { it.url == url }
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }

    /**
     * Plays the next video: the queue first, then — when autoplay is on — the
     * first related video. Returns false when there is nothing left to play.
     */
    fun playNextInQueue(relatedFallback: (() -> Pair<String, String>?)? = null): Boolean {
        if (_repeatMode.value == RepeatMode.ONE) {
            val current = _state.value as? PlayerState.Playing ?: return false
            loadVideo(current.originalUrl, current.title)
            return true
        }
        val next = _queue.value.firstOrNull()
        if (next != null) {
            _queue.value = _queue.value.drop(1)
            if (_repeatMode.value == RepeatMode.ALL) _queue.value = _queue.value + next
            loadVideo(next.url, next.title)
            return true
        }
        if (settingsViewModel?.autoplayNext?.value != false) {
            val related = relatedFallback?.invoke()
            if (related != null) {
                loadVideo(related.first, related.second)
                return true
            }
        }
        return false
    }

    // ------------------------------------------------------- playback options

    /**
     * Loads the comments of the current video on demand: NewPipe shows them in
     * a dedicated tab, and extracting them costs an extra request.
     */
    fun loadComments(url: String) {
        if (url.isBlank() || _comments.value is CommentsState.Loading) return
        _comments.value = CommentsState.Loading
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    val service = NewPipe.getServiceByUrl(url)
                        ?: throw Exception("Service not found for URL")
                    CommentsInfo.getInfo(service, url)
                        .relatedItems
                        .orEmpty()
                        .take(MAX_COMMENTS)
                        .map { comment ->
                            VideoComment(
                                author = comment.uploaderName.orEmpty(),
                                text = comment.commentText?.content.orEmpty(),
                                likeCount = comment.likeCount.toLong(),
                                publishedAt = comment.textualUploadDate.orEmpty(),
                                pinned = comment.isPinned
                            )
                        }
                }
                _comments.value = if (items.isEmpty()) {
                    CommentsState.Error("Comments are disabled for this video")
                } else {
                    CommentsState.Loaded(items)
                }
            } catch (e: Exception) {
                _comments.value = CommentsState.Error(e.message ?: "Unable to load comments")
            }
        }
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed.coerceIn(0.25f, 3.0f)
    }

    /**
     * Switches between the video stream and the plain audio track. Audio-only
     * playback is what NewPipe calls "background"/"music" mode: it keeps the
     * sound while using far less bandwidth.
     */
    fun toggleAudioOnly() {
        val current = _state.value as? PlayerState.Playing ?: return
        if (current.audioOnly) {
            val video = fullVideoUrl ?: return
            _state.value = current.copy(
                streamUrl = video,
                audioUrl = fullAudioUrl,
                audioOnly = false,
                resumePositionMs = lastPositionMs
            )
        } else {
            val audio = current.audioUrl
                ?: current.audioStreams.maxByOrNull { it.averageBitrate }?.let { it.content ?: it.url }
                ?: return
            fullVideoUrl = current.streamUrl
            fullAudioUrl = current.audioUrl
            _state.value = current.copy(
                streamUrl = audio,
                audioUrl = null,
                audioOnly = true,
                resumePositionMs = lastPositionMs
            )
        }
    }

    fun rememberPlaybackPosition() {
        val current = _state.value
        if (current is PlayerState.Playing && lastPositionMs > 0) {
            _state.value = current.copy(resumePositionMs = lastPositionMs)
        }
    }

    /** Resume position stored locally by the watch history. */
    private fun localResumePosition(url: String): Long? =
        libraryViewModel?.history?.value
            ?.firstOrNull { it.url == url }
            ?.takeIf { it.durationMs > 0 && it.positionMs in 5_000..(it.durationMs - 10_000).coerceAtLeast(5_000) }
            ?.positionMs

    /** Resume position synchronized from the ONewPipe server, if any. */
    private suspend fun serverResumePosition(url: String): Long =
        syncViewModel?.resumePositionFor(url)
            ?.takeIf { it.positionMs in 5_000..(it.durationMs - 10_000).coerceAtLeast(5_000) }
            ?.positionMs
            ?: 0L

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
        val preferred = settingsViewModel?.preferredQuality?.value?.toIntOrNull()
        val selectedCandidate = preferred
            ?.let { target ->
                (progressiveCandidates + adaptiveCandidates)
                    .filter { it.height in 1..target }
                    .maxByOrNull { it.height }
            }
            ?: selectFastStartStream(progressiveCandidates, adaptiveCandidates)
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
            resumePositionMs = resumePositionMs,
            // Some YouTube formats expose the creator as a sub-channel rather
            // than uploader. Keep either URL so the profile row never becomes
            // a dead, non-interactive surface.
            uploaderUrl = info.uploaderUrl.orEmpty().ifBlank {
                info.subChannelUrl.orEmpty()
            },
            thumbnailUrl = info.thumbnails.firstOrNull()?.url ?: "",
            durationText = formatDuration(info.duration ?: 0L),
            description = info.description?.content.orEmpty(),
            uploadDate = info.textualUploadDate.orEmpty()
        )
    }

    fun stop() {
        val wasPlaying = _state.value is PlayerState.Playing
        _state.value = PlayerState.Idle
        if (wasPlaying) pushPosition()
    }

    private fun resolutionHeight(resolution: String): Int =
        resolution.filter { it.isDigit() }.toIntOrNull() ?: 0

    companion object {
        /** Only persist the resume position every few seconds. */
        const val HISTORY_WRITE_INTERVAL_MS = 5_000L
        const val MAX_COMMENTS = 50
    }

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

/** "12:34" / "1:02:03" for a duration expressed in seconds. */
internal fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } else {
        "$minutes:${secs.toString().padStart(2, '0')}"
    }
}
