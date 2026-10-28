# JellyCast iOS player: spec for the Android port

Everything below comes from the iOS source, plus `jellycast-ios/NOTES.md` and the "ios-player-redesign" memory note for the reasoning. Where NOTES.md and the code disagree, **the code wins** and the gap is flagged. Sizes are in pt (use dp). Colours are the `JellyCastColors` tokens (dark only):

| Token | Value |
|---|---|
| background | `#0E0E0F` |
| surface | `#1A1A1C` |
| rail | `#2A2A2E` |
| text | `#ECE9E4` |
| textMuted | `#A3A09A` |
| accent | `#B9A6F2` |
| progress gradient | `#8FB8D8` → `#9A88D0` → `#C584B0` |

Root files, all under `/Volumes/workspace/personal/jellycast/jellycast-ios/JellyCast/`:
- **Player:** `Feature/Player/{PlayerScreen,PlayerOrientation,PlayerControls,PlayerTimeline,PlayerOverlays,TapToSeek,TrackSheet,BufferWatch,NowPlaying,PlayerSession,PlayQueue,ItemInfoPanel,ItemInfoParts,InfoTabs,CastTab,RelatedTab,RelatedMovies}.swift`
- **Mini player placement:** `App/MainView.swift`
- **Design system:** `Core/DesignSystem/{SwipeToClose,SheetRow,Components,LayoutMetrics,Format,JellyCastColors}.swift`
- **Playback core:** `Core/Playback/*`
- **Endpoints:** `Core/Jellyfin/JellyfinClient+{Playback,Transcode,Detail,People}.swift`
- **Hand-over:** `Feature/RemotePlay/{PlayOnSheet,RemoteNowPlayingScreen,RemoteSessions}.swift`

---

## 1. Player screen

### 1.1 Architecture
- **`NowPlaying`** is app-level and owned by the main view. It holds one `PlayerSession` (or none), `isExpanded`, `isMiniPlayerLarge` (kept between plays) and the PiP controller.
  - The play outlives the screen.
  - Expanded shows a full-screen cover with `PlayerScreen` and a **clear background**, so the app shows through while you swipe it away.
  - Collapsed shows the mini player.
- **`NowPlayingObserver`** lives as long as the play, whichever view shows it. It forwards to the session:
  - engine state → `stateChanged`, and `.ended` → `itemEnded()`;
  - buffering → `bufferingChanged`;
  - a landed seek → `seeked()`;
  - a video route change → re-attach PiP.
- **Android:** a `PlayerController` / ViewModel scoped to the Activity (or a `MediaSessionService`) owning one ExoPlayer. One `PlayerView` / `SurfaceView` is moved between the full and mini layouts, or two surfaces call `player.setVideoSurface…` in turn.
- **`NowPlaying.play(playable, streams?, queue?)`:**
  - sets `isExpanded = true`;
  - if a session exists, calls `session.follow(playable, queue)`, then `session.play(next)` unless it is the same id;
  - otherwise creates a session and calls `load()`;
  - if the engine can't be created, shows the alert "The player could not start."
- **`close()`:** stops the session (reports the stop) and the engine, and clears the session.
- **`minimize()`:** `isExpanded = false`.

### 1.2 Orientation (`OrientationLock`)
- **iPhone:** the app is portrait-only except while the player is expanded (all but upside-down). Collapsing the player forces portrait again.
- **Full-screen button (iPhone):** forces landscape and holds that lock until the device itself is physically held in landscape. After that the lock goes back to the default, so the sensor rules again. Exit full screen forces portrait the same way. Turning the phone also enters and leaves full screen.
- **iPad:** all orientations always; the full-screen button doesn't rotate (see 1.4).
- **Android:**
  - `requestedOrientation = SENSOR_LANDSCAPE` on the button;
  - an `OrientationEventListener` resets it to `UNSPECIFIED` / `FULL_USER` once the device matches;
  - `PORTRAIT` outside the player on phones.

### 1.3 Layout
- **Portrait and every non-full-screen compact layout (YouTube-style):** a vertical column.
  1. **Video area** at the top, under the status bar (not edge-to-edge in portrait), full width, height = width / (16/9), black.
  2. **`ItemInfoPanel`** in column arrangement below it, on `background`. It scrolls independently; the video stays pinned.
- **Full screen (iPhone landscape):**
  - the video fills the whole screen, ignoring safe areas;
  - status bar and home indicator are hidden (`statusBarHidden` + `persistentSystemOverlays(.hidden)`);
  - no info panel.
  - **Android:** `WindowInsetsControllerCompat.hide(systemBars())` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
- **What counts as full screen:**
  - compact layout (phone): full screen = landscape;
  - regular layout (iPad): full screen = the "video only" toggle.
- **Regular vs compact:** regular only when both size classes are regular. A phone held sideways, or a narrow iPad split window, stays compact. Android equivalent: WindowSizeClass width ≥ Medium **and** height ≥ Medium.
- **iPad:**
  - **Landscape, not video-only:** split arrangement.
    - Left pane is 64.5% of the width (rounded): the video (16:9, aspect-fit) as header, then the summary scrolling below it.
    - Right pane: the tab bar (24 padding horizontal, 18 top, 8 bottom), then the tab content scrolling (24 horizontal, 10 top, 40 bottom).
  - **Portrait:** column arrangement. Text column at most 640 wide, margin 32 (phone margin 20). From 1100 wide the movie detail shows the cast beside the text.
  - **Video-only toggle:** animated easeOut 0.2 s, in either orientation; same full-screen chrome as the phone.

### 1.4 Video area, bottom to top
1. Black background, then the video surface.
2. **Tap layer** (only once started; see 1.6).
3. **Subtitle overlay** (not hit-testable). Draws only the cues whose start ≤ now < end, inside the fitted video rectangle.
   - **Text cues:** white, medium weight, size = 5% of the video height clamped 13–30, shadow black radius 2, 12 horizontal padding, bottom inset 7% of the video height, centred.
   - **Image cues (PGS):** positioned by their normalised rectangle.
   - **Android:** a Media3 `SubtitleView` with a matching `CaptionStyleCompat`, bottom padding fraction 0.07, text-size fraction about 0.05.
