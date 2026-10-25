package nl.jellycast.tv.core.playback

/** As measured on the Chromecast with Google TV 4K, with FFmpeg audio. */
val ChromecastCapabilities =
    DeviceCapabilities(
        videoCodecs = setOf("h264", "hevc", "vp9", "mpeg2video", "mpeg4"),
        hevcMain10 = true,
        avcHigh10 = false,
        maxVideoWidth = 4096,
        maxVideoHeight = 2160,
        hdrTypes = setOf(HdrType.Hdr10, HdrType.Hlg, HdrType.DolbyVision),
        dolbyVisionProfiles = setOf(4, 5, 8),
        passthroughAudio = setOf("ac3", "eac3"),
        decodableAudio =
            setOf(
                "aac", "mp3", "flac", "opus", "vorbis",
                "pcm_s16le", "pcm_s24le", "pcm_s32le",
                "ac3", "eac3", "dts", "dca", "truehd", "mlp",
            ),
        maxPcmChannels = 8,
    )

/** A basic 1080p device: H.264 only, no HDR, no passthrough. */
val BasicCapabilities =
    DeviceCapabilities(
        videoCodecs = setOf("h264"),
        hevcMain10 = false,
        avcHigh10 = false,
        maxVideoWidth = 1920,
        maxVideoHeight = 1080,
        hdrTypes = emptySet(),
        dolbyVisionProfiles = emptySet(),
        passthroughAudio = emptySet(),
        decodableAudio = setOf("aac", "mp3"),
        maxPcmChannels = 2,
    )
