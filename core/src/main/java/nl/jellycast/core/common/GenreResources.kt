package nl.jellycast.core.common

import androidx.annotation.StringRes
import nl.jellycast.core.R

// Keys are the English genre names the server takes from TMDb.
val GenreStringRes: Map<String, Int> =
    mapOf(
        "Action" to R.string.genre_action,
        "Action & Adventure" to R.string.genre_action_adventure,
        "Adventure" to R.string.genre_adventure,
        "Animation" to R.string.genre_animation,
        "Comedy" to R.string.genre_comedy,
        "Crime" to R.string.genre_crime,
        "Documentary" to R.string.genre_documentary,
        "Drama" to R.string.genre_drama,
        "Family" to R.string.genre_family,
        "Fantasy" to R.string.genre_fantasy,
        "History" to R.string.genre_history,
        "Horror" to R.string.genre_horror,
        "Kids" to R.string.genre_kids,
        "Music" to R.string.genre_music,
        "Mystery" to R.string.genre_mystery,
        "News" to R.string.genre_news,
        "Reality" to R.string.genre_reality,
        "Romance" to R.string.genre_romance,
        "Sci-Fi & Fantasy" to R.string.genre_scifi_fantasy,
        "Science Fiction" to R.string.genre_science_fiction,
        "Soap" to R.string.genre_soap,
        "Talk" to R.string.genre_talk,
        "Thriller" to R.string.genre_thriller,
        "TV Movie" to R.string.genre_tv_movie,
        "War" to R.string.genre_war,
        "War & Politics" to R.string.genre_war_politics,
        "Western" to R.string.genre_western,
    )

@StringRes
fun genreStringRes(serverName: String): Int? = GenreStringRes[serverName]
