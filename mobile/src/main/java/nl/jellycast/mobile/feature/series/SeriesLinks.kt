package nl.jellycast.mobile.feature.series

import java.net.URLEncoder
import java.util.Locale
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.core.settings.RatingSource

/** The series' page on the chosen site; a search where the server keeps no id for it. */
internal fun seriesPageUrl(source: RatingSource, series: Series): String? = when (source) {
    RatingSource.Imdb -> series.externalIds.imdb?.let { "https://www.imdb.com/title/$it/" }
    RatingSource.Tmdb -> series.externalIds.tmdb?.let { "https://www.themoviedb.org/tv/$it" }
    RatingSource.RottenTomatoes -> rottenTomatoesSearch(series.title)
}

/** TMDb links episodes under the series, so its id comes from [series]. */
internal fun episodePageUrl(source: RatingSource, episode: Episode, series: Series?): String? =
    when (source) {
        RatingSource.Imdb -> episode.externalIds.imdb?.let { "https://www.imdb.com/title/$it/" }

        RatingSource.Tmdb -> {
            val tmdb = series?.externalIds?.tmdb
            val season = episode.seasonNumber
            val number = episode.episodeNumber
            when {
                tmdb == null -> null

                season != null && number != null ->
                    "https://www.themoviedb.org/tv/$tmdb/season/$season/episode/$number"

                else -> "https://www.themoviedb.org/tv/$tmdb"
            }
        }

        RatingSource.RottenTomatoes -> rottenTomatoesSearch(episode.seriesTitle)
    }

/** The server puts OMDb first for series, so their community rating is IMDb's. */
internal fun seriesRating(source: RatingSource, series: Series): String? =
    series.communityRating.takeIf { source == RatingSource.Imdb }?.let(::ratingText)

/** Episodes keep TMDb first, so theirs is TMDb's. */
internal fun episodeRating(source: RatingSource, episode: Episode): String? =
    episode.communityRating.takeIf { source == RatingSource.Tmdb }?.let(::ratingText)

internal fun ratingText(value: Float): String =
    "★ " + String.format(Locale.getDefault(), "%.1f", value)

private fun rottenTomatoesSearch(name: String): String =
    "https://www.rottentomatoes.com/search?search=" +
        URLEncoder.encode(name, Charsets.UTF_8).replace("+", "%20")
