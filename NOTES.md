# NOTES

Decisions, known issues and codec findings. Newest first in each section.

## Status (2026-10-25)

Finished: all phases (skeleton up to the recalibration against jellyfin-androidtv) are built and tested on the Chromecast, plus remote control from the iOS app (commands, play from any screen, wake via the Android TV Remote protocol; how it works in `../jellycast-ios/docs/remote-play.md`). Setup and installing: `docs/setup.md`. What is still open is under "Open items" at the end of this file.

Not built, worth knowing for later:

- **Skip intro:** the code is there (Media Segments), but the server has no intro markers.
- **libass for ASS subtitles:** postponed, see `docs/research/jellyfin-androidtv-comparison.md`.
- **DV profile 7 → 8.1** (libdovi): step 4 in `docs/research/playback-on-other-devices.md`.
- **Libraries kept apart:** Movies and Series query the server by type over all libraries, so a new library mixes in. Later: choose libraries in Settings, or a shelf per library.
- **Large libraries:** `getMovies()`/`getSeries()` load the whole list in one request; with thousands of titles, load per shelf and in pages.
- **Multiple servers:** one server and account at a time; switching goes via signing out.

Out of scope: Live TV, music, searching the whole server, multiple user profiles, offline downloads and the Play Store.

## Settings to validate (assumed correct, 2026-10-08)

The user assumes these are set correctly. Check them as soon as a test gives reason to (stutter, wrong HDR mode, no Atmos, stereo instead of surround, unexpected transcode):

