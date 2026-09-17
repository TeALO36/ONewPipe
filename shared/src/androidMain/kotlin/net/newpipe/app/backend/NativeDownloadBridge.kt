package net.newpipe.app.backend

/**
 * The shared Compose module cannot depend on the Android application module,
 * because the application already depends on shared. App registers the native
 * NewPipe implementation during Application.onCreate through this small bridge.
 */
object NativeDownloadBridge {
    private var handler: ((NativeDownloadRequest) -> Unit)? = null

    fun register(handler: (NativeDownloadRequest) -> Unit) {
        this.handler = handler
    }

    fun isRegistered(): Boolean = handler != null

    fun enqueue(request: NativeDownloadRequest): Boolean {
        val currentHandler = handler ?: return false
        currentHandler(request)
        return true
    }
}
