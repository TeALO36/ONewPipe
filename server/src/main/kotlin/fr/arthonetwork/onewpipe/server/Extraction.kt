package fr.arthonetwork.onewpipe.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/** Category names as used by the web UI and the apps. */
val CATEGORIES = listOf("all", "gaming", "music", "movies", "podcasts")

private fun categoryQuery(category: String): String = when (category) {
    "gaming" -> "trending gaming"
    "music" -> "trending music videos"
    "movies" -> "trending movies series trailers"
    "podcasts" -> "trending podcasts"
    else -> "trending"
}

suspend fun fetchTrending(serviceId: Int, category: String): List<MediaItemDto> =
    withContext(Dispatchers.IO) {
        val service = NewPipe.getService(serviceId)
        try {
            if (category == "all") {
                val kioskUrl = when (serviceId) {
                    0 -> "https://www.youtube.com/feed/trending"
                    1 -> "https://soundcloud.com/discover"
                    2 -> "https://media.ccc.de/c"
                    3 -> "https://framatube.org/videos/trending"
                    else -> "https://www.youtube.com/feed/trending"
                }
                val kioskInfo = KioskInfo.getInfo(service, kioskUrl)
                kioskInfo.relatedItems.mapNotNull { (it as? StreamInfoItem)?.toDto() }
                    .ifEmpty { searchForTrending(service, categoryQuery(category)) }
            } else {
                searchForTrending(service, categoryQuery(category))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            searchForTrending(service, categoryQuery(category))
        }
    }

private fun searchForTrending(
    service: org.schabi.newpipe.extractor.StreamingService,
    query: String
): List<MediaItemDto> {
    return search(service, query)
}

suspend fun searchMedia(serviceId: Int, query: String): List<MediaItemDto> =
    withContext(Dispatchers.IO) {
        search(NewPipe.getService(serviceId), query)
    }

private fun search(
    service: org.schabi.newpipe.extractor.StreamingService,
    query: String
): List<MediaItemDto> {
    val queryHandler = buildQueryHandler(service, query)
    val searchInfo = SearchInfo.getInfo(service, queryHandler)
    val seen = mutableSetOf<String>()
    return searchInfo.relatedItems.mapNotNull { item ->
        when (item) {
            is StreamInfoItem -> item.toDto()
            is ChannelInfoItem -> MediaItemDto(
                url = item.url,
                title = item.name,
                uploaderName = "Channel • ${item.subscriberCount} subs",
                thumbnailUrl = item.thumbnails.maxByOrNull { it.width }?.url
                    ?: item.thumbnails.firstOrNull()?.url ?: "",
                durationText = "",
                isLive = false,
                viewCount = 0
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
            ytFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), null)
        } else {
            service.searchQHFactory.fromQuery(query)
        }
    } catch (e: Exception) {
        service.searchQHFactory.fromQuery(query)
    }
}

suspend fun fetchVideoInfo(url: String): VideoInfoDto = withContext(Dispatchers.IO) {
    val service = NewPipe.getServiceByUrl(url)
        ?: throw IllegalArgumentException("No service found for URL")
    val info = StreamInfo.getInfo(service, url)
    val streamUrl = info.videoStreams
        ?.filter { !it.content.isNullOrEmpty() }
        ?.maxByOrNull { it.resolutionHeight() }
        ?.content
        ?: info.audioStreams?.firstOrNull { !it.content.isNullOrEmpty() }?.content
        ?: throw IllegalArgumentException("No playable stream found")

    VideoInfoDto(
        url = info.url ?: url,
        title = info.name ?: "Unknown",
        streamUrl = streamUrl,
        uploaderName = info.uploaderName ?: "",
        uploaderSubscriberCount = info.uploaderSubscriberCount ?: 0L,
        viewCount = info.viewCount ?: 0L,
        durationSeconds = info.duration ?: 0L,
        relatedItems = info.relatedItems.mapNotNull { (it as? StreamInfoItem)?.toDto() }
    )
}

private fun VideoStream.resolutionHeight(): Int =
    runCatching { resolution.takeWhile { it.isDigit() }.toInt() }.getOrDefault(0)

private fun StreamInfoItem.toDto(): MediaItemDto = MediaItemDto(
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
