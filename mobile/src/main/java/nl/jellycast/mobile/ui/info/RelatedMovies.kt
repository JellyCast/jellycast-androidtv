package nl.jellycast.mobile.ui.info

import nl.jellycast.core.model.ItemDetail
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.SimilarMovie

/**
 * Picks the server's similar movies that really belong with a movie: ones that share people
 * (cast or crew) with it, ranked by shared people and studios. A shared genre alone isn't enough.
 */
fun rankRelated(
    candidates: List<SimilarMovie>,
    detail: ItemDetail,
    excluding: Set<String>,
): List<String> = candidates
    .filter { it.id !in excluding }
    .mapNotNull { candidate ->
        val sharedPeople = candidate.peopleIds.count { it in detail.peopleIds }
        if (sharedPeople < MIN_SHARED_PEOPLE) return@mapNotNull null
        val sharedStudios = candidate.studioIds.count { it in detail.studioIds }
        candidate.id to sharedPeople * PERSON_WEIGHT + sharedStudios
    }.sortedByDescending { it.second }
    .map { it.first }

/**
 * Keeps the ranking between collections, but puts the movies of one collection together and in
 * release order, so a trilogy reads first, second, third.
 */
fun groupedByCollection(movies: List<Movie>): List<Movie> = movies
    .groupBy { it.externalIds.tmdbCollection ?: it.id }
    .values
    .flatMap { group -> group.sortedBy { it.year ?: 0 } }

/** The other library movies of [movie]'s collection, oldest first. */
fun collectionOf(movie: Movie, library: List<Movie>): List<Movie> {
    val collection = movie.externalIds.tmdbCollection ?: return emptyList()
    return library
        .filter { it.id != movie.id && it.externalIds.tmdbCollection == collection }
        .sortedBy { it.year ?: 0 }
}

/**
 * "The Lord of the Rings" from "The Lord of the Rings: The Two Towers" and its siblings; `null`
 * when the titles share no part before a colon.
 */
fun collectionTitle(names: List<String>): String? {
    val first = names.firstOrNull() ?: return null
    val colon = first.indexOf(':')
    if (colon < 0) return null
    val prefix = first.substring(0, colon)
    return prefix.takeIf { names.all { it.startsWith("$prefix:") } }
}

private const val MIN_SHARED_PEOPLE = 2
private const val PERSON_WEIGHT = 2
