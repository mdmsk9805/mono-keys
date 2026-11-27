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
        void setMode(String mode);      // en | bn_direct | sym
        void setShifted(boolean s);
        boolean shifted();
        String mode();
        void setActiveStyle(String s);
    }
    public interface Prefs {
        String get(String k, String def);
        void put(String k, String v);
    }

    private final Conn conn;
    private final Strip strip;
    private final Prefs prefs;
    private final ShortcutStore store; // may be null

    String bnMode = "phonetic";
    String style = "Normal";
    private String lastAlpha = "en";
    private final StringBuilder word = new StringBuilder();
    private final StringBuilder composing = new StringBuilder();
    private List<String> stripTriggers = new ArrayList<>();
    private boolean styleStripOpen = false;
    private boolean sensitive = false;

    public KeyController(Conn c, Strip s, Prefs p, ShortcutStore st) {
        conn = c; strip = s; prefs = p; store = st;
        bnMode = prefs.get("bn_mode", "phonetic");
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

    public void onStartInput(EditorInfo info) {
        sensitive = isSensitive(info);
        word.setLength(0);
        composing.setLength(0);
        styleStripOpen = false;
        updateCandidates();
    }

    private boolean banglaPhonetic() {
        return strip.mode().equals("bn_direct") && bnMode.equals("phonetic") && !sensitive;
    }

    private void setAlpha(String which) {
        lastAlpha = which;
        strip.setMode(which.equals("bn") ? "bn_direct" : "en");
        updateCandidates();
    }

    public void onKey(String text, int action) {
        InputConnection ic = conn.get();
        if (ic == null) return;
        switch (action) {
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
                ic.commitText(" ", 1);
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
    }

    private void type(InputConnection ic, String text) {
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
            if (!sensitive) word.append(Character.toLowerCase(c));
        } else {
            ic.commitText(text, 1);
            if (c != 0 && (Character.isLetter(c) || banglaChar) && !sensitive) word.append(Character.toLowerCase(c));
            else word.setLength(0);
        }
        if (strip.shifted() && !strip.mode().equals("bn_direct")) strip.setShifted(false);
    }

    private static boolean isRoman(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private void flush(InputConnection ic) {
        if (banglaPhonetic() && composing.length() > 0) {
            ic.commitText(BanglaTransliterator.convert(composing.toString()), 1);
            composing.setLength(0);
        }
        word.setLength(0);
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
        if (sensitive) {
            strip.show(out, false, false);
            return; // no previews, no composition hints, nothing learned
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
        if (banglaPhonetic() && composing.length() > 0) {
            String pick = index == 0 ? BanglaTransliterator.convert(composing.toString()) : composing.toString();
            ic.commitText(pick + " ", 1);
            composing.setLength(0);
        } else if (!sensitive && index < stripTriggers.size() && store != null) {
            String full = store.get(stripTriggers.get(index));
            if (full != null) {
                ic.deleteSurroundingText(word.length(), 0);
                ic.commitText(full, 1);
            }
            word.setLength(0);
        }
        updateCandidates();
    }
}
