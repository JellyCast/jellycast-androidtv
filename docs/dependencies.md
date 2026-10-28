# Dependencies

Every external library in JellyCast, explained for someone who doesn't know the Android ecosystem. Claude Code updates this file in the same commit in which a dependency is added, updated or removed (rule in `.claude/rules/gradle-build.md`).

All versions were looked up on Google Maven, Maven Central or the Gradle Plugin Portal. The actual versions live in `gradle/libs.versions.toml`; this file explains why.

"In use" means it is in the project. "Planned" follows in the phase where it is needed.

## Template

```markdown
### <Name>
- **What:** <what it does, in plain language, 1–2 sentences>
- **Why:** <which problem it solves for us>
- **Alternatives:** <what was considered and why not>
- **Coordinate:** `group:artifact` · version `x.y.z` · added YYYY-MM-DD
- **Docs:** <official link>
```

## Terms

- **Coordinate:** the address of a library: `group:name:version`. Gradle uses it to download the library from Google Maven or Maven Central.
- **Version catalog** (`gradle/libs.versions.toml`): one file with all versions, so the build files only refer to names.
- **BOM** ("bill of materials"): one version number that gives a set of libraries versions that work together. You then no longer name the individual versions.
- **AndroidX / Jetpack:** the official Google libraries that sit alongside the Android OS itself.

## In use

### Android Gradle Plugin (AGP)
- **What:** the Gradle plugin that turns Kotlin code and resources into an installable APK.
- **Why:** required for every Android project.
- **Alternatives:** none.
- **Coordinate:** plugin `com.android.application` · version `9.4.1` · added 2026-10-08
- **Note:** AGP 9 has Kotlin built in. The separate plugin `org.jetbrains.kotlin.android` is no longer needed; AGP uses the Kotlin version that the Compose plugin puts on the classpath (2.4.21).
- **Docs:** https://developer.android.com/build/releases/gradle-plugin

### Kotlin + Compose compiler plugin
- **What:** the programming language and the compiler plugin that turns `@Composable` functions into UI code.
- **Why:** Kotlin is the standard language for Android; Compose doesn't work without the plugin.
- **Alternatives:** Java (no Compose support).
- **Coordinate:** plugin `org.jetbrains.kotlin.plugin.compose` · version `2.4.21` · added 2026-10-08
- **Docs:** https://kotlinlang.org/docs/releases.html

### Jetpack Compose (via BOM)
- **What:** Google's UI toolkit: you describe screens as Kotlin functions instead of XML layouts.
- **Why:** the recommended way to build new Android UI; Compose for TV builds on it.
- **Alternatives:** XML Views with Leanback (outdated for TV, deprecated by Google).
- **Coordinate:** `androidx.compose:compose-bom` · version `2026.09.00` · plus `ui`, `foundation`, `ui-tooling-preview` (and `ui-tooling` in debug only) without their own version · added 2026-10-08
- **Docs:** https://developer.android.com/develop/ui/compose/bom

### Compose for TV (tv-material)
- **What:** TV versions of buttons, cards, tabs and theme, with focus effects for the remote.
- **Why:** regular Material components are made for touch; these show focus clearly from 3 meters away.
- **Alternatives:** Leanback (deprecated); building everything yourself on `foundation` (a lot of work). `tv-foundation` is not needed: its lazy lists have been replaced by the ones in Compose foundation.
- **Coordinate:** `androidx.tv:tv-material` · version `1.1.0` · added 2026-10-08
- **Docs:** https://developer.android.com/training/tv/playback/compose

### Activity Compose
- **What:** the bridge between the Android `Activity` and Compose (`setContent { }`).
- **Why:** needed to show Compose in the single Activity.
- **Alternatives:** none.
- **Coordinate:** `androidx.activity:activity-compose` · version `1.13.0` · added 2026-10-08
- **Docs:** https://developer.android.com/jetpack/androidx/releases/activity

