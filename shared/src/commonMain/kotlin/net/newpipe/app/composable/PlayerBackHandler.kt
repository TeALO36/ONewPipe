package net.newpipe.app.composable

import androidx.compose.runtime.Composable

/** Handles the system back action without coupling shared UI to Android APIs. */
@Composable
expect fun PlatformPlayerBackHandler(enabled: Boolean, onBack: () -> Unit)
