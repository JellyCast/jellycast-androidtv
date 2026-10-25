package nl.jellycast.tv.core.playback

import nl.jellycast.tv.core.model.PlayMethod
import nl.jellycast.tv.core.model.PlaybackPlan

/**
 * Auto tunnels only Dolby Vision through the DV decoder on an Amlogic chip, which otherwise drops
 * frames; elsewhere tunneling gains little and breaks seeking on some devices.
 */
fun usesTunneling(mode: FormatSupport, plan: PlaybackPlan, socManufacturer: String): Boolean =
    when (mode) {
        FormatSupport.On -> true

        FormatSupport.Off -> false

        FormatSupport.Auto ->
            socManufacturer.equals("Amlogic", ignoreCase = true) &&
                plan.playMethod != PlayMethod.Transcode &&
                !plan.decodeDolbyVisionAsHevc &&
                plan.video?.rangeType?.startsWith("DOVI", ignoreCase = true) == true
    }
