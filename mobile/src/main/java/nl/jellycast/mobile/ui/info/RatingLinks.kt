package nl.jellycast.mobile.ui.info

import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.core.settings.RatingSource

/**
 * What the rating sites know a movie, series or episode by. The server puts OMDb first for
 * movies and series, so their community rating is IMDb's; episodes keep TMDb first. Rotten
 * Tomatoes is the critics' score OMDb adds, for movies only.
 */
data class ExternalTitle(
    val kind: Kind,
    /** For an episode its series' name: the sites search by series. */
    val searchName: String,
    val imdbId: String?,
    /** For an episode its series' id: TMDb links episodes under the series. */
    val tmdbId: String?,
    val communityRating: Float?,
    val communityRatingSource: RatingSource,
    val criticRating: Float?,
) {
    sealed interface Kind {
        data object Movie : Kind

        data object Series : Kind

        data class Episode(val season: Int?, val episode: Int?) : Kind
    }

    companion object {
        fun of(movie: Movie) = ExternalTitle(
            Kind.Movie,
            movie.title,
            movie.externalIds.imdb,
            movie.externalIds.tmdb,
            movie.communityRating,
            RatingSource.Imdb,
            movie.criticRating,
        )

        fun of(series: Series) = ExternalTitle(
            Kind.Series,
            series.title,
            series.externalIds.imdb,
            series.externalIds.tmdb,
            series.communityRating,
            RatingSource.Imdb,
            null,
        )

        fun of(episode: Episode, series: Series?) = ExternalTitle(
            Kind.Episode(episode.seasonNumber, episode.episodeNumber),
            episode.seriesTitle,
            episode.externalIds.imdb,
            series?.externalIds?.tmdb,
            episode.communityRating,
            RatingSource.Tmdb,
            null,
        )
    }
}

/** "★ 7.8" or "91%", when the server has this site's rating for the title. */
fun RatingSource.rating(title: ExternalTitle, locale: Locale): String? = when (this) {
    RatingSource.RottenTomatoes -> title.criticRating?.let { "${it.roundToInt()}%" }
    RatingSource.Imdb, RatingSource.Tmdb ->
        title.communityRating
            ?.takeIf { title.communityRatingSource == this }
            ?.let { starRating(it, locale) }
}

fun starRating(value: Float, locale: Locale): String {
    val format =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
    return "★ ${format.format(value)}"
}

/** The title's page; a search where the server keeps no id for this site. */
fun RatingSource.url(title: ExternalTitle): String? = when (this) {
    RatingSource.Imdb -> title.imdbId?.let { "https://www.imdb.com/title/$it/" }
    RatingSource.Tmdb -> title.tmdbId?.let { tmdb ->
        when (val kind = title.kind) {
            ExternalTitle.Kind.Movie -> "$TMDB/movie/$tmdb"
            ExternalTitle.Kind.Series -> "$TMDB/tv/$tmdb"
            is ExternalTitle.Kind.Episode ->
                if (kind.season != null && kind.episode != null) {
                    "$TMDB/tv/$tmdb/season/${kind.season}/episode/${kind.episode}"
                } else {
                    "$TMDB/tv/$tmdb"
                }
        }
    }
    RatingSource.RottenTomatoes -> search(ROTTEN_TOMATOES_SEARCH, "search", title.searchName)
}

/** The person's page, or a search for the name. */
fun RatingSource.personUrl(imdbId: String?, tmdbId: String?, name: String): String = when (this) {
    RatingSource.Imdb ->
        imdbId?.let { "https://www.imdb.com/name/$it/" }
            ?: (search("https://www.imdb.com/find/", "q", name) + "&s=nm")
    RatingSource.Tmdb ->
        tmdbId?.let { "$TMDB/person/$it" } ?: search("$TMDB/search/person", "query", name)
    RatingSource.RottenTomatoes -> search(ROTTEN_TOMATOES_SEARCH, "search", name)
}

private fun search(base: String, key: String, text: String): String =
    "$base?$key=" + URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")

private const val TMDB = "https://www.themoviedb.org"
private const val ROTTEN_TOMATOES_SEARCH = "https://www.rottentomatoes.com/search"
