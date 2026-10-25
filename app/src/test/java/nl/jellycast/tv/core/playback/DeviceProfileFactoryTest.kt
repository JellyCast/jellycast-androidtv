package nl.jellycast.tv.core.playback

import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.VideoRangeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileFactoryTest {
    private fun DeviceProfile.directPlayAudio() =
        directPlayProfiles.single().audioCodec.orEmpty().split(',').toSet()

    private fun DeviceProfile.directPlayVideo() =
        directPlayProfiles.single().videoCodec.orEmpty().split(',').toSet()

    private fun DeviceProfile.hevcRangeTypes(): Set<String> = codecProfiles
        .single { it.type == CodecType.VIDEO && it.codec == "hevc" }
        .conditions
        .single { it.property == ProfileConditionValue.VIDEO_RANGE_TYPE }
        .value
        .orEmpty()
        .split('|')
        .toSet()

    @Test
    fun `the bitrate limit is well above the heaviest files`() {
        val profile = createDeviceProfile(ChromecastCapabilities)
        assertEquals(DEFAULT_MAX_BITRATE, profile.maxStreamingBitrate)
        assertTrue(profile.maxStreamingBitrate!! > 117_000_000)
    }

    @Test
    fun `only video codecs with a hardware decoder`() {
        assertEquals(
            setOf("h264", "hevc", "vp9", "mpeg2video", "mpeg4"),
            createDeviceProfile(ChromecastCapabilities).directPlayVideo(),
        )
        assertEquals(setOf("h264"), createDeviceProfile(BasicCapabilities).directPlayVideo())
    }

    @Test
    fun `without local decoding TrueHD and DTS are not in direct play`() {
        val audio = createDeviceProfile(
            ChromecastCapabilities,
            PlaybackSettings(decodeLosslessAudioLocally = false),
        ).directPlayAudio()
        assertTrue("eac3" in audio)
        assertTrue("ac3" in audio)
        assertFalse("truehd" in audio)
        assertFalse("dts" in audio)
    }

    @Test
    fun `with local decoding TrueHD and DTS may direct play`() {
        val settings = PlaybackSettings(decodeLosslessAudioLocally = true)
        val audio = createDeviceProfile(ChromecastCapabilities, settings).directPlayAudio()
        assertTrue("truehd" in audio)
        assertTrue("dts" in audio)
    }

    @Test
    fun `Dolby Vision profile 7 and 8_6 direct play, like DV with an HDR10 base layer`() {
        val ranges = createDeviceProfile(ChromecastCapabilities).hevcRangeTypes()
        assertTrue(VideoRangeType.DOVI_WITH_EL.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_INVALID.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_WITH_HDR10.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_WITH_HDR10_PLUS.serialName in ranges)
        assertTrue(VideoRangeType.DOVI.serialName in ranges)
        assertTrue(VideoRangeType.HDR10_PLUS.serialName in ranges)
    }

    @Test
    fun `without an HDR display only SDR, so the server tone maps`() {
        assertEquals(
            setOf(VideoRangeType.SDR.serialName),
            createDeviceProfile(BasicCapabilities).hevcRangeTypes(),
        )
    }

    @Test
    fun `DV profile 5 only with a DV decoder that reports profile 5`() {
        val withoutP5 = ChromecastCapabilities.copy(dolbyVisionProfiles = setOf(8))
        val ranges = createDeviceProfile(withoutP5).hevcRangeTypes()
        assertFalse(VideoRangeType.DOVI.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_WITH_HDR10.serialName in ranges)
    }

    @Test
    fun `image subtitles are embedded, never burned in by the server`() {
        val embedded =
            createDeviceProfile(ChromecastCapabilities)
                .subtitleProfiles
                .filter { it.method == SubtitleDeliveryMethod.EMBED }
                .mapNotNull { it.format }
        assertTrue(embedded.containsAll(listOf("pgssub", "dvdsub", "ass", "subrip", "mov_text")))
        assertTrue(
            createDeviceProfile(ChromecastCapabilities).subtitleProfiles.none {
                it.method ==
                    SubtitleDeliveryMethod.ENCODE
            },
        )
    }

    @Test
    fun `transcoding prefers EAC3 so it passes through to the amplifier`() {
        val transcoding = createDeviceProfile(ChromecastCapabilities).transcodingProfiles.single()
        assertEquals("eac3", transcoding.audioCodec.split(',').first())
        assertEquals("hevc,h264", transcoding.videoCodec)
        assertEquals(
            "aac",
            createDeviceProfile(BasicCapabilities).transcodingProfiles.single().audioCodec,
        )
    }

    @Test
    fun `ASS embeds by default and only encodes when burning in`() {
        fun methodFor(profile: DeviceProfile, format: String) =
            profile.subtitleProfiles.filter { it.format == format }.map { it.method }

        val standard = createDeviceProfile(ChromecastCapabilities)
        assertEquals(listOf(SubtitleDeliveryMethod.EMBED), methodFor(standard, "ass"))

        val burnIn = createDeviceProfile(ChromecastCapabilities, PlaybackSettings(burnInAss = true))
        assertEquals(listOf(SubtitleDeliveryMethod.ENCODE), methodFor(burnIn, "ass"))
        assertEquals(listOf(SubtitleDeliveryMethod.ENCODE), methodFor(burnIn, "ssa"))
        // Burning in PGS would make the server transcode the video.
        assertEquals(listOf(SubtitleDeliveryMethod.EMBED), methodFor(burnIn, "pgs"))
    }

    @Test
    fun `transcodes to 5_1 when EAC3 passes through, otherwise to the output's channels`() {
        fun channels(capabilities: DeviceCapabilities) =
            createDeviceProfile(capabilities).transcodingProfiles.single().maxAudioChannels

        assertEquals("6", channels(ChromecastCapabilities))
        assertEquals("2", channels(BasicCapabilities))
        val pcmSurround = ChromecastCapabilities.copy(passthroughAudio = emptySet())
        assertEquals("6", channels(pcmSurround))
    }

    @Test
    fun `stereo direct plays only stereo codecs and transcodes the rest to stereo AAC`() {
        val profile =
            createDeviceProfile(ChromecastCapabilities, PlaybackSettings(downmixToStereo = true))

        assertEquals(setOf("aac", "mp3", "opus", "vorbis", "flac"), profile.directPlayAudio())
        val transcoding = profile.transcodingProfiles.single()
        assertEquals("aac", transcoding.audioCodec)
        assertEquals("2", transcoding.maxAudioChannels)
        val channels =
            profile.codecProfiles
                .single { it.type == CodecType.VIDEO_AUDIO }
                .conditions
                .single()
        assertEquals(ProfileConditionValue.AUDIO_CHANNELS, channels.property)
        assertEquals("2", channels.value)
    }

    @Test
    fun `without stereo there is no channel limit for direct play`() {
        val profile = createDeviceProfile(ChromecastCapabilities)
        assertTrue(
            profile.codecProfiles.none {
                it.type == CodecType.VIDEO_AUDIO && it.codec == null
            },
        )
    }

    private fun DeviceProfile.videoCondition(codec: String, property: ProfileConditionValue) =
        codecProfiles
            .single { it.type == CodecType.VIDEO && it.codec == codec }
            .conditions
            .singleOrNull { it.property == property }
            ?.value

    @Test
    fun `level and resolution follow the decoder`() {
        val caps =
            ChromecastCapabilities.copy(
                maxVideoLevels = mapOf("hevc" to 153, "h264" to 41),
                maxVideoSizes = mapOf("h264" to VideoSize(1920, 1088)),
            )
        val profile = createDeviceProfile(caps)

        assertEquals("153", profile.videoCondition("hevc", ProfileConditionValue.VIDEO_LEVEL))
        assertEquals("41", profile.videoCondition("h264", ProfileConditionValue.VIDEO_LEVEL))
        assertEquals("1920", profile.videoCondition("h264", ProfileConditionValue.WIDTH))
    }

    @Test
    fun `ignoring the video level removes the level limit`() {
        val caps = ChromecastCapabilities.copy(maxVideoLevels = mapOf("hevc" to 150, "h264" to 41))
        val ignored =
            createDeviceProfile(caps.withOverrides(PlaybackSettings(ignoreVideoLevel = true)))
        assertEquals(null, ignored.videoCondition("hevc", ProfileConditionValue.VIDEO_LEVEL))
        assertEquals(null, ignored.videoCondition("h264", ProfileConditionValue.VIDEO_LEVEL))
        val checked = createDeviceProfile(caps.withOverrides(PlaybackSettings()))
        assertEquals("150", checked.videoCondition("hevc", ProfileConditionValue.VIDEO_LEVEL))
    }

    @Test
    fun `without a measured level there is no level limit`() {
        val profile = createDeviceProfile(ChromecastCapabilities)
        assertEquals(null, profile.videoCondition("hevc", ProfileConditionValue.VIDEO_LEVEL))
    }

    @Test
    fun `VP9 and AV1 get HDR only with a 10-bit decoder and an HDR display`() {
        val av1 = ChromecastCapabilities.copy(
            videoCodecs =
                ChromecastCapabilities.videoCodecs + "av1",
        )
        val eightBit = createDeviceProfile(av1)
        assertEquals("8", eightBit.videoCondition("av1", ProfileConditionValue.VIDEO_BIT_DEPTH))
        assertEquals("SDR", eightBit.videoCondition("av1", ProfileConditionValue.VIDEO_RANGE_TYPE))

        val tenBit = createDeviceProfile(av1.copy(av1Main10 = true))
        assertEquals(null, tenBit.videoCondition("av1", ProfileConditionValue.VIDEO_BIT_DEPTH))
        val ranges = tenBit.videoCondition(
            "av1",
            ProfileConditionValue.VIDEO_RANGE_TYPE,
        )!!.split('|')
        assertTrue("HDR10" in ranges)
        assertTrue("HLG" in ranges)

        val sdrTv = createDeviceProfile(av1.copy(av1Main10 = true, hdrTypes = emptySet()))
        assertEquals(
            "SDR",
            sdrTv.videoCondition("av1", ProfileConditionValue.VIDEO_RANGE_TYPE),
        )
    }

    @Test
    fun `locally decoded TrueHD only as surround`() {
        val local =
            createDeviceProfile(
                ChromecastCapabilities,
                PlaybackSettings(decodeLosslessAudioLocally = true),
            )
        val truehd = local.codecProfiles.single {
            it.type == CodecType.VIDEO_AUDIO &&
                it.codec == "truehd"
        }
        assertEquals(ProfileConditionValue.AUDIO_CHANNELS, truehd.conditions.single().property)
        assertEquals("3", truehd.conditions.single().value)

        val receiver = ChromecastCapabilities.copy(
            passthroughAudio = setOf("ac3", "eac3", "truehd"),
        )
        assertTrue(createDeviceProfile(receiver).codecProfiles.none { it.codec == "truehd" })
    }

    @Test
    fun `a known DV HDR10+ defect excludes only those variants`() {
        val broken = ChromecastCapabilities.copy(dolbyVisionHdr10PlusBroken = true)
        val ranges = createDeviceProfile(broken).hevcRangeTypes()
        assertFalse(VideoRangeType.DOVI_WITH_HDR10_PLUS.serialName in ranges)
        assertFalse(VideoRangeType.DOVI_WITH_ELHDR10_PLUS.serialName in ranges)
        assertTrue(VideoRangeType.DOVI_WITH_HDR10.serialName in ranges)
        assertTrue(VideoRangeType.HDR10_PLUS.serialName in ranges)
    }

    @Test
    fun `by default the Chromecast decodes TrueHD and DTS itself, but not for an ARC soundbar`() {
        val local = createDeviceProfile(ChromecastCapabilities).directPlayAudio()
        assertTrue("truehd" in local)
        assertTrue("dts" in local)

        val arc = createDeviceProfile(
            ChromecastCapabilities.copy(maxPcmChannels = 2),
        ).directPlayAudio()
        assertFalse("truehd" in arc)
        assertFalse("dts" in arc)
    }
}