4. **Controls overlay** (when shown and started). Padding:
   - non-full-screen: top 4, left 14, bottom 6, right 6;
   - full screen: top max(inset, 12), left max(inset, 20), bottom max(inset, 8), right max(inset, 20).
5. **Portrait scrub bar** (non-full-screen, started).
   - Always visible: a 3-pt line along the bottom edge of the video, straddling it (a 28-pt touch frame aligned to the bottom, offset +13).
   - The knob and dragging work only while the controls show.
6. **Segment pills**, bottom-right. Right padding max(inset, 12). Bottom padding:

   | | Controls shown | Controls hidden |
   |---|---|---|
   | Full screen | 110 | max(inset, 24) |
   | Not full screen | 56 | 14 |

7. **Stall indicator**, centred. Shown when started, stalled, controls hidden and no error: a large spinner plus "Waiting for the connection" (footnote semibold), shadow 0.6 / 4.
8. **Error view or start overlay**, covering the area (see 1.9).

### 1.5 Controls overlay (`PlayerControlsOverlay`)
- **No scrim.** Every icon carries a black shadow, opacity 0.6, radius 4. The overlay fades in and out with an easeOut 0.2 s opacity animation.
- **Top row:**
  - **Left, full screen only:** a title block, not hit-testable, 6 top padding, one line each:
    - title in headline;
    - subtitle in footnote muted;
    - movie: name, then year;
    - episode: series name, then "S1 · E3 · Episode name".
  - **Right, an HStack with spacing 4.** Each button is a plain white SF Symbol, 19 pt semibold, in a 44×44 hit box, with no glass; pressed = opacity 0.7. Order:
    1. **PiP** `pip.enter` ("Picture in Picture"). Only when PiP is possible.
    2. **TV** `tv` ("Continue on…"). Opens the Play-on sheet as a hand-over.
    3. **AirPlay** route picker (44×44, white tint, prefers video devices).
    4. **Audio and subtitles** `speaker.wave.2.bubble` ("Audio and subtitles").
    5. **Quality** `gearshape` ("Quality").
- **Centre:** a single play/pause button. There are **no ±10 s buttons**; they were removed in favour of double-tap.
  - 64-pt circle filled black at 35% opacity, icon `play.fill` / `pause.fill` 28 pt semibold.
  - a11y "Play or pause".
  - While stalled it becomes a large spinner in the same circle ("Waiting for the connection").
- **Bottom:**
  - **Full screen only:** a scrub bar (4-pt line, knob always shown) above the bottom row.
  - **Bottom row:**
    - **Left:** the time label, footnote semibold with monospaced digits: `0:08` in text, `/ 24:52` in muted, `· 4:57 buffered` in muted. Times are `m:ss`, or `h:mm:ss` from 1 h; `--:--` when not known.
    - **Spacer.**
    - **Only while the active preference is Auto:** `Auto · 1080p` (footnote semibold, muted).
    - **Full-screen toggle:** 22-pt icon. Expand `arrow.up.left.and.arrow.down.right` ("Full screen"), collapse `arrow.down.right.and.arrow.up.left` ("Exit full screen").
- **Auto-hide:**
  - Controls start visible. After 3 s they hide, but only if all of these hold: still playing, not scrubbing, no sheet open.
  - The timer is re-armed on:
    - the start reaching `started`;
    - the engine going to playing while the controls show;
    - any interaction: play/pause, PiP, scrub, full screen;
    - a sheet closing.
  - While paused they never hide.
  - Opening a sheet cancels the timer and forces the controls visible.
  - A single tap toggles them (easeOut 0.2 s) and re-arms the timer when it shows them.

### 1.6 Tap and double-tap seek (`TapToSeekLayer`)
- The layer covers the whole video area.
- **Single tap:** toggles the controls at once, with no delay.
- **Double tap** (second tap on the same half within 0.3 s):
  - the second tap toggles the controls back, undoing the first;
  - it starts a seek run with step **10 s**, backwards on the left half, forwards on the right.
- **Further taps** on the same side while a run is active add 10 s each, with no time limit between taps other than the settle delay.
- **The seek runs once,** 650 ms after the last tap, to clamp(start + n×10, 0, duration). A streamed file is asked for one position only.
- **Seek bubble:**
  - an overlay 40% of the area's width, full height, on the tapped side;
  - a white ellipse at 18% opacity, scaled x2 / y1.4 anchored at the inner edge and clipped (a half-moon);
  - `backward.fill` / `forward.fill` 20 pt semibold over the text "`20 s`" (footnote semibold);
  - opacity transition, 0.15 s.
- **Accessibility:** label "Video", actions "Back 10 seconds" and "Forward 10 seconds".
- **Android:** `pointerInput { detectTapGestures(onTap, onDoubleTap) }` would delay every single tap, so copy the custom logic: handle `onTap` yourself and compare timestamps.

### 1.7 Scrubbing (`ScrubBar`)
- **Track:**
  - text colour at 30% opacity (capsule);
  - a buffered band in text at 35% opacity, up to max(buffered, position);
  - the played part as the left-to-right progress gradient;
  - knob 13-pt white circle, shown only when the controls show.
- **Touch:** a 28-pt-tall frame. Drag with minimum distance 0, anywhere on the bar. The fraction comes from finger x; `scrubSeconds` is shown live in the time label.
- On release it seeks, then clears `scrubSeconds`. Every change re-arms the hide timer, and the timer won't hide while scrubbing.
- a11y "Position".

### 1.8 Swipe down to close (`SwipeToClose`)
- Works on the video area in portrait, landscape and iPad split. In full screen the whole screen is the video.
- **Gesture:** drag with minimum distance 16. It only engages when the vertical movement is greater than the horizontal, or it is already dragging. offset = max(0, dy).
- **While dragging:**
  - the whole content scales to 1 − 0.5 × progress, anchored at the top, and moves down by `offset` (progress = offset / screen height);
  - the black backdrop opacity is 1 − progress, so the app shows through.
