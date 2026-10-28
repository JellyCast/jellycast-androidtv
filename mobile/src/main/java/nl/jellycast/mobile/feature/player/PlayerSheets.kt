package nl.jellycast.mobile.feature.player

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.text.DecimalFormat
import java.util.Locale
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.playback.disableSubtitles
import nl.jellycast.core.playback.languageName
import nl.jellycast.core.playback.normalizeLanguage
import nl.jellycast.core.playback.options
import nl.jellycast.core.playback.selectTrack
import nl.jellycast.core.playback.subtitleEntries
import nl.jellycast.core.playback.toTrackOption
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.CheckedSheetRow
import nl.jellycast.mobile.core.designsystem.component.ListSectionLabel
import nl.jellycast.mobile.core.designsystem.component.SheetTitle
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.settings.QualityLevel
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.core.ui.subtitleDetail
import nl.jellycast.mobile.core.ui.subtitleTitle

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = JellyCastColors.Surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
        }
    }
}

/** Audio and subtitles in one sheet that stays open, so both can be set in one go. */
@OptIn(UnstableApi::class)
@Composable
fun TrackSheet(
    plan: PlaybackPlan,
    engine: PlayerEngineState,
    onTranscodeStreams: (StreamChoice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val player = engine.player
    PlayerSheet(onDismiss, modifier) {
        SheetTitle(stringResource(R.string.player_tracks))
        ListSectionLabel(stringResource(R.string.player_audio), Modifier.padding(start = 4.dp))
        if (plan.playMethod == PlayMethod.DirectPlay) {
            engine.tracks.options(C.TRACK_TYPE_AUDIO).forEach { option ->
                CheckedSheetRow(
                    title = audioTitle(option.language, option.label, option.codec, locale),
                    selected = option.isSelected,
                    onClick = { player?.selectTrack(option) },
                    detail = audioDetail(option.codec, option.channels, option.label.isAtmos()),
                )
            }
        } else {
            plan.audioTracks.forEach { track ->
                CheckedSheetRow(
                    title = audioTitle(track.language, track.title, track.codec, locale),
                    selected = track.index == plan.audioStreamIndex,
                    onClick = {
                        onTranscodeStreams(StreamChoice(track.index, plan.subtitleStreamIndex))
                    },
                    detail = audioDetail(track.codec, track.channels, track.isAtmos),
                )
            }
        }
        ListSectionLabel(
            stringResource(R.string.player_subtitles),
            Modifier.padding(start = 4.dp, top = 8.dp),
        )
        SubtitleRows(plan, engine, player, locale, onTranscodeStreams)
        AudioDelaySection()
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SubtitleRows(
    plan: PlaybackPlan,
    engine: PlayerEngineState,
    player: Player?,
    locale: Locale,
    onTranscodeStreams: (StreamChoice) -> Unit,
) {
    val direct = plan.playMethod == PlayMethod.DirectPlay
    val options =
        if (direct) {
            engine.tracks.options(C.TRACK_TYPE_TEXT)
        } else {
            plan.subtitleTracks.map { it.toTrackOption(it.index == plan.subtitleStreamIndex) }
        }
    val entries = subtitleEntries(options, plan.preferredLanguages, locale)
    CheckedSheetRow(
        title = stringResource(R.string.player_off),
        selected = options.none { it.isSelected },
        onClick = {
            if (direct) {
                player?.disableSubtitles()
            } else {
                onTranscodeStreams(StreamChoice(plan.audioStreamIndex, null))
            }
        },
    )
    entries.forEachIndexed { position, entry ->
        CheckedSheetRow(
            title = subtitleTitle(entry, position),
            selected = entry.option.isSelected,
            onClick = {
                if (direct) {
                    player?.selectTrack(entry.option)
                } else {
                    onTranscodeStreams(StreamChoice(plan.audioStreamIndex, entry.option.groupIndex))
                }
            },
            detail = subtitleDetail(entry),
        )
    }
}

private fun String?.isAtmos(): Boolean = this?.contains("atmos", ignoreCase = true) == true

private fun audioTitle(language: String?, label: String?, codec: String?, locale: Locale): String =
    normalizeLanguage(language)?.let { languageName(it, locale) }
        ?: label?.takeIf { it.isNotBlank() }
        ?: codec?.uppercase(Locale.ROOT).orEmpty()

@Composable
private fun audioDetail(codec: String?, channels: Int?, atmos: Boolean): String = listOfNotNull(
    codec?.uppercase(Locale.ROOT),
    channels?.let { stringResource(R.string.player_channels, it) },
    "Atmos".takeIf { atmos },
).joinToString(" · ")

/** Auto, then each level that really shrinks the file, highest first. Closes on a choice. */
@Composable
fun QualitySheet(
    quality: QualityState,
    onChoose: (QualityChoice) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSheet(onDismiss, modifier) {
        SheetTitle(stringResource(R.string.player_quality))
        CheckedSheetRow(
            title =
                if (quality.isAuto) {
                    stringResource(R.string.player_auto_level, levelShortName(quality.level))
                } else {
                    stringResource(R.string.player_auto)
                },
            selected = quality.isAuto,
            onClick = { onChoose(QualityChoice.Auto) },
        )
        quality.offered.forEach { level ->
            CheckedSheetRow(
                title = levelLabel(level, quality.sourceBitrate),
                selected = quality.preference == QualityPreference.Fixed(level),
                onClick = { onChoose(QualityChoice.Level(level)) },
            )
        }
    }
}

@Composable
fun levelShortName(level: QualityLevel): String = if (level ==
    QualityLevel.Original
) {
    stringResource(R.string.player_original)
} else {
    level.shortName
}

@Composable
fun levelLabel(level: QualityLevel, sourceBitrate: Long?): String {
    val maxBitrate = level.maxBitrate
    return when {
        maxBitrate != null ->
            stringResource(R.string.player_level, megabits(maxBitrate, "0.#"), level.shortName)

        sourceBitrate != null ->
            stringResource(R.string.player_original_bitrate, megabits(sourceBitrate, "0.0"))

        else -> stringResource(R.string.player_original)
    }
}

private const val BITS_PER_MEGABIT = 1_000_000.0

private fun megabits(bitsPerSecond: Long, pattern: String): String =
    DecimalFormat(pattern).format(bitsPerSecond / BITS_PER_MEGABIT)
