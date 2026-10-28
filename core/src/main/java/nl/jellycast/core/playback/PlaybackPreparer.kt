package nl.jellycast.core.playback

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.repository.OriginalLanguageRepository
import nl.jellycast.core.data.repository.PlaybackRepository
import nl.jellycast.core.data.repository.PlaybackRequest
import nl.jellycast.core.model.BitrateLimit
import nl.jellycast.core.model.BitrateLimitCause
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.StreamChoice
import timber.log.Timber

fun interface PlaybackPlanner {
    suspend fun prepare(
        itemId: String,
        startPositionMs: Long,
        settings: PlaybackSettings,
        fallback: PlaybackFallback,
        requestedStreams: StreamChoice,
    ): PlaybackPlan
}

/**
 * When the app picks a different audio track than the server, the plan is requested again: the
 * server's decision (direct play or transcode audio) depends on the track.
 */
class PlaybackPreparer(
    private val capabilitiesReader: CapabilitiesReader,
    private val repository: PlaybackRepository,
    private val originalLanguages: OriginalLanguageRepository,
    private val dispatchers: AppDispatchers,
) : PlaybackPlanner {
    override suspend fun prepare(
        itemId: String,
        startPositionMs: Long,
        settings: PlaybackSettings,
        fallback: PlaybackFallback,
        requestedStreams: StreamChoice,
    ): PlaybackPlan = coroutineScope {
        val original = async { originalLanguages.originalLanguages(itemId) }
        val capabilities =
            withContext(dispatchers.io) { capabilitiesReader.read() }
                .withOverrides(settings)
                .limitedBy(fallback)
        val effectiveSettings =
            settings.copy(downmixToStereo = settings.downmixToStereo || fallback.stereo)
        val profile = createDeviceProfile(capabilities, effectiveSettings)
        Timber.d("Device: %s", capabilities)
        if (fallback != PlaybackFallback()) Timber.i("Fallback active: %s", fallback)
        val bitrateLimit = bitrateLimit(settings.maxStreamingBitrate, fallback.maxBitrate)

        val request =
            PlaybackRequest(
                itemId = itemId,
                deviceProfile = profile,
                maxStreamingBitrate =
                    bitrateLimit?.bitsPerSecond ?: settings.maxStreamingBitrate,
                startPositionMs = startPositionMs,
                allowDirectPlay = fallback.directPlay,
                allowVideoCopy = fallback.videoCopy,
            )
        val first = repository.getPlaybackPlan(request)
        val chosen =
            first.audioTracks.firstOrNull { it.index == requestedStreams.audioStreamIndex }
                ?: selectAudioTrack(
                    first.audioTracks,
                    capabilities,
                    effectiveSettings,
                    preferredLanguages = original.await() + FallbackAudioLanguages,
                )
        val plan =
            if (chosen != null && chosen.index != first.audioStreamIndex) {
                Timber.i(
                    "Audio track %s (%s) instead of %s",
                    chosen.index,
                    chosen.codec,
                    first.audioStreamIndex,
                )
                repository.getPlaybackPlan(
                    request.copy(
                        mediaSourceId = first.mediaSourceId,
                        audioStreamIndex = chosen.index,
                    ),
                )
            } else {
                first
            }

        val withPlayerChoices =
            plan.copy(
                subtitleStreamIndex =
                    plan.subtitleTracks
                        .firstOrNull { it.index == requestedStreams.subtitleStreamIndex }
                        ?.index,
                decodeDolbyVisionAsHevc = needsHevcBaseLayer(plan, capabilities),
                passthroughAudio = playerPassthrough(capabilities, settings, fallback),
                allowTunneling = fallback.tunneling,
                bitrateLimit = bitrateLimit,
            )
        val result = withPlayerChoices.copy(
            outcome = summarizePlayback(withPlayerChoices, capabilities),
        )

        val reasons =
            plan.transcodeReasons
                .takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = " (", postfix = ")")
                .orEmpty()
        Timber.i(
            "Playing '%s': %s%s · video %s · audio #%s",
            plan.title,
            plan.playMethod,
            reasons,
            plan.video?.let {
                "${it.codec} ${it.width}x${it.height} ${it.rangeType} ${it.frameRate}"
            },
            plan.audioStreamIndex,
        )
        result
    }
}

internal fun bitrateLimit(settingBitrate: Int, fallbackBitrate: Int?): BitrateLimit? = when {
    fallbackBitrate != null && fallbackBitrate < settingBitrate ->
        BitrateLimit(fallbackBitrate, BitrateLimitCause.SlowNetwork)

    settingBitrate < DEFAULT_MAX_BITRATE ->
        BitrateLimit(settingBitrate, BitrateLimitCause.QualitySetting)

    else -> null
}

/**
 * Plays the HDR10 base layer through HEVC for DV profile 7 without a p7 decoder, and whenever Dolby
 * Vision is off: otherwise Media3 still picks the DV decoder while the display reports DV.
 */
internal fun needsHevcBaseLayer(plan: PlaybackPlan, capabilities: DeviceCapabilities): Boolean {
    val profile = plan.video?.dolbyVisionProfile ?: return false
    if (HdrType.DolbyVision !in capabilities.hdrTypes) return true
    return profile == 7 && 7 !in capabilities.dolbyVisionProfiles
}

/** `null` lets the output decide; a set only when the fallback or the user disabled passthrough. */
internal fun playerPassthrough(
    capabilities: DeviceCapabilities,
    settings: PlaybackSettings,
    fallback: PlaybackFallback,
): Set<String>? = capabilities.passthroughAudio.takeIf {
    !fallback.passthrough || fallback.stereo || settings.disablesPassthrough()
}
