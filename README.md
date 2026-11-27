# Mono Keys

A lightweight, minimal Bangla + English keyboard for Android. No internet
permission, no accounts, no tracking. Nothing-inspired design with
dot-matrix (Doto) accents.

## Features

- **Bangla, two ways**: phonetic (type roman letters, get Bangla, Avro-style,
  simplified) or direct Bangla key layout with a shift layer for vowels and
  vowel signs (kar). Pick in the app.
- **English text styles**: bold, italic, bold-italic, script, double-struck,
  sans bold, mono. These are Unicode Mathematical Alphanumeric Symbols —
  real characters, so they work anywhere Unicode renders. English only;
  Bangla has no Unicode styled variants.
- **Saved text shortcuts**: save long text (email, ID numbers, status
  updates) behind a short trigger. Type the trigger, tap the suggestion.
  Values are stored AES/GCM-encrypted with a key in the Android Keystore.
- **Themes**: Mono Dark, Slate, Paper; red, teal, amber or white accent.
- **Private by construction**: no INTERNET permission, backups disabled.

## Honest limits

- The phonetic engine is a simplified Avro-like transliterator, not a full
  Avro port; uncommon clusters may differ.
- Snippet encryption is as strong as your lock screen: anyone past it can
  open the app. Uninstalling (or wiping the keystore) destroys the snippets.
- Styled English characters are Unicode symbols; a few apps/fonts may not
  render every glyph, and screen readers may read them letter by letter.

## Build

Standard Gradle build (`./gradlew assembleRelease`). Min SDK 26, target 34.

## Licenses

Code: MIT (see LICENSE). Bundled fonts Doto and Noto Sans Bengali are
SIL Open Font License 1.1 (full texts in licenses/).
