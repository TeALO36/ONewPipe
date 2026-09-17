package net.newpipe.app.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.MediaItemKind
import net.newpipe.app.domain.ChannelHeader
import net.newpipe.app.domain.MediaRepository
import net.newpipe.app.domain.PageResult
import net.newpipe.app.domain.SearchFilter
import net.newpipe.app.domain.TrendingCategory
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class NewPipeMediaRepository : MediaRepository {

    // Cache pagination states keyed by operation identifier
    private val searchPages = mutableMapOf<String, Page>()
    private val trendingPages = mutableMapOf<String, Page>()
    private val channelPages = mutableMapOf<String, ChannelPage>()

    /** A channel tab plus the page to request next. */
    private data class ChannelPage(
        val handler: org.schabi.newpipe.extractor.linkhandler.ListLinkHandler,
        val page: Page
    )

    override suspend fun getTrending(
        serviceId: Int,
        category: TrendingCategory
    ): PageResult = withContext(Dispatchers.IO) {
        try {
            val service = NewPipe.getService(serviceId)

            // Try the official trending/kiosk feed first for ALL category
            if (category == TrendingCategory.ALL) {
                val kioskResult = tryFetchKiosk(serviceId, service)
                if (kioskResult.items.isNotEmpty()) return@withContext kioskResult
            }

            // Fallback: curated search query that reliably returns popular content
            searchWithPagination(service, category.fallbackQuery, "trending:${category.id}")
        } catch (e: Exception) {
            e.printStackTrace()
            PageResult(emptyList())
        }
    }

    override suspend fun search(
        serviceId: Int,
        query: String,
        filter: SearchFilter
    ): PageResult = withContext(Dispatchers.IO) {
            try {
                searchWithPagination(
                    NewPipe.getService(serviceId),
                    query,
                    "search:${filter.name}:$query",
                    filter
                )
            } catch (e: Exception) {
                e.printStackTrace()
                PageResult(emptyList())
            }
        }

    override suspend fun getChannel(serviceId: Int, url: String): PageResult =
        withContext(Dispatchers.IO) {
            try {
                val service = NewPipe.getService(serviceId)
                val channel = ChannelInfo.getInfo(service, url)
                val header = ChannelHeader(
                    url = channel.url ?: url,
                    name = channel.name.orEmpty(),
                    avatarUrl = channel.avatars?.firstOrNull()?.url.orEmpty(),
                    bannerUrl = channel.banners?.firstOrNull()?.url.orEmpty(),
                    subscriberCount = channel.subscriberCount,
                    description = channel.description.orEmpty(),
                    verified = channel.isVerified
                )
                val tab = channel.tabs.firstOrNull()
                if (tab == null) {
                    PageResult(emptyList(), channel = header)
                } else {
                    val tabInfo = ChannelTabInfo.getInfo(service, tab)
                    // Keep the tab handler so the grid can keep scrolling: a
                    // channel page used to stop after its first page.
                    val pageId = "channel:${channel.url ?: url}"
                    val nextPage = tabInfo.nextPage
                    if (nextPage != null && Page.isValid(nextPage)) {
                        channelPages[pageId] = ChannelPage(tab, nextPage)
                    } else {
                        channelPages.remove(pageId)
                    }
                    PageResult(
                        items = tabInfo.relatedItems.mapNotNull { it.toMediaItem() },
                        nextPageToken = if (tabInfo.hasNextPage()) pageId else null,
                        channel = header
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                PageResult(emptyList())
            }
        }

    override suspend fun loadMore(serviceId: Int, pageToken: String): PageResult =
        withContext(Dispatchers.IO) {
            try {
                val service = NewPipe.getService(serviceId)

                if (pageToken.startsWith("channel:")) {
                    val channelPage = channelPages[pageToken]
                        ?: return@withContext PageResult(emptyList())
                    val tabInfo = ChannelTabInfo.getMoreItems(service, channelPage.handler, channelPage.page)
                    val nextPage = tabInfo.nextPage
                    if (nextPage != null && Page.isValid(nextPage)) {
                        channelPages[pageToken] = channelPage.copy(page = nextPage)
                    } else {
                        channelPages.remove(pageToken)
                    }
                    return@withContext PageResult(
                        items = tabInfo.items.mapNotNull { it.toMediaItem() },
                        nextPageToken = if (tabInfo.hasNextPage()) pageToken else null
                    )
                }

                val page = trendingPages[pageToken] ?: searchPages[pageToken]
                if (page == null || !Page.isValid(page)) {
                    return@withContext PageResult(emptyList())
                }

                val isKiosk = pageToken.startsWith("kiosk:")
                val result = if (isKiosk) {
                    val kioskUrl = pageToken.removePrefix("kiosk:")
                    KioskInfo.getMoreItems(service, kioskUrl, page)
                } else {
                    val query = if (pageToken.startsWith("search:")) {
                        pageToken.removePrefix("search:").split(":", limit = 3).last()
                    } else if (pageToken.startsWith("trending:")) {
                        TrendingCategory.entries.find { it.id == pageToken.removePrefix("trending:") }
                            ?.fallbackQuery ?: "trending"
                    } else {
                        pageToken
                    }
                    val searchParts = pageToken.removePrefix("search:").split(":", limit = 3)
                    val filter = searchParts.firstOrNull()?.let { name ->
                        runCatching { SearchFilter.valueOf(name) }.getOrDefault(SearchFilter.ALL)
                    } ?: SearchFilter.ALL
                    val queryHandler = buildQueryHandler(service, query, filter)
                    SearchInfo.getMoreItems(service, queryHandler, page)
                }

                // Store next page token
                val nextPage = result.nextPage
                if (nextPage != null) {
                    if (isKiosk) {
                        trendingPages[pageToken] = nextPage
                    } else {
                        searchPages[pageToken] = nextPage
                    }
                }

                PageResult(
                    items = result.items.mapNotNull { it.toMediaItem() },
                    nextPageToken = if (result.hasNextPage()) pageToken else null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                PageResult(emptyList())
            }
        }

    private fun searchWithPagination(
        service: org.schabi.newpipe.extractor.StreamingService,
        query: String,
        pageId: String,
        filter: SearchFilter = SearchFilter.ALL
    ): PageResult {
        val queryHandler = buildQueryHandler(service, query, filter)
        val searchInfo = SearchInfo.getInfo(service, queryHandler)

        val collected = searchInfo.relatedItems.mapNotNull { item -> item.toMediaItem() }.toMutableList()

        // One search page can be mostly playlists or channels, which leaves a
        // nearly empty category grid. Pull a couple of extra pages until the
        // grid is worth showing.
        var nextPage = if (searchInfo.hasNextPage()) searchInfo.nextPage else null
        var extraPages = 0
        while (collected.distinctBy { it.url }.size < MIN_CATEGORY_ITEMS &&
            nextPage != null &&
            Page.isValid(nextPage) &&
            extraPages < MAX_EXTRA_PAGES
        ) {
            extraPages++
            val more = runCatching { SearchInfo.getMoreItems(service, queryHandler, nextPage) }.getOrNull()
                ?: break
            collected += more.items.mapNotNull { item -> item.toMediaItem() }
            nextPage = if (more.hasNextPage()) more.nextPage else null
        }

        if (nextPage != null && Page.isValid(nextPage)) {
            if (pageId.startsWith("search:")) {
                searchPages[pageId] = nextPage
            } else {
                trendingPages[pageId] = nextPage
            }
        } else {
            searchPages.remove(pageId)
            trendingPages.remove(pageId)
        }

        val items = collected
            .distinctBy { it.url }
            .sortedByDescending { it.viewCount }

        return PageResult(
            items = items,
            nextPageToken = if (nextPage != null && Page.isValid(nextPage)) pageId else null
        )
    }

    private fun tryFetchKiosk(
        serviceId: Int,
        service: org.schabi.newpipe.extractor.StreamingService
    ): PageResult {
        val kioskUrl = when (serviceId) {
            0 -> "https://www.youtube.com/feed/trending"
            1 -> "https://soundcloud.com/discover"
            2 -> "https://media.ccc.de/c"
            3 -> "https://framatube.org/videos/trending"
            4 -> "https://bandcamp.com"
            else -> "https://www.youtube.com/feed/trending"
        }
        return try {
            val kioskInfo = KioskInfo.getInfo(service, kioskUrl)

            // Store next page for pagination
            val pageId = "kiosk:$kioskUrl"
            val nextPage = kioskInfo.nextPage
            if (nextPage != null) {
                trendingPages[pageId] = nextPage
            }

            PageResult(
                items = kioskInfo.relatedItems.mapNotNull { item ->
                    if (item is StreamInfoItem) item.toMediaItem() else null
                },
                nextPageToken = if (kioskInfo.hasNextPage()) pageId else null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            PageResult(emptyList())
        }
    }

    private fun buildQueryHandler(
        service: org.schabi.newpipe.extractor.StreamingService,
        query: String,
        filter: SearchFilter = SearchFilter.ALL
    ): SearchQueryHandler {
        return try {
            val ytFactory = service.searchQHFactory as? YoutubeSearchQueryHandlerFactory
            if (ytFactory != null) {
                val contentFilter = when (filter) {
                    SearchFilter.ALL -> YoutubeSearchQueryHandlerFactory.ALL
                    SearchFilter.VIDEOS -> YoutubeSearchQueryHandlerFactory.VIDEOS
                    SearchFilter.CHANNELS -> YoutubeSearchQueryHandlerFactory.CHANNELS
                }
                ytFactory.fromQuery(query, listOf(contentFilter), null)
            } else {
                service.searchQHFactory.fromQuery(query)
            }
        } catch (e: Exception) {
            service.searchQHFactory.fromQuery(query)
        }
    }

    private companion object {
        /** A category grid looks broken below this many videos. */
        const val MIN_CATEGORY_ITEMS = 16
        const val MAX_EXTRA_PAGES = 3
    }

    private fun InfoItem.toMediaItem(): MediaItem? = when (this) {
        is StreamInfoItem -> MediaItem(
            url = url,
            title = name,
            uploaderName = uploaderName,
            thumbnailUrl = thumbnails.maxByOrNull { it.width }?.url
                ?: thumbnails.firstOrNull()?.url ?: "",
            durationText = formatDuration(duration),
            isLive = false,
            viewCount = viewCount
        )
        is ChannelInfoItem -> MediaItem(
                url = url,
                title = name,
                uploaderName = "Channel • ${subscriberCount} subs",
                thumbnailUrl = thumbnails.maxByOrNull { it.width }?.url
                    ?: thumbnails.firstOrNull()?.url ?: "",
                durationText = "",
                isLive = false,
                kind = MediaItemKind.CHANNEL
            )

        else -> null
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            "${hours}:${remainingMinutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
        } else {
            "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
        }
    }
}
