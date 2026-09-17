package fr.arthonetwork.onewpipe.server

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadsTest {
    @BeforeTest
    fun setUp() {
        NewPipe.init(OkHttpDownloader(okhttp3.OkHttpClient()), Localization("en", "US"), ContentCountry("US"))
    }

    @Test
    fun fileNamesAreSafeOnEveryOperatingSystem() {
        assertEquals("AC_DC _ Live_ 1991.mp4", safeFileName("AC/DC | Live: 1991", "mp4"))
        assertEquals("video.m4a", safeFileName("  ...  ", "m4a"))
        assertEquals(120 + ".webm".length, safeFileName("x".repeat(300), "webm").length)
    }

    // Real network: the video the extractor tests use as well.
    @Test
    fun optionsPairEachVideoQualityWithAMatchingAudioStream() {
        val options = downloadOptions(cachedStreamInfo("https://www.youtube.com/watch?v=jNQXAC9IVRw"))
        val mp4 = options.filter { it.kind == "video" && it.extension == "mp4" }
        assertTrue(mp4.isNotEmpty(), "MP4 video options: $options")
        assertTrue(mp4.all { it.id.startsWith("v:") || it.id.startsWith("m:") })
        assertTrue(options.any { it.kind == "audio" && it.extension == "m4a" }, "M4A audio option: $options")
        assertEquals(options.size, options.map { it.label }.distinct().size, "No duplicate qualities: $options")
    }
}
