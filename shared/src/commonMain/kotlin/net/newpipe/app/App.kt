/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import androidx.compose.runtime.Composable
import net.newpipe.app.di.KoinApp
import net.newpipe.app.navigation.Screen
import net.newpipe.app.theme.AppTheme
import androidx.compose.ui.unit.dp
import org.koin.compose.KoinApplication
import org.koin.plugin.module.dsl.koinConfiguration

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.newpipe.app.composable.TopAppBar
import net.newpipe.app.theme.currentService

/**
 * Entry point for the multiplatform compose application
 * @param startDestination Starting destination for the app
 */
@Composable
fun App(startDestination: Screen? = null) {
    KoinApplication(configuration = koinConfiguration<KoinApp>()) {
        AppTheme {
            val service = currentService()
            Scaffold(
                topBar = {
                    TopAppBar(title = "ONewPipe Desktop - ${service.serviceName}")
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Bienvenue sur ONewPipe Desktop!",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Service actif : ${service.serviceName}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}
