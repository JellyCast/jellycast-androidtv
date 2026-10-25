# Playback problems solved elsewhere: what JellyCast still lacks

Date: 2026-10-11. Reason: stuttering Dolby Vision on the Chromecast turned out to be a problem Moonfin had already solved (#1611/#1612, vsync pacer), even though we had examined Moonfin on 2026-10-10. That analysis (`playback-on-other-devices.md`) read code; this one reads the **issue and PR history** of Moonfin, jellyfin-androidtv and Media3, and checks every solved problem against our code.

Sources: Moonfin-Core (closed `[Playback]` issues, merged PRs and native commits since 2026-09-20), jellyfin-androidtv (~70 issues, ~50 PRs; master since `09c912d` only translations), androidx/media (release notes 1.11.1 and `main`, issues). Moonfin and jellyfin-androidtv are GPL-2.0, JellyCast is MIT: adopt ideas, not code.

Links: `mf#N` = github.com/Moonfin-Client/Moonfin-Core/issues/N, `jf#N` = github.com/jellyfin/jellyfin-androidtv/issues/N, `media#N` = github.com/androidx/media/issues/N.

## Already done (2026-10-11)

- Tunneling Auto for DV on Amlogic (`Tunneling.kt`), see NOTES.md.
- Vsync pacer (`FramePacer.kt`), after mf#1611/#1612 incl. the follow-up fix (max. 1 vsync, max. 6× in a row).
- #1 and the symptom of #3: `StallDetector` (loading with ≥ 10 s buffered, or a stalled clock during playback → seek again, then the fallback).
- #2: fallback step "without tunneling" for tunneled plans.
- #4: `AudioRouteMemory`. #5: `configChanges`. #6: MKV chapters skipped. #7: video track without decoder → fallback (at the start of playback). #10: interlaced → field rate. #11: buffer heap/3 at ≤ 192 MB. #14: decode-only flag pinned off.
- #9: found in the library (Sons of Anarchy S5, 12 episodes; plus 2 damaged Friends files). Solved as a fallback: a direct play file that is not seekable goes to the server remux as a file error. Tested on the Chromecast: S5E1 now resumes at 5:00.
- #12: cannot be told apart automatically from a working eARC chain (same measurement); the setting "Decode TrueHD/DTS on device" is now in Advanced playback as a way out. #15: `PaddingRowMask`. #16: Media3 itself sends DTS-HD as the DTS core if the output can only do DTS; recorded in a test.
- #8: setting "Video level: ignore" (Advanced playback), via `withOverrides`.
- Extra (not from this list): a network speed measured beforehand as a limit was tried and reverted: measuring 10 MB gave 52–121 Mbps on a 200+ connection (TCP ramp-up, and at the same time as buffering a movie), and would wrongly transcode a 117 Mbps file. Now reactive: see StallDetector (loading too slowly).

## Open items, in order of importance

| # | Symptom | Cause | Source | JellyCast now | Size |
|---|---|---|---|---|---|
| 1 | Player keeps loading forever while ≥ 10 s is buffered (after seeking, after pause, or after ~70 min of passthrough) | Stuck decoder or AudioTrack; a passthrough position that runs backwards is read as a 32-bit overflow | mf#1468, mf#1668, mf#1259; media#3407 (fix only on `main`), media#3269 | No monitoring: the fallback only reacts to `onPlayerError` | M |
| 2 | Tunneling gives errors or crashes (incl. Chromecast, transcodes, AAC/FLAC 5.1 via platform PCM) | Tunneling unreliable per device/firmware | jf#3295/#3296, jf#3726/#3786 (upstream turned it off) | No fallback step "without tunneling"; DV + TrueHD/DTS → PCM with tunneling not tested | S |
| 3 | No sound after pause or fast-forward (bitstream) | Audio HAL does not resume a paused bitstream track | mf#1270, mf#1673 | Missing | M |
| 4 | Unexpected PCM/transcode, Atmos/DTS:X gone with AVR/soundbar | Audio measurement falls into an HDMI route flap | mf#1459, mf#960 | For HDR we keep the previous measurement, for audio we do not | S |
| 5 | Picture drops out after a mode change with a different resolution | Activity is rebuilt on configuration change | jf#5683 (jf#5682, #5755) | `configChanges` only keyboard/navigation | S |
| 6 | NPE on MKV with nested chapters → unnecessary server remux | Regression in Media3 1.11.0 | media#3438 (fix only on `main`) | Inherits chapter parsing; NPE counts as a file error | S |
| 7 | Black picture with sound, no error | No decoder for the video track; Media3 selects nothing and reports nothing | mf#1510/#1511, PR mf#1486 | No check for an unselected video track | S |
| 8 | Unnecessary transcode "VideoLevelNotSupported" | Decoder reports too low a level (Shield) | jf#5520 (jf#3380, jf#1237) | Level from the decoder, no override | S–M |
| 9 | Seeking does not work, resume starts at 0 | MKV with a second SeekHead pointing to the Cues | jf#5221; media#2268 not merged | Missing | M |
| 10 | 50i/60i judders at 25/30 Hz | Server reports frame rate instead of field rate | jf#4527, jellyfin/jellyfin#4314 | `isInterlaced` not mapped | S |
| 11 | Stuttering and OOM with a small heap (128 MB) | Buffer too large next to the UI | mf#1085, mf#728 | heap/2 (Moonfin heap/3) | S |
| 12 | 7.1 as stereo at the amplifier | Device reports 8ch PCM, TV mixes back down | PR jf#5822 (open) | `decodesLosslessLocally` trusts `maxChannelCount` | M |
| 13 | Tunneling: first frame late when starting paused; seeking to the exact end hangs | DECODE_ONLY in tunnel mode | media#2919, media#1574 | Seek to exactly `durationMs` | S |
| 14 | Decode-only flag can silently turn on in an update | Google wants it on by default (b/470367414) | Javadoc `MediaCodecVideoRenderer` | Default (off), not pinned | S |
| 15 | Line below the picture at 3840×1606 | Height not a multiple of 8; compositor shows padding rows | mf#1667 | Missing (not seen on the Chromecast) | S |
| 16 | DTS-HD does not go as DTS core to a DTS AVR | Media3 does not fall back to the core | mf#1653 | FFmpeg → PCM, or server → EAC3 | M |
| 17 | Blu-ray TS/M2TS with DTS-HD: silent or buffering | TS extractor splits core and extension | mf#1674 | ts/m2ts as direct play; error → fallback, silent → not | S |
| 18 | Subtitles out of sync on a transcode | HLS segment timing | jf#4459 | No burn-in option on transcode | S |

Already covered (do not redo): HTTP timeouts, PCM/stereo after mode change, SDR variant from master playlist, DV not intersected with screen, AV1 software on the Chromecast, Fire TV/Shield/MediaTek defects, track matching by name, passthrough to Bluetooth, decoder drops out halfway, subtitle position, reclaimed decoder.

## Media3 update

Media3 1.11.1 is the latest stable version. Update right away to 1.11.2 or 1.12.0: they fix #1 (media#3407) and #6 (media#3438) at the source, plus an Amlogic H.264 workaround on resolution changes (media#3457) and a fairer count of frames with the same presentation time. Risks: `@ExperimentalApi` methods (`experimentalParseSubtitlesDuringExtraction`, `experimentalSetLegacyDecodingEnabled`) may be renamed, `setLoadOnlySelectedTracks(true)` becomes the default, the `protected` methods of `MatroskaExtractor` may change (measure Interstellar again), and the FFmpeg extension (1.9.0+1) still has to load.

## Not verified

Whether #1, #3 and #4 occur on the chain Chromecast → LG → Sonos; whether the library contains MKVs with a second SeekHead or interlaced files; tunneling with FFmpeg PCM and AAC 5.1 on the Chromecast; everything on Shield, Fire TV and Google TV Streamer (no device).
