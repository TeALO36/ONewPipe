package net.newpipe.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.newpipe.app.composable.DownloadOverlay
import net.newpipe.app.composable.HomeContent
import net.newpipe.app.composable.NavItem
import net.newpipe.app.composable.PlayerOverlay
import net.newpipe.app.composable.Sidebar
import net.newpipe.app.domain.DownloadState
import net.newpipe.app.domain.DownloadViewModel
import net.newpipe.app.domain.HomeViewModel
import net.newpipe.app.domain.PlayerState
import net.newpipe.app.domain.PlayerViewModel
import net.newpipe.app.domain.SettingsViewModel
import net.newpipe.app.navigation.Screen
import net.newpipe.app.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Application shell: wires the view models together and composes the sidebar,
 * the home content and the player/download overlays.
 */
@Composable
fun App(startDestination: Screen? = null) {
    AppTheme {
        coil3.compose.setSingletonImageLoaderFactory { context ->
            getAsyncImageLoader(context)
        }

        val homeViewModel = koinViewModel<HomeViewModel>()
        val settingsViewModel = koinViewModel<SettingsViewModel>()
        val playerViewModel = koinViewModel<PlayerViewModel>()
        val downloadViewModel = koinViewModel<DownloadViewModel>()

        val homeState by homeViewModel.state.collectAsState()
        val service by settingsViewModel.currentService.collectAsState()
        val playerState by playerViewModel.state.collectAsState()
        val downloadState by downloadViewModel.state.collectAsState()
        val selectedCategory by homeViewModel.selectedCategory.collectAsState()

        var selectedItem by remember { mutableStateOf(NavItem.HOME) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Sidebar
                    Sidebar(
                        selectedItem = selectedItem,
                        onItemSelected = { selectedItem = it },
                        onServiceSelected = { settingsViewModel.setService(it) }
                    )

                    // Main Content Area
                    HomeContent(
                        selectedItem = selectedItem,
                        service = service,
                        homeState = homeState,
                        selectedCategory = selectedCategory,
                        onSearch = homeViewModel::search,
                        onServiceSelected = settingsViewModel::setService,
                        onCategorySelected = homeViewModel::selectCategory,
                        onMediaClick = { media -> playerViewModel.loadVideo(media.url, media.title) },
                        onDownloadClick = { media -> downloadViewModel.loadStreams(media.url, media.title) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Player Overlay
                if (playerState !is PlayerState.Idle) {
                    PlayerOverlay(
                        state = playerState,
                        playerViewModel = playerViewModel,
                        downloadViewModel = downloadViewModel
                    )
                }

                // Download Dialog Overlay
                if (downloadState !is DownloadState.Idle) {
                    DownloadOverlay(
                        state = downloadState,
                        downloadViewModel = downloadViewModel
                    )
                }
            }
        }
    }
}
