package net.newpipe.app.composable

import kotlinx.coroutines.runBlocking
import net.newpipe.app.backend.NewPipeMediaRepository
import net.newpipe.app.backend.OkHttpDownloader
import net.newpipe.app.domain.SearchFilter
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtitleFileTest {

    @Test
    fun extensionFollowsTheMimeType() {
        assertEquals("vtt", subtitleExtension("text/vtt"))
        assertEquals("ttml", subtitleExtension("application/ttml+xml"))
        assertEquals("srt", subtitleExtension("application/x-subrip"))
        assertEquals("vtt", subtitleExtension(null))
    }

    @Test
    fun inlineSubtitleTextIsWrittenAsIs() {
        val text = "WEBVTT\n\n00:00.000 --> 00:02.000\nHello\n"
        val file = writeSubtitleFile(text, "text/vtt")
        assertTrue(file.name.endsWith(".vtt"))
        assertEquals(text, file.readText())
    }

    /**
     * Downloads a real YouTube track the way the desktop player does before
     * handing it to libVLC. YouTube serves TTML, so the file must be a TTML
     * document with a .ttml extension. Skips (passes) without network.
     */
    @Test
    fun downloadsARealYoutubeTrack() = runBlocking {
        NewPipe.init(OkHttpDownloader(OkHttpClient.Builder().build()), Localization("en", "US"))
        val candidates = runCatching {
            NewPipeMediaRepository().search(0, "music video 4k", SearchFilter.VIDEOS).items
        }.getOrElse { e ->
            println("SKIP SubtitleFileTest (search failed): ${e.message}")
            return@runBlocking
        }
        val track = candidates.take(5).firstNotNullOfOrNull { candidate ->
            val url = candidate.url ?: return@firstNotNullOfOrNull null
            runCatching {
                StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url)
                    .subtitles.orEmpty()
                    .firstOrNull { !(it.content ?: it.url).isNullOrBlank() }
            }.getOrNull()
        } ?: run {
            println("SKIP SubtitleFileTest: no subtitle track among the search results")
            return@runBlocking
        }

        val source = track.content ?: track.url ?: return@runBlocking
        val mime = track.format?.mimeType
        val file = writeSubtitleFile(source, mime)
        val content = file.readText()
        println("SUBTITLE FILE: ${file.absolutePath}, mime=$mime, ${content.length} chars, language=${track.languageTag}")
        println("SUBTITLE HEAD: ${content.take(300).replace('\n', ' ')}")
        assertTrue(content.isNotBlank(), "Downloaded subtitle file is empty")
        if (mime.orEmpty().contains("ttml")) {
            assertTrue(file.name.endsWith(".ttml"))
            assertTrue("<tt" in content && "<p" in content, "Expected a TTML document with cues, got: ${content.take(120)}")
        } else {
            assertTrue(file.name.endsWith(".vtt"))
            assertTrue(content.startsWith("WEBVTT") && "-->" in content, "Expected WebVTT cues, got: ${content.take(120)}")
        }
    }
}
