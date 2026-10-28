package nl.jellycast.mobile.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.jellycast.core.common.genreStringRes
import nl.jellycast.mobile.R

@Composable
fun sectionTitle(key: SectionKey): String = when (key) {
    SectionKey.AddedThisWeek -> stringResource(R.string.library_section_week)
    SectionKey.AddedThisMonth -> stringResource(R.string.library_section_month)
    SectionKey.Earlier -> stringResource(R.string.library_section_earlier)
    is SectionKey.Letter -> key.letter
    is SectionKey.Decade -> stringResource(R.string.library_section_decade, key.decade)
    SectionKey.UnknownYear -> stringResource(R.string.library_section_unknown_year)
    SectionKey.OverThreeHours -> stringResource(R.string.library_section_over_3h)
    SectionKey.TwoToThreeHours -> stringResource(R.string.library_section_2_to_3h)
    SectionKey.UnderTwoHours -> stringResource(R.string.library_section_under_2h)
}

/** The server's English genre in the app language; an unknown genre stays as it is. */
@Composable
fun genreName(genre: String): String {
    val res =
        genreStringRes(genre)
            ?: if (genre == POLITICS) R.string.library_genre_politics else return genre
    return stringResource(res)
}

@Composable
fun statusTitle(status: WatchStatus?): String = when (status) {
    null -> stringResource(R.string.library_status_everything)
    WatchStatus.NotStarted -> stringResource(R.string.library_status_not_watched)
    WatchStatus.InProgress -> stringResource(R.string.library_status_in_progress)
    WatchStatus.Watched -> stringResource(R.string.library_status_watched)
}

@Composable
fun kindTitle(kind: LibraryKind): String = when (kind) {
    LibraryKind.All -> stringResource(R.string.library_kind_all)
    LibraryKind.Series -> stringResource(R.string.library_kind_series)
    LibraryKind.Movies -> stringResource(R.string.library_kind_movies)
}

@Composable
fun sortTitle(sort: LibrarySort): String = when (sort) {
    LibrarySort.Newest -> stringResource(R.string.library_sort_newest)
    LibrarySort.Title -> stringResource(R.string.library_sort_title)
    LibrarySort.Year -> stringResource(R.string.library_sort_year)
    LibrarySort.Length -> stringResource(R.string.library_sort_length)
}

// Split off "War & Politics"; the shared genre names have no entry of its own for it.
private const val POLITICS = "Politics"
