package net.newpipe.app.domain

data class MediaItem(
    val url: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationText: String,
    val isLive: Boolean = false,
    val viewCount: Long = 0
)

/**
 * Trending categories shown on the home screen.
 *
 * The official YouTube trending kiosk is frequently blocked by YouTube (it returns a 400
 * or a consent wall depending on the IP/region), so each category falls back to a
 * curated search query that reliably returns popular, fresh content.
 */
enum class TrendingCategory(
    val id: String,
    val label: String,
    val fallbackQuery: String
) {
    ALL("all", "All", "trending"),
    GAMING("gaming", "Gaming", "trending gaming"),
    MUSIC("music", "Music", "trending music videos"),
    MOVIES_SERIES("movies", "Movies & Series", "trending movies series trailers"),
    PODCASTS("podcasts", "Podcasts", "trending podcasts")
}

interface MediaRepository {
    /**
     * Returns trending items for the given service, scoped to [category].
     * Implementations should try the service's official trending/kiosk feed first
     * and gracefully fall back to a search query when the kiosk is unavailable.
     */
    suspend fun getTrending(serviceId: Int, category: TrendingCategory): List<MediaItem>
    suspend fun search(serviceId: Int, query: String): List<MediaItem>
}
