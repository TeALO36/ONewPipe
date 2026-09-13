package net.newpipe.app

import java.awt.Desktop
import java.net.URI

actual fun openExternalUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(URI(url))
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual val classicInterfaceAvailable: Boolean = false

actual fun openClassicInterface(): Boolean = false

// Desktop has no system share sheet; the caller copies the link instead.
actual fun shareLink(url: String, title: String): Boolean = false

// The desktop player (libVLC through vlcj) has no subtitle wiring yet, so the
// subtitle menu stays hidden instead of offering a button that does nothing.
actual val subtitlesSupported: Boolean = false
