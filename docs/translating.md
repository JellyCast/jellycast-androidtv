# Translating

All texts of the app are in one file per language. Adding or improving a language takes one pull request, without touching Kotlin or Gradle.

## Where are the texts?

```
app/src/main/res/
  values/strings.xml          ← English, the default language (all keys are here)
  values/donottranslate.xml   ← texts that are the same in every language (4K, HDR10, OK …): do not translate
  values-es/strings.xml       ← Español
  values-fr/strings.xml       ← Français
  values-nl/strings.xml       ← Nederlands
```

Changing a text: find the key (e.g. `settings_logout`) in the file for that language and change the text between the tags.

## Adding a new language

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`. The code is the two-letter language code: `de` (German), `it` (Italian), `pt` (Portuguese). For a region: `values-pt-rBR` (Brazilian Portuguese).
2. Translate every text. Leave the keys (`name="…"`) as they are.
3. Done. After the next build the language appears automatically in **Settings → Language**, with its name in the language itself ("Deutsch"). There is no list you need to update: Gradle builds it from the `values-*` folders.

## What to watch for

- **Placeholders** such as `%1$s` (text) and `%1$d` (number) stay as they are; the app fills them in. Their order in the sentence may change, the number may not: `%2$s` stays `%2$s`.
- **Apostrophes and quotation marks** get a backslash: `l\'accueil`, `\"OK\"`. Write a `&` as `&amp;`.
- **Plurals** (`<plurals>`): give the forms your language has. Dutch and English: `one` and `other`; French and Spanish also `many` (large numbers); Polish or Russian have more (`few`, `many`). See the [Android documentation on quantity strings](https://developer.android.com/guide/topics/resources/string-resource#Plurals).
- **Length:** some buttons have a fixed width. The comment in `values/strings.xml` says where (e.g. "max. ±26 characters"). Text that is too long is shortened with "…".
- **Genres** (`genre_*`) are the genres the server provides in English (TMDb). Translate them the way they are called on streaming services in your language.
- Do not copy `donottranslate.xml`.

## Checking

```bash
./gradlew lintDebug
```

Lint fails if a text is missing in a language (`MissingTranslation`) or if it contains a key the default language does not have (`ExtraTranslation`), naming the key. A placeholder that does not match (`%1$d` where `%1$s` belongs) also gives an error.

Viewing it on the TV or emulator: choose the language in Settings, or without opening the app:

```bash
adb shell cmd locale set-app-locales nl.jellycast.tv --locales de
```

## What is not translated

Titles, descriptions and episode names come from the Jellyfin server, in the metadata language set there per library. Log lines and the debug screen (Afspeeltest) are in Dutch and are not translated. With a new language the app still starts in English; you choose it in Settings.
