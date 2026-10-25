package nl.jellycast.tv.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.jellycast.tv.core.data.repository.LibraryRepository
import nl.jellycast.tv.core.model.Episode
import nl.jellycast.tv.core.model.MediaSegment
import nl.jellycast.tv.core.model.Movie
import nl.jellycast.tv.core.model.PlayableItem
import nl.jellycast.tv.core.model.Series

class FakeLibraryRepository(
    var movies: List<Movie> = emptyList(),
    var series: List<Series> = emptyList(),
    var nextUp: List<Episode> = emptyList(),
    var episodes: Map<String, List<Episode>> = emptyMap(),
) : LibraryRepository {
    val cachedMovies = mutableMapOf<String, Movie>()
    val cachedSeries = mutableMapOf<String, Series>()
    val cachedEpisodes = mutableMapOf<String, List<Episode>>()
    val playedCalls = mutableListOf<Pair<String, Boolean>>()
    val prefetched = mutableListOf<String>()
    val advancedBackdrops = mutableListOf<Collection<String>>()
    override val backdropChanges = MutableStateFlow(0)

    var failNext = false

    var failCount = 0

    // Makes loading wait until the test calls `complete(Unit)`.
    var gate: CompletableDeferred<Unit>? = null

    var getMoviesCalls = 0

    override suspend fun getMovies(): Result<List<Movie>> = respond {
        getMoviesCalls++
        movies
    }

    override suspend fun getSeries(): Result<List<Series>> = respond { series }

    override suspend fun getNextUp(): Result<List<Episode>> = respond { nextUp }

    override suspend fun getResumeMovies(limit: Int): Result<List<Movie>> = respond {
        movies
            .filter { it.resumePositionMs > 0 && !it.isPlayed }
            .sortedByDescending { it.lastPlayedAt ?: 0 }
            .take(limit)
    }

    override suspend fun getLatestUnwatchedMovies(limit: Int): Result<List<Movie>> = respond {
        movies.filter { !it.isPlayed }.sortedByDescending { it.addedAt ?: 0 }.take(limit)
    }

    override suspend fun getRandomUnwatchedMovie(): Result<Movie?> =
        respond { movies.firstOrNull { !it.isPlayed } }

    override suspend fun getMovie(id: String): Result<Movie> =
        respond { movies.first { it.id == id } }

    override suspend fun getSeriesById(id: String): Result<Series> =
        respond { series.first { it.id == id } }

    override suspend fun getEpisodes(seriesId: String): Result<List<Episode>> =
        respond { episodes[seriesId].orEmpty() }

    override suspend fun getPlayable(id: String): Result<PlayableItem> = respond {
        movies.firstOrNull { it.id == id }?.let { PlayableItem.MovieItem(it) }
            ?: episodes.values.flatten().first { it.id == id }.let { PlayableItem.EpisodeItem(it) }
    }

    override suspend fun setPlayed(itemId: String, played: Boolean): Result<Unit> = respond {
        playedCalls += itemId to played
    }

    override fun advanceBackdrops(ownerIds: Collection<String>) {
        advancedBackdrops += ownerIds
    }

    // Movie id → backdrop that `withBackdrop` shows from now on.
    var chosenBackdrops: Map<String, String> = emptyMap()

    override fun withBackdrop(movie: Movie): Movie =
        chosenBackdrops[movie.id]?.let { movie.copy(artwork = movie.artwork.copy(backdrop = it)) }
            ?: movie

    override fun withBackdrop(series: Series): Series = series

    override fun withBackdrop(episode: Episode): Episode = episode

    override fun peekMovie(id: String): Movie? = cachedMovies[id]

    override fun peekSeries(id: String): Series? = cachedSeries[id]

    override fun peekEpisodes(seriesId: String): List<Episode>? = cachedEpisodes[seriesId]

    override suspend fun prefetchEpisodes(seriesId: String) {
        prefetched += seriesId
    }

    val segments = mutableMapOf<String, List<MediaSegment>>()

    override suspend fun getSegments(itemId: String) = segments[itemId].orEmpty()

    val warmedUp = mutableListOf<String>()

    override suspend fun warmUpMedia(itemId: String) {
        warmedUp += itemId
    }

    private suspend fun <T> respond(block: () -> T): Result<T> {
        gate?.await()
        if (failNext || failCount > 0) {
            failNext = false
            if (failCount > 0) failCount--
            return Result.failure(IllegalStateException("server gone"))
        }
        return Result.success(block())
    }
}
