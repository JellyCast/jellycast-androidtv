# Project structure

The fixed folder and package tree of JellyCast, and where new code goes. Rules for layers and dependencies are in `architecture.md`.

## Layout: feature-based with a shared core [A1] [A4]

- Feature code (screen, ViewModel, UiState) lives together per feature in `feature/<name>/`. Shared code lives in `core/<topic>/`.
- Why feature-based and not per layer (`data/`, `ui/screens/`): everything for one screen is in one folder, and the packages map one-to-one to NiA modules (`:core:data`, `:feature:home`) should we ever split.
- Playback is split into `core/playback/` (engine: DeviceProfile, ExoPlayer setup, reporting, no UI) and `feature/player/` (screen and overlay). Why: the engine can be unit-tested on its own and the UI can be replaced without touching the engine.

## Tree

```
core/src/main/java/nl/jellycast/core/   # :core, shared by both apps, no UI
  model/                      # own models: Movie, Episode, Series, MediaRow, PlaybackPosition ... (plain Kotlin)
  network/                    # Jellyfin SDK setup (createJellyfin, ApiClient), ImageUrlBuilder
  common/                     # AppDispatchers, GenreResources (server genre → strings.xml) and other small helpers without Android UI
  datastore/                  # SessionStore (server URL, token, user id), UiPreferencesStore
  data/
    repository/               # SessionRepository, LibraryRepository, PlaybackRepository, LanguageRepository (+ Default*)
    ResourceGenreNames.kt     # genre names and A–Z order in the chosen language (for the shelves)
    mapper/                   # *Mappers.kt: SDK DTO → core/model
  playback/                   # DeviceProfileFactory, CodecCapabilities, PlayerFactory, PlaybackReporter, PlayMethod
core/src/main/res/values*/strings.xml   # genre names (the only texts :core needs)
core-testing/src/main/java/nl/jellycast/core/testing/   # Fake*Repository, MainDispatcherRule, test data builders

tv/src/main/java/nl/jellycast/tv/       # :tv, this app
  JellyCastApp.kt                # Application: startKoin, Coil singleton
  MainActivity.kt               # the only Activity; host for JellyCastNavHost
  di/                           # Koin modules
  navigation/
    Destinations.kt             # @Serializable NavKeys (Home, Detail(itemId), Player(itemId), ...)
    JellyCastNavHost.kt          # NavDisplay + entryProvider
  core/
    designsystem/
      theme/                    # JellyCastTheme, colors, typography, spacing (tv-material)
      component/                # PosterCard, TvRow (row with focus behaviour), PillButton, Backdrop ...
    ui/                         # small shared UI helpers without design (e.g. focus utils)
  feature/
    server/                     # ServerScreen: find or enter the server address and check it
    login/                      # LoginScreen: Quick Connect and password
    home/                       # start screen: section bar (Home/Series/Movies/Library), always opens on Home;
                                #   HomeSuggestion*: the Home section, the suggestion stack
    movies/                     # Movies section: detail view and bookcase
    detail/                     # movie detail
    series/                     # Series section (rows) and the series page (seasons × episodes)
    library/                    # Library: all movies or series as a grid, search and sort
    settings/
    player/                     # PlayerScreen.kt, PlayerViewModel.kt, overlay components

core/src/test/java/nl/jellycast/core/     # JVM tests of :core
tv/src/test/java/nl/jellycast/tv/         # JVM tests of :tv; mirrors the main tree
tv/src/androidTest/java/nl/jellycast/tv/  # Compose UI/focus tests on device/emulator

tv/src/main/res/
  values/strings.xml                     # all texts, English (default language, see resources.properties)
  values/donottranslate.xml              # texts that are the same in every language (4K, HDR10, OK …)
  values-xx/strings.xml                  # one file per language (es, fr, nl); the language list follows from these
  resources.properties                   # unqualifiedResLocale=en
  drawable-xhdpi/banner.png              # 320×180, with the app name in the image
  xml/network_security_config.xml        # cleartext for the LAN only

mobile/                                  # :mobile, phone and tablet app; see mobile/README section in README.md
```
## File rules

- One screen = `XxxScreen.kt` (stateful entry point + stateless `XxxContent`), `XxxViewModel.kt`, `XxxUiState.kt` in `feature/xxx/`.
- A large screen is split into several files in its feature folder by responsibility, e.g. `XxxState.kt` (state holder), `XxxKeys.kt` (key and focus handling), `XxxLayout.kt` / per-part composables, and drawing code. Same limits as `code-language.md` (~400 lines per file).
- A component used by two or more features moves to `core/designsystem/component/`. Until then it stays `private` or `internal` in the feature.
- A feature never imports from another feature. Shared logic goes to `core/`.
- `:core` doesn't import Compose.
- New top-level folders under `nl/jellycast/tv/` or `nl/jellycast/core/` only after consulting the user.
- Keep the tree in this file up to date when a feature or core package is added.
