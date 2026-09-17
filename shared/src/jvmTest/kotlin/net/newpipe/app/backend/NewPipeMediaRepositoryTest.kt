package net.newpipe.app.backend

import kotlinx.coroutines.runBlocking
import net.newpipe.app.domain.MediaItemKind
import net.newpipe.app.domain.SearchFilter
import net.newpipe.app.domain.TrendingCategory
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live integration test for the trending-by-category feature.
 *
 * Requires network access to YouTube. The official trending kiosk is frequently
 * blocked by YouTube (400 / consent wall), so this asserts the search fallback
 * path returns actual content.
 */
class NewPipeMediaRepositoryTest {

    companion object {
        private val repo: NewPipeMediaRepository by lazy {
            NewPipe.init(
                OkHttpDownloader(OkHttpClient.Builder().build()),
                Localization("en", "US")
            )
            NewPipeMediaRepository()
        }
    }

    private fun repository(): NewPipeMediaRepository = repo

    @Test
    fun `all category returns items via fallback`() = runBlocking {
        val result = repository().getTrending(0, TrendingCategory.ALL)
        assertTrue(result.items.isNotEmpty(), "Expected trending items for 'All' category")
        println("All: got ${result.items.size} items, hasMore=${result.nextPageToken != null}")
        result.items.take(3).forEach { println("  - ${it.title} [${it.uploaderName}]") }
    }

    @Test
    fun `gaming category returns items`() = runBlocking {
        val result = repository().getTrending(0, TrendingCategory.GAMING)
        assertTrue(result.items.isNotEmpty(), "Expected trending items for 'Gaming' category")
        println("Gaming: got ${result.items.size} items, hasMore=${result.nextPageToken != null}")
        result.items.take(3).forEach { println("  - ${it.title} [${it.uploaderName}]") }
    }

    @Test
    fun `channel search returns channel items`() = runBlocking {
        val result = repository().search(0, "Linus Tech Tips", SearchFilter.CHANNELS)
        assertTrue(result.items.isNotEmpty(), "Expected channel search results")
        assertTrue(result.items.all { it.kind == MediaItemKind.CHANNEL }, "Expected only channels")
    }

    @Test
    fun `channel page exposes its header and keeps paginating`() = runBlocking {
        val channelUrl = repository().search(0, "Linus Tech Tips", SearchFilter.CHANNELS)
            .items
            .first()
            .url

        val page = repository().getChannel(0, channelUrl)
        val header = page.channel
        assertTrue(header != null, "Expected a channel header for $channelUrl")
        assertTrue(header.name.isNotBlank(), "Expected the channel name")
        assertTrue(page.items.isNotEmpty(), "Expected videos on the channel page")
        println("Channel: ${header.name} (${header.subscriberCount} subs), ${page.items.size} videos")

        val token = page.nextPageToken
        if (token != null) {
            val more = repository().loadMore(0, token)
            assertTrue(more.items.isNotEmpty(), "Expected a second page of channel videos")
            assertTrue(
                more.items.none { first -> page.items.any { it.url == first.url } },
                "The second page should bring new videos"
            )
        }
    }

    @Test
    fun `search returns items`() = runBlocking {
        val result = repository().search(0, "Linus Tech Tips")
        assertTrue(result.items.isNotEmpty(), "Expected search results")
        println("Search: got ${result.items.size} items, hasMore=${result.nextPageToken != null}")
        result.items.take(3).forEach { println("  - ${it.title} [${it.uploaderName}]") }
    }
}
