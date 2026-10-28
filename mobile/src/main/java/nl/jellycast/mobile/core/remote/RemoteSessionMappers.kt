package nl.jellycast.mobile.core.remote

import java.time.LocalDateTime
import java.time.ZoneOffset
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.ticksToMs
import nl.jellycast.core.network.ImageKind
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.PlayMethod as SdkPlayMethod

/**
 * The user's other devices: what they play, most recently reported first, and which take
 * commands. `controllableByUserId` would drop sessions that can't be controlled, so the
 * filtering on user and device happens here.
 */
fun List<SessionInfoDto>.toRemoteSnapshot(
    userId: String,
    ownDeviceId: String,
    images: ImageUrlBuilder,
): RemoteSnapshot {
    val own =
        filter { it.userId.toString().withoutDashes() == userId.withoutDashes() }
            .filter { it.deviceId != ownDeviceId }
    return RemoteSnapshot(
        playing =
            own.mapNotNull { it.toRemoteSession(images) }
                .sortedByDescending { it.checkedInAtMs ?: Long.MIN_VALUE },
        devices = own.mapNotNull { it.toRemoteDevice() },
    )
}

fun SessionInfoDto.toRemoteSession(images: ImageUrlBuilder): RemoteSession? {
    val sessionId = id ?: return null
    val item = nowPlayingItem?.toRemoteItem(images) ?: return null
    return RemoteSession(
        id = sessionId,
        deviceId = deviceId ?: sessionId,
        deviceName = deviceName.orEmpty(),
        item = item,
        positionMs = playState?.positionTicks?.ticksToMs() ?: 0,
        isPaused = playState?.isPaused ?: false,
        playMethod = playState?.playMethod?.toPlayMethod(),
        checkedInAtMs = lastPlaybackCheckIn.toEpochMs(),
        canControl = supportsRemoteControl,
        audioStreamIndex = playState?.audioStreamIndex,
        subtitleStreamIndex = playState?.subtitleStreamIndex,
    )
}

fun SessionInfoDto.toRemoteDevice(): RemoteDevice? {
    val sessionId = id ?: return null
    if (!supportsRemoteControl) return null
    val device = deviceId ?: sessionId
    return RemoteDevice(
        id = sessionId,
        deviceId = device,
        name = deviceName.orEmpty(),
        isPlaying = nowPlayingItem != null,
        lastActivityMs = lastActivityDate.toEpochMs(),
        address = remoteEndPoint?.removePrefix(IPV4_MAPPED_PREFIX)?.takeIf { it.isNotBlank() },
        isAndroidTv = !device.startsWith(WEBOS_DEVICE_PREFIX),
    )
}

private fun BaseItemDto.toRemoteItem(images: ImageUrlBuilder): RemoteItem {
    val itemId = id.toString().withoutDashes()
    val isEpisode = type == BaseItemKind.EPISODE
    val backdrop = backdropImageTags?.firstOrNull()
    val (kind, tag) =
        if (!isEpisode && backdrop != null) {
            ImageKind.Backdrop to backdrop
        } else {
            ImageKind.Primary to imageTags?.get(ImageType.PRIMARY)
        }
    return RemoteItem(
        id = itemId,
        name = name.orEmpty(),
        seriesName = seriesName.takeIf { isEpisode },
        seasonNumber = parentIndexNumber.takeIf { isEpisode },
        episodeNumber = indexNumber.takeIf { isEpisode },
        runtimeMs = runTimeTicks?.ticksToMs(),
        imageUrl = tag?.let { images.build(itemId, kind, it, HEADER_IMAGE_WIDTH) },
        thumbUrl = tag?.let { images.build(itemId, kind, it, THUMB_IMAGE_WIDTH) },
    )
}

/** Audio and subtitles of the item's first source, by the server's stream index. */
fun BaseItemDto.toRemoteTracks(): RemoteTracks {
    val streams = mediaSources?.firstOrNull()?.mediaStreams ?: mediaStreams.orEmpty()
    return RemoteTracks(
        audio = streams.filter { it.type == MediaStreamType.AUDIO }.map { it.toRemoteTrack() },
        subtitles =
            streams.filter { it.type == MediaStreamType.SUBTITLE }.map { it.toRemoteTrack() },
    )
}

private fun MediaStream.toRemoteTrack() = RemoteTrack(
    index = index,
    title = displayTitle ?: title ?: language ?: "#$index",
)

private fun SdkPlayMethod.toPlayMethod(): PlayMethod = when (this) {
    SdkPlayMethod.DIRECT_PLAY -> PlayMethod.DirectPlay
    SdkPlayMethod.DIRECT_STREAM -> PlayMethod.DirectStream
    SdkPlayMethod.TRANSCODE -> PlayMethod.Transcode
}

// The SDK reads the server's UTC timestamps as local date-times without a zone.
private fun LocalDateTime.toEpochMs(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

private fun String.withoutDashes() = replace("-", "").lowercase()

/** The webOS app makes its device ids like this; the Android TV app uses the SDK's. */
private const val WEBOS_DEVICE_PREFIX = "webos-"
private const val IPV4_MAPPED_PREFIX = "::ffff:"
private const val HEADER_IMAGE_WIDTH = 1280
private const val THUMB_IMAGE_WIDTH = 160