- **Release:**
  - **Close** when offset > 120 or the predicted end translation > height / 2. Animate easeIn 0.2 s to offset = height with opacity 0, then call `minimize()` with no animation. The play goes on in the mini player; it does not stop.
  - Otherwise spring back (0.25 s).
- There is **no close button** in the full player.
- **Android:** a Modifier with `draggable` / `pointerInput`, `graphicsLayer { scaleX = scaleY = …; translationY; transformOrigin = TopCenter }`, and the host window or sheet background alpha. Predictive back should also minimise.

### 1.9 Start, stall and error states
- **Before the first frame (`StartOverlay`):** black with a large spinner.
- **Loading ahead** on a slow connection (iOS loads the original through its own cache), phase `.loadingAhead(secondsLeft, connectionMbps, peakMbps)`:
  - a 72-pt ring: track at 20% text, 4-pt stroke; progress in accent, round caps, starting at the top, animated linearly over 0.5 s;
  - `m:ss` in the centre;
  - "Starts in 1:23" (title3 bold);
  - the explanation (subheadline muted): "The Wi-Fi now does 12 Mbit/s and this file peaks at 40. Loading ahead first, then it plays without stalling." It is dropped when the height doesn't fit;
  - a glass button "Start now";
  - max text width 420, padding 20.
  - **Formula:** lead = remaining runtime × (1 − connection / (1.3 × bitrate)) + 30 s, capped at 1.5 GB.
  - **Android with ExoPlayer:** you may skip this, or copy it with a larger `DefaultLoadControl` buffer. Note the strings anyway.
- **Error (`PlayerErrorView`):** black background, centred, max width 420, spacing 14.
  - `exclamationmark.triangle` (title2, muted), then the message (subheadline, centred), then glass buttons:
    - "Try again": reloads; if the failure came mid-play it resumes from the failed position;
    - when the original doesn't play and a transcode is offered: "Play via the server · 1440p". This picks the highest offered level below Original and sets it as the fixed quality.
  - **Messages:**
    - "This item has no file the server can play." (no MediaSources)
    - "This file doesn't play on this iPhone." (engine or decoder failure; Android: "…on this phone")
    - "Playback stopped because of an error in the file or the connection." (engine error after the start)
    - network or Jellyfin errors use their own localised text.
  - The engine's own text goes to the log, not to the user.
- **Mini player:** a warning triangle on error ("This item doesn't play"); a spinner while starting or stalled.

### 1.10 Segment pills (`SegmentPills`), bottom-right of the video
- **Look:** capsule, height 40, horizontal padding 18. Label with icon, subheadline semibold, text colour, interactive glass. Android: a FilledTonal / Assist chip on a translucent surface.
- **Priority** (one shows at a time; animations easeOut 0.2 s):
  1. **Slow-connection hint** (`wifi.exclamationmark`). Tap dismisses; auto-dismisses after 10 s.
     - "Slow connection · 1080p under the gear plays smoother" when a lower level exists;
     - otherwise "The connection can't keep up with this file".
  2. **Skip pill** (`forward.end.fill`) while an Intro, Recap or Preview media segment contains the position and more than 1 s of it remains. Tap seeks to the segment end. Labels: "Skip intro", "Skip recap", "Skip preview".
  3. **Next pill** (`forward.fill`) in the credits when there is a next item. Tap calls `playNext()`.
     - Episode, no list: "Next episode · S3 · E2".
     - From a list (PlayQueue), episode: "Next · The White Lotus S3 · E2".
     - From a list, movie: "Next · Movie name".
- **Credits:** position ≥ the start of the Outro segment, or, without an Outro, the last 30 s of the duration.
- **Segments endpoint:** `GET /MediaSegments/{itemId}`, giving `Items[{Type, StartTicks, EndTicks}]`. Types: Intro, Recap, Outro, Preview, Commercial.

### 1.11 End of an item
- When the engine reports ended, `NowPlaying.itemEnded()` runs: if `nextItem` exists, `playNext()` (auto-advance, no countdown); otherwise `close()` (the session and the mini player disappear).
- **nextItem:**
  - from a PlayQueue: its next item, refreshed from the library index;
  - otherwise for an episode: the next episode in the series' ordered episode list with season > 0, so it crosses season ends and skips specials;
  - movies: none.
- **playNext** advances the queue and calls `play(next)`. That reports the stop for the current item and resets segments, mediaInfo, level and transcode track indexes. **The quality picked in the player carries over.**

### 1.12 Sheets
- All are bottom sheets with detents medium and large, a visible drag handle, padding 20 and spacing 14. They scroll, bouncing only when the content is taller.
- **Android:** `ModalBottomSheet`, `skipPartiallyExpanded = false`.
- **Rows:**
  - **`SheetRow`:** min height 56, padding 14. Background text at 5% opacity, corner 18, 1.5-pt border at text 10%.
    - Title body semibold; detail subheadline muted; one line each.
    - Optional 40-pt circle icon (18 pt semibold) tinted text or accent, with a 12% fill.
  - **`CheckedSheetRow`:** the same with a trailing checkmark (16 pt bold, accent) when selected.
  - **`SheetTitle`:** title2 bold, with an optional muted subheadline under it.

**Audio and subtitles (`TrackSheet`).** It stays open after a choice, so both can be set in one go.
- **Layout:**
  - Title "Audio and subtitles".
  - Section label "Audio" (headline, muted, 4-pt inset), then audio rows.
  - Section label "Subtitles" (8 top padding), then "Off" first, then subtitle rows.
- **Audio rows when the file plays directly:**
  - title = language name in the app locale (else track name, else codec uppercased);
  - detail = `CODEC · 6ch · Atmos` (for example "TRUEHD · 8ch · Atmos").
