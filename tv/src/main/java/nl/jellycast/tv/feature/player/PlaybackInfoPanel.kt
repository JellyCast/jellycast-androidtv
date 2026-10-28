package nl.jellycast.tv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.core.model.AudioOutcome
import nl.jellycast.core.model.AudioResult
import nl.jellycast.core.model.BitrateLimit
import nl.jellycast.core.model.BitrateLimitCause
import nl.jellycast.core.model.PlaybackOutcome
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.VideoOutcome
import nl.jellycast.core.model.VideoResult
import nl.jellycast.core.playback.DiagnosticsState
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.ui.audioTrackLabel
import nl.jellycast.tv.core.ui.channelLayout
import nl.jellycast.tv.core.ui.sourceRangeLabel

private const val UNKNOWN = "…"
private const val BITS_PER_MEGABIT = 1_000_000

/** What the server decided and what the player actually does. */
@Composable
internal fun PlaybackInfoPanel(
    plan: PlaybackPlan,
    state: DiagnosticsState,
    modifier: Modifier = Modifier,
) {
    val reasons =
        plan.transcodeReasons
            .takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = " — ")
            .orEmpty()
    val video = plan.video
    Column(
        modifier =
            modifier
                .padding(horizontal = Dimens.SafeHorizontal, vertical = Dimens.SafeVertical)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        val style = MaterialTheme.typography.bodySmall
        InfoLine("${plan.playMethod}$reasons", style)
        plan.bitrateLimit?.let { InfoLine(bitrateLimitText(it), style) }
        if (video != null) {
            InfoLine(
                "${video.codec} ${video.width}x${video.height} " +
                    "${video.frameRate}fps ${video.rangeType}" +
                    (video.dolbyVisionProfile?.let { " p$it" } ?: ""),
                style,
            )
        }
        InfoLine(stringResource(R.string.player_info_video, state.videoDecoder ?: UNKNOWN), style)
        InfoLine(
            stringResource(
                R.string.player_info_audio,
                state.audioOutput ?: UNKNOWN,
                state.audioDecoder ?: stringResource(R.string.player_info_no_decoder),
            ),
            style,
        )
        InfoLine(stringResource(R.string.player_info_track, state.audioTrack ?: UNKNOWN), style)
        plan.outcome?.let { OutcomeLines(it, style) }
        if (plan.outcome?.audio?.losesAtmos == true) {
            InfoLine(stringResource(R.string.player_info_atmos_lost), style)
        }
        InfoLine(stringResource(R.string.player_info_dropped, state.droppedFrames), style)
    }
}

@Composable
private fun InfoLine(text: String, style: TextStyle) {
    Text(text, style = style, color = Color.White)
}

@Composable
private fun bitrateLimitText(limit: BitrateLimit): String = stringResource(
    when (limit.cause) {
        BitrateLimitCause.QualitySetting -> R.string.player_info_bitrate_setting
        BitrateLimitCause.SlowNetwork -> R.string.player_info_bitrate_slow_network
    },
    limit.bitsPerSecond / BITS_PER_MEGABIT,
)

/** Per stream: what the file has and how it plays here. */
@Composable
private fun OutcomeLines(
    outcome: PlaybackOutcome,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        outcome.video?.let { InfoLine(videoOutcomeText(it), style) }
        outcome.audio?.let { InfoLine(audioOutcomeText(it), style) }
    }
}

@Composable
private fun videoOutcomeText(video: VideoOutcome): String {
    val source =
        listOfNotNull(
            video.source.codec.uppercase(),
            sourceRangeLabel(video.source),
            video.source.dolbyVisionProfile?.let { "P$it" },
        ).joinToString(" ")
    val result =
        stringResource(
            when (video.result) {
                VideoResult.AsIs -> R.string.player_outcome_as_is
                VideoResult.DolbyVisionBaseLayer -> R.string.player_outcome_dv_base
                VideoResult.ConvertedByServer -> R.string.player_outcome_server
            },
        )
    return stringResource(R.string.player_info_video_outcome, source, result)
}

@Composable
private fun audioOutcomeText(audio: AudioOutcome): String {
    val source =
        listOfNotNull(audioTrackLabel(audio.source), channelLayout(audio.source.channels))
            .joinToString(" ")
    val how =
        when (audio.result) {
            AudioResult.Passthrough -> stringResource(R.string.player_outcome_passthrough)

            AudioResult.DecodedLocally ->
                stringResource(
                    R.string.player_outcome_decoded,
                    channelLayout(audio.pcmChannels).orEmpty(),
                )

            AudioResult.ConvertedByServer -> stringResource(R.string.player_outcome_server)
        }
    val result =
        if (audio.played.index != audio.source.index) {
            val played =
                listOfNotNull(
                    audioTrackLabel(audio.played),
                    channelLayout(audio.played.channels),
                ).joinToString(" ")
            stringResource(R.string.player_outcome_other_track, played, how)
        } else {
            how
        }
    return stringResource(R.string.player_info_audio_outcome, source, result)
}
