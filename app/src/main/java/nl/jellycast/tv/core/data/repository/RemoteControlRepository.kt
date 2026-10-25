package nl.jellycast.tv.core.data.repository

import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.jellycast.tv.core.common.AppDispatchers
import nl.jellycast.tv.core.data.mapper.parseRemoteCommand
import nl.jellycast.tv.core.model.RemoteCommand
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber

interface RemoteControlRepository {
    /**
     * Tells the server this device can be controlled and streams the commands for it. The
     * connection is open only while the flow is collected.
     */
    fun getCommandStream(): Flow<RemoteCommand>

    /**
     * Tells the server again that this app is open: the WebSocket keep-alive doesn't count as
     * activity, and the iOS app only lists devices active in the last minutes.
     */
    suspend fun reportPresence()
}

/**
 * One WebSocket for the whole app, shared by everything that listens and closed shortly after
 * the last listener stops. Not the SDK's socket: that one didn't reconnect after the app had been
 * in the background, which left the TV unreachable until a restart.
 */
class DefaultRemoteControlRepository(
    private val api: ApiClient,
    private val httpClient: OkHttpClient,
    private val dispatchers: AppDispatchers,
    appScope: CoroutineScope,
) : RemoteControlRepository {
    private val commands: Flow<RemoteCommand> =
        socketCommands()
            .retryWhen { cause, attempt ->
                Timber.w(cause, "Remote control socket closed")
                delay(ReconnectDelays.getOrElse(attempt.toInt()) { ReconnectDelays.last() })
                true
            }.shareIn(appScope, SharingStarted.WhileSubscribed(StopAfter.inWholeMilliseconds))

    override fun getCommandStream(): Flow<RemoteCommand> = commands

    private fun socketCommands(): Flow<RemoteCommand> = callbackFlow {
        reportPresence()
        val request = socketRequest() ?: throw IllegalStateException("Not signed in")
        val socket =
            httpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        parseRemoteCommand(text)?.let { trySend(it) }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        close(SocketClosed("closed with $code"))
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        close(t)
                    }
                },
            )
        val keepAlive =
            launch {
                while (isActive) {
                    delay(KeepAliveInterval)
                    socket.send(KEEP_ALIVE)
                }
            }
        awaitClose {
            keepAlive.cancel()
            socket.close(NORMAL_CLOSURE, null)
        }
    }

    /** `ws(s)://server/socket?api_key=…&deviceId=…`; never logged, the token is in it. */
    private fun socketRequest(): Request? {
        val baseUrl = api.baseUrl ?: return null
        val token = api.accessToken ?: return null
        val socketUrl =
            baseUrl.trimEnd('/').replaceFirst("http", "ws") +
                "/socket?api_key=" + URLEncoder.encode(token, "UTF-8") +
                "&deviceId=" + URLEncoder.encode(api.deviceInfo.id, "UTF-8")
        return Request.Builder().url(socketUrl).build()
    }

    override suspend fun reportPresence(): Unit = withContext(dispatchers.io) {
        try {
            api.sessionApi.postFullCapabilities(
                id = null,
                data =
                    ClientCapabilitiesDto(
                        playableMediaTypes = listOf(MediaType.VIDEO),
                        supportedCommands =
                            listOf(
                                GeneralCommandType.SET_AUDIO_STREAM_INDEX,
                                GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
                            ),
                        supportsMediaControl = true,
                        supportsPersistentIdentifier = true,
                    ),
            )
        } catch (e: ApiClientException) {
            Timber.w(e, "Reporting remote control capabilities failed")
        }
    }

    private class SocketClosed(message: String) : Exception(message)
}

private const val KEEP_ALIVE = """{"MessageType":"KeepAlive"}"""
private const val NORMAL_CLOSURE = 1000

// Jellyfin closes a socket that stays quiet for 60 s.
private val KeepAliveInterval = 30.seconds
private val StopAfter = 5.seconds
private val ReconnectDelays = listOf(1.seconds, 2.seconds, 5.seconds, 10.seconds, 30.seconds)
