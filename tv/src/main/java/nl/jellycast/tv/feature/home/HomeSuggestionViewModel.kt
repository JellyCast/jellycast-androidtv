package nl.jellycast.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.jellycast.core.common.Clock
import nl.jellycast.core.common.HomeSectionLeaves
import nl.jellycast.core.common.LOAD_TIMEOUT_MS
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.data.repository.WatchNextRepository
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.HomeSection
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.watchNextEntries

sealed interface Suggestion {
    val id: String
    val reason: SuggestionReason
    val backdropOwnerId: String

    data class MovieSuggestion(val movie: Movie, override val reason: SuggestionReason) :
        Suggestion {
        override val id: String get() = movie.id
        override val backdropOwnerId: String get() = movie.id
    }

    data class SeriesSuggestion(
        val series: Series,
        val episode: Episode,
        override val reason: SuggestionReason,
    ) : Suggestion {
        override val id: String get() = episode.id
        override val backdropOwnerId: String get() = series.id
    }
}

sealed interface HomeSuggestionUiState {
    data object Loading : HomeSuggestionUiState

    data object Error : HomeSuggestionUiState

    data object Empty : HomeSuggestionUiState

    data class Success(
        val stack: List<Suggestion>,
        val index: Int = 0,
        val playRequest: PlayRequest? = null,
    ) : HomeSuggestionUiState {
        val suggestion: Suggestion get() = stack[index]
    }
}

