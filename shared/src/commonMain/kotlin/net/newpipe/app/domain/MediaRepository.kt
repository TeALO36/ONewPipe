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

/** Paginated result from the repository. */
data class PageResult(
    val items: List<MediaItem>,
    val nextPageToken: String? = null
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
     * Returns the first page of trending items for the given service, scoped
     * to [category]. Implementations should try the service's official
     * trending/kiosk feed first and gracefully fall back to a search query.
     */
    suspend fun getTrending(serviceId: Int, category: TrendingCategory): PageResult

    /** Returns the first page of search results for the given query. */
    suspend fun search(serviceId: Int, query: String): PageResult

    /** Returns the next page using a token obtained from a previous [PageResult]. */
    suspend fun loadMore(serviceId: Int, pageToken: String): PageResult
}
