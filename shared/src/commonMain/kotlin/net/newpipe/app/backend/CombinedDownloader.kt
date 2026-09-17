package net.newpipe.app.backend

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * A download request understood by the platform downloader.
 *
 * Android delegates this to NewPipe's native DownloadManagerService, which
 * persists missions, supports pause/resume and selects the correct WebM/MP4
 * post-processing algorithm.
 */
data class NativeDownloadRequest(
    val sourceUrl: String,
    val streamInfo: StreamInfo,
    val videoUrl: String? = null,
    val audioUrl: String? = null,
    val defaultName: String,
    val videoResolution: String? = null,
    /** Keep the extractor's format object; its display name is not necessarily the enum identifier. */
    val videoFormat: MediaFormat? = null,
    val audioFormat: MediaFormat? = null,
    /** Display-name fallbacks used by desktop implementations. */
    val videoFormatName: String? = null,
    val audioFormatName: String? = null,
    val audioBitrate: Int = 0
)

expect fun downloadWithNativeManager(request: NativeDownloadRequest)

/** Whether this platform exposes the native resumable downloader. */
expect fun supportsCombinedVideoDownload(): Boolean
