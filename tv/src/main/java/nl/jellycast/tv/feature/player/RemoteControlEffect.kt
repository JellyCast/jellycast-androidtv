package nl.jellycast.tv.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.exoplayer.ExoPlayer
import nl.jellycast.core.data.repository.RemoteControlRepository
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.playback.PlaybackReporter
import nl.jellycast.core.playback.applyTrackChoice
import org.koin.compose.koinInject

/**
 * Carries out what the iOS app asks through the server, for as long as this player plays. Each
 * command is reported back straight away, so the phone shows the result without waiting for the
 * next progress report.
 */
@Composable
internal fun RemoteControlEffect(
    plan: PlaybackPlan,
    state: PlayerSessionState,
    reporter: PlaybackReporter,
    onStop: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayItem: (itemId: String, startPositionMs: Long, streams: StreamChoice) -> Unit,
    repository: RemoteControlRepository = koinInject(),
) {
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnPlayNext by rememberUpdatedState(onPlayNext)
    val currentOnPlayItem by rememberUpdatedState(onPlayItem)
    val player = state.player
    LaunchedEffect(player, plan) {
        val activePlayer = player ?: return@LaunchedEffect
        repository.getCommandStream().collect { command ->
            // Before the start the player waits for the display mode and HDMI; a remote play
            // must not cut that short.
            val started = state.startedPlayer === activePlayer
            when (command) {
                RemoteCommand.Stop -> currentOnStop()

                RemoteCommand.NextEpisode -> currentOnPlayNext()

                is RemoteCommand.Play ->
                    currentOnPlayItem(command.itemId, command.startPositionMs, command.streams)

                RemoteCommand.Pause -> activePlayer.pause()

                RemoteCommand.Unpause -> if (started) activePlayer.play()

                RemoteCommand.PlayPause ->
                    if (activePlayer.isPlaying) {
                        activePlayer.pause()
                    } else if (started) {
                        activePlayer.play()
                    }

                is RemoteCommand.Seek -> activePlayer.seekTo(command.positionMs)

                is RemoteCommand.SetAudioStream ->
                    activePlayer.chooseStreams(
                        plan,
                        state,
                        state.streamsFor(plan).copy(audioStreamIndex = command.index),
                    )

                is RemoteCommand.SetSubtitleStream ->
                    activePlayer.chooseStreams(
                        plan,
                        state,
                        state.streamsFor(plan).copy(subtitleStreamIndex = command.index),
                    )
            }
            reporter.progress(
                plan,
                activePlayer.currentPosition,
                isPaused = !activePlayer.playWhenReady,
                streams = state.streamsFor(plan),
            )
        }
    }
}

/** Only direct play has every track in the stream; otherwise the server would have to restart. */
private fun ExoPlayer.chooseStreams(
    plan: PlaybackPlan,
    state: PlayerSessionState,
    streams: StreamChoice,
) {
    if (plan.playMethod != PlayMethod.DirectPlay) return
    applyTrackChoice(
        plan.copy(
            audioStreamIndex = streams.audioStreamIndex,
            subtitleStreamIndex = streams.subtitleStreamIndex,
        ),
        currentTracks,
    )
    state.chooseStreams(plan, streams)
}
