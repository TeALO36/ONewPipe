package net.newpipe.app.backend

import kotlinx.coroutines.runBlocking
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
        val items = repository().getTrending(0, TrendingCategory.ALL)
        assertTrue(items.isNotEmpty(), "Expected trending items for 'All' category")
        println("All: got ${items.size} items")
        items.take(3).forEach { println("  - ${it.title} [${it.uploaderName}]") }
    }

    @Test
    fun `gaming category returns items`() = runBlocking {
        val items = repository().getTrending(0, TrendingCategory.GAMING)
        assertTrue(items.isNotEmpty(), "Expected trending items for 'Gaming' category")
        println("Gaming: got ${items.size} items")
        items.take(3).forEach { println("  - ${it.title} [${it.uploaderName}]") }
    }

    @Test
    fun `search returns items`() = runBlocking {
        val items = repository().search(0, "Linus Tech Tips")
        assertTrue(items.isNotEmpty(), "Expected search results")
        println("Search: got ${items.size} items")
        items.take(3).forEach { println("  - ${it.title} [${it.uploaderName}]") }
    }
}
