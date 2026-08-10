package net.newpipe.app.backend

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.koin.core.context.GlobalContext

actual fun downloadFile(url: String, defaultName: String) {
    if (url.isBlank()) return

    val context = GlobalContext.get().get<Context>()
    val safeName = defaultName
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .ifBlank { "ONewPipe-download" }
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(safeName)
        .setDescription("Downloading with ONewPipe")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)

    // Public Downloads works without storage permission on modern Android.
    // Fall back to the app's external files directory on older devices where
    // the public destination may be rejected by scoped-storage rules.
    try {
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    } catch (_: SecurityException) {
        val fallback = DownloadManager.Request(Uri.parse(url))
            .setTitle(safeName)
            .setDescription("Downloading with ONewPipe")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, safeName)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(fallback)
    }
}
