package com.example.nebula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nebula.data.SearchRepository
import com.example.nebula.data.models.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: SearchRepository = SearchRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow<String?>(SearchRepository.SearchFilter.SONGS)
    val filter: StateFlow<String?> = _filter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _continuationToken = MutableStateFlow<String?>(null)
    val continuationToken: StateFlow<String?> = _continuationToken.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var lastKey: String? = null

    init {
        // Fast search with 300ms debounce
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.isNotEmpty()) {
                        performSearchInternal(trimmed, _filter.value)
                    } else {
                        _searchResults.value = emptyList()
                        _continuationToken.value = null
                        _errorMessage.value = null
                        lastKey = null
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun setFilter(newFilter: String?) {
        if (_filter.value == newFilter) return
        _filter.value = newFilter
        lastKey = null // Reset cache key to force search with new filter
        val query = _searchQuery.value.trim()
        if (query.isNotEmpty()) {
            performSearchInternal(query, newFilter)
        }
    }

    // Alias for backwards compatibility
    fun selectFilter(newFilter: String?) = setFilter(newFilter)

    fun performSearch() {
        val query = _searchQuery.value.trim()
        if (query.isNotEmpty()) {
            performSearchInternal(query, _filter.value)
        }
    }

    private fun performSearchInternal(query: String, currentFilter: String?) {
        val key = "$query|${currentFilter ?: "all"}"
        if (key == lastKey && _searchResults.value.isNotEmpty()) return
        lastKey = key

        _errorMessage.value = null
        searchJob?.cancel()
        _isLoading.value = true

        searchJob = viewModelScope.launch {
            try {
                // Echo 3-step search: initial search + automatic multi-page continuation to load 50+ results
                val result = repository.searchInitialAndFill(
                    query = query,
                    filter = currentFilter,
                    targetCount = 50,
                    maxPages = 3
                )
                _searchResults.value = result.items.distinctBy { it.videoId }
                _continuationToken.value = result.continuationToken
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                lastKey = null
                _errorMessage.value = "Search failed — check connection"
                _searchResults.value = emptyList()
                _continuationToken.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        val token = _continuationToken.value ?: return
        if (_isLoading.value || _isLoadingMore.value) return
        if (_searchResults.value.size >= 60) return // Cap at 60 results max

        loadMoreJob?.cancel()
        _isLoadingMore.value = true

        loadMoreJob = viewModelScope.launch {
            try {
                val nextResult = repository.searchContinuation(token)
                val currentList = _searchResults.value
                val combined = (currentList + nextResult.items).distinctBy { it.videoId }
                _searchResults.value = combined.take(60)
                _continuationToken.value = if (combined.size < 60) nextResult.continuationToken else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Failed to load more, keep existing items
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
}
