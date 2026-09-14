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
    val uploaderUrl: String = "",
    val uploaderAvatarUrl: String = "",
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

/** One followed channel, synchronized between devices. */
@Serializable
data class SubscriptionDto(
    val url: String,
    val name: String = "",
    val thumbnailUrl: String = ""
)

/** One entry of a synchronized playlist or of the watch-later list. */
@Serializable
data class PlaylistItemDto(
    val url: String,
    val title: String = "",
    val uploaderName: String = "",
    val thumbnailUrl: String = "",
    val durationText: String = ""
)

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String,
    val items: List<PlaylistItemDto> = emptyList()
)

/**
 * The part of the local library that is worth sharing between devices:
 * subscriptions, playlists and the watch-later list. The watch positions keep
 * their own endpoint because they change constantly during playback.
 */
@Serializable
data class HistoryEntryDto(
    val url: String,
    val title: String = "",
    val uploaderName: String = "",
    val thumbnailUrl: String = "",
    val durationText: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val watchedAt: Long = 0
)

@Serializable
data class LibraryDto(
    val subscriptions: List<SubscriptionDto> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
    val watchLater: List<PlaylistItemDto> = emptyList(),
    val history: List<HistoryEntryDto> = emptyList(),
    val updatedAt: Long = 0
)
