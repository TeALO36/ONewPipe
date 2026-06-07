package net.newpipe.app.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.newpipe.app.theme.currentServiceScheme

enum class NavItem(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    TRENDING("Trending", Icons.Filled.LocalFireDepartment),
    SUBSCRIPTIONS("Subscriptions", Icons.Filled.Subscriptions),
    LIBRARY("Library", Icons.Filled.VideoLibrary)
}

@Composable
fun Sidebar(
    modifier: Modifier = Modifier,
    selectedItem: NavItem,
    onItemSelected: (NavItem) -> Unit
) {
    val serviceColor = currentServiceScheme().primaryContainer

    NavigationRail(
        modifier = modifier
            .width(88.dp)
            .fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = {
            // App Logo or Icon can go here
            Box(
                modifier = Modifier
                    .padding(vertical = 24.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(serviceColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Logo",
                    tint = Color.White
                )
            }
        }
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        NavItem.entries.forEach { item ->
            SidebarItem(
                item = item,
                isSelected = selectedItem == item,
                selectedColor = serviceColor,
                onClick = { onItemSelected(item) }
            )
        }
    }
}

@Composable
fun SidebarItem(
    item: NavItem,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isSelected) 1.1f else 1.0f)
    val color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) selectedColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = color,
                modifier = Modifier.scale(scale)
            )
        }
    }
}
