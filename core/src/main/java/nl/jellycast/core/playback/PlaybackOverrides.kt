package nl.jellycast.core.playback

/**
 * On adds a format the device does not report (an AVR misreporting passthrough); Off removes one it
 * does report (a TV that promises Dolby Vision but shows wrong colors).
 */
fun DeviceCapabilities.withOverrides(settings: PlaybackSettings): DeviceCapabilities {
    var hdr = hdrTypes
    var passthrough = passthroughAudio
    for ((format, support) in settings.formatSupport) {
        val type = format.hdrType
        when (support) {
            FormatSupport.Auto -> Unit

            FormatSupport.On -> {
                if (type != null) hdr = hdr + type
                passthrough = passthrough + format.codecs
            }

            FormatSupport.Off -> {
                if (type != null) hdr = hdr - type
                passthrough = passthrough - format.codecs
            }
        }
    }
    return copy(
        hdrTypes = hdr,
        passthroughAudio = passthrough,
        maxVideoLevels = if (settings.ignoreVideoLevel) emptyMap() else maxVideoLevels,
    )
}

/** When true the player must refuse passthrough too, not just the profile. */
fun PlaybackSettings.disablesPassthrough(): Boolean = formatSupport.any { (format, support) ->
    format.codecs.isNotEmpty() &&
        support == FormatSupport.Off
}
