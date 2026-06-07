package net.newpipe.app

import androidx.compose.runtime.Composable
import net.newpipe.app.navigation.Screen
import net.newpipe.app.theme.AppTheme
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import net.newpipe.app.composable.Sidebar
import net.newpipe.app.composable.NavItem
import net.newpipe.app.composable.MediaGrid
import net.newpipe.app.composable.GlassSearchBar
import net.newpipe.app.theme.currentService

import net.newpipe.app.domain.HomeViewModel
import net.newpipe.app.domain.HomeState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(startDestination: Screen? = null) {
    AppTheme {
        coil3.compose.setSingletonImageLoaderFactory { context ->
            getAsyncImageLoader(context)
        }

        val viewModel = koinViewModel<HomeViewModel>()
        val homeState by viewModel.state.collectAsState()
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

                    Spacer(modifier = Modifier.height(8.dp))

                    when (val state = homeState) {
                        is HomeState.Loading -> {
                            MediaGrid(items = emptyList(), isLoading = true, modifier = Modifier.weight(1f))
                        }
                        is HomeState.Success -> {
                            MediaGrid(items = state.items, modifier = Modifier.weight(1f))
                        }
                        is HomeState.Error -> {
                            MediaGrid(items = emptyList(), errorMessage = state.message, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
