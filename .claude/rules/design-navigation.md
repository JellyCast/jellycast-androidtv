# Design: navigation, transitions and player overlay

Continuation of `design.md`. Focus technique (FocusRequester, `focusProperties`) is in `tv-ux.md`; playback itself in `playback.md`.

## Navigation with the remote (chosen 2026-10-08)

You control everything with ↑ ↓ ← →, OK and Back. The goal: you know without looking how many presses something takes. "Season 5, episode 3" = 5× ↓ and 2× →.

- **Countable grid.** Every screen is rows stacked vertically with items side by side. A position is always "n× ↓, m× →" from the screen's starting point.
- **Entering a row with ↑/↓ = always item 1**, and the row is back at its start. So a row doesn't remember where you were last time. Why: otherwise the number of presses depends on what you did before, and you can no longer count.
- **Exception: Back.** When you come back from a detail screen or the player with Back, the focus is exactly on the item you came from, at the same scroll position.
- **Grid** (Library): fixed 8 columns; ↑/↓ keeps the column. Here too you count: row × column.
- **No wrap-around.** → on the last item and ← on the first item do nothing (no jump to another element or to the start).
- **Starting point per screen:** Home section = Play on suggestion 1; Series and Movies = item 1 of the first row; series page = Continue button; movie detail = Play/Resume.
- **Home:** ↑ from the first row (or from Play in the Home section) goes to the section bar. On the section bar ←/→ switches section directly (on focus, without OK); ↓ goes to item 1 of the first row.
- **Back on Home (overviews):** in the Home, Series, Movies and Library sections Back always goes straight to the section bar (chosen 2026-10-10; going to the starting point first was annoying). The section silently returns to its starting point, so ↓ from the bar lands on item 1 again. On the section bar Back first goes to the Home tab; there Back shows the hint "Press Back again to exit" and a second Back within 2 s closes the app (chosen 2026-10-12). An open panel or menu is closed by Back first.
- **Back on other screens (two steps):** if you're not on the starting point, Back takes you there (and scrolls to the top). If you're on the starting point, Back goes to the previous screen.
- **Everything that can get focus is visible**; nothing is reachable only via the menu button. A long press on OK may open an options menu, only with a visible hint (see `tv-ux.md`).
- **Holding a key** moves the focus with the key repeat and scrolls the row; no backdrops or detail data are loaded along the way (see `design.md` → Loading and skeleton).

## Transitions (chosen 2026-10-08)

There are only a few transitions, and they animate only scale, translation and opacity via `graphicsLayer`: never width/height (no relayout per frame). Storyboard in Paper: "Overgang — kast → filmdetail → afspelen".

- **Bookcase → movie detail (250 ms, ease-out):** OK on the open spine. The panel with the backdrop scales and moves to the full screen; the spines left and right move 40 dp outwards and fade to 0 (150 ms). Then the movie detail stands with the backdrop in exactly the same place; the buttons fade in (100 ms) and Resume/Play has focus.
  - Precondition: panel and movie detail use **the same backdrop URL** (same `fillWidth`), so the detail draws from the cache and nothing flashes.
  - Everything the detail shows at the top (logo, metadata, description) is already in the bookcase item; no skeleton needed. The tech line may come in later.
  - Back: exactly reversed; the panel shrinks back into the bookcase, focus on the same spine.
- **Movie detail → playback (150 ms):** OK on Resume/Play fades the UI to black and starts the player. Back from the player: black → detail (150 ms).
  - Note: the video is on a `SurfaceView`, which ignores opacity. A fade of the player screen itself leaves the video surface over the UI (seen 2026-10-09). Navigation 3 therefore switches to and from the player directly; a fade to black belongs in the screen before it.
