package net.newpipe.app.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.newpipe.app.domain.ServerStatus
import net.newpipe.app.domain.SyncViewModel

/**
 * Lets the user connect the app to their own ONewPipe server (accounts +
 * watch-position sync across devices), or disconnect from it.
 */
@Composable
fun ServerDialog(
    status: ServerStatus,
    syncViewModel: SyncViewModel,
    onDismiss: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val connected = status is ServerStatus.Connected

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (connected) "Server connection" else "Connect to your server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (connected) {
                    val s = status as ServerStatus.Connected
                    Text(
                        "Connected to ${s.serverUrl} as ${s.username}.\n" +
                            "Your play positions are synchronized across devices."
                    )
                } else {
                    Text(
                        "Run your own ONewPipe server (see the `server` module / Docker) " +
                            "to sync your watch history and resume videos on any device."
                    )
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("192.168.1.10:8080 or https://server.example") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (status is ServerStatus.Error) {
                        Text(
                            text = status.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (connected) {
                TextButton(onClick = { syncViewModel.disconnect() }) {
                    Text("Disconnect")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                        onClick = { syncViewModel.connect(serverUrl, username, password, register = false) }
                    ) {
                        Text("Sign in")
                    }
                    OutlinedButton(
                        enabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                        onClick = { syncViewModel.connect(serverUrl, username, password, register = true) }
                    ) {
                        Text("Create account")
                    }
                }
            }
        },
        dismissButton = {
            if (status is ServerStatus.Disconnected || status is ServerStatus.Error) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
