package nl.jellycast.core.data.repository

import android.os.SystemClock
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.AudioPassthrough
import nl.jellycast.core.data.BackdropChoice
import nl.jellycast.core.data.mapper.toEpisode
import nl.jellycast.core.data.mapper.toMediaSegment
import nl.jellycast.core.data.mapper.toMovie
import nl.jellycast.core.data.mapper.toSeries
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.continueEpisode
import nl.jellycast.core.model.isStarted
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

class DefaultLibraryRepository(
    private val api: ApiClient,
    private val store: SessionStore,
    private val images: ImageUrlBuilder,
    private val passthrough: AudioPassthrough,
    private val dispatchers: AppDispatchers,
) : LibraryRepository {
    // Only models, no bitmaps; episodes only for the last few series.
    private val movies = ConcurrentHashMap<String, Movie>()
    private val series = ConcurrentHashMap<String, Series>()
    private val episodes =
        object : LinkedHashMap<String, List<Episode>>(EPISODE_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, List<Episode>>?,
            ) = size > EPISODE_CACHE_SIZE
        }

    private val warmedAt = ConcurrentHashMap<String, Long>()
    private val backdropChoice = BackdropChoice()

    override fun peekMovie(id: String): Movie? = movies[id]?.let { withBackdrop(it) }

    override fun peekSeries(id: String): Series? = series[id]?.let { withBackdrop(it) }

    override fun peekEpisodes(seriesId: String): List<Episode>? =
        synchronized(episodes) { episodes[seriesId] }

    override suspend fun getMovies(): Result<List<Movie>> = load {
        api.itemsApi
            .getItems(
                userId = userId(),
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                recursive = true,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                fields = MovieFields,
                enableImageTypes = ImageTypes,
                imageTypeLimit = BACKDROP_LIMIT,
                enableUserData = true,
                enableTotalRecordCount = false,
            ).content.items
            .let { items ->
                val passthroughAudio = passthrough.codecs()
                items.map { withBackdrop(it.toMovie(images, passthroughAudio)) }
            }.onEach { movies[it.id] = it }
    }

    override suspend fun getSeries(): Result<List<Series>> = load {
        api.itemsApi
            .getItems(
                userId = userId(),
                includeItemTypes = listOf(BaseItemKind.SERIES),
                recursive = true,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                fields = SeriesFields,
                enableImageTypes = ImageTypes,
                imageTypeLimit = BACKDROP_LIMIT,
                enableUserData = true,
                enableTotalRecordCount = false,
            ).content.items
            .map { withBackdrop(it.toSeries(images)) }
            .onEach { series[it.id] = it }
    }

    // The server's next-up episode is not used: it follows the highest watched episode, not the
    // most recently touched one. All steps but the last skip images and text to stay small.
    override suspend fun getNextUp(): Result<List<Episode>> = load {
        val user = userId()
        val picks = continuePicks(lastTouchedPerSeries(user), user)
        if (picks.isEmpty()) return@load emptyList()
        val byId = episodesWithDetails(picks.map { it.episodeId }, user)
        picks.mapNotNull { pick ->
            byId[pick.episodeId]?.let { withBackdrop(it.copy(touchedAt = pick.touchedAt)) }
        }
    }

    private suspend fun lastTouchedPerSeries(user: UUID): List<Episode> {
        val recent =
            lastTouchedEpisodes(user, parentId = null, limit = RECENT_EPISODES_LIMIT)
                .distinctBy { it.seriesId }
        val extra = lastTouchedInEachSeries(seriesOutsideRecent(recent, user), user)
        return (recent + extra)
            .sortedByDescending { it.lastPlayedAt ?: 0 }
            .take(NEXT_UP_LIMIT)
    }

    // Marking a whole series watched gives hundreds of episodes a newer date, which can push a
    // series you are really watching out of the recent window. The server's next-up still has it.
    private suspend fun seriesOutsideRecent(recent: List<Episode>, user: UUID): List<String> {
        val known = recent.mapTo(mutableSetOf()) { it.seriesId }
        return api.tvShowsApi
            .getNextUp(
                userId = user,
                limit = NEXT_UP_LIMIT,
                enableImages = false,
                enableUserData = false,
                enableTotalRecordCount = false,
            ).content.items
            .map { it.toEpisode(images).seriesId }
            .filter { it.isNotEmpty() && it !in known }
            .distinct()
    }

    private suspend fun lastTouchedInEachSeries(
        seriesIds: List<String>,
        user: UUID,
    ): List<Episode> = coroutineScope {
        seriesIds
            .map { seriesId ->
                async {
                    lastTouchedEpisodes(user, seriesId.toUUID(), SERIES_RECENT_LIMIT)
                        .firstOrNull()
                }
            }.awaitAll()
            .filterNotNull()
    }

    private suspend fun continuePicks(lastTouched: List<Episode>, user: UUID): List<ContinuePick> =
        coroutineScope {
            lastTouched
                .map { episode ->
                    async {
                        val id =
                            if (!episode.isPlayed) {
                                episode.id
                            } else {
                                continueEpisode(episodesFrom(episode, user))?.id
                            }
                        id?.let { ContinuePick(episodeId = it, touchedAt = episode.lastPlayedAt) }
                    }
                }.awaitAll()
                .filterNotNull()
        }

    private suspend fun episodesWithDetails(ids: List<String>, user: UUID): Map<String, Episode> =
        api.itemsApi
            .getItems(
                userId = user,
                ids = ids.map { it.toUUID() },
                fields = listOf(ItemFields.OVERVIEW, ItemFields.DATE_CREATED),
                enableImageTypes = ImageTypes,
                imageTypeLimit = BACKDROP_LIMIT,
                enableUserData = true,
                enableTotalRecordCount = false,
            ).content.items
            .map { it.toEpisode(images) }
            .associateBy { it.id }

    /** Started regular episodes, most recently touched first; [parentId] limits to one series. */
    private suspend fun lastTouchedEpisodes(
        user: UUID,
        parentId: UUID?,
        limit: Int,
    ): List<Episode> = api.itemsApi
        .getItems(
            userId = user,
            parentId = parentId,
            includeItemTypes = listOf(BaseItemKind.EPISODE),
            recursive = true,
            sortBy = listOf(ItemSortBy.DATE_PLAYED),
            sortOrder = listOf(SortOrder.DESCENDING),
            limit = limit,
            enableImages = false,
            enableUserData = true,
            enableTotalRecordCount = false,
        ).content.items
        .map { it.toEpisode(images) }
        .filter { it.isStarted && (it.seasonNumber ?: 0) > 0 && it.seriesId.isNotEmpty() }
        .sortedByDescending { it.lastPlayedAt ?: 0 }

    /** [episode] and every episode after it. */
    private suspend fun episodesFrom(episode: Episode, user: UUID): List<Episode> = api.tvShowsApi
        .getEpisodes(
            seriesId = episode.seriesId.toUUID(),
            userId = user,
            isMissing = false,
            startItemId = episode.id.toUUID(),
            enableImages = false,
            enableUserData = true,
        ).content.items
        .map { it.toEpisode(images) }

    override suspend fun getResumeMovies(limit: Int): Result<List<Movie>> = loadMovies(limit) {
        copy(
            filters = listOf(ItemFilter.IS_RESUMABLE),
            sortBy = listOf(ItemSortBy.DATE_PLAYED),
        )
    }

    override suspend fun getLatestUnwatchedMovies(limit: Int): Result<List<Movie>> =
        loadMovies(limit) {
            copy(isPlayed = false, sortBy = listOf(ItemSortBy.DATE_CREATED))
        }

    private suspend fun loadMovies(
        limit: Int,
        query: GetItemsRequest.() -> GetItemsRequest,
    ): Result<List<Movie>> = load {
        val request =
            GetItemsRequest(
                userId = userId(),
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                recursive = true,
                sortOrder = listOf(SortOrder.DESCENDING),
                limit = limit,
                fields = MovieFields,
                enableImageTypes = ImageTypes,
                imageTypeLimit = BACKDROP_LIMIT,
                enableUserData = true,
                enableTotalRecordCount = false,
            ).query()
        val passthroughAudio = passthrough.codecs()
        api.itemsApi
            .getItems(request)
            .content.items
            .map { withBackdrop(it.toMovie(images, passthroughAudio)) }
            .onEach { movies[it.id] = it }
    }

    override suspend fun getRandomUnwatchedMovie(): Result<Movie?> = load {
        api.itemsApi
            .getItems(
                userId = userId(),
                includeItemTypes = listOf(BaseItemKind.MOVIE),
                recursive = true,
                isPlayed = false,
                sortBy = listOf(ItemSortBy.RANDOM),
                limit = 1,
                fields = MovieFields,
                enableImageTypes = ImageTypes,
                imageTypeLimit = BACKDROP_LIMIT,
                enableUserData = true,
                enableTotalRecordCount = false,
            ).content.items
            .firstOrNull()
            ?.toMovie(images, passthrough.codecs())
            ?.let { withBackdrop(it) }
    }

    override suspend fun getMovie(id: String): Result<Movie> = load {
        val item =
            api.itemsApi
                .getItems(
                    userId = userId(),
                    ids = listOf(id.toUUID()),
                    fields = MovieFields,
                    enableImageTypes = ImageTypes,
                    imageTypeLimit = BACKDROP_LIMIT,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                ).content.items
                .firstOrNull() ?: throw InvalidContentException("Movie $id not found")
        withBackdrop(item.toMovie(images, passthrough.codecs())).also { movies[it.id] = it }
    }

    override suspend fun getSeriesById(id: String): Result<Series> = load {
        val item =
            api.itemsApi
                .getItems(
                    userId = userId(),
                    ids = listOf(id.toUUID()),
                    fields = SeriesFields,
                    enableImageTypes = ImageTypes,
                    imageTypeLimit = BACKDROP_LIMIT,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                ).content.items
                .firstOrNull() ?: throw InvalidContentException("Series $id not found")
        withBackdrop(item.toSeries(images)).also { series[it.id] = it }
    }

    override suspend fun getEpisodes(seriesId: String): Result<List<Episode>> = load {
        api.tvShowsApi
            .getEpisodes(
                seriesId = seriesId.toUUID(),
                userId = userId(),
                isMissing = false,
                fields = listOf(ItemFields.OVERVIEW),
                enableImageTypes = listOf(ImageType.PRIMARY),
                imageTypeLimit = 1,
                enableUserData = true,
            ).content.items
            .map { it.toEpisode(images) }
            .also { synchronized(episodes) { episodes[seriesId] = it } }
    }

    override suspend fun getSegments(itemId: String): List<MediaSegment> =
        DebugSegments.override ?: load {
            api.mediaSegmentsApi
                .getItemSegments(itemId.toUUID())
                .content.items
                .mapNotNull { it.toMediaSegment() }
                .sortedBy { it.startMs }
        }.getOrDefault(emptyList())

    override suspend fun getPlayable(id: String): Result<PlayableItem> = load {
        val item =
            api.itemsApi
                .getItems(
                    userId = userId(),
                    ids = listOf(id.toUUID()),
                    fields = listOf(ItemFields.OVERVIEW),
                    enableImageTypes = ImageTypes,
                    imageTypeLimit = BACKDROP_LIMIT,
                    enableUserData = true,
                    enableTotalRecordCount = false,
                ).content.items
                .firstOrNull() ?: throw InvalidContentException("Item $id not found")
        if (item.type == BaseItemKind.EPISODE) {
            PlayableItem.EpisodeItem(withBackdrop(item.toEpisode(images)))
        } else {
            PlayableItem.MovieItem(withBackdrop(item.toMovie(images, passthrough.codecs())))
        }
    }

    override suspend fun prefetchEpisodes(seriesId: String) {
        if (peekEpisodes(seriesId) == null) getEpisodes(seriesId)
    }

    override suspend fun warmUpMedia(itemId: String) {
        val now = SystemClock.elapsedRealtime()
        val last = warmedAt[itemId]
        if (last != null && now - last < WARM_UP_INTERVAL_MS) return
        warmedAt[itemId] = now
        withContext(dispatchers.io) {
            val url = URL("${api.baseUrl}/Videos/$itemId/stream?static=true")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = WARM_UP_TIMEOUT_MS
                connection.readTimeout = WARM_UP_TIMEOUT_MS
                // Token in a header, never in the URL, which can end up in a log.
                connection.setRequestProperty("Authorization", authorizationHeader())
                connection.setRequestProperty("Range", "bytes=0-${WARM_UP_BYTES - 1}")
                val startMs = SystemClock.elapsedRealtime()
                connection.inputStream.use { it.readUpTo(WARM_UP_BYTES) }
                Timber.d(
                    "Disk woken for %s in %d ms",
                    itemId,
                    SystemClock.elapsedRealtime() - startMs,
                )
            } catch (e: IOException) {
                Timber.d(e, "Waking the disk failed for %s", itemId)
                warmedAt.remove(itemId)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun authorizationHeader(): String = AuthorizationHeaderBuilder.buildHeader(
        clientName = api.clientInfo.name,
        clientVersion = api.clientInfo.version,
        deviceId = api.deviceInfo.id,
        deviceName = api.deviceInfo.name,
        accessToken = api.accessToken,
    )

    override suspend fun setPlayed(itemId: String, played: Boolean): Result<Unit> = load<Unit> {
        val id = itemId.toUUID()
        if (played) {
            api.playStateApi.markPlayedItem(itemId = id, userId = userId())
        } else {
            api.playStateApi.markUnplayedItem(itemId = id, userId = userId())
        }
        movies.computeIfPresent(itemId) { _, movie ->
            movie.copy(isPlayed = played, resumePositionMs = 0)
        }
        // Otherwise the series page opens with the old state from the cache.
        synchronized(episodes) {
            episodes.replaceAll { _, list ->
                list.map { episode ->
                    if (episode.isOrBelongsTo(itemId)) {
                        episode.copy(isPlayed = played, resumePositionMs = 0)
                    } else {
                        episode
                    }
                }
            }
        }
    }

    override val backdropChanges: StateFlow<Int> = backdropChoice.changes

    override fun advanceBackdrops(ownerIds: Collection<String>) {
        backdropChoice.advance(ownerIds)
    }

    override fun withBackdrop(movie: Movie): Movie =
        movie.copy(artwork = movie.artwork.chosen(movie.id))

    override fun withBackdrop(series: Series): Series =
        series.copy(artwork = series.artwork.chosen(series.id))

    override fun withBackdrop(episode: Episode): Episode =
        episode.copy(artwork = episode.artwork.chosen(episode.seriesId))

    private fun Artwork.chosen(ownerId: String) =
        withBackdrop(backdropChoice.index(ownerId, backdrops.size))

    /** SDK errors become a [Result.failure]; cancellation passes through. */
    private suspend fun <T> load(block: suspend () -> T): Result<T> = withContext(dispatchers.io) {
        try {
            Result.success(block())
        } catch (e: ApiClientException) {
            Timber.w(e, "Loading the library failed")
            Result.failure(e)
        }
    }

    private suspend fun userId(): UUID =
        requireNotNull(store.session.first().userId) { "Not logged in" }.toUUID()
}

private data class ContinuePick(val episodeId: String, val touchedAt: Long?)

private fun Episode.isOrBelongsTo(itemId: String): Boolean =
    itemId == id || itemId == seasonId || itemId == seriesId

private fun InputStream.readUpTo(byteCount: Int) {
    val buffer = ByteArray(byteCount)
    var total = 0
    while (total < byteCount) {
        val read = read(buffer, total, byteCount - total)
        if (read < 0) break
        total += read
    }
}

private const val NEXT_UP_LIMIT = 20

// Light requests (~0.5 KB per episode without images or text).
private const val RECENT_EPISODES_LIMIT = 300

// Enough to look past episodes that were merely opened (position 0).
private const val SERIES_RECENT_LIMIT = 10

private const val EPISODE_CACHE_SIZE = 4

// Backdrops per item to vary between loads; the server sends only one by default.
private const val BACKDROP_LIMIT = 5

// A woken disk stays awake for minutes.
private const val WARM_UP_INTERVAL_MS = 5 * 60 * 1000L

// Spinning up takes seconds.
private const val WARM_UP_TIMEOUT_MS = 30_000

// Enough to make the disk read, too little to load the network.
private const val WARM_UP_BYTES = 64 * 1024

private val ImageTypes =
    listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.LOGO, ImageType.THUMB)

// `MediaStreams` gives resolution, HDR and Atmos for the spine without a request per movie.
private val MovieFields =
    listOf(
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.DATE_CREATED,
        ItemFields.MEDIA_STREAMS,
        ItemFields.PROVIDER_IDS,
    )

private val SeriesFields =
    listOf(
        ItemFields.OVERVIEW,
        ItemFields.GENRES,
        ItemFields.DATE_LAST_MEDIA_ADDED,
        ItemFields.CHILD_COUNT,
        // Without it the server returns no played percentage per series.
        ItemFields.RECURSIVE_ITEM_COUNT,
        ItemFields.PROVIDER_IDS,
    )
