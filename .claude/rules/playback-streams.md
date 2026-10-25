---
paths:
  - "**/playback/**"
  - "**/player/**"
---

# Playback: streams, recovery and reporting

Continuation of `playback.md` (choices, DeviceProfile and subtitles are there).

## Stream choice and playback [G2] [G4]

- Request playback info via `mediaInfoApi` (posted PlaybackInfo with the DeviceProfile, the user id and `startTimeTicks`). Choose the `MediaSource` from the response and keep the `playSessionId`.
- Determine `PlayMethod`: `supportsDirectPlay` → DIRECT_PLAY (static stream URL), else `supportsDirectStream` → DIRECT_STREAM, else `transcodingUrl` → TRANSCODE. Log the method plus the `transcodeReasons` at every start.
- Resume: set the start position via `setMediaItem(item, startPositionMs)`, not with a `seekTo` after starting.
- Jellyfin ticks: 1 tick = 100 ns, so `ms * 10_000`. Use one conversion function with a test.
- Track choice goes via `TrackSelectionParameters` (preferred language, overrides). Store the audio and subtitle preference as a language, not as a track index, because overrides only apply to identical track groups. [D3]
- **Frame rate:** the Chromecast can't switch seamlessly (each display mode is its own group), and Media3 by default only switches seamlessly. The app chooses the display mode itself with `preferredDisplayModeId` (`chooseDisplayMode`), before playback starts, and restores the original mode when leaving. Rules:
  - only a rate equal to the frame rate or double it (23.976 → 23.976 or 47.95 Hz), never "approximately" (24p on 60 Hz judders just the same);
  - first the same resolution as now; if that lacks the rate, another resolution, never smaller than the video or 720p;
  - setting "Frame rate" (Settings → Advanced playback): **Always** (default; also non-seamless, needed on the Chromecast where the system is set to "seamless only"), **System** (follows "Match content frame rate": Never/Seamless/Always; with Seamless only modes from `alternativeRefreshRates`), **Off**. The system setting Never always applies.
  The player may prepare immediately (open the file, buffer), but the **video track stays off until the new mode is active** (`displayModeReached`, `setVideoHeld`), so the hardware decoder only starts after the mode switch: a decoder that already delivered frames during the HDMI resync sometimes gave green blocks (4K HDR10+, seen 2026-10-10 and 2026-10-11). It **only starts playing when the new mode is active and the HDMI link is stable** (`displaySettled`: mode active + 2.5 s, timeout 3 s). A seamless switch doesn't wait. If the HDMI audio output drops out in that time (amplifier or soundbar in between), only once it's back and has been quiet for 1 s, at most 6 s after the new mode. On top of that the **Start delay** setting (none/0.5/1/2 s, also without a mode switch) as a way out for chains where it still clicks. A switch from SDR to HDR10 or Dolby Vision (at the first frame) starts no new HDCP round and falls within the waiting time with a mode switch (measured 2026-10-10).
  A playback error within 10 s after a non-seamless mode switch → the same plan once more (the resync can briefly remove the video surface; Moonfin), without a fallback step. Why: after the switch HDCP is only done ~2.2 s later; sound that starts earlier falls in the middle of the resync of TV and soundbar (measured 2026-10-09, see `NOTES.md`).
