package nl.jellycast.tv.core.data.mapper

import nl.jellycast.tv.core.model.AudioTrack
import nl.jellycast.tv.core.model.MediaItemSummary
import nl.jellycast.tv.core.model.MediaSegment
import nl.jellycast.tv.core.model.PlayMethod
import nl.jellycast.tv.core.model.SegmentType
import nl.jellycast.tv.core.model.SubtitleTrack
import nl.jellycast.tv.core.model.VideoInfo
import nl.jellycast.tv.core.model.ticksToMs
import org.jellyfin.sdk.model.api.AudioSpatialFormat
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

fun BaseItemDto.toMediaItemSummary(): MediaItemSummary {
    val title =
        if (type == BaseItemKind.EPISODE) {
            "${seriesName.orEmpty()} ${parentIndexNumber ?: 0}x${indexNumber ?: 0}"
        } else {
            name.orEmpty()
        }
    return MediaItemSummary(
        id = id.toString(),
        title = title,
        subtitle = productionYear?.toString().orEmpty(),
        resumePositionMs = userData?.playbackPositionTicks?.ticksToMs() ?: 0L,
    )
}

fun MediaSourceInfo.playMethod(): PlayMethod = when {
    supportsDirectPlay -> PlayMethod.DirectPlay
    supportsDirectStream && transcodingUrl != null -> PlayMethod.DirectStream
    else -> PlayMethod.Transcode
}

/** Parses `TranscodeReasons=A,B` from the transcoding URL. */
fun transcodeReasons(transcodingUrl: String?): List<String> {
    val query = transcodingUrl?.substringAfter('?', "") ?: return emptyList()
    return query
        .split('&')
        .firstOrNull { it.startsWith("TranscodeReasons=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.replace("%2C", ",", ignoreCase = true)
        ?.split(',')
        ?.filter { it.isNotBlank() }
        .orEmpty()
}

fun MediaSourceInfo.videoInfo(): VideoInfo? {
    val video = mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO } ?: return null
    return VideoInfo(
        codec = video.codec.orEmpty(),
        width = video.width ?: 0,
        height = video.height ?: 0,
        frameRate = video.realFrameRate ?: video.averageFrameRate,
        rangeType = video.videoRangeType.serialName,
        dolbyVisionProfile = video.dvProfile,
        interlaced = video.isInterlaced,
    )
}

fun MediaSourceInfo.audioTracks(): List<AudioTrack> =
    mediaStreams.orEmpty().filter { it.type == MediaStreamType.AUDIO }.map { it.toAudioTrack() }

/** Only the tracks in the file: the player never loads the server's external subtitle files. */
fun MediaSourceInfo.subtitleTracks(): List<SubtitleTrack> = mediaStreams.orEmpty().filter {
    it.type == MediaStreamType.SUBTITLE && !it.isExternal
}.map { it.toSubtitleTrack() }

private fun MediaStream.toAudioTrack() = AudioTrack(
    index = index,
    language = language,
    codec = codec.orEmpty(),
    channels = channels,
    title = displayTitle ?: title,
    isDefault = isDefault,
    isAtmos = audioSpatialFormat == AudioSpatialFormat.DOLBY_ATMOS,
)

private fun MediaStream.toSubtitleTrack() = SubtitleTrack(
    index = index,
    language = language,
    codec = codec.orEmpty(),
    title = title,
    isDefault = isDefault,
    isForced = isForced,
)

fun MediaSegmentDto.toMediaSegment(): MediaSegment? {
    val type =
        when (type) {
            MediaSegmentType.INTRO -> SegmentType.Intro
            MediaSegmentType.RECAP -> SegmentType.Recap
            MediaSegmentType.COMMERCIAL -> SegmentType.Commercial
            MediaSegmentType.PREVIEW -> SegmentType.Preview
            MediaSegmentType.OUTRO -> SegmentType.Outro
            MediaSegmentType.UNKNOWN -> return null
        }
    if (endTicks <= startTicks) return null
    return MediaSegment(type, startTicks.ticksToMs(), endTicks.ticksToMs())
}
