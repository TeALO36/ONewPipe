package net.newpipe.app.backend

import kotlinx.coroutines.*
import java.awt.FileDialog
import java.awt.Frame
import java.io.FileOutputStream
import java.net.URL
import javax.swing.SwingUtilities

actual fun downloadFile(url: String, defaultName: String) {
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, "Enregistrer sous...", FileDialog.SAVE)
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
                    val input = connection.getInputStream()
                    val output = FileOutputStream(destPath)
                    
                    val data = ByteArray(4096)
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        output.write(data, 0, count)
                    }
                    output.flush()
                    output.close()
                    input.close()
                    println("Download complete: $destPath")
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Download failed: ${e.message}")
                }
            }
        }
    }
}
