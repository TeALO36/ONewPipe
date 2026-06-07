package net.newpipe.app

import coil3.ImageLoader
import coil3.PlatformContext

actual fun getAsyncImageLoader(context: PlatformContext): ImageLoader {
    return ImageLoader.Builder(context).build()
}
