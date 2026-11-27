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

## Direct Bangla layout
Letter positions follow a Probhat-style QWERTY arrangement (base layer, with the
shift layer shown as small corner labels). It is a layout read from a reference
screenshot, not a certified standard. Extras: contextual kar options after a
consonant, a permanent number row, a numbers-only pad (via ?123 then 1234), and
double-space to choose `.`, `,` or `।`.

## English suggestions
`app/src/main/assets/words_en.txt` is an offline list of 30,000 common English words, most frequent first. It is built from Peter Norvig's `count_1w.txt` word-frequency list (derived from the Google Web Trillion Word Corpus; Norvig publishes his code under MIT and the data on his ngrams page) filtered through the public-domain `words_alpha.txt` list from dwyl/english-words. Check those sources' terms yourself before reusing the list. Suggestions show on the strip: completions first, then one-edit spelling fixes. Nothing is ever changed unless you tap a suggestion. They are off in password fields and while a fancy text style is active. There is no learning of your own words, and no Bangla dictionary.

## Licenses

Code: MIT (see LICENSE). Bundled fonts Doto and Noto Sans Bengali are
SIL Open Font License 1.1 (full texts in licenses/).

## Fonts (not in this repository)
The two font files are not committed here. Before building, download them and
save as `app/src/main/assets/fonts/doto.ttf` (Doto, from
https://github.com/oliverlalan/Doto) and `app/src/main/assets/fonts/bengali.ttf`
(Noto Sans Bengali, from https://github.com/notofonts/bengali or Google Fonts).
Both are SIL OFL 1.1; license texts are in `licenses/`. If a font is missing the
app falls back to the system font.
