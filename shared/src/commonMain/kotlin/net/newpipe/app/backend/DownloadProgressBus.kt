package net.newpipe.app.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Download state reported by the platform downloaders. */
sealed class DownloadProgress {
    data class Running(val percent: Int) : DownloadProgress()
    object Completed : DownloadProgress()
    data class Failed(val message: String) : DownloadProgress()
}

/**
 * Progress of the downloads started from the app, keyed by file name.
 *
 * Android hands downloads to the system DownloadManager, which shows its own
 * notification, while the desktop downloader runs in-process and reports here
 * so the Downloads tab can show a real progress bar instead of nothing.
 */
object DownloadProgressBus {
    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    fun report(fileName: String, percent: Int) {
        if (fileName.isBlank()) return
        _downloads.value = _downloads.value + (fileName to DownloadProgress.Running(percent.coerceIn(0, 100)))
    }

    fun complete(fileName: String) {
        if (fileName.isBlank()) return
        _downloads.value = _downloads.value + (fileName to DownloadProgress.Completed)
    }

    fun fail(fileName: String, message: String) {
        if (fileName.isBlank()) return
        _downloads.value = _downloads.value + (fileName to DownloadProgress.Failed(message))
    }

    fun forget(fileName: String) {
        _downloads.value = _downloads.value - fileName
    }
}
