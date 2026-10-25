---
paths:
  - "**/playback/**"
  - "**/player/**"
---

# Playback

Rules for Media3/ExoPlayer, the Jellyfin DeviceProfile, codecs, subtitles and playback reporting. Applies to `core/playback/` and `feature/player/`. Rationale and sources: `docs/research/playback-engine.md`.
**Split up (2026-10-12)** so every file stays within 100 lines. Here: Choices · Player lifecycle · Controls · DeviceProfile · Subtitles. In `playback-streams.md`: Stream choice and playback · Per-format settings · Media Segments · Start log · Error recovery · Playback reporting.

## Choices (decided 2026-10-08)

- **Engine:** Media3/ExoPlayer only, with the FFmpeg audio extension (`org.jellyfin.media3:media3-ffmpeg-decoder`). No mpv or libVLC in the app: mpv on Android can't send HDR/DV to the TV, and libVLC is 40 MB and proved unstable. Fallback for edge cases: later an "Open in external player" button (intent), not a second engine.
- **The Chromecast does the work, the server as little as possible.** The server is an Intel N150 (4 low-power cores, iGPU, 8 GB RAM). Order of preference:
  1. direct play
  2. remux
  3. transcode audio only
  4. transcode video, only when there is no other way (e.g. VC-1)
  Subtitles are never burned in by the server if the Chromecast can show them itself.
- **libass (full ASS):** not in phase 2. Later as an opt-in setting, only after a memory measurement (the official app removed it because of crashes).
- **The server stays on Jellyfin 10.11.x** with SDK 1.8.12. An upgrade to Jellyfin 12 also means SDK 1.9.x; we do that deliberately and separately.

## Player lifecycle [D1] [D2] [C11]

