package net.newpipe.app.composable

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.unit.dp
import net.newpipe.app.domain.HomeState
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.TrendingCategory
import net.newpipe.app.theme.Service

/**
 * Main content of the home screen: search bar, service switcher, dynamic title,
 * trending category tabs and the media grid.
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(24.dp))
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

        // Dynamic Title
        Text(
            text = "${selectedItem.title} - ${service.serviceName}",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Trending Category Tabs
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

        when (homeState) {
            is HomeState.Loading -> {
                MediaGrid(items = emptyList(), isLoading = true, modifier = Modifier.weight(1f))
            }
            is HomeState.Success -> {
                MediaGrid(
                    items = homeState.items,
                    onMediaClick = onMediaClick,
                    onDownloadClick = onDownloadClick,
                    modifier = Modifier.weight(1f)
                )
            }
            is HomeState.Error -> {
                MediaGrid(items = emptyList(), errorMessage = homeState.message, modifier = Modifier.weight(1f))
            }
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
