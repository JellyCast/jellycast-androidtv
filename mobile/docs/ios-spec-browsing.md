# JellyCast iOS: spec of the browsing screens, for the Android port

Everything below comes from reading the code. All files are under `/Volumes/workspace/personal/jellycast/jellycast-ios/JellyCast/`. Sizes are in pt; treat them as dp. A few things differ from what you expected:
- **There is no password sign-in.** The app signs in with Quick Connect only.
- **The Library has no segmented control.** Movies/Series is a "Kind" picker inside the filter menu.
- **The Library opens with the status filter set to "Not watched", not "Everything".**
- **`PersonScreen` is in `Feature/Library/PersonScreen.swift`.**

## 0. Text style → size table
The app uses SwiftUI text styles at the default Dynamic Type size, so these are the values to copy:

| Style | Size / weight |
|---|---|
| largeTitle | 34 |
| title2 | 22 |
| title3 | 20 |
| headline | 17 semibold |
| body | 17 |
| subheadline | 15 |
| footnote | 13 |
| caption | 12 |
| caption2 | 11 |

The app is dark only (`.preferredColorScheme(.dark)`). The global tint is `accent`.

---

## 1. App shell (`App/JellyCastApp.swift`, `App/MainView.swift`, `App/AppNavigation.swift`)

### RootView
- It is a ZStack on `background`.
  - With a stored session (`SessionStorage.load()`), it shows `MainView`.
  - Without one, it shows `SignInScreen`.
- `MainView` is rebuilt (`.id`) whenever the access token changes.
- If the library reports `isSessionExpired` (a 401 during sync), the app signs out.
- Sign-out does the following:
  - clears the session;
  - deletes the session storage and the library cache;
  - calls `POST Sessions/Logout`.

### Splash (`Feature/Splash/BrandSplash.swift`)
- It is drawn above everything once per launch, at zIndex 1, and fades out over 0.2 s (easeOut).
- Layout: a vertical stack with a gap of 28.
  - **BrandMark**, 132×132. Its outline draws in over 600 ms with the cubic-bezier curve (0.4, 0, 0.2, 1).
  - **BrandWordmark** below it.
    - "Jelly" is bold in `text`; "Cast" is light in `accent`.
    - Default size is 36, with letter-spacing −0.02×size.
    - It fades in (linear, 0.25 s) starting 500 ms after the mark begins.
