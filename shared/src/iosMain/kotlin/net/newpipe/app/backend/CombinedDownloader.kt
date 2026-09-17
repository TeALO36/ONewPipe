package net.newpipe.app.backend

actual fun supportsCombinedVideoDownload(): Boolean = false

actual fun downloadWithNativeManager(request: NativeDownloadRequest) = Unit
