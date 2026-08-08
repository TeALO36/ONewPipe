package net.newpipe.app.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.newpipe.app.domain.HomeState
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.TrendingCategory
import net.newpipe.app.theme.Service

/**
 * Main content of the app: search bar, service switcher, dynamic title and
 * the section-specific content for each sidebar item.
 *
 * Home and Trending show the media grid (trending by category, or search
 * results). Subscriptions and Library have no data source yet, so they show
 * an honest empty state instead of silently reusing the home feed.
 */
@Composable
fun HomeContent(
    selectedItem: NavItem,
    service: Service,
    homeState: HomeState,
    selectedCategory: TrendingCategory,
    onSearch: (String) -> Unit,
    onServiceSelected: (Service) -> Unit,
    onCategorySelected: (TrendingCategory) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onDownloadClick: (MediaItem) -> Unit,
    onLoadMore: () -> Unit = {},
    isLoadingMore: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(24.dp))

        val isMediaSection = selectedItem == NavItem.HOME || selectedItem == NavItem.TRENDING
        if (isMediaSection) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassSearchBar(
                    onSearch = onSearch,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))

                ServiceSwitcher(service = service, onServiceSelected = onServiceSelected)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Dynamic Title (crossfades when switching sections)
        AnimatedContent(
            targetState = selectedItem,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "title"
        ) { item ->
            Text(
                text = "${item.title} - ${service.serviceName}",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Section content (slides + fades when switching sidebar items)
        AnimatedContent(
            targetState = selectedItem,
            transitionSpec = {
                (fadeIn(tween(240)) + slideInHorizontally(initialOffsetX = { it / 10 }, animationSpec = tween(240)))
                    .togetherWith(
                        fadeOut(tween(140)) + slideOutHorizontally(targetOffsetX = { -it / 10 }, animationSpec = tween(140))
                    )
            },
            label = "section"
        ) { item ->
            when (item) {
                NavItem.HOME -> {
                    // Trending Category Tabs (Home only — Trending is the plain feed)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrendingCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { onCategorySelected(category) },
                                label = { Text(category.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Crossfade between loading / content / error states
                    Crossfade(
                        targetState = homeState,
                        animationSpec = tween(250),
                        label = "homeState"
                    ) { state ->
                        when (state) {
                            is HomeState.Loading -> {
                                MediaGrid(items = emptyList(), isLoading = true, modifier = Modifier.weight(1f))
                            }
                            is HomeState.Success -> {
                                MediaGrid(
                                    items = state.items,
                                    isLoadingMore = isLoadingMore,
                                    onMediaClick = onMediaClick,
                                    onDownloadClick = onDownloadClick,
                                    onLoadMore = onLoadMore,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            is HomeState.Error -> {
                                MediaGrid(items = emptyList(), errorMessage = state.message, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                NavItem.TRENDING -> {
                    // Pure trending feed, no category chips — visually distinct from Home
                    Spacer(modifier = Modifier.height(12.dp))

                    Crossfade(
                        targetState = homeState,
                        animationSpec = tween(250),
                        label = "trendingState"
                    ) { state ->
                        when (state) {
                            is HomeState.Loading -> {
                                MediaGrid(items = emptyList(), isLoading = true, modifier = Modifier.weight(1f))
                            }
                            is HomeState.Success -> {
                                MediaGrid(
                                    items = state.items,
                                    isLoadingMore = isLoadingMore,
                                    onMediaClick = onMediaClick,
                                    onDownloadClick = onDownloadClick,
                                    onLoadMore = onLoadMore,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            is HomeState.Error -> {
                                MediaGrid(items = emptyList(), errorMessage = state.message, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                NavItem.SUBSCRIPTIONS -> {
                    EmptySection(
                        icon = { Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(56.dp)) },
                        title = "No subscriptions yet",
                        message = "Connect to your ONewPipe server (cloud icon, bottom left) to sync your " +
                            "channels and subscriptions across devices."
                    )
                }
                NavItem.LIBRARY -> {
                    EmptySection(
                        icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(56.dp)) },
                        title = "Your library is empty",
                        message = "Watch history, playlists and downloads will appear here. " +
                            "Played videos are saved and synchronized through your server."
                    )
                }
            }
        }
    }
}

/** Friendly placeholder for sections whose data source is not implemented yet. */
@Composable
private fun EmptySection(
    icon: @Composable () -> Unit,
    title: String,
    message: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}

@Composable
private fun ServiceSwitcher(
    service: Service,
    onServiceSelected: (Service) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(service.serviceName)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch Service")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Service.entries.forEach { srv ->
                DropdownMenuItem(
                    text = { Text(srv.serviceName) },
                    onClick = {
                        expanded = false
                        onServiceSelected(srv)
                    }
                )
            }
        }
    }
}
