package net.newpipe.app.composable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.newpipe.app.theme.currentServiceScheme

enum class NavItem(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    TRENDING("Trending", Icons.Filled.LocalFireDepartment),
    SUBSCRIPTIONS("Subscriptions", Icons.Filled.Subscriptions),
    LIBRARY("Library", Icons.Filled.VideoLibrary)
}

/** Compact, labelled navigation for phones and narrow windows. */
@Composable
fun MobileNavigationBar(
    selectedItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
    updateAvailable: Boolean,
    onSettingsClick: () -> Unit
) {
    // Settings belongs to the same navigation surface as the other sections.
    // Keeping it outside NavigationBar made it look like an unrelated floating
    // control and caused a visible gap on small screens.
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        NavItem.entries.forEach { item ->
            NavigationBarItem(
                selected = selectedItem == item,
                onClick = { onItemSelected(item) },
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = {
                    Text(
                        text = item.title,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp
                    )
                }
            )
        }
        NavigationBarItem(
            selected = false,
            onClick = onSettingsClick,
            icon = {
                if (updateAvailable) {
                    BadgedBox(badge = { Badge() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                } else {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            },
            label = {
                Text(
                    text = "Settings",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )
            }
        )
    }
}

@Composable
fun Sidebar(
    modifier: Modifier = Modifier,
    selectedItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
    onServiceSelected: (net.newpipe.app.theme.Service) -> Unit,
    serverConnected: Boolean = false,
    onServerClick: () -> Unit = {},
    updateAvailable: Boolean = false,
    onSettingsClick: () -> Unit = {}
) {
    val serviceColor = currentServiceScheme().primaryContainer

    NavigationRail(
        modifier = modifier
            .width(88.dp)
            .fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = {
            var expanded by remember { mutableStateOf(false) }
            
            // App Logo or Icon can go here
            Box {
                Box(
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(serviceColor)
                        .clickable { expanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    BrandLogo(modifier = Modifier.fillMaxSize().padding(6.dp))
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    net.newpipe.app.theme.Service.entries.forEach { srv ->
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
        Spacer(modifier = Modifier.weight(1f))

        // Settings is intentionally a small secondary action. Update status is
        // shown as a badge here and the check itself lives inside Settings.
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Transparent)
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            if (updateAvailable) {
                BadgedBox(badge = { Badge() }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Server sync entry (bottom of the rail)
        val cloudBackground by animateColorAsState(
            if (serverConnected) serviceColor.copy(alpha = 0.3f) else Color.Transparent
        )
        val cloudTint by animateColorAsState(
            if (serverConnected) serviceColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cloudBackground)
                .clickable { onServerClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Cloud,
                    contentDescription = "Server sync",
                    tint = cloudTint
                )
            }
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
    val color by animateColorAsState(
        if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
    )
    val background by animateColorAsState(
        if (isSelected) selectedColor.copy(alpha = 0.18f) else Color.Transparent
    )

    Box(
        modifier = Modifier
            .padding(vertical = 12.dp)
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
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
