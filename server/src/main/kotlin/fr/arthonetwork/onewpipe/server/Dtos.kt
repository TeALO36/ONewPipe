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
data class VideoFormatDto(
    val label: String,
    val url: String
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
    val relatedItems: List<MediaItemDto> = emptyList(),
    /** Progressive formats include audio and can be switched/downloaded by the web player. */
    val videoFormats: List<VideoFormatDto> = emptyList()
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
data class AdminAccountDto(
    val username: String,
    val createdAt: Long,
    val isAdmin: Boolean,
    val watchStateCount: Int
)

@Serializable
data class AdminOverviewDto(
    val currentUsername: String,
    val accounts: List<AdminAccountDto>,
    val storeFileBytes: Long
)

@Serializable
data class AdminPasswordRequest(
    val password: String
)

@Serializable
data class AdminAccountRequest(
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
