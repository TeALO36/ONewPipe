package org.schabi.newpipe.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.File
import net.newpipe.app.backend.NativeDownloadRequest
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.streams.io.StoredDirectoryHelper
import org.schabi.newpipe.streams.io.StoredFileHelper
import us.shandian.giga.get.MissionRecoveryInfo
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService

/**
 * Adapter for the modern Compose UI.
 *
 * The actual mission, persistence, range requests, pause/resume controls,
 * foreground notification and muxing are all kept in NewPipe's existing
 * DownloadManagerService. This class only translates the selected streams into
 * the service's public startMission() contract.
 */
object ComposeDownloadManager {
    private const val TAG = "ComposeDownloadManager"

    fun enqueue(context: Context, request: NativeDownloadRequest) {
        val videoUrl = request.videoUrl?.takeIf { it.isNotBlank() }
        val audioUrl = request.audioUrl?.takeIf { it.isNotBlank() }
        val isVideo = videoUrl != null
        val isCombined = videoUrl != null && audioUrl != null
        // Use the extractor object itself. MediaFormat.name is a display label
        // (for example "MPEG-4"), not a stable valueOf() identifier.
        val videoFormat = request.videoFormat ?: parseFormat(request.videoFormatName)
        val audioFormat = request.audioFormat ?: parseFormat(request.audioFormatName)
        var storage: StoredFileHelper? = null

        try {
            require(videoUrl != null || audioUrl != null) {
                "No downloadable stream was selected"
            }
            if (isCombined) {
                require(videoFormat == MediaFormat.MPEG_4 || videoFormat == MediaFormat.WEBM) {
                    "This video format cannot be muxed by NewPipe"
                }
                require(audioFormat == MediaFormat.M4A || audioFormat == MediaFormat.WEBMA || audioFormat == MediaFormat.WEBMA_OPUS) {
                    "This audio format cannot be muxed by NewPipe"
                }
                if (videoFormat == MediaFormat.MPEG_4) {
                    require(audioFormat == MediaFormat.M4A) {
                        "MP4 video requires M4A audio"
                    }
                } else {
                    require(audioFormat == MediaFormat.WEBMA || audioFormat == MediaFormat.WEBMA_OPUS) {
                        "WebM video requires WebM audio"
                    }
                }
            }

            val mime = when {
                isVideo && videoFormat == MediaFormat.WEBM -> "video/webm"
                isVideo -> "video/mp4"
                audioFormat == MediaFormat.WEBMA_OPUS -> "audio/ogg"
                audioFormat == MediaFormat.M4A -> "audio/mp4"
                else -> "audio/mpeg"
            }
            val extension = when {
                isVideo && videoFormat == MediaFormat.WEBM -> ".webm"
                isVideo -> ".mp4"
                audioFormat == MediaFormat.WEBMA_OPUS -> ".opus"
                audioFormat == MediaFormat.M4A -> ".m4a"
                else -> ".mp3"
            }
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "ONewPipe"
            )
            val directoryHelper = StoredDirectoryHelper(
                context,
                Uri.fromFile(directory),
                if (isVideo) DownloadManager.TAG_VIDEO else DownloadManager.TAG_AUDIO
            )
            require(directoryHelper.mkdirs()) { "Unable to create ${directory.absolutePath}" }

            val baseName = request.defaultName
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .removeSuffix(".mp4")
                .removeSuffix(".webm")
                .removeSuffix(".m4a")
                .removeSuffix(".opus")
                .ifBlank { "ONewPipe-download" }
            storage = directoryHelper.createUniqueFile(baseName + extension, mime)
            require(storage != null && storage.canWrite()) {
                "Unable to create a file in ${directory.absolutePath}"
            }

            val urls = if (isCombined) {
                arrayOf(videoUrl, audioUrl)
            } else if (videoUrl != null) {
                arrayOf(videoUrl)
            } else {
                arrayOf(audioUrl!!)
            }

            val postprocessing = when {
                isCombined && videoFormat == MediaFormat.MPEG_4 ->
                    Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER

                isCombined -> Postprocessing.ALGORITHM_WEBM_MUXER

                !isVideo && audioFormat == MediaFormat.M4A ->
                    Postprocessing.ALGORITHM_M4A_NO_DASH

                !isVideo && audioFormat == MediaFormat.WEBMA_OPUS ->
                    Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER

                else -> null
            }

            val recovery = ArrayList<MissionRecoveryInfo>(2)
            if (videoUrl != null) {
                recovery += MissionRecoveryInfo(
                    format = videoFormat,
                    desired = request.videoResolution,
                    isDesired2 = isCombined,
                    kind = 'v'
                )
            }
            if (audioUrl != null) {
                recovery += MissionRecoveryInfo(
                    format = audioFormat,
                    desiredBitrate = request.audioBitrate,
                    kind = 'a'
                )
            }

            DownloadManagerService.startMission(
                context,
                urls,
                storage,
                if (isVideo) 'v' else 'a',
                3,
                request.streamInfo,
                postprocessing,
                null,
                0L,
                recovery
            )

            Toast.makeText(
                context,
                "Download started: ${storage.getName()}",
                Toast.LENGTH_SHORT
            ).show()
            Log.i(TAG, "Native mission started for ${storage.getUri()} postprocessing=$postprocessing")
        } catch (error: Exception) {
            storage?.delete()
            Log.e(TAG, "Could not start native download", error)
            Toast.makeText(
                context,
                "Could not start download: ${error.message ?: "storage error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun parseFormat(name: String?): MediaFormat? {
        val identifier = name
            ?.trim()
            ?.uppercase()
            ?.replace('-', '_')
            ?.replace(' ', '_')
            ?: return null
        return runCatching { MediaFormat.valueOf(identifier) }.getOrNull()
    }
}
