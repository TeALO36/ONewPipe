package net.newpipe.app.composable

import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

actual fun installGlobalPlayerKeyHandler(onKey: (PlayerKey) -> Boolean): () -> Unit {
    val dispatcher = java.awt.KeyEventDispatcher { event ->
        if (event.id != KeyEvent.KEY_PRESSED || event.isControlDown || event.isAltDown || event.isMetaDown) {
            false
        } else {
            val name = when (event.keyCode) {
                KeyEvent.VK_SPACE -> "SPACE"
                KeyEvent.VK_LEFT -> "LEFT"
                KeyEvent.VK_RIGHT -> "RIGHT"
                KeyEvent.VK_UP -> "UP"
                KeyEvent.VK_DOWN -> "DOWN"
                KeyEvent.VK_F -> "F"
                KeyEvent.VK_M -> "M"
                KeyEvent.VK_0 -> "0"
                KeyEvent.VK_1 -> "1"
                KeyEvent.VK_2 -> "2"
                KeyEvent.VK_3 -> "3"
                KeyEvent.VK_4 -> "4"
                KeyEvent.VK_5 -> "5"
                KeyEvent.VK_6 -> "6"
                KeyEvent.VK_7 -> "7"
                KeyEvent.VK_8 -> "8"
                KeyEvent.VK_9 -> "9"
                else -> null
            }
            name != null && onKey(PlayerKey(name))
        }
    }
    val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    manager.addKeyEventDispatcher(dispatcher)
    return { manager.removeKeyEventDispatcher(dispatcher) }
}
