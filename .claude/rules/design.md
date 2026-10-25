# Design guidelines

What the design (in Paper) and its implementation may use on the Chromecast, and what not. Elaboration of `product-principles.md` (that file comes first); the technique for focus, safe area and memory is in `tv-ux.md`. Sources: `[J*]` in `docs/research/best-practices-sources.md`. Terms: glossary in `code-language.md`.

**Split up (2026-10-12)** so every file stays within 100 lines. A reference "design.md → <heading>" is found in:

- this file: What the hardware can do · Effects · Images (the ladder) · Loading and skeleton · Layout · Focus
- `design-style.md`: Color and typography · Visual style (tokens) · Sign-in, brand, banner and splash · What to deliver per screen
- `design-screens.md`: Screen layouts: Home bar, Groups, Series, Movies (detail view, bookcase, Spine color), Library, Home section, Movie detail, Series page
- `design-navigation.md`: Navigation with the remote · Transitions · Player overlay (and Jump pill)

## What the hardware can do [J1] [J2]

- GPU: **Mali-G31 MP2** (smallest Bifrost GPU), 4× Cortex-A55. UI at 1920×1080 at **59.94 Hz: 16.7 ms per frame** (measured 2026-10-08).
- Rough upper bound: about 10× the full screen in pixels per frame, before blending and bandwidth. Every translucent full-screen layer takes a big share of that. Hence: **the number of full-screen layers counts, not the number of cards.**
- While scrolling the whole screen redraws every frame. A static gradient or backdrop therefore also costs during scrolling through the rows.

## Effects: cheap, limited, forbidden [J3] [J4] [J5]

| Effect | Verdict | Why / how |
|---|---|---|
| Scale on focus (`graphicsLayer` scale) | **Cheap** | No offscreen buffer, no redraw. Standard focus means. |
| Focus border | **Cheap** | Selected: 2 dp white with a hint of blue and purple (sweep gradient). |
| Neon border: comet and weld spark | **Limited** | Only on the focused element, only after 150 ms of rest (a fixed gradient border during key repeat). One Modifier.Node draws everything (`neonFocus`): one path segment with a rotating sweep gradient, three small radial gradients for the weld spark. No recomposition, no layout. |
| Focus halo on cards | **Limited** | Only on the focused card, only after 150 ms of rest (a fixed gradient border during key repeat), a frame every vsync while focus rests. In the same `neonFocus` node: one AGSL shader pass (`HaloGlow`) behind the content that fades from 50% at the outline to nothing at 12 dp, per pixel and dithered (stacked strokes showed bands), with the two rotating lamps: no blur, no offscreen buffer. Stays within 15 dp of the card (the LazyRow clip) and under half the card gap. |
| Flat areas, rounded corners (round-rect) | **Cheap** | Round-rect clip is the fast path; no free paths (path clip). |
| Alpha pulse on skeleton | **Cheap**, provided | Lambda `graphicsLayer` with `CompositingStrategy.ModulateAlpha`, or animate the color in `drawBehind`. Default alpha < 1 creates an offscreen buffer. |
| Progress bar on card | **Cheap** | Rectangle in `drawBehind`. Not via the server (`percentPlayed` breaks the image cache). |
| Static gradient/scrim | **Limited** | At most **one** full-screen scrim, preferably only over the text area. Create the `Brush` once (`drawWithCache`). |
| Crossfade | **Limited** | Draws old and new at the same time. Poster: ≤ 100 ms (the placeholder is flat, so cheap). Backdrop: ≤ 200 ms; if it stutters, switch directly. |
| Screen transition | **Limited** | Fade ≤ 250 ms or none. No slide with two full screens. |
| Translucent panels over the picture | **Limited** | Only for the player overlay and menus; an opaque panel if possible. |
| Blur on the device (`Modifier.blur`, `RenderEffect`) | **Forbidden** | Offscreen buffer plus an extra GPU pass per frame. For a blurred background, ask the server (`blur` parameter). |
| Glow (`Glow`, `setShadowLayer`) | **Forbidden** | Blurred shadow. The tv-material default is already `Glow.None`; leave it. Imitating glow is allowed: two wider, translucent lines (6% and 13%) around the trail. |
| Shadow/elevation per card | **Forbidden** | Many shadows in a row; adds nothing to browsing. |
| Shimmer | **Forbidden** | Redraws a gradient over every skeleton block, every frame. |
| Layout animations (`animateContentSize`, expanding cards) | **Forbidden** | Relayout per frame; shifts neighbours while scrolling. |
| Autoplay trailers, self-running carousel, parallax | **Forbidden** | See `product-principles.md`. |

## Images: the ladder [J6] [J7] [J8]

Request every image at the smallest size that looks good. Lower than the screen resolution is fine where it doesn't show under a scrim or from a distance.

| Use | Display (dp) | Request (px) | Memory (ARGB) |
|---|---|---|---|
| Poster in row/grid (2:3) | 120×180 | `fillWidth=240` | ~0.35 MB |
| Landscape card: continue watching, episode (16:9) | 196×110 | `fillWidth=392` | ~0.35 MB |
| Backdrop behind Home hero (under scrim) | 960×540 | `fillWidth=1280` | ~3.7 MB (1920 would be 8.3 MB) |
| Backdrop on detail page | 960×540 | `fillWidth=1280`; 1920 only if 1280 is visibly soft | ~3.7 MB |
| Logo (title image) | max. 400×120 | `fillWidth=800`, PNG/WebP with transparency | small |
| Season poster | 120×180 | as poster | ~0.35 MB |
| Logo on movie spine (rotated) | 30×236 | `fillWidth=480`, PNG | ~0.2 MB |
| Poster at the top of the spine | 44×78 | `fillWidth=88` | ~0.03 MB |
| Open movie panel | 500×464 | `fillHeight=928` | ~3.5 MB (one at a time) |

