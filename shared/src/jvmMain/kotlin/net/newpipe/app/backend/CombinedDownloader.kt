package net.newpipe.app.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private val combinedDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// YouTube only lists ~360p as ready-to-play files; every higher resolution is
// a video-only stream. Desktop has no MediaMuxer, so libVLC (already required
// by the player) remuxes the two downloads into one file. Without VLC the
// option stays hidden rather than offering a download that cannot finish.
private val vlcAvailable: Boolean by lazy {
    runCatching { NativeDiscovery().discover() }.getOrDefault(false)
}

actual fun supportsCombinedVideoDownload(): Boolean = vlcAvailable

actual fun downloadVideoWithAudio(videoUrl: String, audioUrl: String, defaultName: String) {
    if (videoUrl.isBlank() || audioUrl.isBlank()) return
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, "Save as…", FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        val directory = dialog.directory ?: return@invokeLater
        val fileName = dialog.file ?: return@invokeLater
        val destination = File(directory, fileName)

        combinedDownloadScope.launch {
            val workDir = Files.createTempDirectory("onewpipe-mux-").toFile()
            try {
                val video = File(workDir, "video.stream")
                val audio = File(workDir, "audio.stream")
                DownloadProgressBus.report(fileName, 0)
                downloadInChunks(videoUrl, video) { DownloadProgressBus.report(fileName, it * 80 / 100) }
                downloadInChunks(audioUrl, audio) { DownloadProgressBus.report(fileName, 80 + it * 15 / 100) }
                if (!remuxWithVlc(video, audio, destination)) {
                    throw IOException("VLC could not combine video and audio")
                }
                DownloadProgressBus.complete(fileName)
            } catch (e: Exception) {
                destination.delete()
                DownloadProgressBus.fail(fileName, e.message ?: "Download failed")
            } finally {
                workDir.deleteRecursively()
            }
        }
    }
}

private const val CHUNK_SIZE = 10L * 1024 * 1024

/**
 * Downloads [url] with successive range requests. A single plain GET of a
 * YouTube adaptive stream is throttled to roughly playback speed, while 10 MB
 * ranges are served at full speed (the same approach NewPipe uses).
 */
internal fun downloadInChunks(url: String, destination: File, onProgress: (Int) -> Unit = {}) {
    RandomAccessFile(destination, "rw").use { output ->
        output.setLength(0)
        var position = 0L
        var total = -1L
        var lastPercent = -1
        while (total < 0 || position < total) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Range", "bytes=$position-${position + CHUNK_SIZE - 1}")
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_PARTIAL && code != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP $code")
                }
                total = if (code == HttpURLConnection.HTTP_PARTIAL) {
                    connection.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: throw IOException("Missing Content-Range")
                } else {
                    // The server ignored the range and sends the whole file.
                    position = 0L
                    output.setLength(0)
                    connection.contentLengthLong
                }
                val received = connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.seek(position + count)
                        output.write(buffer, 0, read)
                        count += read
                    }
                    count
                }
                if (received == 0L) throw IOException("Empty response")
                position += received
                if (code == HttpURLConnection.HTTP_OK && total < 0) total = position
                if (total > 0) {
                    val percent = (position * 100 / total).toInt().coerceAtMost(100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}

/**
 * Remuxes (no re-encoding) a video file and an audio file into [output].
 * The container follows the output extension: `.mkv` accepts every codec
 * YouTube serves, `.mp4` is used for H.264 + AAC pairs.
 */
internal fun remuxWithVlc(video: File, audio: File, output: File, timeoutMinutes: Long = 60): Boolean {
    val mux = if (output.name.endsWith(".mkv", ignoreCase = true)) "mkv" else "mp4"
    val destination = output.absolutePath.replace('\\', '/')
    val audioMrl = "file:///" + audio.absolutePath.replace('\\', '/').removePrefix("/")
    output.delete()

    val factory = MediaPlayerFactory("--quiet", "--vout=dummy", "--aout=dummy")
    val player = factory.mediaPlayers().newMediaPlayer()
    val done = CountDownLatch(1)
    val failed = AtomicBoolean(false)
    try {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun finished(mediaPlayer: MediaPlayer?) = done.countDown()
            override fun stopped(mediaPlayer: MediaPlayer?) = done.countDown()
            override fun error(mediaPlayer: MediaPlayer?) {
                failed.set(true)
                done.countDown()
            }
        })
        val started = player.media().play(
            video.absolutePath,
            ":input-slave=$audioMrl",
            ":sout=#std{access=file,mux=$mux,dst=\"$destination\"}",
            ":sout-all",
            ":no-sout-display"
        )
        if (!started) return false
        if (!done.await(timeoutMinutes, TimeUnit.MINUTES)) return false
    } finally {
        runCatching { player.controls().stop() }
        runCatching { player.release() }
        runCatching { factory.release() }
    }
    return !failed.get() && output.length() > 0
}
