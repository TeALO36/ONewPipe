package net.newpipe.app.backend

/** Downloads a video-only stream and an audio stream, then packages them together. */
expect fun downloadVideoWithAudio(videoUrl: String, audioUrl: String, defaultName: String)

/** Whether this platform can safely create one combined video file. */
expect fun supportsCombinedVideoDownload(): Boolean
