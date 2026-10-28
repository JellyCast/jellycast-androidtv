package nl.jellycast.mobile.core.settings

/** Which site's rating and links the app shows (Settings → Ratings). */
enum class RatingSource(val siteName: String) {
    Imdb("IMDb"),
    RottenTomatoes("Rotten Tomatoes"),
    Tmdb("TMDB"),
}
