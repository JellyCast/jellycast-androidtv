package nl.jellycast.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

data class TrackOption(
    /** Only valid for the same [Tracks]. */
    val groupIndex: Int,
    val label: String?,
    val language: String?,
    /** E.g. "EAC3", "SRT", "PGS". */
    val codec: String?,
    val channels: Int?,
    val isForced: Boolean,
    val isSdh: Boolean,
    val isSelected: Boolean,
)

/** [type] is `C.TRACK_TYPE_AUDIO` or `C.TRACK_TYPE_TEXT`; only tracks the player supports. */
@OptIn(UnstableApi::class)
fun Tracks.options(type: Int): List<TrackOption> = groups.mapIndexedNotNull { index, group ->
    if (group.type != type || !group.isTrackSupported(0)) return@mapIndexedNotNull null
    val format = group.getTrackFormat(0)
    TrackOption(
        groupIndex = index,
        label = format.label,
        language = format.language?.takeUnless { it == C.LANGUAGE_UNDETERMINED },
        codec = codecName(format.sampleMimeType, format.codecs),
        channels = format.channelCount.takeIf { it > 0 },
        isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
        isSdh = format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0,
        isSelected = group.isSelected,
    )
}

/** Also enables subtitles when [option] is a subtitle track. */
fun Player.selectTrack(option: TrackOption) {
    val group = currentTracks.groups.getOrNull(option.groupIndex) ?: return
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
}

fun Player.disableSubtitles() {
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
}

/**
 * Media3 converts MKV/MP4 subtitles to `application/x-media3-cues` while extracting; the original
 * type is then in [codecs].
 */
@OptIn(UnstableApi::class)
internal fun codecName(sampleMimeType: String?, codecs: String?): String? {
    val mimeType = if (sampleMimeType ==
        MimeTypes.APPLICATION_MEDIA3_CUES
    ) {
        codecs
    } else {
        sampleMimeType
    }
    return knownCodecName(mimeType)
        // An unknown MIME type means nothing to the viewer; a codec string like "ec-3" does.
        ?: codecs?.takeUnless { '/' in it }?.uppercase()
}

private fun knownCodecName(mimeType: String?): String? = when (mimeType) {
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "EAC3"
    MimeTypes.AUDIO_AC3 -> "AC3"
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.APPLICATION_SUBRIP -> "SRT"
    MimeTypes.TEXT_SSA -> "ASS"
    MimeTypes.APPLICATION_PGS -> "PGS"
    MimeTypes.APPLICATION_VOBSUB -> "VobSub"
    MimeTypes.TEXT_VTT -> "VTT"
    MimeTypes.APPLICATION_TX3G -> "mov_text"
    else -> null
}