- One `ExoPlayer` per player screen, created via `PlayerFactory` in `core/playback`. Never a player in a singleton or in Koin as `single`.
- Access the player only on the main thread.
- Create the player when entering the screen and call `release()` when leaving and in `onStop` (via `LifecycleStartEffect`). Why: the hardware decoder is scarce, and video must pause when the app goes to the background (TV-NP).
- Next episode: a new playback plan gets a new player (the old one is released first). Why: the decoder choice (e.g. Dolby Vision via the HEVC decoder for profile 7) is fixed when the player is created, and the next episode may have a different plan. Changed in phase 5 (2026-10-09); previously this said "reuse the same player".
- Create a `MediaSession` with the same player on creation, and release in the order session → player. No `MediaSessionService`: there is no background playback.
- Keep the screen on during playback (`keepScreenOn`) to prevent Ambient Mode (TV-BU), and turn that off on pause and stop.
- Buffer: `DefaultLoadControl` with `targetBufferBytes = min(default, heap / 2)`, with a heap ≤ 192 MB `heap / 3` (`bufferBytesFor` in `PlayerFactory`), never higher than the default (about 138 MB video + audio), and `prioritizeTimeOverSizeThresholds` off. Why: the buffer lives on the Java heap. On the Chromecast (256 MB) that is 128 MB, about 9 s at 117 Mbps; a device with a 128 MB heap gets ~43 MB, so it fits next to UI, Coil and FFmpeg (Moonfin mf#1085: OOM at heap/2). Measure heap and rebuffers with the heaviest test files.
- Track selector: `DefaultTrackSelector` with `setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)`. Why: if the audio output changes during playback (Bluetooth on, amplifier off), ExoPlayer selects again, e.g. from passthrough to decoding itself. On the Chromecast the audio doesn't change renderer during the mode switch (measured 2026-10-10).

## Controls [C2] [D5]

- D-pad center = play/pause, left/right = seek back/forward (e.g. 10 s), `KEYCODE_MEDIA_PLAY_PAUSE` toggles (TV-PC, TV-PP). Test this on the device.
- Phase 2 may use `PlayerView` (`media3-ui`) in an `AndroidView`. Make it focusable and forward key events (`onKeyEvent { playerView.dispatchKeyEvent(it.nativeKeyEvent) }`).
- The overlay (phase 5) is Compose on `media3-ui-compose`: `ContentFrame` for the picture, state holders like `rememberPlayPauseButtonState` and `rememberProgressStateWithTickInterval`, with tv-material components. Don't use `media3-ui-compose-material3`: it brings in `androidx.compose.material3`, which must not be mixed with tv-material.
- Subtitles are drawn by Media3's `SubtitleView` (in an `AndroidView`, fed via `onCues`), including PGS and VobSub; `ContentFrame` draws only the picture. The style (size, background) comes from `PlaybackSettingsStore`.
- Subtitles are decoded by the `TextRenderer` (legacy decoding), only for the chosen track: `experimentalParseSubtitlesDuringExtraction(false)`. Never during extraction: then Media3 decodes all tracks on the loading thread, and a file with many PGS tracks (Interstellar: 74) stalls the stream and buffers (measured 2026-10-10, NOTES.md).
- On the overlay: ←/→ on the time bar seeks 10 s, after holding 1 s 30 s per step; the player only seeks when the key has been still for 400 ms. OK = pause/play; within 3 s of a seek step OK seeks at once and plays (`ScrubState.confirmSeek`).

## DeviceProfile [G2] [G3]

- Build the profile with the SDK DSL `buildDeviceProfile { }` (`org.jellyfin.sdk.model.deviceprofile`) in `DeviceProfileFactory`.
- Separate measuring and deciding. `CodecCapabilities` (interface) reads `MediaCodecList`/`AudioCapabilities`. `createDeviceProfile(capabilities, settings)` is a pure function that only uses the result, so it is testable on the JVM with a fake.
- Don't hardcode device knowledge. Derive HEVC Main/Main10, levels, max. resolution, HDR10, HDR10+ and Dolby Vision from `MediaCodecInfo.CodecCapabilities.profileLevels`. Only exception: `KnownDefects.kt`, only for proven defects per model (`Build.MODEL`) or decoder name, each with its source (Fire TV: no DV with HDR10+; Shield: p7 unreported; MediaTek: AVC High 10 reported too broadly).
- Levels and resolution per codec: HEVC and H.264 level (`VIDEO_LEVEL`, ffprobe scale: H.264 ×10, HEVC ×30) and the max. resolution of H.264, VP9 and AV1 come from the decoder. VP9/AV1: 10-bit and HDR only with profile 2 / Main 10 and an HDR display. Chromecast: HEVC 153 (5.1), H.264 51 (measured 2026-10-10).
- Measure audio the way the player plays: the same `AudioAttributes` (movie), the output from `getAudioDevicesForAttributes`, and passthrough tested with the format and channels of a movie (AC3/EAC3/DTS 6, DTS-HD/TrueHD 8), because Media3 refuses passthrough if the output reports fewer channels. FFmpeg codecs are checked per format (`FfmpegLibrary.supportsFormat`).
- Locally decoded TrueHD only with ≥ 3 channels: FFmpeg doesn't decode stereo TrueHD well (androidx/media#1843).
- Dolby Vision: declare only the DV profiles the decoder reports. Profile 5 = `DolbyVisionProfileDvheStn`, profile 8 = `DolbyVisionProfileDvheSt`, profile 7 = `DolbyVisionProfileDvheDtb`. The Chromecast reports 4, 5 and 8, not 7 (measured, see `NOTES.md`).
- Profile 7 (`DOVI_WITH_EL`, 23 items): declare it as supported and **play it directly as HDR10** via the regular HEVC decoder; the base layer is HDR10. The app chooses the decoder for this itself. This also applies when the decoder does report p7 but the display can't do Dolby Vision (`needsHevcBaseLayer`). Don't let the server remux or strip (that costs the N150 work). Test both routes in phase 2; only if the HDR10 route fails, server strip.
- Profile 8.6 (`DOVI_INVALID`, 20 items): technically profile 8 with a UHD-BD compatibility id. Declare it as supported and play it via the DV decoder; if that fails in testing, as HDR10.
- Declaring HDR10+ is fine: the LG doesn't know HDR10+, but the stream then simply plays as HDR10. DV with HDR10+ metadata (`DOVI_WITH_HDR10_PLUS`, 50 items) must play as DV; see Wholphin's `Hdr10PlusMaskingFilter` as an example, should HDR10+ disturb the DV choice.
- Audio: include AC3/EAC3/DTS/TrueHD only if there is a decoder or `AudioCapabilities.isPassthroughPlaybackSupported()` returns true for that format. Don't blindly trust the HDMI plug intent. [D8] [D9]
- The device has **no** platform decoder for AC3, EAC3, DTS or TrueHD (measured). Passthrough: AC3, EAC3 and EAC3-JOC (Atmos). Everything else the FFmpeg extension decodes locally. `EXTENSION_RENDERER_MODE_ON` and `setEnableDecoderFallback(true)`.
- Video only via a hardware decoder if there is one (`withoutSoftwareVideoFallback`): otherwise decoder fallback fell back to `c2.android.hevc.decoder` (can't do 4K, green/blue picture with 10-bit HDR). An error is better; the fallback chain restarts the hardware decoder.
- Other devices: FFmpeg decodes AC3, EAC3, EAC3-JOC and TrueHD, even if there is a platform decoder, except a real Dolby decoder (`c2.dolby.*`, `OMX.dolby.*`) (`allowPlatformAudioDecoder`). Why: generic decoders on TV chips often downmix surround to stereo (Moonfin). Passthrough isn't affected. DTS stays with the platform decoder.
- Audio choice for TrueHD/DTS files (since 2026-10-10; the Sonos receives multichannel PCM 7.1, confirmed in the Sonos app):
  1. an EAC3 track in the same language that can pass unchanged (keeps Atmos with EAC3-JOC)
  2. decode locally to multichannel PCM with FFmpeg (lossless 7.1, without Atmos height). On by default (`decodeLosslessAudioLocally`), but only if the output handles ≥ 6 channels of PCM or there is no AC3/EAC3 passthrough (`decodesLosslessLocally`). Measured: ~10% of one core extra and ~11 MB, no stutters (Fellowship of the Ring TrueHD 7.1, Casino DTS-HD MA).
  3. if the output can only do stereo PCM but can pass AC3/EAC3 (soundbar via ARC), or local decoding is off: an AC3/EAC3 track from the file, then a PCM track with at least as many channels, and otherwise the server transcodes the audio to EAC3 (max. 5.1)
  Without passthrough (stereo TV, Bluetooth) the device decodes everything itself.
  FFmpeg delivers 16-bit PCM (float off, so the downmix chain works); for 24-bit TrueHD that is inaudible in practice.
  Send the chosen `AudioStreamIndex` in PlaybackInfo, because the server's decision depends on it.
- Channels when transcoding (`transcodeAudioChannels`): 6 if EAC3/AC3 can pass to an amplifier, otherwise the number of PCM channels of the output (2–6). A stereo output therefore gets stereo straight away.
- `downmixToStereo` (now only as a fallback): direct play only for stereo codecs (AAC, MP3, Opus, Vorbis, FLAC) with `AUDIO_CHANNELS ≤ 2`; the server transcodes the rest to stereo AAC.
- `maxStreamingBitrate` well above the highest bitrate (e.g. 200 Mbps), otherwise the server refuses direct play with `ContainerBitrateExceedsLimit`. An unknown bitrate counts as 40 Mbps on the server.
- Use transcoding profiles for HLS (TS and fMP4) with HEVC (if supported) and H.264. Set `maxStreamingBitrate` and `maxStaticBitrate` from the quality setting (Settings: no limit = 200 Mbps, or 80/40/20/10). Read the settings before a playback plan with `PlaybackSettingsStore.current()`, not `settings.value`: right after start that is still the default.
- In debug builds, log the generated profile at every start (as JSON), so you can recompute the server's decisions.

## Subtitles [D4] [D7] [G2]

- Media3 renders PGS, VobSub (DVDSUB), SRT, mov_text and VTT/TTML. Declare all of them as `EMBED`, so they never force burn-in (= video transcode on the N150).
- Media3 renders SSA/ASS only simplified: color, size, bold/italic and position yes, fonts, animations and karaoke no. Declare ASS/SSA as `EMBED` by default (direct play, simplified rendering). A "Burn in ASS" setting switches to `ENCODE` only.
- With `ENCODE` (burn-in) the server must transcode. That is the real cause of unexpected transcodes. Show in the UI and in the log that a subtitle choice causes transcoding.
- Deliver WebVTT as the only format via `HLS`: the server converts other text formats in HLS to WebVTT, except ASS/SSA (Jellyfin 10.11 refuses to convert those and then chooses burn-in). So don't request an ASS track with a remux.
- External text subtitles (SRT, VTT) may be `EXTERNAL`; add them as `SubtitleConfiguration` on the `MediaItem`.
