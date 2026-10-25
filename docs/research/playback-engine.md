# Playback engine for JellyCast: research and advice

Date: 2026-10-08. Target device: Chromecast with Google TV 4K (`sabrina`) → LG OLED55CS6L → Sonos Arc. Server Jellyfin 10.11.11, SDK 1.8.12, Media3 1.11.1.
Sources appear as `[n]` in the text and in §7. **(measured)** = established on the device with adb. **(uncertain)** = not from a primary source or not tested.

## 1. Advice

1. **Use only Media3/ExoPlayer**, without a second embedded engine. mpv-android cannot send HDR or Dolby Vision to the display on Android; it tonemaps everything to SDR [3][4]. That rules it out for 481 4K items and 245 DV/HDR items. libVLC is 40 MB of native code, and jellyfin-androidtv dropped it in 2024 over CVEs, memory problems and maintenance [1].
2. **Add the FFmpeg audio extension** (`org.jellyfin.media3:media3-ffmpeg-decoder`). The device has no AC3, EAC3, DTS or TrueHD decoder at all (measured). Without passthrough, such audio is therefore unplayable, and TrueHD/DTS even more so.
3. **Audio route:** EAC3/AC3 go via passthrough. For TrueHD and DTS, pick an EAC3 or AC3 track from the same file by default. Decoding locally to 7.1 PCM via FFmpeg is promising: the HDMI sink reports 5.1/7.1 PCM (measured). It is just unproven that Android does not downmix it to stereo, so keep it behind a setting until it has been tested. Server transcode to EAC3 is the last resort (max. 5.1, Atmos lost).
4. **Dolby Vision:** p5, p8.1 and p8.6 direct play via the DV decoder; the decoder reports profiles 4, 5 and 8 (measured). It does **not** report profile 7. So play p7 directly as the HDR10 base layer via the HEVC decoder (own decoder choice in the renderer), instead of having the server remux it. Both p7 routes must be tested on the device.
5. **Frame rate:** the Chromecast never switches "seamlessly" (measured), and Media3 only does seamless by default. So choose the display mode in the app itself (`preferredDisplayModeId`), and have the user set "Match content frame rate" to *Non-seamless*.
6. **Tunneling off**, even though all video decoders and the HDMI output support it (measured). It is only a debug option for testing.
7. **Subtitles:** Media3 renders SRT, PGS, DVDSUB, mov_text and simplified ASS, all as `Embed`. libass (`ass-media`, about 3 MB) only later, as a measurable opt-in.
8. As a fallback for odd edge cases, an "Open in external player" button (intent to Kodi or mpv-android) is enough. It costs no APK space and no GPL native code.

## 2. Comparison of the engines

