package nl.jellycast.mobile.feature.series

import nl.jellycast.core.model.ExternalIds
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.settings.RatingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesLinksTest {
    private val series =
        testSeries("s1", title = "The Last of Us")
            .copy(
                externalIds = ExternalIds(imdb = "tt3581920", tmdb = "100088"),
                communityRating = 8.6f,
            )

    private val episode =
        testEpisode("e1", seriesId = "s1", season = 1, number = 3)
            .copy(
                seriesTitle = "The Last of Us",
                externalIds = ExternalIds(imdb = "tt14500888"),
                communityRating = 8.9f,
            )

    @Test
    fun `IMDb links the series and the episode by their own ids`() {
        assertEquals(
            "https://www.imdb.com/title/tt3581920/",
            seriesPageUrl(RatingSource.Imdb, series),
        )
        assertEquals(
            "https://www.imdb.com/title/tt14500888/",
            episodePageUrl(RatingSource.Imdb, episode, series),
        )
    }

    @Test
    fun `TMDB links an episode under its series`() {
        assertEquals(
            "https://www.themoviedb.org/tv/100088",
            seriesPageUrl(RatingSource.Tmdb, series),
        )
        assertEquals(
            "https://www.themoviedb.org/tv/100088/season/1/episode/3",
            episodePageUrl(RatingSource.Tmdb, episode, series),
        )
        assertNull(episodePageUrl(RatingSource.Tmdb, episode, null))
    }

    @Test
    fun `Rotten Tomatoes searches by the series name`() {
        val expected = "https://www.rottentomatoes.com/search?search=The%20Last%20of%20Us"
        assertEquals(expected, seriesPageUrl(RatingSource.RottenTomatoes, series))
        assertEquals(expected, episodePageUrl(RatingSource.RottenTomatoes, episode, series))
    }

    @Test
    fun `a series shows the IMDb rating and an episode the TMDB one`() {
        assertEquals(ratingText(8.6f), seriesRating(RatingSource.Imdb, series))
        assertNull(seriesRating(RatingSource.Tmdb, series))
        assertNull(seriesRating(RatingSource.RottenTomatoes, series))
        assertEquals(ratingText(8.9f), episodeRating(RatingSource.Tmdb, episode))
        assertNull(episodeRating(RatingSource.Imdb, episode))
    }
}
