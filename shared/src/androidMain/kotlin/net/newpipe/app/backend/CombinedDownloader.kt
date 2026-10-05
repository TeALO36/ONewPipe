package net.newpipe.app.backend

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.context.GlobalContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

private val combinedDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val combinedDownloadClient = OkHttpClient()

actual fun supportsCombinedVideoDownload(): Boolean = true

actual fun downloadVideoWithAudio(videoUrl: String, audioUrl: String, defaultName: String) {
    if (videoUrl.isBlank() || audioUrl.isBlank()) return

    val context = GlobalContext.get().get<Context>()
    val safeName = defaultName
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .removeSuffix(".mp4")
        .ifBlank { "ONewPipe-download" } + ".mp4"

    combinedDownloadScope.launch {
        val workDir = File(context.cacheDir, "onewpipe-mux-${UUID.randomUUID()}")
        val videoFile = File(workDir, "video.stream")
        val audioFile = File(workDir, "audio.stream")
        val outputFile = File(workDir, safeName)
        try {
            workDir.mkdirs()
            downloadToFile(videoUrl, videoFile)
            downloadToFile(audioUrl, audioFile)
            muxToMp4(videoFile, audioFile, outputFile)
            publishToDownloads(context, outputFile, safeName)
            showToast(context, "Download finished: $safeName")
        } catch (error: Exception) {
            showToast(context, "Could not package video and audio")
        } finally {
            workDir.deleteRecursively()
        }
    }
}

private fun downloadToFile(url: String, destination: File) {
    val request = Request.Builder().url(url).build()
    combinedDownloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val body = response.body ?: throw IOException("Empty response")
        body.byteStream().use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        }
    }
}

private fun muxToMp4(videoFile: File, audioFile: File, outputFile: File) {
    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()
    var muxer: MediaMuxer? = null
    var started = false
    try {
        videoExtractor.setDataSource(videoFile.absolutePath)
        audioExtractor.setDataSource(audioFile.absolutePath)
        val videoSource = findTrack(videoExtractor, "video/")
            ?: throw IOException("No compatible video track")
        val audioSource = findTrack(audioExtractor, "audio/")
            ?: throw IOException("No compatible audio track")

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoTrack = muxer.addTrack(videoSource.format)
        val audioTrack = muxer.addTrack(audioSource.format)
        muxer.start()
        started = true
        copyTrack(videoExtractor, muxer, videoSource.index, videoTrack)
        copyTrack(audioExtractor, muxer, audioSource.index, audioTrack)
    } finally {
        if (started) runCatching { muxer?.stop() }
        muxer?.release()
        videoExtractor.release()
        audioExtractor.release()
    }
}

private data class SourceTrack(val index: Int, val format: android.media.MediaFormat)

private fun findTrack(extractor: MediaExtractor, prefix: String): SourceTrack? {
    for (index in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(index)
        if (format.getString(android.media.MediaFormat.KEY_MIME)?.startsWith(prefix) == true) {
            return SourceTrack(index, format)
        }
    }
    return null
}

private fun copyTrack(
    extractor: MediaExtractor,
    muxer: MediaMuxer,
    sourceIndex: Int,
    outputIndex: Int
) {
    extractor.selectTrack(sourceIndex)
    val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
    val info = MediaCodec.BufferInfo()
    while (true) {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        info.offset = 0
        info.size = size
        info.presentationTimeUs = extractor.sampleTime
        info.flags = extractor.sampleFlags
        muxer.writeSampleData(outputIndex, buffer, info)
        extractor.advance()
    }
}

private fun publishToDownloads(context: Context, source: File, fileName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create Downloads entry")
        try {
            resolver.openOutputStream(uri).use { output ->
                if (output == null) throw IOException("Unable to open Downloads entry")
                source.inputStream().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null
            )
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    } else {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val destination = File(directory, fileName)
        source.copyTo(destination, overwrite = true)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(destination.absolutePath),
            arrayOf("video/mp4"),
            null
        )
    }
}

private fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
