# TV UX and performance

D-pad and focus, 10-foot layout, manifest requirements for Google TV, and careful use of the Chromecast's 2 GB RAM. Player controls are in `playback.md`.

## Manifest and launcher [C1] [C2]

- `<uses-feature android:name="android.software.leanback" android:required="true"/>` and `android.hardware.touchscreen` `required="false"`.
- `MainActivity` has an intent filter with `MAIN` + `LEANBACK_LAUNCHER`. Without it the app doesn't appear on the Google TV home screen, not even when sideloaded.
- `android:banner` on `<application>`: `drawable-xhdpi/banner.png`, 320×180 px, app name in the image.
- Activity theme: opaque background (TV-TR), no custom SplashScreen animation (not supported on TV).
- `network_security_config.xml` allows cleartext. Limit that to the LAN where possible, and never set it globally via `usesCleartextTraffic` without a config.

## Focus and D-pad [C2] [C9] [C10]

- Everything clickable is a tv-material component (`Card`, `Button`, `Surface` with `onClick`) or has `Modifier.focusable()` plus a visible focus style. Never `Modifier.clickable` without a focus indication.
- Focus style: scale (about 1.05–1.1) and the neon border (`core/designsystem/component/NeonFocus.kt`, `Modifier.neonFocus`; see `design.md` → Focus). No blurred glow (see `product-principles.md`). Color alone is too weak at 3 meters. Buttons: `neonFocus` before the tv-material `Surface`, with the `Surface`'s own scale and border off. Cards in rows and grids: `core/designsystem/component/FocusCard` (no tv-material `Surface`: that cost ~1.7 ms of composition per card, and ↓ adds a whole row per key press; measured 2026-10-09).
- Every screen sets initial focus: a `FocusRequester` on the main element plus `LaunchedEffect(Unit) { requestFocus() }`, only after the data has loaded.
- A row entered with ↑/↓ gives focus to item 1 and scrolls to the start (`focusProperties { onEnter = { … } }` with a `FocusRequester` on item 1, plus `scrollToItem(0)`). No `focusRestorer` on rows: a row doesn't remember its last item, so the number of presses stays countable (see `design-navigation.md` → Navigation with the remote).
- Navigating back to a screen restores focus on the previously chosen item: keep `lastFocusedItemId` with `rememberSaveable` (or in the ViewModel), scroll to that item and request focus there. `focusRestorer` alone doesn't cover this, because the screen is recomposed.
- A `FocusRequester` must be attached to a composed element before you call `requestFocus()`. In lazy lists: `scrollToItem` first, then focus.
- Use `focusProperties { up = …; down = … }` only when the default 2D search demonstrably jumps wrong. Make the reason clear through naming (e.g. a named requester or helper); add a one-line why-comment only if it is truly non-obvious.
- Back: within a screen, Back first closes an open menu or overlay. On Home (the overviews) Back then goes straight to the section bar; the section silently resets to its starting point (`onBackToBar`, chosen 2026-10-10). On other screens Back first brings the focus to the starting point (scroll to top), and only from there to the previous screen. On the section bar Back first goes to the Home tab; there Back shows the hint "Press Back again to exit" and a second Back within 2 s closes the app (chosen 2026-10-12, TV-DB). Never consume Back without a visible effect.
- No function reachable only via a Menu button or long press (TV-DM). Exception (chosen 2026-10-09): holding OK opens an options menu, provided a visible hint is always shown ("[OK] hold for more"). A short OK acts on release, so a long press doesn't first start the short action.
- Text input always via `core/designsystem/component/TvTextField`, never a bare `BasicTextField`/`TextField`. Why: on TV, Compose opens the keyboard on focus, and ↓ then goes to the keyboard instead of the next button. `TvTextField` is read-only until OK, and ↑/↓ always move the focus.
- Rows use foundation `LazyRow`/`LazyColumn`/`LazyVerticalGrid`. Never `TvLazyRow` etc. from `tv-foundation` (removed). [C4] [C5]
- To keep the focused card in a fixed place (e.g. 30% from the left), use a `BringIntoViewSpec` via `LocalBringIntoViewSpec`, in one place in `core/designsystem`. [C4]

## Layout and typography (10-foot) [C6] [C7]

- Design on the TV canvas of 960×540 dp.
- Safe area: text and controls stay 48 dp from left/right and 27 dp from top/bottom (5% overscan). Backgrounds and backdrops do run to the edge. Define these values once as tokens in `core/designsystem/theme`.
- Use only `MaterialTheme.typography` from tv-material, no loose `sp` values. Display styles only for the screen title or hero title; Title for cards; Body for descriptions.
- Descriptions: at most 3–4 lines with ellipsis on overview screens; the full text only on the detail page.
- Leave enough space between cards (≥ 16 dp) so a scaled-up focused card doesn't overlap its neighbours.
- Dark theme as the only theme. No pure white-on-black areas over large surfaces.

## Performance on 2 GB RAM [C2] [C11]

- Check once whether the device counts as low-RAM (`adb shell getprop ro.config.low_ram`) and record the result in `NOTES.md`. In code: `ActivityManager.isLowRamDevice()`, never measure RAM yourself.
- Images: request exactly the display size in pixels via the image API. Never load original images.
- Image URLs: build them in one place (`core/network/ImageUrlBuilder`) with `imageApi.getItemImageUrl(itemId, imageType, tag, fillWidth/fillHeight or maxWidth, quality)`. Always pass the `tag`: it changes when the image changes, so the URL is safe to cache. [G5]
- Poster in a row: request the width the card takes in px (dp × density), no larger. Backdrop: at most the UI screen width (1920 px), not 4K.
- Coil: one `ImageLoader` singleton, memory cache bounded (around 15–20% of app memory), disk cache on, hardware bitmaps on (Coil default). Placeholders are a flat color, not an image.
- Don't compose images in the app that the server can already deliver.
- `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` or higher: clear the Coil memory cache.
- Never call `System.gc()`.
- Don't hold `Bitmap`s or `Context`s in singletons or ViewModels.
- Measure memory with `adb shell dumpsys meminfo nl.jellycast.tv` after a few minutes of scrolling on Home, and record peak values in `NOTES.md`.

## Testing per screen

- Check every new or changed screen on the device: D-pad in all directions, Back, and returning with focus on the right item. Use `adb shell input keyevent KEYCODE_DPAD_*` and `adb exec-out screencap -p`.
