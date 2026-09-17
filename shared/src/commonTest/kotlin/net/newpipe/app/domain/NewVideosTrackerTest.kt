package net.newpipe.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewVideosTrackerTest {

    private val channel = Subscription(url = "https://youtube.com/channel/a", name = "Channel A")
    private val other = Subscription(url = "https://youtube.com/channel/b", name = "Channel B")

    private fun video(id: String) = MediaItem(
        url = "https://youtube.com/watch?v=$id",
        title = "Video $id",
        uploaderName = "",
        thumbnailUrl = "",
        durationText = ""
    )

    @Test
    fun firstCheckRecordsVideosWithoutNotifying() {
        val result = NewVideosTracker.compare(
            subscriptions = listOf(channel),
            latest = mapOf(channel.url to listOf(video("1"), video("2"))),
            seen = emptyMap()
        )
        assertTrue(result.newVideos.isEmpty())
        assertEquals(listOf(video("1").url, video("2").url), result.seen[channel.url])
    }

    @Test
    fun videosPublishedSinceThePreviousCheckAreNew() {
        val seen = mapOf(channel.url to listOf(video("1").url, video("2").url))
        val result = NewVideosTracker.compare(
            subscriptions = listOf(channel),
            latest = mapOf(channel.url to listOf(video("3"), video("1"), video("2"))),
            seen = seen
        )
        assertEquals(listOf(NewVideo(channel, video("3"))), result.newVideos)
        assertEquals(video("3").url, result.seen[channel.url]?.first())
    }

    @Test
    fun aChannelThatFailedToLoadKeepsItsState() {
        val seen = mapOf(channel.url to listOf(video("1").url), other.url to listOf(video("9").url))
        val result = NewVideosTracker.compare(
            subscriptions = listOf(channel, other),
            latest = mapOf(other.url to listOf(video("9"))),
            seen = seen
        )
        assertTrue(result.newVideos.isEmpty())
        assertEquals(listOf(video("1").url), result.seen[channel.url])
    }

    @Test
    fun unfollowedChannelsAreDroppedAndStateIsCapped() {
        val many = (1..50).map { video("$it") }
        val result = NewVideosTracker.compare(
            subscriptions = listOf(channel),
            latest = mapOf(channel.url to many),
            seen = mapOf(other.url to listOf(video("x").url))
        )
        assertEquals(setOf(channel.url), result.seen.keys)
        assertEquals(NewVideosTracker.MAX_SEEN_PER_CHANNEL, result.seen[channel.url]?.size)
    }

    @Test
    fun stateSurvivesEncoding() {
        val seen = mapOf(channel.url to listOf(video("1").url))
        assertEquals(seen, NewVideosTracker.decode(NewVideosTracker.encode(seen)))
        assertEquals(emptyMap(), NewVideosTracker.decode("not json"))
        assertEquals(emptyMap(), NewVideosTracker.decode(null))
    }
}
