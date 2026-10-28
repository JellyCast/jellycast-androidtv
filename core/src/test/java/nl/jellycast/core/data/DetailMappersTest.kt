package nl.jellycast.core.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import nl.jellycast.core.data.mapper.birthplace
import nl.jellycast.core.data.mapper.calendarDate
import nl.jellycast.core.data.mapper.cleanBiography
import nl.jellycast.core.data.mapper.toItemDetail
import nl.jellycast.core.model.Birthplace
import nl.jellycast.core.model.CreditKind
import nl.jellycast.core.model.StreamKind
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.PersonKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailMappersTest {
    private val images = ImageUrlBuilder { id, kind, tag, width -> "img/$id/$kind/$tag/$width" }
    private val actorId = UUID.fromString("3e78201a-c317-a3c4-2c21-f9c9aea296e2")
    private val directorId = UUID.fromString("40c128a4-4c32-9e75-019a-cb43a763d89f")
    private val studioId = UUID.fromString("5f83e77e-3f46-5e52-8750-d05c6d9cdb0c")

    @Test
    fun `item detail keeps known credit kinds in order with photos, studios and streams`() {
        val item =
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.MOVIE,
                people =
                    listOf(
                        BaseItemPerson(
                            id = actorId,
                            name = "Matthew",
                            role = "Cooper",
                            type = PersonKind.ACTOR,
                            primaryImageTag = "t",
                        ),
                        BaseItemPerson(
                            id = directorId,
                            name = "Christopher",
                            type = PersonKind.DIRECTOR,
                        ),
                        BaseItemPerson(
                            id = UUID.randomUUID(),
                            name = "Hans",
                            type = PersonKind.COMPOSER,
                        ),
                    ),
                studios = listOf(NameGuidPair(name = "Syncopy", id = studioId)),
                mediaSources =
                    listOf(
                        MediaSourceInfo(
                            protocol = MediaProtocol.FILE,
                            type = MediaSourceType.DEFAULT,
                            isRemote = false,
                            readAtNativeFramerate = false,
                            ignoreDts = false,
                            ignoreIndex = false,
                            genPtsInput = false,
                            supportsTranscoding = true,
                            supportsDirectStream = true,
                            supportsDirectPlay = true,
                            isInfiniteStream = false,
                            requiresOpening = false,
                            requiresClosing = false,
                            requiresLooping = false,
                            supportsProbing = true,
                            transcodingSubProtocol = MediaStreamProtocol.HTTP,
                            hasSegments = false,
                            useMostCompatibleTranscodingProfile = false,
                            mediaStreams =
                                listOf(
                                    MediaStream(
                                        type = MediaStreamType.VIDEO,
                                        height = 2160,
                                        codec = "hevc",
                                        index = 0,
                                        isInterlaced = false,
                                        isDefault = true,
                                        isForced = false,
                                        isHearingImpaired = false,
                                        isExternal = false,
                                        isTextSubtitleStream = false,
                                        supportsExternalStream = false,
                                    ),
                                ),
                        ),
                    ),
            )

        val detail = item.toItemDetail(images)

        assertEquals(listOf(CreditKind.Actor, CreditKind.Director), detail.credits.map { it.kind })
        assertEquals("Cooper", detail.credits[0].role)
        assertEquals(
            "img/3e78201ac317a3c42c21f9c9aea296e2/Primary/t/192",
            detail.credits[0].imageUrl,
        )
        assertNull(detail.credits[1].imageUrl)
        assertEquals(3, detail.peopleIds.size)
        assertEquals(setOf("5f83e77e3f465e528750d05c6d9cdb0c"), detail.studioIds)
        assertEquals(StreamKind.Video, detail.media?.streams?.single()?.kind)
        assertEquals(2160, detail.media?.streams?.single()?.height)
    }

    @Test
    fun `a birthday sent as the evening before in UTC lands on the right day`() {
        assertEquals(LocalDate.of(1969, 11, 4), calendarDate(LocalDateTime.of(1969, 11, 3, 23, 0)))
    }

    @Test
    fun `birthplace keeps the first and last part`() {
        assertEquals(Birthplace("Uvalde", "USA"), birthplace("Uvalde, Texas, USA"))
        assertEquals(Birthplace("London", null), birthplace("London"))
        assertNull(birthplace(" , "))
    }

    @Test
    fun `biography drops the Wikipedia attribution`() {
        assertEquals(
            "An actor.",
            cleanBiography("An actor.\n\nDescription above from the Wikipedia article Foo."),
        )
        assertNull(cleanBiography("From Wikipedia, the free encyclopedia. "))
    }
}
