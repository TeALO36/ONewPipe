package net.newpipe.app.composable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.newpipe.app.theme.currentServiceScheme

data class DummyMedia(val id: Int, val title: String, val author: String, val duration: String, val views: String)

val dummyVideos = listOf(
    DummyMedia(1, "Building a Compose Desktop App", "Kotlin By JetBrains", "14:20", "12k views"),
    DummyMedia(2, "Lofi Hip Hop Radio - Beats to Relax/Study to", "Lofi Girl", "LIVE", "45k watching"),
    DummyMedia(3, "Understanding Kotlin Coroutines", "Android Developers", "22:15", "89k views"),
    DummyMedia(4, "How to design a premium UI", "DesignCourse", "10:05", "150k views"),
    DummyMedia(5, "Top 10 Kotlin Multiplatform libraries", "Touchlab", "08:30", "5k views"),
    DummyMedia(6, "Chillwave Synthpop Mix", "NewRetroWave", "45:00", "2M views"),
    DummyMedia(7, "Compose Multiplatform 1.6 Release", "JetBrains", "18:40", "34k views"),
    DummyMedia(8, "The history of Linux", "TechLore", "1:15:00", "200k views"),
    DummyMedia(9, "Cyberpunk 2077 OST", "CD Projekt Red", "2:30:00", "5M views"),
    DummyMedia(10, "Learning Rust in 2024", "Let's Get Rusty", "25:00", "120k views")
)

@Composable
fun MediaGrid(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(dummyVideos) { video ->
            MediaCard(video)
        }
    }
}

@Composable
fun MediaCard(media: DummyMedia) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val scale by animateFloatAsState(targetValue = if (isHovered) 1.05f else 1f)
    val elevation by animateDpAsState(targetValue = if (isHovered) 8.dp else 2.dp)
    
    val serviceColor = currentServiceScheme().primaryContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { /* handle click */ },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.DarkGray,
                                Color.Black
                            )
                        )
                    )
            ) {
                // Duration pill
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = media.duration,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
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
                Text(
                    text = media.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = media.views,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
