package net.newpipe.app.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.newpipe.app.domain.CategoryRow
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.TrendingCategory

/**
 * Home screen as themed rows — Gaming, Music, Movies & Series, Podcasts — the
 * way a streaming home page is laid out, instead of a single flat grid.
 *
 * Each row loads on its own, so a slow or failing category never blocks the
 * others, and "See all" switches to the full grid of that category.
 */
@Composable
fun HomeRows(
    rows: List<CategoryRow>,
    onMediaClick: (MediaItem) -> Unit,
    onChannelClick: (MediaItem) -> Unit,
    onDownloadClick: (MediaItem) -> Unit,
    onPrefetch: (MediaItem) -> Unit,
    onSeeAll: (TrendingCategory) -> Unit,
    cardActions: (MediaItem) -> List<Pair<String, () -> Unit>> = { emptyList() },
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val horizontalPadding = if (isCompact) 16.dp else 24.dp

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rows, key = { it.category.id }) { row ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = row.category.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { onSeeAll(row.category) }) { Text("See all") }
                }

                when {
                    row.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.width(28.dp))
                        }
                    }

                    row.error != null -> {
                        Text(
                            text = row.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp)
                        )
                    }

                    else -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(row.items, key = { "${row.category.id}-${it.url}" }) { media ->
                                Box(modifier = Modifier.width(if (isCompact) 240.dp else 300.dp)) {
                                    MediaCard(
                                        media = media,
                                        onClick = { onMediaClick(media) },
                                        onChannelClick = { onChannelClick(media) },
                                        onPrefetch = { onPrefetch(media) },
                                        onDownloadClick = { onDownloadClick(media) },
                                        extraActions = cardActions(media)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
