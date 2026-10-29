package nl.jellycast.core.data.repository

import java.io.IOException
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series

/** Who the library belongs to; a cached library of another server or user is never shown. */
data class LibraryAccount(val serverId: String, val userId: String)

/** Everything one user can watch on one server, as one full sync returns it. */
data class LibraryContent(
    val movies: List<Movie>,
    val series: List<Series>,
    /** Episodes without a series are left out. */
    val episodes: List<Episode>,
)

/** The server refused the access token (HTTP 401): the user has to sign in again. */
class SessionExpiredException : IOException("The server no longer accepts this sign-in")

/** The whole library in one go, for apps that derive every list themselves (mobile). */
interface LibrarySyncRepository {
    /** `null` when nobody is signed in. */
    suspend fun account(): LibraryAccount?

    /**
     * One request each for movies, series and episodes, in parallel. A refused token fails with
     * [SessionExpiredException].
     */
    suspend fun fetchLibrary(): Result<LibraryContent>
}

/** Whether the signed-in server answers at all, asked with a short timeout. */
interface ServerStatusRepository {
    /** `GET /System/Info/Public`; `false` without a server or when it doesn't answer in time. */
    suspend fun isReachable(): Boolean
}
