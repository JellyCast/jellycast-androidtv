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
import nl.jellycast.core.model.SubtitleTrack
import nl.jellycast.core.playback.SubtitleEntry
import nl.jellycast.core.playback.TrackOption
import nl.jellycast.core.playback.disableSubtitles
import nl.jellycast.core.playback.languageName
import nl.jellycast.core.playback.normalizeLanguage
import nl.jellycast.core.playback.options
import nl.jellycast.core.playback.selectTrack
import nl.jellycast.core.playback.subtitleEntries
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.CheckedSheetRow
import nl.jellycast.mobile.core.designsystem.component.ListSectionLabel
import nl.jellycast.mobile.core.designsystem.component.SheetTitle
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.settings.QualityLevel
import nl.jellycast.mobile.core.settings.QualityPreference

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
) {
    val locale = LocalConfiguration.current.locales[0]
    val player = engine.player
    PlayerSheet(onDismiss) {
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
        ListSectionLabel(stringResource(R.string.player_subtitles), Modifier.padding(start = 4.dp, top = 8.dp))
        SubtitleRows(plan, engine, player, locale, onTranscodeStreams)
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
            plan.subtitleTracks.map { it.toOption(it.index == plan.subtitleStreamIndex) }
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

private fun SubtitleTrack.toOption(selected: Boolean) = TrackOption(
    groupIndex = index,
    label = title,
    language = language,
    codec = serverSubtitleCodec(codec),
    channels = null,
    isForced = isForced,
    isSdh = false,
    isSelected = selected,
)

private fun serverSubtitleCodec(codec: String): String = when {
    codec.contains("pgs", ignoreCase = true) -> "PGS"
    codec.contains("dvd", ignoreCase = true) || codec.contains("vobsub", ignoreCase = true) -> "VobSub"
    else -> codec.uppercase(Locale.ROOT)
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

@Composable
private fun subtitleTitle(entry: SubtitleEntry, position: Int): String {
    val base = entry.language ?: entry.title ?: stringResource(R.string.player_track, position + 1)
    return entry.number?.let { "$base $it" } ?: base
}

@Composable
private fun subtitleDetail(entry: SubtitleEntry): String? = listOfNotNull(
    entry.title.takeIf { entry.language != null },
    stringResource(R.string.player_sdh).takeIf { entry.isSdh },
    stringResource(R.string.player_forced).takeIf { entry.isForced },
    stringResource(R.string.player_image).takeIf { entry.isImage },
).joinToString(" · ").ifEmpty { null }

/** Auto, then each level that really shrinks the file, highest first. Closes on a choice. */
@Composable
fun QualitySheet(quality: QualityState, onChoose: (QualityChoice) -> Unit, onDismiss: () -> Unit) {
    PlayerSheet(onDismiss) {
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
fun levelShortName(level: QualityLevel): String =
    if (level == QualityLevel.Original) stringResource(R.string.player_original) else level.shortName

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
