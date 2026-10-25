package nl.jellycast.tv.core.model

sealed interface ShelfKind {
    data object ContinueWatching : ShelfKind

    data object New : ShelfKind

    data object NotStarted : ShelfKind

    // [name] is already translated to the UI language.
    data class Genre(val name: String) : ShelfKind
}

data class Shelf<T>(val kind: ShelfKind, val items: List<T>)

const val MIN_GENRE_SIZE = 3

const val NEW_SHELF_SIZE = 20

const val NEW_SHELF_DAYS = 30

private const val DAY_MS = 24L * 60 * 60 * 1000

// Without a time limit, New after a large import would be a random pick.
fun <T> newShelfItems(
    items: List<T>,
    nowMs: Long,
    addedAt: (T) -> Long?,
    isPlayed: (T) -> Boolean,
): List<T> {
    val since = nowMs - NEW_SHELF_DAYS * DAY_MS
    return items
        .filter { item -> !isPlayed(item) && (addedAt(item) ?: Long.MIN_VALUE) >= since }
        .sortedByDescending(addedAt)
        .take(NEW_SHELF_SIZE)
}

interface GenreNames {
    // Unknown genres are returned unchanged.
    fun translate(serverName: String): String

    // Locale-aware A–Z: "Épouvante" sorts under E, not after "Western".
    fun order(): Comparator<String>
}

object ServerGenreNames : GenreNames {
    override fun translate(serverName: String): String = serverName

    override fun order(): Comparator<String> = String.CASE_INSENSITIVE_ORDER
}

fun <T> genreShelves(
    items: List<T>,
    genreNames: GenreNames,
    genresOf: (T) -> List<String>,
): List<Shelf<T>> = items
    .flatMap { item -> genresOf(item).map { genreNames.translate(it) to item } }
    .groupBy({ it.first }, { it.second })
    .filterValues { it.size >= MIN_GENRE_SIZE }
    .toSortedMap(genreNames.order())
    .map { (name, members) -> Shelf(ShelfKind.Genre(name), members.distinct()) }

// [all] only feeds the genre shelves; it is not a shelf itself.
fun <T> buildShelves(
    continueWatching: List<T>,
    new: List<T>,
    all: List<T>,
    genreNames: GenreNames,
    notStarted: List<T> = emptyList(),
    genresOf: (T) -> List<String>,
): List<Shelf<T>> = buildList {
    add(Shelf(ShelfKind.ContinueWatching, continueWatching))
    add(Shelf(ShelfKind.New, new))
    add(Shelf(ShelfKind.NotStarted, notStarted))
    addAll(genreShelves(all, genreNames, genresOf))
}.filter { it.items.isNotEmpty() }
