package nl.jellycast.mobile.testing

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.RemoteCommand
import nl.jellycast.mobile.core.remote.RemoteDevice
import nl.jellycast.mobile.core.remote.RemoteDeviceStore
import nl.jellycast.mobile.core.remote.RemoteItem
import nl.jellycast.mobile.core.remote.RemoteSession
import nl.jellycast.mobile.core.remote.RemoteSessionsApi
import nl.jellycast.mobile.core.remote.RemoteSnapshot
import nl.jellycast.mobile.core.remote.RemoteTracks
import nl.jellycast.mobile.core.remote.SavedRemoteDevices
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvControl
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvLink
import nl.jellycast.mobile.core.remote.androidtv.AndroidTvPairing
import nl.jellycast.mobile.core.remote.androidtv.TvKey
import nl.jellycast.mobile.core.remote.androidtv.TvVolume

class FakeRemoteSessionsApi(
    var snapshot: RemoteSnapshot = RemoteSnapshot(emptyList(), emptyList()),
) : RemoteSessionsApi {
    var failSnapshots = false
    var failCommands = false
    var tracks = RemoteTracks(emptyList(), emptyList())
    val sent = mutableListOf<Pair<RemoteCommand, String>>()

    override suspend fun getSnapshot(): Result<RemoteSnapshot> =
        if (failSnapshots) Result.failure(IOException("server gone")) else Result.success(snapshot)

    override suspend fun send(command: RemoteCommand, sessionId: String): Result<Unit> {
        sent += command to sessionId
        return if (failCommands) Result.failure(IOException("refused")) else Result.success(Unit)
    }

    override suspend fun getTracks(itemId: String): Result<RemoteTracks> = Result.success(tracks)
}

class FakeRemoteDeviceStore(var saved: SavedRemoteDevices = SavedRemoteDevices()) :
    RemoteDeviceStore {
    override suspend fun load(): SavedRemoteDevices = saved

    override suspend fun save(devices: SavedRemoteDevices) {
        saved = devices
    }
}

class FakeAndroidTvLink : AndroidTvLink {
    val reachableHosts = mutableSetOf<String>()
    val opened = mutableListOf<Pair<String, String>>()
    val turnedOff = mutableListOf<String>()
    var failWake = false

    override suspend fun isReachable(host: String): Boolean = host in reachableHosts

    override suspend fun wakeAndOpen(host: String, link: String) {
        if (failWake) throw IOException("asleep for good")
        opened += host to link
    }

    override suspend fun turnOff(host: String) {
        turnedOff += host
    }
}

/** Shows the code at once; [failWith] makes the pairing end with that error after the code. */
class FakeAndroidTvPairing : AndroidTvPairing {
    var failBeforeCode: IOException? = null
    var failWith: IOException? = null
    val codes = mutableListOf<String>()

    override suspend fun pair(
        host: String,
        clientName: String,
        onCodeShown: () -> Unit,
        awaitCode: suspend () -> String,
    ) {
        failBeforeCode?.let { throw it }
        onCodeShown()
        codes += awaitCode()
        failWith?.let { throw it }
    }
}

class FakeAndroidTvControl : AndroidTvControl {
    val pressed = mutableListOf<TvKey>()
    var onVolume: ((TvVolume) -> Unit)? = null

    override suspend fun run(host: String, onReady: () -> Unit, onVolume: (TvVolume) -> Unit) {
        this.onVolume = onVolume
        onReady()
        CompletableDeferred<Unit>().await()
    }

    override suspend fun press(key: TvKey) {
        pressed += key
    }
}

fun testRemoteSession(
    id: String = "session-1",
    deviceId: String = "device-1",
    itemId: String = "m1",
    positionMs: Long = 60_000,
    isPaused: Boolean = false,
    checkedInAtMs: Long? = 0,
    canControl: Boolean = true,
    runtimeMs: Long? = 600_000,
    deviceName: String = "chromecast",
) = RemoteSession(
    id = id,
    deviceId = deviceId,
    deviceName = deviceName,
    item = RemoteItem(id = itemId, name = "Item $itemId", runtimeMs = runtimeMs),
    positionMs = positionMs,
    isPaused = isPaused,
    playMethod = PlayMethod.DirectPlay,
    checkedInAtMs = checkedInAtMs,
    canControl = canControl,
    audioStreamIndex = 1,
    subtitleStreamIndex = null,
)

fun testRemoteDevice(
    id: String = "session-1",
    deviceId: String = "device-1",
    name: String = "chromecast",
    lastActivityMs: Long? = 0,
    address: String? = "192.168.1.20",
    isAndroidTv: Boolean = true,
    isPlaying: Boolean = false,
) = RemoteDevice(id, deviceId, name, isPlaying, lastActivityMs, address, isAndroidTv)
