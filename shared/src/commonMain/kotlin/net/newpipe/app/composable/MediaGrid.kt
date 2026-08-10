package net.newpipe.app.composable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.newpipe.app.theme.currentServiceScheme
import kotlinx.coroutines.delay

import net.newpipe.app.domain.MediaItem
import coil3.compose.AsyncImage

@Composable
fun MediaGrid(
    items: List<MediaItem>,
    isLoading: Boolean = false,
    isLoadingMore: Boolean = false,
    errorMessage: String? = null,
    onMediaClick: (MediaItem) -> Unit = {},
    onDownloadClick: (MediaItem) -> Unit = {},
    onPrefetch: (MediaItem) -> Unit = {},
    onLoadMore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        MediaGridSkeleton(modifier = modifier.fillMaxSize())
        return
    }

    if (errorMessage != null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val gridState = rememberLazyGridState()

    // Warm the first few visible videos on every platform. Desktop hover
    // prefetch remains useful, but touch devices have no hover event; without
    // this warm-up the extractor starts only after the user taps a card.
    LaunchedEffect(items) {
        items.take(2).forEachIndexed { index, media ->
            if (index > 0) delay(100L)
            onPrefetch(media)
        }
    }

    // Detect when the user scrolls near the bottom and trigger loadMore
    LaunchedEffect(gridState, items.size) {
        snapshotFlow {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 6 // Trigger when 6 items from the end
        }.collect { shouldLoad ->
            if (shouldLoad && items.isNotEmpty()) {
                onLoadMore()
            }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 280.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.fillMaxSize()
    ) {
        // Some feeds contain the same URL more than once. Include the item
        // index in the key so Compose never crashes during a feed refresh.
        itemsIndexed(items, key = { index, media -> "${media.url}#$index" }) { _, media ->
            MediaCard(
                media = media,
                onClick = { onMediaClick(media) },
                onPrefetch = { onPrefetch(media) },
                onDownloadClick = { onDownloadClick(media) }
            )
        }

        // Loading more indicator at the bottom
        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
fun MediaCard(
    media: MediaItem,
    onClick: () -> Unit = {},
    onPrefetch: () -> Unit = {},
    onDownloadClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // Start extraction as soon as the pointer enters the card. Waiting even a
    // few hundred milliseconds here makes a fast click miss the prefetch and
    // puts the full extractor latency back on the player overlay.
    LaunchedEffect(isHovered) {
        if (isHovered) onPrefetch()
    }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.97f
            isHovered -> 1.04f
            else -> 1f
        }
    )
    val elevation by animateDpAsState(targetValue = if (isHovered) 8.dp else 2.dp)
    
    val serviceColor = currentServiceScheme().primaryContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (media.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(
                        model = media.thumbnailUrl,
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }

                // Duration pill
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (media.isLive) "LIVE" else media.durationText,
                        color = if (media.isLive) Color.Red else Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Hover overlay with play button
                if (isHovered) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledIconButton(
                            onClick = { },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = serviceColor,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                        }
                    }
                }
            }

            // Info section
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = media.uploaderName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    if (media.viewCount > 0) {
                        Text(
                            text = formatViewCount(media.viewCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onDownloadClick() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Add,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun formatViewCount(count: Long): String = when {
    count >= 1_000_000_000 -> "${(count / 1_000_000_000.0).format(1)}B views"
    count >= 1_000_000 -> "${(count / 1_000_000.0).format(1)}M views"
    count >= 1_000 -> "${(count / 1_000.0).format(1)}K views"
    else -> "$count views"
}

private fun Double.format(digits: Int): String = String.format("%.${digits}f", this)
