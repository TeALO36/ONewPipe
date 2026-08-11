package net.newpipe.app.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
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
import net.newpipe.app.domain.SearchFilter
import net.newpipe.app.domain.Subscription
import net.newpipe.app.domain.TrendingCategory
import net.newpipe.app.theme.Service
import coil3.compose.AsyncImage

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
    searchQuery: String? = null,
    searchFilter: SearchFilter = SearchFilter.ALL,
    onSearch: (String) -> Unit,
    onSearchFilterSelected: (SearchFilter) -> Unit = {},
    onServiceSelected: (Service) -> Unit,
    onCategorySelected: (TrendingCategory) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (MediaItem) -> Unit = {},
    subscriptions: List<Subscription> = emptyList(),
    onSubscriptionClick: (Subscription) -> Unit = {},
    onDownloadClick: (MediaItem) -> Unit,
    onPrefetch: (MediaItem) -> Unit = {},
    onLoadMore: () -> Unit = {},
    isLoadingMore: Boolean = false,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp
        val isSearching = !searchQuery.isNullOrBlank()

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 24.dp))

            val isMediaSection = selectedItem == NavItem.HOME || selectedItem == NavItem.TRENDING
            if (isMediaSection) {
                if (isCompact) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassSearchBar(onSearch = onSearch, modifier = Modifier.fillMaxWidth())
                        if (!isSearching) {
                            ServiceSwitcher(
                                service = service,
                                onServiceSelected = onServiceSelected,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassSearchBar(
                            onSearch = onSearch,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isSearching) {
                            Spacer(modifier = Modifier.width(16.dp))
                            ServiceSwitcher(service = service, onServiceSelected = onServiceSelected)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 16.dp))
                if (isSearching) {
                    SearchFilters(
                        selected = searchFilter,
                        onSelected = onSearchFilterSelected,
                        isCompact = isCompact
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

        // Dynamic Title (crossfades when switching sections)
        AnimatedContent(
            targetState = selectedItem,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "title"
        ) { item ->
            Text(
                text = if (isSearching && (item == NavItem.HOME || item == NavItem.TRENDING)) {
                    "Search results"
                } else {
                    item.title
                },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = if (isCompact) 16.dp else 24.dp,
                    vertical = 8.dp
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Trending category chips — pinned between the title and the content,
        // OUTSIDE the animated section. AnimatedContent stacks its children in
        // a Box, so a grid inside it would paint over the chips. Keeping the
        // chips here guarantees they are never covered by the video grid.
        if (selectedItem == NavItem.HOME && !isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = if (isCompact) 16.dp else 24.dp),
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
        }

        // Section content fades in place. Crossfade keeps a stable layout
        // box, avoiding the horizontal remeasurement/shake seen when the
        // previous slide transition switched between Home and Trending.
        Crossfade(
            targetState = selectedItem,
            animationSpec = tween(220),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "section"
        ) { item ->
            when (item) {
                NavItem.HOME, NavItem.TRENDING -> {
                    // Crossfade between loading / content / error states
                    Crossfade(
                        targetState = homeState,
                        animationSpec = tween(250),
                        label = "homeState"
                    ) { state ->
                        when (state) {
                            is HomeState.Loading -> {
                                MediaGrid(items = emptyList(), isLoading = true, modifier = Modifier.fillMaxSize())
                            }
                            is HomeState.Success -> {
                                MediaGrid(
                                    items = state.items,
                                    isLoadingMore = isLoadingMore,
                                    onMediaClick = onMediaClick,
                                    onChannelClick = onChannelClick,
                                    onDownloadClick = onDownloadClick,
                                    onPrefetch = onPrefetch,
                                    onLoadMore = onLoadMore,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            is HomeState.Error -> {
                                MediaGrid(items = emptyList(), errorMessage = state.message, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                NavItem.SUBSCRIPTIONS -> {
                    SubscriptionSection(
                        subscriptions = subscriptions,
                        onSubscriptionClick = onSubscriptionClick
                    )
                }
                NavItem.LIBRARY -> {
                    EmptySection(
                        icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(56.dp)) },
                        title = "Your library is empty",
                        message = "Watch history and local downloads will appear here. " +
                            "Played video positions are synchronized through your server."
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun SubscriptionSection(
    subscriptions: List<Subscription>,
    onSubscriptionClick: (Subscription) -> Unit
) {
    if (subscriptions.isEmpty()) {
        EmptySection(
            icon = { Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(56.dp)) },
            title = "No subscriptions yet",
            message = "Open a video and press Subscribe to keep your favorite channels here."
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Your subscriptions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        subscriptions.forEach { subscription ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSubscriptionClick(subscription) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subscription.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = subscription.thumbnailUrl,
                        contentDescription = subscription.name,
                        modifier = Modifier.size(48.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(48.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(subscription.name, style = MaterialTheme.typography.titleMedium)
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
private fun SearchFilters(
    selected: SearchFilter,
    onSelected: (SearchFilter) -> Unit,
    isCompact: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = if (isCompact) 16.dp else 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(filter.label) }
            )
        }
    }
}

@Composable
private fun ServiceSwitcher(
    service: Service,
    onServiceSelected: (Service) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = modifier
        ) {
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
