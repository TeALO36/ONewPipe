package net.newpipe.app.backend

import kotlinx.coroutines.runBlocking
import net.newpipe.app.domain.SearchFilter
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Downloads a real short YouTube video as separate video and audio streams and
 * packages them with libVLC, like the desktop "Video + audio" download does.
 * Skips (passes) without network or without VLC installed.
 */
class CombinedDownloaderTest {

    @Test
    fun packagesVideoAndAudioIntoOneFile() = runBlocking {
        if (!supportsCombinedVideoDownload()) {
            println("SKIP CombinedDownloaderTest: VLC not found")
            return@runBlocking
        }
        NewPipe.init(OkHttpDownloader(OkHttpClient.Builder().build()), Localization("en", "US"))
        val candidates = runCatching {
            NewPipeMediaRepository().search(0, "one minute short film", SearchFilter.VIDEOS).items
        }.getOrElse { e ->
            println("SKIP CombinedDownloaderTest (search failed): ${e.message}")
            return@runBlocking
        }
        val info = candidates.mapNotNull { it.url }.take(8).firstNotNullOfOrNull { url ->
            runCatching { StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url) }.getOrNull()
                ?.takeIf { it.duration in 20..240 && it.videoOnlyStreams.orEmpty().isNotEmpty() }
        } ?: run {
            println("SKIP CombinedDownloaderTest: no short video among the search results")
            return@runBlocking
        }

        val video = info.videoOnlyStreams
            .filter { it.format == MediaFormat.MPEG_4 && !it.content.isNullOrBlank() }
            .minByOrNull { it.resolution.filter(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }
            ?: run {
                println("SKIP CombinedDownloaderTest: no MP4 video-only stream")
                return@runBlocking
            }
        val audio = audioForCombinedDownload(video, info.audioStreams.orEmpty()) ?: run {
            println("SKIP CombinedDownloaderTest: no audio stream")
            return@runBlocking
        }

        val workDir = Files.createTempDirectory("onewpipe-mux-test-").toFile()
        try {
            val videoFile = workDir.resolve("video.stream")
            val audioFile = workDir.resolve("audio.stream")
            val output = workDir.resolve("combined" + combinedDownloadExtension(video, audio))

            var start = System.nanoTime()
            downloadInChunks(video.content, videoFile)
            downloadInChunks(audio.content, audioFile)
            val downloadMs = (System.nanoTime() - start) / 1_000_000

            start = System.nanoTime()
            val ok = remuxWithVlc(videoFile, audioFile, output, timeoutMinutes = 5)
            val remuxMs = (System.nanoTime() - start) / 1_000_000

            val bytes = output.takeIf { it.exists() }?.readBytes() ?: ByteArray(0)
            val tracks = Regex("trak").findAll(String(bytes, Charsets.ISO_8859_1)).count()
            println(
                "COMBINED ${info.name} (${info.duration}s) ${video.resolution} ${video.format?.name}+${audio.format?.name}: " +
                    "video=${videoFile.length()} audio=${audioFile.length()} output=${output.length()} " +
                    "tracks=$tracks download=${downloadMs}ms remux=${remuxMs}ms"
            )

            assertTrue(videoFile.length() > 0 && audioFile.length() > 0, "Streams were not downloaded")
            assertTrue(ok, "VLC did not produce the combined file")
            assertTrue(
                output.length() >= (videoFile.length() + audioFile.length()) * 8 / 10,
                "Combined file is too small to hold both streams"
            )
            if (output.name.endsWith(".mp4")) {
                assertTrue(tracks >= 2, "Expected a video and an audio track, found $tracks")
            }
        } finally {
            workDir.deleteRecursively()
        }
    }
}
