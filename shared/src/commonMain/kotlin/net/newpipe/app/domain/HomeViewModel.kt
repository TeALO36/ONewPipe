package net.newpipe.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import net.newpipe.app.theme.Service

sealed class HomeState {
    object Loading : HomeState()
    data class Success(val items: List<MediaItem>) : HomeState()
    data class Error(val message: String) : HomeState()
}

class HomeViewModel(
    private val repository: MediaRepository,
    private val settingsViewModel: SettingsViewModel
) : ViewModel() {
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _selectedCategory = MutableStateFlow(TrendingCategory.ALL)
    val selectedCategory: StateFlow<TrendingCategory> = _selectedCategory.asStateFlow()

    private var currentServiceId: Int = Service.YOUTUBE.serviceId
    private var currentQuery: String? = null

    init {
        viewModelScope.launch {
            settingsViewModel.currentService.collectLatest { service ->
                currentServiceId = service.serviceId
                reload()
            }
        }
    }

    fun selectCategory(category: TrendingCategory) {
        if (category == _selectedCategory.value) return
        _selectedCategory.value = category
        currentQuery = null
        reload()
    }

    fun reload() {
        if (currentQuery.isNullOrBlank()) {
            loadTrending()
        } else {
            search(currentQuery!!)
        }
    }

    private fun loadTrending() {
        val category = _selectedCategory.value
        viewModelScope.launch {
            _state.value = HomeState.Loading
            try {
                val items = repository.getTrending(currentServiceId, category)
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
        currentQuery = query
        if (query.isBlank()) {
            loadTrending()
            return
        }
        viewModelScope.launch {
            _state.value = HomeState.Loading
            try {
                val items = repository.search(currentServiceId, query)
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
