package net.newpipe.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSelectionTest {
    @Test
    fun prefersProgressiveStreamAtOrBelow720p() {
        val progressive = listOf(
            PlaybackStreamCandidate("progressive-1080", 1080, adaptive = false),
            PlaybackStreamCandidate("progressive-720", 720, adaptive = false),
            PlaybackStreamCandidate("progressive-360", 360, adaptive = false)
        )
        val adaptive = listOf(PlaybackStreamCandidate("adaptive-1080", 1080, adaptive = true))

        assertEquals("progressive-720", selectFastStartStream(progressive, adaptive)?.stream)
    }

    @Test
    fun fallsBackToAdaptiveWhenNoProgressiveStreamExists() {
        val adaptive = listOf(
            PlaybackStreamCandidate("adaptive-1080", 1080, adaptive = true),
            PlaybackStreamCandidate("adaptive-720", 720, adaptive = true)
        )

        assertEquals("adaptive-720", selectFastStartStream(emptyList(), adaptive)?.stream)
    }

    @Test
    fun returnsNullWhenNoVideoStreamExists() {
        assertNull(selectFastStartStream<String>(emptyList(), emptyList()))
    }
}
