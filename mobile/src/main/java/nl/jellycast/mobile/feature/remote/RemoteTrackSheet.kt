package nl.jellycast.mobile.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.CheckedSheetRow
import nl.jellycast.mobile.core.designsystem.component.ListSectionLabel
import nl.jellycast.mobile.core.designsystem.component.SheetTitle
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.remote.RemoteSession
import nl.jellycast.mobile.core.remote.RemoteTracks

/**
 * The TV's own tracks, by the server's stream index. Only offered for direct play: there every
 * track is in the stream the TV plays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteTrackSheet(
    session: RemoteSession,
    tracks: RemoteTracks,
    onCommand: (RemoteCommand) -> Unit,
    onDismiss: () -> Unit,
) {
    val subtitle = session.subtitleStreamIndex?.takeIf { it != RemoteCommand.NO_STREAM }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = JellyCastColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetTitle(stringResource(R.string.remote_audio_subtitles))
            if (tracks.audio.isNotEmpty()) {
                ListSectionLabel(stringResource(R.string.remote_audio), Modifier.padding(top = 8.dp))
                tracks.audio.forEach { track ->
                    CheckedSheetRow(
                        title = track.title,
                        selected = track.index == session.audioStreamIndex,
                        onClick = { onCommand(RemoteCommand.SetAudioStream(track.index)) },
                    )
                }
            }
            ListSectionLabel(stringResource(R.string.remote_subtitles), Modifier.padding(top = 8.dp))
            CheckedSheetRow(
                title = stringResource(R.string.remote_off),
                selected = subtitle == null,
                onClick = { onCommand(RemoteCommand.SetSubtitleStream(RemoteCommand.NO_STREAM)) },
            )
            tracks.subtitles.forEach { track ->
                CheckedSheetRow(
                    title = track.title,
                    selected = track.index == subtitle,
                    onClick = { onCommand(RemoteCommand.SetSubtitleStream(track.index)) },
                )
            }
        }
    }
}
