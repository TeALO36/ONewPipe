package net.newpipe.app.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import fr.arthonetwork.onewpipe.BuildConfig
import net.newpipe.app.openExternalUrl
import net.newpipe.app.domain.UpdateState

@Composable
fun UpdateDialog(
    state: UpdateState,
    onCheckAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    val available = state as? UpdateState.Available
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (available != null) "Update available" else "Updates") },
        text = {
            Text(
                when (state) {
                    UpdateState.Checking -> "Checking the public ONewPipe GitHub releases…"
                    UpdateState.UpToDate -> "You are running ONewPipe ${BuildConfig.VERSION_NAME}, the latest version."
                    is UpdateState.Available -> "ONewPipe ${state.release.versionName} is available. Choose the installer for your operating system on the release page."
                    is UpdateState.Error -> "Could not check GitHub: ${state.message}"
                    UpdateState.Idle -> "Check the public ONewPipe GitHub releases for a newer version."
                }
            )
        },
        confirmButton = {
            when {
                available != null -> Button(onClick = { openExternalUrl(available.release.html_url) }) {
                    Text("Open download page")
                }
                state is UpdateState.Checking -> Unit
                else -> TextButton(onClick = onCheckAgain) { Text("Check again") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