- **Audio rows while transcoding:** the server's `DisplayTitle` (or Codec) per `MediaStreams` Index.
- **Subtitle order (`SubtitleOrder.entries`):**
  1. Rank by the user's preferred languages from `GET /Users/Me` → `Configuration.SubtitleLanguagePreference`, then `AudioLanguagePreference`. Codes are normalised to ISO 639-2/T ("dut" → "nld", "fre" → "fra", 2-letter → 3-letter; "und" counts as none).
  2. Then tracks with a language before tracks without one.
  3. Then A–Z by language name in the app locale.
  4. Within the same name: plain, then SDH, then Forced.
  5. Then the file's order.
- **Subtitle labels:**
  - **Title** = the language name (capitalised; "Track N" when there is no language and no title).
  - **Detail** joins with " · ": the file title only if it adds something; "SDH"; "Forced"; "Image" for PGS / DVD / DVB / VobSub codecs.
  - **A file title is dropped when:**
    - it is longer than 40 characters;
    - all tracks share the same title (a release name);
    - it only repeats the language name (locale, English or native), SDH / CC / "hearing impaired", or the noise words: forced, full, default, subtitles, subtitle, subs, srt, subrip, ass, ssa, pgs, vobsub.
  - **SDH** = the hearing-impaired flag, or the title contains the word sdh, cc or "hearing impaired". **Forced** = the flag, or the word "forced".
  - **Duplicates** that still look identical get numbers: "Spanish", "Spanish 2", …
  - The same logic exists on Android TV in `SubtitleEntries.kt`; reuse it.
- **While transcoding,** a track change means a new transcode at the same position: a reload with `AudioStreamIndex` / `SubtitleStreamIndex`.
- **Direct play** switches the track in the player.

**Quality (`QualitySheet`).** It dismisses on choice and replays at the current position if the choice changed.
- **Title:** "Quality". Subtitle "Measured 238.6 Mbit/s": this play's measurement, else the last fresh one on this network; none if unknown.
- **Rows, in order:**
  1. **Auto row.** "Auto", or "Auto · 1080p" when Auto is active and has picked. Choosing it uses the network's setting if that is an Auto variant, else Auto up to Original.
  2. **Each offered level, highest first:** "Original · 19.9 Mbit/s", "10 Mbit/s · 1440p", "5 Mbit/s · 1080p", "2.5 Mbit/s · 720p". Only levels that would actually shrink the file are offered; Original is always there.
- **The choice holds** for the rest of the play, next items included. The quality never changes by itself.

**Continue on… (`PlayOnSheet` with hand-off).** Spec separately with remote play; the strings are in section 6.
- Title "Continue {name} on", subtitle "Continues from 9:04".
- Lists the TVs, no "This iPhone".
- Choosing a TV sends the position and the current audio and subtitle stream indexes, then closes the player (`nowPlaying.close()`).

### 1.13 BufferWatch and the slow-connection hint
- **Buffered ahead,** sampled every second, is shown in the time label and as the band.
- **Hint rule:**
  - only while playing and not seeking;
  - the window resets on pause, on seek, and when the buffer reaches within 10 s of the end;
  - fires when, over ≥ 27 s of a 30-s window, the buffer dropped by ≥ 5 s **and** is now < 60 s;
  - at most one hint per 5 minutes (carries over across loads).
- **Stall** = the engine is buffering after the start. On Android use `Player.STATE_BUFFERING` while `playWhenReady`.

### 1.14 PiP, AirPlay, Dynamic Island

| iOS | What it does | Android equivalent |
|---|---|---|
| **PiP** | Around the AVPlayerLayer; button only when possible; starts automatically when the app goes to the background. When the app comes back to the foreground, a running PiP stops and the video returns to the full or mini player it came from. | `PictureInPictureParams` with `setAutoEnterEnabled(true)` (API 31+), aspect 16:9, `RemoteAction`s for play/pause; or `enterPictureInPictureMode` from `onUserLeaveHint`. Hide the controls in PiP. |
| **AirPlay** | Route picker button | Cast button (`MediaRouteButton`, Media3 Cast extension), or leave it out. |
| **Dynamic Island / Live Activity** | Not used by the player (only by downloads) | Media3 `MediaSessionService` media notification and lock-screen controls. |

---

## 2. Info area under the video (`ItemInfoPanel`)
The same panel is used under the backdrop on the movie detail, on the now-playing-on-TV screen and, with `showsWholeSeries`, on the series page.

