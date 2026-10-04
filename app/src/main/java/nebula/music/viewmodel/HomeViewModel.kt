package nebula.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import nebula.music.data.SearchRepository
import nebula.music.data.models.HomeCard
import nebula.music.data.models.HomeChip
import nebula.music.data.models.HomeFeed
import nebula.music.data.models.HomeSection
import nebula.music.ui.theme.OnboardingStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        // Returning user with saved personalization gets the personalized feed;
        // everyone else gets the default. OnboardingStore.init runs in onCreate,
        // before any ViewModel exists, so in-memory state is ready here.
        // Playlist seeds arrive separately (MainActivity, once playlists load).
        val saved = OnboardingStore.selectedGenreEndpoints
        if (saved.isNotEmpty() || OnboardingStore.artistSeeds.isNotEmpty()) {
            loadPersonalized()
        } else {
            load()
        }
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

    /** Pull-to-refresh keeps the active chip filter — or the genre feed when unfiltered. */
    fun refresh() {
        val chip = _selectedChip.value
        if (chip != null) load(chip) else reloadDefault()
    }

    fun retry() = reloadDefault()

    /** Genre feed for returning users, plain feed otherwise. */
    private fun reloadDefault() {
        val saved = OnboardingStore.selectedGenreEndpoints
        if (saved.isNotEmpty() || OnboardingStore.artistSeeds.isNotEmpty()) {
            loadPersonalized(emptyList())
        } else {
            load(null)
        }
    }

    /**
     * The personalized feed: genre sections, then playlist-based recommendations
     * ("Because you have X"), then artist-based ones ("Because you like Y"),
     * then the default YouTube feed. Every personalized fetch is independent —
     * a failure yields an empty section, never blocks the rest.
     *
     * @param playlistSeeds (playlist name, seed videoIds). Passed in because
     * playlists live behind PlaylistsViewModel and change over time; genres
     * and artists come straight from the store.
     */
    fun loadPersonalized(playlistSeeds: List<Pair<String, List<String>>> = emptyList()) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val home = repository.home(null)
            if (home.sections.isEmpty() && _feed.value == null) {
                _errorMessage.value = "Couldn't load your home feed"
                _isLoading.value = false
                return@launch
            }
            val genreSections = OnboardingStore.selectedGenreEndpoints.map { (title, browseId, params) ->
                async {
                    try {
                        repository.genreTracks(browseId, params).copy(title = title)
                    } catch (_: Exception) {
                        HomeSection(title, emptyList())
                    }
                }
            }.awaitAll().filter { it.cards.isNotEmpty() }
            val playlistSections = playlistSeeds.map { (name, vids) ->
                async { relatedSection("Because you have $name", vids) }
            }.awaitAll().filter { it.cards.isNotEmpty() }
            val artistSections = OnboardingStore.artistSeeds.map { (name, vids) ->
                async { relatedSection("Because you like $name", vids) }
            }.awaitAll().filter { it.cards.isNotEmpty() }
            defaultChips = home.chips
            _feed.value = home.copy(
                chips = defaultChips,
                sections = genreSections + playlistSections + artistSections + home.sections
            )
            _selectedChip.value = null
            _isLoading.value = false
        }
    }

    /** One recommendation section from seed songs' related tracks. */
    private suspend fun relatedSection(title: String, seedIds: List<String>): HomeSection {
        val cards = seedIds.take(2).flatMap { vid ->
            try {
                repository.getRelatedSongs(vid)
            } catch (_: Exception) {
                emptyList()
            }
        }.distinctBy { it.videoId.ifBlank { it.title } }.take(10).map {
            HomeCard(it.title, it.artist, it.thumbnailUrl, videoId = it.videoId)
        }
        return HomeSection(title, cards)
    }
}
