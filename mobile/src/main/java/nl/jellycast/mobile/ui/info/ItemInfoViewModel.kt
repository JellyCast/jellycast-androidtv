package nl.jellycast.mobile.ui.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.DetailRepository
import nl.jellycast.core.model.Credit
import nl.jellycast.core.model.CreditKind
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.GenreNames
import nl.jellycast.core.model.ItemDetail
import nl.jellycast.core.model.MediaInfo
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PersonDetails
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.library.SeriesProgress
import nl.jellycast.mobile.core.library.progressOf
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.RatingSource

/** The credits of a movie, or of an episode plus its series, sorted into the Cast tab's groups. */
data class CastCredits(
    val actors: List<Credit>,
    val guestStars: List<Credit>,
    val directors: List<Credit>,
    val writers: List<Credit>,
    val producers: List<Credit>,
    val details: Map<String, PersonDetails>,
) {
    val hasMakers: Boolean
        get() = directors.isNotEmpty() || writers.isNotEmpty() || producers.isNotEmpty()
}

sealed interface CastState {
    data object Loading : CastState

    data object Failed : CastState

    data class Loaded(val credits: CastCredits) : CastState
}

data class ItemInfoUiState(
    val subject: InfoSubject,
    val movie: Movie? = null,
    /** The episode the panel is about; for the series page its next (or first) episode. */
    val episode: Episode? = null,
    val series: Series? = null,
    val progress: SeriesProgress? = null,
    /** For an episode: the one before it and the next few, across season ends. */
    val episodeWindow: List<Episode> = emptyList(),
    val ratingSource: RatingSource = RatingSource.Imdb,
    /** The first two genres, translated: of the movie, or of the series. */
    val genres: String? = null,
    val media: MediaInfo? = null,
    val cast: CastState = CastState.Loading,
    val collection: List<Movie> = emptyList(),
    val collectionTitle: String? = null,
    /** `null` while the related movies are still loading. */
    val related: List<Movie>? = null,
) {
    val tabs: List<InfoTab>
        get() = when (subject) {
            is InfoSubject.MovieInfo -> listOf(InfoTab.Cast, InfoTab.Related)
            else -> listOf(InfoTab.Episodes, InfoTab.Cast)
        }

    val hasContent: Boolean
        get() = movie != null || series != null
}

enum class InfoTab { Episodes, Cast, Related, Chat }

private data class Loaded(
    val media: MediaInfo? = null,
    val cast: CastState = CastState.Loading,
    val rankedRelatedIds: List<String>? = null,
)