- MKV chapters are skipped (`FastCuesMatroskaExtractor.getElementType`): Media3 1.11 crashes on nested chapters (media#3438). Remove after the Media3 update that fixes this.
- **MKV via `JellyCastExtractorsFactory`** (in `PlayerFactory`): it replaces Media3's `MatroskaExtractor` with `FastCuesMatroskaExtractor`. That reads the seek index (Cues) in one go and leaves out subtitle cue points; otherwise starting remuxes with many subtitle tracks took up to 7 s. On a Media3 update: check that the `protected` methods and element IDs of `MatroskaExtractor` still match, and measure Interstellar again. Around it sits `ResyncingMatroskaExtractor`: a content error within a cluster (broken file, e.g. gaps of zeros) → continue at the next cluster, like ffmpeg. Network errors and errors outside a cluster stay errors, so the fallback chain still works there.
- Tracks are matched to the player by name (`matchTrack`), never by position alone.
- Seek with `SeekParameters.CLOSEST_SYNC`: at 4K exact seeking costs seconds of decoding.
- Audio offload is off. **Tunneling:** setting Auto/On/Off in Advanced playback, default Auto = only Dolby Vision via the DV decoder on an Amlogic chip (`usesTunneling`, chosen 2026-10-11). Why: without tunneling on the Chromecast with DV ~35 frames per minute never reached the screen (The White Lotus 1x1); with tunneling it plays smoothly. Otherwise off: tunneling excludes playback speed and audio processing, and is unreliable per device. Record the result per test file in `NOTES.md`. [D3] [D6]

## Per-format settings (Settings → Advanced playback)

- Per format (HDR10, HDR10+, Dolby Vision; passthrough of AC3, EAC3, DTS, TrueHD) Auto/On/Off, plus "Stereo only" (`downmixToStereo`), the frame rate, tunneling (Auto/On/Off) and "Video level: check/ignore" (`ignoreVideoLevel`: no `VIDEO_LEVEL` conditions to the server, for decoders that report too low a level; jellyfin-androidtv #5520). Auto = the measurement; the button shows what the device reports.
- Applied in `withOverrides` before the profile, so also in the track choice. Off also applies in the player: passthrough via `PassthroughPolicyAudioSink` (refused = FFmpeg to PCM), Dolby Vision via the HEVC decoder (`needsHevcBaseLayer`; otherwise Media3 picked the DV decoder as long as the display reports DV). On only adds to the profile: if the output really can't do it, the player decodes it itself.
- Why: HDMI chains often report wrongly (AVR, soundbar, TV). This way the user can correct it without a new build. Both reference clients have this (jellyfin-androidtv `HdrFormat`/`BitstreamAudioFormat`, Moonfin).

## Media Segments

- Intro, recap, commercial, preview and credits come from the server (`mediaSegmentsApi`, Jellyfin 10.10+), via `LibraryRepository.getSegments`; empty if the server doesn't know them. The server doesn't detect them itself: that needs a plugin (e.g. Intro Skipper). Logic in `core/model/MediaSegment.kt` (`skippableAt`, `nextCardStartMs`). Testing without a plugin: `--es debugSegments "intro:5-60,outro:1650-1721"` (debug/profile only).

## Start log

- `PlaybackStartLog` keeps per start (last 10) what happened: plan, mode switch, video track on, decoder, first frame, playing, errors and on stop the position and dropped frames. Readable via adb in release too: `adb shell cat /sdcard/Android/data/nl.jellycast.tv/files/playback-starts.txt`. Why: an error that occasionally happens at start is reported later, when logcat is already overwritten. No URLs or tokens in it.

## Error recovery

- **Silent stalls** (`StallDetector`, every second): loading while ≥ 10 s is buffered (for 4 s), or "playing" with a frozen position (3 s). First `seekTo` the same position; if it recurs within 60 s, it goes to the fallback chain as a video or audio error respectively. Loading with < 10 s buffered that lasts 10 s, or 20 s in total within 2 minutes, is **too slow**: the fallback step lowers the bitrate (40 → 20 → 10 Mbps, `SlowNetworkBitrates`) and the server transcodes. Loading after a start or a seek (`onSeek`) doesn't count until playback runs again: the buffer fills from zero there (a 10 s start falsely lowered the quality, 2026-10-12); a start that never gets going counts as too slow after 30 s. The playback info shows which cap applies (Quality setting or lowered automatically). No pre-measured limit: measuring 10 MB gave 52–121 Mbps on a 200+ connection and wrongly made direct play transcode (2026-10-12). A video track without a decoder (Media3 then plays only sound) also goes to the fallback chain as a video error. Why: Media3 reports no error then (Moonfin mf#1468/#1270/#1510, media#3407).
- If the plan played with tunneling, the first step after an audio, video or other error is: the same plan without tunneling (jellyfin-androidtv turned tunneling off for that reason).
- An error during playback first leads to a fallback, only then to the error screen (`PlaybackRecovery.kt`). The error is classified (`classifyPlaybackFailure`) and the next plan steps back one step (`recoverFrom`), from the same position:
  - network: the same plan once more, never remux;
  - file (Media3 can't read it): remux, then full transcode;
  - audio (output or audio decoder refuses): no passthrough (everything to PCM, also in the player: `allowPassthrough`), then stereo AAC from the server;
  - video (decoder): the same plan once more (the system may have reclaimed the decoder), then full transcode.
- A step never goes back while the same item plays; a new item (next episode) starts again without fallback. "The same plan once more" is allowed again after 30 s of error-free playback, so an error that keeps coming back always ends at the error screen.
- Why: on an unknown chain (other TV, AVR) things mostly go wrong at the audio output and the decoder; without fallback the error screen was the only way out. Source: `docs/research/playback-on-other-devices.md` (A1).

## Playback reporting [G4]

- `PlaybackReporter` reports start (`reportPlaybackStart`), progress (`reportPlaybackProgress`, every 10 s and directly on pause, resume and seek) and stop (`reportPlaybackStopped`) via `playStateApi`.
- Always send `itemId`, `mediaSourceId`, `playSessionId`, `positionTicks`, `isPaused` and `playMethod`.
- Send a stop report at every end: leaving, `onStop`, error, next episode. Use an app scope so the report still gets out after the screen closes.
- After the stop, no more progress for that plan (ExoPlayer still reports a pause on release). Otherwise the server thinks something is still playing and Continue watching is stale for a minute (seen 2026-10-10).
- Stopped in the credits (from the start of the outro, without outro the last 30 s; `isFinishedAt`) = watched: after the stop report `PlaybackReporter` marks the item as watched (chosen 2026-10-12). Why: the server only does that above 90% (*Maximum resume percentage*), and with episodes the credits often start earlier; otherwise a finished episode stays in Continue watching and at the top of Home.
- A failed report is logged and never thrown to the user. Playback must never stop because of it.
