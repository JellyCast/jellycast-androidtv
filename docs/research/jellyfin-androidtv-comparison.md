# Comparison with jellyfin-androidtv

Date: 2026-10-09. Compared with the official client [jellyfin/jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv), commit `09c912d` (2026-10-06). Goal: check whether JellyCast's structure, code and way of working hold up, and what we can adopt.

Status: **reassessed in phase 7 (2026-10-09), every candidate has a decision** (section 5). Adopted: Coil `serviceLoaderEnabled(false)`.

## 1. What is confirmed

- **Same stack and versions.** Jellyfin SDK 1.8.12, Media3 1.11.1, Koin 4.2.2, Coil 3.6.3, AGP 9.4.1, coroutines and serialization 1.11.0, Lifecycle 2.11.0, FFmpeg audio decoder `org.jellyfin.media3:media3-ffmpeg-decoder` 1.9.0+1. So the official team makes the same choices.
- **Same basic structure.**
  - Koin with one `ApiClient` singleton that is updated on login and logout instead of being recreated.
  - Repository interface with one implementation (theirs `…Impl`, ours `Default…`).
  - Session state as a `StateFlow` in a `SessionRepository`.
  - DeviceProfile via `buildDeviceProfile`, transcode via HLS/TS with `copyTimestamps = false` and `enableSubtitlesInManifest = true`.
- **Where JellyCast is tighter.**
  - For us, `createDeviceProfile` is a pure function of `DeviceCapabilities`, tested with fixed test data. They read `Context` and `UserPreferences` in the wrapper and test codec detection with MockK and `mockkObject`.
  - Layers: they are in the middle of a migration (36 Java files, Leanback fragments, `VideoManager.java` next to a new `playback` module, `minSdk 23` with compat code, ViewModels with `get(), get(), get()`). They have no fixed layers like our DTO → `core/model` separation. Not an example to follow.
  - Their multi-module setup (`playback:core`, `design`, `preference`) suits a large project with music, Live TV and external players. For JellyCast one module remains the right choice (see `architecture.md`).

## 2. Candidates to adopt (in order of usefulness)

| # | What | How they do it | Why for us | To do / to test |
|---|---|---|---|---|
| 1 | **ASS subtitles via libass** | `io.github.peerless2012:ass-media`: `AssRenderersFactory`, `withAssMkvSupport`, `AssSubtitleParserFactory`, render type `OVERLAY_OPEN_GL` (`playback/media3/exoplayer/.../ExoPlayerBackend.kt`). | 238 items with ASS (incl. Friends). Now only via the fallback (MKV error → server remux) and with simplified styling (`NOTES.md`, test file 13). Also touches the phase 5 setting "Burn in ASS subtitles". | Measure on the Chromecast (release): the GL overlay is an extra GPU layer during playback. Note memory and dropped frames. Record the dependency in `docs/dependencies.md`. |
| 2 | **`ENCODE` (burn-in) as last resort for image subtitles** | PGS, DVDSUB, DVBSUB and IDX get `EMBED` and `ENCODE`. | We only have `EMBED`. On a transcode (e.g. VC-1 "Blow" with PGS) PGS cannot go along in the HLS stream. Expectation: the subtitle then drops out instead of being burned in. **Not checked on the device.** | Test first: Blow with PGS on. If the subtitle drops out, add `ENCODE` in `DeviceProfileFactory` with a test. |
| 3 | **Decoder level and ref frames in the profile** | Per codec/profile `VIDEO_LEVEL ≤ <decoderlevel>` via `applyConditions` on `VIDEO_PROFILE`; H.264 `REF_FRAMES ≤ 12` from 1200 px and `≤ 4` from 1900 px wide. Level via `MediaCodecQuery.getDecoderLevel`. | We only check resolution, bit depth and HDR. The Chromecast does HEVC up to level 5.1; a file at level 5.2 or 6.x now gets direct play and fails on the device instead of being transcoded. | Level per profile in `AndroidCapabilitiesReader` → `DeviceCapabilities`, conditions in `DeviceProfileFactory`, unit tests. First check the library for files above level 5.1. |
| 4 | **Channel limit for audio** | `CodecType.VIDEO_AUDIO` with `AUDIO_CHANNELS ≤ 8` (2 with downmix). | Small fallback against exotic audio. We only limit channels in the transcode profile (`maxAudioChannels = "6"`). | One `codecProfile` plus a test. |
| 5 | **Coil: `serviceLoaderEnabled(false)`** | In their `AppModule` on the `ImageLoader.Builder`. | Coil then does not look for components via ServiceLoader at startup: a small gain in cold start. | One line in `ImageLoaderFactory`; measure cold start. |
| 6 | **`setConstantBitrateSeekingEnabled(true)`** | On `DefaultExtractorsFactory`, together with a smaller TS timestamp search buffer on low-RAM devices. | Seeking in files without a seek index. For us such files (ts) usually already go via remux, so low priority. | Pass it in `JellyCastExtractorsFactory`, if there is a file that suffers from it. |
| 7 | **Session restore with `Mutex` and `NonCancellable`** | `SessionRepositoryImpl.restoreSession` and `switchCurrentSession` under one `Mutex`, in `withContext(NonCancellable)`. | Protects against restoring and logging in at the same time, and against a half-aborted switch. Small risk with one user, but cheap. | In `DefaultSessionRepository`, with a test for concurrent calls. |

