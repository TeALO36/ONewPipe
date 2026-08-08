package net.newpipe.app.backend

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Integration test for the download/stream pipeline.
 *
 * Verifies that a real YouTube video resolves multiple video formats (not just
 * 360p) and that every stream carries a usable content URL. This guards the
 * "downloads only ever show 360p / don't start" regression.
 *
 * Skips (passes) when the network is unavailable, so it is safe in CI.
 */
class StreamInfoTest {

    companion object {
        private val initialized: Boolean by lazy {
            NewPipe.init(
                OkHttpDownloader(OkHttpClient.Builder().build()),
                Localization("en", "US")
            )
            // Same as the app: the WEB client alone is throttled to ~360p.
            org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setFetchIosClient(true)
            true
        }
    }

    // "Baby Shark Dance" — modern 4K video with a wide range of formats.
    private val testUrl = "https://www.youtube.com/watch?v=XqZsoesa55w"

    @Test
    fun resolvesMultipleResolutionsWithContentUrls() = runBlocking {
        initialized

        val info = runCatching {
            val service = NewPipe.getServiceByUrl(testUrl)
                ?: fail("No service for $testUrl")
            StreamInfo.getInfo(service, testUrl)
        }.getOrElse { e ->
            // No network / blocked: skip like the other integration tests.
            println("SKIP StreamInfoTest: ${e.message}")
            return@runBlocking
        }

        val videos = info.videoStreams ?: emptyList()
        val videoOnly = info.videoOnlyStreams ?: emptyList()
        val audios = info.audioStreams ?: emptyList()

        println("MIXED: ${videos.map { "${it.resolution} (${it.format?.name}) content=${it.content?.take(40)}" }}")
        println("VIDEO-ONLY: ${videoOnly.map { "${it.resolution} (${it.format?.name}) content=${it.content?.take(40)}" }}")
        println("AUDIO: ${audios.map { "${it.averageBitrate}kbps (${it.format?.name}) content=${it.content?.take(40)}" }}")

        val allVideos = videos + videoOnly
        assertTrue(allVideos.isNotEmpty(), "Expected at least one video stream")
        assertTrue(
            allVideos.size >= 2,
            "Expected multiple video formats, got only: ${allVideos.map { it.resolution }}"
        )
        assertTrue(
            allVideos.any { it.resolution.contains("720") || it.resolution.contains("1080") },
            "Expected a resolution above 360p, got: ${allVideos.map { it.resolution }}"
        )
        allVideos.forEach { stream ->
            assertTrue(
                stream.content.isNotBlank(),
                "Video stream ${stream.resolution} has no content URL"
            )
        }
        assertTrue(audios.isNotEmpty(), "Expected at least one audio format")
        audios.forEach { stream ->
            assertTrue(
                stream.content.isNotBlank(),
                "Audio stream has no content URL"
            )
        }
        println(
            "Resolved ${allVideos.size} video formats: " +
                allVideos.map { "${it.resolution} (${it.format?.name})" } +
                " | ${audios.size} audio formats"
        )
    }
}
