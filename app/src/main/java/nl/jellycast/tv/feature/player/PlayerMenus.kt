package nl.jellycast.tv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.neonFocus
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.playback.PlaybackSettings
import nl.jellycast.tv.core.playback.PlaybackSettingsStore
import nl.jellycast.tv.core.playback.SubtitleEntry
import nl.jellycast.tv.core.playback.SubtitleSize
import nl.jellycast.tv.core.playback.TrackOption
import nl.jellycast.tv.core.playback.disableSubtitles
import nl.jellycast.tv.core.playback.languageName
import nl.jellycast.tv.core.playback.options
import nl.jellycast.tv.core.playback.selectTrack
import nl.jellycast.tv.core.playback.subtitleEntries

private data class MenuOption(
    val key: String,
    val label: String,
    val detail: String? = null,
    val value: String? = null,
    val isSelected: Boolean = false,
    val onClick: () -> Unit,
)

/** The audio or subtitle menu for [panel]; the info panel is drawn elsewhere. */
@Composable
internal fun PlayerPanelMenu(
    panel: PlayerPanel,
    player: Player,
    tracks: Tracks,
    settings: PlaybackSettings,
    settingsStore: PlaybackSettingsStore,
    preferredLanguages: List<String>,
    modifier: Modifier = Modifier,
) {
    when (panel) {
        PlayerPanel.Audio ->
            AudioMenu(
                tracks = tracks.options(C.TRACK_TYPE_AUDIO),
                onSelect = { player.selectTrack(it) },
                modifier = modifier,
            )

        PlayerPanel.Subtitles ->
            SubtitleMenu(
                tracks = tracks.options(C.TRACK_TYPE_TEXT),
                preferredLanguages = preferredLanguages,
                size = settings.subtitleSize,
                background = settings.subtitleBackground,
                onSelect = { player.selectTrack(it) },
                onDisable = { player.disableSubtitles() },
                onSizeChange = settingsStore::setSubtitleSize,
                onBackgroundChange = settingsStore::setSubtitleBackground,
                modifier = modifier,
            )

        PlayerPanel.Info, PlayerPanel.None -> Unit
    }
}

@Composable
fun AudioMenu(
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        tracks.mapIndexed { index, track ->
            MenuOption(
                key = "audio-${track.groupIndex}",
                label = trackLabel(track, index),
                detail = listOfNotNull(
                    track.codec,
                    track.channels?.let {
                        channelLabel(it)
                    },
                ).joinToString(" · "),
                isSelected = track.isSelected,
                onClick = { onSelect(track) },
            )
        }
    MenuPanel(modifier) {
        MenuHeader(stringResource(R.string.player_audio))
        MenuOptions(options)
    }
}

@Composable
fun SubtitleMenu(
    tracks: List<TrackOption>,
    preferredLanguages: List<String>,
    size: SubtitleSize,
    background: Boolean,
    onSelect: (TrackOption) -> Unit,
    onDisable: () -> Unit,
    onSizeChange: (SubtitleSize) -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val off =
        MenuOption(
            key = "off",
            label = stringResource(R.string.player_off),
            isSelected = tracks.none { it.isSelected },
            onClick = onDisable,
        )
    val locale = LocalConfiguration.current.locales[0]
    val entries =
        remember(tracks, preferredLanguages, locale) {
            subtitleEntries(tracks, preferredLanguages, locale)
        }
    val trackOptions =
        entries.mapIndexed { index, entry ->
            MenuOption(
                key = "text-${entry.option.groupIndex}",
                label = subtitleLabel(entry, index),
                detail = subtitleDetail(entry),
                isSelected = entry.option.isSelected,
                onClick = { onSelect(entry.option) },
            )
        }
    val sizeLabel =
        stringResource(
            when (size) {
                SubtitleSize.Small -> R.string.player_size_small
                SubtitleSize.Normal -> R.string.player_size_normal
                SubtitleSize.Large -> R.string.player_size_large
            },
        )
    val styleOptions =
        listOf(
            MenuOption(
                key = "size",
                label = stringResource(R.string.player_size),
                value = sizeLabel,
                onClick = { onSizeChange(size.next()) },
            ),
            MenuOption(
                key = "background",
                label = stringResource(R.string.player_background),
                value = stringResource(if (background) R.string.player_on else R.string.player_off),
                onClick = { onBackgroundChange(!background) },
            ),
        )
    MenuPanel(modifier) {
        MenuHeader(stringResource(R.string.player_subtitles))
        MenuOptions(listOf(off) + trackOptions)
        Box(
            Modifier
                .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(JellyCastColors.Text.copy(alpha = 0.12f)),
        )
        MenuHeader(stringResource(R.string.player_style))
        MenuOptions(styleOptions, takeFocus = false)
    }
}

