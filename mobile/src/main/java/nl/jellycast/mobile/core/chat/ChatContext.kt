package nl.jellycast.mobile.core.chat

import nl.jellycast.core.model.ChatCredits
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series

/** What the chat is about: a movie, or an episode with its series and the series' episodes. */
sealed interface ChatSubject {
    /** One conversation per movie or series. */
    val contextKey: String

    /** The item whose credits are loaded. */
    val itemId: String

    data class MovieSubject(val movie: Movie) : ChatSubject {
        override val contextKey: String get() = movie.id
        override val itemId: String get() = movie.id
    }

    data class EpisodeSubject(
        val episode: Episode,
        val series: Series?,
        /** All the series' episodes in air order. */
        val episodes: List<Episode>,
    ) : ChatSubject {
        override val contextKey: String get() = episode.seriesId
        override val itemId: String get() = episode.id
    }
}

/** What the model is told about the series or movie, and how to answer. */
object ChatContext {
    private const val MAX_CAST_NAMES = 25

    fun system(
        subject: ChatSubject,
        cast: ChatCredits?,
        avoidsSpoilers: Boolean,
        searchesWeb: Boolean,
    ): String {
        val parts =
            mutableListOf(
                "You are the companion in JellyCast, a video app. The user is watching something " +
                    "and asks quick questions about it. Answer in the language the user writes " +
                    "in, in a few sentences. Markdown is shown formatted: use bold, lists, links " +
                    "and tables when they help, no headings for a short answer. If you are not " +
                    "sure of a fact, say so rather than guess.",
            )
        parts +=
            when (subject) {
                is ChatSubject.EpisodeSubject -> episodeContext(subject, avoidsSpoilers)
                is ChatSubject.MovieSubject -> movieContext(subject.movie, avoidsSpoilers)
            }
        if (cast != null) parts += castContext(cast)
        if (searchesWeb) parts += searchContext(avoidsSpoilers)
        return parts.joinToString("\n\n")
    }

    private fun episodeContext(subject: ChatSubject.EpisodeSubject, avoidsSpoilers: Boolean) =
        buildList {
            val episode = subject.episode
            val code = code(episode)
            val year = subject.series?.year?.let { " ($it)" }.orEmpty()
            add("The user is watching ${episode.seriesTitle}$year, $code \"${episode.title}\".")
            if (avoidsSpoilers) {
                add(
                    "The user has seen the series up to and including $code. Never reveal " +
                        "anything that happens after that episode: later episodes or seasons, " +
                        "who survives, twists or endings, even though you may know them. If a " +
                        "question can't be answered without that, say so in one sentence.",
                )
            }
            subject.series?.overview?.let { add("About the series: $it") }
            val list =
                episodesUpTo(episode, subject.episodes).map { item ->
                    "${code(item)} \"${item.title}\"" + item.overview?.let { ": $it" }.orEmpty()
                }
            add("Episodes up to the current one:\n" + list.joinToString("\n"))
        }

    /** The regular episodes before [episode] in air order, then [episode] itself. */
    fun episodesUpTo(episode: Episode, episodes: List<Episode>): List<Episode> =
        episodes
            .filter { (it.seasonNumber ?: 0) > 0 }
            .takeWhile { it.id != episode.id } + episode

    private fun movieContext(movie: Movie, avoidsSpoilers: Boolean) = buildList {
        add("The user is watching the movie ${movie.title}${movie.year?.let { " ($it)" }.orEmpty()}.")
        if (movie.genres.isNotEmpty()) add("Genres: ${movie.genres.joinToString(", ")}.")
        movie.overview?.let { add("Overview: $it") }
        if (avoidsSpoilers && !movie.isPlayed) {
            add(
                "The user hasn't finished the movie. Don't reveal the ending, twists or who " +
                    "survives unless the user explicitly asks for it.",
            )
        }
    }

    private fun castContext(cast: ChatCredits): String {
        val actors =
            (cast.actors + cast.guestStars).take(MAX_CAST_NAMES).map { credit ->
                credit.role?.let { "${credit.name} as $it" } ?: credit.name
            }
        return buildList {
            add("Cast: " + actors.joinToString("; "))
            if (cast.directors.isNotEmpty()) {
                add("Directed by: " + cast.directors.joinToString(", ") { it.name })
            }
            if (cast.writers.isNotEmpty()) {
                add("Written by: " + cast.writers.joinToString(", ") { it.name })
            }
        }.joinToString("\n")
    }

    private fun searchContext(avoidsSpoilers: Boolean): String {
        var text =
            "You can search the web. Search only when the answer depends on something this " +
                "context and your own knowledge don't cover well: real events behind the story, " +
                "filming, reception, news about the cast. Answer directly otherwise."
        if (avoidsSpoilers) {
            text +=
                " Search results often summarize later episodes or the ending; leave out " +
                "everything that happens after what the user has seen, and prefer queries that " +
                "won't return spoilers."
        }
        return text
    }

    private fun code(episode: Episode) = "S${episode.seasonNumber ?: 0}E${episode.episodeNumber ?: 0}"
}
