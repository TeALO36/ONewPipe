package net.newpipe.app.composable

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.arthonetwork.onewpipe.BuildConfig
import net.newpipe.app.domain.LibraryViewModel
import net.newpipe.app.domain.ServerStatus
import net.newpipe.app.domain.UpdateState
import net.newpipe.app.domain.SettingsViewModel
import net.newpipe.app.classicInterfaceAvailable
import net.newpipe.app.openClassicInterface
import net.newpipe.app.openExternalUrl

@Composable
fun SettingsDialog(
    settingsViewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel? = null,
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
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    text = "Playback",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val autoplayNext by settingsViewModel.autoplayNext.collectAsState()
                SettingsSwitch(
                    title = "Autoplay next video",
                    subtitle = "Continue with the queue or the first related video.",
                    checked = autoplayNext,
                    onCheckedChange = settingsViewModel::setAutoplayNext
                )
                val resumePlayback by settingsViewModel.resumePlayback.collectAsState()
                SettingsSwitch(
                    title = "Resume where you stopped",
                    subtitle = "Restart a video at its saved position.",
                    checked = resumePlayback,
                    onCheckedChange = settingsViewModel::setResumePlayback
                )
                val preferredQuality by settingsViewModel.preferredQuality.collectAsState()
                Text(
                    text = "Preferred quality",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsViewModel.QUALITY_CHOICES.forEach { choice ->
                        FilterChip(
                            selected = preferredQuality == choice,
                            onClick = { settingsViewModel.setPreferredQuality(choice) },
                            label = {
                                Text(
                                    if (choice == SettingsViewModel.QUALITY_AUTO) "Auto" else "${choice}p"
                                )
                            }
                        )
                    }
                }

                if (libraryViewModel != null) {
                    HorizontalDivider()

                    Text(
                        text = "History and cache",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val historyEnabled by libraryViewModel.historyEnabled.collectAsState()
                    SettingsSwitch(
                        title = "Keep watch and search history",
                        subtitle = "Store what you watch and search on this device only.",
                        checked = historyEnabled,
                        onCheckedChange = libraryViewModel::setHistoryEnabled
                    )
                    val history by libraryViewModel.history.collectAsState()
                    OutlinedButton(
                        onClick = { libraryViewModel.clearHistory() },
                        enabled = history.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Clear watch history (${history.size})") }
                    val searchHistory by libraryViewModel.searchHistory.collectAsState()
                    OutlinedButton(
                        onClick = { libraryViewModel.clearSearchHistory() },
                        enabled = searchHistory.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Clear search history (${searchHistory.size})") }
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

                if (libraryViewModel != null) {
                    HorizontalDivider()

                    Text(
                        text = "Backup and restore",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Copy your history, playlists and subscriptions as text, " +
                            "and paste it back on another device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val clipboard = LocalClipboardManager.current
                    val subscriptions by settingsViewModel.subscriptions.collectAsState()
                    var restoreText by remember { mutableStateOf("") }
                    var restoreMessage by remember { mutableStateOf<String?>(null) }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(libraryViewModel.exportBackup(subscriptions))
                            )
                            restoreMessage = "Backup copied to the clipboard."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Copy backup") }
                    OutlinedTextField(
                        value = restoreText,
                        onValueChange = { restoreText = it },
                        label = { Text("Paste a backup here") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            val restored = libraryViewModel.importBackup(restoreText)
                            restoreMessage = if (restored == null) {
                                "This text is not a valid ONewPipe backup."
                            } else {
                                settingsViewModel.replaceSubscriptions(restored)
                                restoreText = ""
                                "Backup restored."
                            }
                        },
                        enabled = restoreText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Restore backup") }
                    restoreMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (classicInterfaceAvailable) {
                    HorizontalDivider()

                    Text(
                        text = "Classic interface",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "The classic NewPipe interface is still installed: it carries the " +
                            "feed, bookmarked playlists, the download manager and the popup player.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var classicFailed by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { classicFailed = !openClassicInterface() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open the classic interface") }
                    if (classicFailed) {
                        Text(
                            text = "The classic interface could not be opened on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ONewPipe ${BuildConfig.VERSION_NAME} — a free, ad-free media front-end " +
                        "built on the NewPipe core. Released under the GNU GPL v3 or later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { openExternalUrl(PROJECT_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open the project page") }
                OutlinedButton(
                    onClick = { openExternalUrl(LICENSE_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Read the license") }
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

/** Public ONewPipe project page, used by the About section. */
private const val PROJECT_URL = "https://github.com/TeALO36/ONewPipe"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** A labelled switch row, the building block of the settings sections. */
@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
