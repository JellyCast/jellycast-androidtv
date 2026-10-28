package nl.jellycast.mobile.ui.info

import java.time.LocalDate
import java.util.Locale
import nl.jellycast.core.model.Birthplace
import nl.jellycast.core.model.ExternalIds
import nl.jellycast.core.model.ItemDetail
import nl.jellycast.core.model.MediaInfo
import nl.jellycast.core.model.MediaStreamInfo
import nl.jellycast.core.model.SimilarMovie
import nl.jellycast.core.model.StreamKind
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.settings.RatingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfoLogicTest {
    private val fellowship =
        detail(people = setOf("elijah", "ian", "viggo", "peter"), studios = setOf("wingnut"))

    @Test
    fun `related keeps movies sharing two people, ranked by people then studios`() {
        val candidates =
            listOf(
                SimilarMovie("readyPlayerOne", setOf("ian"), emptySet()),
                SimilarMovie("hobbit", setOf("ian", "peter"), setOf("wingnut")),
                SimilarMovie("twoTowers", setOf("elijah", "ian", "viggo"), setOf("wingnut")),
                SimilarMovie("self", setOf("elijah", "ian"), emptySet()),
            )

        val ranked = rankRelated(candidates, fellowship, excluding = setOf("self"))

        assertEquals(listOf("twoTowers", "hobbit"), ranked)
    }

    @Test
    fun `a collection is pulled together in release order where it first appears`() {
        val hobbit3 = movie("h3", 2014, "hobbit")
        val other = movie("o", 2001, null)
        val hobbit1 = movie("h1", 2012, "hobbit")

        val grouped = groupedByCollection(listOf(hobbit3, other, hobbit1))

        assertEquals(listOf("h1", "h3", "o"), grouped.map { it.id })
    }

    @Test
    fun `collection of a movie is the rest of its collection, oldest first`() {
        val fellowship = movie("f", 2001, "lotr")
        val king = movie("k", 2003, "lotr")
        val towers = movie("t", 2002, "lotr")
        val other = movie("o", 2002, "matrix")

        assertEquals(
            listOf("t", "k"),
            collectionOf(fellowship, listOf(king, other, fellowship, towers)).map {
                it.id
            },
        )
        assertEquals(
            emptyList<String>(),
            collectionOf(movie("x", 2000, null), listOf(king)).map {
                it.id
            },
        )
    }

    @Test
    fun `collection title is the shared part before the colon`() {
        assertEquals(
            "The Lord of the Rings",
            collectionTitle(
                listOf(
                    "The Lord of the Rings: The Two Towers",
                    "The Lord of the Rings: The Return of the King",
                ),
            ),
        )
        assertNull(collectionTitle(listOf("Alien", "Aliens")))
        assertNull(collectionTitle(listOf("Star Wars: A New Hope", "The Empire Strikes Back")))
    }

    @Test
    fun `tech badges read resolution, range, default audio and PGS`() {
        val info =
            MediaInfo(
                listOf(
                    MediaStreamInfo(
                        StreamKind.Video,
                        height = 2160,
                        videoRangeType = "DOVIWithHDR10",
                    ),
                    MediaStreamInfo(StreamKind.Audio, codec = "aac", channels = 2),
                    MediaStreamInfo(
                        StreamKind.Audio,
                        codec = "truehd",
                        profile = "TrueHD + Dolby Atmos",
                        isDefault = true,
                    ),
                    MediaStreamInfo(StreamKind.Subtitle, codec = "PGSSUB"),
                ),
            )

        assertEquals(listOf("4K", "Dolby Vision", "TrueHD Atmos", "PGS"), techBadges(info))
    }

    @Test
    fun `tech badges fall back to the first audio track and skip stereo AAC`() {
        val hd =
            MediaInfo(
                listOf(
                    MediaStreamInfo(StreamKind.Video, height = 800, videoRangeType = "SDR"),
                    MediaStreamInfo(StreamKind.Audio, codec = "aac", channels = 6),
                ),
            )
        val sd =
            MediaInfo(
                listOf(
                    MediaStreamInfo(StreamKind.Video, height = 576),
                    MediaStreamInfo(StreamKind.Audio, codec = "aac"),
                ),
            )

        assertEquals(listOf("1080p", "AAC 6ch"), techBadges(hd))
        assertEquals(listOf("576p"), techBadges(sd))
    }

    @Test
    fun `dts profiles are named`() {
        fun dts(profile: String) = techBadges(
            MediaInfo(
                listOf(MediaStreamInfo(StreamKind.Audio, codec = "dts", profile = profile)),
            ),
        )
        assertEquals(listOf("DTS-HD MA"), dts("DTS-HD MA"))
        assertEquals(listOf("DTS:X"), dts("DTS:X"))
        assertEquals(listOf("DTS"), dts("DTS"))
    }

    @Test
    fun `each site shows only its own rating`() {
        val movie =
            testMovie(
                "m",
            ).copy(
                communityRating = 7.84f,
                criticRating = 91.4f,
                externalIds = ExternalIds(imdb = "tt1", tmdb = "157"),
            )
        val title = ExternalTitle.of(movie)

        assertEquals("★ 7.8", RatingSource.Imdb.rating(title, Locale.ENGLISH))
        assertEquals("★ 7,8", RatingSource.Imdb.rating(title, Locale.forLanguageTag("nl")))
        assertEquals("91%", RatingSource.RottenTomatoes.rating(title, Locale.ENGLISH))
        assertNull(RatingSource.Tmdb.rating(title, Locale.ENGLISH))
    }

    @Test
    fun `episode ratings are TMDB's`() {
        val episode = testEpisode("e", season = 2, number = 5).copy(communityRating = 8.1f)
        val title = ExternalTitle.of(
            episode,
            testSeries("s").copy(externalIds = ExternalIds(tmdb = "1399")),
        )

        assertNull(RatingSource.Imdb.rating(title, Locale.ENGLISH))
        assertEquals("★ 8.1", RatingSource.Tmdb.rating(title, Locale.ENGLISH))
        assertEquals(
            "https://www.themoviedb.org/tv/1399/season/2/episode/5",
            RatingSource.Tmdb.url(title),
        )
    }

    @Test
    fun `title links go to the page or a search`() {
        val movie = testMovie(
            "m",
            title = "Blade Runner",
        ).copy(externalIds = ExternalIds(imdb = "tt0083658", tmdb = "78"))
        val title = ExternalTitle.of(movie)

        assertEquals("https://www.imdb.com/title/tt0083658/", RatingSource.Imdb.url(title))
        assertEquals("https://www.themoviedb.org/movie/78", RatingSource.Tmdb.url(title))
        assertEquals(
            "https://www.rottentomatoes.com/search?search=Blade%20Runner",
            RatingSource.RottenTomatoes.url(title),
        )
        assertNull(RatingSource.Imdb.url(ExternalTitle.of(testMovie("x"))))
    }

    @Test
    fun `person links use the id or search by name`() {
        assertEquals(
            "https://www.imdb.com/name/nm0000190/",
            RatingSource.Imdb.personUrl("nm0000190", null, "M"),
        )
        assertEquals(
            "https://www.imdb.com/find/?q=Anne%20Hathaway&s=nm",
            RatingSource.Imdb.personUrl(null, "1813", "Anne Hathaway"),
        )
        assertEquals(
            "https://www.themoviedb.org/person/1813",
            RatingSource.Tmdb.personUrl(null, "1813", "Anne Hathaway"),
        )
    }

    @Test
    fun `age counts whole years and needs at least one`() {
        val today = LocalDate.of(2026, 10, 28)
        assertEquals(56, ageInYears(LocalDate.of(1969, 11, 4), today))
        assertNull(ageInYears(LocalDate.of(2026, 1, 1), today))
        assertNull(ageInYears(null, today))
    }

    @Test
    fun `birthplace shows the country in the app language`() {
        val uvalde = Birthplace("Uvalde", "USA")

        assertEquals("Uvalde, United States", birthplaceText(uvalde, Locale.ENGLISH))
        assertEquals(
            "Uvalde, Verenigde Staten",
            birthplaceText(uvalde, Locale.forLanguageTag("nl")),
        )
        assertEquals(
            "Paris, Frankrijk",
            birthplaceText(Birthplace("Paris", "France"), Locale.forLanguageTag("nl")),
        )
        assertEquals(
            "Gotham, Atlantis",
            birthplaceText(Birthplace("Gotham", "Atlantis"), Locale.ENGLISH),
        )
        assertEquals("London", birthplaceText(Birthplace("London", null), Locale.ENGLISH))
    }

    private fun detail(people: Set<String>, studios: Set<String>) =
        ItemDetail(emptyList(), people, studios, null)

    private fun movie(id: String, year: Int, collection: String?) =
        testMovie(id).copy(year = year, externalIds = ExternalIds(tmdbCollection = collection))
}