- Every URL: `tag` (safe caching), `fillWidth`/`fillHeight` at display size, **`quality=85`** (the server otherwise uses 100), and `Accept: image/webp` on the Coil requests so the server delivers WebP.
- Placeholder: flat block in a `surfaceVariant` tint. No BlurHash, no icon. When the real image replaces it, with a fade of ≤ 100 ms.
- Missing image: the same flat block with the title in `titleSmall` in it. Never a broken-image icon.
- Posters in rows always load (small, from the disk cache). What loads only after ~300 ms of rest: backdrop, detail data, logo.
- Backdrops vary (chosen 2026-10-21): the server keeps up to 5 per item. Leaving a section via the bar moves its items to their next backdrop, already while hidden, so nothing swaps on screen when it returns. Opening a screen and Back keep the same backdrop (bookcase, panel, movie detail, player).
- Never compose images in the app (no custom collage, vignette or overlay text on the bitmap).

## Loading and skeleton [J12]

- Navigate in the frame of the click. What's already known (title, year, poster from the list) is there immediately.
- Skeleton only after **100 ms**; if the data comes sooner, show content directly without a flash.
- Skeleton blocks have exactly the sizes of the real content: posters, title line (one bar at title height), 3 text lines as bars of 100/95/60%, buttons in place. Nothing shifts when the data arrives.
- Animation: **wave** (chosen 2026-10-08). Each skeleton block pulses in opacity between 0.5 and 0.85 (1 s there and back), with a 120 ms delay per block from left to right, so a wave runs across the screen. Only alpha via the lambda `graphicsLayer`; no shimmer. Runs only while the skeleton is visible.
- The primary button (Play) is already in place in the skeleton and has focus; it is dimmed until the data is there.
- Error: a message in place of the content with a "Try again" button. Never an endless skeleton; after 10 s without data it is an error.

## Layout [J9] [J10]

- Design in Paper on artboards of **960×540** (1 px = 1 dp). Then all sizes carry over directly.
- Safe area: 48 dp left/right, 27 dp top/bottom (tokens in `core/designsystem/theme`). Backdrops run to the edge.
- Space between cards: 20 dp (≥ 16 dp, so a scaled-up card doesn't touch its neighbours). Space between rows: 24–32 dp.
- Card ratios: 2:3 (poster), 16:9 (episode, continue watching). No others.
- Little at a time: Home shows one hero area and 1–2 rows above the fold. At most ±5 rows in total, ±20 items per row.
- No title under posters in rows: the poster already carries the title, and the title/info of the focused item is in the hero area. Saves text layout per item and a scaled text layer. Exception: the Library grid (see `design-screens.md` → Screen layouts).
- Per card at most one badge (watched check or unwatched count) plus the progress bar.

## Focus

- Poster and landscape card (`FocusCard`: rows, Continue watching, grid, episodes): scale **1.05** and after 150 ms of rest the **focus halo** instead of a circling weld spark (2026-10-21; replaces the focus burst of 2026-10-11). Pill buttons (`PillButton`): scale 1.05 and the same focus halo (2026-10-21). Small chips, menus and the player: scale 1.05–1.1 and the neon border with weld spark (see below). No blurred glow, no color change as the only signal.
- **Focus halo** (chosen 2026-10-21, replaces the focus burst: its wave and thick line were too much): as if a lamp shines behind the card. Two sweep gradients, each with two colored lights (blue and pink; purple and white) and dark gaps, turn in opposite directions (7 s and 11 s per turn), so the light keeps shining from another place in another color. It fades in over 400 ms while the fixed neon border fades out; the border becomes a thin 1.5 dp line in the same moving colors.
- **Section bar** (chosen 2026-10-10): no frame around the focused tab. The line under the tab turns neon (light blue → white → purple) with a soft glow of two translucent lines, plus scale 1.1. Selected but not focused: white line. The settings icon gets the neon border.
- **Neon border** (chosen 2026-10-10, replaces the amber border): no border without focus. On a new item the whole border lights up immediately in the gradient (approved 2026-10-10, keep it). When the focus rests, a light trail runs around the border (light blue → white → purple → pink, 45% of the perimeter, 5.5 s per lap) over a white border with a hint of blue and purple. The head is a **weld spark**: a small white-hot dot, stretched in the direction of travel, with a purple light patch along the line and a light-blue one across it, flickering slightly (every 30–80 ms). The weld spark goes **on and off** at a random rhythm: 2.5–4 s visible (trail and spark), then 1–2.5 s fully gone (shortened 2026-10-10), with a 0.35 s fade; the white selection border stays (chosen 2026-10-10, so you don't see it circling all the time). While it's gone no frames run. Behind the head a **52 dp tail** cools from white-hot via lavender to the trail. No ball, no round halo.
  - No sparks anywhere, not even when seeking (removed 2026-10-10: too busy).
  - The comet starts only when the focus has rested 150 ms; while holding a key, a fixed gradient border.
  - The reference for the motion is the demo from the design conversation (2026-10-10); Paper ("Neon — …") shows only color and structure in one still moment.
- Focus animation ≤ 150 ms (tv-material does the scale itself).
- The focused card stays in a fixed place in the row (about 30% from the left) via one `BringIntoViewSpec`; the row moves, the focus doesn't jump.
- Design the focus state of every element in Paper, not just the resting state.
