package nl.jellycast.mobile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.jellycast.core.playback.SubtitleEntry
import nl.jellycast.mobile.R

/** The language, else the file's name, else "Track 3"; a number for look-alikes. */
@Composable
internal fun subtitleTitle(entry: SubtitleEntry, position: Int): String {
    val base = entry.language ?: entry.title ?: stringResource(R.string.player_track, position + 1)
    return entry.number?.let { "$base $it" } ?: base
}

@Composable
internal fun subtitleDetail(entry: SubtitleEntry): String? = listOfNotNull(
    entry.title.takeIf { entry.language != null },
    stringResource(R.string.player_sdh).takeIf { entry.isSdh },
    stringResource(R.string.player_forced).takeIf { entry.isForced },
    stringResource(R.string.player_image).takeIf { entry.isImage },
).joinToString(" · ").ifEmpty { null }
