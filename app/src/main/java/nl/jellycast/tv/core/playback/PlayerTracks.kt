package nl.jellycast.tv.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import nl.jellycast.tv.core.model.PlayMethod
import nl.jellycast.tv.core.model.PlaybackPlan
import nl.jellycast.tv.core.model.StreamChoice

/**
 * With direct play every track is in the file and ExoPlayer would pick its own. Jellyfin indexes
 * count over all streams; ExoPlayer counts per type in file order, so tracks are matched by name.
 */
@OptIn(UnstableApi::class)
fun Player.applyTrackChoice(plan: PlaybackPlan, tracks: Tracks) {
    if (plan.playMethod != PlayMethod.DirectPlay) return
    val builder = trackSelectionParameters.buildUpon().setTrackTypeDisabled(
        C.TRACK_TYPE_AUDIO,
        false,
    )

    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val audioTarget = plan.audioTracks.indexOfFirst { it.index == plan.audioStreamIndex }
    plan.audioTracks.getOrNull(audioTarget)?.let { track ->
        val target = ServerTrack(track.title, track.codec, track.channels, track.language)
        matchTrack(target, audioTarget, audioGroups.map { it.toPlayerTrack() })?.let {
            builder.setOverrideForType(TrackSelectionOverride(audioGroups[it].mediaTrackGroup, 0))
        }
    }

    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val subtitleTarget = plan.subtitleTracks.indexOfFirst { it.index == plan.subtitleStreamIndex }
    val textPosition =
        plan.subtitleTracks.getOrNull(subtitleTarget)?.let { track ->
            val target = ServerTrack(track.title, track.codec, null, track.language)
            matchSubtitle(target, subtitleTarget, textGroups.map { it.toPlayerTrack() })
        }
    if (textPosition != null) {
        builder
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(textGroups[textPosition].mediaTrackGroup, 0))
    } else {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    }
    trackSelectionParameters = builder.build()
}

/**
 * The server's stream indexes of the tracks that play now, so the server (and an iPhone that takes
 * over) sees a choice made in the menu. Only direct play has every track; otherwise `null`.
 */
@OptIn(UnstableApi::class)
fun Tracks.chosenStreams(plan: PlaybackPlan): StreamChoice? {
    if (plan.playMethod != PlayMethod.DirectPlay) return null
    val audioGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val audioPlayers = audioGroups.map { it.toPlayerTrack() }
    val audioPosition = audioGroups.indexOfFirst { it.isSelected }
    val audio =
        plan.audioTracks.withIndex().firstOrNull { (position, track) ->
            val target = ServerTrack(track.title, track.codec, track.channels, track.language)
            matchTrack(target, position, audioPlayers) == audioPosition
        }?.value?.index
    val textGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val textPlayers = textGroups.map { it.toPlayerTrack() }
    val textPosition = textGroups.indexOfFirst { it.isSelected }
    val subtitle =
        if (textPosition < 0) {
            null
        } else {
            plan.subtitleTracks.withIndex().firstOrNull { (position, track) ->
                val target = ServerTrack(track.title, track.codec, null, track.language)
                matchSubtitle(target, position, textPlayers) == textPosition
            }?.value?.index
        }
    return StreamChoice(audio ?: plan.audioStreamIndex, subtitle)
}

/** Media3 selects no video track without reporting an error when no decoder can handle it. */
fun Tracks.videoWithoutDecoder(player: Player): Boolean = containsType(C.TRACK_TYPE_VIDEO) &&
    !isTypeSelected(C.TRACK_TYPE_VIDEO) &&
    C.TRACK_TYPE_VIDEO !in player.trackSelectionParameters.disabledTrackTypes

/**
 * Held until the new display mode is active: the file already opens and buffers, but the video
 * decoder only starts after the mode switch.
 */
fun Player.setVideoHeld(held: Boolean) {
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, held)
            .build()
}

/**
 * With direct play ExoPlayer would start decoding the default track (e.g. TrueHD) right away, so
 * audio and subtitles stay off until [applyTrackChoice]. Returns `true` when the start is deferred.
 */
fun Player.holdTracksUntilChosen(plan: PlaybackPlan): Boolean {
    if (plan.playMethod != PlayMethod.DirectPlay) return false
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    return true
}

@OptIn(UnstableApi::class)
private fun Tracks.Group.toPlayerTrack(): PlayerTrack {
    val format = getTrackFormat(0)
    return PlayerTrack(
        label = format.label,
        mimeType =
            if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) {
                format.codecs
            } else {
                format.sampleMimeType
            },
        channels = format.channelCount.takeIf { it > 0 },
        language = format.language,
    )
}
