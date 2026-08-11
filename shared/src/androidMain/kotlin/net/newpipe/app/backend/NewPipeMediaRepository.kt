package net.newpipe.app.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.MediaItemKind
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
                val tab = channel.tabs.firstOrNull()
                if (tab == null) {
                    PageResult(emptyList())
                } else {
                    val tabInfo = ChannelTabInfo.getInfo(service, tab)
                    PageResult(tabInfo.relatedItems.mapNotNull { it.toMediaItem() })
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

        // Store the next page for later pagination
        if (searchInfo.hasNextPage()) {
            if (pageId.startsWith("search:")) {
                searchPages[pageId] = searchInfo.nextPage
            } else {
                trendingPages[pageId] = searchInfo.nextPage
            }
        }

        val items = searchInfo.relatedItems.mapNotNull { item -> item.toMediaItem() }
            .sortedByDescending { it.viewCount }
            .distinctBy { it.url }

        return PageResult(
            items = items,
            nextPageToken = if (searchInfo.hasNextPage()) pageId else null
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
