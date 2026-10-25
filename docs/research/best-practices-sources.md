# JellyCast best-practice sources

Source list behind the rules in `.claude/rules/`. Consulted on **2026-10-08**. The codes (e.g. `[A1]`) are used in the rule files. Version numbers below are a snapshot; always look them up again.

## A. Architecture and modularization

- `[A1]` https://developer.android.com/topic/architecture : Guide to app architecture: UI, domain and data layers, UDF.
- `[A2]` https://developer.android.com/topic/architecture/recommendations : Recommendations with strength ratings (repositories, `collectAsStateWithLifecycle`, `WhileSubscribed(5_000)`, no events from ViewModel to UI, single activity with Navigation 3, fakes over mocks, naming).
- `[A3]` https://developer.android.com/topic/modularization : When modularizing pays off and when it does not; a small codebase does not get the scaling benefits.
- `[A4]` https://github.com/android/nowinandroid : Reference app: `core:*`/`feature:*` layout, interfaces for repositories, test implementations without a mocking library.
- `[A5]` https://developer.android.com/guide/navigation/navigation-3 : Navigation 3: the app owns the back stack as a list, `NavDisplay`, keys per destination.
- `[A6]` https://developer.android.com/jetpack/androidx/releases/navigation3 : Navigation 3 stable since 1.0.0 (2025-11-19); 1.2.0 stable on 2026-09-23.
- `[A7]` https://developer.android.com/kotlin/coroutines/coroutines-best-practices : Inject dispatchers, main-safe suspend functions, no `GlobalScope`, never swallow `CancellationException`.
- `[A8]` https://insert-koin.io/docs/reference/koin-compose/compose : Koin 4.2 + Compose: `startKoin` in `Application`, `koinViewModel()`, `koin-compose-navigation3`, `KoinApplicationPreview`.

## B. Kotlin and Compose style

- `[B1]` https://kotlinlang.org/docs/coding-conventions.html : Official Kotlin coding conventions (naming, formatting).
- `[B2]` https://developer.android.com/kotlin/style-guide : Android Kotlin style guide; Google's variant of B1.
- `[B3]` https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-component-api-guidelines.md : Compose component API guidelines: `modifier` parameter, parameter order, stateless, no `MutableState` parameters, slots, `XxxDefaults`.
- `[B4]` https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md : General Compose API guidelines (PascalCase for UI composables, `remember*` naming).
- `[B5]` https://developer.android.com/develop/ui/compose/performance/stability : Strong skipping on by default since Kotlin 2.0.20; no `@Immutable` on collection classes unless needed.
- `[B6]` https://developer.android.com/develop/ui/compose/performance : Always measure Compose performance in a release build with R8.
- `[B7]` https://pinterest.github.io/ktlint/ : ktlint, formatter/linter for B1/B2 (only described, not added).
- `[B8]` https://detekt.dev/ : detekt, static analysis; jellyfin-androidtv uses it (only described).
- `[B9]` https://mrmans0n.github.io/compose-rules/ : Compose-specific lint rules for ktlint/detekt (only described).

## C. Android TV / Google TV

- `[C1]` https://developer.android.com/training/tv/get-started/create : Manifest: `leanback` feature, touchscreen not required, `LEANBACK_LAUNCHER`, banner 320×180 xhdpi with text in it.
- `[C2]` https://developer.android.com/docs/quality-guidelines/tv-app-quality : TV quality criteria: TV-DP, TV-DB, TV-PC, TV-PP, TV-NP, TV-BU, TV-OV, TV-ME, TV-BP.
- `[C3]` https://developer.android.com/training/tv/playback/compose : Compose for TV: use `androidx.tv.material3`, do not mix it with `androidx.compose.material3`.
- `[C4]` https://developer.android.com/training/tv/playback/compose/lists : `TvLazyRow` and similar deprecated; use foundation `LazyRow`/`LazyColumn`; `BringIntoViewSpec` replaces `pivotOffsets`.
- `[C5]` https://developer.android.com/jetpack/androidx/releases/tv : tv-material 1.1.0 and tv-foundation 1.0.0 stable (2026-05-06); TV lazy layouts removed.
- `[C6]` https://developer.android.com/design/ui/tv/guides/styles/layouts : 960×540 dp canvas, 5% overscan margin (48 dp sides, 27 dp top/bottom), 12-column grid.
- `[C7]` https://developer.android.com/design/ui/tv/guides/styles/typography : TV type scale (Display only for short headline text; Title for cards).
- `[C8]` https://developer.android.com/design/ui/tv/guides/styles/focus-system : Design of the focus system on TV.
- `[C9]` https://developer.android.com/develop/ui/compose/touch-input/focus : Focus in Compose: D-pad 2D, `requestFocus()` in `LaunchedEffect`, `focusRestorer()` on containers.
- `[C10]` https://developer.android.com/reference/kotlin/androidx/compose/ui/focus/focusRestorer.modifier : `focusRestorer(fallback)`; the overload with a lambda is deprecated.
- `[C11]` https://developer.android.com/training/tv/playback/memory : TV memory budgets, image size, media buffer per RAM class (2 GB/2160p: 100–120 MB), releasing episodes.
- `[C12]` https://github.com/android/tv-samples/tree/main/JetStreamCompose : Official Compose for TV sample app.

