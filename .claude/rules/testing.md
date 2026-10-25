# Testing

What gets tested, how, and with which test doubles. Manual checks on the device are in `tv-ux.md` and `CLAUDE.md`.

## What to test [A2]

- Required JVM unit tests (`app/src/test`) for:
  - DTO → model mappers (including missing fields and null images);
  - `createDeviceProfile` with fake `CodecCapabilities`: at least "H.264 only", "HEVC Main10 + HDR10", "DV profile 8 without EL", "passthrough EAC3 on/off";
  - resume and next-up logic, ticks ↔ ms conversion, `PlayMethod` determination;
  - every ViewModel: initial state, success, error, and every `onXxx` action;
  - repositories with a fake source (only where there is logic; no tests for pure pass-throughs);
  - Koin module verification (one test).
- Compose UI tests (`app/src/androidTest`) only for focus behaviour that broke before or is tricky: initial focus, "entering a row = item 1", Back to the starting point, and focus restoration after navigating back on Home and Series. No UI tests for static layout.
- Don't test: composables at pixel level, the Jellyfin SDK itself, or Media3 internals.
- A bug fix first gets a failing test, if the logic is testable on the JVM.
- Test names are English backtick sentences describing behaviour (see `code-language.md`).

## Test doubles [F1] [A4]

- Use fakes, not mocks. `FakeXxxRepository` implements the full interface, keeps data in a `MutableStateFlow` and has test hooks (`emit(…)`, `failNext = true`).
- Fakes and test data builders live in `app/src/test/.../testing/` and are reused. Never copy a fake per test class.
- No mocking library (Mockito/MockK). If an SDK class can't be faked, put an interface of your own in `core/` in front of it.

## Coroutines and Flow [F2] [A2]

- Every test with coroutines runs in `runTest`.
- Replace `Dispatchers.Main` with a JUnit rule (`MainDispatcherRule`) that calls `Dispatchers.setMain(testDispatcher)`; inject the same `testDispatcher` as the io dispatcher.
- Assert `uiState` on `.value`. For a `stateIn(WhileSubscribed)` flow, first start a collector in `backgroundScope` (`backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }`).
- No `delay`/`Thread.sleep` in tests; use `advanceTimeBy`/`advanceUntilIdle` (e.g. for the 10-second progress of `PlaybackReporter`).

## Compose UI tests [F3]

- Find nodes by `testTag` or semantics text. Put `testTag`s only on elements a test needs.
- Test focus with `onNodeWithTag(…).performKeyInput { pressKey(Key.DirectionRight) }` and `assertIsFocused()`.
- Use the stateless `XxxContent` with a fixed UiState, not the whole screen with Koin.

## Running

- `./gradlew testDebugUnitTest` must be green for every commit that touches logic.
- UI tests run on the connected Chromecast; only before a release or when focus code changed. If the Chromecast is connected twice (IP and mDNS, `adb devices`), `./gradlew connectedDebugAndroidTest` runs twice and fails on uninstalling the app. Then run via adb on one connection:
  `ANDROID_SERIAL=<ip>:5555 ./gradlew installDebug installDebugAndroidTest && adb shell am instrument -w nl.jellycast.tv.test/androidx.test.runner.AndroidJUnitRunner`
- Never put a test you can't get green on `@Ignore` silently. Report it and record it in `NOTES.md`.
