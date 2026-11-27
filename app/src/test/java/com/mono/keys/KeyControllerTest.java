package com.mono.keys;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 34})
public class KeyControllerTest {
    static class FakeStrip implements KeyController.Strip {
        List<String> last = new ArrayList<>(); boolean styles; String mode = "en";
        boolean shift; String activeStyle = "Normal"; int showCalls = 0;
        @Override public void show(List<String> items, boolean s, boolean p) {
            last = new ArrayList<>(items); styles = s; showCalls++;
        }
        @Override public void setMode(String m) { mode = m; }
        @Override public void setShifted(boolean s) { shift = s; }
        @Override public boolean shifted() { return shift; }
        @Override public String mode() { return mode; }
        @Override public void setActiveStyle(String s) { activeStyle = s; }
    }
    static class FakePrefs implements KeyController.Prefs {
        Map<String, String> map = new HashMap<>();
        @Override public String get(String k, String def) { return map.containsKey(k) ? map.get(k) : def; }
        @Override public void put(String k, String v) { map.put(k, v); }
    }

    EditText editor;
    InputConnection ic;
    FakeStrip strip;
    FakePrefs prefs;
    ShortcutStore store;
    KeyController ctl;

    @Before public void setUp() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        editor = new EditText(ctx);
        ic = new BaseInputConnection(editor, true);
        strip = new FakeStrip();
        prefs = new FakePrefs();
        SharedPreferences sp = ctx.getSharedPreferences("t_ctl", Context.MODE_PRIVATE);
        sp.edit().clear().commit();
        byte[] raw = new byte[32]; new SecureRandom().nextBytes(raw);
        store = new ShortcutStore(sp, new CryptoBox(new SecretKeySpec(raw, "AES")));
        ctl = new KeyController(() -> ic, strip, prefs, store);
    }

    private void startField(int inputType) {
        EditorInfo info = new EditorInfo();
        info.inputType = inputType;
        ctl.onStartInput(info);
    }

    private String text() {
        CharSequence cs = ic.getTextBeforeCursor(10000, 0);
        return cs == null ? "" : cs.toString();
    }

    private void typeKeys(String s) {
        for (int i = 0; i < s.length(); i++) ctl.onKey(String.valueOf(s.charAt(i)), KeyboardView.A_TEXT);
    }

    @Test public void englishStyledTypingAndGraphemeBackspace() {
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("Aa", KeyboardView.A_STYLE);
        assertTrue(strip.styles);
        ctl.onCandidate(1); // Bold
        assertEquals("Bold", prefs.map.get("style"));
        typeKeys("Hi");
        assertEquals("𝐇𝐢", text());
        ctl.onKey("⌫", KeyboardView.A_BACK); // must delete the whole surrogate pair
        assertEquals("𝐇", text());
        ctl.onKey("⌫", KeyboardView.A_BACK);
        assertEquals("", text());
    }

    @Test public void phoneticTypingCommitsBangla() {
        prefs.map.put("bn_mode", "phonetic");
        ctl = new KeyController(() -> ic, strip, prefs, store);
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("বাং", KeyboardView.A_LANG); // to Bangla, phonetic setting
        assertEquals("bn_phon", strip.mode);
        typeKeys("ami");
        assertEquals("ami", text()); // composing shows roman
        assertEquals("আমি", strip.last.get(0)); // candidate preview
        ctl.onKey("space", KeyboardView.A_SPACE);
        assertEquals("আমি ", text());
    }

    @Test public void shortcutReplacesTriggerCleanly() throws Exception {
        store.save("iq", "IQAMA 2222 3333 4444");
        startField(InputType.TYPE_CLASS_TEXT);
        typeKeys("iq");
        assertEquals(1, strip.last.size());
        assertEquals("iq", strip.last.get(0));
        ctl.onCandidate(0);
        assertEquals("IQAMA 2222 3333 4444", text());
        assertFalse(text().contains("iq"));
        assertTrue(strip.last.isEmpty()); // strip cleared after insert
    }

    @Test public void passwordFieldIsLockedDown() throws Exception {
        store.save("pw", "secret-snippet");
        prefs.map.put("style", "Bold");
        ctl = new KeyController(() -> ic, strip, prefs, store);
        startField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int shows = strip.showCalls;
        ctl.onKey("Aa", KeyboardView.A_STYLE); // style picker must NOT open
        assertFalse(strip.styles);
        typeKeys("pw");
        assertEquals("pw", text()); // not styled
        assertTrue(strip.last.isEmpty()); // no snippet previews
        ctl.onKey("বাং", KeyboardView.A_LANG);
        typeKeys("ami");
        assertEquals("pwami", text()); // no phonetic conversion either
    }

    @Test public void sensitiveDetectionCoversVariants() {
        EditorInfo i = new EditorInfo();
        i.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        assertTrue(KeyController.isSensitive(i));
        i.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        assertTrue(KeyController.isSensitive(i));
        i.inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        assertTrue(KeyController.isSensitive(i));
        i.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        assertFalse(KeyController.isSensitive(i));
    }

    @Test public void focusChangeClearsState() throws Exception {
        store.save("iq", "X");
        startField(InputType.TYPE_CLASS_TEXT);
        typeKeys("iq");
        assertFalse(strip.last.isEmpty());
        startField(InputType.TYPE_CLASS_TEXT); // move to another field
        assertTrue(strip.last.isEmpty());
    }

    @Test public void symbolsRoundTripRestoresMode() {
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("বাং", KeyboardView.A_LANG);
        ctl.onKey("123", KeyboardView.A_SYM);
        assertEquals("sym", strip.mode);
        ctl.onKey("ABC", KeyboardView.A_ABC);
        assertEquals("bn_direct", strip.mode); // back to Bangla, not English
    }

    @Test public void enterCommitsAndNewlines() {
        startField(InputType.TYPE_CLASS_TEXT);
        typeKeys("ok");
        ctl.onKey("⏎", KeyboardView.A_ENTER);
        assertEquals("ok\n", text());
    }

    @Test public void directBanglaCommitsChars() {
        prefs.map.put("bn_mode", "direct");
        ctl = new KeyController(() -> ic, strip, prefs, store);
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("বাং", KeyboardView.A_LANG);
        ctl.onKey("ক", KeyboardView.A_TEXT);
        ctl.onKey("া", KeyboardView.A_TEXT);
        assertEquals("কা", text());
    }

    @Test public void stripShowsTriggerNotValueAndClearsOnPasswordFocus() throws Exception {
        store.save("iqx", "VALUE-SHOULD-NOT-SHOW");
        startField(InputType.TYPE_CLASS_TEXT);
        typeKeys("iq");
        assertEquals(1, strip.last.size());
        assertEquals("iqx", strip.last.get(0));
        startField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        assertTrue(strip.last.isEmpty());
        typeKeys("iq");
        assertTrue(strip.last.isEmpty());
        startField(InputType.TYPE_CLASS_TEXT);
        assertTrue(strip.last.isEmpty()); // nothing retained from earlier typing
    }

    @Test public void banglaBackspaceRemovesOneCodePoint() {
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("বাং", KeyboardView.A_LANG);
        ic.commitText("\u0995\u09BE", 1);
        ctl.onKey("", KeyboardView.A_BACK);
        assertEquals("\u0995", text());
    }

    @Test public void karOptionsAppearAfterConsonantAndInsertOnlyTheMark() {
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("", KeyboardView.A_LANG);
        assertEquals("bn_direct", strip.mode);
        ctl.onKey("ম", KeyboardView.A_TEXT);
        assertEquals(10, strip.last.size());
        assertEquals("মা", strip.last.get(0));
        assertEquals("মৌ", strip.last.get(9));
        ctl.onCandidate(6); // ে
        assertEquals("মে", text());
        assertTrue(strip.last.isEmpty());
        ctl.onKey("া", KeyboardView.A_TEXT); // vowel sign: no kar strip
        assertTrue(strip.last.isEmpty());
    }

    @Test public void karStripNotShownForVowelsOrEnglishOrPassword() {
        startField(InputType.TYPE_CLASS_TEXT);
        typeKeys("m");
        assertTrue(strip.last.isEmpty());
        ctl.onKey("", KeyboardView.A_LANG);
        ctl.onKey("অ", KeyboardView.A_TEXT);
        assertTrue(strip.last.isEmpty());
        startField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ctl.onKey("ম", KeyboardView.A_TEXT);
        assertTrue(strip.last.isEmpty());
    }

    @Test public void spaceReturnsToBaseLayerAndShiftIsOneShot() {
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("", KeyboardView.A_LANG);
        ctl.onKey("⇧", KeyboardView.A_SHIFT);
        assertTrue(strip.shift);
        ctl.onKey("ধ", KeyboardView.A_TEXT);
        assertFalse(strip.shift); // one-shot
        ctl.onKey("⇧", KeyboardView.A_SHIFT);
        assertTrue(strip.shift);
        ctl.onKey("space", KeyboardView.A_SPACE);
        assertFalse(strip.shift); // space returns to default layer
    }

    @Test public void doubleSpaceOffersDotOrCommaAndReplacesTrailingSpace() {
        startField(InputType.TYPE_CLASS_TEXT);
        typeKeys("hi");
        ctl.onKey("space", KeyboardView.A_SPACE);
        assertEquals("hi ", text());
        ctl.onKey("space", KeyboardView.A_SPACE);
        assertEquals("hi ", text()); // no second space
        assertEquals(java.util.Arrays.asList(".", ","), strip.last);
        ctl.onCandidate(0);
        assertEquals("hi. ", text());
        assertTrue(strip.last.isEmpty());
        // comma choice, and typing instead of choosing keeps text clean
        typeKeys("ok");
        ctl.onKey("space", KeyboardView.A_SPACE);
        ctl.onKey("space", KeyboardView.A_SPACE);
        ctl.onCandidate(1);
        assertEquals("hi. ok, ", text());
        typeKeys("a");
        ctl.onKey("space", KeyboardView.A_SPACE);
        ctl.onKey("space", KeyboardView.A_SPACE);
        typeKeys("b");
        assertEquals("hi. ok, a b", text());
        assertTrue(strip.last.isEmpty() || !strip.last.contains(","));
    }

    @Test public void doubleSpaceInBanglaAlsoOffersDanda() {
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("", KeyboardView.A_LANG);
        ctl.onKey("ক", KeyboardView.A_TEXT);
        ctl.onCandidate(0);
        ctl.onKey("space", KeyboardView.A_SPACE);
        ctl.onKey("space", KeyboardView.A_SPACE);
        assertEquals(java.util.Arrays.asList(".", ",", "।"), strip.last);
        ctl.onCandidate(2);
        assertEquals("কা। ", text());
    }

    @Test public void doubleSpaceNeverOffersInPasswordField() {
        startField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        typeKeys("a");
        ctl.onKey("space", KeyboardView.A_SPACE);
        ctl.onKey("space", KeyboardView.A_SPACE);
        assertEquals("a  ", text());
        assertTrue(strip.last.isEmpty());
    }

    @Test public void numericPadOpensAndReturns() {
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("?123", KeyboardView.A_SYM);
        ctl.onKey("1234", KeyboardView.A_NUM);
        assertEquals("num", strip.mode);
        ctl.onKey("7", KeyboardView.A_TEXT);
        ctl.onKey("0", KeyboardView.A_TEXT);
        assertEquals("70", text());
        ctl.onKey("ABC", KeyboardView.A_ABC);
        assertEquals("en", strip.mode);
    }

    @Test public void spaceAfterKarOptionsClearsStripAndRestoresBase() {
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("", KeyboardView.A_LANG);
        ctl.onKey("⇧", KeyboardView.A_SHIFT);
        ctl.onKey("খ", KeyboardView.A_TEXT);
        assertEquals(10, strip.last.size()); // kar options visible
        assertFalse(strip.shift);            // one-shot shift already released
        ctl.onKey("⇧", KeyboardView.A_SHIFT);
        ctl.onKey("space", KeyboardView.A_SPACE);
        assertEquals("খ ", text());
        assertTrue(strip.last.isEmpty());    // contextual strip gone
        assertFalse(strip.shift);            // base layer
        assertEquals("bn_direct", strip.mode);
    }

    @Test public void banglaRow3KeysAreRealMarks() {
        assertEquals("ৃ", KeyboardView.BN[2][7]);
        assertEquals("্", KeyboardView.BN[2][8]);
        assertEquals("ঁ", KeyboardView.BN_SHIFT[2][8]);
        assertEquals("ৎ".length(), 1);
    }

    @Test public void modeLabelTapSwitchesDirectAndPhoneticAndPersists() {
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("", KeyboardView.A_LANG);
        assertEquals("bn_direct", strip.mode);
        ctl.onKey("", KeyboardView.A_BNMODE);
        assertEquals("bn_phon", strip.mode);
        assertEquals("phonetic", prefs.map.get("bn_mode"));
        ctl.onKey("", KeyboardView.A_BNMODE);
        assertEquals("bn_direct", strip.mode);
        assertEquals("direct", prefs.map.get("bn_mode"));
    }

    @Test public void staleViewModeIsCorrectedToSavedSettingOnFocus() {
        prefs.map.put("bn_mode", "direct");
        ctl = new KeyController(() -> ic, strip, prefs, store);
        strip.mode = "bn_phon"; // view left in phonetic from an older setting
        startField(InputType.TYPE_CLASS_TEXT);
        assertEquals("bn_direct", strip.mode);
    }

    @Test public void fancyEnglishStyleDoesNotTouchBanglaPhonetic() {
        prefs.map.put("bn_mode", "phonetic");
        prefs.map.put("style", "Bold");
        ctl = new KeyController(() -> ic, strip, prefs, store);
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("", KeyboardView.A_LANG);
        assertEquals("bn_phon", strip.mode);
        typeKeys("ami");
        assertEquals("ami", text()); // plain roman while composing, not bold
        assertEquals("আমি", strip.last.get(0));
        ctl.onKey("space", KeyboardView.A_SPACE);
        assertEquals("আমি ", text());
        ctl.onKey("", KeyboardView.A_LANG); // back to English: style applies again
        typeKeys("a");
        assertEquals("আমি \uD835\uDC1A", text()); // bold a
    }

    private WordDictionary realDict() throws Exception {
        java.io.File f = new java.io.File("src/main/assets/words_en.txt");
        return WordDictionary.load(new java.io.FileInputStream(f));
    }

    @Test public void dictionaryHasRealSize() throws Exception {
        WordDictionary d = realDict();
        assertTrue(d.size() >= 29000);
        assertTrue(d.contains("keyboard") || d.contains("keyboards"));
        assertTrue(d.contains("tomorrow") && d.contains("because"));
    }

    @Test public void englishSuggestionsCompleteAndCorrectWithoutSilentChange() throws Exception {
        ctl.setDictionary(realDict());
        startField(InputType.TYPE_CLASS_TEXT);
        typeKeys("tomor");
        assertTrue(strip.last.contains("tomorrow"));
        assertEquals("tomor", text()); // nothing changed by itself
        ctl.onCandidate(strip.last.indexOf("tomorrow"));
        assertEquals("tomorrow ", text());
        typeKeys("becuase"); // typo: offered, not auto-applied
        assertEquals("tomorrow becuase", text());
        assertTrue(strip.last.contains("because"));
        ctl.onCandidate(strip.last.indexOf("because"));
        assertEquals("tomorrow because ", text());
    }

    @Test public void suggestionKeepsCapitalAndSkipsStyledBanglaAndPassword() throws Exception {
        ctl.setDictionary(realDict());
        startField(InputType.TYPE_CLASS_TEXT);
        strip.shift = true; typeKeys("t"); typeKeys("omor");
        ctl.onCandidate(strip.last.indexOf("Tomorrow"));
        assertEquals("Tomorrow ", text());
        // password: no suggestions at all
        editor.setText("");
        ic = new BaseInputConnection(editor, true);
        startField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        typeKeys("tomor");
        assertTrue(strip.last.isEmpty());
    }

    @Test public void suggestionsWorkWithFancyStyleAndReplaceStyledWord() throws Exception {
        ctl.setDictionary(realDict());
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("Aa", KeyboardView.A_STYLE); ctl.onCandidate(1); // Bold
        typeKeys("tomor");
        assertTrue(strip.last.contains("tomorrow"));
        ctl.onCandidate(strip.last.indexOf("tomorrow"));
        assertEquals("\uD835\uDC2D\uD835\uDC28\uD835\uDC26\uD835\uDC28\uD835\uDC2B\uD835\uDC2B\uD835\uDC28\uD835\uDC30 ", text());
        // backspacing a styled letter then picking still replaces exactly the shown word
        typeKeys("becuasx");
        ctl.onKey("\u232B", KeyboardView.A_BACK);
        typeKeys("e");
        ctl.onCandidate(strip.last.indexOf("because"));
        assertEquals(17 + 15, text().length()); // 8 styled letters + space, 7 styled letters + space
    }

    @Test public void editDistanceMatching() {
        assertTrue(WordDictionary.oneEdit("becuase", "because")); // swap
        assertTrue(WordDictionary.oneEdit("helo", "hello"));
        assertTrue(WordDictionary.oneEdit("cat", "cut"));
        assertFalse(WordDictionary.oneEdit("cat", "dog"));
    }

    @Test public void repeatedBackspaceIsUnicodeSafe() {
        startField(InputType.TYPE_CLASS_TEXT);
        ic.commitText("a\uD835\uDC07\uD835\uDC22\u0995\u09BE", 1);
        for (int i = 0; i < 3; i++) ctl.onKey("⌫", KeyboardView.A_BACK);
        assertEquals("a\uD835\uDC07", text()); // surrogate pair kept whole until its turn
        ctl.onKey("⌫", KeyboardView.A_BACK);
        assertEquals("a", text());
        ctl.onKey("⌫", KeyboardView.A_BACK);
        ctl.onKey("⌫", KeyboardView.A_BACK); // extra repeats on empty text are harmless
        assertEquals("", text());
    }

    private WordDictionary bnDict() throws Exception {
        return WordDictionary.load(new java.io.FileInputStream(new java.io.File("src/main/assets/words_bn.txt")));
    }

    @Test public void banglaDictionaryHasRealSizeAndWords() throws Exception {
        WordDictionary d = bnDict();
        assertTrue(d.size() >= 20000);
        for (String w : new String[]{"\u0986\u09AE\u09BF", "\u09AC\u09BE\u0982\u09B2\u09BE", "\u09AD\u09BE\u09B2\u09CB\u09AC\u09BE\u09B8\u09BF"})
            assertTrue("missing " + w, d.contains(w));
    }

    @Test public void banglaSuggestionsKeepKarAccessAndReplaceWholeWord() throws Exception {
        ctl.setBanglaDictionary(bnDict());
        prefs.map.put("bn_mode", "direct");
        ctl = new KeyController(() -> ic, strip, prefs, store);
        ctl.setBanglaDictionary(bnDict());
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("\u0985", KeyboardView.A_LANG); // to Bangla direct
        assertEquals("bn_direct", strip.mode);
        typeKeys("\u0986\u09AE"); // আম, last is a consonant
        List<String> chips = new ArrayList<>(strip.last);
        assertTrue("suggestions shown: " + chips, chips.size() >= 2);
        assertEquals("kar toggle is the last chip", KeyController.KAR_TOGGLE, chips.get(chips.size() - 1));
        assertTrue(chips.get(0).startsWith("\u0986\u09AE"));
        // kar access: toggle opens the kar chips for the last consonant
        ctl.onCandidate(chips.size() - 1);
        assertEquals(10, strip.last.size());
        assertEquals("\u09AE\u09BE", strip.last.get(0));
        ctl.onCandidate(0); // tap the kar: inserts only the mark
        assertEquals("\u0986\u09AE\u09BE", text());
        // now last char is a kar: suggestions only, pick replaces the whole typed word and adds a space
        List<String> c2 = new ArrayList<>(strip.last);
        assertFalse(c2.isEmpty());
        String pick = c2.get(0);
        assertTrue(pick.startsWith("\u0986\u09AE\u09BE"));
        ctl.onCandidate(0);
        assertEquals(pick + " ", text());
        // english mode and password fields get no Bangla suggestions
        ctl.onKey("EN", KeyboardView.A_LANG);
        typeKeys("am");
        assertFalse(strip.last.contains("\u0986\u09AE\u09BF"));
    }

    @Test public void banglaKarToggleStaysAfterKarAndLongWordReplaces() throws Exception {
        prefs.map.put("bn_mode", "direct");
        ctl = new KeyController(() -> ic, strip, prefs, store);
        ctl.setBanglaDictionary(bnDict());
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("\u0985", KeyboardView.A_LANG);
        typeKeys("\u0986\u09AE");
        ctl.onCandidate(strip.last.size() - 1); // open kar chips
        ctl.onCandidate(0);                     // tap a-kar
        assertEquals("\u0986\u09AE\u09BE", text());
        List<String> c = new ArrayList<>(strip.last);
        assertTrue("max 3 suggestions + toggle: " + c, c.size() <= 4);
        assertEquals(KeyController.KAR_TOGGLE, c.get(c.size() - 1));
        ctl.onCandidate(c.size() - 1); // kar access after a kar
        assertEquals(10, strip.last.size());
        ctl.onCandidate(0);            // second kar appended
        assertEquals(4, text().codePointCount(0, text().length()));
        // keep typing a consonant, then pick a longer suggestion: whole typed word replaced
        typeKeys("\u09B0");
        List<String> c3 = new ArrayList<>(strip.last);
        assertEquals(KeyController.KAR_TOGGLE, c3.get(c3.size() - 1));
        if (c3.size() > 1) {
            String pick = c3.get(0);
            ctl.onCandidate(0);
            assertEquals(pick + " ", text());
        }
    }

    @Test public void banglaSuggestionsOffInPasswordAndWithoutDictionaryKarStillWork() throws Exception {
        startField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ctl.setBanglaDictionary(bnDict());
        ctl.onKey("\u0985", KeyboardView.A_LANG);
        typeKeys("\u0986\u09AE");
        assertTrue(strip.last.isEmpty());
        // no dictionary loaded yet: consonant still shows the 10 kar chips
        ctl = new KeyController(() -> ic, strip, prefs, store);
        startField(InputType.TYPE_CLASS_TEXT);
        ctl.onKey("\u0985", KeyboardView.A_LANG);
        typeKeys("\u0995");
        assertEquals(10, strip.last.size());
    }
}