@Composable
private fun MenuPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier =
            modifier
                .width(320.dp)
                .fillMaxHeight()
                .background(JellyCastColors.Background.copy(alpha = 0.94f))
                // Focus stays in the panel; Back closes it.
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(start = 36.dp, end = 40.dp, top = 48.dp, bottom = 27.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 0.14.em,
            fontWeight = FontWeight.Normal,
        ),
        color = JellyCastColors.Text.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 14.dp, bottom = 8.dp),
    )
}

/** With [takeFocus], the selected option (or else the first) gets focus on open. */
@Composable
private fun MenuOptions(options: List<MenuOption>, takeFocus: Boolean = true) {
    val selectedKey = options.firstOrNull { it.isSelected }?.key ?: options.firstOrNull()?.key
    options.forEach { option ->
        val requester = remember { FocusRequester() }
        if (takeFocus && option.key == selectedKey) {
            LaunchedEffect(Unit) { requester.requestFocus() }
        }
        MenuRow(option, Modifier.focusRequester(requester))
    }
}

@Composable
private fun MenuRow(option: MenuOption, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .neonFocus(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = option.onClick,
                    ).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleLarge,
                color = JellyCastColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (option.value != null) {
                Text(
                    text = option.value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Light,
                    ),
                    color = JellyCastColors.Text.copy(alpha = 0.6f),
                )
            }
            if (option.isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = JellyCastColors.Accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (!option.detail.isNullOrEmpty()) {
            Text(
                text = option.detail,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                color = JellyCastColors.Text.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
            )
        }
    }
}

/** The name from the file, else the language, else "Track n". */
@Composable
private fun trackLabel(track: TrackOption, index: Int): String =
    track.label?.takeIf { it.isNotBlank() }
        ?: track.language?.let { languageName(it) }
        ?: stringResource(R.string.player_track, index + 1)

@Composable
private fun languageName(code: String): String =
    languageName(code, LocalConfiguration.current.locales[0])

/** The language, else the file's name, else "Track n"; numbered when two would look the same. */
@Composable
private fun subtitleLabel(entry: SubtitleEntry, index: Int): String {
    val name =
        entry.language
            ?: entry.title
            ?: stringResource(R.string.player_track, index + 1)
    return entry.number?.let { "$name $it" } ?: name
}

/** What tells tracks of one language apart; the format only when it changes what the style does. */
@Composable
private fun subtitleDetail(entry: SubtitleEntry): String = listOfNotNull(
    entry.title.takeIf { entry.language != null },
    stringResource(R.string.player_sdh).takeIf { entry.isSdh },
    stringResource(R.string.player_forced).takeIf { entry.isForced },
    stringResource(R.string.player_subtitle_image).takeIf { entry.isImage },
).joinToString(" · ")

private fun SubtitleSize.next(): SubtitleSize =
    SubtitleSize.entries[(ordinal + 1) % SubtitleSize.entries.size]

@Composable
private fun channelLabel(channels: Int): String = when (channels) {
    2 -> stringResource(R.string.player_channels_stereo)
    6 -> stringResource(R.string.player_channels_51)
    8 -> stringResource(R.string.player_channels_71)
    else -> stringResource(R.string.player_channels_other, channels)
}
