package net.newpipe.app.composable

data class PlayerKey(val name: String)

/**
 * Installs a handler while the player is active. Desktop captures keys even
 * when the native VLC surface owns focus; Android provides a no-op actual.
 * The returned function removes the handler.
 */
expect fun installGlobalPlayerKeyHandler(onKey: (PlayerKey) -> Boolean): () -> Unit
