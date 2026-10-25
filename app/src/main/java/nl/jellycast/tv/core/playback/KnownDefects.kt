package nl.jellycast.tv.core.playback

// The only place with knowledge about specific devices: proven defects only, each with a source.
private val DolbyVisionHdr10PlusBroken =
    setOf(
        "AFTKA", // Fire TV 4K Max (1st gen)
        "AFTKM", // Fire TV 4K (2nd gen)
        "AFTKRT", // Fire TV 4K Max (2nd gen)
        "AFTMM", // Fire TV 4K (1st gen)
        "BRAVIA 4K VH22", // Sony (MediaTek): crashes on DV p8 with HDR10+ (Moonfin)
    )

/** Plays DV profile 7 but does not report it. */
private val UnreportedDolbyVisionProfile7 =
    setOf(
        "SHIELD Android TV", // Shield TV Pro 2019 (mdarcy), jellyfin-androidtv KnownDefects.kt
    )

fun DeviceCapabilities.withKnownDefects(model: String): DeviceCapabilities {
    val name = model.trim()
    var result = this
    if (DolbyVisionHdr10PlusBroken.any { it.equals(name, ignoreCase = true) }) {
        result = result.copy(dolbyVisionHdr10PlusBroken = true)
    }
    val p7Capable = HdrType.DolbyVision in hdrTypes && hevcMain10 && HdrType.Hdr10 in hdrTypes
    if (p7Capable && UnreportedDolbyVisionProfile7.any { it.equals(name, ignoreCase = true) }) {
        result = result.copy(dolbyVisionProfiles = dolbyVisionProfiles + 7)
    }
    return result
}

/** MediaTek decoders report AVC High 10 but show a black picture instead (Sony, TCL, Philips). */
fun overstatesAvcHigh10(decoderName: String): Boolean =
    decoderName.startsWith("OMX.MTK.", ignoreCase = true) ||
        decoderName.startsWith("c2.mtk.", ignoreCase = true)
