package nl.jellycast.tv.core.data.mapper

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import nl.jellycast.tv.core.model.RemoteCommand
import nl.jellycast.tv.core.model.StreamChoice
import nl.jellycast.tv.core.model.ticksToMs

/** `null` for messages the player doesn't act on and for anything unreadable. */
fun parseRemoteCommand(text: String): RemoteCommand? {
    val message =
        try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return null
    val data = message["Data"]
    return when (message.string("MessageType")) {
        "Playstate" -> (data as? JsonObject)?.playstateCommand()
        "GeneralCommand" -> (data as? JsonObject)?.generalCommand()
        "Play" -> (data as? JsonObject)?.playCommand()
        else -> null
    }
}

private fun JsonObject.playstateCommand(): RemoteCommand? = when (string("Command")) {
    "Pause" -> RemoteCommand.Pause
    "Unpause" -> RemoteCommand.Unpause
    "PlayPause" -> RemoteCommand.PlayPause
    "Stop" -> RemoteCommand.Stop
    "NextTrack" -> RemoteCommand.NextEpisode
    "Seek" -> long("SeekPositionTicks")?.let { RemoteCommand.Seek(it.ticksToMs()) }
    else -> null
}

private fun JsonObject.generalCommand(): RemoteCommand? {
    val index = (this["Arguments"] as? JsonObject)?.string("Index")?.toIntOrNull() ?: return null
    return when (string("Name")) {
        "SetAudioStreamIndex" -> RemoteCommand.SetAudioStream(index)
        "SetSubtitleStreamIndex" -> RemoteCommand.SetSubtitleStream(index)
        else -> null
    }
}

/** Only "play now" of one item: the player has no queue. */
private fun JsonObject.playCommand(): RemoteCommand? {
    if (string("PlayCommand") != "PlayNow") return null
    val itemId =
        (this["ItemIds"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.contentOrNull
            ?: return null
    return RemoteCommand.Play(
        itemId.replace("-", ""),
        long("StartPositionTicks")?.ticksToMs() ?: 0,
        StreamChoice(long("AudioStreamIndex")?.toInt(), long("SubtitleStreamIndex")?.toInt()),
    )
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
