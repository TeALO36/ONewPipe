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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.newpipe.app.composable.DownloadOverlay
import net.newpipe.app.composable.HomeContent
import net.newpipe.app.composable.MobileNavigationBar
import net.newpipe.app.composable.NavItem
import net.newpipe.app.composable.PlayerOverlay
import net.newpipe.app.composable.ServerDialog
import net.newpipe.app.composable.SettingsDialog
import net.newpipe.app.composable.Sidebar
import net.newpipe.app.composable.UpdateDialog
import net.newpipe.app.domain.DownloadState
import net.newpipe.app.domain.DownloadViewModel
import net.newpipe.app.domain.HomeViewModel
import net.newpipe.app.domain.PlayerState
import net.newpipe.app.domain.PlayerViewModel
import net.newpipe.app.domain.ServerStatus
import net.newpipe.app.domain.SettingsViewModel
import net.newpipe.app.domain.SyncViewModel
import net.newpipe.app.domain.TrendingCategory
import net.newpipe.app.domain.UpdateState
import net.newpipe.app.domain.UpdateViewModel
import net.newpipe.app.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Application shell shared by desktop and Android.
 *
 * Compact windows use a labelled bottom navigation bar; larger windows keep the
 * navigation rail. This prevents the desktop layout from being squeezed onto a phone.
 */
@Composable
fun App() {
    val settingsViewModel = koinViewModel<SettingsViewModel>()
    val themeMode by settingsViewModel.themeMode.collectAsState()

    AppTheme(themeOverride = themeMode) {
        coil3.compose.setSingletonImageLoaderFactory { context ->
            getAsyncImageLoader(context)
        }

        val homeViewModel = koinViewModel<HomeViewModel>()
        val playerViewModel = koinViewModel<PlayerViewModel>()
        val downloadViewModel = koinViewModel<DownloadViewModel>()
        val syncViewModel = koinViewModel<SyncViewModel>()
        val updateViewModel = koinViewModel<UpdateViewModel>()

        val homeState by homeViewModel.state.collectAsState()
        val service by settingsViewModel.currentService.collectAsState()
        val playerState by playerViewModel.state.collectAsState()
        val downloadState by downloadViewModel.state.collectAsState()
        val selectedCategory by homeViewModel.selectedCategory.collectAsState()
        val searchQuery by homeViewModel.searchQuery.collectAsState()
        val searchFilter by homeViewModel.searchFilter.collectAsState()
        val subscriptions by settingsViewModel.subscriptions.collectAsState()
        val serverStatus by syncViewModel.status.collectAsState()
        val updateState by updateViewModel.state.collectAsState()

        var selectedItem by remember { mutableStateOf(NavItem.HOME) }
        var showServerDialog by remember { mutableStateOf(false) }
        var showSettingsDialog by remember { mutableStateOf(false) }
        var showUpdateDialog by remember { mutableStateOf(false) }

        val onItemSelected: (NavItem) -> Unit = { item ->
            selectedItem = item
            if (item == NavItem.TRENDING) {
                homeViewModel.selectCategory(TrendingCategory.ALL)
            }
        }
        val onUpdateClick = {
            showUpdateDialog = true
            updateViewModel.checkForUpdates(force = true)
        }

        LaunchedEffect(Unit) { updateViewModel.checkForUpdates() }
        Surface(
            // ComposeActivity opts into edge-to-edge. Keep the app shell below
            // the status bar and above the gesture/navigation area so the
            // search field and bottom navigation never collide with Android UI.
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompact = maxWidth < 600.dp

                if (isCompact) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        HomeContent(
                            selectedItem = selectedItem,
                            service = service,
                            homeState = homeState,
                            selectedCategory = selectedCategory,
                            searchQuery = searchQuery,
                            searchFilter = searchFilter,
                            onSearch = homeViewModel::search,
                            onSearchFilterSelected = homeViewModel::selectSearchFilter,
                            onServiceSelected = settingsViewModel::setService,
                            onCategorySelected = homeViewModel::selectCategory,
                            onMediaClick = { media -> playerViewModel.loadVideo(media.url, media.title) },
                            onChannelClick = { media -> homeViewModel.openChannel(media.url) },
                            subscriptions = subscriptions,
                            onSubscriptionClick = { subscription ->
                                selectedItem = NavItem.HOME
                                homeViewModel.openChannel(subscription.url)
                            },
                            onPrefetch = { media -> playerViewModel.prefetch(media.url) },
                            onDownloadClick = { media -> downloadViewModel.loadStreams(media.url, media.title) },
                            onLoadMore = homeViewModel::loadMore,
                            isLoadingMore = homeViewModel.isLoadingMore,
                            modifier = Modifier.weight(1f)
                        )
                        MobileNavigationBar(
                            selectedItem = selectedItem,
                            onItemSelected = onItemSelected,
                            updateAvailable = updateState is UpdateState.Available,
                            onSettingsClick = { showSettingsDialog = true }
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Sidebar(
                            selectedItem = selectedItem,
                            onItemSelected = onItemSelected,
                            onServiceSelected = settingsViewModel::setService,
                            serverConnected = serverStatus is ServerStatus.Connected,
                            onServerClick = { showServerDialog = true },
                            updateAvailable = updateState is UpdateState.Available,
                            onSettingsClick = { showSettingsDialog = true }
                        )
                        HomeContent(
                            selectedItem = selectedItem,
                            service = service,
                            homeState = homeState,
                            selectedCategory = selectedCategory,
                            searchQuery = searchQuery,
                            searchFilter = searchFilter,
                            onSearch = homeViewModel::search,
                            onSearchFilterSelected = homeViewModel::selectSearchFilter,
                            onServiceSelected = settingsViewModel::setService,
                            onCategorySelected = homeViewModel::selectCategory,
                            onMediaClick = { media -> playerViewModel.loadVideo(media.url, media.title) },
                            onChannelClick = { media -> homeViewModel.openChannel(media.url) },
                            subscriptions = subscriptions,
                            onSubscriptionClick = { subscription ->
                                selectedItem = NavItem.HOME
                                homeViewModel.openChannel(subscription.url)
                            },
                            onPrefetch = { media -> playerViewModel.prefetch(media.url) },
                            onDownloadClick = { media -> downloadViewModel.loadStreams(media.url, media.title) },
                            onLoadMore = homeViewModel::loadMore,
                            isLoadingMore = homeViewModel.isLoadingMore,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

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
                        onChannelClick = homeViewModel::openChannel,
                        isSubscribed = { url -> subscriptions.any { it.url == url } },
                        onToggleSubscription = { subscription -> settingsViewModel.toggleSubscription(subscription) },
                        isCovered = downloadState !is DownloadState.Idle
                    )
                }

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

                if (showSettingsDialog) {
                    SettingsDialog(
                        settingsViewModel = settingsViewModel,
                        themeMode = themeMode,
                        serverStatus = serverStatus,
                        updateState = updateState,
                        onCheckUpdates = onUpdateClick,
                        onServerClick = {
                            showSettingsDialog = false
                            showServerDialog = true
                        },
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                if (showUpdateDialog) {
                    UpdateDialog(
                        state = updateState,
                        onCheckAgain = { updateViewModel.checkForUpdates(force = true) },
                        onDismiss = { showUpdateDialog = false }
                    )
                }

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
