package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeState {
    object Loading : HomeState()
    data class Success(val items: List<MediaItem>) : HomeState()
    data class Error(val message: String) : HomeState()
}

class HomeViewModel(private val repository: MediaRepository) : ViewModel() {
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        loadTrending()
    }

    fun loadTrending() {
        viewModelScope.launch {
            _state.value = HomeState.Loading
            try {
                val items = repository.getTrending()
                if (items.isEmpty()) {
                    _state.value = HomeState.Error("No trending items found")
                } else {
                    _state.value = HomeState.Success(items)
                }
            } catch (e: Exception) {
                _state.value = HomeState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            loadTrending()
            return
        }
        viewModelScope.launch {
            _state.value = HomeState.Loading
            try {
                val items = repository.search(query)
                if (items.isEmpty()) {
                    _state.value = HomeState.Error("No results found for '$query'")
                } else {
                    _state.value = HomeState.Success(items)
                }
            } catch (e: Exception) {
                _state.value = HomeState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
