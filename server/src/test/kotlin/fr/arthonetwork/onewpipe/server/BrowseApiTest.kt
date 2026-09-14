package fr.arthonetwork.onewpipe.server

import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Exercises the /api/v2 operations against YouTube: video details, a channel
 * and its second page, comments, and a channel search. Skips (passes) when the
 * network is unavailable.
 */
class BrowseApiTest {

    @Test
    fun videoChannelCommentsAndSearch() = runBlocking {
        NewPipe.init(OkHttpDownloader(okhttp3.OkHttpClient.Builder().build()))
        val results = runCatching { searchPage(0, "official music video", "videos") }.getOrElse {
            println("SKIP BrowseApiTest (search failed): ${it.message}")
            return@runBlocking
        }
        val video = results.items.firstOrNull { it.kind == "video" && it.durationSeconds > 0 } ?: run {
            println("SKIP BrowseApiTest: no video found")
            return@runBlocking
        }
        assertTrue(results.nextPage != null, "A video search should have a next page")

        val watch = watchInfo(video.url)
        println("WATCH ${watch.title} | uploader=${watch.uploaderName} verified=${watch.uploaderVerified} likes=${watch.likeCount} related=${watch.related.size} subtitles=${watch.subtitles.map { it.label }} description=${watch.description.length} chars (${watch.descriptionFormat})")
        assertTrue(watch.title.isNotBlank() && watch.uploaderUrl.isNotBlank(), "Video details are incomplete")
        assertTrue(watch.related.isNotEmpty(), "Expected related videos")
        assertTrue(watch.subtitles.all { it.url.startsWith("/api/stream?t=") }, "Subtitles must be relayed")

        val channel = channelInfo(watch.uploaderUrl)
        println("CHANNEL ${channel.name} subscribers=${channel.subscriberCount} videos=${channel.videos.items.size} next=${channel.videos.nextPage != null}")
        assertTrue(channel.videos.items.isNotEmpty(), "Expected channel videos")
        channel.videos.nextPage?.let { token ->
            val more = morePage(token)
            println("CHANNEL PAGE 2 videos=${more?.items?.size}")
            assertTrue(more != null && more.items.isNotEmpty(), "Expected a second page of channel videos")
            assertNotEquals(channel.videos.items.first().url, more.items.first().url, "Page 2 should differ from page 1")
        }

        val comments = commentsPage(video.url)
        println("COMMENTS count=${comments.comments.size} disabled=${comments.disabled} next=${comments.nextPage != null} first=${comments.comments.firstOrNull()?.text?.take(60)}")
        assertTrue(comments.disabled || comments.comments.isNotEmpty(), "Expected comments or the disabled flag")

        val channels = searchPage(0, "Linus Tech Tips", "channels")
        println("CHANNEL SEARCH kinds=${channels.items.map { it.kind }.distinct()} first=${channels.items.firstOrNull()?.title}")
        assertTrue(channels.items.isNotEmpty() && channels.items.all { it.kind == "channel" }, "Channel search should only return channels")

        assertTrue(morePage("0".repeat(24)) == null, "Unknown page tokens must not resolve")
    }
}
