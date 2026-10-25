package nl.jellycast.tv.core.data.repository

import kotlinx.coroutines.flow.StateFlow
import nl.jellycast.tv.core.model.Episode
import nl.jellycast.tv.core.model.MediaSegment
import nl.jellycast.tv.core.model.Movie
import nl.jellycast.tv.core.model.PlayableItem
import nl.jellycast.tv.core.model.Series
import nl.jellycast.tv.core.model.continueEpisode

/** The app builds all shelves itself from [getMovies], [getSeries] and [getNextUp]. */
interface LibraryRepository {
    /** A–Z, with the media info the spine needs. */
    suspend fun getMovies(): Result<List<Movie>>

    /** A–Z. */
    suspend fun getSeries(): Result<List<Series>>

    /**
     * Per started series the episode from [continueEpisode], most recently touched series first.
     * Series not started or fully watched are left out.
     */
    suspend fun getNextUp(): Result<List<Episode>>

    /** Most recently played first. */
    suspend fun getResumeMovies(limit: Int): Result<List<Movie>>

    /** Most recently added first. */
    suspend fun getLatestUnwatchedMovies(limit: Int): Result<List<Movie>>

    suspend fun getRandomUnwatchedMovie(): Result<Movie?>

    suspend fun getMovie(id: String): Result<Movie>

    suspend fun getSeriesById(id: String): Result<Series>

    /** In order, in one request even for hundreds of episodes. */
    suspend fun getEpisodes(seriesId: String): Result<List<Episode>>

    suspend fun getPlayable(id: String): Result<PlayableItem>

    /** Empty when the server has none or on an error. */
    suspend fun getSegments(itemId: String): List<MediaSegment>

    /** Works for a movie, an episode, or everything in a season or series. */
    suspend fun setPlayed(itemId: String, played: Boolean): Result<Unit>

    /**
     * What an earlier list already loaded, without a request, so a detail page can show it in the
     * frame of the click and refresh afterwards.
     */
    fun peekMovie(id: String): Movie?

    fun peekSeries(id: String): Series?

    fun peekEpisodes(seriesId: String): List<Episode>?

    suspend fun prefetchEpisodes(seriesId: String)

    /**
     * Reads the start of the video file so a sleeping server disk spins up while the user is still
     * choosing (otherwise ~7 s on the first start after a night). At most once per 5 minutes per
     * item; fails silently.
     */
    suspend fun warmUpMedia(itemId: String)

    /** Increases when [advanceBackdrops] moved a backdrop on; screens then apply `withBackdrop`. */
    val backdropChanges: StateFlow<Int>

    /** From now on every screen shows the next backdrop of these movies and series (by id). */
    fun advanceBackdrops(ownerIds: Collection<String>)

    /** The backdrop every screen shows right now; lists and items from here already have it. */
    fun withBackdrop(movie: Movie): Movie

    fun withBackdrop(series: Series): Series

    fun withBackdrop(episode: Episode): Episode
}
