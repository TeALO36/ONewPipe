/*
 * SPDX-FileCopyrightText: 2026 ONewPipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.composable

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import net.newpipe.app.domain.DownloadViewModel
import net.newpipe.app.domain.PlayerState
import net.newpipe.app.domain.PlayerViewModel
import net.newpipe.app.domain.QualityLoadState
import net.newpipe.app.theme.AppTheme

@RunWith(AndroidJUnit4::class)
class QualityMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun opensQualityMenuAndSelectsEveryAvailableProfile() {
        val selectedProfiles = mutableListOf<Pair<String, String?>>()
        val profiles = listOf(
            "144p · Low" to "https://example.test/video/144p",
            "360p · Standard" to "https://example.test/video/360p",
            "480p · Enhanced" to "https://example.test/video/480p",
            "720p · HD" to "https://example.test/video/720p",
            "2160p · Full HD" to "https://example.test/video/2160p"
        )
        val audioUrl = "https://example.test/audio/m4a"

        composeRule.setContent {
            AppTheme(isPreview = true) {
                VideoDetailsContent(
                    state = playingState(),
                    playerViewModel = PlayerViewModel(),
                    downloadViewModel = DownloadViewModel(),
                    onQualitySelected = { videoUrl, selectedAudioUrl ->
                        selectedProfiles += videoUrl to selectedAudioUrl
                    }
                )
            }
        }

        composeRule.onNodeWithText("Quality").performClick()
        profiles.forEach { (label, _) ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }

        profiles.forEach { (label, videoUrl) ->
            composeRule.onNodeWithText("Quality").performClick()
            composeRule.onNodeWithText(label).performClick()
            composeRule.runOnIdle {
                assertEquals(videoUrl, selectedProfiles.last().first)
                assertEquals(audioUrl, selectedProfiles.last().second)
            }
        }

        assertEquals(profiles.map { it.second to audioUrl }, selectedProfiles)
    }

    @Test
    fun showsQualityLoadingIndicator() {
        render(QualityLoadState(isLoading = true))

        composeRule.onNodeWithText("Quality").performClick()
        composeRule.onNodeWithText("Loading quality profiles…").assertIsDisplayed()
        composeRule.onNodeWithText("Fetching HD and 4K formats").assertIsDisplayed()
    }

    @Test
    fun showsDetailedQualityLoadingErrorAndRetryAction() {
        render(
            QualityLoadState(
                errorMessage =
                    "Unable to load HD/4K profiles (IOException): HTTP 403 from the extractor"
            )
        )

        composeRule.onNodeWithText("Quality").performClick()
        composeRule.onNodeWithText("Quality loading failed").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Unable to load HD/4K profiles (IOException): HTTP 403 from the extractor"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Tap to retry").assertIsDisplayed()
    }

    private fun render(qualityLoadState: QualityLoadState) {
        composeRule.setContent {
            AppTheme(isPreview = true) {
                VideoDetailsContent(
                    state = playingState(),
                    playerViewModel = PlayerViewModel(),
                    downloadViewModel = DownloadViewModel(),
                    qualityLoadStateOverride = qualityLoadState
                )
            }
        }
    }

    private fun playingState(): PlayerState.Playing {
        val videoOnlyStreams = listOf(144, 360, 480, 720, 1080, 2160).map { height ->
            VideoStream.Builder()
                .setId("video-$height")
                .setContent("https://example.test/video/${height}p", true)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .setIsVideoOnly(true)
                .setResolution("${height}p")
                .build()
        }
        val audioStream = AudioStream.Builder()
            .setId("audio-m4a")
            .setContent("https://example.test/audio/m4a", true)
            .setMediaFormat(MediaFormat.M4A)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(128)
            .build()

        return PlayerState.Playing(
            title = "Quality test video",
            originalUrl = "",
            streamUrl = videoOnlyStreams[0].content,
            audioUrl = audioStream.content,
            uploaderName = "Test channel",
            uploaderSubscriberCount = 0,
            viewCount = 0,
            relatedItems = emptyList(),
            videoStreams = emptyList(),
            videoOnlyStreams = videoOnlyStreams,
            audioStreams = listOf(audioStream)
        )
    }
}
