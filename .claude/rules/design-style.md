# Design: color, style and brand

Continuation of `design.md` (that file comes first for effects and hardware limits). Tokens live in code in `core/designsystem/theme`. Paper artboard names are quoted as they are in Paper (Dutch).

## Color and typography [J9] [J11]

- Dark theme only. Background near-black (no pure `#000`, no large pure-white area); text off-white; no filled accent color: focus is the neon border, progress the calm progress gradient.
- Contrast text/background ≥ 4.5:1, also over a backdrop (the scrim takes care of that).
- Only the tv-material type scale. One font family with 2–3 weights. The system font (Roboto) costs nothing extra; a bundled font costs only APK size and one load, no work per frame. No downloadable fonts (they load asynchronously, so the first measurement is off).
- Text never smaller than `bodySmall`; descriptions on overviews at most 3 lines.

## Visual style (approved 2026-10-08)

Design in Paper: file "jelzefin" (https://app.paper.design/file/01M4EKYVQ2AVZYWF1VAX97EAM0). Mood: projection booth, black metal with one warm lamp.

| Token | Value | Use |
|---|---|---|
| `background` | `#0E0E0F` | background |
| `surface` | `#1A1A1C` | skeleton, placeholders |
| `rail` | `#2A2A2E` | lines, progress track, inactive button |
| `text` | `#ECE9E4` | primary text |
| `textMuted` | `#A3A09A` | secondary text |
| `neonBlue` · `neonWhite` · `neonPurple` · `neonPink` | `#7FC8FF` · `#F2F6FF` · `#9B6BFF` · `#FF6EC7` | only the neon border (trail, weld spark) |
| `progressStart` · `progressMid` · `progressEnd` | `#8FB8D8` · `#9A88D0` (70%) · `#C584B0` | progress: same order, less saturated and calmer than the border |
| `accent` | `#B9A6F2` | small flat accent: check mark, countdown arc, waiting dot |
| `primaryContainer` | `#16161B` | primary button surface (dark pill, 1 dp purple at 45% at rest) |

Neon chosen 2026-10-10 instead of amber (`#F2A93B`). Paper: the artboards "Neon — …".

- Font: Roboto (system font), 700 for titles, 400–500 for labels; sizes 36/24/16/14/12.
- Cards: radius 8 dp; focus = scale 1.05 + the neon border. Buttons: pill shape, 40 dp high.
- Backdrop on Home and the series page: 720×405 dp top right, with one radial gradient from the top right to `background`, plus a soft fade to `background` in the bottom 40% (otherwise a hard bottom edge). Image and gradients are rendered offscreen once.
- Backdrop on movie detail and the Home section: full screen, with one horizontal gradient (`background` → 94% at 42% → 60% at 64% → 20% on the right). The text column is at most 400–440 dp wide and falls within the dark part.
- Watched episode: check mark top right on a light circle + dimmed still.
- Skeleton button: same shape and width as the real button, dimmed purple (`#2E2840`) with light text.

## Sign-in, brand, banner and splash (chosen 2026-10-08, brand 2026-10-11)

- **Brand: the Cast triangle** (chosen 2026-10-11, idea B from Paper "Logo-ideeën — neon outline (JellyCast)"; replaces the amber JC tile). The Jellyfin triangle upright as one neon line (7 on 200, the neon gradient from light blue via white and purple to pink), with inside a dot and two arcs (the "Cast") instead of the small triangle. On the right edge the weld spark with the tail, like the focus border. Glow only as two wider translucent lines (6% and 13%). Drawn in code (`core/designsystem/component/BrandMark.kt`), not as a PNG.
- **Wordmark:** "Jelly" in Roboto Bold, off-white, directly followed by "Cast" in Roboto Light, lavender (`accent`); `BrandWordmark`.
- **Icon:** Paper artboard "Icoon — B (512)". Launcher icon: adaptive, background `background`, foreground the brand as a vector (`drawable/ic_launcher_foreground.xml`) within the 66 dp safe zone.
- **Banner** (320×180, xhdpi): dark surface with the brand on the left and the wordmark on the right. Paper artboard "Banner — B, 320×180".
- **Splash** (chosen 2026-10-11, Paper "Splash — JellyCast (B)"): at startup the brand (132 dp) with the wordmark below it, centered on `background`. The line draws itself in 600 ms, with the weld spark as its head, only once frames run regularly (the app's first frame takes ~0.4 s); the wordmark fades in; after 250 ms of rest the splash fades out in 200 ms. The start screen builds only once the line is drawn (under the pause and the fade): doing both at once made the line stutter. Home may then still briefly show its skeleton. Any key removes the splash immediately. A plain Compose screen, not the SplashScreen API (tv-ux.md). Once per start, not after a language change. Possibly later also when starting a video.
- **Sign-in (first time):** dark, cinematic. On the left the brand (148 dp), below it the wordmark "JellyCast" (back since 2026-10-11) and the tagline "Your cinema, on this TV.", centered under each other, with a soft purple glow (a hint of light blue) behind it (neon since 2026-10-11; Paper "Inlogachtergrond — neon"). On the right "Sign in with your phone": the Quick Connect code in 6 boxes (3 + 3), explanation in 2 lines, status "Waiting for approval" with a pulsing amber dot (wave animation). Below that "or" and the button "Sign in with password" (initial focus). Bottom left the server with a green dot and the button "Other server" (focusable, ↓ from the password button).
  - The background (glow + beam) is **one pre-rendered image** in the resources (WebP, 960×540 lossless; see NOTES.md), no full-screen gradients.
  - **Password page** (built 2026-10-09, not yet separately in Paper): same structure; on the right "SIGN IN WITH PASSWORD", fields Username (initial focus) and Password, button "Sign in", "or" and "Sign in with code". Back = to the code page.
  - **Server screen** (revised 2026-10-11): same structure without the server line; on the right "CONNECT TO YOUR SERVER", one status line and below it the servers the network finds as **cards** (360 × 64 dp, `FocusCard`; max. 3): name, address and Jellyfin version (one `PublicSystemInfo` request per server; "too old" or "not responding" when so), on the right "Current" for the server you're connected to now. The first card has focus and is always the same element: while searching (~2 s) "Searching…", then the first server, or with no result "Enter address" (with the text "No Jellyfin server found…" above it). So the focus stays put and connecting is usually one OK. Below that "or" and the button "Enter address" (with no result: "Search again"). **Enter address** shows an explanation, the field Server address (focus when empty) and "Connect" (focus when an address is already there), with "Servers on your network" below; Back also returns to the cards. A 2nd or 3rd server shifts the column a little (rare; no fixed space for empty slots).
  - **Settings** (revised 2026-10-11): same structure; on the right three groups with a heading in small caps: **Display** (Language, initial focus; Movie view), **Playback** (Quality, Burn in ASS, Advanced playback) and **Account** ("han on home server", Other server, Sign out at the very bottom). No "Back to Home" button (Back does that) and no playback test. Bottom left the explanation of the focused button and the version line "JellyCast 0.1.x (n)". What the device itself can do (decoders, HDR, passthrough) is shown bottom left on Advanced playback.
  - **Advanced playback** (2026-10-10, not yet in Paper; functional screen): same structure, heading "PLAYBACK", one column of buttons that scrolls with the focus: Frame rate (always/system/off, initial focus), HDR10, HDR10+, Dolby Vision, Passthrough AC3/EAC3/DTS/TrueHD (each "auto (yes/no)"/on/off; OK = next) and Stereo only. Explanation of the focused button bottom left. Back = Settings, focus on Advanced playback. The explanation of the focused setting is bottom left above the version line (moved 2026-10-10: with Language added and longer translations it no longer fit in the column).
  - **Language** (chosen 2026-10-10): OK cycles through the languages the app has (English first, then A–Z by own name: Español, Français, Nederlands). Each language is shown in its own language. Default English (chosen 2026-10-10, previously Dutch), also when the TV is set to another language. After a change the screen rebuilds in the new language, with the focus on the language button. Titles and descriptions come from the server and stay in its metadata language.
  - Buttons in the right column are centered in that column (360 dp); headings, text and fields align left.
  - Buttons here: dark surface at rest, on focus off-white with dark text and scale 1.05 (like "Knop Wachtwoord — gefocust").

## What to deliver per screen

For Home, the series grid, movie and series detail and the player overlay:

1. The filled state with one focused element.
2. The skeleton state (same structure, same sizes).
3. The error state.
4. A note for every effect that falls under "limited" in the effects table of `design.md`.
