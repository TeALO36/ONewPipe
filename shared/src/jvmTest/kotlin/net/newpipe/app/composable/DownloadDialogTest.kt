package net.newpipe.app.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import net.newpipe.app.theme.AppTheme
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * UI test for the download dialog: high-resolution video-only streams are
 * presented as packaged video+audio choices, never as useless raw video files.
 */
class DownloadDialogTest {

    private fun video(url: String, resolution: String, format: MediaFormat, videoOnly: Boolean) =
        VideoStream.Builder()
            .setId("itag-test")
            .setContent(url, true)
            .setMediaFormat(format)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setIsVideoOnly(videoOnly)
            .setResolution(resolution)
            .build()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsAllFormatSections() = runComposeUiTest {
        setContent {
            AppTheme(isPreview = true) {
                DownloadDialog(
                    videoStreams = listOf(
                        video("https://googlevideo.example/360p", "360p", MediaFormat.MPEG_4, false)
                    ),
                    videoOnlyStreams = listOf(
                        video("https://googlevideo.example/1080p", "1080p", MediaFormat.MPEG_4, true),
                        video("https://googlevideo.example/720p", "720p", MediaFormat.WEBM, true)
                    ),
                    audioStreams = listOf(
                        AudioStream.Builder()
                            .setId("itag-audio")
                            .setContent("https://googlevideo.example/audio", true)
                            .setMediaFormat(MediaFormat.M4A)
                            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                            .setAverageBitrate(128)
                            .build()
                    ),
                    title = "Test Video",
                    onDismiss = {},
                    onDownloadVideo = {},
                    onDownloadVideoWithAudio = { _, _ -> },
                    onDownloadAudio = {},
                    showCombinedVideoOptions = true
                )
            }
        }

        onNodeWithText("Download: Test Video").assertIsDisplayed()
        onNodeWithText("Video + audio").assertIsDisplayed()
        onNodeWithText("High quality · packaged MP4").performScrollTo().assertIsDisplayed()
        onNodeWithText("Audio only").performScrollTo().assertIsDisplayed()
        onNodeWithText("360p · MPEG-4").assertIsDisplayed()
        onNodeWithText("1080p · video + audio").performScrollTo().assertIsDisplayed()
        onNodeWithText("720p · video + audio").performScrollTo().assertIsDisplayed()
        onNodeWithText("128 kbps · m4a").performScrollTo().assertIsDisplayed()
    }
}