### Lifecycle (ViewModel + Compose)
- **What:** `ViewModel` (holds screen state outside the UI) and `collectAsStateWithLifecycle` (reads data only while the screen is visible).
- **Why:** the official foundation of the UI layer.
- **Alternatives:** no reasonable alternative.
- **Coordinate:** `androidx.lifecycle:lifecycle-viewmodel-compose`, `androidx.lifecycle:lifecycle-runtime-compose` · version `2.11.0` · added 2026-10-08
- **Docs:** https://developer.android.com/jetpack/androidx/releases/lifecycle

### Navigation 3
- **What:** decides which screen is visible and what "back" does. The app manages its own list of screens (the back stack).
- **Why:** Google now recommends Navigation 3 for new Compose apps; it is simpler than Navigation Compose, and the official Jellyfin TV app uses it too.
- **Alternatives:** Navigation Compose (`androidx.navigation:navigation-compose`): older, route-based, no longer the recommendation.
- **Coordinate:** `androidx.navigation3:navigation3-runtime`, `androidx.navigation3:navigation3-ui` · version `1.2.0`; `androidx.lifecycle:lifecycle-viewmodel-navigation3` · version `2.11.0` (ViewModel per screen) · added 2026-10-08
- **Docs:** https://developer.android.com/guide/navigation/navigation-3

### Kotlin Serialization
- **What:** converts Kotlin objects to JSON or another format, and back, without reflection.
- **Why:** Navigation 3 needs `@Serializable` keys to save the back stack; the Jellyfin SDK already uses it internally. Also handy for logging the DeviceProfile as JSON.
- **Alternatives:** Gson/Moshi (reflection or codegen, redundant next to the SDK); Parcelable (Android only, more boilerplate).
- **Coordinate:** plugin `org.jetbrains.kotlin.plugin.serialization` (version = Kotlin, `2.4.21`) + `org.jetbrains.kotlinx:kotlinx-serialization-core` · version `1.11.0` · added 2026-10-08. `-json` (same version) added 2026-10-23 to read the server's WebSocket messages for remote control.
- **Docs:** https://kotlinlang.org/docs/serialization.html