- [ ] Chromecast: "Match content frame rate" on **Non-seamless** (adb showed `match_content_frame_rate` not set and seamless-only).
- [ ] Chromecast: match dynamic range to the content on (adb showed that SDR goes out as Dolby Vision).
- [ ] Chromecast: Surround sound on **Automatic**.
- [ ] LG OLED: eARC on, digital sound output on **Passthrough**.
- [x] Sonos app: multichannel PCM 7.1 comes in (Jurassic World Dominion, seen by the user 2026-10-10). Atmos (EAC3-JOC) not checked yet.
- [x] Jellyfin server: hardware acceleration on Intel QuickSync (checked 2026-10-08; test that it works with VC-1 in phase 2).
- [ ] Network: Wi-Fi 5 GHz, link 780 Mbps @ −59 dBm (measured). Enough for 117 Mbps? Otherwise a gigabit USB-C hub (not Google's 100 Mbit adapter).

## Decisions and findings per phase

### Continue watching on the Google TV home screen: Watch Next is ignored (2026-10-25)

- Built: every Home stack load (start, return from the player) writes next-up episodes and started movies (max. 10) to `TvContract.WatchNextPrograms`; a card opens `jellycast://play?item=…&start=…`. Sign-out clears them. Framework `TvContract` (minSdk 34), not `androidx.tvprovider`: its builders fail lint as restricted API.
- Inserting needs no permission; updating or deleting a row needs `WRITE_EPG_DATA` (normal permission, granted at install).
- On the Chromecast (launcherx, 2026-10-25) the rows are stored (4 cards) but the "Continue watching" row shows only YouTube, also after restarting the launcher. The launcher logs `SetWatchNextVerification: Watch next verification data is empty`: it seems to accept Watch Next only from apps Google verified. The Settings list "Apps & services" contains only Google's catalog partners.
- The successor, the Engage SDK, needs enrolment and an allowlist at Google; not realistic for a sideloaded personal app. Watch Next loses support in the 2nd half of 2027 anyway.

### Tracks follow a hand-over between iPhone and TV (2026-10-25)

- A play from the iPhone (server `Play` message or the `jellycast://play` link) carries the audio and subtitle stream index the iPhone played; the TV starts with those instead of its own choice.
- The TV reports what plays, including a track picked in its own menu, so "Continue on iPhone" takes it over.
- Subtitles are matched to Media3's tracks by file order first: plain and SDH English (or two Spanish tracks) share codec and language, and the old match always picked the first. The server's external subtitle files are left out of the plan; the player never loads them.

### Subtitle list: your languages first, then A–Z (2026-10-25)

- The server user's subtitle language, then audio language (`SubtitleLanguagePreference`, `AudioLanguagePreference`; for "han" Dutch, English) come first; the rest is sorted by name in the app's language. Within a language: plain, then SDH, then forced.
- The label is always the language. A track name from the file only shows underneath when it adds something ("Simplified"); names that repeat the language, SDH, forced or the format, release names on every track and names over 40 characters are dropped. SDH and forced become a small mark (from the file flags or the name, e.g. a track only called "SDH").
- The format (SRT, ASS) is gone; image subtitles (PGS, VobSub) are marked "Image", because the size and background settings do not apply to them.
- Tracks that still look the same get a number ("Spanish 2"): Netflix releases carry two Spanish and two Portuguese tracks (Spain/Latin America, Portugal/Brazil) without saying which is which.
- Same logic in all three apps (Android `SubtitleEntries.kt`, webOS `trackLabels.ts`, iOS `SubtitleOrder.swift`).

### Audio in the original language (2026-10-23)

About 120 files (Italian, French and Spanish releases such as Star City, The Bear, The White Lotus) mark their dub as the default audio track, with the original English track second. Jellyfin stores no original language (no field; series have no production country either), so the app asks Wikidata: the title's IMDb id (an episode uses its series) → P364 "original language" → ISO 639-1. One SPARQL request, no API key, 2.5 s timeout, cached per IMDb id in memory; checked 2026-10-23: all 52 titles in the library answer.

The audio track starts from the first language the file has in: original language(s), then English, then Dutch; else the default track. Commentary tracks never count. The TrueHD/DTS replacement then works within that language. The tech line shows a codec swap within the language as a replacement, a language choice not.

### Transcode "because of bitrate" with Quality on no limit (2026-10-12)

- Seen with The White Lotus 1x2 and 1x3 (4K DV WEB-DL, DP): the info screen said `ContainerBitrateExceedsLimit` with Quality on no limit. Cause: the too-slow fallback (`StallDetector` → 40 Mbps), not the setting.
- Start log, 2 of 3 times real: buffering every ~5 s while the position moved on 2–4 s, so the data came in slower than playback. Temporary: the same episode later played 12 min in DP with one buffering, and the transcodes ran smoothly. So a dip on the link to the server (Wi-Fi or server disk), not the internet; the log can't tell which.
- 1 of 3 times false: a start that took 10.5 s to the first frame, then "too slow" 0.8 s later. Fixed: loading after a start or seek no longer counts until playback runs (a start that never gets going: 30 s). The playback info now shows the cap and its cause ("lowered automatically" or "Quality setting"); the start log header too.

### Home: suggestion stack by likelihood (2026-10-12)

- Home was one random suggestion (50/50 movie or series). Now a suggestion stack of at most 5 in a fixed order (`rankHome`, design-screens.md → Home section): in progress ≤ 3 days, new episode ≤ 14 days, continue ≤ 30 days, new movies ≤ 30 days, fallback. ←/→ pages through it. Four small requests at once: `getNextUp` (now with `DateCreated` and `touchedAt`), in-progress movies and newest unwatched movies (limit 5), and one random movie.
- "New episode" = added after the most recently touched episode of that series. Catching up on a backlog (the episode already existed) therefore counts as "Next episode".
- Stopping in the end credits marks as watched (`isFinishedAt`). The server has no segments yet (see below), so for now the last 30 s applies; with Intro Skipper, from the start of the outro.
- First test on the Chromecast: suggestion 1 was A Knight of the Seven Kingdoms 1x1 (in progress, from 39:30); the user's OK played it, and the transition to 1x2 marked 1x1 as watched ("Stopped in the end credits: watched" in the start log).

### Skip intro and Next at the end credits (2026-10-12)

- The server does not provide segments yet: only default plugins (TMDb, OMDb, MusicBrainz, AudioDB, Studio Images), `MediaSegments` empty for White Lotus, Mythic Quest and Stranger Things. Needed: a plugin such as Intro Skipper (the user's own choice; analysis costs hours of CPU on the N150).
- **Intro Skipper 1.10.11.24 installed** (2026-10-12, on request; source `https://intro-skipper.org/manifest.json`), analysis started right away: ~3% per 25 min. Segments are visible through the API right after the analysis (no Media Segment Scan needed). First result: A Knight of the Seven Kingdoms S1E1–E6 Outro only (~2 min). Tested with real data: S1E1 from 39:30 → Next card at 39:46 (start of outro), then E2. During the analysis the HDD is busy: 14.7 s to first frame and brief loading every few seconds with 4K DV (no fallback: too short).
- **Jump pill** (design approved 2026-10-12, Paper "Sprong — …"): tested on the Chromecast with real segments. Alone S13E11: pill bottom right with focus; with the overlay, at rest to the right above the seek bar, ↑ from the seek bar gives focus, OK → 1:07 and the pill stays for the intro (focus kept). A Knight of the Seven Kingdoms S1E1: "Next episode" at the end credits with countdown line; overlay open = countdown paused, overlay gone = continues. Distance to the seek bar 132 dp (124 was too close to the seek bar with focus).
- **Real intro and recap tested** (Alone S13E11: Recap 0:00–1:07, Intro 1:07–1:37): after the start first the overlay (4 s), then "Skip recap"; OK → 1:07 and immediately "Skip intro"; OK → 1:37; after that OK just shows the overlay. Picture within 0.1 s after each jump. After ~50 min of analysis: 34 episodes with segments (11 Intro, 6 Recap, 28 Outro).
- Tested with `debugSegments` on Mythic Quest 1x1 (profile build): the "Skip recap" pill appears and gets focus without the overlay; OK jumps to the end (0:15); "Skip intro" appears; Back removes the pill and playback continues. Outro from 27:40: Next card at ~27:41 (without segments 28:11), S01E02 after the countdown, disk woken in advance.

### Waking the disk, next episode, measuring smoothness (2026-10-12)

- **Smoothness in the start log** (`FrameStats`): "Video: n of m frames not shown" on stop, without tunneling. Compared with `no present fence`: White Lotus without tunneling 14 vs 8, GoT 1x1 9 vs 12, calm files 1–3 vs 0–1 per minute. Same order of magnitude, not an exact copy; good enough to see stutter without logcat.
- **Waking the disk** (`warmUpMedia`, 64 KB, per item at most once every 5 min): Home suggestion, Continue watching, series page, movies, movie detail. Measured after ~40 min of silence: first request 7.1 s (disk was asleep), after that 50–100 ms. Those 7 s now fall during choosing, not after Play.
- **Preparing the next episode** (`onNearEnd`): at the Next card, wake the disk and fetch the plan. Mythic Quest 1x1 → 1x2: first frame 0.6 s after stopping.
- Media3: 1.11.1 is still the latest stable version (Google Maven and GitHub, 2026-10-12); FFmpeg extension 1.9.0+1. Update as soon as 1.11.2/1.12 is out.

### Stable player: test matrix and fallbacks (2026-10-12)

Source: `docs/research/solved-playback-bugs.md` (solved bugs in Moonfin, jellyfin-androidtv and Media3) and `docs/playback-test-matrix.md` (library). Tested with the profile build via `debugPlayItem`, each item 60 s from 5:00, on the Chromecast (Sonos off, so sound to the LG; passthrough chain not listened to).

| Item | What | Result |
|---|---|---|
| Alone 0x18 | H.264 1080p 29.97, AAC 2.0 | DP, first frame 1.6 s, 0 drops |
| Stranger Things 1x1 | HEVC 4K 10-bit SDR | DP, 1.4 s, 0 |
| Queen's Gambit 1x1 | HDR10 24.000 fps | DP, 2.0 s, 0 |
| The End of Oak Street | HDR10+ | DP, 1.5 s, 0 |
| Silo 3x4 | DV p5, 3840×1606 | DP, tunneling, 1.6 s, 0 |
| Ready Player One | DV p7 FEL, 92 Mbps | DP via HEVC (HDR10), 1.7 s, 0 |
| Avatar: TLA (2024) 2x1, 2x2 | HDR10 24.000 fps, 13 Mbps, NF WEB-DL | DP, 24 Hz, 1–2 of ~90 frames not shown, 2 `no present fence` in 3.5 min (2026-10-25). User saw a steady stutter: native 24p judder on the OLED (source has clean 41/42 ms timestamps, no duplicate frames), not the app. Remedy is the TV's TruMotion (Cinematic Movement), not the player. |
| GoT 8x6 | 117 Mbps, TrueHD 7.1 → PCM | DP, 1.6 s, 0; seeking/pause OK |
| White Lotus 1x1 | DV p8.1 | DP, tunneling, 2.0 s, 0; seeking/pause OK |
| GoT 1x1 | DV p8.6 + TrueHD → PCM 7.1 | DP, tunneling, 3.8 s, 0; A/V offset −2…0 ms |
| White Lotus 3x1 | DV 25 fps | DP, tunneling, 1.1 s, 0 |
| Mythic Quest 1x1 | 23.81 fps | DP, 1.6 s, 0 |
| Jurassic World Rebirth | mp4 76 GB, 22 mov_text, DV | DP, tunneling, **7.1 s** (reading the mp4 index; Media3), 0 |
| The Gentlemen 2x1 | default audio track is not the first | DP, tunneling, 2.1 s, 0 |
| Sons of Anarchy 1x1 | DTS core only | DP (FFmpeg → PCM), 2.2 s, 1 drop |
| Interstellar | 74 PGS, Atmos, 109 Mbps | DP, tunneling (EAC3 8ch), 3.5 s, 0; seeking/pause OK |
| Sons of Anarchy 5x1 | MKV with Cues in a second SeekHead | was: resume at 0:00. Now: "Not seekable; remux" → resumes at 5:00 |
| Friends 10x9 | broken Cues (zeros) | read error → restart → remux, plays |

- No errors, no hangs, no false alarms from the `StallDetector` (not on pause, resume or 12 seeks in a row either).
- Tunneling with PCM 7.1 from FFmpeg (DV + TrueHD) works: that was the open item.
- Found and fixed our own bug: the "no video decoder" check raised a false alarm on every start (tracks not chosen yet); now only at the start of playback.
- Reverted: a network limit measured in advance. Measuring 10 MB gave 52–121 Mbps on a 200+ connection and would wrongly make GoT 8x6 transcode. Now reactive: loading too slowly → 40/20/10 Mbps.
- Not available: `AlwaysBurnInSubtitleWhenTranscoding` (jf#4459) is not in `PlaybackInfoDto` of SDK 1.8.12.
- First start on the emulator (clean release install): server via IP address only → login screen with Quick Connect code. Server discovery finds nothing on the emulator (NAT does not pass the UDP broadcast; not a bug). Login not tested by Claude (password).
- First start after a night (release 0.1.36, 2026-10-12 08:41): 11 s to first frame, of which ~7 s waiting for data after the HDMI resync. Cause: the server's HDD goes idle and has to spin up (confirmed by the user). After that 1–2 s. Not an app problem.
- With every tunneled movie, Android logs ~12×/s `VideoRenderQualityTracker` warnings ("earlier than expected", "time jumped backwards"); without tunneling 0. A measurement error of the tracker in tunnel mode: the decoder delivers a steady 24 fps, A/V offset 26–28 ms, picture smooth.
- Not testable without a device or root: slow network ("too slow"), Shield, Fire TV, Google TV Streamer, AVR with DTS.

### Stuttering picture in The White Lotus 1x1 (2026-10-11, open)

- Release 0.1.28, DirectPlay, DV p8.6 3840×2160 @ 23.976 (MAX WEB-DL, 22 Mbps, EAC3 5.1, SRT). TV mode 2160p 23.976 Hz, DV output.
- logcat: `CCodecBufferChannel: no present fence for frame N`, ~1.1–1.4 per second, isolated frames (5–20 frames in between). Each such frame is decoded but never shown: a visible stutter. `DecoderCounters` (the "dropped frames" in the playback info) stays at 0: the player did not consider them late, they are only dropped at composition.
- Ruled out: subtitles (off: 33 in 30 s, on: ~14 per 10 s), app UI (0 UI frames in 4 s, still 9 dropped), memory (swap full but 0 swap activity during the drops), heat (SoC 32 °C, CPU at 1.9 GHz), network (no buffering).
- Mythic Quest S04E01 (also DV p8.6, 3840×1920, 25 Mbps) and Silo S03E01 (4K SDR), just before: 0 messages.
- On ExoPlayer's playback thread there is constantly `Choreographer: Frame time is … ms in the future! Check that graphics HAL is generating vsync timestamps using the correct timebase.` Suspicion: ExoPlayer aligns each frame's release time to the vsync; with deviating vsync times, two frames occasionally land in the same vsync and the compositor drops one. That depends on the phase between video and vsync, and explains why it varies per start/file.
- Overlay ruled out: 6+ min without a key press, 31–43 drops per minute.
- **DV off** (Advanced playback; same file, restarted at 14:15, HDR10 via `c2.amlogic.hevc.decoder`): **0 drops in 60 s**, and no more `Choreographer` warnings either. So it is in the DV route (`c2.amlogic.dolby-vision.dvhe.decoder` + DV output to the LG), not in the app UI. Still to confirm: DV back on + restart (drops back?), and whether tunneled playback saves the DV route.
- **Tunneling on** (0.1.29, toggle in Advanced playback; DV auto, same file): start log "Audio output: encoding 6, 6ch, tunneling", decoder `dvhe` with `Tunnelrender`. No more `no present fence` and no `Choreographer` warnings, but that message does not exist in tunnel mode anyway, so that proves little. However: the decoder delivers exactly 120 frames per 5 s (24 fps, `DebugUtil` in/out), and A/V offset 24 ms (`audio_hw_primary` pts vs pcr). The `/sys/class/video` counters cannot be read without root; the assessment is therefore by eye.
- **User (by eye): smooth.** Decision: tunneling Auto (default) = only DV via the DV decoder on an Amlogic chip; On/Off for the rest (0.1.30). Still to test with tunneling: TrueHD/DTS via FFmpeg to PCM in a DV movie, seeking and pause, and A/V sync by eye.
- **Vsync pacer** (0.1.32, `FramePacer`, idea from Moonfin #1611/#1612, own code), tunneling off, DV on, same file: **21 drops in 3 min (~7/min)** vs 31–43/min without. Helps a lot, not everything: what remains is probably due to the deviating vsync times in the DV route. For DV, tunneling remains the solution; the pacer applies to everything without tunneling. 0.1.31 crashed on every decoder start (`context.display` on the app context); the fallback then went to a transcode that also failed. Fixed in 0.1.32.
- MKV timestamps of 24p are whole ms (41/42) against a vsync of 41.7 ms: with an unlucky phase, frames without the pacer land on the same vsync (calculated: 5 of 200 phases, up to 140 of 480 frames). Measured with `ffprobe` on White Lotus and Mythic Quest S04: no bunched timestamps as in Moonfin #1611.

### New brand and splash (2026-10-11)

- Five logo ideas in Paper ("Logo-ideeën — neon outline (JellyCast)"); chosen: **B, the Cast triangle**. Worked out in `design-style.md` → "Login, brand, banner and splash".
- The brand is a Compose drawing (`BrandMark`), no longer a PNG: sharp at any size and able to draw itself in for the splash. `brand_icon.png` and the launcher PNGs (`mipmap-*/ic_launcher_foreground.png`) are gone; the launcher foreground is a vector with the same dimensions (also as `monochrome`). `brand_amber` is gone.
- Banner re-exported from Paper (320×180). The login background is now a purple glow with a hint of light blue: one WebP, 960×540 lossless, 102 KB (was 78 KB).
- Splash on the Chromecast (0.1.25, release, `speed`), measured with `screenrecord` and `gfxinfo framestats`:
  - First version (Home built up right away under the splash): the line hung for ~0.3 s and then appeared complete all at once.
  - Cause: the app's first frame takes ~390 ms and after that it is quiet until ~570 ms. The animation clock kept running, so the line jumped. After that, frames simply come every 16.7 ms.
  - Fix: drawing in waits until two frames in a row arrive within 25 ms (`awaitSteadyFrames`), and Home only builds once the line is there. Now the line visibly draws itself in, at 60 fps or sometimes 30 fps. The single long frame (83–133 ms) while building Home falls in the still pause.
  - Cost: Home starts loading ~0.6 s later, so after the splash the skeleton is shown briefly (~0.5 s).
  - Cold start (TotalTime) 0.67–0.70 s; in the emulator 0.36–0.43 s.

### Another TV: old 1080p TV without HDR and without Dolby (2026-10-11)

- **Chain:** Chromecast via HDMI directly into a 2010 Philips (EDID "Philips FTV"). Only 1920×1080 and 1280×720, with 23.976/24/25/29.97/30/50/59.94/60 Hz. `supportedHdrTypes=[]`. The app measures: HDR none, passthrough AC3 only, PCM 6 channels, output HDMI. Tested with the profile build of 0.1.24, then release 0.1.24 put back.
- **Good:** interface at 1080p, Home section, Movies and Library. Mode switch to 23.976 Hz is stable after 2.5–3 s. Star Wars VII (HEVC 10-bit SDR, AAC 5.1) direct plays and is downmixed by the TV. When the server transcodes, the audio comes as AC3 5.1 passthrough; the TV decodes it itself. Picture and sound approved by the user.
- **HDR on Auto (as the TV reports):** HDR10 and DV go to the server (`VideoRangeTypeNotSupported`). Oppenheimer: starts in **19 s**, and the server turns it into 4K SDR (`MaxWidth=4096`, `VideoBitrate=199 Mbps`) for a 1080p TV. The N150 keeps up (~24 fps), but seeking is slow. Picture good (tone mapping on the server).
- **HDR10 on On (Advanced playback):** the Chromecast converts HDR to SDR itself. Oppenheimer (HDR10, DTS-HD MA via FFmpeg to PCM) starts in ~3 s, most of which is the mode switch. According to the user "slightly darker picture, but good", and seeking is much faster. Interstellar (DV p8.6 + HDR10+, TrueHD 7.1 to PCM 8 channels): picture and sound good. **This setting stays on**; the LG can do HDR10 anyway.
- **Open items:**
  - Transcoding by the server uses the decoder's size (4096) and not the screen's. With a 1080p screen that should be 1920×1080: less work for the N150 and a faster start. Direct play of 4K stays fine, because the Chromecast scales itself.
  - Auto chooses the server for HDR on an SDR TV, while the Chromecast tone maps itself. That is an assumption per device, see `KnownDefects.kt`. Adjust if it turns out to be needed again.
  - The tech line says "DV P8 → HDR10" and "PCM 5.1", but it plays as SDR with PCM 8 channels (Interstellar).

### Interface measurement of all screens (0.1.22, 2026-10-10)

Release build, `compile -m speed`, `measure-scroll.sh` (30 s of real key repeat), one run per row unless stated otherwise. Budget: < 5% janky frames.

| Screen | Movement | Janky | p50 / p90 / p99 (ms) | Before |
|---|---|---|---|---|
| Home section, idle (neon border running) | 15 s nothing | 0.7% | 22 / 30 / 32 | — |
| Section bar | ←/→ (switches section) | 4.1% | 27 / 38 / 129 | — |
| Series | ↓↑→← (2 runs) | 2.6% · 2.6% | 22–26 / 32 / 69–81 | 5–8% |
| Series, genre row | ←/→ | 1.1% | 22 / 29 / 34 | 12–13% |
| Movies, detail view (New shelf) | ←/→ | **8.3%** | 28 / 42 / 57 | 56–60% |
| Movies, detail view | ↓↑→← | 3.8% | 24 / 36 / 85 | — |
| Movies, bookcase (New shelf) | ←/→ | **8.8%** | 29 / 40 / 65 | 10–12% |
| Movies, bookcase | ↓↑→← | 3.0% | 28 / 36 / 150 | — |
| Library (movie grid) | ↓↑→← | 1.8% | 22 / 31 / 69 | 8.5–10.7% |
| Series page (Game of Thrones) | ↓↑→← | 2.5% | 26 / 32 / 53 | — |
| Series page, season 1 | ←/→ | 1.1% | 22 / 29 / 34 | — |
| Movie detail (Blow), idle | 15 s nothing | 1.1% | 25 / 30 / 34 | — |
| Movie detail | ←/→ across the buttons | 0.3% | 26 / 31 / 36 | — |

- Cold start 0.82 s, warm start 0.09 s. Memory after ~10 min of browsing through all screens: **146 MB PSS** (Graphics 52, Native 27, Java 12). Everything within budget.
- Over budget: only ←/→ in Movies (detail 8.3%, bookcase 8.8%): each step there swaps a whole panel (logo, metadata), not just the focus. Not solved yet.
- p50 is 22–29 ms everywhere, even idle with only the neon border moving (Home: 620 frames in 15 s, ~41 fps). `gfxinfo` does not count that as janky. Not yet investigated whether the neon border or the whole screen exceeds the 16.7 ms.
- Not measured: the player overlay (seeking changes the watch position on the server) and Settings.

### TrueHD passthrough via IEC61937: measurement (2026-10-10)

The audio HAL does not report TrueHD as passthrough (only AC3, EAC3, EAC3-JOC), but it does report `IEC61937`; Moonfin wraps TrueHD itself (MAT) and plays it as already packed IEC61937 data. TrueHD and DTS-HD MA then need 8 channels at 192 kHz (HBR). Question: does the platform allow that?

- **Status: built.** `app/src/androidTest/java/nl/jellycast/tv/playback/Iec61937ProbeTest.kt`. Measures with the movie attributes (`USAGE_MEDIA`, `CONTENT_TYPE_MOVIE`): the output from `getAudioDevicesForAttributes` with encodings, channels, rates and `audioProfiles`; `getDirectProfilesForAttributes`; `isDirectPlaybackSupported` and `getDirectPlaybackSupport` per format; and whether an IEC61937 `AudioTrack` can be created (immediately `release()`). Plays nothing: no `play()`, no `write()`. Logs under tag `IecProbe` and as instrument status; always passes.
- **Status: run** on 2026-10-10 at 19:00, Chromecast with Google TV 4K (Android 14) via HDMI → LG (eARC) → Sonos. Plex was in the foreground, not JellyCast.

  | Format | Channels | Rate | isDirectPlaybackSupported | getDirectPlaybackSupport | Create AudioTrack |
  |---|---|---|---|---|---|
  | IEC61937 | 2 | 48 kHz | true | BITSTREAM | succeeds (route HDMI, 8192 frames) |
  | IEC61937 | 2 | 192 kHz | true | BITSTREAM | succeeds (route HDMI, 8192 frames) |
  | IEC61937 | 8 (7.1) | 192 kHz | true | BITSTREAM | succeeds (state=1, 192 kHz, 8 channels, route HDMI, 16388 frames) |
  | IEC61937 | 8 (7.1) | 48 kHz | true | BITSTREAM | not tried |
  | TrueHD | 8 | 48 kHz | false | NOT_SUPPORTED | – |
  | Dolby MAT | 8 | 48 kHz | false | NOT_SUPPORTED | – |
  | DTS-HD | 8 | 48 kHz | false | NOT_SUPPORTED | – |
  | EAC3-JOC (control) | 6 | 48 kHz | true | BITSTREAM | – |

  Output: HDMI "Chromecast", encodings PCM16, EAC3_JOC, AC3, EAC3, IEC61937; channels 1–8; rates up to 192 kHz. The IEC61937 profile lists all masks (including 7.1, `0x18fc`) and all rates from 8 to 192 kHz, also in `getDirectProfilesForAttributes`. System property `sys.vendor.atmos.passthrough=enable`. The receiver's audio capabilities (EDID, `/sys/class/amhdmitx/amhdmitx0/aud_cap`) cannot be read without root.
- **Outcome: mixed.** The platform allows IEC61937 7.1 @ 192 kHz (HBR): it is reported as bitstream and an `AudioTrack` opens on HDMI with exactly that format. So the route Moonfin uses is not closed here. It remains unproven whether the chain also plays it: the IEC61937 profile looks like a generic "anything goes" report (all rates and masks), and the absence of TrueHD/MAT may mean that the LG/Sonos does not report MAT in the EDID (AC3/EAC3 do come from there). Then the result is silence or noise, not Atmos.
  - Next test (cheap, no own code): play a TrueHD Atmos movie in Moonfin or Kodi (passthrough on, "TrueHD" and IEC packer on) and check in the Sonos app whether it says "Dolby Atmos"/"TrueHD". If that does not work, the topic is closed for this chain.
  - If it does work: our own TrueHD MAT packer (FFmpeg `spdifenc`, Moonfin `N/iec/*`) plus our own path to the `AudioTrack` alongside Media3. Multi-day work with risk (silent or stuttering sound); the decision is the user's, weighed against "smooth > pretty" (`product-principles.md`). Until then the current path stays: TrueHD/DTS-HD MA decoded locally to PCM 7.1 (without Atmos height), EAC3 Atmos track if there is one.

### Tech line and playback info: file → how it plays (2026-10-10)

- At the user's request: per stream, see whether something direct plays, and if not, whether it is due to video or audio. `summarizePlayback` (`core/playback/PlaybackOutcomes.kt`) summarizes, for each playback plan, what the file has and how it plays (`PlaybackOutcome`): video as in the file / as HDR10 base layer / transcoded by the server; audio unchanged to the amplifier / to PCM here / another track / transcoded by the server. Which stream the server transcodes follows from `TranscodeReasons` (Audio… = audio, ContainerNotSupported = remux only, the rest = video).
- Tech line (movie detail, Movies detail view): e.g. "4K · DV P7 → HDR10 · AC3 · DIRECT PLAY" (The Godfather), "4K · DOLBY VISION · TRUEHD ATMOS → EAC3 ATMOS TRACK · DIRECT PLAY" (Interstellar), "4K · HDR10 · TRUEHD ATMOS → PCM 7.1 · DIRECT PLAY" (Fellowship), "4K · HDR10 · AAC · DIRECT PLAY" (Jurassic World). Checked on the Chromecast. The end now says "Video transcoded", "Audio transcoded" or "Remux" instead of just "Transcoded" (The Imitation Game was first labeled "Transcoded" while only the audio was transcoded).
- Info panel in the player: lines "video: in file … → plays …" and "audio: in file … → plays …", plus "Atmos is lost" when that is the case. Atmos per track now comes from the server (`AudioSpatialFormat`), no longer from the track name.
- The Home section still only shows what the library knows (there is no playback plan there).

### Interstellar: comparison with Plex and open items (2026-10-10)

- **Sound 41–43 min (rocket launch):** JellyCast passes the EAC3 Atmos track (7.1, 15 objects, 1664 kbps) through unchanged; the Sonos shows Dolby Atmos. Plex delivers the same fragment as "Dolby Multichannel 5.1" (transcoded or AC3), and sounds louder. The crackling is part of the sound mix of the launch: also audible in Plex, less in the foreground due to the higher volume (user). No interruptions in the log. Plex's louder sound probably comes from a different dialnorm level in the transcoded track, not from better quality. No action.
- **Open: pop at the start** (only Interstellar, not with Casino). Suspicion: at the first frame the LG switches to Dolby Vision and briefly interrupts the audio path to the Sonos; an Atmos bitstream tolerates that worse than PCM. To test: Start delay 1 s. If that helps, wait longer only on an HDR/DV switch.
- **Solved: buffering around 1:39.** Every 5–20 s, 1–4 s of BUFFERING. Not the Wi-Fi: `nc` on the Chromecast gets 215–230 Mbps per connection (400 MB in 14–15 s, also around 1:39), the server delivers 340–590 Mbps to the Mac. In the app the data came in in fits and starts (2 s blocks of 150–180 Mbps alternating with 0–10 Mbps), on one HTTP connection, and the GC cleaned up ~20 MB of large objects every 2–3 s. Cause: by default Media3 decodes subtitles during extraction, for **all** tracks, on the thread that reads the file; Interstellar has 74 PGS tracks (each subtitle an image). Now the subtitle renderer decodes only the chosen track (`experimentalParseSubtitlesDuringExtraction(false)` + legacy decoding in the `TextRenderer`). Afterwards, same scene: buffering once at the start, 170–234 Mbps, buffer full, no more LOS garbage. PGS (Interstellar, English) and ASS (Friends 1x1) shown on the Chromecast; subtitles via HLS (transcode) not tested. ~150 items have ≥ 10 image subtitle tracks.
- Measuring: `--el debugStartMs <ms>` on the debug start, and in debug/profile `ThroughputLogger` logs the actual throughput every 2 s ("Network: … Mbps").

### More robust playback on other devices, step 5 (2026-10-10)

B6 from `docs/research/playback-on-other-devices.md`. Step 4 (DV p7 → 8.1) has not been done yet; see there.

- **Chromecast settings changed by the user:** "Match content dynamic range" on (was off: the Chromecast converted everything, including SDR and the menu, to Dolby Vision; `HDR_CONVERSION_SYSTEM`, on the LG "RGB 8b TM dolbyvision" with every movie). Now `HDR_CONVERSION_PASSTHROUGH`. "Match content frame rate" on Always (`match_content_frame_rate=2`). So the LG's DV logo said nothing about the movie: The Godfather (p7) played via `c2.amlogic.hevc.decoder` as HDR10.
- **Measured (profile build, plan = 0 s):**

  | Movie | Mode requested | First frame (+ HDR/DV switch) | HDCP encrypted | Sound starts |
  |---|---|---|---|---|
  | Casino (HDR10) | 0.37 s | 1.12 s | 2.73 s | 3.27 s |
  | Interstellar (DV p8) | 0.37 s | 1.56 s | 2.73 s | 3.15 s |
  | The Imitation Game (SDR) | 0.39 s | 3.13 s | 2.77 s | 3.18 s |

  The HDR/DV switch does not start a new HDCP round and falls within the wait time of the mode switch. On this chain Android reports no dropped HDMI audio during the switch (though sometimes an `onAudioPortListUpdate` just before HDCP is done), so the new wait logic changes nothing here.
- **Built:** waiting for a returning HDMI audio output (max. 6 s), a Start delay setting, and one restart on an error within 10 s after a mode switch.
- **For the user to test:** with dynamic range on, start a few movies (Casino, Interstellar, The Imitation Game): does the sound start cleanly, without a click?
- **PCM as substitute** (2026-10-10): after EAC3/AC3, a PCM track in the same language now also counts, but only with at least as many channels as the main track. The Imitation Game has only stereo PCM alongside DTS-HD MA 5.1, so it stays via the server (AC3 5.1, passthrough; checked on the Chromecast). In the library it is the only item with PCM alongside a lossless main track; the rule is for other libraries (Blu-ray LPCM 5.1/7.1).
- **Local decoding of TrueHD/DTS is on by default** (2026-10-10), after confirming that the Sonos receives multichannel PCM 7.1. 247 items with TrueHD/DTS without an EAC3/AC3 track no longer need the server to transcode; Casino now plays DTS-HD MA locally (was the AC3 track). EAC3 Atmos still takes precedence (Interstellar). Only if the output can handle ≥ 6 channels of PCM; otherwise (soundbar via ARC) the server keeps transcoding.
- **Measured (profile build, ~45 s of playback):** Interstellar (EAC3 passthrough) 33–43% of one core, 228 MB PSS; Fellowship of the Ring (TrueHD 7.1 → PCM 7.1 via `ffmpegLavc60.3.100-truehd`) 40–53%, 240 MB PSS; Casino (DTS-HD MA via `…-dca`) → PCM 7.1. No dropped frames or underruns. Note: PSS during 4K playback is above the 200 MB of the budget (that applies to browsing); mostly the buffer (max. 128 MB). Measured earlier during playback: 172 MB with Ted Lasso (lower bitrate).

### More robust playback on other devices, step 3 (2026-10-10)

B4, B5 and the choice from §5 of `docs/research/playback-on-other-devices.md`.

- **Settings → Advanced playback:** frame rate (always/system/off, default always = as it was), per format Auto/On/Off, and Stereo only. The old debug setting `match_frame_rate = false` becomes "off".
- **Mode choice:** falls back to another resolution (≥ video and ≥ 720p) if the current one does not have the rate; a seamless switch does not wait for the HDMI resync. Nothing changed on the Chromecast: all rates exist in 4K, and it reports no seamless rates.
- **Tested on the Chromecast (debug):** screen opens, column scrolls along, explanation per button, Back puts the focus on Advanced playback. Passthrough AC3 off → Game of Thrones 8x6 plays AC3 via `ffmpegLavc60.3.100-ac3` as PCM 6ch (was passthrough); then set back to Auto. Mode switch to 23.976 Hz and "stable after 2528 ms" as before.
- In a debug build, Settings with the extra "Playback test" button sits just outside the safe area (header at ~13 dp); in release that button is gone.

### More robust playback on other devices, step 2 (2026-10-10)

B1, B7, B9 from `docs/research/playback-on-other-devices.md`.

- **Measured on the Chromecast:** HEVC level 153 (5.1), H.264 51 (5.1), VP9 profile 2 yes, AV1 not in hardware; max. H.264/VP9 4096×4096, MPEG-2 1920×1088, MPEG-4 896×896. Output HDMI, passthrough AC3 and EAC3 (also tested with 6 channels), PCM 8 channels. The library does not go above HEVC 5.1 / H.264 4.1, so the new level limit changes nothing here.
- Interstellar (DV decoder, EAC3 7.1 passthrough) and Casino (HDR10, AC3 via passthrough instead of DTS:X) play as before.
- New in Settings (device line): the audio output.
- Not tested on another device: the KnownDefects rules (Fire TV, Shield, MediaTek) exist only in unit tests.

### More robust playback on other devices, step 1 (2026-10-10)

From `docs/research/playback-on-other-devices.md` (A1, A3, A4, B2, B3, B8). Rules in `playback.md`.

- **Error recovery** (`core/playback/PlaybackRecovery.kt`): an error during playback first picks a fallback. Network → retry once; file → remux, then transcode; audio → no passthrough, then stereo AAC; video → retry once, then transcode. Before, only a file error during direct play went to a remux; every decoder or audio output error went straight to the error screen.
- **Buffer** = min(default ~138 MB, heap/2). On the Chromecast 128 MB (was ~138 MB).
- **Audio output changes during playback:** the player then chooses again (`setAllowInvalidateSelectionsOnRendererCapabilitiesChange`). Checked that this does nothing on the Chromecast at the mode switch: Interstellar and Game of Thrones 8x6 each have one audio renderer, no switch.
- **Stereo TV/Bluetooth:** transcoding delivers as many channels as the output can handle (was always 5.1); without passthrough, an EAC3/AC3 track that the device decodes itself wins over TrueHD/DTS.
- **HDR of the screen:** the list of the whole screen plus that of the current mode; if the screen is off, the previous measurement. p7 with a p7 decoder on a screen without DV → HDR10 base layer.
- **Dolby surround via FFmpeg** instead of a generic platform decoder (which often mixes down to stereo), except a real Dolby decoder. Nothing changes on the Chromecast: it has no platform AC3/EAC3 decoder.
- **Tested on the Chromecast (debug, 2026-10-10):** Interstellar: DV decoder (`dvhe.08.06`), EAC3 7.1 passthrough. Game of Thrones 8x6: p7 via `c2.amlogic.hevc.decoder`, AC3 5.1 passthrough. Alone Together: AAC → PCM 2ch. No fallback needed, no errors.
- **Not tested on the device:** the fallback steps themselves (no error to trigger; unit tests only), a different audio output, a second device.
- Anyone But You (test file 8) now gives a 404 on `PlaybackInfo`: the item id no longer exists on the server (rescanned?). Update the test list.

### Continue watching by time (2026-10-10)

- Next-up from the server (`/Shows/NextUp`) was based on the highest fully watched episode and ignored in-progress episodes. Ted Lasso (S1 · E1 watched, S2 · E1 and S2 · E3 started) therefore gave S1 · E2. Checked in the server source (`TVSeriesManager.cs`).
- Now by time, like Netflix, Apple TV and Plex: per series the most recently touched episode (`UserData.LastPlayedDate`; also set by "mark as watched"). In progress = resume, watched = the first unwatched one after it. Rule: `continueEpisode` in `core/model/Library.kt`, shared by the Continue watching row, the Continue button on the series page and the suggestion on Home.
- Requests for Continue watching: `/Items` with episodes by `DatePlayed` (300, without images), for each series where the last one is fully watched `/Shows/{id}/Episodes?startItemId=…` (light), then the chosen episodes in full in one `/Items?ids=…`. The series page computes Continue from the episodes it loads anyway (one request fewer than before).
- **Bug: Continue watching stayed ~1 minute stale after stopping.** On release, the player still reported a `Progress` after `Stopped` (pause event from ExoPlayer). The server then thought playback was still going: `/Shows/{id}/Episodes` already gave the new state, but `/Items` (sorted by `DatePlayed`) only after about a minute. Now `PlaybackReporter` ignores progress from a plan that has stopped (`StoppedSessions`). Tested on the Chromecast: series page → play S3 · E2 → stop → Back → Back: the section shows S3 · E2 right away (before: S1 · E2 until you switched sections).
- Checked on the Chromecast with the real timestamps: for Ted Lasso, S1 · E2 (31 s, 12:53 UTC) was touched last, after S2 · E1 (12:19) and S2 · E3 (12:14). So Continue watching correctly shows S1 · E2.

### Surprise me becomes the Home section (2026-10-10)

- Section bar: Home · Series · Movies · Library. The Home section is the old Surprise me, simplified: one suggestion with only Play/Resume. The choice chips (Movie/Series/Both) and "Other" are gone, as is the `surprise_kind` preference (the old key in DataStore is ignored).
- Suggestion: an unwatched movie (`isPlayed=false`, random on the server) or a started series from next-up (`isStarted`, like the Continue watching row). Both groups 50/50; if one group is empty, the other. Two requests at once, plus the series itself if it becomes a series.
- Back from the player refreshes the suggestion: movie fetched again (`getMovie`, watched = new suggestion), series via next-up (no longer in progress = new suggestion). If that fails, the old one stays. Tested on the Chromecast: Ted Lasso S1 · E2 → watch 25 s → Back gives "29 min left" and Resume, focus on the button.
- **Bug (2026-10-12, 0.1.41): Home only refreshed after playback started from Home itself.** A movie started via Movies only appeared after a restart. Now the suggestion stack refreshes every time the section appears (the old stack stays until the new one is there). Tested on the Chromecast.
- **Bug (2026-10-12, 0.1.41): the gradient over the backdrop lagged behind.** The gradient faded with its own ease animation that lagged behind Coil's linear crossfade, so the image was too light at first. Now the gradient is on as soon as the image is in (the gradient is in the background color, so without an image it changes nothing). Tested on the Chromecast.
- In the code: `feature/home/HomeSuggestion*` (previously `feature/surprise/Surprise*`); strings `home_play`, `home_resume`, `home_empty`, and `library_kind_*` for the Library chips.
- The app always opens on Home; the `last_section` preference is gone (the old key in DataStore is ignored). Within a session the section stays: Back from detail or player lands on the same section (HomeViewModel belongs to the Home entry on the back stack).

### New name: JellyCast (2026-10-10)

- The app is called **JellyCast** (was Jelzefin): package and applicationId `nl.jellycast.tv`, classes `JellyCast…`, client name on the server "JellyCast". The name does not exist in the stores or as a known brand; on GitHub there are a few small Jellyfin projects with that name.
- New applicationId = a different app for Android: the old `nl.jelzefin.tv` stays on the TV until you remove it (`adb uninstall nl.jelzefin.tv`), and JellyCast starts without login and preferences.
- Deliberately not renamed: the keystore (`~/keys/jelzefin.jks`, alias `jelzefin`), the Paper file "jelzefin" and the project folder. Icon and banner: initials **JC** (was JF), banner with "JellyCast". In Paper the launcher foreground is its own artboard, so it can be exported directly from now on.

### Multilingual interface (2026-10-10)

- **Setup:** one `values-xx/strings.xml` per language (and the default in `values/`, es, fr, nl), technical strings in `values/donottranslate.xml`. `generateLocaleConfig` builds the language list from the folders; checked: only `nl` and `en` in the trial, the translations of Media3/tv-material do *not* end up in it. So a new language is just a file (see `docs/translating.md`).
- **Switching:** framework `LocaleManager` (API 33+, no AppCompat needed with minSdk 34). Tested on the Google TV emulator (API 34): switching, remembered after force-stop, Back to Home works; Android sets the app language itself in its settings. On the first start `JellyCastApp` sets the default language (now `en`) if nothing has been chosen yet (otherwise Android follows the TV language). Tested on the Chromecast (release 0.1.9 of this branch, 2026-10-10): first start English, switching via Settings (focus stays on the language button), remembered after force-stop, a change from outside (`adb`) visible immediately, no crashes. Cold start 1.3 s, warm 0.7 s. Note: a debug build stays at `verify` with `compile -m speed` (debuggable); for the TV always use the release build.
- **Lint pitfall:** `tools:locale="nl"` on `values/strings.xml` silently disabled `MissingTranslation` (a half-English file gave no error). Removed; `MissingTranslation`/`ExtraTranslation` are set as errors in `app/lint.xml`, tested by removing a French string. After that English became the default language (`values/` = English, Dutch in `values-nl/`, `unqualifiedResLocale=en`, `DEFAULT_LANGUAGE_TAG = "en"`), so the spell check is correct again without an exception.
- **Language changed from outside** (Android per-app settings, `adb shell cmd locale set-app-locales`): `DefaultLanguageRepository` listens to `onConfigurationChanged`, otherwise Settings kept showing the old language (found on the emulator 2026-10-10).
- **Genres:** the translated name and the A–Z order come from the chosen language (`ResourceGenreNames`, Collator: "Épouvante" under E). The ViewModel survives a language switch; the shelves are correct again on the next load (`onShown`).
- **Length:** French/Spanish are longer. Adjusted: Settings explanation moved to bottom left, Continue button on the series page 240 → 300 dp, ellipsis on `AuthButton` and on the second text of `PillButton`. Checked on the emulator in es/fr: Settings, Home (Series, Movies detail, Surprise me), series page.

### Strange sound when playback starts (2026-10-09)

Investigated with logcat on the Chromecast (`profile` build) with Casino (AC3 alongside DTS:X), Interstellar (EAC3 Atmos), Band of Brothers 1x1 (EAC3), Alone Together (AAC → PCM 2ch) and Jurassic World Dominion (AAC → PCM 8ch). Times from the playback plan.

- **Ruled out:** per start the player creates exactly one AudioTrack, directly in the final format (AC3 5.1, EAC3 5.1/7.1 or PCM 2/8ch). No temporary track (Casino no longer starts with DTS→PCM: `holdTracksUntilChosen` works), no switch between PCM and passthrough, no FFmpeg decoding for these titles. Hypotheses (a), (b) and (d) are eliminated.
- **Found (hypothesis c):** the switch to 23.976/29.97 Hz re-establishes the HDMI connection. Android reports the new mode after ~0.5 s, the picture is up after ~0.8 s, but HDCP 2.2 is only done at **2.7–2.9 s** (`hdcp_tx22: [ESM] Encryption enabled`, always 2.1–2.3 s after the mode report). The sound already started after **1.0–1.3 s** (Interstellar 2.4 s), so in the middle of the resync: the bitstream began while the TV and soundbar were still resynchronizing, and the HDCP switchover fell 0.4–1.8 s into the first sound. Without a mode switch (`--ez debugMatchFrameRate false`) there is not a single HDMI/HDCP event after the start.
- **Side finding:** Band of Brothers gave `AudioTrack: … disabled due to previous underrun, restarting` at the start (2× out of 2 with a mode switch, 1× out of 3 without). The HAL only starts the bitstream after that restart, so probably inaudible; still something to watch.
- **Why "not with every movie":** presumably mostly with passthrough (AC3/EAC3): a bitstream that the TV does not recognize properly during a resync sounds like noise or a click; PCM at most drops out briefly. How annoying it sounds depends on where in the sound the HDCP switchover falls.
- **Fix:** the player prepares right away (first frame as fast as before), but only starts playing once the chosen track is set and the new display mode has been active for 2.5 s (`displaySettled` in `core/playback/DisplaySettle.kt`; timeout 3 s if the mode does not come). Without a mode switch it starts directly. A pause by the user is not lifted by a later mode switch.
- **Measured after the fix** (profile build): playback starts 3.2–3.3 s after the plan, always 0.3–0.5 s after HDCP encryption; no underruns in 3× Band of Brothers with a mode switch. First frame unchanged:

  | Title | First frame before → after | Sound/motion starts before → after |
  |---|---|---|
  | Casino | 1.0–1.2 → 1.1 s | 1.1–1.3 → 3.2–3.3 s |
  | Band of Brothers 1x1 | 0.9 → 0.9 s | 1.0 → 3.2–3.3 s |
  | Interstellar | 2.3 → 2.8 s (variance) | 2.4 → 3.3 s |
  | Alone Together | 0.9 → 0.9 s | 1.0 → 3.2 s |
  | Jurassic World Dominion | 1.0 → 0.9 s | 1.2 → 3.3 s |

  The cost: with a mode switch, the first frame stands still for ~2 s before it starts moving. The budget (≤ 3 s to first frame) is still met.
- **For the user to test:** see Open items.
- Not solved: when the app comes back from the background, it creates a new player while the TV still has the old mode; the mode report then only comes ~0.5 s later. Rare; not measured yet.
- Possible speed-up later: start the mode switch already on the click (frame rate from the detail data), so part of the wait time overlaps with fetching the plan.

### Phase 7 — Reassessment against jellyfin-androidtv (2026-10-09)

- Fetched jellyfin-androidtv again: nothing changed since `09c912d` (2026-10-06). Decisions per candidate in `docs/research/jellyfin-androidtv-comparison.md` → section 5.
- Adopted: Coil `serviceLoaderEnabled(false)`. Tested on the Chromecast (release 0.1.5): images load on Series Home and in the bookcase (posters, logos, backdrops); cold start 0.49–0.79 s, same as before (any measurable difference falls within the spread).
- Library checked via the server API: no video above level 5.1, `RefFrames` always 1 (unusable), at most 8 audio channels.
- Blow (VC-1 + PGS): on a transcode the server burns in PGS itself (`SubtitleMethod=Encode`), even with only `Embed` in the profile.

### Scroll budget Series Home (2026-10-09, after phase 6)

Investigated with Perfetto (system trace, CPU sampling and composition tracing in the `profile` build):
- **The biggest deception was in the measuring.** Right after `adb install` the Compose code runs in the ART interpreter (CPU samples full of `nterp`/`ClassLinker::ResolveMethod`); `cmd package compile -m speed-profile` right after installing does not help, because the JIT profile is still empty then. Measured that way, even the phase 3 commit (5a8f547) gave 15–19%. On the TV, Android compiles after use during the nightly maintenance. New measurement script: `scripts/perf/measure-steady.sh <apk> [rows|left-right]` (install, use, `speed-profile`, then measure 3x). Full AOT (`-m speed`) gives about the same.
- **↓/↑ builds a whole row per key press**, synchronously during the key press (focus search off screen). In the interpreter that cost 40–50 ms per key press. Per card, tv-material `Surface` cost ~1.7 ms of the ~2.2 ms. New `FocusCard` without `Surface` (bare `Box` with `clickable`, scale in `graphicsLayer`, border in the draw phase): ~0.6 ms per card, and a focus change no longer recomposes anything. ↑ went from 44 to ~2 ms per key press. `tv-ux.md` updated.
- **After that the GPU/RenderThread is the limit** (UI thread 3–5 ms per key press, RenderThread ~10–15 ms, GPU clock switches between 285 and 846 MHz). The top-right backdrop cost ~3 percentage points: the offscreen texture is now half resolution (720×405 px, drawn ×2), as good as no backdrop at all. Tried without effect: no rounded clip on cards. SurfaceFlinger scales via the hardware (one layer, `DEVICE`), not via the GPU.
- With key repeat the app draws ~20 frames per second (one per step); almost all missed frames fall while holding the key, not when the hero changes.

| Series Home, release, steady (`measure-steady.sh`) | rows | left-right |
|---|---|---|
| Before (phase 6 code) | 8–9% (once 15%) | – |
| After (`FocusCard` + half backdrop) | **5.0–5.6%** | 12–13% |

- **User decision (2026-10-09): acceptable as is.** ←/→ on the Continue watching row stays above budget (12–13%, GPU median 10 ms); that is in RenderThread/GPU, no longer in composition. Possible next steps if it does bother on the TV: fewer RenderNodes per card (no own layer without focus), remove the text under the landscape cards, or a baseline profile so the first day after installing is also compiled.

### Phase 6 — Wrap-up (2026-10-09, partial)

- **Settings** (same layout as the login screens): Back to Home (focus), Other server, Log out, Quality, Burn in ASS. Below the buttons is the explanation of the focused playback setting (fixed height of 3 lines, so nothing shifts). Bottom left: version and debug info of the device: video decoders, max. resolution, HDR, passthrough and PCM channels. On the Chromecast: "HEVC Main10, H.264, VP9, MPEG-2, MPEG-4 (max. 4K) · Dolby Vision (4, 5, 8, 9), HDR10, HLG · passthrough AC3, EAC3 · PCM 8 channels". The decoder reports 4096×4096 (also portrait), so only the width counts.
- **Other server** opens the server screen while you stay logged in; only once the new server is saved does the login go away (Login follows). Back = nothing changed, focus back on "Other server".
- **Quality limit:** none (200 Mbps) → 80 → 40 → 20 → 10 Mbps, each OK the next. Tested with Casino (72 Mbps) at 10 Mbps: server transcodes (`ContainerBitrateExceedsLimit`) to HEVC 1440p **SDR** (tonemap), AC3 passthrough; plays. So a limit costs HDR.
- **Marking series as watched** (design choice by the user, see `design.md`): buttons "Mark S·E as watched" and "Mark series as watched" after Continue, and a card "Mark season as watched" at the end of each season row. Season and series are one request each (`markPlayedItem` on the season or series id; the server marks all episodes). The status changes on screen immediately; then the page reloads (correct Continue, and a failed mark falls back to the server state). While marks are in flight, an older load result is ignored, so pressing OK quickly in succession does not jump back. Tested on the TV: Shameless season 2 there and back, Queen's Gambit 7× OK (the last one puts focus on "Series watched") and back; the server was correct after every step. Note: marking as unwatched also clears the progress (that is how the server works).
- **Measurement at wrap-up (release 0.1.3, after `speed-profile`):** cold start 0.5–1.0 s, warm start 0.11 s, memory 130 MB PSS (Graphics 58 MB). **Series Home 13–21% janky frames** (`measure-scroll.sh rows`, budget < 5%). No regression from phase 6: the commit before it (246eea2) gave 17–20% under the same conditions. In phase 3 it was 5–8%; so it has risen since phase 4/5, or the content (Continue watching, rows) differs. To investigate before new features (product-principles.md).
- UI tests (`TvRowFocusTest`) green on the Chromecast; lint 0 errors.
- **Bug found and fixed:** the playback plan read `settings.value`, which right after startup is still the default (DataStore not read yet). With the debug start a 10 Mbps limit was ignored (DirectPlay). Now `PlaybackSettingsStore.current()`, which waits for the saved value (player and tech line).

### Icon, banner and login screens (2026-10-09)

- Icon and banner exported from Paper. The launcher icon is adaptive: amber (`@color/brand_amber`) as background, band + "JF" as PNG foreground per density (`mipmap-*/ic_launcher_foreground.png`). The 512 design fills the visible part (72 of 108 dp); "JF" falls well within the safe zone of 66 dp.
- Server screen, Quick Connect and password share one layout (`AuthLayout`): brand on the left, content on the right, server bottom left. The brand icon is a PNG (`drawable-xhdpi/brand_icon.png`, 296 px), not a Compose drawing, so it is exactly the same as the launcher icon.
- The background glow is one WebP of **960×540** (lossless, 78 KB, ~2 MB in memory) instead of the 1920×1080 from `design.md`: for a soft glow the lower resolution is not visible, and it saves ~6 MB. Lossy WebP gave visible blocks in the dark.
- Back on the password page goes to the code page (not if Quick Connect is off). Back from "Other server" puts the focus back on that button.
- Checked in the emulator: D-pad ↓ to "Other server", OK/Back between the two pages, focus restore. Not yet looked at on the Chromecast (would require logging out).

### Phase 5 — Player polish (2026-10-09)

- **Own overlay** on `media3-ui-compose` (`ContentFrame` + state holders) with tv-material, following Paper "Player — overlay": title, "S1 · E2  Title", time bar, times and one row of outline icons. Every key press shows it; after 4 s without input during playback it goes away, when paused it stays. Back in steps: menu closed → card gone → overlay gone → stop.
- **Seeking** on the time bar: 10 s per press, after holding for 1 s 30 s per step; the player only seeks once the key has been still for 400 ms (otherwise it seeks at every step). On the TV: 20 minutes in ±2 s of holding.
- **Audio and subtitle menu:** panel on the right, tracks with the name from the file (otherwise language, otherwise "Track n") and a tech line ("EAC3 · 5.1", "PGS"). Subtitles: Off, tracks, and "Style" (size small/normal/large, background on/off), remembered in DataStore. Media3 converts subtitles from MKV to `application/x-media3-cues`; the real codec is then in `Format.codecs` (`codecName`, with test).
- **Subtitles** via Media3's `SubtitleView` (`ContentFrame` does not draw them). Tested with SRT on Game of Thrones 1x1 (size Large).
- **Next episode:** card bottom right in the last 30 s, a circle counts down from 10; OK = now, Back = card gone and stop at the end instead of continuing. Without the card (Back not pressed) the series continues automatically at the end. Also via the "next" icon in the overlay. Tested: Dark Matter 1x1 → card, Back → after the end back on Home; Ted Lasso E5 → E6 via the icon.
- **New player per episode** instead of reuse: the decoder choice (DV via HEVC) is fixed at creation. `playback.md` updated.
- **MediaSession** added (was in `playback.md`, still missing): `cmd media_session dispatch pause/play` works, and after closing the session is gone.
- **"Burn in ASS"** as a setting (Settings): ASS/SSA then as `ENCODE` in the DeviceProfile, the server burns them in (= video transcode on the N150). Off by default.
- **Skip intro: not done.** The server delivers no intro segments (no intro plugin installed). Only pick up once that plugin is there.
- **Bug found and fixed (Home, Series):** after watching, the series in "Continue watching" moved to position 1 and focus was lost (no element had focus). Two causes: the card had the id of the episode (after "next episode" a different card), and the row stayed put while the focused card moved off screen. Now the card has the id of the series, and `TvRow` scrolls along in the same frame when new data moves the focused card (`requestScrollToItem`).
- **Friends (fallback remux via HLS):** the duration in the HLS playlist is ~30–45 s longer than the stream, so the episode ends before the "Next" card appears (it does continue playing then). The ASS subtitles do not show up in that remux (track is called "Track 1", no codec). Edge case of the fallback; see Open items. Cause (2026-10-09): the file is damaged; the app now reads through it, see Open items.
- **Measurement (release, Ted Lasso, overlay visible, 30 s ←/→ held on the time bar):** 6.6% janky frames (472 frames, p50 31 ms). Slightly above the Home budget of 5%, while the player is actually seeking at the same time. Memory during playback: 172 MB PSS (budget 200).

### Phase 4 — Series page and movie detail (2026-10-09)

- **Instantly on screen:** the repository remembers the last loaded movies and series (models only). The movie detail and the header of the series page are there in the frame of the click; then the screen refreshes. Episodes are preloaded when focus on Home rests ~300 ms on a series poster: on the TV the whole series page (Ted Lasso, 4 seasons) was there within 0.4 s.
- **Tech line** from the same playback plan as the player (DeviceProfile, track choice), e.g. "4K · DOLBY VISION · ATMOS · DIRECT PLAY". Requesting a plan does not start a stream yet. Fills in within ~0.3 s.
- **Watched** toggles on screen immediately; if it fails at the server, it reverts.
- **Bug found and fixed:** back from the player, focus landed on episode 1 instead of on the episode that was playing. Cause: `focusProperties.onEnter` (entering a row = item 1) also fires on a focus request from code. Now `onEnter` only redirects on an arrow key (`isArrowKey`), in all four places. Captured in a UI test (`TvRowFocusTest`) that fails without the fix. The same bug was in the Series rows on Home.
- **Overdraw movie detail:** backdrop + gradient over the whole screen cost ~22 ms GPU per frame on a focus change; now rendered offscreen once (~8 MB): 14 ms, 5% janky frames across the buttons.
- Series page with key repeat (Ted Lasso): 4–10% janky frames, comparable to Series Home.
- **Not done yet:** the scale/slide transition bookcase → movie detail (now a 200 ms crossfade, see `design.md`).

### Phase 3 — Home: Series, Movies and Surprise me (2026-10-09)

Built according to the Paper design. Choices and findings:
- **Two requests per section.** Movies: one `/Items` with `MediaStreams` (resolution, HDR, Atmos for the spine; 44 movies = 600 KB, 0.3 s on the server). Series: `/Items` + Continue watching (since 2026-10-10 by time, see above; previously `/Shows/NextUp`). The app groups Continue watching, New, A–Z and genres itself. Surprise me: `sortBy=Random&limit=1`, for a series then next-up (never started or all watched → episode 1).
- **Spine color from the BlurHash** (characters 2–5 = average color). Checked against the whole library: logos with transparency do not give a skewed dark average; dark logos (Usual Suspects, Godfather) get a light spine.
- **Atmos on the spine only for EAC3-JOC:** TrueHD Atmos plays as AC3 here, so that label would promise what does not arrive.
- **OK plays directly for now** (movie: resume; series: the next episode). Movie detail and series page follow in phase 4.
- **Playback test** is now under Settings (debug/profile only). Removed 2026-10-11 (the test set was from the dev server); testing playback can still be done with `--es debugPlayItem <id>` on `am start`.
- **Focus:** Back in two steps (starting point → section bar → app closes), entering a row = item 1, no wrap-around (not to the settings icon either), focus restore after the player (tested: bookcase comes back on the same movie). Key presses during a shelf change are saved up and executed afterwards (the debug build takes ~1.8 s for a change; without that, focus was lost).
- **Measure with real key repeat.** `adb shell input keyevent 22 22 …` sends keys without pause (several steps per frame) and measures too heavy. `scripts/perf/measure-scroll.sh` creates a virtual keyboard via `uinput`, so Android does the repeating itself (400 ms, then every 50 ms).

Feedback from the user on the TV (2026-10-09), and what was done with it:
- **Surprise me:** "works really great, don't change anything."
- **Glitch on Back from the player (Disclosure Day):** ~1.2 s of a black area over the bookcase, exactly the video area (2.39:1). Cause: Navigation 3 fades for 700 ms by default, and the video `SurfaceView` ignores transparency. Fixed: no transition to and from the player, other screen changes 200 ms (recording after the fix: straight to the bookcase).
- **Bookcase, second round:** amber line at the top of the panel removed; black fade with ‹/› on the side where movies are still off screen. Measured: the fades cost nothing measurable (fresh install with and without fades both 14–17%; the earlier 10–12% was after many runs with a warm JIT, so the spread between measurements is large).
- **Bookcase too full:** 6 dp below the bar and the safe area of 27 dp at the bottom kept clear; spines 44 × 431 dp, logo 212 dp long.
- **Bookcase, third round (2026-10-09):** the description in the panel has been replaced by the buttons Watch/Continue watching and Details (stacked; ↓ = Details, another ↓ = next shelf). Movie detail shows the full description. Measured on the release build (30 s ←/→): 5.5% and 12%, within the known spread of the bookcase; the buttons do not make it heavier. Tested on the TV: ↓/↑/Back in the panel, OK plays directly, Details → movie detail → Back lands on Details, Back from the player on the same movie.
- **Hard bottom edge of the Series backdrop:** fade to the background in the bottom 40% (in the offscreen texture, costs nothing per frame).
- **Series tiles:** episode card = play directly (built that way); series poster = series page (phase 4, recorded in `design-screens.md`).
- **"Loading feels buggy after watching or a lot of scrolling":** probably images that drop out of the memory cache and visibly reload. Backdrops (~3.7 MB each) no longer go into the memory cache, which is now 20%. In debug/profile Coil logs the source per image (memory/disk/network): still to confirm with that on the TV. Consequence for phase 4: the transition bookcase → movie detail counted on the backdrop from the memory cache; look at that again there.

Performance budget, release build on the Chromecast (2026-10-09):

| Measurement | Budget | Measured |
|---|---|---|
| Cold start | ≤ 3 s | **0.7 s** to first frame; content (Movies, 600 KB) within 1.5 s |
| Warm start | ≤ 1 s | **0.1 s** |
| Memory after ~5 min browsing | ≤ 200 MB PSS | **111 MB** (Graphics 41 MB) |
| Janky frames, bookcase (30 s ←/→ held, All movies) | < 5% | **10–12%** ❌ (first version: 44%) |
| Janky frames, Series (30 s ↓↑→← held) | < 5% | **5–8%** ❌ (first version: 15–20%) |

What reduced the jank (measured with atrace, per step):
- Bookcase: one fixed panel as an overlay instead of an item that switches between spine and panel; spines cached offscreen (`CompositingStrategy.Offscreen`, ~0.3 MB per spine); scrolling per step with `dispatchRawDelta` and a cache window (next spines built in advance); per spine `derivedStateOf` and stable callbacks (2 instead of ~25 items recomposed); panel shows only spine color + logo while browsing.
- Everywhere: own draw layers for the section bar and bookcase, no second full-screen background, Coil with `Size.ORIGINAL` (cache hit in the same frame), placeholder via Coil (posters from 4× to 2× overdraw), top-right backdrop offscreen (radial gradient not every frame), cache windows on rows and the row area.
- AOT compilation (`cmd package compile -m speed-profile`) helped Series a bit (→ 5–8%), not the bookcase.

Where it still is:
- **Bookcase:** per step ~10 ms UI thread (focus change + recomposing the panel + recording) and ~10 ms RenderThread, of which ~5 ms is fixed cost for swapping buffers on this device. Per step ~2.2 frames are drawn instead of 1; the cause of that second frame has not been found yet (not the panel logo: without the logo it got worse).
- **Series:** the GPU sits at ~14 ms per frame while scrolling (Mali-G31: backdrop + posters + window).

### Phase 2 — playback (2026-10-08/09, partial)

Test set run on the Chromecast (results in the codec table). Found and fixed:
- **Track choice only counts with `MediaSourceId`.** Without it the server ignores `AudioStreamIndex`/`SubtitleStreamIndex` (confirmed via the API). Always send it; with a single version = item id without dashes.
- **Do not send a subtitle (`SubtitleStreamIndex=-1`).** Otherwise the server picks one itself and wants to burn in image subtitles → full video transcode.
- **HLS: `main.m3u8` instead of `master.m3u8`.** Besides the copy variant, the master playlist also contains SDR transcode variants (`AllowVideoStreamCopy=false`); ExoPlayer picked those. With `main.m3u8` it stays a copy: only transcoding audio = server `DirectStream`, video untouched.
- **No `External` subtitle profile.** With it, the server extracted all text subtitles from the file on every start (reading the whole movie from the HDD).
- **Fallback:** if Media3 cannot read a file (MKV error on Friends, M2TS on The Usual Suspects), then retry once with a remux by the server. Works.
- Jellyfin ids have no dashes: always `toUUID()` from the SDK. The application context has no display: `DisplayManager.getDisplay(DEFAULT_DISPLAY)`.
- QuickSync on the N150 works: 4K HDR → 1080p SDR (tonemap + burn-in) at 2× realtime. Usable as an emergency fallback, never as the default.

Phase 2b (2026-10-09), tested by the user and with the `profile` build:
- ✅ DV profile 7 via the HEVC decoder (Anyone But You): picture good. DV p5 and p8.1 show "Dolby Vision" on the LG, Atmos on the Sonos.
- ✅ Display mode switches to 23.976 Hz (Casino, Interstellar, Anyone But You).
- ✅ Commentary track bug fixed: tracks are matched by **name** (`matchTrack`), not by position; commentary is never the replacement. Anyone But You now plays "Compatibility Track" (AC3 5.1).
- ✅ GoT 8x6 (117 Mbps) plays directly and smoothly over Wi-Fi.
- ⚠️ **Start time with large remuxes** (time from playback plan to first frame, `profile` build):

  | File | Tracks | Seek index (Cues) | First frame |
  |---|---|---|---|
  | Alone, GoT 1x1, Dark Matter, Band of Brothers | 4–44 | – | 1.0–1.6 s |
  | Casino | 21 | – | 2.5 s |
  | Anyone But You | 69 | – | 4.1 s |
  | Interstellar | 91 | 7 MB | 7.6 s |

  Cause: Media3 reads and processes the whole MKV seek index (Cues, at the end of the file) before the first frame. In remuxes with dozens of subtitle tracks there is a cue per subtitle; parsing costs seconds on the Cortex-A55. Server and network are fast (first byte 0.1 s, 76 MB/s). The mode switch to 23.976 Hz costs nothing extra. Outside budget (≤ 3 s): to be fixed.
- ✅ **Fast seek index** (`core/playback/mkv/FastCuesMatroskaExtractor`): the MKV Cues are read in one go as bytes and only video/audio points go to Media3 (subtitle points are dropped). Measured (profile build, plan → first frame): Interstellar 7.6 → **2.7 s**, Anyone But You 4.1 → **1.3 s**, Casino 2.5 → **1.3 s**, Band of Brothers 1.6 → **0.8 s**. Everything remains seekable.
- ✅ Casino: HDMI output is at **23.98 Hz** during playback (`dumpsys SurfaceFlinger`: `activeMode=23.98 Hz`). The LG's VRR screen always shows 60 Hz; that says nothing about the input signal.
- ✅ Interstellar now seeks fast (user, after `CLOSEST_SYNC`).
- Seeking to the nearest keyframe (`SeekParameters.CLOSEST_SYNC`) and the decoder scan once per app start: added, still to be tested by the user.
- New build type **`profile`**: release speed (R8, not debuggable) with logs, debug start and overlay. `./gradlew assembleProfile` → `app/build/outputs/apk/profile/app-profile.apk`. Debug builds are 2–3× slower at starting (Interstellar: 20+ s in debug).

Testing:
- Video is on a separate hardware layer: `screencap` shows only the UI (black picture is normal). Check via the debug overlay, logcat and the server session (API).
- Debug start (debug and profile): `adb shell am start -n nl.jellycast.tv/.MainActivity --es debugPlayItem <id>`; `--ez debugMatchFrameRate false` turns off the mode switch.
- Server API from the terminal with `jellyfin.api_key` from `local.properties` (Android Studio escapes `:` as `\:`).
- The Chromecast goes to sleep when the TV turns off (CEC); "Stay awake" in the developer options helps during long test sessions.

### Phase 1 — connecting and logging in (2026-10-08)

- **Jellyfin SDK 1.8.12, not 1.9.x:** 1.9 requires Jellyfin 12; the server runs 10.11.11. Switch on a server upgrade.
- **Crash found on the device:** the SDK logs via SLF4J and crashed without an implementation (`NoClassDefFoundError: org/slf4j/LoggerFactory`). Fixed with `slf4j-timber`, just like the official app. Unit tests did not catch this: only a real network call on Android hits that code.
- **Text fields on TV:** Compose opens the keyboard on focus already, and ↓ then went to the keyboard. Fixed in `TvTextField` (read-only until OK, ↑/↓ move the focus). Recorded in `tv-ux.md`.
- **Focus restore on Login:** after Back from "Other server" the focus was on the first button; now on the button you came from.
- Navigation follows `AuthState`: after logging in to Home, after logging out or with a revoked token to Login. Starting while logged in goes straight to Home; the token is then checked in the background. The "token revoked" scenario is only covered by unit tests, not tested on the device.
- The token is stored unencrypted in the app's own DataStore (readable only by the app) and is excluded from backups (`data_extraction_rules.xml`).
- Performance budget, release build on the Chromecast: cold start **0.41 s** (logged in, straight to Home), warm start **0.07 s**, memory **44 MB PSS**. Well within budget.

### Phase 0 — skeleton (2026-10-08)

- AGP 9.4.1 has Kotlin built in: no `org.jetbrains.kotlin.android` plugin. The Kotlin version (2.4.21) comes onto the classpath via the Compose plugin. Android Lint internally uses its own Kotlin compiler (2.2.21), that is normal.
- `compileSdk`/`targetSdk` 37, `minSdk` 34. Debug and release both signed with `~/keys/jelzefin.jks`, so switching between debug and release works without uninstalling.
- Cleartext is enabled globally in `network_security_config.xml`: Android cannot restrict it per IP range, and the server URL is entered in the app.
- ~~Banner and icon are temporary~~: replaced by the JF design from Paper (2026-10-09).
- Measured on the Chromecast: debug build first start 10.5 s (no compiled code). Release build cold start **3.9 s** (first start, before the baseline profile is applied), memory **55 MB PSS**. APK: debug 35 MB, release 1.6 MB (R8).
- D-pad tested with `adb shell input keyevent`: left/right, OK to Settings, Back restores focus on "Settings", Back on Home closes the app.

## Dev server

- Jellyfin **10.11.11** on **Windows** (x64, no Docker), `http://192.168.1.4:8096` (fixed IP). Media Segments API available.
- Hardware (according to the user): **Intel N150** (4 E-cores, iGPU with QuickSync), **8 GB RAM**, media on an **HDD** (`D:\movies`, `D:\series`). Program data, cache and transcodes are on `C:\Users\homepc_han\AppData\Local\Jellyfin` (C: is presumably the system SSD; not verified).
- Test account `claude`, **admin** since 2026-10-08 (credentials in `local.properties`). The user's main account: `homepc_han`. The official Jellyfin app (0.19.10) and Wholphin are also in use on the TV.
- Server discovery (2026-10-10): the server answers the UDP broadcast on port 7359 (`{"Address":"http://192.168.1.4:8096","Name":"home server",…}`), also from the Chromecast. Does not work in the emulator (NAT), nor when "Automatic discovery" in Jellyfin (Networking) is off or the server runs in Docker without `7359/udp` or host networking.
- Libraries: `Movies` (44 movies), `Series` (41 series, 2023 episodes). No internet metadata providers active in the library options; plugins: TMDb, OMDb, AudioDB, MusicBrainz, Studio Images. No intro plugin.

### Server settings (checked 2026-10-08)

Conclusion: **well configured for this hardware and our approach.** Nothing needs to change for phase 2.

| Component | Setting | Verdict |
|---|---|---|
| Hardware acceleration | **Intel QuickSync (QSV)**, hardware decoding for H.264, HEVC, VC-1, MPEG-2, VP9, AV1; HW encoding on, HEVC encoding allowed, low-power encoders on | Good. Not proven to work: no transcodes in the logs of the last 3 days. Test with "Blow" (VC-1) in phase 2. Whether the N150 decodes VC-1 in hardware is uncertain; if not, the CPU does it (1080p, feasible). |
| Tonemapping | On (VPP, bt2390) | Only relevant for an HDR→SDR transcode, which JellyCast never requests. Fine as a fallback. |
| Throttling | Off | Good: throttling caused stutters on the Chromecast according to Jellyfin forums. |
| Delete segments | Off | Fine: transcodes are rare and are on C:. |
| Subtitle extraction | On | Reads the whole file from the HDD when used. JellyCast requests subtitles as `Embed`, so this does not apply. |
| Trickplay and chapter images | Off (per library) | Good for N150 + HDD. Trickplay (preview while seeking) is a later choice for phase 5: then with HW acceleration and "keyframe only", at night. |
| Bitrate limit (remote and per user) | 0 (no limit) | Good. |
| Network | LAN subnets not filled in (auto-detection), remote access on, no HTTPS | Fine for LAN. |
| Resume | Min. 5%, max. 90%, min. duration 300 s | Determines when the server considers something "watched" or "resumable"; JellyCast follows this. |
| Log retention | 3 days | Short; increase if needed when we have to trace server problems. |

Points of attention outside Jellyfin (to be validated by the user):
- [ ] Does Windows put the HDD to sleep ("Turn off hard disk after …" in Power Options)? Spinning up costs 5–10 s on the first movie, which breaks the budget of ≤ 3 s to the first frame.
- [ ] Is C: an SSD, and is there enough free space for cache and an occasional transcode?

## Emulator (set up 2026-10-09)

- AVD `Google_TV_1080p_API_34`: image `system-images;android-34;google-tv;arm64-v8a`, profile `tv_1080p`, RAM 2048 MB, hardware keyboard on (arrows/Enter = D-pad/OK), GPU host.
- Screen 1920×1080 at 320 dpi = 960×540 dp, same as the TV canvas.
- Use: iterate quickly on UI, focus and navigation. Not for video (different codecs) and not for performance (the Mac is much faster than the Chromecast).
- With the Chromecast also connected: pick the device with `ANDROID_SERIAL=emulator-5554` or `adb -s emulator-5554`.

## Target device (measured 2026-10-08)

- Chromecast with Google TV 4K (`sabrina`), Android 14, **API 34** (build `UTTC.250917.004`). Connection: `adb connect 192.168.1.115:5555`; after a reboot pair again via Wireless debugging and `adb tcpip 5555`.
- **32-bit userspace (`armeabi-v7a`).** Native libraries must have an armeabi-v7a variant.
- RAM 1.9 GB, **not** a low-RAM device. App heap: 256 MB (`heapgrowthlimit`), 384 MB with `largeHeap`.
- Panel 3840×2160. UI renders at **1920×1080, 320 dpi (xhdpi)**, so 960×540 dp.
- **Chain:** Chromecast → HDMI → **LG OLED55CS6L** → (e)ARC → **Sonos Arc**.
- **HDR (reported by the display):** Dolby Vision, HDR10, HLG. **No HDR10+**: that plays as HDR10.
- **Audio passthrough (HDMI profile):** **AC3, EAC3, EAC3-JOC (Atmos)**. **No DTS and no TrueHD.** `encoded_surround_output` = 0 (automatic).
- **Hardware video decoders (Amlogic):** H.264, HEVC, Dolby Vision (`dvhe`, `dvav`), VP9, MPEG-2, MPEG-4. **No VC-1.** AV1 only in software (`c2.android.av1.decoder`), so unusable for HD/4K.

Additional measurements (2026-10-08, `dumpsys media.player`, `/vendor/etc/media_codecs*.xml`, `dumpsys display`, `dumpsys media.audio_policy`, `dumpsys hdmi_control`, sysfs). Details in `docs/research/playback-engine.md`.
- **SoC and memory:** `AMLS905D3`; MemTotal 1.98 GB; `ro.config.low_ram` not set.
- **HEVC** (`c2.amlogic.hevc.decoder`):
  - Main, Main10, Main10HDR10 and Main10HDR10Plus @ level 5.1.
  - Max. 4096×2160 at 4K60.
  - 9 instances; measured 103 fps at 2160p.
- **H.264:** High @ 5.1, **no High10**; 4K up to 30 fps.
- **VP9:** profile 0, 2 and 3, including HDR/HDR+, @ 5.1.
- **MPEG-2 and MPEG-4:** up to 1080p.
- **Dolby Vision `dvhe`:**
  - Reports **profile 4, 5 and 8** (`DvheDtr`, `DvheStn`, `DvheSt`) @ `Uhd60`. **It does not report profile 7 (`DvheDtb`).**
  - Max. 1 instance, 4K60.
- **Dolby Vision `dvav`:** profile 9 @ `Uhd60`, 4K30.
- **Tunneled playback:** the decoders for AVC, HEVC, VP9, MPEG-2 and DV report `tunneled-playback`.
- **No platform audio decoder for AC3, EAC3, DTS or TrueHD.** There are only software decoders for AAC, MP3, FLAC, Opus, Vorbis, AMR, G.711 and raw PCM. `media_codecs_amlogic_audio.xml` refers to ddp, dtshd and ffmpeg XMLs that do not exist.
- **HDMI audio profile:**
  - PCM 16-bit in **stereo, 5.1 and 7.1** @ 32, 44.1 and 48 kHz.
  - Also AC3, EAC3, EAC3-JOC and IEC61937.
  - The primary mixer is stereo only @ 48 kHz. Multichannel PCM is only possible via a "direct output".
  - There is a "tunnel direct output" (`HW_AV_SYNC`) with 7.1 PCM and AC3/EAC3/JOC.
  - `encoded_surround_output_enabled_formats=18,5,6`.
- **Display modes:**
  - 2160p and 1080p @ 23.976, 24, 25, 29.97, 30, 50, 59.94 and 60 Hz.
  - 720p @ 50, 59.94 and 60 Hz.
  - **Each mode is in its own group, without alternative refresh rates.** So seamless frame rate switching is not possible.
  - Active: 2160p 59.94 Hz.
  - Mode switching is set to `SWITCHING_TYPE_WITHIN_GROUPS` (seamless only); `match_content_frame_rate` is not set.
  - ALLM is supported.
- **Display HDR:**
  - EDID name "LG TV SSCR2", year of manufacture 2022.
  - Max. luminance 500.
  - DV VSVDB v2, with DV-Std (RGB 8-bit) and LL (4:2:2 12-bit).
  - `hdr_conversion_mode=2` (system).
  - During YouTube SDR playback the HDMI output was at **`DolbyVision-Std`**: SDR goes to the TV as DV.
- **HDMI-CEC:**
  - TV `OLED55CS6L` (0x0000)
  - Chromecast 0x1000 (HDMI 1)
  - **Sonos Arc 0x2000 (HDMI 2)**
  - System audio on.
- **Wi-Fi:**
  - 802.11ac on 5 GHz (5240 MHz), RSSI −59 dBm.
  - Link speed Rx 780 / Tx 195 Mbps, max. 866 Mbps.
  - No Ethernet interface present.

Consequences:
- **TrueHD and DTS (approx. 250 items):** by default pick an AC3/EAC3 track if the file has one. Many 4K movies have one. Otherwise the server transcodes only the audio to EAC3 (direct stream, light for the server). Decoding locally to multichannel PCM via an FFmpeg extension is an alternative; that needs a library and eARC support in the chain.
- **Audio choice must be smart:** prefer a passthrough track in the same language over a lossless track that has to be transcoded.
- **VC-1 (1 item):** becomes a video transcode.

## Library profile (2026-10-08, 2067 items)

| Dimension | Distribution |
|---|---|
| Container | mkv 1824, mp4 241, ts 1, mov 1 |
| Video | HEVC 1106, H.264 959, VC-1 1 |
| Resolution | 4K 481, 1080p 1349, 720p 236 |
| Bit depth | 10-bit 1059, 8-bit 1007 |
| HDR | SDR 1771, DV+HDR10 113, DV+HDR10+ 50, HDR10 66, DV p7 with EL 23, DV p8.6 (Jellyfin: `DOVIInvalid`) 20, HDR10+ 17, DV p5 5, HLG 1 |
| Audio (tracks) | AC3 616, EAC3 590 (of which Atmos 292), AAC 525, DTS family 341 (DTS-HD MA 173), TrueHD 86 (Atmos 85), FLAC 8, PCM 2 |
| Subtitles | SRT 1217, PGS 273, ASS 238, mov_text 56, DVDSUB 47, none 353; no external files |
| Max. bitrate | 116.8 Mbps (Game of Thrones 8x6) |

Conclusions for the design:
- **Lossless audio is not an edge case**: DTS-HD MA and TrueHD are on ~250 items, mostly 4K movies. Passthrough vs. audio transcode must work well in phase 2 and be visible.
- **Bitrates up to ~117 Mbps**: probably too much over Wi-Fi. Test on Ethernet (USB-C adapter) and Wi-Fi; consider a quality limit in settings (phase 6).
- **ASS is common** (238 items, also ordinary series like Friends): the simplified rendering by Media3 is a visible difference.
- **DV p7 and p8.6** occur 43× together: the DeviceProfile has to decide whether they are played as HDR10 (base layer).

## Codec test set

Expectation = hypothesis before the test on the Chromecast (DP = direct play, DS = direct stream / remux / audio-only transcode, TC = video transcode). Fill in **Result** after the test in phase 2.

| # | Case | Item (Jellyfin id) | Characteristics | Expectation | Result |
|---|---|---|---|---|---|
| 1 | Baseline | Alone Together 0x18 (`3c89fee3fd3d5c549e161d9ef519e281`) | mkv H.264 1080p SDR, AAC 2ch, SRT, 7.5 Mbps | DP || ✅ DP · `c2.amlogic.avc` · AAC → PCM 2ch |
| 2 | HEVC 10-bit SDR | Band of Brothers 1x1 (`69bc494c74fccc4adb5563b4141ec60c`) | mkv HEVC 1080p 10-bit, EAC3 6ch | DP || ✅ DP · `c2.amlogic.hevc` · EAC3 passthrough |
| 3 | 4K HDR10 + DTS:X | Casino (1995) (`8435323a42a585b8e9b8184749c42767`) | HEVC 4K HDR10, DTS-HD MA+DTS:X / AC3 / DTS, SRT+PGS, 72 Mbps | DP video; DTS-HD cannot be passed through → pick AC3 track or audio transcode || ✅ DP with AC3 track (#2) instead of DTS:X · briefly starts with DTS→PCM (see open points) |
| 4 | HDR10+ | Dark Matter 1x1 (`2ca07f18f0e16ce8dd776e5924ca941a`) | HEVC 4K HDR10+, EAC3 Atmos | DP, plays as HDR10 (LG does not support HDR10+); Atmos via passthrough || ✅ DP · HDR10+ plays as HDR10 · EAC3 passthrough |
| 5 | DV p5 | Star City 1x4 (`ced7811a650204267628372bf2268a70`) | HEVC 4K DV p5.0 (no HDR10 fallback), EAC3 Atmos | DP if a DV decoder is reported; without a DV TV green/purple picture → TC || ✅ DP · DV decoder (`dvhe.05`) · EAC3 passthrough · picture not yet confirmed with own eyes |
| 6 | DV p8.1 (mp4) | Disclosure Day (2026) (`e954c12dc0a712e3a6b18368481c32cb`) | mp4 HEVC 4K DV p8.1+HDR10+, EAC3 Atmos, mov_text | DP || ✅ DP · DV decoder (`dvhe.08.06`) · **EAC3-JOC (Atmos) passthrough** |
| 7 | DV p8.6 (`DOVIInvalid`) | Game of Thrones 1x1 (`3e9edff3142d06572d434b09223f73f2`) | HEVC 4K, TrueHD Atmos + AC3, PGS+SRT, 42 Mbps | DP as HDR10; unclear what Jellyfin does with "Invalid" || ✅ DP · DV decoder (`dvhe.08.06`) · AC3 track instead of TrueHD |
| 8 | DV p7 with EL | Anyone but You (2023) (`568fffd2f31865ea0ed4717c4c3f0f3d`) | HEVC 4K DV p7.6 FEL, TrueHD Atmos / AC3, 84.6 Mbps | HDR10 base layer (EL ignored) or TC || ⚠️ DP, but Media3 picked `c2.amlogic.dolby-vision.dvav` (AVC-DV!) for `dvhe.07`; picture unconfirmed → phase 2b: p7 via HEVC decoder |
| 9 | Max. bitrate | Game of Thrones 8x6 (`2bc7b1d116a8224bc89d0b33c8775222`) | DV p7.6, 116.8 Mbps, 5 audio tracks | Network stress test; Wi-Fi vs. Ethernet || ⚠️ as 8; network/buffer at 117 Mbps not yet assessed |
| 10 | HLG | The White Lotus 3x4 (`7eeacbbffdf4f94e67752f3a0b1876e1`) | HEVC 4K HLG, EAC3 Atmos | DP || ✅ DP · HLG · EAC3 passthrough |
| 11 | VC-1 | Blow (2001) (`31fb27dbf9b42264cae147220e3e32ee`) | mkv VC-1 1080p, TrueHD / DTS-HD MA / AC3, PGS | TC (no VC-1 hardware decoder) || ✅ Transcode via QuickSync → HEVC 1080p SDR, AC3 (VC-1 cannot be done otherwise) |
| 12 | TrueHD + PCM + DTS-HD | Interstellar (2014) (`3a5e1d27a22c1a697151f0d6be3bfc5d`) | DV p8.1, 109 Mbps, 6 audio tracks incl. PCM 24-bit 8ch | Video DP; pick the EAC3 Atmos track by default (passthrough); TrueHD/PCM/DTS-HD → transcode || ✅ DP · DV decoder (`dvhe.08.06`) · EAC3 Atmos 7.1 (#2) passthrough · start 2.7 s after fast seek index · seeking fast |
| 13 | ASS | Friends 1x1 (`b99e1b11d558f14b8669b80e441b98a0`) | H.264 720p, AAC 2ch, ASS only | DP, simplified subtitle || ⚠️ was fallback: the **file is damaged** (15 gaps of 16 KiB of zeros, see Open items). After `ResyncingMatroskaExtractor` (2026-10-09): ✅ DP with ASS, confirmed by the user on the TV (0.1.6) |
| 14 | DVDSUB | Jurassic World Dominion (2022) (`11b4ce484aae38676b689f7643e648b5`) | HEVC 4K HDR10, AAC 8ch, DVDSUB only | DP (Media3 has a VobSub decoder) || ✅ DP · HDR10 · AAC 7.1 → **PCM 8ch** · DVDSUB rendering not yet tested |
| 15 | ts container | The Usual Suspects (1995) (`3acaf014d1d24c5058c90f8e5c4ab372`) | ts H.264 1080p, DTS-HD MA / AC3, PGS, 44 Mbps | DP || ✅ via fallback: Media3 cannot read M2TS → server remux, AC3 |
| 16 | mov container | How I Met Your Mother 6x24 (`c04141bdfc1518d2bf321bcb11b0fb29`) | mov HEVC 1080p, HE-AAC 6ch | DP || ✅ DP · HEVC · HE-AAC → PCM 6ch |
| 17 | DTS only (no AC3) | Jurassic World (2015) (`72fc291affdfce73fd9966b36e6e92ce`) | HEVC 4K HDR10, DTS-HD MA 5.1, no AC3/EAC3 | Server transcodes audio only | ✅ server DirectStream: video **copied** (HDR10 10-bit), DTS → AC3 |

### Edge cases

- **Friends 10x8** has no audio track (and probably missing media info). The app must handle this gracefully and not crash.
- There are no external subtitle files in the library. The code for that cannot be tested with real data.

## Open items

- **Library (0.1.8, 2026-10-10, release, `measure-steady.sh … rows`, shelf Movies A–Z, 45 titles):** 8.5–10.7% janky frames (p50 25–26 ms, GPU 11 ms) with 30 s ↓↑→← held. Above the 5% budget, in the same order as Series (5–8%) and the bookcase (10–12%). Cold start 0.47 s, memory 111 MB PSS (within budget). Per ↓ a whole line of 8 tiles with titles comes in (when holding ~20 steps per second). **Accepted by the user (2026-10-10):** no jank visible in use, not even when holding; do not optimize further unless it becomes noticeable in practice.

- **Movies detail view (0.1.7, 2026-10-09):** measured with `measure-steady.sh … left-right` (30 s ←/→ held, shelf New): first version 64–70% janky frames (p50 38 ms, GPU 16 ms); with a plain spine color while browsing 56–60% (GPU 6 ms, UI thread still ~30 ms). After that: do not lay out the description and tech line while holding; **not measured yet** (the user found it fast enough on the TV). Budget < 5%: remeasure and go further if needed (logo fade, neighbor text).
- **Green and blue blocks on Dark Matter 1x3** (reported 2026-10-10, once; fine on the next start). The file (`597db01edb34c652d14dc0ade891e73b`, HEVC 4K 3840×1920 HDR10+, 9.4 GB) is intact: no gaps of zeros as with Friends, so `ResyncingMatroskaExtractor` plays no role here. The log of the failed start was already gone; not reproduced (5× quick stop and start: always `c2.amlogic.hevc.decoder`). Suspected cause: with decoder fallback, `c2.android.hevc.decoder` (software, does not report this profile) was in the list as a backup; if the hardware decoder does not open at start, Media3 takes that one, and it shows 10-bit HDR like that. **0.1.23:** video only via hardware now (`withoutSoftwareVideoFallback`); such a start becomes an error and the fallback restarts. If it comes back: open Info (i) in the overlay and note the decoder name.
  - **Back on 0.1.24 (2026-10-11, The End of Oak Street, `5cc754f553e10f167697738c7f472435`):** first start green blocks, second fine. Again 4K HEVC **HDR10+** (3840×1600, 23.976), direct play, LG + Sonos. The software decoder is already excluded by then. Server log: both starts direct play, nothing transcoded. The file is clean: an `IDR_N_LP` keyframe with VPS/SPS/PPS every 2 s (ffmpeg `trace_headers` on the first 150 MB), so starting or resuming at any point gives no missing references. Logcat of the start was already overwritten.
  - Suspicion: the hardware decoder was already delivering picture (first frame ~1 s) while the TV was still switching modes (HDMI resync, HDCP again, then HDR on). The only thing that differs per start.
  - **0.1.26:** the video track stays off until the new mode is active (`displayModeReached` + `setVideoHeld`); meanwhile the file is already opened and buffered. Measured (profile, Chromecast, 4 starts Oak Street/Dark Matter 1x3 at 0–120 s): mode active 0.67–0.77 s, decoder open 0.1–0.3 s after that, first frame 1.0–2.0 s, playing 3.2–3.3 s (was 3.2–3.3 s). Without a mode switch unchanged (Alone Together: first frame 0.67 s). The first variant (prepare only after the mode) cost 3.07 s to the first frame when resuming at 120 s; hence only holding the video track.
  - New: **start log** (`playback-starts.txt`, see `playback.md`). If the blocks come back: immediately `adb shell cat /sdcard/Android/data/nl.jellycast.tv/files/playback-starts.txt` and look at the start with the right time (order mode → video track → decoder, errors in the decoder).
  - **Back on 0.1.42 (2026-10-15 17:22, The Prestige, `f865fba855564fcaa44e855c403a9a03`):** first start green blocks; Home → Settings → back to the app (new player, resume at 33 s) fine. Again 4K HEVC 10-bit HDR10, 3840×1600, 23.976, direct play, not tunneled (PCM 5.1). Start log in the right order: mode active +649 ms, video track on +716, decoder `c2.amlogic.hevc.decoder` open +1059, first frame +1521, HDMI stable +3149. So holding the video track until the mode is active does **not** prevent it; the resync theory is ruled out. The good start opened its decoder at almost the same moment as its mode switch. Only other difference: the Chromecast had booted ~2 min before (uptime), so this was the first hardware HEVC decode after boot. Bad start 11 of 788 frames not shown, good one 7 of 7308. Logcat of the bad start has no decoder lines at all (ADB was off; the vendor C2 logs only appear once ADB is on). Common factor of all three cases: 4K HEVC 10-bit HDR, wide crop (3840×1600/1920), not tunneled, first start.
  - Next step: reboot the Chromecast and start The Prestige right away, with ADB on, and pull `adb logcat -d -b all` immediately.
  - Not visible on a screenshot: the video area is on its own hardware layer. Whether the picture is clean, the user has to see on the TV.
- **Back shortly after the player sometimes closes the app:** Back within ~150 ms after returning from the player did not reach Home's BackHandler (seen 2026-10-09, also before this change). Double Back in the player itself is fixed (`onExit` only removes a `Player` from the stack).

- **Progress of watching short bits is lost** (reported 2026-10-09). Not an app bug: the app reports start, progress and stop correctly (server log: "Playback stopped … Stopped at 48508 ms"; tested myself with Ted Lasso, 2 min → "29 min left" and progress bar). Jellyfin discards a position below **5% of the runtime** (Dashboard → Playback → Resume, "Minimum resume percentage"). For a 30 min episode that is ~1.5 min; the tested bits were 20–50 s. Lowered on the server to **1%** (2026-10-09, via `POST /System/Configuration`): at 30 min that is ~18 s.
- **Icon white in TV Settings** (fixed 2026-10-09): the foreground PNGs from Paper had a white background that covered the amber background layer. Now opaque amber.
- **Brief strange sound when starting playback** (reported 2026-10-09): cause found and fixed in the app (see "Strange sound when playback starts"): the sound started in the middle of the HDMI/HDCP resync after the mode switch. ✅ **Confirmed by the user on the TV (0.1.6, 2026-10-09).** Test list used for this: start Casino, Band of Brothers 1x1, Interstellar (passthrough) and Alone Together (PCM) 2–3× each. Expected: picture holds still for ~2 s, then the sound starts cleanly, without a click or noise. If you still hear it, set "Match content frame rate" to Never in the Google TV settings (Display & Sound → Advanced display settings) and start Casino again (set it back afterwards): if it is gone then, 2.5 s is too short; if it stays, the cause is elsewhere (e.g. the Sonos switching from PCM to Dolby).
- Test whether DTS and TrueHD really do not come through (that is what the HDMI profile reports), and whether multichannel PCM goes via the LG to the Sonos Arc (requires eARC).
- **Friends via the fallback remux** (phase 5, investigated 2026-10-09): the end comes before the "Next" card, and the ASS subtitles do not appear. ✅ Fixed: Friends now plays directly, confirmed by the user (0.1.6).
  - **Cause of the MKV error: the file itself is broken.** It has 15 gaps of exactly 16 KiB of zeros in the middle of clusters (incompletely downloaded torrent blocks; `/Download` and `/stream?static=true` give the same bytes). Media3 stops at the first gap (byte 1,359,350, 8.1 s) with `No valid varint length mask found`; ffmpeg reports `0x00 … invalid as first byte of an EBML number` and reads on at the next cluster. In total ~26 s is missing (gaps of 0.3–3.9 s at 8, 47, 63, 742–767, 1005–1015, 1277–1338 s).
  - **Fixed in the app:** `ResyncingMatroskaExtractor` does the same as ffmpeg: a content error within a cluster of known size → continue at the next cluster. Tested on the JVM against the real bytes: Media3 stops after 191 video frames; with the new reader the whole file (17 jumps, 359 ASS lines, the same gaps as the remux: 25.4 s video, 26.8 s audio). Friends then no longer needs the fallback and the ASS subtitles come from the file. To test on the TV: picture errors up to the next keyframe after each gap are normal (also in the remux).
  - **Remux and the duration:** the remux is not shorter than the playlist. Fetched all 228 segments: the timestamps match the playlist (keyframe segments, 1369.4 s) up to the end; only the same ~26 s gaps as in the file. Why the card did not appear in the remux cannot be reproduced outside the TV (Media3 jumps along with the clock at a gap, so the position does reach the end). If it comes back with another file: log position and duration at `STATE_ENDED`.
  - **ASS in a remux is not possible without burning in.** Jellyfin 10.11 never converts ASS/SSA to WebVTT (`MediaStream.SupportsSubtitleConversionTo` refuses `ass`/`ssa`), so the `vtt`/`Hls` profile is out and the server picks `Encode` (= video transcode). That is why the app sends `SubtitleStreamIndex=-1` for the fallback and the remux has no subtitles. Possible route if it happens more often: the ASS as a separate file (`/Videos/{id}/{bron}/Subtitles/{index}/0/Stream.ass`) as a `SubtitleConfiguration`, only for the fallback (costs the server one read of the file). Not built now.
  - The track "Track 1" without a codec in the remux is probably the CEA-608 subtitle track that Media3 always creates for H.264 in TS (empty). Not checked on the TV.
