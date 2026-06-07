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
import androidx.compose.runtime.*
import net.newpipe.app.composable.Sidebar
import net.newpipe.app.composable.NavItem
import net.newpipe.app.composable.MediaGrid
import net.newpipe.app.composable.GlassSearchBar
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
            var selectedItem by remember { mutableStateOf(NavItem.HOME) }

            // Using Surface as the background for the whole app
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Sidebar
                    Sidebar(
                        selectedItem = selectedItem,
                        onItemSelected = { selectedItem = it }
                    )

                    // Main Content Area
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        GlassSearchBar()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Dynamic Title
                        val service = currentService()
                        Text(
                            text = "${selectedItem.title} - ${service.serviceName}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )

                        // Grid
                        MediaGrid(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
