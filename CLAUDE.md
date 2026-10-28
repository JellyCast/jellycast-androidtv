# JellyCast

Own Jellyfin client for Android TV, package `nl.jellycast.tv`, Application class `JellyCastApp`.
Target device: Chromecast with Google TV 4K (Android TV OS 14, API 34, about 2 GB RAM), installed by sideloading.
Goal: simple, reliable and smooth browsing and playback, with maximum direct play. Smooth comes before pretty (see `product-principles.md`). Personal use only.

## Documents

- `NOTES.md`: status, what is not built yet, decisions, known issues, and codec findings per test file. Keep it up to date.
- `docs/setup.md`: tooling on the Mac, preparing the Chromecast, signing and installing.
- `docs/dependencies.md`: every library with an explanation. Update on every dependency change.
- `docs/testing-emulator-and-device.md`: starting the emulator and what to test where (UI in the emulator, video and final checks on the Chromecast).
- `README.md` and `docs/navigation.md`: public description of the app and the controls, with design images from Paper in `docs/images/design/`. Update when the controls or a screen change.
- `docs/translating.md`: where the texts live and how to add a language (one `values-xx/strings.xml`).
- `docs/research/best-practices-sources.md`: sources behind the rules (codes like `[A2]`).
- `docs/research/jellyfin-androidtv-comparison.md`: comparison with the official client and the decision per candidate.
- `docs/research/playback-on-other-devices.md`: making playback robust on other TVs, boxes and audio (lessons from Moonfin and jellyfin-androidtv).
- `docs/research/solved-playback-bugs.md`: playback problems Moonfin, jellyfin-androidtv and Media3 already solved, checked against JellyCast (open items in order).

## Rule files (`.claude/rules/`)

- `product-principles.md`: **why the app exists and the priority smooth > pretty**, with the performance budget and a checklist per change. Overrides all other rules.
- `code-language.md`: everything in English, when a comment is allowed, readable code, and the Dutch→English glossary of design terms.
- `design.md`: what the design may use on this hardware: effects (cheap/limited/forbidden), image sizes, skeleton, layout, focus. Starts with an index of the headings across the four design files.
- `design-style.md`: color, typography, tokens, brand, banner, splash and sign-in screens.
- `design-screens.md`: layout per screen (Home, Series, Movies/bookcase, Library, movie detail, series page).
- `design-navigation.md`: navigation with the remote, transitions and the player overlay.
- `architecture.md`: layers, UDF, ViewModel/StateFlow, repositories, DTO mapping, Koin, Navigation 3, single module.
- `project-structure.md`: the package tree and where new code goes.
- `kotlin-compose-style.md`: naming, Compose conventions, stability, previews, coroutines.
- `tv-ux.md`: manifest, D-pad/focus, safe area and typography, memory and images on 2 GB RAM.
- `playback.md`: Media3, choices, lifecycle, controls, DeviceProfile, codecs/HDR, subtitles (loads only for `**/playback/**` and `**/player/**`).
- `playback-streams.md`: stream choice, per-format settings, Media Segments, start log, error recovery, reporting (same paths).
- `gradle-build.md`: minSdk 34, version catalog, dependency policy, signing, R8, baseline profiles.
- `testing.md`: what to test, fakes instead of mocks, coroutine tests, focus tests.
- Keep every rule file and this file under 100 lines (Claude reads instructions initially only up to 100 lines). If a file grows longer, split it by topic and put an index of the headings in the original file, so references like "design.md → Layout" keep working.

## Working agreements

- Everything in the repository is English: code, comments, docs, rules, commit messages (see `code-language.md`). Talk to the user in Dutch. User-facing text lives only in `strings.xml`.
- Close each change with `./gradlew testDebugUnitTest ktlintCheck lintDebug :tv:installDebug` on the connected Chromecast, then the compile step (see "Commands"), and a short list of what the user should test. If the change touches UI or playback, also measure the performance budget from `product-principles.md` on a release build and record it in `NOTES.md`.
- Check UI yourself: `adb exec-out screencap -p > screen.png` and `adb shell input keyevent KEYCODE_DPAD_RIGHT` (etc.). Test D-pad, Back and focus restoration per screen.
- Always test video on the real Chromecast; the emulator lacks its hardware codecs.
- Dependencies: add only what really adds something, and then do it without asking. Record it in `docs/dependencies.md` (see `gradle-build.md`).
- Look versions up, never guess them; stable versions only.
- SDK DTOs only in `core/network`, `core/data` and `core/playback`. Map to `core/model`.
- Write unit tests for mappers, DeviceProfile logic, resume/next-up and ViewModels.
- No secrets in code or git: `local.properties` and `keystore.properties` are in `.gitignore`.
- Small commits per step.
- Write what you discover on the device in `NOTES.md` (codec messages, low-RAM status, transcode reasons).

## Commands

```bash
adb connect <chromecast-ip>:5555
./gradlew :tv:installDebug
adb shell cmd package compile -m speed -f nl.jellycast.tv   # after every adb install: otherwise Compose runs in the interpreter
adb shell am force-stop nl.jellycast.tv                     # restart so the compiled code loads
adb shell dumpsys package dexopt | grep -A3 nl.jellycast.tv # check: status=speed
adb shell am start -n nl.jellycast.tv/.MainActivity
adb logcat --pid=$(adb shell pidof nl.jellycast.tv)
adb shell cat /sdcard/Android/data/nl.jellycast.tv/files/playback-starts.txt   # what happened in the last 10 starts
./gradlew :tv:assembleRelease   # R8; judge performance only on this
adb install -r tv/build/outputs/apk/release/tv-release.apk   # then the same compile step
scripts/perf/measure-scroll.sh left-right   # janky frames during 30 s key repeat (put the focus in place first)
scripts/perf/measure-steady.sh <apk> rows    # same, but installs + compiles as after a night first (judge the budget on this)
```

Emulator (Google TV, API 34, 1080p) for quick UI and focus work; video and performance always on the Chromecast:

```bash
emulator -avd Google_TV_1080p_API_34 &
ANDROID_SERIAL=emulator-5554 ./gradlew :tv:installDebug   # picks the emulator when the Chromecast is also connected
```

Android CLI (`android`, in `cmdline-tools/latest/bin`; replaces `sdkmanager`):

```bash
android sdk list | install | update   # manage SDK packages
android docs <query>                  # search the official Android docs; use this before web search
android screen                        # screenshot of the connected device
android layout                        # layout tree of the app on the device (check focus/structure)
android run                           # build, install and launch
```
