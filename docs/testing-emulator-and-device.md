# Testing: emulator and Chromecast

What you test where, and how to start it. In short: **interface first in the emulator, video always on the Chromecast, and the final check always on the Chromecast.**

## What where

| What | Emulator | Chromecast |
|---|---|---|
| Layout, colors, text, safe area | ✅ first here | final check |
| D-pad, focus, Back, focus restore | ✅ first here | final check |
| Navigation between screens, loading/skeleton | ✅ first here | final check |
| Video playback, codecs, HDR, audio, subtitles | ❌ different codecs | ✅ always |
| Performance (stutter, startup, memory) | ❌ Mac is much faster | ✅ always, release build |
| "Does it really work?" before a commit or release | — | ✅ always |

Why: the emulator is fast to iterate with, but it does not have the same video decoders and is much faster than the Chromecast. Something that is smooth in the emulator can stutter on the device.

Even faster than the emulator: **Compose Previews** in Android Studio (open a `…Screen.kt`, the Design pane on the right). There you see a screen without installing, just without a D-pad.

## Starting the emulator

Set up once: emulator `Google_TV_1080p_API_34` (Google TV, Android 14, 1920×1080, 2 GB RAM).

**Android Studio:** Device Manager → ▶ next to `Google_TV_1080p_API_34`, select it as the device at the top, press Run.

**Terminal:**

```bash
emulator -avd Google_TV_1080p_API_34 &
```

Controls: arrow keys = D-pad, Enter = OK, Esc = Back. The remote control is also under ⋯ (Extended controls).

## Installing on the right device

If the emulator and the Chromecast are both connected, `./gradlew :tv:installDebug` installs on both. Pick one with `ANDROID_SERIAL`:

```bash
adb devices                                            # which devices are there?
ANDROID_SERIAL=emulator-5554 ./gradlew :tv:installDebug    # only the emulator
ANDROID_SERIAL=192.168.1.115:5555 ./gradlew :tv:installDebug   # only the Chromecast
```

Starting the app and taking a screenshot (works on both; add `-s <serial>` if there are more devices):

```bash
adb shell am start -n nl.jellycast.tv/.MainActivity
adb exec-out screencap -p > screen.png
```

## Connecting the Chromecast

```bash
adb connect 192.168.1.115:5555
```

The IP address can change; see Settings → System → About → Status on the Chromecast.

## Order per change

1. Build and check in Preview and the emulator: layout, focus, Back.
2. If it touches video: test on the Chromecast.
3. Final check on the Chromecast: does it work, does every key respond immediately.
4. Before a release: release build on the Chromecast and measure (see `product-principles.md` and `NOTES.md`).