- **Other screen changes** (Home ↔ series page, sections): short crossfade ≤ 200 ms or direct.
- **Keys during a transition:** the transition continues and the key is executed directly afterwards (in order, nothing lost). Back during bookcase → detail reverses the animation.
- **Implementation:** within the Movies screen (panel as overlay) or with Navigation 3 + `SharedTransitionLayout`/`sharedElement` if a measurement on the Chromecast (release) shows it drops no frames. Measure with `dumpsys gfxinfo` during 20× open/close.
  - Status 2026-10-09 (phase 4): built as a short crossfade (200 ms) between bookcase and movie detail. The detail stands in the frame of the click with everything from the bookcase; the tech line comes in within ~0.3 s. The scale/move transition isn't built yet: first meet the bookcase scroll budget.

## Player overlay (chosen 2026-10-09)

Simple and minimal: thin type (Roboto Light 300), outline icons (stroke 1.4 on 24), no buttons with surfaces. Paper: "Player — overlay", "Player — menu Ondertitels", "Player — volgende aflevering".

- **Appearing:** any key shows the overlay (direct, fade-in ≤ 150 ms); after 4 s without input during playback it fades away. When paused it stays. Back first hides the overlay, only then stops.
- **Structure (at the bottom, within the safe area):** title (Light 28) and "S3 · E4  Violet" (Light 14, 70%); a 2 dp time bar over the full width (track 22% white, progress in the progress gradient); times below it (Light 13: elapsed left, remaining "−18:00" right); below that one row of icons: left 10 s back · play/pause · 10 s forward, right audio · subtitles · info · next episode (series only).
- **Info** (chosen 2026-10-10): OK shows or hides the playback info top left (direct play/transcode and reason, video codec, decoder, audio output, track, dropped frames); the focus stays on the button, the overlay stays while the panel is open, and Back closes the panel first. Release builds only: debug builds always show the playback info and have no button.
- **One gradient** at the bottom (black 90% → 0 over 320 dp). No blur, no surfaces behind the icons.
- **Focus:** starts on the time bar. The head of the time bar is a small weld spark with the same 52 dp tail, still at rest. When seeking it briefly grows and flickers (no sparks). ←/→ seeks 10 s (key repeat accelerates to 30 s steps after 1 s), OK = pause/play, except within 3 s of a seek step: then OK confirms the jump and plays (chosen 2026-10-25; Netflix and the official Jellyfin app pause while seeking, so "seek, OK" kept pausing). ↓ to the icon row. On an icon: the neon border, 40 dp around.
- **Menus (audio, subtitles):** a 320 dp panel on the right, `background` at 94% (no blur). Heading in small caps (12, 55%), options Light 17 at 44 dp high, chosen option with a lavender check mark, focused option with the neon border. Under an option one line of tech is allowed (Light 12, 50%), e.g. "SDH · SRT" or "EAC3 5.1 · passthrough". Subtitles have "Style" below: size and background. Back closes the panel and returns the focus to the icon.
- **Jump pill** (chosen 2026-10-12, Paper: "Sprong — …"; replaces the Next card and the separate skip pill): one shape for every jump within the episode. Pill 44 dp high, flat `background` 82%, text Light 17 with the jump arrow (▷|); focus = the neon border, at rest a 1.5 dp border of white 32%. On the right of the picture: without overlay bottom right in the safe area, with overlay on the right above the time bar (132 dp from the bottom edge).
  - Skip: "Skip intro", "Skip recap", "Skip ad", "Skip preview", while that part runs (Media Segments from the server). OK jumps to the end of the part.
  - Next episode (series only): at the start of the credits (outro segment), otherwise the last 30 s. Two lines ("NEXT EPISODE" small, "S1 · E2  Title"), 66 dp high, with a 2 dp countdown line in the progress gradient that fills in 10 s; then it starts by itself. The countdown pauses while the overlay is shown. No still and no duration any more. Takes precedence over a skip pill when they coincide.
  - Always visible when there is something to jump. Without overlay the pill has the focus (OK = jump, another key shows the overlay). With overlay the focus is on the time bar; ↑ from the time bar gives the pill focus. Back: first the overlay away, then the pill away for this part.
