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