## D. Media3 / ExoPlayer

- `[D1]` https://developer.android.com/media/media3/exoplayer/hello-world : One application thread (main), `release()` as soon as the player is no longer needed, update the playlist without preparing again.
- `[D2]` https://developer.android.com/media/media3/session/control-playback : MediaSession strongly recommended for TV apps (media keys, power management); a service only for background playback.
- `[D3]` https://developer.android.com/media/media3/exoplayer/track-selection : `TrackSelectionParameters`, overrides, tunneling as a preference via `DefaultTrackSelector`, audio offload.
- `[D4]` https://developer.android.com/media/media3/exoplayer/supported-formats : SSA/ASS as a supported text format; HDR/DV decoding depends on the device.
- `[D5]` https://developer.android.com/media/media3/ui/androidtv : D-pad in the player: the `Player` composable handles focus itself; `PlayerView` needs key forwarding.
- `[D6]` https://developer.android.com/jetpack/androidx/releases/media3 : Media3 1.11.1 stable (2026-09-10); Compose UI building blocks; DV fixes; tunneling race fixed in 1.10.1.
- `[D7]` https://github.com/androidx/media/tree/main/libraries/extractor/src/main/java/androidx/media3/extractor/text : Source code: `pgs/PgsParser` exists (PGS is rendered); `ssa/SsaStyle` only knows basic styling (color, size, bold/italic, alignment/position), no fonts or effects.
- `[D8]` https://developer.android.com/reference/androidx/media3/exoplayer/audio/AudioCapabilities : `isPassthroughPlaybackSupported(format, attributes)` for passthrough detection.
- `[D9]` https://github.com/google/ExoPlayer/issues/7669 : The HDMI plug intent sometimes reports E-AC3 while the user chose PCM; do not trust the intent blindly.

## E. Gradle and build

- `[E1]` https://developer.android.com/build/migrate-to-catalogs : Version catalog `gradle/libs.versions.toml`.
- `[E2]` https://developer.android.com/develop/ui/compose/bom : Compose BOM for aligned Compose versions.
- `[E3]` https://developer.android.com/topic/performance/app-optimization/enable-app-optimization : R8 on in release; `proguard-android-optimize.txt`; from AGP 9.3 the new `optimization { enable = true }` DSL; full mode by default.
- `[E4]` https://developer.android.com/topic/performance/baselineprofiles/overview : Baseline profiles: outside Play they only take effect after background dexopt ("likely overnight"); ProfileInstaller installs on first launch.
- `[E5]` https://developer.android.com/build/building-cmdline : `./gradlew` tasks, `installDebug`.
- `[E6]` https://developer.android.com/studio/publish/app-signing : Release signing; keystore outside version control.

## F. Testing

- `[F1]` https://developer.android.com/training/testing/fundamentals/test-doubles : Fakes are preferred over mocks and stubs; avoid spies.
- `[F2]` https://developer.android.com/kotlin/coroutines/test : `runTest`, `StandardTestDispatcher`, `Dispatchers.setMain`.
- `[F3]` https://developer.android.com/develop/ui/compose/testing : Compose UI tests, semantics, `performKeyInput`, `assertIsFocused()`.
- `[F4]` https://developer.android.com/training/testing/fundamentals/strategies : Test strategy and the test pyramid.

## G. Jellyfin