class HomeSuggestionViewModel(
    private val library: LibraryRepository,
    private val watchNext: WatchNextRepository,
    private val clock: Clock,
    sectionLeaves: HomeSectionLeaves,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeSuggestionUiState>(HomeSuggestionUiState.Loading)
    val uiState: StateFlow<HomeSuggestionUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private var playing = false
    private var browsedDuringRefresh = false

    init {
        // Spin up the server disk for the shown suggestion, but only once browsing settles.
        viewModelScope.launch {
            uiState
                .map { (it as? HomeSuggestionUiState.Success)?.suggestion?.id }
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest {
                    delay(WARM_UP_SETTLE_MS)
                    library.warmUpMedia(it)
                }
        }
        viewModelScope.launch {
            sectionLeaves.leavesOf(HomeSection.Home).collect {
                val stack = (_uiState.value as? HomeSuggestionUiState.Success)?.stack.orEmpty()
                library.advanceBackdrops(stack.map { it.backdropOwnerId })
            }
        }
        viewModelScope.launch {
            library.backdropChanges.drop(1).collect {
                _uiState.update { state ->
                    if (state !is HomeSuggestionUiState.Success) return@update state
                    state.copy(stack = state.stack.map { it.withBackdrop() })
                }
            }
        }
        load()
    }

    private fun Suggestion.withBackdrop(): Suggestion = when (this) {
        is Suggestion.MovieSuggestion -> copy(movie = library.withBackdrop(movie))

        is Suggestion.SeriesSuggestion ->
            copy(series = library.withBackdrop(series), episode = library.withBackdrop(episode))
    }

    fun onRetry() {
        _uiState.value = HomeSuggestionUiState.Loading
        load()
    }

    fun onNext() = browse(+1)

    fun onPrevious() = browse(-1)

    fun onBackToStart() {
        _uiState.update {
            if (it is HomeSuggestionUiState.Success) it.copy(index = 0) else it
        }
    }

    private fun browse(step: Int) {
        _uiState.update { state ->
            if (state !is HomeSuggestionUiState.Success) return@update state
            val target = (state.index + step).coerceIn(0, state.stack.lastIndex)
            if (target != state.index) browsedDuringRefresh = true
            state.copy(index = target)
        }
    }

    fun onPlay() {
        _uiState.update { state ->
            if (state !is HomeSuggestionUiState.Success) return@update state
            val request =
                when (val suggestion = state.suggestion) {
                    is Suggestion.MovieSuggestion ->
                        PlayRequest(suggestion.movie.id, suggestion.movie.resumePositionMs)

                    is Suggestion.SeriesSuggestion ->
                        PlayRequest(suggestion.episode.id, suggestion.episode.resumePositionMs)
                }
            state.copy(playRequest = request)
        }
    }

    fun onPlayHandled() {
        playing = true
        _uiState.update {
            if (it is HomeSuggestionUiState.Success) it.copy(playRequest = null) else it
        }
    }

    fun onHidden() {
        if (!playing) onBackToStart()
    }

    /** Something may have been watched elsewhere meanwhile; the old stack stays until then. */
    fun onVisible() {
        playing = false
        refresh()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val result = withTimeoutOrNull(LOAD_TIMEOUT_MS) { loadStack() }
                _uiState.value =
                    when {
                        result == null || result.isFailure -> HomeSuggestionUiState.Error
                        else -> successOrEmpty(result.getOrThrow(), index = 0)
                    }
            }
    }

    /** Keeps the current stack on failure; keeps the shown suggestion if the user browsed. */
    private fun refresh() {
        val hasStack = _uiState.value is HomeSuggestionUiState.Success ||
            _uiState.value is HomeSuggestionUiState.Empty
        if (!hasStack) return
        browsedDuringRefresh = false
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val result = withTimeoutOrNull(LOAD_TIMEOUT_MS) { loadStack() }
                if (result == null || result.isFailure) return@launch
                val stack = result.getOrThrow()
                val shownId = (_uiState.value as? HomeSuggestionUiState.Success)?.suggestion?.id
                val index =
                    if (browsedDuringRefresh) {
                        stack.indexOfFirst { it.id == shownId }.coerceAtLeast(0)
                    } else {
                        0
                    }
                _uiState.value = successOrEmpty(stack, index)
            }
    }

    private fun successOrEmpty(stack: List<Suggestion>, index: Int): HomeSuggestionUiState =
        if (stack.isEmpty()) {
            HomeSuggestionUiState.Empty
        } else {
            HomeSuggestionUiState.Success(stack, index.coerceAtMost(stack.lastIndex))
        }

    private suspend fun loadStack(): Result<List<Suggestion>> = coroutineScope {
        val nextUp = async { library.getNextUp() }
        val resume = async { library.getResumeMovies(HOME_STACK_SIZE) }
        val latest = async { library.getLatestUnwatchedMovies(HOME_STACK_SIZE) }
        // Only the fallback; on failure rank without it.
        val random = async { library.getRandomUnwatchedMovie() }
        val nextUpEpisodes = nextUp.await().getOrElse { return@coroutineScope Result.failure(it) }
        val resumeMovies = resume.await().getOrElse { return@coroutineScope Result.failure(it) }
        viewModelScope.launch { watchNext.publish(watchNextEntries(nextUpEpisodes, resumeMovies)) }
        val candidates =
            rankHome(
                nextUp = nextUpEpisodes,
                resumeMovies = resumeMovies,
                newMovies = latest.await().getOrElse { return@coroutineScope Result.failure(it) },
                randomMovie = random.await().getOrNull(),
                nowMs = clock.nowMs(),
            )
        val suggestions =
            candidates
                .map { candidate ->
                    async {
                        when (candidate) {
                            is Candidate.MovieCandidate ->
                                Result.success(
                                    Suggestion.MovieSuggestion(candidate.movie, candidate.reason),
                                )

                            is Candidate.EpisodeCandidate -> seriesFor(candidate)
                        }
                    }
                }.awaitAll()
        suggestions.firstOrNull { it.isFailure }?.let { failed ->
            return@coroutineScope Result.failure(requireNotNull(failed.exceptionOrNull()))
        }
        Result.success(suggestions.map { it.getOrThrow() })
    }

    private suspend fun seriesFor(candidate: Candidate.EpisodeCandidate): Result<Suggestion> {
        val episode = candidate.episode
        val series =
            library.peekSeries(episode.seriesId)?.let { Result.success(it) }
                ?: library.getSeriesById(episode.seriesId)
        return series.map { Suggestion.SeriesSuggestion(it, episode, candidate.reason) }
    }
}

private const val WARM_UP_SETTLE_MS = 300L
