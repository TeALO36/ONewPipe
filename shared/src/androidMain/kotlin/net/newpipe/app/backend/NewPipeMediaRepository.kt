package net.newpipe.app.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.MediaRepository
import net.newpipe.app.domain.TrendingCategory
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class NewPipeMediaRepository : MediaRepository {

    override suspend fun getTrending(
        serviceId: Int,
        category: TrendingCategory
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val service = NewPipe.getService(serviceId)

            // Try the official trending/kiosk feed first, but only for the generic
            // "All" category: the YouTube kiosk has no per-category tabs and it is
            // frequently blocked by YouTube (400 / consent wall).
            if (category == TrendingCategory.ALL) {
                val kioskItems = tryFetchKiosk(serviceId, service)
                if (kioskItems.isNotEmpty()) return@withContext kioskItems
            }

            // Fallback: curated search query that reliably returns popular content.
            searchItems(service, category.fallbackQuery)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun search(serviceId: Int, query: String): List<MediaItem> =
        withContext(Dispatchers.IO) {
            try {
                searchItems(NewPipe.getService(serviceId), query)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    private fun tryFetchKiosk(
        serviceId: Int,
        service: org.schabi.newpipe.extractor.StreamingService
    ): List<MediaItem> {
        val kioskUrl = when (serviceId) {
            0 -> "https://www.youtube.com/feed/trending" // YouTube
            1 -> "https://soundcloud.com/discover" // SoundCloud
            2 -> "https://media.ccc.de/c" // MediaCCC
            3 -> "https://framatube.org/videos/trending" // PeerTube
            4 -> "https://bandcamp.com" // Bandcamp
            else -> "https://www.youtube.com/feed/trending"
        }
        return try {
            val kioskInfo = KioskInfo.getInfo(service, kioskUrl)
            kioskInfo.relatedItems.mapNotNull { item ->
                if (item is StreamInfoItem) item.toMediaItem() else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun searchItems(
        service: org.schabi.newpipe.extractor.StreamingService,
        query: String
    ): List<MediaItem> {
        val queryHandler = buildQueryHandler(service, query)
        val searchInfo = SearchInfo.getInfo(service, queryHandler)
        // Sort by view count (descending) and dedupe by URL so the category tabs
        // surface the most popular content first, which is what "trending" is about.
        val seen = mutableSetOf<String>()
        return searchInfo.relatedItems.mapNotNull { item ->
            when (item) {
                is StreamInfoItem -> item.toMediaItem()
                is ChannelInfoItem -> MediaItem(
                    url = item.url,
                    title = item.name,
                    uploaderName = "Channel • ${item.subscriberCount} subs",
                    thumbnailUrl = item.thumbnails.maxByOrNull { it.width }?.url
                        ?: item.thumbnails.firstOrNull()?.url ?: "",
                    durationText = "",
                    isLive = false
                )
                else -> null
            }
        }.sortedByDescending { it.viewCount }.filter { seen.add(it.url) }
    }

    private fun buildQueryHandler(
        service: org.schabi.newpipe.extractor.StreamingService,
        query: String
    ): SearchQueryHandler {
        return try {
            val ytFactory = service.searchQHFactory as? YoutubeSearchQueryHandlerFactory
            if (ytFactory != null) {
                // Restrict to videos only so the category tabs show actual videos
                // instead of channels and playlists.
                ytFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), null)
            } else {
                service.searchQHFactory.fromQuery(query)
            }
        } catch (e: Exception) {
            service.searchQHFactory.fromQuery(query)
        }
    }

    private fun StreamInfoItem.toMediaItem(): MediaItem = MediaItem(
        url = url,
        title = name,
        uploaderName = uploaderName,
        thumbnailUrl = thumbnails.maxByOrNull { it.width }?.url
            ?: thumbnails.firstOrNull()?.url ?: "",
        durationText = formatDuration(duration),
        isLive = false,
        viewCount = viewCount
    )

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
