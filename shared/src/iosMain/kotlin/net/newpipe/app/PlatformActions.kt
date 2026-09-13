package net.newpipe.app

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openExternalUrl(url: String) {
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) }
}

actual fun currentTimeMillis(): Long =
    (platform.Foundation.NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual val classicInterfaceAvailable: Boolean = false

actual fun openClassicInterface(): Boolean = false

actual fun shareLink(url: String, title: String): Boolean = false

actual val subtitlesSupported: Boolean = false
