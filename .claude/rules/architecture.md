# Architecture

Layers, data flow, state, DI and the module setup of JellyCast. Where code lives is in `project-structure.md`.

## Module setup [A3] [A4]

- Keep one Gradle module `:app`. Why: one developer, one device, a few thousand lines of code; you don't get the scaling and build-time gains of modules here, only the overhead.
- Organise the packages like Now in Android organises its modules (`core/*`, `feature/*`). A later split into Gradle modules is then mechanical.
- Add a module only with the user's approval. The only expected candidate: a `:baselineprofile` test module (see `gradle-build.md`).

## Layers [A1] [A2]

- Three layers: UI (`feature/*`, `core/designsystem`), data (`core/data`, `core/network`, `core/datastore`) and the playback engine (`core/playback`). No separate domain layer.
- Create a use case in `core/domain` only when the same logic is needed in two or more ViewModels (e.g. determining next-up). Never a use case that only forwards a repository call.
- Dependencies go one way: `feature` → `core/data` → `core/network`/`core/datastore`. Never from `core` to `feature`, never between features.

## Data layer [A2] [G1]

- Access the Jellyfin SDK (`ApiClient`) and DataStore only from `core/data` (repositories) and `core/playback` (DeviceProfile, reporting). ViewModels and composables never call the SDK or DataStore.
- Define each repository as an interface with one implementation (`DefaultLibraryRepository` etc.). Why: tests use a `Fake…` implementation.
- Return one-off actions as `suspend fun`, changing data as `Flow`.
- Make suspend functions main-safe: the repository does `withContext(ioDispatcher)` with an injected dispatcher. Never hardcode `Dispatchers.IO`. [A7]
- Map SDK DTOs (`org.jellyfin.sdk.model.api.*`) in `core/data` to own models in `core/model`. Check: `grep -r "org.jellyfin.sdk" app/src/main/java/nl/jellycast/tv/feature` returns nothing.
- Mappers are pure top-level extension functions (`fun BaseItemDto.toMovie(): Movie`) in a `*Mappers.kt` file. No Android types in them, so they are JVM-testable.
- Handle SDK errors in the repository: catch `ApiClientException`/`IOException` and pass on a `Result` or an error state. Never `catch (e: Exception)` and never swallow `CancellationException`. [A7]
- Keep auth state (server URL, token, user id) in one place: `SessionRepository` on top of `SessionStore` (DataStore). The token never leaves `core/data`; the UI only sees `AuthState`. The device id comes from the SDK (`androidDevice()`). The `ApiClient` is updated via `api.update(baseUrl, accessToken)`, not recreated per screen.

## UI layer and state [A2]

- One ViewModel per screen destination. Reusable components get no ViewModel but state and callbacks.
- Expose exactly one `val uiState: StateFlow<XxxUiState>`. Use a sealed interface (`Loading`/`Success`/`Error`) when the states are mutually exclusive, otherwise a data class.
- Build `uiState` from repository flows with `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. Only for state without a source flow: a private `MutableStateFlow` with a public `StateFlow`.
- In Compose, always collect with `collectAsStateWithLifecycle()`.
- UI actions go to the ViewModel as method calls (`onPlayClick(id)`). The ViewModel sends no events back (no `Channel`/`SharedFlow` for navigation or toasts). Put the result in state, and let the UI handle and acknowledge it (`onNavigated()`).
- No `Context`, `Activity` or `Resources` in a ViewModel, and no `AndroidViewModel`. Strings come from resources in the UI.
- Pass navigation arguments (item id) via the Navigation 3 key to the ViewModel as a constructor parameter via Koin (`parametersOf`).

## Navigation [A2] [A5]

- Navigation between signed-in and signed-out follows `AuthState` (in `JellyCastNavHost`): sign in → Home, sign out or a revoked token → Login. Screens don't navigate after signing in or out; they only change the state in the repository.
- Use one Activity with Navigation 3: destinations are `@Serializable` keys (`data object Home : NavKey`, `data class Detail(val itemId: String) : NavKey`), the back stack is an app-owned list, and `NavDisplay` renders it.
- Scope ViewModels per back-stack entry via the Navigation 3 ViewModel decorator, so a ViewModel disappears when its entry leaves the stack.
- Keys contain only ids and primitive values, never models.

## Dependency injection (Koin) [A8]

- Start Koin in `JellyCastApp.onCreate()` with `startKoin { androidContext(this@JellyCastApp); modules(...) }`. Not via the `KoinApplication` composable, because `Application`-level objects (Coil, player) need Koin outside Compose too.
- One Koin module per package group (`networkModule`, `dataModule`, `playbackModule`, `featureModules`), defined in `di/`.
- Use constructor injection. Koin APIs (`get()`, `inject()`, `KoinComponent`) appear only in `di/` and in composable entry points (`koinViewModel()`), never in ViewModels, repositories or mappers.
- `single` for `Jellyfin`, `ApiClient`, repositories, `ImageLoader` and DataStore; `viewModel`/`viewModelOf` for ViewModels; `factory` for the rest.
- Inject dispatchers via a qualifier (`named("io")`) or a small `AppDispatchers` object, so tests can replace them.
- Add one JVM test that verifies all Koin modules (Koin-test `verify()`), so a missing binding doesn't first crash on the TV.
