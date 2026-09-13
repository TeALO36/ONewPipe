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

    private val _searchQuery = MutableStateFlow<String?>(null)
    val searchQuery: StateFlow<String?> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilter.ALL)
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    private val _currentChannel = MutableStateFlow<ChannelHeader?>(null)
    /** Set while the grid shows one channel, so its header can be displayed. */
    val currentChannel: StateFlow<ChannelHeader?> = _currentChannel.asStateFlow()

    // Pagination state
    private var currentPageToken: String? = null
    private var currentItems = mutableListOf<MediaItem>()
    private var _isLoadingMore = false
    val isLoadingMore: Boolean get() = _isLoadingMore

    private var currentServiceId: Int = Service.YOUTUBE.serviceId
    private var currentQuery: String? = null

    /** True while a channel page or the subscription feed replaces the default feed. */
    private var showingCustomFeed = false
    private var currentChannelUrl: String? = null
    private var currentFeedSubscriptions: List<Subscription> = emptyList()

    init {
        viewModelScope.launch {
            settingsViewModel.currentService.collectLatest { service ->
                currentServiceId = service.serviceId
                reload()
            }
        }
    }

    /**
     * Goes back to the default feed. A search, a channel page or the
     * subscription feed is dropped; an unchanged default feed is not reloaded
     * so switching tabs costs no network request.
     */
    fun openHome() {
        _currentChannel.value = null
        currentChannelUrl = null
        currentFeedSubscriptions = emptyList()
        val needsReload = showingCustomFeed ||
            !currentQuery.isNullOrBlank() ||
            _selectedCategory.value != TrendingCategory.ALL
        showingCustomFeed = false
        currentQuery = null
        _searchQuery.value = null
        _searchFilter.value = SearchFilter.ALL
        _selectedCategory.value = TrendingCategory.ALL
        if (needsReload) reload()
    }

    fun selectCategory(category: TrendingCategory) {
        if (category == _selectedCategory.value && !showingCustomFeed) return
        _currentChannel.value = null
        showingCustomFeed = false
        _selectedCategory.value = category
        currentQuery = null
        _searchQuery.value = null
        _searchFilter.value = SearchFilter.ALL
        reload()
    }

    /** Reloads whatever the grid currently shows, including a retry after an error. */
    fun reload() {
        currentItems.clear()
        currentPageToken = null
        val channelUrl = currentChannelUrl
        when {
            channelUrl != null -> openChannel(channelUrl)
            currentFeedSubscriptions.isNotEmpty() -> loadSubscriptionFeed(currentFeedSubscriptions)
            currentQuery.isNullOrBlank() -> loadTrending()
            else -> search(currentQuery!!, _searchFilter.value)
        }
    }

    /** Load the next page when the user scrolls to the bottom of the grid. */
    fun loadMore() {
        if (isLoadingMore || currentPageToken == null) return
        _isLoadingMore = true
        viewModelScope.launch {
            try {
                val result = repository.loadMore(currentServiceId, currentPageToken!!)
                currentPageToken = result.nextPageToken
                currentItems.addAll(result.items)
                _state.value = HomeState.Success(currentItems.toList())
            } catch (e: Exception) {
                // Silently ignore pagination errors — the user already sees the first page
            } finally {
                _isLoadingMore = false
            }
        }
    }

    private fun loadTrending() {
        val category = _selectedCategory.value
        viewModelScope.launch {
            _state.value = HomeState.Loading
            try {
                val result = repository.getTrending(currentServiceId, category)
                currentItems.clear()
                currentItems.addAll(result.items)
                currentPageToken = result.nextPageToken
                if (currentItems.isEmpty()) {
                    _state.value = HomeState.Error("No trending items found")
                } else {
                    _state.value = HomeState.Success(currentItems.toList())
                }
            } catch (e: Exception) {
                _state.value = HomeState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun search(query: String, filter: SearchFilter = _searchFilter.value) {
        _currentChannel.value = null
        currentChannelUrl = null
        currentFeedSubscriptions = emptyList()
        showingCustomFeed = false
        val normalizedQuery = query.trim()
        currentQuery = normalizedQuery
        _searchQuery.value = normalizedQuery.takeIf { it.isNotBlank() }
        _searchFilter.value = filter
        if (normalizedQuery.isBlank()) {
            loadTrending()
            return
        }
        viewModelScope.launch {
            _state.value = HomeState.Loading
            try {
                val result = repository.search(currentServiceId, normalizedQuery, filter)
                currentItems.clear()
                currentItems.addAll(result.items)
                currentPageToken = result.nextPageToken
                if (currentItems.isEmpty()) {
                    _state.value = HomeState.Error("No results found for '$normalizedQuery'")
                } else {
                    _state.value = HomeState.Success(currentItems.toList())
                }
            } catch (e: Exception) {
                _state.value = HomeState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun selectSearchFilter(filter: SearchFilter) {
        val query = currentQuery
        _searchFilter.value = filter
        if (!query.isNullOrBlank()) search(query, filter)
    }

    /**
     * Builds the subscription feed: the newest videos of every subscribed
     * channel, merged into one list.
     *
     * Channels that fail to load are skipped so one broken channel cannot
     * empty the whole feed.
     */
    fun loadSubscriptionFeed(subscriptions: List<Subscription>) {
        _currentChannel.value = null
        currentChannelUrl = null
        currentFeedSubscriptions = subscriptions
        showingCustomFeed = true
        currentQuery = null
        _searchQuery.value = null
        _searchFilter.value = SearchFilter.ALL
        currentPageToken = null
        if (subscriptions.isEmpty()) {
            currentItems.clear()
            _state.value = HomeState.Success(emptyList())
            return
        }
        viewModelScope.launch {
            _state.value = HomeState.Loading
            val collected = mutableListOf<MediaItem>()
            for (subscription in subscriptions.take(MAX_FEED_CHANNELS)) {
                val channelItems = runCatching {
                    repository.getChannel(currentServiceId, subscription.url).items
                }.getOrDefault(emptyList())
                collected += channelItems.take(MAX_VIDEOS_PER_CHANNEL)
            }
            currentItems.clear()
            currentItems.addAll(collected.distinctBy { it.url })
            _state.value = if (currentItems.isEmpty()) {
                HomeState.Error("No videos found for your subscriptions")
            } else {
                HomeState.Success(currentItems.toList())
            }
        }
    }

    fun openChannel(url: String) {
        if (url.isBlank()) return
        _currentChannel.value = null
        currentChannelUrl = url
        currentFeedSubscriptions = emptyList()
        showingCustomFeed = true
        currentQuery = null
        _searchQuery.value = null
        _searchFilter.value = SearchFilter.ALL
        viewModelScope.launch {
            _state.value = HomeState.Loading
            try {
                val result = repository.getChannel(currentServiceId, url)
                _currentChannel.value = result.channel
                currentItems.clear()
                currentItems.addAll(result.items)
                currentPageToken = result.nextPageToken
                _state.value = if (currentItems.isEmpty()) {
                    HomeState.Error("No videos found on this channel")
                } else {
                    HomeState.Success(currentItems.toList())
                }
            } catch (e: Exception) {
                _state.value = HomeState.Error(e.message ?: "Unable to load channel")
            }
        }
    }

    companion object {
        /** Keep the feed responsive: a handful of channels, a few videos each. */
        const val MAX_FEED_CHANNELS = 20
        const val MAX_VIDEOS_PER_CHANNEL = 6
    }
}
