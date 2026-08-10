package net.newpipe.app.composable

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.arthonetwork.onewpipe.BuildConfig
import net.newpipe.app.domain.ServerStatus
import net.newpipe.app.domain.UpdateState
import net.newpipe.app.domain.SettingsViewModel

@Composable
fun SettingsDialog(
    settingsViewModel: SettingsViewModel,
    themeMode: String,
    serverStatus: ServerStatus,
    updateState: UpdateState,
    onCheckUpdates: () -> Unit,
    onServerClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Choose how ONewPipe looks on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChoice("System", SettingsViewModel.THEME_SYSTEM, themeMode, settingsViewModel::setThemeMode)
                    ThemeChoice("Light", SettingsViewModel.THEME_LIGHT, themeMode, settingsViewModel::setThemeMode)
                    ThemeChoice("Dark", SettingsViewModel.THEME_DARK, themeMode, settingsViewModel::setThemeMode)
                }

                HorizontalDivider()

                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (serverStatus) {
                        is ServerStatus.Connected -> "Connected as ${serverStatus.username}. Watch positions sync through your server."
                        is ServerStatus.Error -> "Account connection failed: ${serverStatus.message}"
                        else -> "Connect a self-hosted ONewPipe account to sync videos across devices."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onServerClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (serverStatus is ServerStatus.Connected) "Manage server account" else "Sign in or create account")
                }

                HorizontalDivider()

                Text(
                    text = "Updates",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (updateState) {
                        UpdateState.Checking -> "Checking the public GitHub releases…"
                        UpdateState.UpToDate -> "ONewPipe ${BuildConfig.VERSION_NAME} is up to date."
                        is UpdateState.Available -> "Version ${updateState.release.versionName} is available."
                        is UpdateState.Error -> "Unable to check: ${updateState.message}"
                        UpdateState.Idle -> "Check the public ONewPipe releases manually."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onCheckUpdates,
                    enabled = updateState !is UpdateState.Checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check for updates")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ThemeChoice(
    label: String,
    value: String,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    FilterChip(
        selected = selectedValue == value,
        onClick = { onSelected(value) },
        label = { Text(label) }
    )
}
