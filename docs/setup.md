# Setup, installing and testing on the Chromecast

One-time tooling on the Mac, preparing the Chromecast, and building and installing the app. Day-to-day commands are in `CLAUDE.md`.

## Homebrew packages

```bash
brew install --cask android-studio
brew install scrcpy          # mirror/control the TV screen from your Mac (handy when testing)
brew install gradle          # only needed if Claude Code bootstraps the project from scratch
```

## First start of Android Studio

Go through the setup wizard with install type **Standard** and accept all licences. It installs the Android SDK in `~/Library/Android/sdk`.

Then install via **Settings → Languages & Frameworks → Android SDK**:

- **SDK Platforms:** only the newest stable API (that becomes `compileSdk`). A platform for API 34 isn't needed: minSdk only determines which devices the app runs on.
- **SDK Tools:** Android SDK Build-Tools, Android SDK Command-line Tools (latest), Android Emulator, Android SDK Platform-Tools.

## Shell configuration (`~/.zshrc`)

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin"
```

`$JAVA_HOME/bin` comes first in `PATH`, because otherwise macOS uses its own stub `java` in `/usr/bin`.

After `source ~/.zshrc`, check that everything works:

```bash
java -version
adb version
android sdk list        # the Android CLI; replaces the deprecated sdkmanager
```

## Emulator (optional, but handy)

In Android Studio go to **Device Manager → Create Virtual Device → TV → "Television (1080p)"** and choose a **Google TV** system image, API 34, ARM64.

Note: the emulator doesn't support hardware codecs like the real Chromecast. Always do video tests on the real device.

## Preparing the Chromecast for development

1. Go to **Settings → System → About → Android TV OS build** and click 7× to unlock the developer options.
2. In **Settings → System → Developer options** turn on:
   - **USB debugging**
   - **Wireless debugging**, if that option is there
3. Look up the IP address via **Settings → Network** and preferably give the Chromecast a fixed IP in your router.
4. Connect from your Mac:

```bash
adb connect <chromecast-ip>:5555
# The TV shows an "Allow debugging?" prompt → tick "Always allow".

# Doesn't work (Android 14 sometimes uses pairing)? Then use the pairing route:
# Developer options → Wireless debugging → "Pair device with pairing code"
adb pair <ip>:<pair-port>       # enter the code shown on the TV
adb connect <ip>:<port>         # the port shown under Wireless debugging

adb devices                     # the Chromecast must be in this list
```

## Jellyfin server

- Turn on **Quick Connect** via **Dashboard → General → Quick Connect**. The TV app uses it to sign in without typing a password.
- Note the server URL, for example `http://192.168.1.10:8096`.

## Building and installing

### During development

```bash
adb connect <chromecast-ip>:5555          # once per session
./gradlew installDebug                     # build + install
adb shell cmd package compile -m speed -f nl.jellycast.tv   # compile ahead of time
adb shell am force-stop nl.jellycast.tv     # restart with the compiled code
adb shell am start -n nl.jellycast.tv/.MainActivity   # open directly
adb logcat --pid=$(adb shell pidof nl.jellycast.tv)   # the app's logs

# Screenshot of the TV (Claude Code can look at this to check the UI)
adb exec-out screencap -p > screen.png

# Control from your Mac
scrcpy
adb shell input keyevent KEYCODE_DPAD_RIGHT   # simulate the D-pad (also for Claude Code)
```

Compile after every install: otherwise the app runs in the interpreter until overnight maintenance, with 2× as many janky frames (measured, see `NOTES.md`). Check: `adb shell dumpsys package dexopt | grep -A3 nl.jellycast.tv` shows `status=speed`.

### Installing the "final" version

1. Create your own release keystore once and keep it safe, outside git. With a different key you can't later update over the existing installation.
   ```bash
   keytool -genkey -v -keystore ~/keys/jelzefin.jks -alias jelzefin -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Let Claude Code read the signing config from `keystore.properties` (which is in `.gitignore`). The debug build also uses this key, so you can later switch to release without uninstalling.
3. Build and install:
   ```bash
   ./gradlew assembleRelease
   adb install -r app/build/outputs/apk/release/app-release.apk
   adb shell cmd package compile -m speed -f nl.jellycast.tv
   adb shell am force-stop nl.jellycast.tv
   ```
   The compile step makes the app smooth right away, not only after overnight maintenance.

### Alternative without cable or Mac: installing via a link

- Put the APK on your home server, for example via a simple web server or a shared folder reachable over HTTP.
- On the Chromecast install the **Downloader** app (from the Play Store) and give it permission to "install unknown apps".
- Open the URL to the APK in Downloader and choose install.
- Play Protect may show a warning. That is normal for self-built apps.
- Without adb you can't compile ahead of time. The app may then be a bit less smooth on the first day, until overnight maintenance compiles it.

### Optional later
A Google Play Console account costs a one-time $25. With it you can update via an internal test track like a normal app. For personal use that isn't needed.

