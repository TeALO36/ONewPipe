package net.newpipe.app.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import net.newpipe.app.theme.currentServiceScheme

/**
 * Search field with the recent-searches drop-down NewPipe shows when the field
 * is focused. Every entry can be replayed or removed, and the whole history can
 * be cleared.
 */
@Composable
fun GlassSearchBar(
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    history: List<String> = emptyList(),
    onHistoryRemove: (String) -> Unit = {},
    onHistoryClear: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val serviceColor = currentServiceScheme().primaryContainer

    val submit: (String) -> Unit = { query ->
        showHistory = false
        onSearch(query)
    }

    Box(modifier = modifier) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search Icon",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { submit(searchQuery) }
                .padding(8.dp)
        )
        
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search videos, music...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.clickable {
                            searchQuery = ""
                            submit("")
                        }
                    )
                }
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && history.isNotEmpty()) showHistory = true
                }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                        submit(searchQuery)
                        true
                    } else {
                        false
                    }
                },
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { submit(searchQuery) },
                onSearch = { submit(searchQuery) }
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            )
        )
    }

        DropdownMenu(
            expanded = showHistory && history.isNotEmpty(),
            onDismissRequest = { showHistory = false }
        ) {
            history.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry) },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove from history",
                            modifier = Modifier.clickable { onHistoryRemove(entry) }
                        )
                    },
                    onClick = {
                        searchQuery = entry
                        submit(entry)
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Clear search history") },
                onClick = {
                    onHistoryClear()
                    showHistory = false
                }
            )
        }
    }
}