- `[G1]` https://kotlin-sdk.jellyfin.org/guide/getting-started.html : `createJellyfin { clientInfo; context }`, `createApi(baseUrl, accessToken)`, API groups as extensions (`api.userApi`).
- `[G2]` https://github.com/jellyfin/jellyfin-androidtv/blob/master/app/src/main/java/org/jellyfin/androidtv/util/profile/deviceProfile.kt : Reference DeviceProfile with `buildDeviceProfile {}` from the SDK; codec checks behind `MediaCodecCapabilitiesTest`; ASS/PGS direct play as a setting; `DOVI_WITH_EL` excluded unless the device reports it.
- `[G3]` https://github.com/jellyfin/jellyfin-androidtv/blob/master/app/src/main/java/org/jellyfin/androidtv/util/profile/KnownDefects.kt : Per-model exceptions for DV/HDR10+.
- `[G4]` https://github.com/jellyfin/jellyfin-androidtv/blob/master/playback/jellyfin/src/main/kotlin/playsession/PlaySessionService.kt : Playback reporting: start/progress/stop with `playSessionId`, `positionTicks`, `playMethod`; log errors, do not crash.
- `[G5]` https://github.com/jellyfin/jellyfin-androidtv/blob/master/app/src/main/java/org/jellyfin/androidtv/util/apiclient/JellyfinImage.kt : Image URLs via `imageApi.getItemImageUrl(itemId, type, tag, maxWidth/fillWidth…)`, always with `tag`.
- `[G6]` https://github.com/jellyfin/jellyfin-androidtv/blob/master/gradle/libs.versions.toml : What an active Jellyfin TV client uses in 2026 (Navigation 3, Koin 4.2, Coil 3, Media3 1.11.1, detekt).

## H. Device (Chromecast with Google TV 4K)

- `[H1]` https://forum.makemkv.com/forum/viewtopic.php?p=105848 : User tests: DV profile 5 and 8 work; single-track profile 7 (MEL/FEL) reportedly also plays, dual-track profile 7 falls back to HDR10. Secondary source from 2021, not official.

## I. Claude Code

- `[I1]` https://code.claude.com/docs/en/memory : `CLAUDE.md` under 200 lines; `.claude/rules/*.md` loaded recursively; YAML frontmatter `paths:` (globs) loads a rule only on Read/Write/Edit of matching files; `paths` is the only field that is read.

## J. Design and rendering on the Chromecast

Basis for `.claude/rules/design.md`. Consulted on **2026-10-08**.

- `[J1]` https://wiki.lineageos.org/devices/sabrina/ : Hardware of `sabrina`: Amlogic S905D3G, 4× Cortex-A55, Mali-G31 MP2. GPU and 59.94 Hz also measured ourselves (`dumpsys SurfaceFlinger`/`display`).
- `[J2]` https://android-headunits.com/mali-g31-mp2/ : Mali-G31 MP2 clock speed and fill rate (secondary source; the upper limit of ~10× the screen per frame is our own derivation).
- `[J3]` https://developer.android.com/develop/ui/compose/graphics/draw/modifiers : `graphicsLayer`: alpha < 1 creates an offscreen buffer (except with `ModulateAlpha`); scale/translation do not; blur/`RenderEffect` always offscreen.
- `[J4]` https://developer.android.com/develop/ui/compose/performance/bestpractices : Read fast-changing values in the lambda modifiers; cache `Brush`/objects with `drawWithCache`/`remember`.
- `[J5]` https://github.com/androidx/androidx/blob/androidx-main/tv/tv-material/src/main/java/androidx/tv/material3/Card.kt : tv-material `CardDefaults`: focusedScale 1.1, focused border 3 dp, `Glow.None`, shape 8 dp. Glow draws via `setShadowLayer` (`SurfaceGlow.kt`).
- `[J6]` https://github.com/jellyfin/jellyfin/blob/master/Jellyfin.Api/Controllers/ImageController.cs : Image API parameters (`fillWidth`, `quality`, `format`, `blur`, `tag`); `quality` falls back to 100; WebP only with `Accept: image/webp`.
- `[J7]` https://coil-kt.github.io/coil/compose/ : `AsyncImage` loads at the size of the constraints; `SubcomposeAsyncImage` is too slow for lists.
- `[J8]` https://github.com/coil-kt/coil/blob/main/coil-core/src/androidMain/kotlin/coil3/util/contexts.kt : Default memory cache 15% (low-RAM) / 20% of the app heap.
- `[J9]` https://developer.android.com/design/ui/tv/guides/components/cards : Card ratios 16:9, 2:3, 1:1 and widths per number of cards per row.
- `[J10]` https://developer.android.com/design/ui/tv/guides/components/immersive-list : Immersive list: background follows focus, card scales 1.1, subject of the backdrop top right.
- `[J11]` https://developer.android.com/design/ui/tv/guides/styles/color-system : "Build from dark themes".
- `[J12]` https://www.nngroup.com/articles/response-times-3-important-limits/ : 0.1 s feels instant, 1 s keeps the flow of thought, 10 s is the limit of attention.
