package nl.jellycast.core.data

import java.time.LocalDateTime
import java.util.UUID
import nl.jellycast.core.data.mapper.blurHashAverageColor
import nl.jellycast.core.data.mapper.spineColor
import nl.jellycast.core.data.mapper.toEpisode
import nl.jellycast.core.data.mapper.toMovie
import nl.jellycast.core.data.mapper.toSeries
import nl.jellycast.core.model.AgeRating
import nl.jellycast.core.model.DynamicRange
import nl.jellycast.core.model.Resolution
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.model.api.AudioSpatialFormat
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMappersTest {
    private val images = ImageUrlBuilder { id, kind, tag, width -> "img/$id/$kind/$tag/$width" }
    private val movieId = UUID.fromString("3a5e1d27-a22c-1a69-7151-f0d6be3bfc5d")
    private val plainId = "3a5e1d27a22c1a697151f0d6be3bfc5d"

    // The Chromecast passes EAC3 (including Atmos) over HDMI, but not TrueHD.
    private val chromecastPassthrough = setOf("ac3", "eac3")

    @Test
    fun `movie with images, progress and media info`() {
        val movie =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.MOVIE,
                name = "Interstellar",
                productionYear = 2014,
                runTimeTicks = 101_442_240_000,
                officialRating = "NL-12",
                genres = listOf("Adventure", "Science Fiction"),
                dateCreated = LocalDateTime.of(2026, 1, 2, 3, 4),
                imageTags = mapOf(ImageType.PRIMARY to "p", ImageType.LOGO to "l"),
                backdropImageTags = listOf("b"),
                imageBlurHashes =
                    mapOf(
                        ImageType.PRIMARY to
                            mapOf("p" to "dyI~Mj%Mofx]_Nt7oft7M_WBazfQRkofWCWCWWofWAWB"),
                        ImageType.LOGO to mapOf("l" to "HcH.j200IAofayt7WBWBayMxofoffQj[WBj[ofWB"),
                    ),
                userData = userData(playbackPositionTicks = 40_000_000_000),
                mediaStreams =
                    listOf(
                        videoStream(width = 3840, range = VideoRangeType.DOVI_WITH_HDR10_PLUS),
                        audioStream(codec = "truehd", atmos = true),
                        audioStream(codec = "eac3", atmos = true),
                    ),
            ).toMovie(images, chromecastPassthrough)

        assertEquals(plainId, movie.id)
        assertEquals(10_144_224L, movie.runtimeMs)
        assertEquals(4_000_000L, movie.resumePositionMs)
        assertEquals(AgeRating("12"), movie.ageRating)
        assertEquals("img/$plainId/Primary/p/240", movie.artwork.poster)
        assertEquals("img/$plainId/Backdrop/b/1280", movie.artwork.backdrop)
        assertEquals("img/$plainId/Logo/l/800", movie.artwork.logo)
        assertEquals("img/$plainId/Primary/p/88", movie.spine.posterUrl)
        assertEquals("img/$plainId/Logo/l/480", movie.spine.logoUrl)
        assertEquals(Resolution.UHD, movie.tech.resolution)
        assertEquals(DynamicRange.DolbyVision, movie.tech.dynamicRange)
        assertTrue(movie.tech.hasAtmos)
        assertFalse("white logo: dark spine", movie.spine.isLight)
    }

    @Test
    fun `movie without images, progress or streams`() {
        val movie =
            BaseItemDto(id = movieId, type = BaseItemKind.MOVIE)
                .toMovie(images, chromecastPassthrough)

        assertEquals("", movie.title)
        assertNull(movie.artwork.poster)
        assertNull(movie.artwork.backdrop)
        assertNull(movie.spine.logoUrl)
        assertNull(movie.ageRating)
        assertNull(movie.tech.resolution)
        assertNull(movie.tech.dynamicRange)
        assertEquals(0L, movie.resumePositionMs)
    }

    @Test
    fun `only Atmos the device passes through counts`() {
        val movie =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.MOVIE,
                mediaStreams =
                    listOf(
                        videoStream(width = 1920, range = VideoRangeType.SDR),
                        audioStream(codec = "truehd", atmos = true),
                    ),
            ).toMovie(images, chromecastPassthrough)

        assertFalse(movie.tech.hasAtmos)
        assertEquals(Resolution.HD, movie.tech.resolution)
        assertNull(movie.tech.dynamicRange)
    }

    @Test
    fun `TrueHD Atmos counts when the device passes TrueHD through`() {
        val movie =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.MOVIE,
                mediaStreams = listOf(audioStream(codec = "truehd", atmos = true)),
            ).toMovie(images, passthroughAudio = setOf("ac3", "eac3", "truehd", "mlp"))

        assertTrue(movie.tech.hasAtmos)
    }

    @Test
    fun `EAC3 Atmos does not count without passthrough`() {
        val movie =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.MOVIE,
                mediaStreams = listOf(audioStream(codec = "EAC3", atmos = true)),
            ).toMovie(images, passthroughAudio = emptySet())

        assertFalse(movie.tech.hasAtmos)
    }

    @Test
    fun `DV profile 8_6 counts as Dolby Vision`() {
        val movie =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.MOVIE,
                mediaStreams = listOf(
                    videoStream(width = 3840, range = VideoRangeType.DOVI_INVALID),
                ),
            ).toMovie(images, chromecastPassthrough)

        assertEquals(DynamicRange.DolbyVision, movie.tech.dynamicRange)
    }

    @Test
    fun `age rating without NL prefix, other ratings unchanged`() {
        fun rating(value: String?) =
            BaseItemDto(id = movieId, type = BaseItemKind.SERIES, officialRating = value)
                .toSeries(images)
                .ageRating

        assertEquals(AgeRating("16"), rating("NL-16"))
        assertEquals(AgeRating("R"), rating("R"))
        assertEquals(AgeRating("TV-MA"), rating("TV-MA"))
        assertNull(rating(null))
        assertNull(rating(" "))
    }

    @Test
    fun `an episode uses the series thumb and backdrop`() {
        val seriesId = UUID.fromString("88ad1464-e02c-fe27-a50f-490bfe3d13b0")
        val episode =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.EPISODE,
                name = "Violet",
                seriesId = seriesId,
                seriesName = "The Bear",
                seasonId = UUID.fromString("1f2e3d4c-5b6a-7980-a1b2-c3d4e5f60718"),
                parentIndexNumber = 3,
                indexNumber = 4,
                parentThumbItemId = seriesId,
                parentThumbImageTag = "t",
                parentBackdropItemId = seriesId,
                parentBackdropImageTags = listOf("b", "c"),
                imageTags = mapOf(ImageType.PRIMARY to "still"),
            ).toEpisode(images)

        val series = "88ad1464e02cfe27a50f490bfe3d13b0"
        assertEquals(series, episode.seriesId)
        assertEquals("img/$series/Thumb/t/392", episode.artwork.landscape)
        assertEquals("img/$series/Backdrop/b/1280", episode.artwork.backdrop)
        assertEquals(
            listOf("img/$series/Backdrop/b/1280", "img/$series/Backdrop/c/1280"),
            episode.artwork.backdrops,
        )
        assertNull(episode.artwork.logo)
        assertEquals(3, episode.seasonNumber)
        assertEquals(4, episode.episodeNumber)
        assertEquals("1f2e3d4c5b6a7980a1b2c3d4e5f60718", episode.seasonId)
    }

    @Test
    fun `critic rating and external ids are mapped, blank ids are dropped`() {
        val movie =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.MOVIE,
                criticRating = 91f,
                communityRating = 7.8f,
                providerIds =
                    mapOf("Imdb" to "tt0816692", "Tmdb" to "157336", "TmdbCollection" to " "),
            ).toMovie(images, chromecastPassthrough)

        assertEquals(91f, movie.criticRating)
        assertEquals(7.8f, movie.communityRating)
        assertEquals("tt0816692", movie.externalIds.imdb)
        assertEquals("157336", movie.externalIds.tmdb)
        assertNull(movie.externalIds.tmdbCollection)
    }

    @Test
    fun `an episode carries its series poster`() {
        val seriesId = UUID.fromString("88ad1464-e02c-fe27-a50f-490bfe3d13b0")
        val episode =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.EPISODE,
                seriesId = seriesId,
                seriesPrimaryImageTag = "sp",
            ).toEpisode(images)

        assertEquals("img/88ad1464e02cfe27a50f490bfe3d13b0/Primary/sp/240", episode.artwork.poster)
    }

    @Test
    fun `an episode without a series poster tag has no poster`() {
        val episode = BaseItemDto(id = movieId, type = BaseItemKind.EPISODE).toEpisode(images)

        assertNull(episode.artwork.poster)
    }

    @Test
    fun `an episode without a series thumb uses the still`() {
        val episode =
            BaseItemDto(
                id = movieId,
                type = BaseItemKind.EPISODE,
                imageTags = mapOf(ImageType.PRIMARY to "still"),
            ).toEpisode(images)

        assertEquals("img/$plainId/Primary/still/392", episode.artwork.landscape)
        assertEquals("", episode.seriesId)
        assertNull(episode.seasonId)
        assertNull(episode.artwork.backdrop)
        assertEquals(emptyList<String>(), episode.artwork.backdrops)
    }

    @Test
    fun `average color from a BlurHash`() {
        // Characters 2–5 are the DC component; for this hash (155, 161, 161).
        assertEquals(0x9BA1A1, blurHashAverageColor("HcH.j200IAofayt7WBWBayMxofoffQj[WBj[ofWB"))
        assertNull(blurHashAverageColor(null))
        assertNull(blurHashAverageColor("abc"))
        assertNull(blurHashAverageColor("ab\u0000\u0000\u0000\u0000"))
    }

    @Test
    fun `a dark logo gives a light spine, a light logo a dark one`() {
        val poster = "dyI~Mj%Mofx]_Nt7oft7M_WBazfQRkofWCWCWWofWAWB"
        // Red logo with lightness ~25%.
        val darkLogo = spineColor(poster, "NNEkEN00000000000000000000")
        val lightLogo = spineColor(poster, "HcH.j200IAofayt7WBWBayMxofoffQj[WBj[ofWB")

        assertTrue(darkLogo.isLight)
        assertFalse(lightLogo.isLight)
        assertTrue(lightness(lightLogo.argb) < 0.2f)
        assertTrue(lightness(darkLogo.argb) > 0.7f)
        // The background behind light text stays dark, even on a light spine.
        assertTrue(lightness(darkLogo.darkArgb) < 0.2f)
        assertEquals(lightLogo.argb, lightLogo.darkArgb)
    }

    @Test
    fun `without BlurHashes a neutral dark spine`() {
        val spine = spineColor(null, null)

        assertFalse(spine.isLight)
        assertTrue(lightness(spine.argb) < 0.2f)
    }

    private fun lightness(argb: Long): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return (maxOf(r, g, b) + minOf(r, g, b)) / 2
    }

    private fun userData(playbackPositionTicks: Long) = UserItemDataDto(
        playbackPositionTicks = playbackPositionTicks,
        playCount = 0,
        isFavorite = false,
        played = false,
        key = "k",
        itemId = movieId,
    )

    private fun videoStream(width: Int, range: VideoRangeType) = MediaStream(
        type = MediaStreamType.VIDEO,
        index = 0,
        width = width,
        videoRangeType = range,
        isInterlaced = false,
        isDefault = true,
        isForced = false,
        isHearingImpaired = false,
        isExternal = false,
        isTextSubtitleStream = false,
        supportsExternalStream = false,
    )

    private fun audioStream(codec: String, atmos: Boolean) = MediaStream(
        type = MediaStreamType.AUDIO,
        index = 1,
        codec = codec,
        audioSpatialFormat = if (atmos) AudioSpatialFormat.DOLBY_ATMOS else AudioSpatialFormat.NONE,
        isInterlaced = false,
        isDefault = false,
        isForced = false,
        isHearingImpaired = false,
        isExternal = false,
        isTextSubtitleStream = false,
        supportsExternalStream = false,
    )
}
