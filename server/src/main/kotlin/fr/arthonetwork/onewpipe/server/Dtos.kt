package fr.arthonetwork.onewpipe.server

import kotlinx.serialization.Serializable

@Serializable
data class MediaItemDto(
    val url: String,
    val title: String,
    val uploaderName: String,
    val thumbnailUrl: String,
    val durationText: String,
    val isLive: Boolean = false,
    val viewCount: Long = 0
)

@Serializable
data class VideoInfoDto(
    val url: String,
    val title: String,
    val streamUrl: String,
    val uploaderName: String,
    val uploaderSubscriberCount: Long,
    val viewCount: Long,
    val durationSeconds: Long,
    val relatedItems: List<MediaItemDto> = emptyList()
)

@Serializable
data class AuthRequest(
    val username: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val username: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class WatchStateItem(
    val url: String,
    val title: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class WatchStateRequest(
    val items: List<WatchStateItem> = emptyList()
)

@Serializable
data class WatchStateResponse(
    val synced: Int,
    val items: List<WatchStateItem> = emptyList()
)
