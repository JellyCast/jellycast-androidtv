# Product principles

Why JellyCast exists and what every choice is tested against. This file comes before the other rules: if a rule or idea conflicts with it, this file wins.

## Why this app

The user has Plex, the official Jellyfin app and Wholphin on the Chromecast. They all just don't quite work:

- **Plex:** hangs, is slow, misses input or processes it in the wrong order, and the focus jumps back and forth.
- **Jellyfin (official):** works, but isn't pretty.
- **Wholphin:** pretty, but too heavy for this device.

JellyCast has to fill the gap: **simple, reliable and smooth on a Chromecast with 2 GB RAM, and within that as pretty as it can be.**

## Core

1. A simple overview of the library.
2. Play every movie, series and episode, preferably with direct play.
3. Never stutter, never lose input, never a jumping focus.

## Priority for every choice

**Works well > responds instantly > looks pretty.**

- Rather slightly less pretty and smooth than very pretty and slow. When in doubt, choose the simpler, lighter variant.
- An effect that drops a frame on the Chromecast goes, however pretty it is.
- What doesn't contribute to "browsing and playing" doesn't belong in the app (see "Out of scope" under Status in `NOTES.md`).

## Input and focus (the Plex problem)

- Every press on the remote has a directly visible effect, in the next frame. Never ignore a key or execute it later.
- Keys are processed in order. Hold the button (key repeat) and the focus moves along smoothly without lagging behind.
- The focus never jumps by itself. Data arriving later (images, a refresh, a new row) may not move or take the focus. Only request focus when opening a screen or when returning.
- After Back, the focus is on the item you came from.
- Network work never blocks the UI. A screen shows something immediately (skeleton or cache) and fills in.

## Straight through on click: skeleton instead of waiting

Many apps make you wait after a click until all data is there and only then show the page. JellyCast doesn't: a click always feels like "it's going".

- Navigate in the same frame as the click. Never wait for data before the new screen appears.
- Immediately show the screen's structure with a skeleton: blocks where the poster, title, text and buttons go, in the same sizes as the real content. When the data arrives it fills the blocks without anything shifting.
- Fill in what you already know right away. A click on a poster already passes title, year and poster (from the list or the cache), so those are there immediately; only the rest is skeleton.
- Load ahead: when the focus rests on an item for about 300 ms, fetch the detail data. Not when scrolling past quickly.
- The skeleton is calm: flat blocks with at most a slow, subtle pulse in opacity (`graphicsLayer.alpha`). No shimmer gradient running across the screen, because that costs work every frame on this device.
- Show the skeleton only when loading takes longer than about 100 ms. If the data comes sooner, show the content directly, without a flash of skeleton.
- Focus: the primary button (Play) is already in place in the skeleton and gets focus immediately. It becomes active as soon as the data is there, without the focus moving.
- An error while loading shows a message in place of the content, with a "Try again" button. Never an empty screen or an endless skeleton.

## Approve the design first

- The user approves the design of every main screen before it is built: Home (Home, Series, Movies sections), the "all" grid, series page, movie detail and player overlay. The design is made in Paper (via the Paper MCP).
- Design within these principles: the design also shows what the skeleton and loading screen look like, and uses no effects that are forbidden here.
- Exception: functional screens you rarely see (server setup, sign-in, debug) may first be built simply in the existing style and be taken into the design later.

## Animations and effects

- Allowed: scale and opacity via `graphicsLayer` (cheap, on the GPU), focus border, short crossfades.
- Duration: focus animations ≤ 150 ms, transitions between screens ≤ 250 ms, or none.
- Not: blur (`Modifier.blur`, `RenderEffect`), parallax, moving or autoplaying backgrounds and trailers, glow effects, shadows on every card, or animations that change layout while scrolling.
- A background/hero that follows the focus: only update when the focus has rested for about 300 ms, so fast scrolling loads no large images.
- When scrolling fast (key repeat): load no extra data or images for items the focus only shoots past.

## What's on the screen

- Little at a time: a few rows, posters at the right size, one backdrop. No screens full of text, badges and metadata.
- Images always at display size via the image API (see `tv-ux.md`). No image larger than needed.

## Performance budget (on the Chromecast, release build)

| Measurement | Budget | How to measure |
|---|---|---|
| Janky frames while scrolling on Home | < 5% | `scripts/perf/measure-steady.sh <apk>` (steady state: compiled after use, as on the TV after a night; measuring right after installing reports 2x too much), which runs `scripts/perf/measure-scroll.sh` (30 s of real key repeat via `uinput`, then `dumpsys gfxinfo`). Not `input keyevent 22 22 …`: that sends keys without pauses and measures too heavy |
| Cold start to interactive | ≤ 3 s | `adb shell am start -W` (TotalTime) |
| Warm start | ≤ 1 s | same, after the Home button and back |
| Memory after 5 min of browsing | ≤ 200 MB PSS | `adb shell dumpsys meminfo nl.jellycast.tv` |
| Start playback (direct play, LAN) | ≤ 3 s to first frame | logcat timestamps |

- Always measure on the Chromecast with a release build. Debug builds are much slower and say nothing.
- Record every measurement with date and phase in `NOTES.md`. Exceeding the budget is a bug and gets fixed before new features are added.

## Check on every change

Go through these questions for every feature or UI change, and say so when something doesn't comply:

1. Does every key respond instantly, also when held?
2. Does the focus stay where the user put it, also when data arrives?
3. Does the new screen appear right after the click, with skeleton and what's already known?
4. Does this effect or element add something to browsing or playing? If not: leave it out.
5. Is there a lighter way that looks almost as good?
6. Is it measured on the Chromecast (release) and within budget?
