# Playback on other Android TV devices: what we learn from Moonfin and jellyfin-androidtv

Date: 2026-10-10. Goal: JellyCast now plays well on one chain (Chromecast with Google TV 4K → LG OLED → Sonos Arc). This analysis works out what has to change so it also works on other boxes (Shield, Google TV Streamer, Fire TV), TVs with built-in Google TV (Sony, TCL, Philips, often MediaTek), other TVs (no HDR or DV, 1080p, 50/60 Hz) and other audio (AVR with DTS/TrueHD, eARC soundbar, stereo TV, Bluetooth).

Sources, only the source code was read, nothing was built:

- **Moonfin** ([Moonfin-Client/Moonfin-Core](https://github.com/Moonfin-Client/Moonfin-Core), commit `867b650`, 2026-10-09). A Flutter app, but playback on Android is native Kotlin with Media3 1.10.1 in `packages/moonfin_native_video/android/`. In this document: **N** = `…/kotlin/org/moonfin/nativevideo/`, **A** = `android/app/src/main/kotlin/org/moonfin/androidtv/`, **L** = `lib/playback/`.
- **jellyfin-androidtv** ([jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv), commit `09c912d`, 2026-10-06). Paths relative to `app/src/main/java/org/jellyfin/androidtv/`. Earlier comparison: `jellyfin-androidtv-comparison.md`.
- **JellyCast**: paths relative to `app/src/main/java/nl/jellycast/tv/`.

## 1. In short

- **Moonfin is much further along on playback than the official client.** It has recovery per error type, HDMI hotplug with debounce, a check of what the display can do, and a Dolby Vision converter. The official client is simpler: everything comes from `MediaCodecList`, with an override per format in the settings and one fixed retry ladder.
- **Our foundation is good.** Our profile is a pure function, it is measured again for every playback plan, decoder and display are intersected for HDR10, and we set the display mode ourselves. The weak spots are in three things:
  1. Errors during playback: decoder and audio sink errors have no fallback.
  2. Audio outside the HDMI passthrough chain: stereo, Bluetooth, DTS-HD and the number of channels.
  3. Settings with which you can overrule a wrong measurement.
- **One item also pays off directly on the Chromecast:** Moonfin converts DV profile 7 to profile 8.1 during playback (via libdovi). We now play p7 as HDR10 and so lose Dolby Vision (23 items).

## 2. How the three clients do it

| Topic | JellyCast now | jellyfin-androidtv | Moonfin |
|---|---|---|---|
| Video capabilities | `MediaCodecList`, hardware only; profiles yes, **levels no**; max. resolution only for HEVC | Profiles **and levels** (`MediaCodecQuery.kt:38-51`); level override in the settings | Profiles, levels and resolution per codec; software does not count for High10; MediaTek High10 excluded (`A/MediaCodecCapabilities.kt:23-42`) |
| HDR of the display | `display.mode.supportedHdrTypes` (current mode) ∩ decoder, only for HEVC HDR10/HLG | Not used (only in the diagnostics report) | Display-wide `hdrCapabilities`; "no answer" is **not** SDR; retry on a display change (`A/DisplayCapabilities.kt:54-68`, `L/display_hdr_probe.dart`) |
| Dolby Vision p7 | As HDR10 via the HEVC decoder (`PlaybackPreparer.kt:90-91`) | Only with a p7 decoder with 2 instances; otherwise remux/strip; Shield via KnownDefects | **Convert to p8.1** with libdovi in an extractor wrapper; STRIP (HDR10) as fallback (`N/DoviCompatExtractorsFactory.kt`) |
| Passthrough detection | `AudioCapabilities`, `AudioAttributes.DEFAULT`; AC3/EAC3/DTS/TrueHD; no DTS-HD | `AudioCapabilities` with a 2-channel test format | Per route (HDMI/ARC/eARC/BT) and per encoding with 7.1/5.1/2.0 test formats; eARC for HD audio (`A/AudioCapabilities.kt:63-430`) |
| Output changes during playback | Nothing | `setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)` | Same, plus an `AudioDeviceCallback` with 4 s debounce (at most 12 s), a settle window of 6 s on fewer capabilities, and automatic resume after an HDMI flap |
| Decoding surround locally | FFmpeg for what the platform lacks (`EXTENSION_RENDERER_MODE_ON`) | `ON`, setting `PREFER` | Surround always via FFmpeg, except with a real Dolby decoder: generic TV SoC decoders mix down to stereo (`Media3VideoView.kt:404-480`) |
| Downmix / channels | Transcode fixed at 6 channels; `maxPcmChannels` measured but not used | Setting "Downmix to stereo" (`AUDIO_CHANNELS ≤ 2`) | `ChannelMixingAudioProcessor` (BS.775, up to 12 channels), live on/off; setting for max. channels |
| Choosing the display mode | Same resolution, Hz = fps or 2× fps; also forces with SEAMLESS_ONLY | Setting off / TV scales / box scales; exact, ×2, ×2.5; not smaller than the video | Setting likewise; integer multiples only, no 60 Hz for 24p; resolution may change too (4K24 only on 1080p); unit tests with real mode lists (`N/DisplayModeChooser.kt`) |
| After the mode change | Wait until the mode is active + 2.5 s (max. 3 s) | Manual "video start delay" | No waiting; any error within 10 s after the change → prepare again, at most 3× |
| Errors during playback | Source error on direct play → 1× remux; everything else → error screen | Counter: 1st error → without direct play, 2nd → full transcode, 3rd → stop; reset after 30 s stable | Per error type one step per source: mode retry → decoder again → offload off → tunneling off → IEC off → stereo downmix → server transcode (audio or container) |
| Buffer | `DefaultLoadControl` (default) | Default, or larger via a setting | `targetBufferBytes = maxHeap / 3` (max. 384 MB) |
| Tunneling / offload | Both off | Tunneling off; **offload on** | Tunneling off by default and never with lossless audio; offload only for audio-only |
| Device quirks | None | Fire TV (DV+HDR10+), Shield (p7 not reported) | Same plus MTK High10, Google sw FLAC, FFmpeg stereo TrueHD (androidx/media#1843), Fire TV resume, Tensor JOC |
| User overrides | Quality, burn in ASS, subtitle style | Per HDR format and per bitstream codec AUTO/ON/OFF, downmix, FFmpeg preference, levels, refresh behavior | Same, more extensive, plus the choice of the p7 route and "detect again" |

## 3. What can go wrong for us on another device

From an inventory of our playback code, in order of risk.

1. **A renderer error has no fallback** (`feature/player/PlayerViewModel.kt:72-82`). A decoder that does not start, or an AVR that refuses a passthrough format (`AudioSink.InitializationException`), goes straight to the error screen. No fallback to PCM, HEVC or transcode.
2. **Stereo TV and Bluetooth.**
   - EAC3/AC3 always go direct, because FFmpeg can decode them (`DeviceProfileFactory.kt:113-124`). That is fine.
   - But the transcode always asks for 5.1 (`:61`, hardcoded, "the Sonos").
   - Without passthrough, the track selection does not look for an EAC3/AC3 track: TrueHD or DTS stays selected, and the server then has to transcode (`AudioTrackSelector.kt:42`).
3. **Nothing is registered when the output changes during playback** (HDMI → BT, AVR off). Only the next plan measures again.
4. **Audio is measured differently from how the player plays.**
   - We measure with `AudioAttributes.DEFAULT` and without a route (`AndroidCapabilitiesReader.kt:62`), while the player uses `USAGE_MEDIA/MOVIE`.
   - DTS-HD (`ENCODING_DTS_HD`) and DTS:X (`ENCODING_DTS_UHD_P2`) are not measured, nor is the number of channels per encoding.
5. **Dolby Vision is not intersected with the display** (`AndroidCapabilitiesReader.kt:82`).
   - A DV box connected to an HDR10 TV gets DV direct play, and then the box decides. With p5 that can give a green/purple picture.
   - On a device that does report p7, p7 goes to the DV decoder, even on a TV without DV.
6. **HDR is read from the current mode** (`:140`). According to Moonfin, on a DV TV in an SDR mode that returns an empty list. On the Chromecast the measurement is correct (measured), but it is fragile.
7. **HLG is assumed as soon as there is a Main10 decoder** (`:127`). That is not a measurement.
8. **H.264, VP9 and AV1 have no resolution, level or HDR conditions.**
   - A weak TV SoC with AVC up to 1080p gets 4K AVC as direct play.
   - AV1 HDR goes to an SDR TV without tone mapping.
9. **The display mode is also forced with SEAMLESS_ONLY** (`FrameRateMatching.kt:80`). On the Chromecast this is deliberate: the system setting there is seamless-only (`NOTES.md:9`). On other devices it ignores what the user chose.
10. **The fixed 2.5 s / 3 s after a mode change** was measured on Amlogic + LG HDCP 2.2 (`DisplaySettle.kt`).
    - Moonfin measured 1.8–3.2 s of HDMI flap; that confirms the order of magnitude.
    - But with an AVR in between it can take longer, and on a TV with built-in Google TV there is no HDMI.
11. **The FFmpeg codec list is fixed** and is not checked with `FfmpegLibrary.supportsFormat` (`AndroidCapabilitiesReader.kt:39-40`).
12. **Generic platform AC3/EAC3 decoders.** With `EXTENSION_RENDERER_MODE_ON` the platform goes before FFmpeg. On a TV with a generic AC3 decoder, 5.1 can end up as stereo. Moonfin saw that on "TV SoCs routinely".
13. **No settings to overrule a measurement** (HDR format, passthrough codec, downmix). Frame rate matching is only behind a debug intent.

## 4. Candidates

Average effort: S = a few hours, M = a day, L = several days. "CC" = noticeable on the Chromecast.

### A. Gains on the Chromecast and elsewhere

| # | What | Source | Why | Effort |
|---|---|---|---|---|
| A1 | **Recovery per error type.** Renderer and audio sink errors get a ladder instead of the error screen: audio sink → without passthrough (FFmpeg PCM) → server transcodes audio; video decoder → remux → transcode. Network error → simply retry. One attempt per step per plan; the counter goes back to 0 after 30 s of stable playback. | jellyfin `PlaybackController.java:256-279,523-524`; Moonfin `Media3VideoView.kt:1174-1212,4305-4656` | The biggest risk on an unknown chain (item 1). On the CC it also catches the rare decoder error. Without a fallback the only way out now is "Retry", and that does the same thing again. | M |
| A2 | **DV p7 → p8.1** with an extractor wrapper that converts the RPU (NAL 62) via libdovi, discards the EL (NAL 63) and sets `dvhe.07` → `dvhe.08`. If there is no RPU or libdovi does not load: STRIP = our current HDR10 route. Only if there is a p8 decoder **and** the display reports DV. | Moonfin `N/DoviCompatExtractorsFactory.kt`, `N/DoviRpu.kt`, `src/main/cpp/dovi_jni.c` | CC: 23 items get DV instead of HDR10, with no work for the server (like Kodi v21 and Infuse-like). | L |
| A3 | **Cap the LoadControl at `min(default, maxHeap / 3)`** | Moonfin `Media3VideoView.kt:1648-1680` | Complies with `playback.md` (default or lower). CC (256 MB heap): ~85 MB instead of 125 MiB, so less chance of OOM with 4K remux. Other devices follow their own heap. First measure whether rebuffers occur at 117 Mbps. | S |
| A4 | **`setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)`**, turned off on stop | both; Moonfin lines 3019 and 3035-3040 (crash when restoring the mode on an idle player) | Corrects the selection when the output changes during playback (BT on, AVR off). On the CC it happens when the Sonos restarts. | S |

### B. Needed for other devices (nothing changes on the CC)

| # | What | Source | Why | Effort |
|---|---|---|---|---|
| B1 | **Measure audio the way the player plays**: the same `AudioAttributes` as the player, the passthrough test with a 6- and 8-channel `Format`, plus `ENCODING_DTS_HD` and `ENCODING_DTS_UHD_P2`. The route (HDMI/ARC/eARC/BT/built-in) goes into `DeviceCapabilities`. | Moonfin `A/AudioCapabilities.kt:63-430`; jellyfin only tests 2 channels (weak) | AVR with DTS-HD/TrueHD, eARC versus ARC (ARC cannot do TrueHD), BT. The pure function stays a pure function: only the reader changes. | M |
| B2 | **Channels and track selection per output.** `maxAudioChannels` in the transcode = `min(6, maxPcmChannels or route)`, so 2 for BT/stereo. Track selection also prefers EAC3/AC3 over TrueHD/DTS without passthrough (decoding locally is cheaper than having the server transcode). | jellyfin "Downmix to stereo"; Moonfin max. channels | Stereo TV and BT (item 2). | S |
| B3 | **Intersect DV with the display**, and read the display-wide `hdrCapabilities` instead of only the current mode. "No answer" = keep the previous measurement, not SDR. | Moonfin `A/DisplayCapabilities.kt:54-68`, `L/display_hdr_probe.dart` | Box connected to an HDR10 or SDR TV: then the server tone maps (neither client does that well for HDR10; Moonfin only for DV). At Moonfin, a sleeping AVR wiped the DV measurement. | S |
| B4 | **Overrides in Settings**, one "Advanced playback" screen: per HDR format (HDR10, HDR10+, DV) and per passthrough codec (AC3, EAC3, DTS, DTS-HD, TrueHD) Auto/On/Off, "Downmix to stereo", and "Adjust frame rate" (now debug only). The override can allow more or less than the measurement. | jellyfin `HdrFormat.kt`, `BitstreamAudioFormat.kt`; Moonfin `user_preferences.dart` | The only fallback for a device that reports something wrong (HDMI sinks often lie). Checked against `product-principles.md`: it is behind one button and changes nothing about browsing. | M |
| B5 | **Choose the display mode like Moonfin**: only integer multiples of the standard rates, no "closest"; the resolution may change too if a rate only exists in another resolution (not smaller than the video, min. 720p); `Surface.setFrameRate(…, CHANGE_FRAME_RATE_ALWAYS)` as an extra hint; unit tests with mode lists from real TVs. Respecting SEAMLESS_ONLY is a choice for the user (see §5). | Moonfin `N/DisplayModeChooser.kt:20-97`, `Media3VideoView.kt:2840-2867` | TVs that only have 4K24 in 1080p; 50 Hz content on 50 Hz TVs. `chooseDisplayMode` is already a pure function, so easy to test. | S–M |
| B6 | **Wait smarter after the mode change.** Wait for a stable audio route (`AudioDeviceCallback`, no new events for ~1 s) and the active mode, with a ceiling of 4 s, instead of a fixed 2.5 s. In addition, Moonfin's fallback: an error within 10 s after the change → prepare again at the same position (max. 2×). | Moonfin `MainActivity.kt:222-227`, `Media3VideoView.kt:640-648` | AVR in between (longer), built-in TV (shorter). On the CC, measure again that the sound does not fall into the HDCP resync. | M |
| B7 | **Codec conditions for weaker SoCs**: AVC resolution and level from the decoder; AV1/VP9 HDR and 10-bit only if both decoder and display can do it; only assume HLG if the display reports it; check FFmpeg codecs with `FfmpegLibrary.supportsFormat`. | jellyfin `MediaCodecQuery.kt`, `deviceProfile.kt:417-460`; Moonfin `A/MediaCodecCapabilities.kt` | Google TV TVs with 1080p decoders. In phase 7 we dropped levels because the CC can handle everything; that judgment only applied to this device. | M |
| B8 | **Surround always via FFmpeg, unless there is a Dolby decoder** (`c2.dolby*`/`OMX.dolby*`). A custom `MediaCodecSelector` for the audio renderer. | Moonfin `SteeredMediaCodecAudioRenderer`, lines 404-480 | TVs with a generic AC3 decoder that mixes 5.1 down to stereo (item 12). | S |
| B9 | **KnownDefects (small)**: Shield `mdarcy` → p7 native; Fire TV `AFTKA`/`AFTKM`/`AFTKRT`/`AFTMM` → no DV+HDR10+; TrueHD only with ≥ 3 channels (FFmpeg stereo TrueHD does not work, androidx/media#1843); exclude MediaTek AVC High10. As a list in `core/playback`, with the reason per line. | jellyfin `KnownDefects.kt:8-25`; Moonfin `L/known_defects.dart`, `A/MediaCodecCapabilities.kt:34-42` | Only useful once we support such a device. The TrueHD stereo rule applies everywhere. | S |

### C. Deliberately not adopted

| What | Why not |
|---|---|
| Flutter (Moonfin) | Its own render engine plus Dart runtime on a Mali-G31 with 2 GB. We have one platform; native Compose remains the lightest route. |
| Custom IEC 61937 packer (Moonfin) | Heavy (TrueHD MAT frames, custom AudioTrack). Only with a demonstrably broken vendor packer. |
| `PassthroughSilenceRecovery`, `RouteFlapHold` (Moonfin) | Solve specific TV HAL errors that we do not see. Keep as a reference in case an AVR stays silent after pause. |
| `VsyncPacer` (Moonfin) | Only with demonstrated cadence problems (Moonfin: frames lost at 50 Hz). Measure first. |
| Letterbox crop (Moonfin) | PixelCopy analysis during playback; adds nothing to "playback". |
| Audio offload with video (jellyfin) | Moonfin only uses it for audio-only; we keep it off (`playback.md`). |
| Large buffer presets (jellyfin) | 240 s of 4K does not fit in the heap. A3 is the safer rule. |
| Software codecs on by default (jellyfin) | On the Cortex-A55 a recipe for stuttering video. Moonfin only adds dav1d if there is no hardware AV1; that can come later as opt-in. |
| `NotEquals` for HDR exclusion (jellyfin) | Moonfin explains that the server only passes positive conditions to the transcode. We already use `inCollection` (positive). Leave as is. |

## 5. Open choice

**SEAMLESS_ONLY.** On the CC the system setting is seamless-only, and the app forces the switch anyway, because the CC never switches seamlessly. If we start respecting that setting (B5), the user has to choose "Non-seamless" on the CC (already listed as an action item in `NOTES.md:9`). Alternative: the choice in the app (B4: "Adjust frame rate: Off / Always / Follow system") with "Always" as default. Advice: the app setting with "Always" as default. Then the CC stays as it is now, and another device can switch to "Follow system".

## 6. Proposed order

1. ✅ **Small and safe, also testable on the CC** (done 2026-10-10, see `NOTES.md`): A1, A3, A4, B2, B3, B8. Deviation: the buffer is heap/2 instead of heap/3, so the CC stays practically at the default (128 MB); B8 only for Dolby formats (DTS stays with the platform). Unit tests with fake capabilities (stereo TV, BT, AVR with DTS-HD, SDR TV, HDR10 TV without DV). Then on the CC: no regression on the test files in `NOTES.md` and measure the heap.
2. ✅ **Measure like the player** (done 2026-10-10): B1, B7, B9. HLG was already only on if the screen reports it (intersection decoder ∩ screen).
3. ✅ **Settings** (done 2026-10-10): B4, B5, plus the choice from §5 (app setting with "Always" as default, as advised). `Surface.setFrameRate` as an extra hint not done: `preferredDisplayModeId` works, and the surface lives in `ContentFrame`.
4. **DV p7 → p8.1:** A2. A native build of libdovi (Rust, `cargo-ndk`, MIT) or the prebuilt `.so` from a trusted source; record the dependency in `docs/dependencies.md`. Point of attention: dual-layer MKV puts the RPU in BlockAdditional, so it touches `FastCuesMatroskaExtractor`. Test with the p7 files from `NOTES.md`, including a DV TV check (LG shows the DV logo).
4. **Status 2026-10-10:** not done yet. On reflection: on almost all Android TV devices (Google TV Streamer, Fire TV, TVs with Google TV) p7 now plays as HDR10; this would make it real Dolby Vision there. It is picture quality, not compatibility: it already plays everywhere. It requires Rust + NDK on the development machine (built once) and a native lib in the app. In the library: 5 movies and 16 episodes of Game of Thrones (S2, S8).
5. ✅ **B6** (done 2026-10-10): wait for a returning HDMI audio output, Start delay, restart on an error shortly after a mode change. Measured on the CC: an HDR/DV switch does not start a new HDCP round (see `NOTES.md`).

Testing without a second device: the emulator has no HDMI, but it does have BT-less stereo audio, so B2 and A1 can partly be seen there. The rest is only possible with the unit tests (thanks to the pure `createDeviceProfile` and `chooseDisplayMode`) and on a real device.
