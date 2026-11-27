package com.mono.keys;

import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.List;

/** Input logic, separated from InputMethodService so it can be tested against
 *  a real editor-backed InputConnection (BaseInputConnection + EditText). */
public class KeyController {
    public interface Conn { InputConnection get(); }
    public interface Strip {
        void show(List<String> items, boolean isStylePicker, boolean banglaPhonetic);
        void setMode(String mode);      // en | bn_direct | bn_phon | sym | num
        void setShifted(boolean s);
        boolean shifted();
        String mode();
        void setActiveStyle(String s);
        default void setClips(List<String> c) { }
    }
    public interface Prefs {
        String get(String k, String def);
        void put(String k, String v);
    }

    private final Conn conn;
    private final Strip strip;
    private final Prefs prefs;
    private final ShortcutStore store; // may be null

    String bnMode = "direct";
    String style = "Normal";
    private WordDictionary dict, bnDict;
    private boolean karOpen = false, karToggleShown = false;
    static final String KAR_TOGGLE = "\u09BE \u09BF \u09C1 \u2026"; // া ি ু …
    public void setBanglaDictionary(WordDictionary d) { bnDict = d; }
    private List<String> dictSugs = new ArrayList<>();
    private boolean wordCap = false;
    public void setDictionary(WordDictionary d) { dict = d; }
    private String lastAlpha = "en";
    private final StringBuilder word = new StringBuilder();
    /** UTF-16 length of what was actually committed for each letter of word (styled letters are 2 chars). */
    private final List<Integer> wordLens = new ArrayList<>();
    private int shownLen() { int n = 0; for (int x : wordLens) n += x; return n; }
    private void resetWord() { word.setLength(0); wordLens.clear(); karOpen = false; }
    private String styledWord(String w) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            char ch = w.charAt(i);
            if (i == 0 && wordCap) ch = Character.toUpperCase(ch);
            String m = UnicodeStyles.map(ch, style);
            sb.append(m != null ? m : String.valueOf(ch));
        }
        return sb.toString();
    }
    private final StringBuilder composing = new StringBuilder();
    private List<String> stripTriggers = new ArrayList<>();
    private boolean styleStripOpen = false;
    private boolean sensitive = false;
    private char lastTyped = 0;
    private boolean lastWasSpace = false;
    private List<String> karMarks = new ArrayList<>();
    private List<String> punctChoices = new ArrayList<>();
    static final String[] KAR = {"া","ি","ী","ু","ূ","ৃ","ে","ৈ","ো","ৌ"};

    public KeyController(Conn c, Strip s, Prefs p, ShortcutStore st) {
        conn = c; strip = s; prefs = p; store = st;
        bnMode = prefs.get("bn_mode", "direct");
        style = prefs.get("style", "Normal");
        strip.setActiveStyle(style);
    }

    /** True for password/PIN/web-password/number-password fields. */
    public static boolean isSensitive(EditorInfo info) {
        if (info == null) return false;
        int t = info.inputType;
        int cls = t & EditorInfo.TYPE_MASK_CLASS;
        int var = t & EditorInfo.TYPE_MASK_VARIATION;
        if (cls == EditorInfo.TYPE_CLASS_TEXT) {
            if (var == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                    || var == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || var == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) return true;
        }
        if (cls == EditorInfo.TYPE_CLASS_NUMBER
                && var == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD) return true;
        return false;
    }

    // ---- clipboard history: memory only, never written to disk ----
    private final List<String> clips = new ArrayList<>();
    static final int MAX_CLIPS = 20, MAX_CLIP_CHARS = 2000;
    public void addClip(String t) {
        if (t == null || t.trim().isEmpty() || t.length() > MAX_CLIP_CHARS) return;
        clips.remove(t);
        clips.add(0, t);
        while (clips.size() > MAX_CLIPS) clips.remove(clips.size() - 1);
        if (strip.mode().equals("clip")) strip.setClips(new ArrayList<>(clips));
    }
    public void clearClips() { clips.clear(); strip.setClips(new ArrayList<String>()); }
    int clipCount() { return clips.size(); }

    public void onStartInput(EditorInfo info) {
        sensitive = isSensitive(info);
        if (strip.mode().equals("clip")) setAlpha(lastAlpha);
        String m = strip.mode();
        if (m.equals("bn_phon") && !bnMode.equals("phonetic")) strip.setMode("bn_direct");
        else if (m.equals("bn_direct") && bnMode.equals("phonetic")) strip.setMode("bn_phon");
        resetWord();
        composing.setLength(0);
        styleStripOpen = false;
        lastTyped = 0; lastWasSpace = false; punctChoices = new ArrayList<>();
        updateCandidates();
    }

    private boolean banglaPhonetic() {
        return strip.mode().equals("bn_phon") && !sensitive;
    }

    private void setAlpha(String which) {
        lastAlpha = which;
        strip.setMode(which.equals("bn") ? (bnMode.equals("phonetic") ? "bn_phon" : "bn_direct") : "en");
        updateCandidates();
    }

    public void onKey(String text, int action) {
        InputConnection ic = conn.get();
        if (ic == null) return;
        boolean wasSpace = lastWasSpace;
        lastWasSpace = false;
        punctChoices = new ArrayList<>();
        if (action != KeyboardView.A_TEXT) lastTyped = 0;
        switch (action) {
            case KeyboardView.A_BNMODE:
                bnMode = bnMode.equals("phonetic") ? "direct" : "phonetic";
                prefs.put("bn_mode", bnMode);
                composing.setLength(0);
                ic.finishComposingText();
                lastTyped = 0;
                setAlpha("bn");
                return;
            case KeyboardView.A_CLIP:
                if (!sensitive) { strip.setClips(new ArrayList<>(clips)); strip.setMode("clip"); updateCandidates(); }
                return;
            case KeyboardView.A_CLIPCLR:
                clearClips();
                return;
            case KeyboardView.A_CLIPPICK: {
                int i;
                try { i = Integer.parseInt(text); } catch (NumberFormatException e) { return; }
                if (!sensitive && i >= 0 && i < clips.size()) {
                    flush(ic);
                    ic.commitText(clips.get(i), 1);
                    resetWord();
                    lastTyped = 0;
                    setAlpha(lastAlpha);
                }
                return;
            }
            case KeyboardView.A_NONE:
                return;
            case KeyboardView.A_NUM:
                strip.setMode("num");
                updateCandidates();
                return;
            case KeyboardView.A_BACK:
                if (banglaPhonetic() && composing.length() > 0) {
                    composing.deleteCharAt(composing.length() - 1);
                    if (composing.length() == 0) ic.finishComposingText();
                    else ic.setComposingText(composing.toString(), 1);
                } else {
                    deleteOneCodePoint(ic);
                    shrinkWord(ic);
                }
                break;
            case KeyboardView.A_ENTER:
                flush(ic);
                ic.commitText("\n", 1);
                break;
            case KeyboardView.A_SHIFT:
                strip.setShifted(!strip.shifted());
                return;
            case KeyboardView.A_SYM:
                strip.setMode("sym");
                return;
            case KeyboardView.A_ABC:
                setAlpha(lastAlpha);
                return;
            case KeyboardView.A_LANG:
                setAlpha(lastAlpha.equals("en") ? "bn" : "en");
                return;
            case KeyboardView.A_STYLE:
                if (!sensitive) showStyles();
                return;
            case KeyboardView.A_SPACE:
                flush(ic);
                if (strip.shifted()) strip.setShifted(false); // space returns to the default layer
                if (wasSpace && !sensitive && !strip.mode().equals("num") && endsWithSpace(ic)) {
                    punctChoices = new ArrayList<>();
                    punctChoices.add(".");
                    punctChoices.add(",");
                    if (strip.mode().startsWith("bn")) punctChoices.add("।");
                    break; // no second space: offer dot / comma instead
                }
                ic.commitText(" ", 1);
                lastWasSpace = true;
                break;
            default:
                type(ic, text);
        }
        updateCandidates();
    }

    /** Deletes one user-perceived character (handles UTF-16 surrogate pairs). */
    static void deleteOneCodePoint(InputConnection ic) {
        CharSequence before = ic.getTextBeforeCursor(4, 0);
        int chars = 1;
        if (before != null && before.length() >= 2) {
            char last = before.charAt(before.length() - 1);
            char prev = before.charAt(before.length() - 2);
            if (Character.isLowSurrogate(last) && Character.isHighSurrogate(prev)) chars = 2;
        }
        ic.deleteSurroundingText(chars, 0);
    }

    private void shrinkWord(InputConnection ic) {
        if (word.length() == 0) return;
        int end = word.length() - 1;
        // styled letters are surrogate pairs: drop a full code point
        if (end >= 1 && Character.isLowSurrogate(word.charAt(end))
                && Character.isHighSurrogate(word.charAt(end - 1))) end--;
        word.delete(end, word.length());
        if (!wordLens.isEmpty()) wordLens.remove(wordLens.size() - 1);
    }

    private void type(InputConnection ic, String text) {
        karOpen = false;
        char c = text.length() == 1 ? text.charAt(0) : 0;
        boolean banglaChar = c != 0 && ((c >= 0x0980 && c <= 0x09FF));
        if (banglaPhonetic() && c != 0 && isRoman(c)) {
            composing.append(c);
            ic.setComposingText(composing.toString(), 1);
            return;
        }
        if (strip.mode().equals("en") && c != 0 && Character.isLetter(c) && c < 0x80) {
            String styled = sensitive ? null : UnicodeStyles.map(strip.shifted() ? Character.toUpperCase(c) : c, style);
            ic.commitText(styled != null ? styled : String.valueOf(c), 1);
            if (!sensitive) {
                if (word.length() == 0) wordCap = strip.shifted();
                word.append(Character.toLowerCase(c));
                wordLens.add((styled != null ? styled : String.valueOf(c)).length());
            }
        } else {
            ic.commitText(text, 1);
            if (c != 0 && (Character.isLetter(c) || banglaChar) && !sensitive) { word.append(Character.toLowerCase(c)); wordLens.add(1); }
            else resetWord();
        }
        lastTyped = (banglaChar && !sensitive) ? c : 0;
        if (strip.shifted()) strip.setShifted(false);
    }

    private static boolean endsWithSpace(InputConnection ic) {
        CharSequence b = ic.getTextBeforeCursor(1, 0);
        return b != null && b.length() == 1 && b.charAt(0) == ' ';
    }

    static boolean isBanglaConsonant(char c) {
        return (c >= 0x0995 && c <= 0x09B9) || c == 0x09DC || c == 0x09DD || c == 0x09DF;
    }

    private static boolean isRoman(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private void flush(InputConnection ic) {
        if (banglaPhonetic() && composing.length() > 0) {
            ic.commitText(BanglaTransliterator.convert(composing.toString()), 1);
            composing.setLength(0);
        }
        resetWord();
    }

    private void showStyles() {
        styleStripOpen = true;
        List<String> names = new ArrayList<>();
        for (String n : UnicodeStyles.NAMES) names.add(n);
        strip.show(names, true, false);
    }

    void updateCandidates() {
        List<String> out = new ArrayList<>();
        stripTriggers = new ArrayList<>();
        dictSugs = new ArrayList<>();
        if (sensitive) {
            strip.show(out, false, false);
            return; // no previews, no composition hints, nothing learned
        }
        karMarks = new ArrayList<>();
        if (!punctChoices.isEmpty()) {
            out.addAll(punctChoices);
            strip.show(out, false, false);
            return;
        }
        if (banglaPhonetic() && composing.length() > 0) {
            out.add(BanglaTransliterator.convert(composing.toString()));
            out.add(composing.toString());
        } else if (store != null && word.length() > 0) {
            List<String> hits = store.match(word.toString());
            for (int i = 0; i < hits.size() && i < 4; i++) {
                stripTriggers.add(hits.get(i));
                out.add(hits.get(i)); // trigger name only: saved values are never shown on the strip
            }
        }
        if (dict != null && strip.mode().equals("en") && word.length() > 0
                && out.size() < 4 && punctChoices.isEmpty()) {
            for (String w : dict.suggest(word.toString(), 4 - out.size())) {
                dictSugs.add(w);
                out.add(wordCap ? Character.toUpperCase(w.charAt(0)) + w.substring(1) : w);
            }
        }
        karToggleShown = false;
        boolean bnDirect = strip.mode().equals("bn_direct");
        if (bnDict != null && bnDirect && word.length() > 0 && !karOpen && out.size() < 4 && punctChoices.isEmpty()) {
            int room = isBanglaConsonant(lastTyped) ? 3 - out.size() : 4 - out.size();
            if (room > 0) for (String w : bnDict.suggest(word.toString(), room, false)) { dictSugs.add(w); out.add(w); }
            if (!dictSugs.isEmpty() && isBanglaConsonant(lastTyped)) { out.add(KAR_TOGGLE); karToggleShown = true; }
        }
        if (bnDirect && isBanglaConsonant(lastTyped) && (karOpen || out.isEmpty())) {
            out.clear(); dictSugs = new ArrayList<>(); stripTriggers = new ArrayList<>(); karToggleShown = false;
            for (String k : KAR) { karMarks.add(k); out.add(String.valueOf(lastTyped) + k); }
        }
        strip.show(out, false, banglaPhonetic() && composing.length() > 0);
    }

    public void onCandidate(int index) {
        InputConnection ic = conn.get();
        if (ic == null) return;
        if (styleStripOpen) {
            if (index >= 0 && index < UnicodeStyles.NAMES.length) {
                style = UnicodeStyles.NAMES[index];
                prefs.put("style", style);
                strip.setActiveStyle(style);
            }
            styleStripOpen = false;
            updateCandidates();
            return;
        }
        if (!punctChoices.isEmpty()) {
            if (index >= 0 && index < punctChoices.size()) {
                String p = punctChoices.get(index);
                if (endsWithSpace(ic)) ic.deleteSurroundingText(1, 0);
                ic.commitText(p + " ", 1);
            }
            punctChoices = new ArrayList<>();
            lastWasSpace = false; lastTyped = 0;
            updateCandidates();
            return;
        }
        if (karToggleShown && index == stripTriggers.size() + dictSugs.size()) {
            karOpen = true;
            updateCandidates();
            return;
        }
        if (!karMarks.isEmpty()) {
            if (index >= 0 && index < karMarks.size()) {
                String km = karMarks.get(index);
                ic.commitText(km, 1);
                word.append(km); wordLens.add(km.length()); // the word goes on, so suggestions can follow
            } else resetWord();
            karOpen = false;
            karMarks = new ArrayList<>();
            lastTyped = 0;
            updateCandidates();
            return;
        }
        if (banglaPhonetic() && composing.length() > 0) {
            String pick = index == 0 ? BanglaTransliterator.convert(composing.toString()) : composing.toString();
            ic.commitText(pick + " ", 1);
            composing.setLength(0);
        } else if (!sensitive && index < stripTriggers.size() && store != null) {
            String full = store.get(stripTriggers.get(index));
            if (full != null) {
                ic.deleteSurroundingText(shownLen(), 0);
                ic.commitText(full, 1);
            }
            resetWord();
        } else if (!sensitive && !dictSugs.isEmpty() && index >= stripTriggers.size()
                && index - stripTriggers.size() < dictSugs.size()) {
            String w = dictSugs.get(index - stripTriggers.size());
            ic.deleteSurroundingText(shownLen(), 0);
            ic.commitText((strip.mode().equals("bn_direct") ? w : styledWord(w)) + " ", 1);
            lastTyped = 0;
            resetWord();
        }
        updateCandidates();
    }
}
