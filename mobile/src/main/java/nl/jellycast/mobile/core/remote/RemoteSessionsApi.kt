package nl.jellycast.mobile.core.remote

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.core.model.msToTicks
import nl.jellycast.core.network.ImageUrlBuilder
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.GeneralCommand
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.PlayCommand
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.serializer.toUUID

/** The Jellyfin Sessions API: what the user's other devices play, and commands to them. */
interface RemoteSessionsApi {
    suspend fun getSnapshot(): Result<RemoteSnapshot>

    suspend fun send(command: RemoteCommand, sessionId: String): Result<Unit>

    suspend fun getTracks(itemId: String): Result<RemoteTracks>
}

class JellyfinRemoteSessionsApi(
    private val api: ApiClient,
    private val store: SessionStore,
    private val images: ImageUrlBuilder,
    private val dispatchers: AppDispatchers,
) : RemoteSessionsApi {
    override suspend fun getSnapshot(): Result<RemoteSnapshot> = request {
        val userId = userId() ?: return@request RemoteSnapshot(emptyList(), emptyList())
        api.sessionApi
            .getSessions(activeWithinSeconds = SESSION_ACTIVITY_WINDOW_SECONDS)
            .content
            .toRemoteSnapshot(userId.toString(), api.deviceInfo.id, images)
    }

    override suspend fun send(command: RemoteCommand, sessionId: String): Result<Unit> =
        request<Unit> {
            val sessions = api.sessionApi
            when (command) {
                RemoteCommand.Pause -> sessions.sendPlaystateCommand(
                    sessionId,
                    PlaystateCommand.PAUSE,
                )

                RemoteCommand.Unpause ->
                    sessions.sendPlaystateCommand(sessionId, PlaystateCommand.UNPAUSE)

                RemoteCommand.PlayPause ->
                    sessions.sendPlaystateCommand(sessionId, PlaystateCommand.PLAY_PAUSE)

                RemoteCommand.Stop -> sessions.sendPlaystateCommand(
                    sessionId,
                    PlaystateCommand.STOP,
                )

                RemoteCommand.NextEpisode ->
                    sessions.sendPlaystateCommand(sessionId, PlaystateCommand.NEXT_TRACK)

                is RemoteCommand.Seek ->
                    sessions.sendPlaystateCommand(
                        sessionId,
                        PlaystateCommand.SEEK,
                        seekPositionTicks = command.positionMs.msToTicks(),
                    )

                is RemoteCommand.Play ->
                    sessions.play(
                        sessionId,
                        PlayCommand.PLAY_NOW,
                        itemIds = listOf(command.itemId.toUUID()),
                        startPositionTicks = command.startPositionMs.msToTicks(),
                        audioStreamIndex = command.streams.audioStreamIndex,
                        subtitleStreamIndex = command.streams.subtitleStreamIndex,
                    )

                is RemoteCommand.SetAudioStream ->
                    sendIndex(sessionId, GeneralCommandType.SET_AUDIO_STREAM_INDEX, command.index)

                is RemoteCommand.SetSubtitleStream ->
                    sendIndex(
                        sessionId,
                        GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
                        command.index,
                    )
            }
        }

    override suspend fun getTracks(itemId: String): Result<RemoteTracks> = request {
        api.userLibraryApi.getItem(itemId.toUUID(), userId()).content.toRemoteTracks()
    }

    private suspend fun sendIndex(sessionId: String, name: GeneralCommandType, index: Int) {
        val userId = userId() ?: throw IOException("Not signed in")
        api.sessionApi.sendFullGeneralCommand(
            sessionId,
            GeneralCommand(name, userId, mapOf(INDEX_ARGUMENT to index.toString())),
        )
    }

    private suspend fun userId(): UUID? = store.session.first().userId?.toUUID()

    private suspend fun <T> request(block: suspend () -> T): Result<T> =
        withContext(dispatchers.io) {
            try {
                Result.success(block())
            } catch (e: ApiClientException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }

    private companion object {
        const val SESSION_ACTIVITY_WINDOW_SECONDS = 300
        const val INDEX_ARGUMENT = "Index"
    }
}