## 3. Deliberately different, and kept that way

- **`DOVI_INVALID` (DV p8.6).** They always exclude it (transcode). We play it directly via the DV decoder. Tested with Game of Thrones 1x1 (`dvhe.08.06`, direct play; `NOTES.md`, test file 7).
- **HDR exclusion.** They use `notEquals` plus `applyConditions` on `VIDEO_RANGE_TYPE`, we use `inCollection`. According to their comment, this mainly determines which transcode pipeline the server picks (tone mapping). Only revisit if an HDR transcode comes out wrong.
- **`KnownDefects` per device model, settings per codec, fMP4 HLS profile, external players.** Needed for thousands of devices, not for one Chromecast.
- **detekt, Renovate and GitHub workflows.** ktlint with compose-rules and Android lint cover the same; CI is not needed for personal use.
- **Tests with kotest and MockK.** We use fakes and JUnit (`testing.md`). Keep it that way.

## 4. Reassessment after the build (last phase)

When phases 0–6 are done:

1. Fetch the latest version of jellyfin-androidtv and note the commit and date here.
2. Look again at: `app/src/main/java/org/jellyfin/androidtv/util/profile/` (DeviceProfile, codec detection, `KnownDefects`), `playback/` (ExoPlayer setup, reporting, media segments), `auth/` (session) and `di/AppModule.kt` (SDK, Coil). Also look at new parts that did not exist yet.
3. Go through section 2. Per item: still relevant? Has it since been solved differently on their side or ours? Pick up, drop (with reason), or postpone.
4. Check every change against `product-principles.md` (smooth > pretty, performance budget) and measure on the Chromecast (release).
5. Update this file, `NOTES.md` and the rules where needed.

## 5. Reassessment (phase 7, 2026-10-09)

Fetched again on 2026-10-09: the main branch is still at `09c912d` (2026-10-06), latest tag `v0.20.0-beta.3`. Nothing has changed since the first comparison, so sections 1–3 still apply. Media segments (skip intro) were looked at again: they use the server's `MediaSegments` API; for us the server provides no segments (no intro plugin), see `NOTES.md`.

Per candidate, checked against our own library (2067 items, server API) and against `product-principles.md`:

| # | Candidate | Decision | Reason |
|---|---|---|---|
| 1 | libass for ASS | **Postponed** | Requires a memory and GPU measurement on the Chromecast during playback (the user is testing himself right now). And the biggest ASS case, Friends, does not even play directly: the MKV fails in Media3 and goes via the server remux, in which the ASS subtitles drop out (`NOTES.md` → Open items). Solve that first, then try libass as an opt-in setting, with the interaction with `FastCuesMatroskaExtractor` as a point of attention (`withAssMkvSupport` wraps the extractor factory). |
| 2 | `ENCODE` for image subtitles | **Dropped** | Tested via the server API with Blow (VC-1 + PGS, always transcode): with only `Embed` in the profile, the server itself chooses `SubtitleMethod=Encode`. So the subtitle does not drop out; adding it changes nothing. |
| 3 | Decoder level and ref frames | **Dropped** (level: revisit with new files) | Not a single file above level 5.1 (highest: HEVC Main 10 level 153 = 5.1, H.264 4.1). The server reports ref frames as 1 for every H.264 file, so a condition on it never works. If a 4K file above 5.1 is added and it does not play, read the level from the decoder after all. |
| 4 | Audio channel limit | **Dropped** | The highest in the library is 8 channels; the chain can handle that (FFmpeg decodes, passthrough is AC3/EAC3). The fallback protects against something that is not there. |
| 5 | Coil `serviceLoaderEnabled(false)` | **Adopted** | One line in `ImageLoaderFactory`. The only component we got via ServiceLoader, the OkHttp fetcher, we already register ourselves (with the WebP header); without ServiceLoader no second, unused one is added and Coil does not search anything at startup. Still to measure on the Chromecast: cold start and whether images load. |
| 6 | Constant bitrate seeking | **Dropped** | Applies to files without a seek index (ts); for us those go via the server remux. No file suffers from it. |
| 7 | `Mutex` around session restore | **Dropped** | One user; restoring happens at startup, logging in or out only later via a screen. The only overlap (a slow `validateSession` during logout) writes at most a user name without a token, and that does not give a logged-in state. Costs more test infrastructure than the risk is worth. |
