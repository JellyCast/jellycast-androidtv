package nl.jellycast.core.playback

import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.PlaybackTech
import nl.jellycast.core.model.StreamChoice
import org.jellyfin.sdk.api.client.exception.ApiClientException
import timber.log.Timber

fun interface PlaybackTechProvider {
    /** `null` when the server does not answer; the tech line is then left out. */
    suspend fun techFor(itemId: String): PlaybackTech?
}

/**
 * Uses the same playback plan as the player, so the tech line matches what will happen. Requesting
 * a plan starts no stream or transcode.
 */
class DefaultPlaybackTechProvider(
    private val preparer: PlaybackPlanner,
    private val settingsStore: PlaybackSettingsStore,
) : PlaybackTechProvider {
    override suspend fun techFor(itemId: String): PlaybackTech? = try {
        preparer.prepare(
            itemId,
            startPositionMs = 0,
            settings = settingsStore.current(),
            fallback = PlaybackFallback(),
            requestedStreams = StreamChoice(null, null),
        ).toTech()
    } catch (e: ApiClientException) {
        Timber.w(e, "Could not fetch the tech line")
        null
    }
}

internal fun PlaybackPlan.toTech(): PlaybackTech {
    val chosen = audioTracks.firstOrNull { it.index == audioStreamIndex }
    // A language choice is no replacement; a codec swap within the language is.
    val source = chosen?.let { audioTracks.sourceOf(it) }
    return PlaybackTech(
        playMethod = playMethod,
        audio = chosen,
        replacedAudio = source?.takeIf { it.index != chosen.index },
        outcome = outcome,
    )
}
