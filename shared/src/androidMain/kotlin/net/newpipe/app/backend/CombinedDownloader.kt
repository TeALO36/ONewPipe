package net.newpipe.app.backend

import android.widget.Toast
import org.koin.core.context.GlobalContext

actual fun supportsCombinedVideoDownload(): Boolean = NativeDownloadBridge.isRegistered()

actual fun downloadWithNativeManager(request: NativeDownloadRequest) {
    if (NativeDownloadBridge.enqueue(request)) return

    // This should only be reachable during an unusually early lifecycle call.
    // Never silently fall back to a video-only file.
    runCatching {
        val context = GlobalContext.get().get<android.content.Context>()
        Toast.makeText(
            context,
            "The native download service is not ready yet",
            Toast.LENGTH_LONG
        ).show()
    }
}
