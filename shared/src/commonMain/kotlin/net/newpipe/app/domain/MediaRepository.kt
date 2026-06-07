package net.newpipe.app.domain

data class MediaItem(
    val url: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationText: String,
    val isLive: Boolean = false
)

interface MediaRepository {
    suspend fun getTrending(): List<MediaItem>
    suspend fun search(query: String): List<MediaItem>
}