### Koin
- **What:** dependency injection: creates objects (repositories, the Jellyfin client) once and hands them to whoever needs them.
- **Why:** no code generation, little setup, with Compose and Navigation 3 integration.
- **Alternatives:** Hilt (Google's recommendation for large apps; more setup and annotation processing); manual DI (fine for small apps, but ViewModel scoping is more work).
- **Coordinate:** `io.insert-koin:koin-bom` · version `4.2.2` · with `koin-android`, `koin-androidx-compose`; test: `koin-test`, `koin-test-junit4` · added 2026-10-08. `koin-compose-navigation3` is not needed (yet): `koinViewModel()` already works per screen thanks to the ViewModel decorator of Navigation 3.
- **Docs:** https://insert-koin.io/docs/reference/koin-compose/compose

### Timber (logging)
- **What:** a thin layer on top of Android's `Log` that automatically uses the class name as the tag and that you switch on or off per build.
- **Why:** it plants a `DebugTree` only in debug builds, so release logs nothing. That keeps tokens and stream URLs out of logcat without having to write `if (BuildConfig.DEBUG)` everywhere. The official Jellyfin TV app uses it too.
- **Alternatives:** `android.util.Log` directly. That works, but then you have to handle tags and turning logging off in release yourself.
- **Coordinate:** `com.jakewharton.timber:timber` · version `5.0.1` · added 2026-10-08 (last release 2021; the library is finished, not abandoned)
- **Docs:** https://github.com/JakeWharton/timber

### ktlint + compose-rules (code style)
- **What:** ktlint checks and fixes formatting according to the Kotlin and Android style guides. compose-rules adds Compose-specific checks (e.g. a `modifier` parameter, no `MutableState` as a parameter).
- **Why:** enforces the style rules without anyone having to review them. `./gradlew ktlintFormat` fixes most things itself; `./gradlew ktlintCheck` runs at the end of every phase. Settings are in `.editorconfig` (style `android_studio`, trailing commas on, PascalCase for composables).
- **Alternatives:** detekt (looks for broader code smells; overlaps largely and mostly produces noise in a hobby project); Spotless (a wrapper around ktlint, an extra layer with no gain for a single module).
- **Coordinate:** Gradle plugin `org.jlleitschuh.gradle.ktlint` · version `14.2.0`; ktlint itself `1.8.0`; ruleset `io.nlopez.compose.rules:ktlint` · version `0.6.7` · added 2026-10-08
- **Docs:** https://pinterest.github.io/ktlint/ · https://mrmans0n.github.io/compose-rules/

### JUnit 4 + koin-test (testing)
- **What:** JUnit 4 is the test runner for JVM tests. `koin-test` checks that all Koin modules are complete (`verify()`), so a missing binding doesn't first show up as a crash on the TV.
- **Why:** JUnit 4 is needed for Compose UI tests and is the standard on Android. No mocking library: we use fakes (see `.claude/rules/testing.md`).
- **Alternatives:** JUnit 5 (doesn't work with Compose UI tests without an extra plugin); Kotest (different style, more setup).
- **Coordinate:** `junit:junit` · version `4.13.2`; `io.insert-koin:koin-test`, `koin-test-junit4` (via Koin BOM `4.2.2`) · added 2026-10-08.
- **Docs:** https://junit.org/junit4/ · https://insert-koin.io/docs/reference/koin-test/verify


### Compose UI tests on the device (ui-test + AndroidX Test)
- **What:** tests that run real Compose screens on the TV and press keys (`pressKey(Key.DirectionDown)`) to check where the focus ends up. `ui-test-junit4` provides the test rule and the assertions, `ui-test-manifest` an empty test screen (debug only), and `androidx.test:runner` and `ext:junit` start the tests on the device.
- **Why:** focus behavior (entering a row = item 1, returning to the right item) can't be tested on the JVM and has broken once already (2026-10-09). `testing.md` asks for UI tests only for behavior like this.
- **Alternatives:** testing only by hand with `adb shell input keyevent` (doesn't catch a regression automatically); Robolectric (doesn't run Compose focus as reliably as the device does).
- **Coordinate:** `androidx.compose.ui:ui-test-junit4` and `ui-test-manifest` (via the Compose BOM `2026.09.00`); `androidx.test:runner` · version `1.7.0`; `androidx.test.ext:junit` · version `1.3.0` · added 2026-10-09
- **Docs:** https://developer.android.com/develop/ui/compose/testing

### Jellyfin Kotlin SDK
- **What:** the official Kotlin client for the Jellyfin server API: logging in, fetching libraries, building stream and image URLs, and the DeviceProfile.
- **Why:** saves hundreds of hand-written API calls and models.
- **Alternatives:** writing Retrofit/Ktor calls yourself (a lot of work, error-prone).
- **Coordinate:** `org.jellyfin.sdk:jellyfin-core` · version `1.8.12` · added 2026-10-08 (phase 1)
- **Note on the version choice:** the newest is 1.9.2, but 1.9.x only works with Jellyfin 12. The server runs 10.11.11, and 1.8.12 recommends exactly that version. Switch only when the server is upgraded to Jellyfin 12. Android Lint reports the newer version; that warning is deliberately turned off in `app/lint.xml`.
- **Docs:** https://kotlin-sdk.jellyfin.org/

### DataStore (Preferences)
- **What:** stores small settings (server URL, token, user ID, device ID) safely and asynchronously on the device.
- **Why:** the successor to SharedPreferences, works with coroutines/Flow.
- **Alternatives:** SharedPreferences (blocking, outdated); Room (a database, overkill).
- **Coordinate:** `androidx.datastore:datastore-preferences` · version `1.2.1` · added 2026-10-08 (phase 1)
- **Docs:** https://developer.android.com/topic/libraries/architecture/datastore

### Kotlin Coroutines
- **What:** makes asynchronous work (network, timers) readable as regular code, with `Flow` for values that change.
- **Why:** the standard on Android and required by the SDK, Koin and DataStore. The repositories use it directly, so it is listed explicitly in the build.
- **Alternatives:** RxJava (outdated for new projects).
- **Coordinate:** `org.jetbrains.kotlinx:kotlinx-coroutines-android`; test: `kotlinx-coroutines-test` · version `1.11.0` · added 2026-10-08 (phase 1)
- **Docs:** https://kotlinlang.org/docs/coroutines-overview.html

### slf4j-timber (logging from the Jellyfin SDK)
- **What:** a small bridge that forwards log messages from SLF4J to Timber. SLF4J is the standard logging interface in the Java world; the Jellyfin SDK logs through it (via `kotlin-logging`).
- **Why:** without an SLF4J implementation the SDK crashes on the first network call (`NoClassDefFoundError: org/slf4j/LoggerFactory`, seen on the Chromecast in phase 1). Through Timber, the SDK logs follow our own policy: visible only in debug builds.
- **Alternatives:** `slf4j-android` (always logs, also in release); `logback-android` (heavy, its own configuration). The official Jellyfin TV app also uses slf4j-timber.
- **Coordinate:** `io.github.unveloper:slf4j-timber` · version `0.0.8` · added 2026-10-08 (phase 1). Last release 2022; the library is small and finished.
- **Docs:** https://github.com/unveloper/slf4j-timber

### Media3 (ExoPlayer)
- **What:** Google's video player library: fetches the stream, decodes it with the Chromecast's hardware and shows the picture and subtitles.
- **Why:** the only engine on Android that sends HDR and Dolby Vision to the TV properly, with passthrough, PGS/VobSub subtitles and MediaSession. Every serious Jellyfin client uses it. Research: `docs/research/playback-engine.md`.
- **Alternatives:** mpv (can't output HDR/DV on Android, tone-maps to SDR; ±23 MB); libVLC (±40 MB, dropped by the official Jellyfin app in 2024 over memory problems and a CVE); the old `MediaPlayer` (too limited).
- **Coordinate:** `androidx.media3:media3-exoplayer`, `media3-session`, `media3-ui` (phase 2), `media3-ui-compose` (phase 5) · version `1.11.1` · added 2026-10-08 (phase 2), plus `media3-exoplayer-hls` for transcodes via HLS
- **Parts:** `media3-ui-compose` provides the Compose building blocks for our own player overlay (phase 5): `ContentFrame` (the video picture) and small state holders for play/pause and progress, without ready-made buttons. `media3-session` creates a `MediaSession`, so the system and the Assistant ("pause") can control the player. `media3-ui` now only provides `SubtitleView` (draws subtitles, including PGS); the mobile app uses it the same way, with `ContentFrame` from `media3-ui-compose` for the picture and its own Compose controls (2026-10-28).
- **Docs:** https://developer.android.com/media/media3

### FFmpeg audio decoder for Media3 (Jellyfin build)
- **What:** an extra audio decoder (FFmpeg) that Media3 uses for formats the device can't decode itself.
- **Why:** the Chromecast has no decoder at all for AC3, EAC3, DTS or TrueHD (measured). Without this extension such audio only plays if the chain can pass it through; TrueHD and DTS would otherwise make the server transcode.
- **Alternatives:** building FFmpeg ourselves (a lot of work); letting the server transcode everything (loads the N150, Atmos lost).
- **License:** GPL-3.0. The source stays MIT, but an APK that includes this decoder is distributed under GPL-3.0 as a whole, so its source must be shared with it; see `THIRD-PARTY-NOTICES.md`.
- **Coordinate:** `org.jellyfin.media3:media3-ffmpeg-decoder` · version `1.9.0+1` (built against Media3 1.9; the official Jellyfin app runs it with 1.11.1) · added 2026-10-08 (phase 2). Ship armeabi-v7a only (about 1.4 MB).
- **Docs:** https://github.com/jellyfin/jellyfin-androidx-media

### Coil 3
- **What:** loads images (posters, backdrops) from the server, scales them and keeps a cache in memory and on disk.
- **Why:** Kotlin- and Compose-first, lightweight, with caches you can cap; important on 2 GB of RAM. JellyCast uses one `ImageLoader` (memory cache 20% of the app heap without backdrops, disk cache 200 MB) with its own OkHttp client (OkHttp is already in the app via the Jellyfin SDK; the remote control WebSocket uses it too, because the SDK's WebSocket didn't reconnect after the app had been in the background), and asks for WebP via the `Accept` header.
- **Alternatives:** Glide (older, View-oriented); loading images ourselves (no caching).
- **Coordinate:** `io.coil-kt.coil3:coil-compose` + `io.coil-kt.coil3:coil-network-okhttp` (Coil 3 needs a separate network module) · version `3.6.3` · added 2026-10-09. `:core` builds the `ImageLoader` and uses `io.coil-kt.coil3:coil` (Coil without Compose, same version, 2026-10-28); the apps add `coil-compose`.
- **Docs:** https://coil-kt.github.io/coil/

### Composition tracing (only in the `profile` measurement build)
- **What:** puts the names of composables (e.g. `SeriesCard`, `FocusCard`) into a Perfetto trace. Without it you only see "Compose:recompose" and don't know *which* part of the screen is taking the time. `runtime-tracing` provides the names; `tracing-perfetto-binary` is the small native library that writes them to Perfetto. Turn it on per app start with `adb shell am broadcast -a androidx.tracing.perfetto.action.ENABLE_TRACING nl.jellycast.tv/androidx.tracing.perfetto.TracingReceiver`. The measurement build is `profileable` for this (`app/src/profile/AndroidManifest.xml`).
- **Why:** this is how we found that tv-material `Surface` cost ~1.7 ms per card when building a row (2026-10-09). Only `profileImplementation`: debug and release don't get it.
- **Alternatives:** Android Studio's profiler (does the same, but needs a cable session and can't be run from scripts); system traces only (no names).
- **Coordinate:** `androidx.compose.runtime:runtime-tracing` · via the Compose BOM (1.12.1) · `androidx.tracing:tracing-perfetto-binary` · version `1.0.1` · added 2026-10-09
- **Docs:** https://developer.android.com/develop/ui/compose/tooling/tracing

### AndroidX Core KTX
- **What:** Kotlin helpers on top of the Android framework, e.g. `contentValuesOf` for the Watch Next rows.
- **Why:** `:core` used it already; before the module split it came in indirectly through other libraries. A library module has to name what it uses.
- **Alternatives:** building `ContentValues` by hand (a few more lines, nothing gained).
- **Coordinate:** `androidx.core:core-ktx` · version `1.19.1` · added 2026-10-28
- **Docs:** https://developer.android.com/kotlin/ktx#core

### Android Library plugin
- **What:** the AGP variant that builds a library module (an `.aar`) instead of an app; used by `:core` and `:core-testing`.
- **Why:** the TV and mobile apps share `:core`.
- **Alternatives:** none; part of AGP.
- **Coordinate:** `com.android.library` · same version as AGP · added 2026-10-28
- **Docs:** https://developer.android.com/build/configure-library-module

### Compose Material 3 (mobile app only)
- **What:** Google's standard component set for phones and tablets: buttons, text fields, navigation bar and rail, chips, top bars.
- **Why:** the mobile app needs touch components; tv-material is built for focus and D-pad. Only `:mobile` uses it, so the TV app never mixes the two.
- **Alternatives:** our own components on Compose foundation (much more work for the same result).
- **Coordinate:** `androidx.compose.material3:material3` · version from the Compose BOM `2026.09.00` · added 2026-10-28
- **Docs:** https://developer.android.com/develop/ui/compose/designsystems/material3

## Planned

Nothing planned.

## Advice (not added yet)

### libass for full ASS subtitles (`io.github.peerless2012:ass-media`)
- **Advice:** later, as an opt-in setting, and only after a memory measurement on the Chromecast. MIT, about 3 MB native (armeabi-v7a). The official Jellyfin app temporarily removed it in 2025 because of memory-related crashes and brought it back as an option in 0.20.

### Baseline profiles (`androidx.profileinstaller`)
- **Advice:** not now. See `.claude/rules/gradle-build.md`.

## Removed

_(nothing yet)_
