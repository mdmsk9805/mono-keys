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
}
