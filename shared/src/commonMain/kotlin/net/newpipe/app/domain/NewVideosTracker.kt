package net.newpipe.app.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A video published on a followed channel since the previous check. */
data class NewVideo(val channel: Subscription, val video: MediaItem)

/**
 * Finds the videos published on followed channels since the previous check,
 * for the "new videos" notification.
 *
 * The state is, per channel URL, the URLs of the videos already seen. A channel
 * checked for the first time only records its current videos: announcing the
 * whole catalogue of a channel that was just followed would be noise.
 */
object NewVideosTracker {
    /** Settings key holding the seen videos, as JSON. */
    const val KEY_SEEN = "new_videos_seen"

    /** Seen URLs kept per channel; enough to cover the videos a check loads. */
    const val MAX_SEEN_PER_CHANNEL = 30

    data class Result(
        val newVideos: List<NewVideo>,
        val seen: Map<String, List<String>>
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param latest newest videos per channel URL; a channel missing from the
     *   map failed to load and keeps its previous state
     * @param seen state from the previous check
     */
    fun compare(
        subscriptions: List<Subscription>,
        latest: Map<String, List<MediaItem>>,
        seen: Map<String, List<String>>
    ): Result {
        val newVideos = mutableListOf<NewVideo>()
        val updated = mutableMapOf<String, List<String>>()
        for (subscription in subscriptions.distinctBy { it.url }) {
            val previous = seen[subscription.url]
            val items = latest[subscription.url]?.filter { it.url.isNotBlank() }
            if (items == null) {
                previous?.let { updated[subscription.url] = it }
                continue
            }
            if (previous != null) {
                items.filter { it.url !in previous }
                    .distinctBy { it.url }
                    .forEach { newVideos += NewVideo(subscription, it) }
            }
            updated[subscription.url] = (items.map { it.url } + previous.orEmpty())
                .distinct()
                .take(MAX_SEEN_PER_CHANNEL)
        }
        // Channels that are no longer followed are dropped from the state.
        return Result(newVideos, updated)
    }

    fun encode(seen: Map<String, List<String>>): String = json.encodeToString(seen)

    fun decode(raw: String?): Map<String, List<String>> =
        runCatching { json.decodeFromString<Map<String, List<String>>>(raw.orEmpty()) }
            .getOrDefault(emptyMap())
}
