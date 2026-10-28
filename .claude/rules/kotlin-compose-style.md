# Kotlin and Compose style

Code style, naming, Compose conventions and coroutine/Flow rules specific to this stack. Language and comments: `code-language.md`.

## Basics [B1] [B2]

- Follow the Kotlin coding conventions and the Android Kotlin style guide: 4 spaces, max. 100 characters per line, trailing commas in multiline lists.
- No `!!`. Use `?:`, `requireNotNull(x) { "reason" }` or an early `return`.
- No wildcard imports.
- Log only through the chosen logger (see `docs/dependencies.md`), never `println`. Never log the access token or the full stream URL with `api_key`.

## Naming [A2] [B4]

- Composables that draw UI: PascalCase, a noun (`PosterCard`). Composables that return a value: camelCase (`rememberHeroState`).
- `remember…` for functions that remember a state holder; `…Defaults` for objects with a component's default values.
- Repository flows: `observeXxx(): Flow<Xxx>` or `getXxxStream()`; choose the NiA form `getXxxStream()` and keep it consistent.
- Implementations: `DefaultXxxRepository`; fakes: `FakeXxxRepository`.
- UiState: `XxxUiState`; ViewModel actions: `onXxx()` (`onPlayClick`, `onFocusItem`).

## Compose conventions [B3]

- Every UI composable has `modifier: Modifier = Modifier` as its first optional parameter, and applies it exactly once, to the root.
- Parameter order: required, `modifier`, optional, trailing `content` lambda.
- Components are stateless: pass a value plus a callback. Never `MutableState<T>` or `State<T>` as a parameter; use `T` or `() -> T` to defer reads.
- Screen pattern: `XxxScreen(viewModel = koinViewModel())` collects state and passes it to `XxxContent(uiState, onAction…)`. Only `XxxContent` has previews.
- In `:tv` use `androidx.tv.material3` for components and theme. Never import anything from `androidx.compose.material3` there. Check: `grep -r "androidx.compose.material3" tv/src` returns nothing. [C3] `:mobile` uses Material 3 and never tv-material.
- No `CompositionLocal` for component configuration, only for theme tokens (spacing, colors).

## Stability and recomposition [B5] [B6]

- Strong skipping is on by default. Add no `@Stable`/`@Immutable` and no kotlinx-immutable-collections unless a compiler report or trace shows a concrete problem.
- Models in `core/model` and UiState classes are `data class`es with only `val`s.
- Don't create new lists or lambdas in a composable that change on every recomposition and are passed to list items. Do the mapping in the ViewModel.
- In `LazyRow`/`LazyColumn` always pass `key = { it.id }` and, where useful, `contentType`.
- Read fast-changing values (scroll position, player position) in a lambda modifier (`Modifier.offset { }`, `drawBehind { }`), not in composition.
- Judge performance only on a release build (R8, not debuggable). Debug builds of Compose are much slower.

## Previews

- Every `XxxContent` and every designsystem component has a `@Preview` with `device = Devices.TV_1080p` and fixed test data.
- Previews never call Koin, the network or a ViewModel.

## Coroutines and Flow [A7]

- Start coroutines in `viewModelScope`; repositories start nothing themselves and have no scope of their own, except `PlaybackReporter` (injected app scope, so the stop report is still sent after the screen closes).
- Never `GlobalScope`, `runBlocking` (outside tests) or `Thread.sleep`.
- Dispatchers are injected, never hardcoded.
- Catch specific exceptions. Always rethrow `CancellationException`; so don't use `runCatching` around suspend calls without doing that.
- Periodic work (progress reporting, hero rotation) via `while (isActive) { …; delay(…) }` in a scope that stops with the screen.
- Expose `StateFlow`, never `MutableStateFlow`.