| Criterion | Media3/ExoPlayer (+FFmpeg audio, opt. libass) | libmpv (mpv-android / `dev.jdtech.mpv:libmpv`) | libVLC (`libvlc-all`) | Kodi (reference) |
|---|---|---|---|---|
| HW decoding | MediaCodec directly to a `SurfaceView` | `hwdec=mediacodec`; FFmpeg MediaCodec gave green frames on Amlogic (trac #11676, 07-2025) [5] | MediaCodec | MediaCodec |
| HDR/DV to the TV | **Yes**, via the platform DV decoder | **No**, tonemaps to SDR (#1022, 2024-12; #874 "will not happen", 2025-12) [3][4] | (uncertain) | Yes; v21 converts p7 to 8.1 with libdovi (FEL lost) [6] |
| Frame-rate matching | `Surface.setFrameRate`, but only seamless; pick `Display.Mode` yourself | build it yourself | build it yourself | built in; mixed reports on Google TV [7] |
| Passthrough | `AudioCapabilities` (AC3/EAC3/JOC on this device) | `audio-spdif`, unreliable on Android (#871, #619) | yes | yes |
| PGS / ASS | PGS native, ASS simplified; full ASS via libass | native libass | native | native |
| Memory and CPU (32-bit, 2 GB) | lowest; buffer capped via `LoadControl` | higher (GPU render path, FFmpeg demuxer) | jellyfin-androidtv reported memory problems [1] | full app |
| Startup time | fast, no native init | + native init | + native init | n/a |
| Native APK size (armeabi-v7a) | FFmpeg audio ≈ 1.4 MB; libass ≈ 3 MB [1][8] | ≈ 23 MB (AAR 45 MB) [1] | ≈ 40 MB (`libvlc.so` 39.8 MB) [1] | separate app |
| License | Apache-2.0; FFmpeg AAR GPL-3.0; ass-media MIT | GPL-3 (FFmpeg `--enable-gpl`); LGPL only with your own build | LGPL-2.1 | GPL |
| MediaSession, D-pad, Compose | `media3-session`, `media3-ui-compose` | wire it up yourself | wire it up yourself | n/a |
| Maintenance | 1.11.1 (2026-09-10) | active (releases 2026) | 3.7.7 (2026-10-01) | active |

**License for a private app:** the GPL only imposes requirements once you distribute the app. For sideloading onto your own devices, the GPL-3.0 FFmpeg AAR is therefore not a problem. If the app is ever shared, the source code must be GPL-compatible.

**Hybrid (ExoPlayer + embedded mpv or VLC):** not recommended. The hard cases are precisely HDR/DV, and that is where mpv fails. Wholphin, with "Prefer MPV", even explicitly falls back to ExoPlayer for HDR and 4K software decoding [1]. It costs 23–40 MB, a second player lifecycle, and a second path for MediaSession and reporting. That clashes with "smooth and reliable".

## 3. Findings per topic

### 3.1 What existing clients do

- **jellyfin-androidtv:**
  - Media3 1.11.1 only. libVLC was removed in PR #3639 (2024-06-02, v0.17) [1]. Reasons: an outdated libvlc-jni with a CVE, memory problems, and a build going from 107 to 24 MB.
  - Uses `media3-ffmpeg-decoder 1.9.0+1` with `EXTENSION_RENDERER_MODE_ON`, or `PREFER` via a setting.
  - libass was in, came out in 0.19 (#5032, 2025-10) over memory crashes, and is back in the 0.20 betas as an opt-in [8].
  - Switches the refresh rate itself via `Display.Mode`.
  - Tunneling is off.
- **Findroid:** ExoPlayer by default, mpv (libmpv 1.0.0) as an "experimental" opt-in, mainly for ASS [1].
- **Wholphin:**
  - Plays with ExoPlayer by default, with MPV and "Prefer MPV" as options. HDR always goes to ExoPlayer.
  - Its own FFmpeg and AV1 decoders, and ass-media.
  - An `Hdr10PlusMaskingFilter` strips HDR10+ SEI during DV decoding: a useful pattern [1].
- **Plex:** uses its own ExoPlayer fork. HEVC stutters have been reported on the Chromecast (2025-02) [9].
- **Just Player:**
  - Media3 with FFmpeg audio and auto frame rate.
  - Setting "DV profile 7 → play as HDR HEVC": exactly the route recommended here [10].
- **Nova:** its own FFmpeg engine with DV support since 6.0.70 [1].
- **Infuse (Apple):** plays p7 as HDR10. So even a "good" player does no FEL there.

### 3.2 Dolby Vision

**Measured:**
- `c2.amlogic.dolby-vision.dvhe.decoder` reports the profiles `DvheDtr` (p4), `DvheStn` (p5) and `DvheSt` (p8), each at level `Uhd60`.
- It does **not** report `DvheDtb` (p7).
- `dvav` reports `DvavSe` (p9).
- Max. 1 instance, 4K60.
- The display reports DV, HDR10 and HLG; no HDR10+.

**Findings:**
- **Media3 1.11:**
  - Only falls back from DV to HEVC for p8 and p4, and only if the display does *not* report DV [11]. Here the display reports DV, so Media3 always picks the DV decoder.
  - p7 and p5 deliberately have no fallback.
  - A DV track with a profile the decoder does not report has been `NO_EXCEEDS_CAPABILITIES` since 1.11.0, but is still selected [11]. p7 would then go to the dvhe decoder. Whether Amlogic handles that well is **uncertain**. Forums report that single-track p7 MEL/FEL plays on the Chromecast, but "not the most reliably" (2021) [12].
- **Jellyfin 10.11:**
  - p7 gets range type `DOVIWithEL`.
  - **p8 with compatibility ID 6 gets `DOVIInvalid`** (`MediaStream.cs`) [13]. ID 6 is the signaling for an HDR10 base layer from UHD-BD (p7). p8.6 usually results from a p7-to-p8 conversion; the base layer stays HDR10, the RPU is valid.
- **What the server does with a range type that is not supported:**
  - Strip DV via `hevc_metadata=remove_dovi` or `dovi_rpu=strip`, then remux to the HDR10 base layer [13].
  - It **never** converts p7 to 8.1.
  - jellyfin-androidtv always excludes `DOVIInvalid`, and `DOVIWithEL` without a p7 decoder. As a result, p7 is remuxed there instead of played directly (issue #5316, 2026-01) [13].
- **p5** has no HDR10 fallback: without DV processing you get a green/purple picture. A p5 decoder is present here, so direct play.
- **Bug #16687 (2026-04):** `DOVIWithHDR10Plus` is transcoded to SDR if the client does not list that range type [14]. So list it explicitly (50 items).

**Advice:**
- **DeviceProfile, `VideoRangeType` for HEVC:**
  - **Supported:** SDR, HDR10, HLG, HDR10Plus, DOVI, DOVIWithHDR10, DOVIWithHLG, DOVIWithSDR, DOVIWithHDR10Plus, DOVIWithEL, DOVIWithELHDR10Plus and DOVIInvalid.
  - **Why:** everything can direct play. The app decides itself which decoder it will be, and the server does not need to strip anything.
- **Decoder choice in the app** (own `MediaCodecVideoRenderer` with an overridden `getDecoderInfos`):
  - DV p7 (`format.codecs` = `dvhe.07.*`) goes to `video/hevc`. That gives the HDR10 base layer; the EL and RPU NALs are ignored. This is the **default**, because reliable comes before pretty.
  - A debug setting "p7 via DV decoder" makes comparison possible.
  - p8.6 goes via the DV decoder first (p8 is reported). If that fails in testing, the same route to HEVC.
  - **(uncertain)** That `format.codecs` in MKV arrives as `dvhe.07…` follows from parsing `dvcC` [11]. Verify that in phase 2 with a log.
- **Derive range types from `MediaCodecList` and `Display.getHdrCapabilities`**, not hardcoded. That follows `playback.md`, but that rule calls `DOVI_WITH_EL` "not supported unless DvheDtb". **Update that rule** if the user adopts this advice.
- **Measured:** during YouTube SDR playback, the HDMI output was at `DolbyVision-Std` (`hdmi_hdr_status`, dataspace SDR). So the Chromecast is in "always DV" mode and converts SDR to DV. That is not an app problem, but it is a user setting (§4.3).

### 3.3 Audio route

**Measured** (`dumpsys media.audio_policy`, `hdmi_control`):
- **HDMI profiles:** PCM 16-bit in stereo, 5.1 (`0x3f`) and **7.1 (`0x63f`)** at 32, 44.1 and 48 kHz. Also AC3, EAC3, **EAC3-JOC** and IEC61937. **No DTS, TrueHD or MAT.**
- The primary mixer is **stereo only** at 48 kHz. Multichannel PCM can therefore only go via the "direct output".
- **No** platform audio decoder for AC3, EAC3, DTS or TrueHD. There are only software decoders for AAC, MP3, FLAC, Opus, Vorbis, AMR and PCM. `media_codecs_amlogic_audio.xml` refers to ddp, dtshd and ffmpeg XMLs that do not exist.
- **Settings:** `encoded_surround_output=0` (Auto), `encoded_surround_output_enabled_formats=18,5,6` (EAC3-JOC, AC3, EAC3).
- **CEC:** the Sonos Arc is at physical address 0x2000 (TV HDMI 2), the Chromecast at 0x1000. System audio is on.

**Findings:**
- **LG OLED55CS6L:** a 2022 model (C chassis, webOS 22) with 4× HDMI 2.1 and eARC [15]. No DTS passthrough: LG left out DTS in its 2020 through 2022 models [15]. The 7.1 PCM in the HDMI profile suggests that eARC is active and that the Arc passes on its capabilities (inferred, not confirmed).
- **Sonos Arc:** plays DD, DD+ (Atmos), TrueHD (Atmos), multichannel PCM (via eARC, since S2 12.2) and DTS core (since 2022-11). **No DTS-HD or DTS:X** [16].
- **Chromecast with Kodi** (2021): TrueHD went out as stereo LPCM, DTS-HD only as core [12]. That fits the stereo mixer above. Media3's `AudioCapabilities` reads `getDirectProfilesForAttributes` on API 33+ and therefore sees 8 channels. **Whether an 8-channel PCM `AudioTrack` goes to the direct output or ends up in the stereo mixer is the biggest uncertainty in this report (uncertain).**
- **Manual surround setting:** this only enables formats the HAL knows. TrueHD and DTS do not suddenly get passed through because of it [17].
- **Server audio transcode:** EAC3 and AC3 are capped at 6 channels (`EncodingHelper`), and Atmos is always lost [13]. If you pick a different track with `AudioStreamIndex` in PlaybackInfo, the server works with that track [14].
- **Jellyfin's FFmpeg AAR:**
  - Contains decoders for ac3, eac3, dca (incl. DTS-HD MA lossless) and truehd/mlp.
  - The latest version is `1.9.0+1` (2025-12-29), built against Media3 1.9. jellyfin-androidtv runs it in practice with Media3 1.11.1 [17].

**Advice:**

| Source track | Route 1 (default) | Route 2 | Last resort |
|---|---|---|---|
| EAC3 (JOC), AC3 | passthrough | FFmpeg to PCM (e.g. BT headphones) | – |
| TrueHD Atmos (85) | an EAC3-JOC track in the same language if there is one, otherwise AC3 | **FFmpeg to 7.1 PCM** (lossless bed, no Atmos), behind a setting until the test passes | server transcode to EAC3 5.1 (DirectStream) |
| DTS / DTS-HD MA / DTS:X (≈175) | EAC3 or AC3 track in the same language | FFmpeg to 5.1/7.1 PCM | server to EAC3 5.1 |
| AAC, FLAC, PCM | platform decoder | – | – |

Implementation:
- Decide which audio track it will be before PlaybackInfo, and send `AudioStreamIndex` along. That way the server direct plays the file and ExoPlayer selects the same track locally.
- **DeviceProfile:** declare TrueHD and DTS as "playable" only when route 2 is on. Otherwise the server transcodes the audio to EAC3. Compare jellyfin-androidtv PR #5822 (2026-09): TrueHD was always advertised there and silently became a stereo downmix.
- `DefaultRenderersFactory.setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)`: platform/passthrough first, FFmpeg as fallback.

### 3.4 Frame rate and HDR mode

**Measured:**
- 19 display modes: 2160p and 1080p at 23.976, 24, 25, 29.97, 30, 50, 59.94 and 60 Hz, plus 720p at 50/59.94/60 Hz.
- **Each mode is in its own group, with `alternativeRefreshRates=[]`.** So seamless switching can never happen.
- `mModeSwitchingType: SWITCHING_TYPE_WITHIN_GROUPS`: the setting is effectively at "Seamless" now, and therefore never switches.
- The active mode is 2160p 59.94 Hz. ALLM is supported.

**Findings:**
- Media3 calls `Surface.setFrameRate(..., FIXED_SOURCE)` with strategy `ONLY_IF_SEAMLESS` [11]. On this device that does nothing.
- For movies, Google recommends `CHANGE_FRAME_RATE_ALWAYS` if the user has chosen "Always/Non-seamless", or `preferredDisplayModeId` for a "heavy" mode switch on TV [18].
- jellyfin-androidtv picks a `Display.Mode` itself [1].

**Advice:**
- When opening the player, before `prepare()`, set `window.attributes.preferredDisplayModeId` to the mode with the current resolution and the refresh rate that matches the content:
  - 23.976 → 23.976
  - 24 → 24
  - 25 → 50 (or 25)
  - 29.97 → 59.94
- Restore the mode when leaving the player.
- Set `setVideoChangeFrameRateStrategy(OFF)` in Media3, so it does not happen twice.
- The frame rate comes from Jellyfin `MediaStream.realFrameRate`/`averageFrameRate`, so you know it before playback.
- Each switch gives 1–3 s of black screen (HDMI resync, uncertain how long on the LG). That hurts the "≤ 3 s to first frame" budget. Measure it separately.
- **(uncertain)** Whether `preferredDisplayModeId` also works when the user has set "Seamless" has to be shown by testing. That is why the *Non-seamless* setting is in §4.3.
- The app does not need to switch **HDR mode** itself. The platform switches the HDMI output based on the HDR metadata and the decoder (DV decoder to DV output). Use a `SurfaceView`, not a `TextureView`.

### 3.5 Tunneled playback

**Measured:**
- All `c2.amlogic` video decoders report `tunneled-playback`, including DV (except MPEG-4).
- The audio HAL has a "tunnel direct output" with `HW_AV_SYNC` for PCM up to 7.1, AC3, EAC3 and EAC3-JOC.

**Findings:**
- There is no primary source on Chromecast-specific tunneling bugs with Media3.
- ExoPlayer maintainers see little gain and do see device-specific seek and stability problems [19].
- Tunneling rules out playback speed and audio processing.
- jellyfin-androidtv and Wholphin do not use it.
- An undated Kodi report mentions about 1 s of A/V offset with passthrough on the Chromecast [17].

**Advice:**
- Off by default; only a debug switch.
- Only test it if 4K/DV drops frames without tunneling (`dumpsys gfxinfo` is not usable for that, so use `DecoderCounters.droppedBufferCount`).

### 3.6 Network and buffering

**Measured:**
- Wi-Fi 5 (802.11ac) on 5 GHz (5240 MHz), RSSI −59 dBm.
- Link speed Rx 780 / Tx 195 Mbps; max. 866 Mbps (so 2×2 at 80 MHz).
- There is currently no Ethernet interface.

**Findings:**
- Google's own Ethernet adapter is **10/100**. 117 Mbps does not fit through it [20].
- Gigabit USB-C adapters reach about 300–350 Mbps in practice [20] (forum level).
- Media3 `DefaultLoadControl` 1.11.1 [11]:
  - 50 s min/max, but with a byte target of about 131 MB for video.
  - Size takes precedence over time, so at 117 Mbps you keep only about 9 s of buffer.
  - The buffer lives on the Java heap (256 MB here).
- Media3 1.11.0 falls back to byte limits when the heap is tight [11].
- jellyfin-androidtv only changes the durations, not the number of bytes [21].
- MKV without Cues cannot be seeked in Media3 (`Unseekable`). 1.11.1 fixes "Tracks after Clusters" [11].

**Advice:**
- **`DefaultLoadControl`:**
  - `setTargetBufferBytes(100 MB)`, in line with `playback.md`
  - `setBufferDurationsMs(min 30_000, max 50_000, forPlayback 2_500, afterRebuffer 5_000)`
  - `setPrioritizeTimeOverSizeThresholds(false)`
  - back buffer 0
  - A slightly higher `forPlayback` prevents an immediate rebuffer on remuxes of 80–117 Mbps.
- **HTTP:** `OkHttpDataSource` with the OkHttp client the app already uses (via the SDK/Coil), or `DefaultHttpDataSource`. With a single progressive range stream, the choice makes little difference [22].
- **Quality setting** with `maxStreamingBitrate`: default **unlimited or 200 Mbps**, never lower than 120 Mbps on LAN. Otherwise the server transcodes with `ContainerBitrateExceedsLimit`.
- **Network test:** test 9 on Wi-Fi and on a gigabit adapter.

### 3.7 Subtitles

**Findings** [11][8]:
- Media3 1.11 parses SSA/ASS, SRT, VTT, TTML, **Tx3g (mov_text)**, **PGS**, **VobSub/DVDSUB** (since 1.6) and DVB, by default during extraction.
- ASS is simplified: position, color and outline yes, but no animations, karaoke or `\t`.
- There are no reports of PGS performance problems in 4K. There are open bugs, though: outline-only rendering (#1006) and multiple PGS at once.
- **ass-media 0.5.1** (MIT, libass 0.17.5):
  - armeabi-v7a costs about 2.1 MB `libass.so` + 0.85 MB `libc++_shared`.
  - Render modes: `OVERLAY_OPEN_GL` on its own thread, works with HDR/DV. `maxRenderPixels` can cap rendering at 1080p.
  - jellyfin-androidtv removed it in 2025 over memory crashes and now has it back as an opt-in.

**Advice:**
- **SubtitleProfiles:**
  - `Embed` for SRT/SUBRIP, ASS/SSA, PGSSUB, DVDSUB, mov_text and VTT.
  - `External` for SRT and VTT.
  - `Hls` for VTT (when transcoding).
  - Nothing as `Encode`, except behind the "Burn in ASS" setting.
- Why: otherwise the server falls back to Encode, and an Encode-only subtitle forces a transcode [14].
- Phase 2: Media3 only. libass only in a later phase, as an opt-in with `OVERLAY_OPEN_GL` and `maxRenderPixels` = 1920×1080, and only after a memory measurement on Friends 1x1 and a 4K movie with ASS.

### 3.8 DeviceProfile and PlaybackInfo

**Findings** [14][13]:
- **StreamBuilder 10.11.11:**
  - An unknown bitrate counts as 40 Mbps.
  - **DirectStream (remux) is only possible if the remaining reasons are audio, container or codec tag.** Any video reason (VideoRangeType, VideoLevel, VideoProfile, BitDepth…) means a full transcode.
  - Never embed subtitles with a ts or mp4 transcode.
- **`TranscodeReasons`** is a flags enum: ContainerNotSupported, VideoCodecNotSupported, AudioCodecNotSupported, SubtitleCodecNotSupported, VideoRangeType, VideoLevel, AudioChannels, ContainerBitrateExceedsLimit, and so on.
- **jellyfin-androidtv builds the profile like this** (study only the approach, the code is GPL):
  - `buildDeviceProfile {}` with one broad DirectPlay rule.
  - CodecProfiles per codec from `MediaCodecList`: profiles, levels per profile, max. width and height.
  - An "unsupported range types" set as `VIDEO_RANGE_TYPE notEquals "a|b"` with `applyConditions inCollection`, a deliberate workaround for StreamBuilder.
  - Three HLS transcode profiles: TS, fMP4 with HEVC/H.264, and audio-only.
  - `KnownDefects` has nothing for the Chromecast.

**Advice for JellyCast's profile:**
- **DirectPlay containers:** mkv, mp4/m4v, mov, ts/m2ts, webm.
- **Video:** hevc (Main, Main10, level ≤ 5.1, ≤ 4096×2176), h264 (High, level ≤ 5.1, 8-bit; 4K up to 30 fps), vp9, mpeg2video and mpeg4. No vc1 and no av1: those are software-only.
- **VideoRangeType:** see §3.2. HDR10+ as supported, because it plays as HDR10.
- **Audio:** aac, mp3, flac, alac, opus, vorbis, pcm and ac3/eac3, plus truehd/dts only when route 2 is on. Channels ≤ 8.
- **Transcoding:**
  - HLS fMP4 with HEVC + AAC/EAC3, and HLS TS with H.264 as fallback.
  - Audio transcode codec `eac3,ac3`, so audio-only transcodes stay passthrough.
- **Bitrates:** `maxStaticBitrate` and `maxStreamingBitrate` ≥ 200 Mbps.
- **Pitfalls:**
  - Always log `playMethod` + `transcodeReasons` (already in `playback.md`).
  - Bug #15988 (10.11): sometimes a video transcode where only audio was needed.
  - Bug #16687: DOVIWithHDR10Plus becomes SDR.
  - Always send `AudioStreamIndex` and `SubtitleStreamIndex` along.

## 4. What this means for phase 2

### 4.1 Dependencies

| Coordinate | Version | Why |
|---|---|---|
| `androidx.media3:media3-exoplayer` | 1.11.1 | engine |
| `androidx.media3:media3-session` | 1.11.1 | remote control/MediaSession |
| `androidx.media3:media3-ui` | 1.11.1 | `PlayerView` in phase 2 (see `playback.md`) |
| `androidx.media3:media3-datasource-okhttp` | 1.11.1 | optional; only if OkHttp is already on the classpath |
| `org.jellyfin.media3:media3-ffmpeg-decoder` | 1.9.0+1 | decode AC3/EAC3/TrueHD/DTS; the device has no decoders of its own for these. GPL-3.0; built against Media3 1.9, works with 1.11.1 in jellyfin-androidtv. Set `abiFilters "armeabi-v7a"` (and possibly `arm64-v8a`), so x86 is not included. |
| *later:* `io.github.peerless2012:ass-media` | 0.5.1 | full ASS; only after a memory measurement |
| *not:* libmpv, libVLC | – | no HDR on Android, 23–40 MB, second path |

### 4.2 Settings in the app

- `DefaultRenderersFactory`: `EXTENSION_RENDERER_MODE_ON`, `setEnableDecoderFallback(true)`, and a custom video renderer for DV p7 to HEVC.
- `DefaultLoadControl` as in §3.6. Tunneling and audio offload off.
- `setVideoChangeFrameRateStrategy(OFF)` plus our own `preferredDisplayModeId`.
- A `SurfaceView`, via `PlayerView` or `ContentFrame` with `SURFACE_TYPE_SURFACE_VIEW`.
- Audio track choice before PlaybackInfo (§3.3), and send `AudioStreamIndex`/`SubtitleStreamIndex` along.
- A debug overlay or log with: chosen decoder (`onVideoDecoderInitialized`), `Format.codecs`, HDR type, audio route (passthrough, PCM or FFmpeg), `AudioCapabilities` and dropped frames.

### 4.3 What the user sets up themselves

1. **Chromecast → Display & Sound → Advanced display settings → Match content frame rate: *Non-seamless*.**
2. **Dynamic range: turn on "Match content dynamic range"** (the name differs per firmware; uncertain). Right now SDR also goes to the TV as DV (measured).
3. **Surround sound: leave on Auto** (it already is). Manual adds nothing.
4. **LG:**
   - Sonos Arc on HDMI 2 (eARC). eARC on.
   - Digital sound out to *Passthrough* (or *Auto*).
   - Chromecast port with "HDMI Ultra HD Deep Colour" on.
5. **Sonos app:** check under "Audio format" what is coming in (Dolby Atmos (DD+), Multichannel PCM 7.1).
6. **Network:** preferably a gigabit USB-C hub with PD. **Not** the Google adapter (100 Mbit), otherwise a quality limit.

### 4.4 What must be tested on the device

Use the codec test set in `NOTES.md` and add:
- **#8 and #9 (p7):** play them via HEVC (HDR10) and via DV. Check whether it is stable, what the TV badge (DV or HDR) says, and whether frames are dropped.
- **#7 (p8.6):** does it play via the DV decoder? Otherwise via HEVC.
- **#4 and #6:** HDR10+ and DV+HDR10+, check the badge.
- **#3, #11 and #12:** TrueHD/DTS-HD via FFmpeg to PCM.
  - Does the Sonos app show 7.1 PCM, or stereo?
  - `adb shell dumpsys media.audio_flinger`: which output thread, and which channel mask?
  - CPU usage (`top`) and A/V sync.
- **EAC3-JOC (Atmos):** does Atmos arrive at the Sonos? Is the A/V sync good?
- **Refresh switch:** 23.976 fps content with the setting on "Seamless" and on "Non-seamless". Check the mode (`dumpsys display | grep mActiveModeId`) and measure the time to first frame.
- **#9 (117 Mbps):** Wi-Fi versus Ethernet hub. Count the rebuffers and check the heap (`dumpsys meminfo`).
- **A PGS 4K movie and Friends (ASS):** are the subtitles correct, with no stale subtitle frames left on screen?

## 5. Results of the adb measurements (2026-10-08)

| Topic | Measurement |
|---|---|
| Firmware | `UTTC.250917.004`, SoC `AMLS905D3`, `armeabi-v7a`, MemTotal 1.98 GB, heapgrowthlimit 256 MB, `ro.config.low_ram` empty |
| HEVC `c2.amlogic.hevc.decoder` | Main, Main10, Main10HDR10, Main10HDR10Plus @ level 5.1; max. 4096×2160 at 2,073,600 blocks/s (4K60); 9 instances; measured 103 fps at 2160p; tunneled-playback |
| AVC `c2.amlogic.avc.decoder` | Baseline/Main/High/ConstrainedHigh @ 5.1 (**no High10**); 4K up to 30 fps; tunneled |
| DV `dvhe` | profiles **p4 (DvheDtr), p5 (DvheStn), p8 (DvheSt)** @ Uhd60; **no p7 (DvheDtb)**; 1 instance; 4K60; tunneled |
| DV `dvav` | p9 (DvavSe) @ Uhd60; 4K30 |
| VP9 | profile 0/2/2HDR/2HDR+/3 @ 5.1, 4K; tunneled |
| MPEG-2 / MPEG-4 | up to 1920×1088; MPEG-2 tunneled |
| AV1 | only `c2.android.av1.decoder` (software) |
| Audio decoders | software only: AAC, MP3, FLAC, Opus, Vorbis, AMR, G.711, raw PCM. **No AC3/EAC3/DTS/TrueHD** |
| HDMI audio | PCM16 2/5.1/7.1 @ 32–48 kHz; AC3; EAC3; EAC3-JOC; IEC61937. Primary mixer stereo 48 kHz only. Direct output and "tunnel direct output" (HW_AV_SYNC) with 7.1 PCM and AC3/EAC3/JOC |
| Surround settings | `encoded_surround_output=0` (Auto), `enabled_formats=18,5,6` |
| Display | EDID "LG TV SSCR2", manufacture date 2022; HDR types DV, HDR10, HLG; max. luminance 500; no HDR10+ (`hdr_cap`); DV VSVDB v2, DV-Std (RGB 8-bit) and LL 4:2:2 12-bit |
| Display modes | 2160p and 1080p @ 23.976/24/25/29.97/30/50/59.94/60, 720p @ 50/59.94/60; **each mode in its own group, no alternative refresh rates**; active 2160p59.94; ALLM yes |
| Mode switching | `SWITCHING_TYPE_WITHIN_GROUPS` (Seamless), `match_content_frame_rate` not set |
| HDR output | `hdr_conversion_mode=2` (system); during YouTube SDR: `hdmi_hdr_status=DolbyVision-Std` |
| CEC | TV OLED55CS6L (0x0000), Chromecast 0x1000 (HDMI 1), Sonos Arc 0x2000 (HDMI 2), system audio on |
| Wi-Fi | 802.11ac, 5240 MHz, RSSI −59, Rx 780 / Tx 195 Mbps, max. 866; no Ethernet interface |

## 6. Open questions and uncertainties

1. **Multichannel PCM:** does 7.1 PCM from FFmpeg go via the direct output to the Sonos, or does the stereo mixer downmix it? This determines whether route 2 is usable. Only a test can settle it.
2. **p7 via the dvhe decoder:** does Amlogic play MEL/FEL single-track reliably as DV, even though p7 is not reported? And how does the HEVC route handle the EL NALs?
3. **p8.6 via the dvhe decoder:** does it work without artifacts?
4. **`preferredDisplayModeId`:** is it respected when the user setting is "Seamless"? And how long does the HDMI resync take on the LG?
5. **Atmos:** does EAC3-JOC arrive at the Arc as Atmos? Is the A/V sync good with passthrough? There is an undated report of an offset.
6. **eARC and TrueHD:** does the LG CS pass TrueHD or 7.1 PCM through via eARC? rtings data for the CS was missing. The HDMI profile suggests it does for PCM.
7. **FFmpeg AAR:** `1.9.0+1` is built against Media3 1.9. New Media3 versions may break it. Alternative: build it ourselves following the Media3 docs.
8. **Server version:** Jellyfin has been renumbered. v12.0 came out on 2026-09-08 and v12.2 on 2026-10-05 [14]. This advice applies to 10.11.11 and SDK 1.8.12. On an upgrade, recheck the DOVI and StreamBuilder logic.
9. **The 2×2 Wi-Fi throughput** in practice (is 117 Mbps reached?) has not been measured. There is only the link speed.
10. **Android 14 on the Chromecast:** in 2025 there were reports of DV freezes. The current build `UTTC.250917.004` is newer. Whether that has been fixed is uncertain [1].

## 7. Sources

1. jellyfin-androidtv PR #3639 (libVLC removed), 2024-06-02: https://github.com/jellyfin/jellyfin-androidtv/pull/3639. Plus code (`deviceProfile.kt`, `VideoManager.java`, `libs.versions.toml`) at commit `09c912d`, read 2026-10-08. Findroid `1b48de9` and Wholphin `e23c446` (`PlayerFactory.kt`, `PlaybackViewModel.kt`), read 2026-10-08. PR #5822 (2026-09-14), #5868 (2026-10-07), #3590 (2024-05). 9to5google on Android 14 on the Chromecast, 2025-03-13: https://9to5google.com/2025/03/13/chromecast-google-tv-android-14-update-problems/
2. Maven Central metadata and AAR sizes (libmpv, libvlc-all, media3-ffmpeg-decoder, ass-media), retrieved 2026-10-08.
3. mpv-android #1022 (sfan5: no native HDR), 2024-12-06: https://github.com/mpv-android/mpv-android/issues/1022
4. mpv-android #874 "Native HDR support", comment 2025-12-19: https://github.com/mpv-android/mpv-android/issues/874. Plus #717 (DV), 2023-08.
5. FFmpeg trac #11676, MediaCodec on Amlogic, 2025-07: https://ffmpeg.org/pipermail/ffmpeg-trac/2025-July/074088.html
6. Kodi 21 Omega release (DV p7 to 8.1), 2024: https://kodi.tv/article/kodi-21-0-omega-release/
7. Smartworld on frame-rate matching on Google TV, ca. 2022: https://www.smartworld.it/news/frame-rate-matching-google-tv.html
8. libass-android / ass-media README, v0.5.1 (2026-08-11): https://github.com/peerless2012/libass-android. jellyfin-androidtv #4876 (2025-08-24) and #5032 (2025-10-19).
9. Plex forum, ExoPlayer fork (2022): https://forums.plex.tv/t/exo-player-issues/779877. Google TV stutters (2025-02): https://forums.plex.tv/t/is-the-google-tv-plex-app-just-broken/904401
10. Just Player README (2026): https://github.com/moneytoo/Player
11. androidx/media `RELEASENOTES.md` (1.4 through 1.11.1) and source code at tag 1.11.1: `MediaCodecUtil.java`, `DefaultLoadControl.java`, `MatroskaExtractor.java`, `DefaultSubtitleParserFactory.java`. https://github.com/androidx/media/blob/release/RELEASENOTES.md. Plus issue #3377 (2026-08-17).
12. MakeMKV forum, Chromecast DV/audio tests, 2021-02-23 and 2021-04-11: https://forum.makemkv.com/forum/viewtopic.php?p=105717, https://forum.makemkv.com/forum/viewtopic.php?p=107988, https://forum.makemkv.com/forum/viewtopic.php?p=115544
13. Jellyfin `release-10.11.z`: `MediaStream.cs` (range types) and `EncodingHelper.cs` (DV stripping, channel limit), read 2026-10-08: https://github.com/jellyfin/jellyfin/blob/release-10.11.z/MediaBrowser.Model/Entities/MediaStream.cs. jellyfin-androidtv issue #5316, 2026-01-03.
14. Jellyfin v10.11.11: `StreamBuilder.cs`, `TranscodeReason.cs`, `VideoRangeType.cs`: https://github.com/jellyfin/jellyfin/blob/v10.11.11/MediaBrowser.Model/Dlna/StreamBuilder.cs. Issues #16687 (2026-04-23), #15988, #17198 (2026-06-27). Releases: https://github.com/jellyfin/jellyfin/releases. jellyfin-web `browserDeviceProfile.js` (release-10.11.z).
15. TechRadar, LG CS review: https://www.techradar.com/reviews/lg-cs-65-inch-oled-tv-review. John Lewis "OLED65CS6LA (2022)". fullspecs.net model code list. Plex forum, LG C2 without DTS: https://forums.plex.tv/t/lg-c2-no-sound-with-dts-ma/824781
16. Sonos, supported formats: https://support.sonos.com/en-us/article/supported-home-theater-audio-formats. ChannelNews, DTS on Sonos, 2022-11. Xataka, multichannel PCM on the Arc, ca. 2020/21.
17. Google, surround settings on the Chromecast: https://support.google.com/chromecast/answer/10110321. AudioManager API diff (Android 12). `jellyfin-androidx-media/build.sh`. Kodi forum tid 377074 (undated).
18. Android Developers, frame rate guide, updated 2026-10-01: https://developer.android.com/media/optimize/performance/frame-rate. 9to5google, 2022-10-20: https://9to5google.com/2022/10/20/chromecast-google-tv-frame-rate-switching/
19. ExoPlayer #1688 (tunneling): https://github.com/google/ExoPlayer/issues/1688
20. 9to5google on the Ethernet adapter, 2020-09-30: https://9to5google.com/2020/09/30/chromecast-google-tv-ethernet-adapter/. Channels community, 2021-06-26: https://community.getchannels.com/t/chromecast-with-google-tv-third-party-ethernet-really-improves-performance/27919. Jellyfin forum on 4K remux buffering, 2025-01-28.
21. jellyfin-androidtv PR #5515 (buffer presets), 2026-09: https://github.com/jellyfin/jellyfin-androidtv/pull/5515
22. Android Developers, Media3 network stacks, updated 2026-09-08: https://developer.android.com/media/media3/exoplayer/network-stacks
