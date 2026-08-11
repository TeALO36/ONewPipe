package net.newpipe.app.composable

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.arthonetwork.onewpipe.BuildConfig
import net.newpipe.app.UpdateInstaller
import net.newpipe.app.domain.UpdateInstallState
import net.newpipe.app.domain.UpdateState

@Composable
fun UpdateDialog(
    state: UpdateState,
    onCheckAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    val available = state as? UpdateState.Available
    var installState by remember(state) { mutableStateOf<UpdateInstallState>(UpdateInstallState.Idle) }
    val isInstalling = installState is UpdateInstallState.Downloading ||
        installState is UpdateInstallState.WaitingForPermission ||
        installState is UpdateInstallState.OpeningInstaller

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (available != null) "Update available" else "Updates") },
        text = {
            Text(
                when (state) {
                    UpdateState.Checking -> "Checking the public ONewPipe GitHub releases…"
                    UpdateState.UpToDate -> "You are running ONewPipe ${BuildConfig.VERSION_NAME}, the latest version."
                    is UpdateState.Available -> {
                        val installText = when (val currentInstallState = installState) {
                            UpdateInstallState.Idle -> ""
                            is UpdateInstallState.Downloading -> "\n\nDownloading the Android APK… ${currentInstallState.progressPercent}%"
                            UpdateInstallState.WaitingForPermission -> "\n\nAllow ONewPipe to install updates, then return here."
                            UpdateInstallState.OpeningInstaller -> "\n\nOpening Android’s installer…"
                            is UpdateInstallState.Failed -> "\n\nUpdate failed: ${currentInstallState.message}"
                            UpdateInstallState.Unsupported -> "\n\nDirect installation is unavailable on this platform."
                        }
                        "ONewPipe ${state.release.versionName} is available. Tap Install update to download and open the Android installer.$installText"
                    }
                    is UpdateState.Error -> "Could not check GitHub: ${state.message}"
                    UpdateState.Idle -> "Check the public ONewPipe GitHub releases for a newer version."
                }
            )
        },
        confirmButton = {
            when {
                available != null -> Button(
                    enabled = !isInstalling,
                    onClick = {
                        installState = UpdateInstallState.Downloading(0)
                        UpdateInstaller.install(available.release) { nextState ->
                            installState = nextState
                        }
                    }
                ) {
                    Text(if (isInstalling) "Installing…" else "Install update")
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
