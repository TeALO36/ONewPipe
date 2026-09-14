package net.newpipe.app.backend

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * The audio stream to package with [video]: AAC (M4A) for an MP4 video so the
 * pair fits an MP4 container, otherwise the best available bitrate.
 */
fun audioForCombinedDownload(video: VideoStream, audioStreams: List<AudioStream>): AudioStream? {
    val usable = audioStreams.filter { !(it.content ?: it.url).isNullOrBlank() }
    val matching = if (video.format == MediaFormat.MPEG_4) {
        usable.filter { it.format == MediaFormat.M4A }
    } else {
        emptyList()
    }
    return matching.maxByOrNull { it.averageBitrate } ?: usable.maxByOrNull { it.averageBitrate }
}

/** `.mp4` when both streams fit an MP4 container, `.mkv` otherwise. */
fun combinedDownloadExtension(video: VideoStream, audio: AudioStream): String =
    if (video.format == MediaFormat.MPEG_4 && audio.format == MediaFormat.M4A) ".mp4" else ".mkv"
