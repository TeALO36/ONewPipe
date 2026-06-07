package net.newpipe.app.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.MediaRepository
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class NewPipeMediaRepository : MediaRepository {
    override suspend fun getTrending(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val kioskUrl = "https://www.youtube.com/feed/trending"
            val items = try {
                val kioskInfo = KioskInfo.getInfo(ServiceList.YouTube, kioskUrl)
                kioskInfo.relatedItems.mapNotNull { item ->
                    if (item is StreamInfoItem) {
                        MediaItem(
                            url = item.url,
                            title = item.name,
                            uploaderName = item.uploaderName,
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                            durationText = formatDuration(item.duration),
                            isLive = false
                        )
                    } else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to a search query if Trending parsing is broken
                search("Trending music 2026")
            }
            items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.searchQHFactory.fromQuery(query))
            searchInfo.relatedItems.mapNotNull { item ->
                if (item is StreamInfoItem) {
                    MediaItem(
                        url = item.url,
                        title = item.name,
                        uploaderName = item.uploaderName,
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                        durationText = formatDuration(item.duration),
                        isLive = false
                    )
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
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
