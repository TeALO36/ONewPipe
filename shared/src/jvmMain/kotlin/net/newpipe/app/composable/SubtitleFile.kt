package net.newpipe.app.composable

import java.io.File
import java.net.URI

/** libVLC picks its subtitle demuxer from the file extension, not the content. */
internal fun subtitleExtension(mimeType: String?): String {
    val mime = mimeType.orEmpty().lowercase()
    return when {
        "ttml" in mime -> "ttml"
        "subrip" in mime || "srt" in mime -> "srt"
        else -> "vtt"
    }
}

/**
 * Writes a subtitle track to a temporary file libVLC can load. [source] is
 * usually a URL, but some services hand over the subtitle text itself.
 */
internal fun writeSubtitleFile(source: String, mimeType: String?): File {
    val bytes = if (source.startsWith("http://") || source.startsWith("https://")) {
        val connection = URI(source).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        connection.getInputStream().use { it.readBytes() }
    } else {
        source.toByteArray()
    }
    return File.createTempFile("onewpipe-subtitle-", ".${subtitleExtension(mimeType)}").apply {
        deleteOnExit()
        writeBytes(bytes)
    }
}
