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

// The desktop player downloads the selected track and attaches it to libVLC
// as a subtitle slave (see VideoPlayer.kt).
actual val subtitlesSupported: Boolean = true
