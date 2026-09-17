package net.newpipe.app.backend

import kotlinx.coroutines.*
import java.awt.FileDialog
import java.awt.Frame
import java.io.FileOutputStream
import java.net.URL
import javax.swing.SwingUtilities

actual fun downloadFile(url: String, defaultName: String) {
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, "Save as…", FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true

        val dir = dialog.directory
        val file = dialog.file

        if (dir != null && file != null) {
            val destPath = dir + file
            // Perform download in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val connection = URL(url).openConnection()
                    connection.connect()
                    val total = connection.contentLengthLong
                    DownloadProgressBus.report(file, 0)
                    connection.getInputStream().use { input ->
                        FileOutputStream(destPath).use { output ->
                            val data = ByteArray(64 * 1024)
                            var downloaded = 0L
                            var lastPercent = -1
                            var count: Int
                            while (input.read(data).also { count = it } != -1) {
                                output.write(data, 0, count)
                                downloaded += count
                                if (total > 0) {
                                    val percent = ((downloaded * 100) / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        DownloadProgressBus.report(file, percent)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                    DownloadProgressBus.complete(file)
                } catch (e: Exception) {
                    DownloadProgressBus.fail(file, e.message ?: "Download failed")
                }
            }
        }
    }
}