- Timing: hold 250 ms after the draw finishes, then call `onFinish`. A tap anywhere skips the splash.
- The background matches the system launch screen (#0E0E0F).
- BrandMark gradient stops: neonBlue → neonWhite at 0.4 → neonPurple at 0.75 → neonPink at 1. It also uses a lavender #D9CCFF weld glow.
  - stroke 7 on a 200 viewBox
  - a signal dot (radius 6) with arcs of radius 18 and 32

### Tabs
The shell is a `TabView` with three tabs. Each tab has its own NavigationStack and its own back-stack:

| Tab | Label (en → nl) | Icon (SF Symbol) |
|---|---|---|
| Home | "Home" → "Home" | `house` |
| Library | "Library" → "Bibliotheek" | `square.stack` |
| Downloads | "Downloads" → "Downloads" | `arrow.down.circle` |

- All routes can be pushed on any tab: `.series(id)`, `.movie(id)`, `.person(PersonResult)`, `.downloadedSeries(id)`, `.lists`, `.list(id)`.
- `AppNavigation.open(route)` pushes onto the current tab's stack.
- `showNewInLibrary()` switches to the Library tab and sets sort = Newest and status = nil (Everything).

### Settings button
- It is only on Home: a toolbar item at top-trailing, with SF `person`, labelled "Settings".
- It opens `SettingsScreen` as a modal sheet.

### Bottom accessory
This uses the iOS 26.1 `tabViewBottomAccessory`, a glass capsule above the tab bar.
- **Shown when:** a remote TV session is playing (`TVBar`), or a download is running and the current tab is not Downloads (`DownloadAccessory`). TVBar wins over the download bar.
- **TVBar:**
  - a still of 48×32 in a capsule;
  - a title in subheadline semibold: "Episode name · Series name", or the movie name;
  - a line in caption medium at 80% text opacity: "Playing on {device} · 32 min left" or "Paused on {device} · …", refreshed every 1 s;
  - a trailing `tv` icon at 17 semibold in `accent`;
  - a 2 pt gradient progress line along the bottom, inset 24.
  - Tap opens the full-screen RemoteNowPlaying screen.
- **DownloadAccessory:**
  - DownloadStateIcon at 32;
  - the title;
  - the status line, e.g. "Downloading · 42%";
  - a 2 pt progress line, inset 24.
  - Tap switches to the Downloads tab.
  - In the inline (collapsed) placement, the second line and the progress line are hidden.

### Mini player (local playback)
- It is an overlay at the bottom-trailing corner, with a trailing margin of 16.
- Bottom inset: 72 on phone (above the tab bar), 16 on iPad (the tab bar is at the top). Add 56 (phone) or 88 (iPad) while the TV bar shows.
- Width:
  - phone: small 200; large = shorter screen side − 32;
  - iPad: 320 small / 480 large.
- Corner radius 12. A pinch toggles between small and large.
- Show/hide transition: scale from 0.6 anchored bottom-trailing, plus a fade.
- Tap expands it to the full-screen player.

### Other shell UI
- **Waking pill:**
  - text "Turning on {name}…" (nl "%@ wordt aangezet…");
  - a spinner next to the text;
  - subheadline semibold;
  - height 44, horizontal padding 18, glass capsule, bottom-centred.
- **Alerts:**
  - **Server problem at launch or on return to the app:**
    - Title: "You're offline." (nl "Je bent offline.") or "Can't reach {server}" (nl "%@ is niet bereikbaar").
    - Message when there are no downloads: "What you see is from your last visit; nothing plays until the server answers."
    - Message when there are downloads: "What you see is from your last visit; only your downloads play until the server answers."
    - Buttons: "Try again", "Connect to another server" (signs out), "Cancel".
    - If the server is unreachable at launch, the user is still on Home root, and there are finished downloads, the app switches to the Downloads tab instead of showing the alert.
  - "The list wasn't changed": shown with the error text after a failed list edit.
  - "The TV didn't get that".
  - A player start error, which uses the error text as its title.
- **Offline flag:** `isOffline` = server unreachable OR no network. While it is set, `availableOffline(itemID)` dims any item not on the device to opacity 0.35, disables its taps, and gives it the accessibility hint "Not downloaded".
- **Server checks:** on network change and whenever the app becomes active, the app re-checks the server with `GET System/Info/Public` (5 s timeout). It refreshes the library when connectivity comes back after a failed sync.

### iPad (`layout.isRegular`)
"Regular" means both the horizontal and the vertical size class are regular. A landscape iPhone stays compact. Values from `LayoutMetrics`:

| Metric | Compact | Regular |
|---|---|---|
| margin | 20 | 32 |
| textMaxWidth | ∞ | 640 |
| upNextCardWidth | 220 | 300 |
| posterWidth | 120 | 150 |
| castImageSize | 72 | 88 |
| cardSpacing | 12 | 16 |
| heroLogoSize | 280×80 | 420×120 |

The iPad tab bar is at the top.

---

## 2. Home (`Feature/Home/HomeScreen.swift`, `Core/Library/LibraryIndex.swift`)

Home is built entirely from the cached library snapshot, so it works offline.

### Screen states
- **Snapshot available:** the content below.
- **No snapshot and the sync failed:** `ContentErrorView` with the message, a "Try again" glass button, padding 32, centred.
- **Otherwise:** `HomeSkeleton`, made of `surface` blocks:
  - a full-bleed rectangle 300 high;
  - a 140×22 bar (radius 4);
  - two 220×124 cards (radius 10), spacing 12;
  - section spacing 26, horizontal padding 20.
- **Sync-problem banner:** an overlay at the top when a cached library is shown after a failed refresh (`SyncProblemBanner`):
  - glass rounded rect, radius 16, padding 16 horizontal / 10 vertical, outer padding 16/8;
  - a `wifi.slash` icon in textMuted;
  - the message in footnote semibold;
  - "Updated {relative time}" in caption textMuted (nl "Bijgewerkt %@");
  - a trailing `arrow.clockwise`, or a spinner while retrying;
  - tap retries;
  - it slides in from the top.
- Navigation bar: inline title mode, no title text.

### Pull to refresh
- The app uses its own control (`RefreshableScrollView`) because the content runs under the status bar.
- A 36 pt glass circle sits just under the top safe area, 8 above the content.
  - Its `arrow.clockwise` icon at 15 semibold rotates progress×270°.
  - The icon turns `accent` once the threshold (pull distance = top inset) is reached.
  - The circle scales from 0.6 to 1 and its opacity follows progress.
  - Light haptic at the threshold.
- Releasing past the threshold calls `store.refresh()`, which fetches the full library. The spinner shows for at least 600 ms, and the content stays pushed down while refreshing.

### Layout
A vertical column with section spacing 26 and bottom padding 24, in this order:

**1. Hero (`HomeHeroCard`)**
- **Which item:** the first entry of `continueWatching`. If that list is empty, the fallback is the first episode with season > 0 of the newest not-started series; failing that, the first unwatched movie in the snapshot (sorted by SortName).
- **DetailHero:** the full-bleed backdrop (see §4). The backdrop is the movie backdrop (else its poster), or for an episode the series backdrop (else the still).
- **Logo:** the movie logo, or the series logo for an episode, centred and overlapping the bottom edge of the hero by half the logo height.
- **Play card in the middle of the hero (`HeroContinueFrame`):**
  - a 16:9 still, radius 10;
  - width = min(hero width × 0.56 (iPad 0.44), hero height × 0.62 × 16/9);
  - 1 pt border in text at 14% opacity; shadow black 55%, radius 18, y 8;
  - a 56 pt circular play button in the centre: black 35% fill, 1.5 pt stroke at text 85%, `play.fill` at 22 semibold, nudged 2 pt right;
  - a progress bar along the bottom of the still (see ContinueImage below).
  - Tap plays the item immediately. Long press opens the action drawer.
- **Under the hero** (hero bottom spacing 18; inner VStack spacing 10):
  - **Label** in caption semibold, tracking 1, UPPERCASE, `accent`:
    - "Continue watching" (nl "Verder kijken") when the item is in progress;
    - "Next episode" (nl "Volgende aflevering") when it is next but not started;
    - "Start something new" (nl "Iets nieuws beginnen") when it is the fallback.
  - **Detail line** in subheadline textMuted, 1 line:
    - movie: "2014 · 1 h 2 min left" (year · timeLabel);
    - episode: "S3 · E4 · Episode name · 41 min".
- **Tap anywhere on the hero card** (outside the play card) opens the movie page, or the series page for an episode.

**ContinueImage** (shared by the hero and Up next):
- 16:9, radius 10.
- Image choice:
  - movie: backdrop (type Backdrop), else poster (type Primary);
  - episode: still (Primary), else series backdrop (Backdrop).
- When in progress, a progress bar at the bottom: height 3, track = background at 70%, horizontal padding 10, bottom padding 8.

**2. Row "Up next"**
- Subtitle "in progress and next episode" (nl "Volgende" / "bezig en volgende aflevering").
- Shown only if `continueWatching.count > 1`. It lists items 2 onwards; the first is the hero.
- **UpNextCard** (width 220, iPad 300; the stack inside has spacing 6):
  - ContinueImage at that width;
  - a "NEW" badge (`ImageBadge`, nl "Nieuw") at top-leading with padding 8, shown for an episode that is not in progress and was added within 7 days;
  - title in subheadline semibold `text`: the series name, or the movie name;
  - detail in footnote textMuted, 1 line: "S3 · E4 · 18 min left", or for a movie "Year · time".
  - Tap plays immediately. Long press opens the drawer.

**3. Row "New"**
- Subtitle "last 30 days" (nl "Nieuw" / "laatste 30 dagen").
- Trailing link "All" (nl "Alles") in subheadline `accent`. It calls `showNewInLibrary()`: Library tab, sort Newest, status Everything.
- **Cards:** PosterCard.
  - Movie caption: "Movie · 2014" (nl "Film · 2014").
  - Series caption: "Series · 3 new" (nl "Serie · %lld nieuw"), or "Series · new season" (nl "Serie · nieuw seizoen").
- Tap opens the movie or series page. Long press opens the drawer.

**4. Row "Not started"**
- Subtitle "series you haven't begun" (nl "Nog niet begonnen" / "nog niet gekeken").
- Shows all series with status notStarted, newest `dateAdded` first.
- Caption "{n} seasons", counting seasons > 0, with plural forms (nl "%lld seizoen/seizoenen").
- Tap opens the series page. Long press opens the drawer.

**Rows, general**
- No row has a size limit; each is a horizontal LazyRow.
- Section header: padding = margin; header-to-row spacing 10. The row has content padding = margin, card spacing 12 (iPad 16), and no scroll indicator.
- **SectionHeader** (all on one line, 1 line max, aligned on the first baseline, spacing 8): title in title3 bold `text`; subtitle in subheadline textMuted; spacer; optional trailing link button in subheadline `accent`.
- **PosterCard:** width 120 (iPad 150), height = width × 1.5, radius 10. Caption below with spacing 6, footnote textMuted, max 2 lines.

### Row logic (`LibraryIndex`)

**SeriesProgress per series**
- Episodes are sorted by air order: season ascending with season 0/nil last, then episode number.
- "Regular" episodes are those with season > 0.
- Season summaries are sorted with Specials (0) last.
- `lastTouched` is the regular episode with the latest `lastPlayed` among those played or in progress.
- `next`:
  - if `lastTouched` is in progress, `next` is that episode;
  - otherwise it is the first unwatched regular episode after `lastTouched`;
  - if nothing was ever touched, `next` is nil.
- Status:
  - watched if every episode is played;
  - inProgress if any episode is watched or in progress;
  - otherwise notStarted.

**continueWatching**
- It combines:
  - movies in progress (`!played && positionTicks > 0`), keyed by `lastPlayed`;
  - each series' `next` (only if the series was touched), keyed by max(lastTouched, next.dateAdded), so a newly added episode moves its series to the front.
- Sorted by that key, descending. No limit.

**newEntries (30-day window)**
- Movies: not played and added within 30 days.
- Series: not fully watched, with episodes added in the window. The key is the latest added date and the count is the number of added episodes.
- `isNewSeason` is true when the series has older seasons and the newly added episodes include a season number that is not among them.
- Sorted by date, descending.

**Episode filter:** episodes without a SeriesId are dropped.

---

## 3. Library (`Feature/Library/*`)

### Screen
- The navigation bar is hidden.
- States:
  - snapshot available: `LibraryGrid`;
  - sync failed: ContentErrorView;
  - otherwise: `LibrarySkeleton` (a 120×14 bar, then 4 rows × 3 posters at 2:3 with radius 8, spacing 18/12, padding 20).
- The sync banner is a top safe-area inset.
- Pull to refresh uses the system `.refreshable` and calls `store.refresh()`.
- **Search bar:** a bottom safe-area inset that floats above the tab bar.
  - Horizontal padding = margin, bottom padding 8, gap 10.
  - Field: glass capsule, height 48, horizontal padding 16.
    - `magnifyingglass` icon in textMuted;
    - placeholder "Movies, series, people" (nl "Films, series, mensen");
    - autocorrect off; return key "search";
    - an `xmark.circle.fill` clear button (labelled "Clear", nl "Wissen") when the field is not empty.
  - A 48×48 glass circle beside the field holds the filter menu.

### Filter menu
- The button is `line.3.horizontal.decrease` at 18 semibold. It turns `accent` when `isNarrowed` (kind ≠ all, a genre is set, or status ≠ nil). Accessibility label: "Filter and sort".
- Menu contents, in order:
  1. **Picker "Kind"** (inline): All / Series / Movies (nl Alles / Series / Films).
  2. **Picker "Status"** (inline):
     - Everything (nil) (nl Alles);
     - Not watched (notStarted) (nl Nog niet gezien);
     - In progress (nl Bezig);
     - Watched (nl Gezien).
  3. **Picker "Genre"** (submenu):
     - "All genres" (nl Alle genres);
     - then "{translated genre} · {count}", sorted by count descending.
     - Genres are canonicalised: "Sci-Fi & Fantasy" → Science Fiction + Fantasy, "Action & Adventure" → Action + Adventure, "War & Politics" → War + Politics, "Sci-Fi" → Science Fiction, "Kids" → Family.
     - Dutch names come from `Genres.xcstrings`: Action→Actie, Adventure→Avontuur, Animation→Animatie, Comedy→Komedie, Crime→Misdaad, Documentary→Documentaire, Family→Familie, History→Geschiedenis, Music→Muziek, Mystery→Mysterie, News→Nieuws, Politics→Politiek, Romance→Romantiek, Science Fiction→Sciencefiction, TV Movie→Tv-film, Talk→Talkshow, War→Oorlog. All others stay the same.
     - Counts are computed over the current Kind.
  4. **Picker "Sort"** (submenu): Newest / A–Z / Year / Length (nl Nieuwste / A–Z / Jaar / Lengte).
  5. **Section** with a "Lists" button (`list.number`, nl "Lijsten") that pushes ListsScreen.
- **Defaults:** kind All, genre nil, **status Not watched**, sort Newest.
- Typing a non-empty search term resets status to nil.

### Active filter chips
- Shown at the top of the grid when the filters are narrowed; they scroll horizontally, spacing 8, top padding 8.
- Each chip is a capsule in `rail`, height 32, horizontal padding 12, subheadline semibold:
  - the label text;
  - for status, the matching count at 70% opacity;
  - an `xmark` (caption2 bold).
- Tapping a chip clears that filter.

### Grid content (top to bottom)
1. **Recent searches**, only while the field is focused, the term is empty, and there are recent searches:
   - label "RECENT SEARCHES" (nl "Recent gezocht");
   - rows of height 48: a `clock` icon (24 wide) and the term in body; a trailing `xmark` (28×48, labelled "Remove") forgets the term.
   - Tapping a row fills the query. Up to 5 terms are kept in UserDefaults, most recent first, de-duplicated case-insensitively.
2. **Empty state:** "Nothing here" (nl "Hier staat niets") when there is no term, or "No results for “term”" (nl "Geen resultaten voor ‘%@’"); textMuted, centred, top padding 48.
3. **Sections:**
   - Each has a `ListSectionLabel` (UPPERCASE, footnote semibold, tracking 0.8, textMuted; top 18, bottom 10), then a tile grid.
   - Section titles by sort:

     | Sort | Order | Section titles |
     |---|---|---|
     | Newest | dateAdded descending | "Added this week" (<7 days, nl "Deze week toegevoegd"), "Added this month" (<30 days, nl "Deze maand toegevoegd"), "Earlier" (nl "Eerder") |
     | A–Z | ignores a leading "The ", "A ", "An ", "De ", "Het ", "Een "; localizedStandardCompare | first letter uppercased, or "#" for non-letters |
     | Year | descending | decade "2010s" (key "%@s", nl "jaren %@"), or "Unknown year" (nl "Onbekend jaar") |
     | Length | descending; a series uses the sum of its episode runtimes | "Over 3 hours" (nl "Langer dan 3 uur"), "2 to 3 hours" (nl "2 tot 3 uur"), "Under 2 hours" (nl "Korter dan 2 uur") |

4. **People** (only with a search term): label "PEOPLE" (nl "Mensen"; top 18, bottom 8), then PersonRows. Tap remembers the term and pushes the Person screen.
5. **Episodes** (only with a term, and only if kind ≠ Movies and no genre is set): label "EPISODES" (nl "Afleveringen"), then EpisodeResultRows. Tap pushes the series page. Long press opens the episode drawer.

- Horizontal padding = margin, bottom padding 24. Scrolling or tapping dismisses the keyboard.

### Tile grid (`LibraryTileGrid`)
- Columns = max(3, floor((width + spacing) / (minWidth + spacing))).

| | Compact | Regular |
|---|---|---|
| minWidth | 100 | 150 |
| column spacing | 12 | 20 |
| row spacing | 18 | 24 |

- The last row is padded with empty cells.

### LibraryTile
- Poster at 2:3, radius 8, bottom padding 2.
  - A watched item gets a 26 pt circle at top-trailing (padding 6): background at 72% with a `checkmark` (caption bold).
  - A movie in progress gets the on-image progress bar at the bottom (padding 6).
- Title in footnote semibold `text`, 1 line. Year in caption textMuted.
- Text stack spacing 4.
- Tap opens the movie or series page and records the term in recent searches. Long press opens the drawer. The tile is dimmed when offline.

### Search behaviour (`LibrarySearch.swift`)
- Titles: movie and series names are matched by case- and diacritic-insensitive substring. The Kind and Genre filters still apply; status is reset.
- Episodes: episode names in the local snapshot are matched the same way, first 10.
- People: `GET /Persons?userId&searchTerm&limit=10&enableImageTypes=Primary`, debounced 300 ms and cancelled when the term changes.
- **PersonRow:**
  - a 60 pt circle in `rail` with initials (2 letters, title3 semibold, textMuted), overlaid by the photo;
  - the name in headline;
  - the role in subheadline textMuted when present (it is absent in search results);
  - a trailing `chevron.right`;
  - vertical padding 8, spacing 14.
- **EpisodeResultRow:**
  - still 96×54, radius 6;
  - name in subheadline semibold;
  - "Series · S1 · E3" in footnote textMuted;
  - vertical padding 6, spacing 14.

### Action drawer (long press, `Core/DesignSystem/ActionDrawer.swift` + `Feature/Library/MediaContextMenu.swift`)
- **Trigger:** a 0.4 s long press, with a medium haptic.
- **Sheet:**
  - height fits the content, with a drag indicator;
  - padding: horizontal 20, top 28, bottom 12; rows spaced 10.
  - The chosen action runs after the sheet has closed.
- **Header:**
  - image 44×66 (poster), or 96×54 (wide, for episodes), radius 6; spacing 14;
  - title in headline, max 2 lines;
  - subtitle in subheadline textMuted, 1 line.
  - Movie: "Movie · 2014"; series: "Series · 2019"; episode: the series name as title and "S1 · E3 · Episode name" as subtitle.
- **SheetRow:**
  - minimum height 56, padding 14, radius 18;
  - fill: text at 5%; stroke 1.5 at text 10%;
  - a 40 pt icon circle with the tint at 12% (icon 18 semibold);
  - title in body semibold;
  - the accented row uses an `accent` tint.

**Actions, in order** (a series acts on its `next` episode where an episode is needed):
1. "Play" (accented, `play.fill`).
   - Becomes "Continue" (nl "Verder") when the item is in progress.
   - For a series it reads "Play S2 · E3" (key "Play %@", nl "%@ afspelen").
   - It only appears if there is something to play; a series without a `next` has none.
2. "Play from the beginning" (`arrow.counterclockwise`, nl "Vanaf het begin afspelen"), only when in progress.
3. "Play on…" (`tv`, nl "Afspelen op…"), which opens the PlayOnSheet (TV picker).
4. Watched actions, hidden when offline:
   - "Mark as watched" (`checkmark.circle`, nl "Markeren als gezien") if the status ≠ watched;
   - "Mark as not watched" (`circle`, nl "Markeren als niet gezien") if the status ≠ notStarted.
   - An item in progress gets both.
5. Download actions, not for series:
   - "Remove download" when the download is done (nl "Verwijder download"); "Cancel download" while it is in progress (nl "Annuleer download");
   - otherwise "Download…" (`arrow.down.circle`, nl "Downloaden…"), only when the server is reachable. It opens the DownloadSheet.
6. "Add to list…" (`text.badge.plus`, nl "Aan lijst toevoegen…"), hidden when offline. It opens AddToListSheet.
7. "Go to movie" (`film`, nl "Naar film"), or "Go to series" (`rectangle.stack`, nl "Naar serie"), which works for episodes too.
8. "Open on {IMDb|Rotten Tomatoes|TMDB}" (`arrow.up.right.square`, nl "Openen op %@") when a URL can be built. It opens an external link.

**Setting played:** `store.setPlayed` updates the UI immediately, then calls the server. On a series it marks every episode. It is reverted if the call fails.

---

## 4. Movie detail (`Feature/Movie/MovieScreen.swift`, `Core/DesignSystem/DetailHero.swift`, `Feature/Player/ItemInfoPanel.swift`)

### States and loading
- If the movie id is not in the index: ContentErrorView "This movie is no longer in the library." (nl "Deze film staat niet meer in de bibliotheek.") with "Try again", which refreshes the library.
- When the screen opens, it loads `mediaInfo` (`GET Items/{id}?fields=MediaSources`) and warms the stream cache.
- The scroll content runs under the status bar. The navigation bar is inline with only the system back button.

### DetailHero
- Full width. Height = width/(16/9) + status-bar inset, capped at 55% of the window height.
- The backdrop is an image (type Backdrop, falling back to the poster) under a vertical gradient over `background`:

  | Position | Background opacity |
  |---|---|
  | 0 | 45% |
  | 0.3 | 0 |
  | 0.5 | 0 |
  | 0.8 | 75% |
  | 1 | 100% |

- The overlay is padded below the status bar.
- **Logo:** an image of type Logo fitted inside heroLogoSize (280×80, iPad 420×120), centred at the bottom edge and offset down by half its height. The hero adds that half height as bottom padding.
  - With no logo, it shows the title in system 34 heavy, centred, max 2 lines, scaling down to 0.6.
- **Play button in the centre (`HeroPlayButton`):**
  - a 76 pt circle: black 25% fill, 1.5 stroke at text 85%;
  - `play.fill` at 28 semibold, nudged right by 8% of the icon size;
  - while in progress, a 96-wide progress bar below it (height 3, track text 30%), spacing 12;
  - an optional caption in footnote semibold with a black 60% shadow (radius 4).
  - Tap plays.

### Summary block
- Below the hero, padding = margin, top 18. The column spacing is 22 and the summary spacing is 14.
- **Heading:**
  - no title here (the logo is the title);
  - meta line in subheadline textMuted: "2014 · 2 h 49 min · Science Fiction, Drama · PG-13" — year · runtime · first 2 translated genres joined by ", " · officialRating;
  - **TechBadges** (top padding 2), from MediaInfo: UPPERCASE caption bold, tracking 0.6, height 24, horizontal padding 8, 1 pt `rail` border, radius 6, spacing 6.
- **Tech badge rules (`TechLine`):**
  - Resolution: height > 1100 → "4K"; > 730 → "1080p"; otherwise "{h}p".
  - Range: DOVI* → "Dolby Vision", HDR10Plus → "HDR10+", "HDR10", "HLG".
  - Audio (from the default audio track, else the first):
    - truehd → "TrueHD" or "TrueHD Atmos";
    - eac3 → "Dolby Digital+" or "Dolby Atmos";
    - ac3 → "Dolby Digital";
    - dts → "DTS-HD MA", "DTS:X" or "DTS";
    - aac with more than 2 channels → "AAC 6ch";
    - flac → "FLAC".
  - "PGS" if any subtitle track is pgssub.
- **Overview:** `ExpandableText`, subheadline textMuted, 3 lines; tap expands.
- **Action row** (horizontal, spacing 10):
  1. **ExternalLink capsule:**
     - the source name in subheadline bold;
     - an `arrow.up.right` (caption bold, textMuted);
     - height 34, horizontal padding 14, `surface` fill.
  2. **Rating text** in footnote textMuted: "★ 7.8" (one decimal), or "91%" for Rotten Tomatoes.
  3. **PlayOnButton** (34 `RailCircleButton`, `tv`), only when remote sessions are available.
  4. **Watched toggle** (34 RailCircleButton): `checkmark` when not watched, `checkmark.circle.fill` when watched. Labelled "Mark as watched" / "Mark as not watched".
  5. **DownloadButton** (34).

### DownloadButton
The 34 pt `rail` circle has these states:

| State | Look |
|---|---|
| not downloaded | `arrow.down.to.line` at 13 bold |
| queued | dashed ring (dash 3, 4.5) + `clock` |
| running | progress ring in `accent` (ring width 2.5, inset 5, track text 20%) + `stop.fill` at 9 |
| waiting for network | progress ring + `wifi.slash` at 11 |
| paused | ring + `arrow.down` |
| failed | `exclamationmark` |
| done | `checkmark` at 15 bold in `accent` |

- Opacity is 0.4 when the server can't be reached.
- **Tap when there is no download:**
  - if the server is unreachable: re-check it, then alert "Downloading only works at home, where the server is.";
  - a movie opens the DownloadSheet;
  - an episode starts immediately with the default kind. If it doesn't fit: alert "This doesn't fit: {x} more is needed."
  - For episodes, long-press "Download options…" opens the sheet.
- **Tap when a download exists:** a menu.
  - done: "Remove download";
  - paused or failed: "Go on", "Cancel download";
  - running: "Pause", "Cancel download".
- **DownloadSheet** (medium detent, `surface` background, padding 20):
  - title "Download {title}" in title3 bold;
  - "Free on this iPhone: X";
  - option cards "Compact" and "Original" (radius 18, accent stroke when chosen), each with a size;
  - Compact description: "1080p, standard colours (SDR), one audio track in stereo. The server converts it: ready in about N min."
  - Original description: "{tech}, every audio and subtitle track. About N min to download."
  - An option that doesn't fit shows "Doesn't fit · X more needed" in orange.
  - An AccentButton "Download" with the size.

### Tabs (`InfoTabBar`)
- A `rail` capsule, height 40, inset 3. The selected segment is a capsule filled with grey #595959 (white 0.35).
- Labels in subheadline: semibold when selected, medium otherwise. Chat has an `accent` sparkle.
- Max width 640 on iPad.
- Choosing a tab scrolls the bar to the top. Each tab's content gets a minimum height so the bar can always reach the top.
- **Movie tabs:** "Cast" (nl "Acteurs"), "Related" (nl "Verwant"), plus "Chat" when an AI key is set.

**Cast tab (`CastTab`)**
- Loads `GET Items/{id}?fields=People`, then a batch person call: `GET Items?ids=a,b,c&fields=Overview,ProductionLocations,PremiereDate,ProviderIds`.
- **ActorRow** (row spacing 22):
  - a 64 pt circle (initials or photo), spacing 14;
  - name in headline;
  - an "IMDb ↗" text button on the right (footnote bold + caption2 arrow);
  - "as {role}" in subheadline `accent` (nl "als %@");
  - a facts line in footnote textMuted: "48 years · New York City, United States" — age from the birth date (PremiereDate), and birthplace = first + last part of ProductionLocations[0], with the country localised;
  - biography (Overview, with the Wikipedia attribution removed) in subheadline textMuted, 3 lines.
- The first 6 actors are shown, then a link "All N actors" + chevron.down in `accent`. With guest stars it reads "All N actors and M guest stars"; expanding adds a "GUEST STARS" section.
- **Makers:** a divider (1 pt `rail`), "Makers" in title3 bold, then:
  - "DIRECTED BY" and "WRITTEN BY", each a list of MakerRows (44 pt photo, name in subheadline semibold, facts line, link);
  - "PRODUCED BY" as plain text joined with " · ".
- Tapping a person's link opens their IMDb or TMDB page. Without a stored id it searches by name; Rotten Tomatoes always searches.
- Loading shows a spinner. On error: "The cast couldn't be loaded." + "Try again".

**Related tab (`RelatedTab`)**
- **Collection section:** the other movies with the same `TmdbCollection` provider id, oldest year first.
  - The title is the shared prefix before ":" when every name has it, e.g. "The Lord of the Rings". Otherwise "From the same collection" (nl "Uit dezelfde reeks").
- **"Related" section:**
  - `GET Items/{id}/Similar?limit=20&fields=People,Studios`;
  - each candidate is kept only if it shares at least 2 people with this movie (`Items/{id}?fields=People,Studios`) and is in the local library;
  - score = shared people × 2 + shared studios;
  - the movie itself and its collection are excluded;
  - movies of the same collection are grouped together in release order.
- **Grid:** adaptive columns of at least 110, spacing 12, row spacing 18.
  - Poster 2:3, radius 8;
  - title in footnote semibold, max 3 lines;
  - year in caption.
  - Tap pushes that movie's page.
- Empty: "Nothing related in the library." (nl "Niets verwants in de bibliotheek."). A spinner shows while loading.

**Chat tab:** an AI chat about the item, with an input bar pinned to the bottom. Out of scope for browsing.

### Rating source (`Core/Library/RatingSource.swift`)
The source is chosen in Settings and stored under the key `ratings.source`; the default is IMDb.

| Source | Rating shown | Title link |
|---|---|---|
| IMDb | "★ x.x" from `CommunityRating` for movies and series (the server puts OMDb first) | `https://www.imdb.com/title/{Imdb}/` |
| Rotten Tomatoes | `CriticRating` as "NN%", movies only | `https://www.rottentomatoes.com/search?search={name}` |
| TMDB | only for episodes (their CommunityRating comes from TMDb) | `https://www.themoviedb.org/movie/{id}`, `/tv/{id}`, `/tv/{seriesTmdb}/season/{s}/episode/{e}` |

Person links:
- IMDb: `https://www.imdb.com/name/{id}/`, else `https://www.imdb.com/find/?q=…&s=nm`;
- TMDB: `https://www.themoviedb.org/person/{id}`, else `https://www.themoviedb.org/search/person?query=…`;
- Rotten Tomatoes: search.

---

## 5. Series page (`Feature/Series/SeriesScreen.swift`)

### Error state
"This series is no longer in the library." (nl "Deze serie staat niet meer in de bibliotheek.") when the series, its progress, or a playable episode is missing.

### Which episode is shown
- `next` = `progress.next`, or the first regular episode when nothing has been watched or everything has.
- The page warms the stream cache for `next`.
- It uses the same `ItemInfoPanel` as the movie, with `showsWholeSeries = true` and no mediaInfo.
- On iPad landscape (regular and width > height), the layout splits:
  - left 64.5% of the width: the hero plus the summary, scrolling;
  - right: the tab bar (top padding 18 + top inset) and the tab content (padding 24), scrolling separately.

### Hero and buttons
- Hero: the series backdrop (else the poster) and the series logo.
- HeroPlayButton plays `next` and has a caption:
  - "Continue · S2 · E3" (nl "Verder · %@") when in progress;
  - "Watch again · S1 · E1" (nl "Opnieuw kijken · %@") when everything is watched;
  - otherwise "Play · S2 · E3" (nl "Afspelen · %@").
  - It is dimmed offline if `next` isn't downloaded.

### Summary
- Meta line: "2019 · 3 seasons · Drama, Crime · TV-MA". The season count excludes specials.
- No tech badges.
- Overview: the series overview.

### Action row
1. External link capsule and rating, for the series.
2. PlayOnButton (34) for `next`.
3. **DownloadNextButton** (34 circle):
   - shows `arrow.down.to.line` (16) when episodes are missing, or `arrow.down.circle.fill` (22, `accent`, disabled) when the next 5 are all on the device;
   - invisible (opacity 0) when there are no upcoming unwatched episodes.
   - Tap computes plans for the next 5 unwatched regular episodes from `next` that aren't downloaded, then shows a confirmation dialog:
     - title: the series name;
     - message: "S2 · E3 – S2 · E7 · about 4.2 GB · Free 80 GB";
     - button "Download 5 episodes".
   - It queues as many as fit and alerts "Only N of M fit on this device." otherwise.
   - It marks the series as "kept", so it stays 5 ahead when the "Keep series ahead" setting is on.
   - Accessibility: "Download next N", or "Next episodes are on this device".
4. **Mark-series-watched toggle** (34): `checkmark`, or `checkmark.circle.fill` when all episodes are watched. It calls `setPlayed` on the series id.
5. There is no per-item DownloadButton on the series level.

### Tabs
"Episodes" (nl "Afleveringen"), "Cast" (the series' own credits), plus Chat when enabled.

**Episodes tab (`SeriesEpisodesTab`)**
- **Season chips**, horizontally scrolling and centred on the selected season:
  - a capsule of height 34, horizontal padding 14, spacing 8, font subheadline;
  - **selected:** `text` fill with `background`-coloured text. It reads "Season 2" (bold) + "3/10" (watched/total) at 60% opacity, or "Specials".
  - **unselected:** `rail` fill. It reads "S2" (medium), or "Specials". The text is textMuted plus a `checkmark` when the whole season is watched, `text` otherwise.
  - The default season is `next`'s season, else the first season.
  - Long-pressing a chip opens a drawer (header: the season title and the series poster) with one action, "Add season to list…" (nl "Seizoen aan lijst toevoegen…"). It is hidden when offline.
- **Episode rows** (`EpisodeInfoList`, spacing 18):
  - still 136 wide (192 on a regular layout), 16:9, radius 8;
  - a watched episode's still is at opacity 0.55;
  - an in-progress episode gets the on-image progress bar (horizontal padding 8, bottom 6);
  - the `next` episode gets a 2 pt `accent` outline.
- Text next to the still (stack spacing 3):
  - "4. Episode name" in subheadline semibold, max 2 lines; textMuted when watched.
  - Status line in footnote:
    - watched: "Watched · 41 min" (nl "Gezien");
    - in progress: "18 min left" in `accent`;
    - otherwise: the runtime.
  - The episode's own "IMDb ↗" inline link plus its rating.
- A 28-wide column with a `checkmark` (15 bold, textMuted) when watched. A DownloadButton (34) at the end of the row.
- Overview (footnote textMuted, 3 lines): under the row on phone, next to the still on a regular layout.
- Tap plays the episode. Long press opens a drawer (header: the episode name, "S1 · E3", the wide still) with Mark as watched / Mark as not watched and "Add to list…".
- Each row is dimmed offline if the episode isn't downloaded.
- Downloading an episode starts immediately with the default kind; long press on the download button gives "Download options…".

---

## 6. Person screen (`Feature/Library/PersonScreen.swift`)
- Navigation title: the person's name (standard bar).
- Loads `GET /Items?userId&recursive=true&personIds={id}&includeItemTypes=Movie,Series`, which returns ids only.
- It shows the library entries (movies and series) whose ids are in that list, in the default library entry order: movies first, then series, both by SortName.
- The grid is the `LibraryTileGrid` (top padding 12, horizontal padding = margin, bottom 24), with the same tile, tap and long-press behaviour.
- States:
  - loading: a centred spinner in textMuted;
  - error: ContentErrorView with the error text and "Try again";
  - empty: "Nothing here".
- There are no sections and no person details.

---

## 7. Settings, sign-in, splash

### SettingsScreen (`Feature/Settings/SettingsScreen.swift`)
A modal sheet with its own NavigationStack. Title "Settings" (nl "Instellingen"), inline, with a "Done" (nl "Klaar") confirmation button. It is a grouped Form with these sections:

1. **"Server"**: rows Server = server name; Address = server URL (nl "Adres"); "Signed in as" = user name (nl "Ingelogd als").
2. **"Library"** (nl "Bibliotheek"):
   - counts for Movies, Series, Episodes;
   - "Updated" + relative time;
   - the sync error text in orange if the sync failed;
   - a "Refresh library" (nl "Bibliotheek vernieuwen") button with a spinner, disabled while syncing.
3. **"Streaming quality"** (nl "Streamingkwaliteit"):
   - pickers for "Local network" (nl "Lokaal netwerk"), "Wi-Fi" (nl "Wifi"), "Mobile data" (nl "Mobiele data");
   - options:
     - "Auto", "Auto, up to 1440p", "Auto, up to 1080p", "Auto, up to 720p" (nl "Auto, tot %@");
     - "Original" (nl "Origineel"), and the fixed levels.
     - The fixed levels' labels come from `StreamLevel.label`: bitrate and resolution, e.g. "5 Mbit/s · 1080p". The caps are 720p 2.5 Mbit/s, 1080p 5, 1440p 10.
   - defaults: Local = Original, Wi-Fi = Auto, Mobile = Auto up to 1080p;
   - a "Now on" row showing the current network type;
   - footer: "Local network is when the server is on the same network as this iPhone. Auto measures the connection when playback starts and picks the highest quality that keeps up. Below Original the server converts the video, in SDR."
4. **"Downloads"**:
   - toggle "Remove watched downloads" (default on, nl "Gekeken downloads verwijderen");
   - toggle "Keep series ahead" (default off, nl "Series bijhouden");
   - footer: "Watched downloads go a day after you watched them. A series you downloaded with Download next keeps its next 5 episodes on this device: at home, the next one downloads once you watch one."
5. **"Ratings"** (nl "Beoordelingen"):
   - picker "Rating and links" (nl "Beoordeling en links"): IMDb / Rotten Tomatoes / TMDB;
   - a footer per choice:
     - IMDb: "The IMDb rating of movies and series, with links to IMDb. Descriptions come from IMDb."
     - Rotten Tomatoes: "The Rotten Tomatoes critics' score, which the server has for movies only. Links search Rotten Tomatoes. Descriptions come from IMDb."
     - TMDB: "Links to TMDB. The server keeps a TMDB rating for episodes only. Descriptions come from IMDb."
6. **AI chat**: a navigation row labelled "AI chat" whose value is the provider name or "Off" (nl "Uit").
   - `AIChatSettingsScreen` configures:
     - the service, an inline picker: "Claude (Anthropic)" / "ChatGPT (OpenAI)";
     - the API key, stored in the Keychain:
       - SecureField with placeholder "sk-ant-…" or "sk-…" and a "Save key" button;
       - once saved, the masked key in monospace with "Change";
       - "Test key" with a spinner, a check mark, or an error such as "Not accepted";
       - "Remove key" (destructive);
     - toggle "Search the web";
     - toggle "No spoilers".
   - Without a key there is no Chat tab.
7. **"Developer"**: shown only when timings exist, e.g. "cache 120 ms · sync 900 ms".
8. **"Sign out"** (destructive, nl "Uitloggen"): dismisses the sheet and signs out.

### Sign-in (`Feature/SignIn/*`)
- A scrolling column: max width 480, horizontal padding 24, top 64, spacing 32, on `background`.
- **Header:** BrandMark 88×88, then BrandWordmark size 28, spacing 16.

**Phase: enter address / checking / failed**
- **Discovery section:**
  - "Servers on your network" (subheadline textMuted, nl "Servers op je netwerk"), with a spinner while searching.
  - **ServerRow:** `surface` fill, radius 12, padding 14.
    - name in body semibold;
    - detail in footnote textMuted: "192.168.1.4:8096 · Jellyfin 10.11.2", "… · not responding", or the bare address while checking;
    - "Current" (caption semibold `accent`, nl "Huidige") if it is the last used server;
    - a chevron.
  - Tap connects to that server.
  - Empty states: "Looking for servers on your network…", or, when done, "No Jellyfin server found on your network. Enter its address yourself, or search again." with a "Search again" glass button (`arrow.clockwise`).
- **Discovery mechanics:** a UDP packet "who is JellyfinServer?" on port 7359, sent unicast to every host in the local IPv4 subnets (capped at a /22), listening for 2 s. The JSON reply carries `{Id, Name, Address}`. Each server found is then checked with `GET System/Info/Public` to get its version.
- **Address form:**
  - label "Server address", or "Or enter the address" when servers were found;
  - TextField with placeholder "192.168.1.4:8096": URL keyboard, no capitalisation or autocorrect, go key; padding 14, `surface` fill, radius 12;
  - the error in footnote orange;
  - a "Connect" button (semibold, full width, glassProminent tinted `primaryContainer` #16161B, with a spinner while checking).
  - Address parsing: adds `http://` when the scheme is missing, strips trailing slashes, allows only http/https, and requires a host.
  - The last address is pre-filled.

**Connect flow**
1. `GET System/Info/Public`. Errors: "No Jellyfin server answers at this address." if ProductName lacks "Jellyfin" or decoding fails; also "This is not a valid address." and "The server can't be reached right now." / "You're offline.".
2. `POST QuickConnect/Initiate` returns {Code, Secret}.
3. Shows the **QuickConnect code view**:
   - "Sign in to {server}" in headline;
   - the code in system 44 bold monospaced digits, tracking 6, selectable;
   - "In Jellyfin, open your profile, choose Quick Connect and enter this code.";
   - a spinner;
   - a "Cancel" glass button, which returns to the address phase.
4. Polls `GET QuickConnect/Connect?secret=` every 2 s for up to 10 min. On timeout: "The code has expired. Try again for a new one."
5. `POST Users/AuthenticateWithQuickConnect {Secret}` returns AccessToken and User{Id, Name}. The session is saved.

- **Auth header:** `MediaBrowser Client="JellyCast", Device="iPhone", DeviceId="…", Version="…", Token="…"`.
- **There is no username/password path.**

---

## 8. Design system (`Core/DesignSystem/*`)

### Colours (`JellyCastColors`)

| Token | Hex | Notes |
|---|---|---|
| background | #0E0E0F | |
| surface | #1A1A1C | image placeholders, chips, sheets |
| rail | #2A2A2E | circle buttons, chips, borders |
| text | #ECE9E4 | |
| textMuted | #A3A09A | |
| accent | #B9A6F2 | |
| onAccent | #1A1206 | |
| primaryContainer | #16161B | |
| neonBlue | #7FC8FF | brand mark |
| neonWhite | #F2F6FF | brand mark |
| neonPurple | #9B6BFF | brand mark |
| neonPink | #FF6EC7 | brand mark |
| progressStart | #8FB8D8 | progress gradient |
| progressMid | #9A88D0 | progress gradient |
| progressEnd | #C584B0 | progress gradient |

Other colours used directly: the InfoTabBar selected fill is white 0.35 (#595959); errors and shortages use system orange.

### Components
- **AccentButton:** capsule in `accent`; minimum height 52, full width, horizontal padding 20.
  - icon at 15 bold (default `play.fill`);
  - title in headline bold, with optional detail in headline regular at 70%;
  - text in `onAccent`;
  - pressed opacity 0.7.
- **GlassCircleButton:** 44 pt circle on interactive glass; icon at 17 semibold in `text`.
- **RailCircleButton:** circle in `rail`, default size 52 (34 in detail action rows); icon at 18 bold.
- **PressedOpacityButtonStyle:** opacity 0.7 while pressed. Every tappable card uses it.
- **SectionHeader:** see §2.
- **ListSectionLabel:** footnote semibold, UPPERCASE, tracking 0.8, textMuted.
- **WatchProgressBar:** capsule track at text 22%, fill = a horizontal gradient progressStart → progressMid → progressEnd. Default height 4; the on-image variant is height 3 with the track at background 70%.
- **ImageBadge:** caption2 bold, tracking 0.6, uppercase; padding 8 horizontal / 3 vertical; capsule at background 72%.
- **ContentErrorView:** see §2.
- **SheetRow / CheckedSheetRow / SheetTitle:**
  - SheetRow: see §3.
  - CheckedSheetRow adds a trailing `checkmark` (16 bold, `accent`).
  - SheetTitle: title2 bold, plus a subtitle in subheadline textMuted, spacing 4.
- **RemoteImage:** a `surface` rectangle with the image scaled to fill, fading in over 0.15 s.
  - URL: `Items/{itemId}/Images/{Primary|Backdrop|Thumb|Logo}?tag=…&fillWidth={pt×scale}&quality=85`.
- **SyncProblemBanner:** see §2.

### Format helpers (`Format.swift`)
- **runtime:** "41 min", or "2 h 49 min", rounded to the nearest minute. Dutch: "%lld u %lld min".
- **remaining:** "{runtime} left", e.g. "18 min left". Dutch "nog %@", so "nog 18 min".
- **seasonEpisode:** "S3 · E4"; "S3" when there is no episode number; "Special" when season is 0 or nil.
- **joined:** drops nil and empty parts and joins the rest with " · ".
- **rating:** "★ " + the value with 1 decimal in the locale's format (Dutch shows a comma: "★ 7,8").
- **timeLabel:** the remaining time when in progress, otherwise the runtime.
- **megabits:** "117 Mbit/s", "2.5 Mbit/s".
- **Ticks:** 10,000,000 per second.
- **WatchState.isInProgress:** `!played && positionTicks > 0`.
- **progress:** position / runtime, capped at 1.

---

## 9. Lists (reached from the Library filter menu → "Lists")
Lists are Jellyfin playlists.

- **ListsScreen:**
  - large title "Lists"; a toolbar "+" ("New list") opens a name alert (TextField "Name"; "Create" / "Cancel"), then pushes the new list;
  - subtitle "Movies and episodes in your own order";
  - **rows:**
    - a poster stack of up to 3 posters, 50×75, each shifted 5 pt, back ones at opacity 0.7;
    - name in body semibold;
    - "N titles · M watched";
    - a status line: "Continue with {title}" in `accent`, "Not started", "All watched" or "Empty";
    - a chevron.
  - Empty state: "No lists yet" + "Make one with + above, or hold a movie, series or episode and choose Add to list…".
  - Pull to refresh.
- **ListScreen:**
  - title = the list name;
  - a "…" menu: "Play from the beginning", "Rename", "Delete list" (with the confirmation "Delete this list?" / "The movies and episodes stay in the library.");
  - a header: "N titles · M watched · 3 h 10 min to go", then an AccentButton:
    - "Continue: {title}" when the list is started;
    - "Play" when it isn't;
    - "Play from the beginning" when everything is watched.
  - It plays a queue that carries on item after item.
  - **Rows:**
    - the number (22 wide, `accent` and bold for the next item);
    - poster 44×66 (opacity 0.55 when watched);
    - the title (an episode shows "Series · S1 · E2");
    - an info line: "Movie · 2014 · 1 h 2 min left" or "Episode name · watched", with a check mark in `accent` when watched;
    - a progress bar on the next item;
    - a drag handle.
  - Drag reorders, swipe deletes, tap plays from that item.
- **AddToListSheet:**
  - "Add to list" / "{title} goes at the bottom";
  - one CheckedSheetRow per list: "N titles · already in it, as 3";
  - a "New list…" row (accented).

---

## 10. Jellyfin API calls used by these screens
All calls are made with `userId` = the signed-in user id.

| Purpose | Endpoint |
|---|---|
| Full library sync (one call each for `Movie`, `Series`, `Episode`, run in parallel, 60 s timeout) | `GET /Items?userId&recursive=true&includeItemTypes={Movie\|Series\|Episode}&fields=Overview,DateCreated,Genres,ProviderIds&enableUserData=true&enableImageTypes=Primary,Backdrop,Thumb,Logo&imageTypeLimit=1&sortBy=SortName` |
| Server check, discovery check | `GET /System/Info/Public` (5 s for the reachability check) |
| Quick Connect | `POST /QuickConnect/Initiate`; `GET /QuickConnect/Connect?secret=`; `POST /Users/AuthenticateWithQuickConnect` |
| Sign out | `POST /Sessions/Logout` |
| User language prefs | `GET /Users/Me` |
| Mark watched / not watched (works on a series id too) | `POST` / `DELETE /UserPlayedItems/{id}?userId` |
| Tech badges, download sizes | `GET /Items/{id}?userId&fields=MediaSources` |
| Credits | `GET /Items/{id}?userId&fields=People` (Type ∈ Actor, GuestStar, Director, Writer, Producer) |
| Related ranking | `GET /Items/{id}?userId&fields=People,Studios` |
| Person facts (one batch) | `GET /Items?userId&ids=a,b,c&fields=Overview,ProductionLocations,PremiereDate,ProviderIds` |
| Person external link fallback | `GET /Items/{personId}?userId&fields=ProviderIds` |
| Similar movies | `GET /Items/{id}/Similar?userId&limit=20&fields=People,Studios` |
| People search | `GET /Persons?userId&searchTerm=&limit=10&enableImageTypes=Primary` |
| Person screen | `GET /Items?userId&recursive=true&personIds={id}&includeItemTypes=Movie,Series` |
| Lists: read | `GET /Items?includeItemTypes=Playlist&recursive=true&sortBy=SortName` (MediaType Audio is filtered out); `GET /Playlists/{id}/Items?enableImages=false&enableUserData=false` |
| Lists: create | `POST /Playlists {Name, UserId, MediaType:"Video", Ids}` |
| Lists: add, remove | `POST` / `DELETE /Playlists/{id}/Items?ids=…` (`entryIds=` for delete) |
| Lists: move | `POST /Playlists/{id}/Items/{entryId}/Move/{index}` |
| Lists: rename | `POST /Playlists/{id} {Name}` |
| Lists: delete | `DELETE /Items/{id}` |
| Images | `GET /Items/{id}/Images/{type}?tag&fillWidth&quality=85` |

### Fields a typical client may not request
- `ProviderIds`: `Imdb`, `Tmdb`, and **`TmdbCollection`**, which drives the "same collection" grouping.
- `CriticRating`: the Rotten Tomatoes score.
- `CommunityRating`, `OfficialRating`, `Status`.
- `DateCreated`: drives New and the "Newest" sort.
- `UserData`: `LastPlayedDate`, `PlaybackPositionTicks`, `Played`, `UnplayedItemCount`.
- Episode fields: `SeriesPrimaryImageTag`, `ParentBackdropItemId`/`ParentBackdropImageTags`, `ParentIndexNumber`/`IndexNumber`.
- `ImageTags["Logo"]`.
- On person items: `PremiereDate` = birth date, `ProductionLocations[0]` = birthplace, `Overview` = biography.

### Client-side logic to port
Everything on Home, the Library sections, Continue watching, series progress and "next", and related-movie ranking is computed in the app from the cached snapshot; none of it comes from the server's Resume or NextUp endpoints. The snapshot is cached on disk (`LibraryCache`) and shown before the sync finishes, and watch positions recorded offline (`OfflineProgress`) are applied over it.

## 11. Dutch strings
All en→nl pairs for strings on these screens are given inline above. They come from `Resources/Localizable.xcstrings` (source language en, 339 keys). These keys use plural variants:
- "%lld seasons" → "%lld seizoen" / "%lld seizoenen"
- "%lld titles" → "titel" / "titels"
- "Download %lld episodes" → "aflevering" / "afleveringen"
- "%lld movies", "%lld episodes", "%lld not watched"

Some other pairs worth noting:

| English | Dutch |
|---|---|
| Cast | Acteurs |
| Related | Verwant |
| Directed by | Regie |
| Written by | Scenario |
| Produced by | Productie |
| Makers | Makers |
| Guest stars | Gastrollen |
| All %lld actors | Alle %lld acteurs |
| Not started | Nog niet begonnen |
| Up next | Volgende |
| Continue | Verder |
| Play | Afspelen |
| Special / Specials | Special / Specials |
| Season %lld | Seizoen %lld |
| Watched | Gezien |
| %lld years | %lld jaar |
| %@ s (decade) | jaren %@ |
| type.series | Serie (en "Series") |
| Playing now | Speelt nu |
| Rename | Naam wijzigen |

Genre names are in `Resources/Genres.xcstrings`, listed in §3.
