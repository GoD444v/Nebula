package com.example.nebula.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nebula.data.SearchRepository
import com.example.nebula.data.models.HomeChip
import com.example.nebula.data.models.HomeFeed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Home tab feed: the anonymous FEmusic_home browse, so a fresh install shows
 * real content instead of an empty card. No history mining yet — YouTube serves it.
 */
class HomeViewModel(
    private val repository: SearchRepository = SearchRepository()
) : ViewModel() {

    private val _feed = MutableStateFlow<HomeFeed?>(null)
    val feed: StateFlow<HomeFeed?> = _feed.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // SearchRepository swallows network errors into an empty feed, so "empty" IS the failure
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedChip = MutableStateFlow<String?>(null)
    val selectedChip: StateFlow<String?> = _selectedChip.asStateFlow()

    // Chips come back on every page; keep the list from the default feed pinned
    private var defaultChips: List<HomeChip> = emptyList()
    private var loadJob: Job? = null

    init {
        load()
    }

    /** params = null loads the default feed; a chip's params reloads just that category. */
    fun load(params: String? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.home(params)
            if (result.sections.isEmpty()) {
                // Empty sections IS the failure signal — must match HomeScreen's check,
                // so a sectionless result never overwrites a good feed with a broken one.
                if (_feed.value == null) {
                    _errorMessage.value = "Couldn't load your home feed"
                }
            } else {
                if (params == null) defaultChips = result.chips
                _feed.value = result.copy(chips = defaultChips.ifEmpty { result.chips })
                _selectedChip.value = params
            }
            _isLoading.value = false
        }
    }

    /** Tapping the active chip jumps back to the default feed. */
    fun onChipSelected(chip: HomeChip) =
        if (chip.params == _selectedChip.value) load(null) else load(chip.params)

    /** Pull-to-refresh keeps the active chip filter. */
    fun refresh() = load(_selectedChip.value)

    fun retry() = load(null)
}
