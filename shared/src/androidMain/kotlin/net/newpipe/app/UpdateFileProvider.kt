package net.newpipe.app

import androidx.core.content.FileProvider

/** Separate provider class so AndroidX does not share authority state with NewPipe's legacy provider. */
class UpdateFileProvider : FileProvider()
