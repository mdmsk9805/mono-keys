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

Standard Gradle build (`gradle assembleRelease`). Min SDK 26, target 34.

Two sets of files are not in this repository because the GitHub web uploader cannot take them, so a fresh clone needs two extra steps before it matches the released APK:
1. Fonts: put Doto (`doto.ttf`) and a Bengali font (`bengali.ttf`) in `app/src/main/assets/fonts/` (see "Fonts" below). Without them the keyboard falls back to the system font.
2. English word list: download `en_50k.txt` and `words_alpha.txt` (links in `tools/build_wordlist.py`) into `tools/`, then run `python3 tools/build_wordlist.py` inside `tools/`. It writes `app/src/main/assets/words_en.txt`. Without it the keyboard still works but shows no English suggestions.

## Direct Bangla layout
Letter positions follow a Probhat-style QWERTY arrangement (base layer, with the
shift layer shown as small corner labels). It is a layout read from a reference
screenshot, not a certified standard. Extras: contextual kar options after a
consonant, a permanent number row, a numbers-only pad (via ?123 then 1234), and
double-space to choose `.`, `,` or `।`.

## English suggestions
`app/src/main/assets/words_en.txt` is an offline list of 30,000 English words, most frequent first. It is built by `tools/build_wordlist.py` from the OpenSubtitles-based frequency list in hermitdave/FrequencyWords (`en_50k.txt`, 2018), keeping only entries that are also in dwyl/english-words `words_alpha.txt` (public domain, used only as a spelling filter). The frequency content is CC BY-SA 4.0 (see `licenses/FrequencyWords-CC-BY-SA-4.0.txt`); it ships as a separate data file, and the app code stays MIT. If you redistribute the word list, keep that notice and the same licence. The list comes from film and TV subtitles, so it leans conversational and may contain a few odd entries.

Suggestions show on the strip: completions first, then one-edit spelling fixes. Nothing is ever changed unless you tap a suggestion. They work with the fancy text styles too (the suggested word is re-styled). They are off in password fields and in Bangla. No learning of your own words, no next-word prediction, no Bangla dictionary.

## Bangla suggestions
`app/src/main/assets/words_bn.txt` is a list of about 23,700 Bangla words, most frequent first, from hermitdave/FrequencyWords `bn_50k.txt` (2018, OpenSubtitles; CC BY-SA 4.0, see `licenses/FrequencyWords-CC-BY-SA-4.0.txt`), keeping entries seen at least 5 times that are pure Bangla letters. It comes from film and TV subtitles: conversational, with spelling variants and names, and it is missing many technical or newer words (for example ক্লিপবোর্ড). Coverage is real but modest. Suggestions appear in Direct Bangla mode: after a consonant you get up to 3 suggestions plus a chip with া ি ু … that switches the strip to the kar choices, so kar access is never lost. Tapping a suggestion replaces the whole typed word and adds a space. No spelling correction for Bangla, no learning, off in password fields. Regenerate with `tools/build_wordlist.py` (Bangla step documented there).

## Clipboard panel
?123 page, key "CLIP". Shows up to 20 recent copies (four per page; PREV and NEXT keys, page counter in the middle) made while this keyboard was running, newest first; tap one to insert it, "CLEAR" empties the list. The list lives only in memory: it is never written to disk, never leaves the phone, and disappears when Android stops the keyboard process (this can happen at any time). Clips Android marks as sensitive (API 33+) are skipped, texts over 2,000 characters are skipped, and the panel does not open in password fields. It only sees copies made after the keyboard started, plus the current clip when you open the panel.

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
