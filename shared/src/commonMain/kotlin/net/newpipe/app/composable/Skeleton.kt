package net.newpipe.app.composable

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SkeletonBase = Color(0xFF212121)
private val SkeletonHighlight = Color(0xFF2E2E2E)

/**
 * A single moving highlight brush shared by every placeholder block of a
 * skeleton screen, so the whole screen shimmers in sync (YouTube style).
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val shift by transition.animateFloat(
        initialValue = -700f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeletonShift"
    )
    return Brush.linearGradient(
        colors = listOf(SkeletonBase, SkeletonHighlight, SkeletonBase),
        start = Offset(shift - 400f, 0f),
        end = Offset(shift + 400f, 0f)
    )
}

/** Rounded shimmer block. */
@Composable
fun SkeletonBox(
    modifier: Modifier,
    brush: Brush,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    Box(modifier = modifier.background(brush, shape))
}

/** Shimmer bar used as a text line. */
@Composable
fun SkeletonLine(
    brush: Brush,
    modifier: Modifier,
    widthFraction: Float = 1f,
    height: Dp = 14.dp
) {
    SkeletonBox(
        brush = brush,
        modifier = modifier.fillMaxWidth(widthFraction).height(height)
    )
}

/** YouTube-style skeleton grid shown while trending/search is loading. */
@Composable
fun MediaGridSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(8) {
            Column {
                SkeletonBox(
                    brush = brush,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonLine(brush = brush, modifier = Modifier, widthFraction = 0.9f)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonLine(brush = brush, modifier = Modifier, widthFraction = 0.6f, height = 12.dp)
            }
        }
    }
}

/**
 * YouTube-style skeleton shown in the player while a video is loading:
 * a 16:9 placeholder for the video, title bars, channel row, action buttons
 * and (on wide windows) skeleton rows for the related videos.
 */
@Composable
fun PlayerSkeleton(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth > 1000.dp
        Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(modifier = Modifier.weight(if (isWide) 0.65f else 1f)) {
                // Video area placeholder
                SkeletonBox(
                    brush = brush,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(4.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                // Title lines
                SkeletonLine(brush = brush, modifier = Modifier, widthFraction = 0.85f, height = 18.dp)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonLine(brush = brush, modifier = Modifier, widthFraction = 0.55f, height = 18.dp)
                Spacer(modifier = Modifier.height(20.dp))
                // Channel row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(brush = brush, modifier = Modifier.size(40.dp), shape = RoundedCornerShape(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    SkeletonLine(brush = brush, modifier = Modifier, widthFraction = 0.25f, height = 14.dp)
                }
                Spacer(modifier = Modifier.height(24.dp))
                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBox(brush = brush, modifier = Modifier.width(110.dp).height(36.dp), shape = RoundedCornerShape(18.dp))
                    SkeletonBox(brush = brush, modifier = Modifier.width(120.dp).height(36.dp), shape = RoundedCornerShape(18.dp))
                    SkeletonBox(brush = brush, modifier = Modifier.width(110.dp).height(36.dp), shape = RoundedCornerShape(18.dp))
                }
            }
            if (isWide) {
                Spacer(modifier = Modifier.width(24.dp))
                Column(
                    modifier = Modifier.weight(0.35f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(4) {
                        Row {
                            SkeletonBox(
                                brush = brush,
                                modifier = Modifier.width(140.dp).aspectRatio(16f / 9f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                SkeletonLine(brush = brush, modifier = Modifier, widthFraction = 0.95f, height = 12.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                SkeletonLine(brush = brush, modifier = Modifier, widthFraction = 0.6f, height = 12.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
