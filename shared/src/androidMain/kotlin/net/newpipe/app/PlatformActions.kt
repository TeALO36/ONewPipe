package net.newpipe.app

import android.content.Intent
import android.net.Uri
import org.koin.core.context.GlobalContext

actual fun openExternalUrl(url: String) {
    val context = GlobalContext.get().get<android.content.Context>()
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual val classicInterfaceAvailable: Boolean = true

actual fun openClassicInterface(): Boolean {
    val context = GlobalContext.get().get<android.content.Context>()
    // The classic activity lives in the application module, which the shared
    // module does not depend on, so it is addressed by name.
    return runCatching {
        context.startActivity(
            Intent()
                .setClassName(context, "org.schabi.newpipe.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}

actual fun shareLink(url: String, title: String): Boolean {
    if (url.isBlank()) return false
    val context = GlobalContext.get().get<android.content.Context>()
    return runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(
            Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}

actual val subtitlesSupported: Boolean = true