### 2.1 Column arrangement (phone and iPad portrait)
- One ScrollView with the indicator hidden. Content padding: margin horizontally (20 phone, 32 iPad), 18 top, 40 bottom, spacing 22.
- **Summary, spacing 14:**
  - **Heading, spacing 8:**
    - **Title,** title2 heavy, wraps. Movie: its name. Episode: **the series name** (the episode's own name is in the episode list).
    - **Meta line,** subheadline muted, joined with " · ":
      - movie: year · runtime ("1 h 58 min" / "48 min") · up to 2 genres (translated) · official rating;
      - episode: "S1 · E3" · runtime · up to 2 series genres · series official rating · episode rating (from the selected rating site).
    - **Tech badges,** 2 pt above. Each badge is the text uppercased (caption bold, tracking 0.6), height 24, horizontal padding 8, 1-pt rail border, corner 6, spacing 6. Parts:
      - resolution: "4K" when height > 1100, "1080p" when > 730, else "{h}p";
      - range: Dolby Vision / HDR10+ / HDR10 / HLG;
      - the default audio track: TrueHD Atmos, TrueHD, Dolby Atmos (EAC3 + Atmos), Dolby Digital+, Dolby Digital, DTS-HD MA, DTS:X, DTS, "AAC 6ch" when more than 2 channels, FLAC;
      - "PGS" if there is a PGS subtitle.
  - **Overview** (movie overview; episode: **series** overview). Subheadline muted, 3 lines, tap to expand or collapse.
  - **Link row,** spacing 10:
    1. **External link capsule** (height 34, horizontal padding 14, surface fill): site name (subheadline bold) plus `arrow.up.right` muted. The site is from Settings → Ratings: IMDb (default), Rotten Tomatoes or TMDB. It opens a universal link, so the app opens when installed. Episode: the series link.
    2. The rating text (footnote muted).
    3. **Actions slot:** in the player, a 34-pt "Continue on…" `tv` circle (rail fill, 18 pt bold icon), only when remote sessions exist.
    4. The download button (5 states; out of scope).
- **Tab bar,** 6 extra top padding:
  - a rail-filled capsule, height 40, inset 3;
  - equal-width segments; the selected one is a capsule filled grey (white 0.35);
  - labels subheadline semibold when selected, medium otherwise;
  - Chat has a `sparkle` icon in accent.
  - **Android:** a custom segmented row, or `SingleChoiceSegmentedButtonRow` restyled.
- **Choosing a tab** smooth-scrolls (easeInOut 0.25 s) so the tab bar sits right under the video. Tab content gets a min height of viewport − 72 (+ the chat input height on Chat) so this is always possible.
- **Tabs:**

  | Item | Tabs |
  |---|---|
  | Episode | Episodes, Cast |
  | Movie | Cast, Related |

  Chat is appended when a chat API key is set. The default is the first tab, and it resets per item.

### 2.2 Split arrangement (iPad landscape)
- Left 64.5%: header (the video) and the summary scrolling.
- Right: the tab bar and the tab content scrolling. Paddings are in 1.3.

### 2.3 Episodes tab (player)
- **Window:** from the cached library index, the series' regular episodes (season > 0) in order, across season ends: **1 before** the playing one and **6 after** (`episodesBefore = 1`, `episodesAfter = 6`). NOTES.md says "next four"; the code says 6.
- Rows have spacing 18.
- **Row (`EpisodeInfoRow`), compact:** an HStack with spacing 12, centred.
  - **Still:** 136 wide, 16:9, corner 8. Uses the episode Primary image, else the series backdrop.
    - progress bar on the image when in progress (8 horizontal, 6 bottom);
    - image opacity 0.55 when watched and not playing;
    - a 2-pt accent border on the one playing.
  - **Text column:**
    - **Title** "3. Episode name" (subheadline semibold, 2 lines; muted when watched).
    - **Status line,** footnote, 1 line:
      - playing: "Playing now · 18 min left" (semibold accent), or "Playing on {TV}";
      - watched: "Watched · 48 min" (muted);
      - in progress: "18 min left" (accent);
      - otherwise the runtime (muted).
    - **Inline link + rating:** "IMDb" footnote bold + `arrow.up.right` caption2, 2 pt above.
  - **Trailing:** a 28-wide slot with a checkmark (15 pt bold muted) if watched and not playing, then the download button.
  - **Overview** under the row: footnote, 3 lines; text colour when playing, muted otherwise.
- **iPad (`isLarge`):** still 192, top-aligned, spacing 16, overview inside the text column.
- **Tap** on another episode: `nowPlaying.play(.episode)` in the same player. That keeps the queue if the episode is in it, otherwise drops the queue. Tapping the playing one does nothing.
- **Long press:** a bottom drawer with the episode still as header and "Mark as watched" / "Mark as not watched". Both show while in progress. "Add to list…" is absent in the player.
- **Offline:** rows not downloaded are dimmed to 35% and ignore taps.

### 2.4 Cast tab (`CastTab`)
- **Data:**
  - `GET /Items/{id}?userId=&fields=People` → `People[{Id, Name, Role, Type, PrimaryImageTag}]`;
  - for an episode also the series' credits, to fill actors or producers when the episode has none;
  - person details in one batch: `GET /Items?userId=&ids=a,b,c&fields=Overview,ProductionLocations,PremiereDate,ProviderIds`. PremiereDate is the birth date, ProductionLocations[0] the birthplace, Overview the biography.
- **States:** a spinner (24 vertical); on error "The cast couldn't be loaded." with a glass "Try again".
- **Content,** spacing 22:
  - **Actors,** first 6, in server billing order. Each row:
    - 64-pt circular photo (initials on rail when there is no image);
    - name (headline) with a trailing "IMDb ↗" text button;
    - "as {Role}" (subheadline accent);
    - facts "48 years · New York City, United States" (footnote muted);
    - biography (subheadline muted, 3 lines, 4 top padding).
  - **Expand link** (subheadline semibold accent + chevron.down): "All 23 actors", or "All 23 actors and 5 guest stars" when guest stars exist. Expanding also shows the "GUEST STARS" section label.
  - **Makers,** after a 1-pt rail divider: "Makers" (title3 bold), then groups:
    - "Directed by" and "Written by": maker rows with a 44-pt photo, name subheadline semibold, facts, and the IMDb text button. For an episode the labels become "Directed by · this episode" and "Written by · this episode".
    - "Produced by" (episode: "Produced by · series"): names joined with " · " as plain text.
  - Section labels are uppercased (footnote semibold, tracking 0.8, muted).
- **Tap a person:** opens the person on the rating site using the IMDb / TMDB id from the details. If missing, it fetches `GET /Items/{personId}?fields=ProviderIds`, falling back to a name search URL.

### 2.5 Related tab (movies only; `RelatedTab` + `RelatedMovies`)
- **Collection section:** library movies with the same `ProviderIds.TmdbCollection` (stored as `collectionID` at library sync), excluding the current one, sorted by year.
  - Title = the common prefix before ":" if every title shares it ("The Lord of the Rings"); otherwise "From the same collection".
  - No Jellyfin BoxSets are needed.
- **Related section ("Related"):**
  - **Fetch:** `GET /Items/{id}/Similar?userId=&limit=20&fields=People,Studios`, plus the movie's own `GET /Items/{id}?fields=People,Studios`.
  - **Rank** (the "related-movie ranking"):
    1. exclude the movie itself and its collection;
    2. keep only candidates sharing **≥ 2 people** (cast or crew, by person Id);
    3. score = 2 × shared people + 1 × shared studios;
    4. sort descending;
    5. keep only movies present in the cached library;
    6. group by collection: the first occurrence of a collection pulls its siblings next to it, and each group is sorted by year (a trilogy reads 1, 2, 3).
  - Example: Fellowship gives Two Towers and Return of the King (collection), then the three Hobbits. Ready Player One and Ministry drop out.
- **Grid:** adaptive columns of at least 110, spacing 12, row spacing 18.
  - Tile: 2:3 poster, corner 8; name (footnote semibold, 3 lines); year (caption muted).
  - Sections have spacing 24 and a section label.
- **States:** a spinner while related movies load. If both lists are empty: "Nothing related in the library." (muted, centred, 24 vertical).
- **Tap:** plays the movie in the same player.

### 2.6 Chat tab (entry point only)
- Shown only when `ChatSettings.hasKey`: an API key is stored for the selected provider, Claude (Anthropic) or ChatGPT (OpenAI). It is the last tab, with an accent sparkle.
- **Settings → "AI chat":** provider, key (shown masked), "Search the web" (default on), "No spoilers" (default on; for an episode the chip reads "No spoilers after S1 · E3").
- **Panel behaviour:**
  - the input bar is pinned as a bottom inset only while Chat is selected;
  - the panel auto-scrolls to the end of the conversation, never above the tab bar;
  - one conversation per series or movie, with "New conversation" to reset it.

---

## 3. Mini player
- **Defined in:** `MiniPlayer` in `NowPlaying.swift`. Placed by the main view as a bottom-trailing overlay over the tab UI.
- **Shown** when a session exists and the player is not expanded.
- **Position:** trailing margin 16. Bottom offset:

  | | Bottom offset | Extra while the TV bar shows |
  |---|---|---|
  | Phone (above the tab bar) | 72 | +56 |
  | iPad (tab bar at the top) | 16 | +88 |

- **Size:** always 16:9, corner 12, 1-pt rail border, black behind the video.

  | | Small | Large |
  |---|---|---|
  | Phone | width 200 | screen short side − 32 (the full portrait width minus margins; landscape keeps the portrait width) |
  | iPad | width 320 | width 480 |

  The size is kept between plays (`isMiniPlayerLarge`, in memory).
- **Content:**
  - the live video (the same engine surface, handed over from the full player);
  - a centre spinner while starting or stalled, or a warning triangle on error;
  - a top row: play/pause at top-left and × "Close" at top-right, using the player icon buttons (44 hit box, icons 15 pt small / 19 pt large, shadow).
  - There is no title or progress bar.
- **Gestures:**
  - **Tap** anywhere: expand back to the full player, with no gap in playback.
  - **Pinch:**
    - live feedback scale clamped to 0.8–1.25, anchored bottom-trailing;
    - on release: magnification > 1.15 gives large, < 0.85 gives small, otherwise nothing;
    - spring 0.3 s.
  - **×** ends the play (stop report, session cleared).
  - **There is no drag, swipe-to-dismiss or reposition gesture.**
- **Appear and disappear:** scale from 0.6 at bottom-trailing combined with opacity, easeOut 0.2 s.
- **Related:** while something plays, the "Continue {title}" accessory above the tab bar is hidden.
- **Android:**
  - an overlay Box anchored above the `NavigationBar`, with `detectTransformGestures` for the pinch;
  - consider adding a swipe-to-dismiss, although iOS has none;
  - system PiP is separate (see 1.14).

---

## 4. Core/Playback

### 4.1 PlaybackReporter
- One serial queue: reports are sent in order, and failures are only logged.
- **start:** `POST /Sessions/Playing`, right after load and play start. Body:
  - ItemId, MediaSourceId, PlaySessionId, PositionTicks (the start position), IsPaused = false, IsMuted = false, CanSeek = true;
  - PlayMethod: "DirectPlay" or "Transcode";
  - AudioStreamIndex only for a transcode.
- **progress:** `POST /Sessions/Playing/Progress`, same body plus EventName:
  - "TimeUpdate" every 10 s and after every landed seek;
  - "Pause" / "Unpause" immediately on a state change.
  - Progress is ignored after the stop for the same PlaySessionId.
- **stop:** `POST /Sessions/Playing/Stopped` with {ItemId, MediaSourceId, PlaySessionId, PositionTicks}. Sent on close, on the next item, on a quality or track reload, and on an engine error.
  - If **finished** (engine ended, or in the credits), it then sends `POST /UserPlayedItems/{id}?userId=` to mark the item played.
  - After a stop: record the position locally at once (optimistic library update), `flush()` the queue, then refresh the library.
- **PlaySessionId:** a random UUID for direct play; the server's id from PlaybackInfo for a transcode. A stop with it ends the server transcode.
- **Downloads:** no server reports. The position goes to `OfflineProgress` (`progress.json` with a watched-at time) every 10 s, on pause and on stop, and is sent later with `POST /UserItems/{id}/UserData` if it is newer than the server's `LastPlayedDate`.

### 4.2 StreamQuality

| Level | Max width | Max bitrate | Short name | Label |
|---|---|---|---|---|
| p720 | 1280 | 2.5 Mbit/s | 720p | "2.5 Mbit/s · 720p" |
| p1080 | 1920 | 5 Mbit/s | 1080p | "5 Mbit/s · 1080p" |
| p1440 | 2560 | 10 Mbit/s | 1440p | "10 Mbit/s · 1440p" |
| original | – | – | Original | "Original · 19.9 Mbit/s" (the file's bitrate) |

- **Preferences:** `auto(upTo:)` or `fixed(level)`. All values in the settings picker order: Auto, "Auto, up to 10 Mbit/s · 1440p", "Auto, up to 5 Mbit/s · 1080p", "Auto, up to 2.5 Mbit/s · 720p", Original, "10 Mbit/s · 1440p", "5 Mbit/s · 1080p", "2.5 Mbit/s · 720p".
- **Stored as** "auto-{raw}" / "fixed-{raw}". Raw values: 720p = 0, 1080p = 1, 1440p = 2, original = 3.
- **Per-network defaults** (Settings → Streaming quality):
  - Local network: Original (fixed);
  - Wi-Fi: Auto;
  - Mobile data: Auto up to 1080p.
- **Resolution rule:** a level that would not shrink the file (source bitrate ≤ cap **and** source width ≤ max width) resolves to Original. "Offered" = the levels that resolve to themselves, highest first; Original is always included.
- **Auto picks once at the start** and never adapts:
  - take the highest level ≤ the ceiling whose bitrate × 1.3 ≤ the measured connection;
  - without a measurement, take the ceiling;
  - if nothing fits, 720p.
  - The measurement is fresh for 10 minutes and reset when the network type changes. It comes from a warm-up on the detail page or 3 chunks fetched at Play.

### 4.3 Direct play vs transcode on iOS
- **Original:** `GET /Videos/{id}/stream?static=true&mediaSourceId=…` with an Authorization header. The device engine remuxes or decodes everything itself, so the server does no work.
  - On iOS it goes through the app's own chunked disk cache (2 MB chunks, up to 5 min / 1.5 GB ahead) served on 127.0.0.1, because AVPlayer buffers only about 4 s.
  - **Android:** ExoPlayer can stream the URL directly with a large `DefaultLoadControl` buffer (for example 5 min max, back buffer), so the loopback server isn't needed.
- **Transcode** (only when the quality level isn't Original, or via "Play via the server"):
  - `POST /Items/{id}/PlaybackInfo` with UserId, MediaSourceId, MaxStreamingBitrate, AudioStreamIndex, SubtitleStreamIndex (−1 = none), and EnableDirectPlay / EnableDirectStream = false, EnableTranscoding = true, AllowVideoStreamCopy = false;
  - DeviceProfile: one TranscodingProfile {Video, mp4, hls, hevc, aac, Streaming, MaxAudioChannels 2, MinSegments 1, BreakOnNonKeyFrames true}, plus SubtitleProfile {vtt, Hls};
  - play `TranscodingUrl + "&MaxWidth={w}"` as HLS.
  - A text subtitle arrives as a WebVTT rendition and is auto-selected; an image subtitle is burned in.
  - On Jellyfin 10.11 an HDR source comes out as SDR.
- **Media info:** `GET /Items/{id}?userId=&fields=MediaSources`, first source: Id, Bitrate, Size, MediaStreams.
- **Audio track at start** (`AudioLanguage`):
  1. the title's original language (Wikidata P364 via the IMDb id; series id for episodes);
  2. then "eng", then "nld";
  3. otherwise the file's default track.
  - Commentary tracks are never picked.
  - Within a language the default flag wins.
  - Applied before play for direct play, or as AudioStreamIndex for a transcode.
- **Start position:** the item's `PositionTicks` if in progress, else 0. After an error, the failed position.

### 4.4 NetworkMonitor
- **Path monitor:** `isOnline` (status satisfied) and `isCellular` (`isExpensive`, so a hotspot counts as cellular). A change resets the connection meter.
- **Network kind for quality:**
  - **local** when the server's IPv4 is inside the subnet of an up, non-loopback, non-point-to-point interface (a VPN doesn't count; a host name never counts);
  - otherwise **cellular** if expensive, else **wifi**.
- **Android:** `ConnectivityManager.NetworkCallback` with `NET_CAPABILITY_NOT_METERED`; `LinkProperties.linkAddresses` for the subnet check; exclude TUN / VPN.

### 4.5 Offline downloads → player interface
- `PlayerSession.load` first checks `DownloadStore.localFile(itemID)`: `Downloads/<item>/compact.mp4`, or `original.*` excluding `.resume`.
- If found, it plays the file URL with no server calls at all: no media info, segments, reports or quality.
- The track sheet then shows the player's own tracks. Progress goes through OfflineProgress as in 4.1.

---

## 5. PlayerSession
- **Lifecycle of `load()`:**
  1. reset the error, set phase `preparing`, reset the buffer watch;
  2. try the local download first;
  3. in parallel: media segments and original languages;
  4. media info;
  5. apply requested streams (from a hand-over);
  6. pick the audio track;
  7. prepare the stream (Auto measurement → level → original or transcode);
  8. engine load with the start position;
  9. select tracks;
  10. phase `started`, then `play()`, start report, the 10-s progress loop and the 1-s buffer-watch loop.
- **Phases:** `preparing` → optionally `loadingAhead` (countdown every 500 ms; "Start now" skips it) → `started`. A cancelled load is silent; other errors set `loadError`.
- **Resume:** start at `watch.positionTicks` when in progress; 0 when watched or not started. "Play from the beginning" uses `playable.fromStart`.
- **Reload** (quality, transcode audio or subtitle change): stop (with a stop report) at the current position, then load from there.
- **Marked watched:** on stop, if the engine ended **or** the position is in the credits (≥ the Outro start, or the last 30 s without an Outro), it sends `setPlayed(true)` after the stop report. It is local first: `store.recordPlayback(…, finished:)`.
- **Queue (`PlayQueue` {listName, items, position}):** started from a Jellyfin playlist (List screen).
  - `nextItem` = `queue.next` (taken fresh from the library index) instead of the next episode;
  - used at the end of an item, on the "Next ·" pill and in auto-advance;
  - tapping an item that is in the list moves the position there; tapping anything else drops the queue.
- **Requested streams:** an audio / subtitle server index pair, applied once at the next start. Direct play maps the server index to the player track by order among the embedded tracks; external subtitle files are skipped.

**"Continue on…" and hand-over hooks.**
- **Player → TV:**
  - `PlayOnSheet(playable, handOffTicks = current position, handOffStreams = session.currentStreams)`;
  - then a server command `POST /Sessions/{id}/Playing?playCommand=PlayNow&itemIds=…&startPositionTicks=…` with the audio and subtitle indexes; a paired Chromecast instead gets the Android-TV-remote wake plus the `jellycast://play?item=&start=<ms>&audio=&subtitle=` link;
  - on success, `nowPlaying.close()`.
- **`currentStreams`:** while transcoding, the transcode indexes (subtitle "off" when none); for direct play, the player tracks mapped back to server indexes.
- **TV → phone ("Continue on iPhone"** on the now-playing-on-TV screen):
  - resumes the playable at the TV's position with the TV's audio and subtitle indexes;
  - dismisses the screen, then about 0.5 s later calls `navigation.play(resumed, streams)`, which reaches `NowPlaying.play(…, streams:)`;
  - in parallel `remote.handBack`: stop the TV, and power off a paired Chromecast.
- **Entry points:** `AppNavigation.play(playable, streams?)`, `AppNavigation.play(queue)` and `NowPlaying.play` (from the info panel inside the player).
- **Warm-up:** `StreamCaches.warmUp(itemID)` when the movie detail or series page opens (it measures the connection). Optional on Android.

---

## 6. Strings (en → nl)

**Player controls and status**
| English | Dutch |
|---|---|
| Continue on… | Verder op… |
| Picture in Picture | Beeld-in-beeld |
| Audio and subtitles | Audio en ondertitels |
| Quality | Kwaliteit |
| Full screen | Volledig scherm |
| Exit full screen | Volledig scherm sluiten |
| Play or pause | Afspelen of pauzeren |
| Close | Sluiten |
| Position | Positie |
| Video | Video |
| Back 10 seconds | 10 seconden terug |
| Forward 10 seconds | 10 seconden vooruit |
| %lld s | %lld s |
| Auto · %@ | Auto · %@ |
| · %@ buffered | · %@ gebufferd |
| Waiting for the connection | Wachten op de verbinding |

**Segment pills**
| English | Dutch |
|---|---|
| Skip intro | Intro overslaan |
| Skip recap | Samenvatting overslaan |
| Skip preview | Voorproefje overslaan |
| Next episode · %@ | Volgende aflevering · %@ |
| Next · %@ %@ | Volgende · %1$@ %2$@ |
| Next · %@ | Volgende · %@ |
| Slow connection · %@ under the gear plays smoother | Trage verbinding · %@ onder het tandwiel speelt soepeler |
| The connection can't keep up with this file | De verbinding kan dit bestand niet bijhouden |

**Start overlay and errors**
| English | Dutch |
|---|---|
| Starts in %@ | Start over %@ |
| The Wi-Fi now does %lld Mbit/s and this file peaks at %lld. Loading ahead first, then it plays without stalling. | De wifi haalt nu %lld Mbit/s, dit bestand vraagt pieken tot %lld. We laden eerst vooruit, daarna loopt het zonder haperen. |
| Start now | Nu starten |
| Try again | Opnieuw proberen |
| Play via the server · %@ | Via de server afspelen · %@ |
| This item has no file the server can play. | Dit item heeft geen bestand dat de server kan afspelen. |
| This file doesn't play on this iPhone. | Dit bestand speelt niet af op deze iPhone. |
| Playback stopped because of an error in the file or the connection. | Het afspelen is gestopt door een fout in het bestand of de verbinding. |
| The player could not start. | De speler kon niet starten. |
| This item doesn't play | Dit item speelt niet af |

**Track sheet**
| English | Dutch |
|---|---|
| Audio | Audio |
| Subtitles | Ondertitels |
| Off | Uit |
| Forced | Geforceerd |
| Image | Beeld |
| Track %lld | Spoor %lld |

**Quality**
| English | Dutch |
|---|---|
| Auto | Auto |
| Auto, up to %@ | Auto, tot %@ |
| Original | Origineel |
| Measured %@ | Gemeten %@ |
| %@ Mbit/s | %@ Mbit/s |

**Info tabs and episodes**
| English | Dutch |
|---|---|
| Episodes | Afleveringen |
| Cast | Acteurs |
| Related | Verwant |
| Chat | Chat |
| Playing now | Speelt nu |
| Playing on %@ | Speelt op %@ |
| Watched | Gezien |
| %@ left | nog %@ |
| %lld min | %lld min |
| %lld h %lld min | %lld u %lld min |
| Special | Special |
| %lld seasons | %lld seizoen / %lld seizoenen (plural) |
| Mark as watched | Markeren als gezien |
| Mark as not watched | Markeren als niet gezien |
| Add to list… | Aan lijst toevoegen… |

**Cast and related**
| English | Dutch |
|---|---|
| The cast couldn't be loaded. | De cast kon niet worden geladen. |
| Guest stars | Gastrollen |
| All %lld actors | Alle %lld acteurs |
| All %lld actors and %lld guest stars | Alle %1$lld acteurs en %2$lld gastrollen |
| Makers | Makers |
| Directed by | Regie |
| Directed by · this episode | Regie · deze aflevering |
| Written by | Scenario |
| Written by · this episode | Scenario · deze aflevering |
| Produced by | Productie |
| Produced by · series | Productie · serie |
| as %@ | als %@ |
| %lld years | %lld jaar |
| From the same collection | Uit dezelfde reeks |
| Nothing related in the library. | Niets verwants in de bibliotheek. |

**Play on / continue on**
| English | Dutch |
|---|---|
| Continue %@ on | %@ verder op |
| Play %@ on | %@ afspelen op |
| Continues from %@ | Gaat verder vanaf %@ |
| This iPhone | Deze iPhone |
| Asleep · turns the TV on | Slaapt · zet de tv aan |
| JellyCast is open | JellyCast staat open |
| Playing something else now | Speelt nu iets anders |
| Your Chromecast can only be reached from your home network. | Je Chromecast is alleen vanaf je thuisnetwerk bereikbaar. |
| No TV can be reached now. Open JellyCast on a TV to play there. | Er is nu geen tv bereikbaar. Open JellyCast op een tv om daar af te spelen. |
| Is your TV missing? Open JellyCast on that TV. | Staat je tv er niet bij? Open JellyCast op die tv. |

**Two strings to adapt on Android:**
- "This file doesn't play on this iPhone." → for example "…on this phone".
- "This iPhone" → "This phone".

---

## 7. Where NOTES.md and the code disagree
- **Episodes window:** NOTES.md says "the one before and the next four"; the code uses 1 before and 6 after.
- **Double-tap seek:** NOTES.md first says it was left out. A later feedback round (2026-10-24) added it, and it is in the code.
- **Release-movie subtitles** stay in the server's metadata language. Overviews are not translated.
