package net.newpipe.app.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import net.newpipe.app.theme.AppTheme
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * UI test for the download dialog: it must show every format family
 * (video with audio, video-only, audio) so users never see "only 360p".
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
                    onDownloadAudio = {}
                )
            }
        }

        onNodeWithText("Download: Test Video").assertIsDisplayed()
        onNodeWithText("Video (with audio)").assertIsDisplayed()
        onNodeWithText("Video only (no audio)").assertIsDisplayed()
        onNodeWithText("Audio").assertIsDisplayed()
        // All the resolutions the user complained were missing.
        onNodeWithText("360p - MPEG-4").assertIsDisplayed()
        onNodeWithText("1080p - MPEG-4").assertIsDisplayed()
        onNodeWithText("720p - WebM").assertIsDisplayed()
        onNodeWithText("128kbps - m4a").assertIsDisplayed()
    }
}