class ItemInfoViewModel(
    private val subject: InfoSubject,
    private val store: LibraryStore,
    private val details: DetailRepository,
    preferences: AppPreferences,
    private val genreNames: GenreNames,
) : ViewModel() {
    private val loaded = MutableStateFlow(Loaded())
    private var loadJob: Job? = null

    val uiState: StateFlow<ItemInfoUiState> =
        combine(store.snapshot, preferences.ratingSource, loaded) { snapshot, source, extras ->
            buildState(snapshot, source, extras)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemInfoUiState(subject))

    init {
        load()
    }

    fun onRetryCast() {
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loaded.update { it.copy(cast = CastState.Loading) }
        loadJob =
            viewModelScope.launch {
                val own = details.getItemDetail(ownItemId()).getOrNull()
                if (own == null) {
                    loaded.update { it.copy(cast = CastState.Failed, rankedRelatedIds = emptyList()) }
                    return@launch
                }
                loaded.update { it.copy(media = own.media) }
                val series =
                    (subject as? InfoSubject.EpisodeInfo)?.let { episode ->
                        details.getItemDetail(episode.seriesId).getOrNull()
                    }
                val credits = castCredits(own, series)
                loaded.update { it.copy(cast = CastState.Loaded(credits)) }
                if (subject is InfoSubject.MovieInfo) loadRelated(subject.movieId, own)
            }
    }

    private suspend fun castCredits(own: ItemDetail, series: ItemDetail?): CastCredits {
        fun of(kind: CreditKind, credits: List<Credit>?) =
            credits.orEmpty().filter { it.kind == kind }.distinctBy { it.personId }
        val actors =
            of(CreditKind.Actor, own.credits).ifEmpty { of(CreditKind.Actor, series?.credits) }
        val producers =
            of(CreditKind.Producer, own.credits).ifEmpty {
                of(CreditKind.Producer, series?.credits)
            }
        val guests = of(CreditKind.GuestStar, own.credits)
        val directors = of(CreditKind.Director, own.credits)
        val writers = of(CreditKind.Writer, own.credits)
        val ids = (actors + guests + directors + writers).map { it.personId }
        val people = details.getPersonDetails(ids).getOrDefault(emptyMap())
        return CastCredits(actors, guests, directors, writers, producers, people)
    }

    private suspend fun loadRelated(movieId: String, own: ItemDetail) {
        val candidates = details.getSimilarMovies(movieId).getOrDefault(emptyList())
        val snapshot = store.snapshot.filterNotNull().first()
        val movie = snapshot.moviesById[movieId]
        val excluded =
            setOf(movieId) + movie?.let { collectionOf(it, snapshot.movies) }.orEmpty().map { it.id }
        loaded.update { it.copy(rankedRelatedIds = rankRelated(candidates, own, excluded)) }
    }

    private fun ownItemId(): String = when (subject) {
        is InfoSubject.MovieInfo -> subject.movieId
        is InfoSubject.EpisodeInfo -> subject.episodeId
        is InfoSubject.SeriesInfo -> subject.seriesId
    }

    private fun buildState(
        snapshot: LibrarySnapshot?,
        source: RatingSource,
        extras: Loaded,
    ): ItemInfoUiState {
        val base =
            ItemInfoUiState(
                subject = subject,
                ratingSource = source,
                media = extras.media,
                cast = extras.cast,
            )
        snapshot ?: return base
        return when (subject) {
            is InfoSubject.MovieInfo -> movieState(base, snapshot, subject.movieId, extras)
            is InfoSubject.EpisodeInfo -> seriesState(base, snapshot, subject.seriesId)
                .let { state -> state.copy(episode = snapshot.episodesById[subject.episodeId]) }
                .let { state -> state.copy(episodeWindow = episodeWindow(state)) }
            is InfoSubject.SeriesInfo -> seriesState(base, snapshot, subject.seriesId).let {
                it.copy(episode = it.progress?.let(::playableNext))
            }
        }
    }

    private fun movieState(
        base: ItemInfoUiState,
        snapshot: LibrarySnapshot,
        movieId: String,
        extras: Loaded,
    ): ItemInfoUiState {
        val movie = snapshot.moviesById[movieId] ?: return base
        val collection = collectionOf(movie, snapshot.movies)
        return base.copy(
            movie = movie,
            genres = genreText(movie.genres),
            collection = collection,
            collectionTitle = collectionTitle(listOf(movie.title) + collection.map { it.title }),
            related =
                extras.rankedRelatedIds?.let { ids ->
                    groupedByCollection(ids.mapNotNull { snapshot.moviesById[it] })
                },
        )
    }

    private fun seriesState(
        base: ItemInfoUiState,
        snapshot: LibrarySnapshot,
        seriesId: String,
    ): ItemInfoUiState {
        val series = snapshot.seriesById[seriesId] ?: return base
        val progress = progressOf(series, snapshot.episodesBySeries[seriesId].orEmpty())
        return base.copy(series = series, progress = progress, genres = genreText(series.genres))
    }

    private fun genreText(genres: List<String>): String? =
        genres.take(GENRES_SHOWN).joinToString(", ") { genreNames.translate(it) }.ifEmpty { null }

    private fun episodeWindow(state: ItemInfoUiState): List<Episode> {
        val episode = state.episode ?: return emptyList()
        val regular = state.progress?.episodes.orEmpty().filter { (it.seasonNumber ?: 0) > 0 }
        val position = regular.indexOfFirst { it.id == episode.id }
        if (position < 0) return listOf(episode)
        val start = (position - EPISODES_BEFORE).coerceAtLeast(0)
        val end = (position + EPISODES_AFTER + 1).coerceAtMost(regular.size)
        return regular.subList(start, end)
    }
}

/** The next episode, or the first one once all are watched. */
fun playableNext(progress: SeriesProgress): Episode? =
    progress.next ?: progress.episodes.firstOrNull { (it.seasonNumber ?: 0) > 0 }

private const val GENRES_SHOWN = 2
private const val EPISODES_BEFORE = 1
private const val EPISODES_AFTER = 6
