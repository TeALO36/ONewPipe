package net.newpipe.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import net.newpipe.app.composable.ServerDialog
import net.newpipe.app.composable.Sidebar
import net.newpipe.app.domain.DownloadState
import net.newpipe.app.domain.DownloadViewModel
import net.newpipe.app.domain.HomeViewModel
import net.newpipe.app.domain.PlayerState
import net.newpipe.app.domain.PlayerViewModel
import net.newpipe.app.domain.ServerStatus
import net.newpipe.app.domain.SettingsViewModel
import net.newpipe.app.domain.SyncViewModel
import net.newpipe.app.domain.TrendingCategory
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
        val syncViewModel = koinViewModel<SyncViewModel>()

        val homeState by homeViewModel.state.collectAsState()
        val service by settingsViewModel.currentService.collectAsState()
        val playerState by playerViewModel.state.collectAsState()
        val downloadState by downloadViewModel.state.collectAsState()
        val selectedCategory by homeViewModel.selectedCategory.collectAsState()
        val serverStatus by syncViewModel.status.collectAsState()

        var selectedItem by remember { mutableStateOf(NavItem.HOME) }
        var showServerDialog by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Sidebar
                    Sidebar(
                        selectedItem = selectedItem,
                        onItemSelected = {
                            selectedItem = it
                            // Trending shows the plain feed: drop any category filter
                            if (it == NavItem.TRENDING) {
                                homeViewModel.selectCategory(TrendingCategory.ALL)
                            }
                        },
                        onServiceSelected = { settingsViewModel.setService(it) },
                        serverConnected = serverStatus is ServerStatus.Connected,
                        onServerClick = { showServerDialog = true }
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

                // Player Overlay — slides up from the bottom, fades out on close
                AnimatedVisibility(
                    visible = playerState !is PlayerState.Idle,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) +
                        fadeIn(animationSpec = tween(300)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220)) +
                        fadeOut(animationSpec = tween(220))
                ) {
                    PlayerOverlay(
                        state = playerState,
                        playerViewModel = playerViewModel,
                        downloadViewModel = downloadViewModel,
                        // Hide the native video surface while the download dialog is open
                        // so the dialog always paints on top of everything.
                        isCovered = downloadState !is DownloadState.Idle
                    )
                }

                // Download Dialog Overlay — gently scales in, fades out
                AnimatedVisibility(
                    visible = downloadState !is DownloadState.Idle,
                    enter = scaleIn(initialScale = 0.94f, animationSpec = tween(220)) +
                        fadeIn(animationSpec = tween(220)),
                    exit = scaleOut(targetScale = 0.94f, animationSpec = tween(160)) +
                        fadeOut(animationSpec = tween(160))
                ) {
                    DownloadOverlay(
                        state = downloadState,
                        downloadViewModel = downloadViewModel
                    )
                }

                // Server Connection Dialog
                if (showServerDialog) {
                    ServerDialog(
                        status = serverStatus,
                        syncViewModel = syncViewModel,
                        onDismiss = { showServerDialog = false }
                    )
                }
            }
        }
    }
}
