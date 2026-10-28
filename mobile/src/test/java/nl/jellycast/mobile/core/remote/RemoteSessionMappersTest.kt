package nl.jellycast.mobile.core.remote

import java.time.LocalDateTime
import java.util.UUID
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.network.ImageKind
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod as SdkPlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlayerStateInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSessionMappersTest {
    private val images = ImageUrlBuilder { id, kind, tag, width -> "$id/$kind/$tag/$width" }

    @Test
    fun `only the user's own other devices count`() {
        val snapshot =
            listOf(
                session("a", user = USER, deviceId = "tv"),
                session("b", user = OTHER_USER, deviceId = "tv2"),
                session("c", user = USER, deviceId = OWN_DEVICE),
            ).toRemoteSnapshot(USER.toString().replace("-", ""), OWN_DEVICE, images)

        assertEquals(listOf("a"), snapshot.devices.map { it.id })
        assertEquals(listOf("a"), snapshot.playing.map { it.id })
    }

    @Test
    fun `what plays is sorted with the most recent report first`() {
        val snapshot =
            listOf(
                session("old", checkIn = at(10, 0)),
                session("new", checkIn = at(10, 5)),
            ).toRemoteSnapshot(USER.toString(), OWN_DEVICE, images)

        assertEquals(listOf("new", "old"), snapshot.playing.map { it.id })
    }

    @Test
    fun `a session maps position, pause, tracks and play method`() {
        val mapped = requireNotNull(session("a").toRemoteSession(images))

        assertEquals(90_000L, mapped.positionMs)
        assertTrue(mapped.isPaused)
        assertEquals(PlayMethod.DirectPlay, mapped.playMethod)
        assertEquals(2, mapped.audioStreamIndex)
        assertEquals(-1, mapped.subtitleStreamIndex)
        assertEquals(at(10, 0).toEpochMs(), mapped.checkedInAtMs)
        assertEquals(3_600_000L, mapped.item.runtimeMs)
    }

    @Test
    fun `an episode shows its own still and its series`() {
        val item = requireNotNull(session("a", item = episode()).toRemoteSession(images)).item

        assertEquals("The Pacific", item.seriesName)
        assertEquals(1, item.seasonNumber)
        assertEquals(3, item.episodeNumber)
        assertEquals("${ITEM_ID.hex()}/${ImageKind.Primary}/still/1280", item.imageUrl)
    }

    @Test
    fun `a movie shows its backdrop, or its poster without one`() {
        val withBackdrop = requireNotNull(session("a", item = movie()).toRemoteSession(images)).item
        val withoutBackdrop =
            requireNotNull(session("a", item = movie(backdrop = null)).toRemoteSession(images)).item

        assertEquals("${ITEM_ID.hex()}/${ImageKind.Backdrop}/backdrop/1280", withBackdrop.imageUrl)
        assertEquals("${ITEM_ID.hex()}/${ImageKind.Primary}/poster/160", withoutBackdrop.thumbUrl)
        assertNull(withBackdrop.seriesName)
    }

    @Test
    fun `a session without an item plays nothing but is a device`() {
        val dto = session("a", item = null)

        assertNull(dto.toRemoteSession(images))
        assertFalse(requireNotNull(dto.toRemoteDevice()).isPlaying)
    }

    @Test
    fun `a session that takes no commands is no device`() {
        assertNull(session("a", controllable = false).toRemoteDevice())
    }

    @Test
    fun `a webOS TV can't be woken and its address loses the IPv4 mapping`() {
        val device =
            requireNotNull(
                session("a", deviceId = "webos-123", endPoint = "::ffff:10.0.0.5").toRemoteDevice(),
            )

        assertFalse(device.isAndroidTv)
        assertEquals("10.0.0.5", device.address)
    }

    @Test
    fun `tracks are the item's audio and subtitles by server index`() {
        val item =
            movie().copy(
                mediaStreams =
                    listOf(
                        stream(0, MediaStreamType.VIDEO, "4K HEVC"),
                        stream(1, MediaStreamType.AUDIO, "English - EAC3 5.1"),
                        stream(2, MediaStreamType.SUBTITLE, "Dutch - SRT"),
                    ),
            )

        val tracks = item.toRemoteTracks()

        assertEquals(listOf(RemoteTrack(1, "English - EAC3 5.1")), tracks.audio)
        assertEquals(listOf(RemoteTrack(2, "Dutch - SRT")), tracks.subtitles)
    }

    private fun stream(index: Int, type: MediaStreamType, title: String) = MediaStream(
        type = type,
        index = index,
        displayTitle = title,
        isInterlaced = false,
        isDefault = false,
        isForced = false,
        isHearingImpaired = false,
        isExternal = false,
        isTextSubtitleStream = type == MediaStreamType.SUBTITLE,
        supportsExternalStream = false,
    )

    private fun session(
        id: String,
        user: UUID = USER,
        deviceId: String = "device-$id",
        checkIn: LocalDateTime = at(10, 0),
        item: BaseItemDto? = movie(),
        controllable: Boolean = true,
        endPoint: String? = "192.168.1.20",
    ) = SessionInfoDto(
        id = id,
        userId = user,
        deviceId = deviceId,
        deviceName = "chromecast",
        playableMediaTypes = emptyList(),
        lastActivityDate = at(10, 1),
        lastPlaybackCheckIn = checkIn,
        isActive = true,
        supportsMediaControl = controllable,
        supportsRemoteControl = controllable,
        hasCustomDeviceName = false,
        supportedCommands = emptyList(),
        remoteEndPoint = endPoint,
        nowPlayingItem = item,
        playState =
            PlayerStateInfo(
                positionTicks = 900_000_000,
                canSeek = true,
                isPaused = true,
                isMuted = false,
                audioStreamIndex = 2,
                subtitleStreamIndex = -1,
                playMethod = SdkPlayMethod.DIRECT_PLAY,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
    )

    private fun movie(backdrop: String? = "backdrop") = BaseItemDto(
        id = ITEM_ID,
        type = BaseItemKind.MOVIE,
        name = "Big Buck Bunny",
        runTimeTicks = 36_000_000_000,
        imageTags = mapOf(ImageType.PRIMARY to "poster"),
        backdropImageTags = listOfNotNull(backdrop),
    )

    private fun episode() = BaseItemDto(
        id = ITEM_ID,
        type = BaseItemKind.EPISODE,
        name = "Melbourne",
        seriesName = "The Pacific",
        parentIndexNumber = 1,
        indexNumber = 3,
        imageTags = mapOf(ImageType.PRIMARY to "still"),
        backdropImageTags = listOf("series-backdrop"),
    )

    private fun at(hour: Int, minute: Int) = LocalDateTime.of(2026, 10, 28, hour, minute)

    private fun LocalDateTime.toEpochMs() = toInstant(java.time.ZoneOffset.UTC).toEpochMilli()

    private fun UUID.hex() = toString().replace("-", "")

    private companion object {
        val USER: UUID = UUID.fromString("b8fe8713-3252-4a4c-93d8-7c4bfbac2a5f")
        val OTHER_USER: UUID = UUID.fromString("3d9e786a-ffd9-4e43-b555-bd4351e1830b")
        val ITEM_ID: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")
        const val OWN_DEVICE = "phone"
    }
}
