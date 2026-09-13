package net.newpipe.app

expect fun openExternalUrl(url: String)

/** Wall-clock time in milliseconds, used for history and playlist timestamps. */
expect fun currentTimeMillis(): Long

/**
 * True when the platform also ships the classic NewPipe interface (Android
 * only). The setting that opens it is hidden everywhere else.
 */
expect val classicInterfaceAvailable: Boolean

/**
 * Opens the classic NewPipe interface, which carries the full NewPipe feature
 * set (feed, bookmarked playlists, downloads, popup player, settings).
 * Returns false when it could not be started.
 */
expect fun openClassicInterface(): Boolean
