package net.newpipe.app.backend

import javax.swing.JOptionPane
import javax.swing.SwingUtilities

actual fun supportsCombinedVideoDownload(): Boolean = false

actual fun downloadVideoWithAudio(videoUrl: String, audioUrl: String, defaultName: String) {
    SwingUtilities.invokeLater {
        JOptionPane.showMessageDialog(
            null,
            "Combining video and audio is currently available on Android only.",
            "Download unavailable",
            JOptionPane.INFORMATION_MESSAGE
        )
    }
}
