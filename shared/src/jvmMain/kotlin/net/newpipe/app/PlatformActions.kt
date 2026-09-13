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
