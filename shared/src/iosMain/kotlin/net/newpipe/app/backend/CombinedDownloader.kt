package net.newpipe.app.backend

actual fun supportsCombinedVideoDownload(): Boolean = false

actual fun downloadVideoWithAudio(videoUrl: String, audioUrl: String, defaultName: String) = Unit
