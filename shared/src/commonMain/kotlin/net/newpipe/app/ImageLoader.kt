package net.newpipe.app

import coil3.ImageLoader
import coil3.PlatformContext

expect fun getAsyncImageLoader(context: PlatformContext): ImageLoader
