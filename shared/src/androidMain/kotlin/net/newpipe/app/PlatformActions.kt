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
