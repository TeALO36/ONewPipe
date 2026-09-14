package net.newpipe.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behaviour behind the queue, repeat and speed buttons of the player. */
class PlayerQueueTest {

    private fun item(id: String) = MediaItem(
        url = "https://example.org/$id",
        title = "Video $id",
        uploaderName = "Uploader",
        thumbnailUrl = "",
        durationText = "1:00"
    )

    @Test
    fun queueIgnoresDuplicatesAndKeepsOrder() {
        val player = PlayerViewModel()

        player.enqueue(item("a"))
        player.enqueue(item("b"))
        player.enqueue(item("a"))

        assertEquals(listOf("Video a", "Video b"), player.queue.value.map { it.title })
    }

    @Test
    fun playNextPutsTheVideoFirst() {
        val player = PlayerViewModel()
        player.enqueue(item("a"))
        player.enqueue(item("b"))

        player.playNext(item("b"))

        assertEquals(listOf("Video b", "Video a"), player.queue.value.map { it.title })
    }

    @Test
    fun queueEntriesCanBeRemovedAndCleared() {
        val player = PlayerViewModel()
        player.enqueue(item("a"))
        player.enqueue(item("b"))

        player.removeFromQueue("https://example.org/a")
        assertEquals(listOf("Video b"), player.queue.value.map { it.title })

        player.clearQueue()
        assertTrue(player.queue.value.isEmpty())
    }

    @Test
    fun repeatButtonCyclesThroughEveryMode() {
        val player = PlayerViewModel()
        assertEquals(RepeatMode.OFF, player.repeatMode.value)

        player.cycleRepeatMode()
        assertEquals(RepeatMode.ALL, player.repeatMode.value)

        player.cycleRepeatMode()
        assertEquals(RepeatMode.ONE, player.repeatMode.value)

        player.cycleRepeatMode()
        assertEquals(RepeatMode.OFF, player.repeatMode.value)
    }

    @Test
    fun playbackSpeedStaysInASaneRange() {
        val player = PlayerViewModel()

        player.setPlaybackSpeed(1.5f)
        assertEquals(1.5f, player.playbackSpeed.value)

        player.setPlaybackSpeed(12f)
        assertEquals(3.0f, player.playbackSpeed.value)

        player.setPlaybackSpeed(0.01f)
        assertEquals(0.25f, player.playbackSpeed.value)
    }

    @Test
    fun nothingToPlayWhenTheQueueIsEmptyAndThereIsNoRelatedVideo() {
        val player = PlayerViewModel()

        assertFalse(player.playNextInQueue { null })
    }
}
