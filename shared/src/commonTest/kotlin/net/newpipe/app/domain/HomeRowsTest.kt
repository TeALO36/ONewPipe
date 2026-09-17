package net.newpipe.app.domain

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The themed rows of the home screen and the feeds behind the other tabs. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeRowsTest {

    private val dispatcher = StandardTestDispatcher()

    /** Returns canned pages, and fails one category to prove a row survives it. */
    private class FakeRepository : MediaRepository {
        var trendingCalls = 0
        override suspend fun getTrending(serviceId: Int, category: TrendingCategory): PageResult {
            trendingCalls++
            if (category == TrendingCategory.PODCASTS) throw IllegalStateException("boom")
            return PageResult(
                items = List(3) { index ->
                    MediaItem(
                        url = "https://example.org/${category.id}/$index",
                        title = "${category.label} $index",
                        uploaderName = "Uploader",
                        thumbnailUrl = "",
                        durationText = "1:00"
                    )
                }
            )
        }

        override suspend fun search(serviceId: Int, query: String, filter: SearchFilter) =
            PageResult(emptyList())

        override suspend fun getChannel(serviceId: Int, url: String) = PageResult(emptyList())

        override suspend fun loadMore(serviceId: Int, pageToken: String) = PageResult(emptyList())
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun homeRowsLoadEveryCategoryAndSurviveAFailingOne() = runTest(dispatcher) {
        val repository = FakeRepository()
        val viewModel = HomeViewModel(repository, SettingsViewModel(MapSettings()))
        advanceUntilIdle()

        val rows = viewModel.rows.value
        assertEquals(
            TrendingCategory.entries.filter { it != TrendingCategory.ALL }.map { it.category() },
            rows.map { it.category.category() },
            "every category except 'All' gets its own row"
        )
        assertTrue(rows.none { it.isLoading }, "every row finished loading")

        val gaming = rows.single { it.category == TrendingCategory.GAMING }
        assertEquals(3, gaming.items.size)

        val podcasts = rows.single { it.category == TrendingCategory.PODCASTS }
        assertTrue(podcasts.items.isEmpty())
        assertEquals("boom", podcasts.error, "a failing category reports its error instead of blocking the others")
    }

    @Test
    fun subscriptionFeedMergesEveryChannel() = runTest(dispatcher) {
        val repository = object : MediaRepository by FakeRepository() {
            override suspend fun getChannel(serviceId: Int, url: String) = PageResult(
                items = listOf(
                    MediaItem(
                        url = "$url/video",
                        title = "Video of $url",
                        uploaderName = "Uploader",
                        thumbnailUrl = "",
                        durationText = "1:00"
                    )
                )
            )
        }
        val viewModel = HomeViewModel(repository, SettingsViewModel(MapSettings()))
        advanceUntilIdle()

        viewModel.loadSubscriptionFeed(
            listOf(
                Subscription(url = "https://example.org/a", name = "A"),
                Subscription(url = "https://example.org/b", name = "B")
            )
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is HomeState.Success, "the feed should load")
        assertEquals(2, state.items.size, "one video per subscribed channel")
    }
}

/** Stable identity for the assertion message. */
private fun TrendingCategory.category(): String = id
