# Gradle and build

Build configuration, SDK levels, the version and dependency policy, signing, R8 and baseline profiles.

## SDK levels

- `:tv`: `minSdk = 34`. Why: the only target device runs Android TV OS 14. No compat paths for API < 34 in `:tv` (no `Build.VERSION.SDK_INT` checks below 34, no `@RequiresApi(≤34)`, no core library desugaring).
- `:core` and `:mobile`: `minSdk = 31` (Android 12), so cheaper phones and tablets run the app (the test tablet, a Tab 16, runs Android 12). A newer API there gets an `SDK_INT` check with the closest older behaviour; lint (`NewApi`) finds them.
- `targetSdk` = newest stable API, `compileSdk` at least equal to `targetSdk`.

## Setup [E1] [E2]

- Modules `:core`, `:core-testing`, `:tv`, `:mobile` (see `architecture.md`). No `buildSrc`, no convention plugins. Why: four small build files don't need the indirection. `:core` exposes its libraries (Jellyfin SDK, Media3 engine, Coil, DataStore, coroutines) as `api`.
- Run app tasks per module (`:tv:installDebug`, `:mobile:installDebug`); a bare `./gradlew installDebug` installs both apps.
- All versions and coordinates live in `gradle/libs.versions.toml`. Never a version string in `build.gradle.kts`.
- Compose artifacts via the Compose BOM, without their own versions. `androidx.tv:tv-material` and Media3 have their own version in the catalog; all `media3-*` artifacts share one `version.ref`.
- Use the Kotlin Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) with the same version as Kotlin.
- Always the Gradle wrapper (`./gradlew`), never a globally installed `gradle` after the bootstrap.

## Versions and dependencies

- When adding or updating, look up the newest stable version (Google Maven / Maven Central / release notes). Never guess, no alpha/beta/rc, unless no stable version exists and the user agrees.
- Add a dependency only if it really adds something. Never for something you write yourself in a few lines (a helper, an extension function, one API call).
- If it qualifies, add it directly without asking, and record it in the same commit in `docs/dependencies.md` using the template there: what it is in plain language, why, alternatives considered, coordinate + version + date, and a link to the official docs. The user learns the ecosystem from that file; write it for someone who doesn't know Android.
- On a version update, update the entry in `docs/dependencies.md`.
- When you remove a dependency, also remove it from the catalog and move it to the "Removed" section of `docs/dependencies.md`, with the reason.

## Signing and secrets [E6]

- Release signing reads `keystore.properties` in the project root (in `.gitignore`); the keystore itself lives outside the repo (`~/keys/jelzefin.jks`). If `keystore.properties` is missing, `assembleRelease` builds unsigned and the build doesn't fail.
- `local.properties` and `keystore.properties` are in `.gitignore`. A dev default for the server URL may come via `local.properties` → `BuildConfig`, debug only.
- Bump `versionCode` and `versionName` for every release APK that goes on the TV. Why: `adb install -r` refuses a lower `versionCode`, and this way you see on the TV which version runs.

## R8 [E3] [B6]

- Release: R8 on with code and resource shrinking. Use the DSL that matches the AGP version (`isMinifyEnabled`/`isShrinkResources` + `proguard-android-optimize.txt`, or from AGP 9.3 `optimization { enable = true }`). Debug: R8 off.
- Add keep rules only narrowly (one class/package), each with a one-line why in `proguard-rules.pro`. Never `-keep class ** { *; }` or `-dontobfuscate` to hide a crash.
- Test every release APK on the device before it counts as "final". Reflection in the Jellyfin SDK or Koin problems only surface there.

## Baseline profiles [E4] [C2]

- Don't add them now. Why: with sideloading they only apply after background dexopt (often at night), and they need an extra `:baselineprofile` module plus Macrobenchmark.
- Reconsider after phase 3 if scrolling on Home stutters in a release build. Measure first; when in doubt, use `adb shell cmd package compile -m speed-profile -f nl.jellycast.tv` to approximate the effect.
- When installing via adb, `adb shell cmd package compile -m speed -f nl.jellycast.tv` makes up for the missing baseline profiles. Not `speed-profile` right after installing: the profile is still empty then.

## Lint and formatting [B7] [B8] [B9]

- ktlint with compose-rules is in place; settings in `.editorconfig`. Run `./gradlew ktlintFormat` after writing code, and keep `ktlintCheck` and `lintDebug` clean before every release. No detekt.
