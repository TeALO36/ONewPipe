package net.newpipe.app.composable

import kotlinx.coroutines.runBlocking
import net.newpipe.app.backend.NewPipeMediaRepository
import net.newpipe.app.backend.OkHttpDownloader
import net.newpipe.app.backend.audioForCombinedDownload
import net.newpipe.app.backend.supportsCombinedVideoDownload
import net.newpipe.app.domain.SearchFilter
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Attaching a subtitle file to a playing YouTube stream (adaptive video plus
 * an input-slave audio track, as the desktop player plays it) must not stall
 * playback. Skips (passes) without network or VLC.
 */
class SubtitleSlaveTest {

    @Test
    fun playbackKeepsRunningAfterAttachingSubtitles() = runBlocking {
        if (!supportsCombinedVideoDownload()) {
            println("SKIP SubtitleSlaveTest: VLC not found")
            return@runBlocking
        }
        NewPipe.init(OkHttpDownloader(OkHttpClient.Builder().build()), Localization("en", "US"))
        val candidates = runCatching {
            NewPipeMediaRepository().search(0, "music video 4k", SearchFilter.VIDEOS).items
        }.getOrElse {
            println("SKIP SubtitleSlaveTest (search failed): ${it.message}")
            return@runBlocking
        }
        val info = candidates.mapNotNull { it.url }.take(5).firstNotNullOfOrNull { url ->
            runCatching { StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url) }.getOrNull()
                ?.takeIf { it.subtitles.orEmpty().isNotEmpty() && it.videoOnlyStreams.orEmpty().isNotEmpty() }
        } ?: run {
            println("SKIP SubtitleSlaveTest: no subtitled video among the search results")
            return@runBlocking
        }
        val video = info.videoOnlyStreams
            .filter { it.format == MediaFormat.MPEG_4 && !it.content.isNullOrBlank() }
            .filter { (it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0) in 1..720 }
            .maxByOrNull { it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0 }
            ?: return@runBlocking println("SKIP SubtitleSlaveTest: no <=720p MP4 stream")
        val audio = audioForCombinedDownload(video, info.audioStreams.orEmpty())
            ?: return@runBlocking println("SKIP SubtitleSlaveTest: no audio stream")
        val track = info.subtitles.first()
        val source = track.content ?: track.url
            ?: return@runBlocking println("SKIP SubtitleSlaveTest: subtitle track has no URL")
        val subtitleFile = writeSubtitleFile(source, track.format?.mimeType)

        val factory = MediaPlayerFactory(
            "--quiet", "--vout=dummy", "--aout=dummy",
            "--avcodec-hw=none", "--network-caching=400", "--drop-late-frames"
        )
        val player = factory.mediaPlayers().newMediaPlayer()
        try {
            player.media().play(video.content, ":input-slave=${audio.content}")
            waitUntil(20_000) { player.status().time() > 2_000 }
            val before = player.status().time()
            println("SUBTITLE SLAVE playing at ${before}ms, attaching ${subtitleFile.name}")

            val attached = player.subpictures().setSubTitleFile(subtitleFile)
            Thread.sleep(6_000)
            val after = player.status().time()
            println(
                "SUBTITLE SLAVE attached=$attached time ${before}ms -> ${after}ms, " +
                    "spu tracks=${player.subpictures().trackCount()} selected=${player.subpictures().track()} " +
                    "playing=${player.status().isPlaying} state=${player.status().state()}"
            )
            assertTrue(before > 0, "Playback never started")
            assertTrue(after - before >= 3_000, "Playback stalled after attaching subtitles: ${before}ms -> ${after}ms")
        } finally {
            runCatching { player.controls().stop() }
            runCatching { player.release() }
            runCatching { factory.release() }
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end && !condition()) Thread.sleep(100)
    }
}
