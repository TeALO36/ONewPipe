package net.newpipe.app.backend

import javax.swing.JOptionPane
import javax.swing.SwingUtilities

actual fun supportsCombinedVideoDownload(): Boolean = false

actual fun downloadWithNativeManager(request: NativeDownloadRequest) {
    val isCombined = !request.videoUrl.isNullOrBlank() && !request.audioUrl.isNullOrBlank()
    if (!isCombined) {
        val url = request.videoUrl ?: request.audioUrl
        if (!url.isNullOrBlank()) {
            val extension = when {
                request.videoFormatName?.contains("WEBM", ignoreCase = true) == true -> ".webm"
                request.audioFormatName?.contains("WEBM", ignoreCase = true) == true -> ".webm"
                else -> ".mp4"
            }
            downloadFile(url, request.defaultName + extension)
        }
        return
    }

    SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(
            null,
            "Combining video and audio is currently available on Android only.",
            "Download unavailable",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
}
