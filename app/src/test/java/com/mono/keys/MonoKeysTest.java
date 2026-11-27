package com.mono.keys;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 34})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MonoKeysTest {

    // ---------- transliteration ----------
    @Test public void phoneticCommonWords() {
        assertEquals("আমি", BanglaTransliterator.convert("ami"));
        assertEquals("তুমি", BanglaTransliterator.convert("tumi"));
        assertEquals("বাংলা", BanglaTransliterator.convert("bangla"));
        assertEquals("ভালো", BanglaTransliterator.convert("bhalO"));
        assertEquals("এখন", BanglaTransliterator.convert("ekhon"));
        assertEquals("কথা", BanglaTransliterator.convert("kotha"));
        assertEquals("ঢাকা", BanglaTransliterator.convert("Dhaka"));
        assertEquals("শব", BanglaTransliterator.convert("shob"));
    }

    @Test public void phoneticClustersAndKar() {
        assertEquals("কি", BanglaTransliterator.convert("ki"));
        assertEquals("কী", BanglaTransliterator.convert("kI"));
        assertEquals("কু", BanglaTransliterator.convert("ku"));
        assertEquals("কে", BanglaTransliterator.convert("ke"));
        assertEquals("কো", BanglaTransliterator.convert("kO"));
        assertEquals("স্ত্রী", BanglaTransliterator.convert("strI"));
        assertEquals("।", BanglaTransliterator.convert(".."));
    }

    // ---------- unicode styles ----------
    @Test public void stylesMapLettersAndDigits() {
        assertEquals("𝐚", UnicodeStyles.map('a', "Bold"));
        assertEquals("𝐀", UnicodeStyles.map('A', "Bold"));
        assertEquals("𝟓", UnicodeStyles.map('5', "Bold"));
        assertEquals("ℎ", UnicodeStyles.map('h', "Italic")); // letterlike exception
        assertEquals("𝚣", UnicodeStyles.map('z', "Mono"));
        assertNull(UnicodeStyles.map('a', "Normal"));
        assertNull(UnicodeStyles.map('!', "Bold"));
        assertEquals("𝐇𝐢", UnicodeStyles.convert("Hi", "Bold"));
    }

    // ---------- crypto + shortcuts ----------
    private CryptoBox softwareBox() throws Exception {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return new CryptoBox(new SecretKeySpec(raw, "AES"));
    }

    @Test public void cryptoRoundTrips() throws Exception {
        CryptoBox box = softwareBox();
        String enc = box.encrypt("IQ-1234-5678 আমার");
        assertNotEquals(enc, "IQ-1234-5678 আমার");
        assertEquals("IQ-1234-5678 আমার", box.decrypt(enc));
        assertNotEquals(box.encrypt("x"), box.encrypt("x")); // random IV
    }

    @Test public void shortcutStoreMatches() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        SharedPreferences p = ctx.getSharedPreferences("t_shortcuts", Context.MODE_PRIVATE);
        p.edit().clear().commit();
        ShortcutStore store = new ShortcutStore(p, softwareBox());
        store.save("iq", "IQAMA 2222 3333 4444");
        store.save("em", "me@example.com");
        List<String> hits = store.match("i");
        assertTrue(hits.contains("iq"));
        assertEquals("IQAMA 2222 3333 4444", store.get("iq"));
        store.delete("iq");
        assertNull(store.get("iq"));
        assertFalse(store.match("i").contains("iq"));
    }

    // ---------- keyboard raster proof (phone density: 360dp wide, 3.0 density) ----------
    @Test public void keyboardRendersRealPixels() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getResources().getDisplayMetrics().density = 3.0f;
        ctx.getResources().getDisplayMetrics().densityDpi = 480;
        ctx.getResources().getDisplayMetrics().scaledDensity = 3.0f;
        render(ctx, "en", false, false, null, "kb_en.png");
        render(ctx, "bn_direct", false, false, null, "kb_bn.png");
        render(ctx, "bn_direct", true, false, null, "kb_bn_shift.png");
        render(ctx, "en", false, false, java.util.Arrays.asList("আমি", "ami"), "kb_phonetic.png");
        render(ctx, "en", false, false, java.util.Arrays.asList("iq: IQAMA 2222 3333 4…", "em: me@example.com"), "kb_shortcut.png");
        render(ctx, "en", false, true, null, "kb_styles.png");
        render(ctx, "sym", false, false, null, "kb_sym.png");
        render(ctx, "bn_phon", false, false, null, "kb_bn_phon.png");
        render(ctx, "num", false, false, null, "kb_num.png");
        List<String> kar = new java.util.ArrayList<>();
        for (String k : KeyController.KAR) kar.add("ম" + k);
        render(ctx, "bn_direct", false, false, kar, "kb_bn_kar.png");
        render(ctx, "en", false, false, java.util.Arrays.asList(".", ","), "kb_punct.png");
    }

    @Test public void settingsScreenRenders() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getResources().getDisplayMetrics().density = 3.0f;
        ctx.getResources().getDisplayMetrics().densityDpi = 480;
        ctx.getResources().getDisplayMetrics().scaledDensity = 3.0f;
        android.content.SharedPreferences sp = ctx.getSharedPreferences("t_settings", Context.MODE_PRIVATE);
        sp.edit().clear().commit();
        byte[] raw = new byte[32]; new SecureRandom().nextBytes(raw);
        ShortcutStore store = new ShortcutStore(sp, new CryptoBox(new SecretKeySpec(raw, "AES")));
        store.save("iq", "IQAMA 2222 3333 4444");
        SettingsActivity.testStore = store;
        SettingsActivity act = org.robolectric.Robolectric.buildActivity(SettingsActivity.class).setup().get();
        android.view.View root = act.getWindow().getDecorView();
        int w = 1080;
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2208, android.view.View.MeasureSpec.AT_MOST));
        root.layout(0, 0, w, root.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(w, Math.max(root.getMeasuredHeight(), 1), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bmp));
        int lit = 0;
        for (int y = 0; y < bmp.getHeight(); y += 3)
            for (int x = 0; x < w; x += 3)
                if (bmp.getPixel(x, y) != 0xFF000000) lit++;
        assertTrue("settings render empty", lit > 2000);
        File out = new File("build/test-renders/settings.png");
        out.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(out);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        SettingsActivity.testStore = null;
    }

    private static boolean fontsReady = false;

    private void loadRealFonts(Context ctx) throws Exception {
        if (fontsReady) return;
        KeyboardView.overrideDoto = fontFromAsset(ctx, "fonts/doto.ttf", "doto_test");
        KeyboardView.overrideBengali = fontFromAsset(ctx, "fonts/bengali.ttf", "bengali_test");
        fontsReady = true;
    }

    private android.graphics.Typeface fontFromAsset(Context ctx, String asset, String tmpName) throws Exception {
        File disk = new File("build/intermediates/assets/release/mergeReleaseAssets/" + asset);
        if (!disk.exists()) disk = new File("src/main/assets/" + asset);
        java.io.InputStream in = new java.io.FileInputStream(disk);
        File f = File.createTempFile(tmpName, ".ttf");
        FileOutputStream fos = new FileOutputStream(f);
        byte[] buf = new byte[8192]; int n;
        while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        fos.close(); in.close();
        return android.graphics.Typeface.createFromFile(f);
    }

    private void render(Context ctx, String mode, boolean shift, boolean styles, List<String> cands, String name) throws Exception {
        loadRealFonts(ctx);
        KeyboardView kb = new KeyboardView(ctx, null);
        kb.setTheme(KeyboardView.themeFor("mono", 0));
        kb.setMode(mode);
        if (shift) kb.setShifted(true);
        if (cands != null) kb.setCandidates(cands, styles);
        else if (styles) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String n : UnicodeStyles.NAMES) names.add(n);
            kb.setCandidates(names, true);
        }
        int w = 1080;
        kb.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        kb.layout(0, 0, w, kb.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(w, kb.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        kb.draw(c);
        // assert: many key pixels drawn (non-black)
        int lit = 0;
        for (int y = 0; y < bmp.getHeight(); y += 3)
            for (int x = 0; x < w; x += 3)
                if (bmp.getPixel(x, y) != 0xFF000000) lit++;
        assertTrue("expected lit pixels in " + name + ", got " + lit, lit > 2000);
        File out = new File("build/test-renders/" + name);
        out.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(out);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
    }

    @Test public void longPressInsertsAlternates() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getResources().getDisplayMetrics().density = 3.0f;
        loadRealFonts(ctx);
        KeyboardView kb = new KeyboardView(ctx, null);
        kb.setMode("bn_direct");
        final List<String> got = new java.util.ArrayList<>();
        kb.setListener(new KeyboardView.Listener() {
            @Override public void onKey(String t, int a) { got.add(t); }
            @Override public void onCandidate(int i) {}
        });
        int w = 1080;
        kb.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        kb.layout(0, 0, w, kb.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(w, kb.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        kb.draw(new Canvas(bmp));
        float[] p = kb.keyCenter("য়");
        long t0 = android.os.SystemClock.uptimeMillis();
        kb.onTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, p[0], p[1], 0));
        org.robolectric.shadows.ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        kb.onTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 700, android.view.MotionEvent.ACTION_UP, p[0], p[1], 0));
        assertEquals(java.util.Arrays.asList("্য"), got); // long press only, no extra short tap
        got.clear();
        kb.onTouchEvent(android.view.MotionEvent.obtain(t0, t0, android.view.MotionEvent.ACTION_DOWN, p[0], p[1], 0));
        kb.onTouchEvent(android.view.MotionEvent.obtain(t0, t0 + 50, android.view.MotionEvent.ACTION_UP, p[0], p[1], 0));
        assertEquals(java.util.Arrays.asList("য়"), got); // short tap still types the key
    }

    private KeyboardView backKb(Context ctx, List<String> got) {
        ctx.getResources().getDisplayMetrics().density = 3.0f;
        KeyboardView kb = new KeyboardView(ctx, null);
        kb.setMode("en");
        kb.setListener(new KeyboardView.Listener() {
            @Override public void onKey(String t, int a) { if (a == KeyboardView.A_BACK) got.add("B"); }
            @Override public void onCandidate(int i) {}
        });
        kb.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        kb.layout(0, 0, 1080, kb.getMeasuredHeight());
        kb.draw(new Canvas(Bitmap.createBitmap(1080, kb.getMeasuredHeight(), Bitmap.Config.ARGB_8888)));
        return kb;
    }

    private void ev(KeyboardView kb, int action, float[] p) {
        long t = android.os.SystemClock.uptimeMillis();
        kb.onTouchEvent(android.view.MotionEvent.obtain(t, t, action, p[0], p[1], 0));
    }

    @Test public void holdingBackspaceRepeatsAndTapDeletesOnce() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        List<String> got = new java.util.ArrayList<>();
        KeyboardView kb = backKb(ctx, got);
        float[] p = kb.keyCenter("⌫");
        ev(kb, android.view.MotionEvent.ACTION_DOWN, p);
        ev(kb, android.view.MotionEvent.ACTION_UP, p);
        assertEquals(1, got.size()); // plain tap = exactly one delete
        got.clear();
        ev(kb, android.view.MotionEvent.ACTION_DOWN, p);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue("repeats while held: " + got.size(), got.size() > 5);
        ev(kb, android.view.MotionEvent.ACTION_UP, p);
        int n = got.size();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals("stops on release, no extra delete", n, got.size());
    }

    @Test public void backspaceRepeatStopsOnCancelAndDetach() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        List<String> got = new java.util.ArrayList<>();
        KeyboardView kb = backKb(ctx, got);
        float[] p = kb.keyCenter("⌫");
        ev(kb, android.view.MotionEvent.ACTION_DOWN, p);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(700, java.util.concurrent.TimeUnit.MILLISECONDS);
        ev(kb, android.view.MotionEvent.ACTION_CANCEL, p);
        int n = got.size();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(700, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(n, got.size());
        got.clear();
        ev(kb, android.view.MotionEvent.ACTION_DOWN, p);
        org.robolectric.shadows.ShadowLooper.idleMainLooper(700, java.util.concurrent.TimeUnit.MILLISECONDS);
        kb.stopRepeat(); // what detach / hide / new input calls
        n = got.size();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(700, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(n, got.size());
        // finger slides off the key: repeat stops
        got.clear();
        ev(kb, android.view.MotionEvent.ACTION_DOWN, p);
        long t = android.os.SystemClock.uptimeMillis();
        kb.onTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_MOVE, 5, 5, 0));
        n = got.size();
        org.robolectric.shadows.ShadowLooper.idleMainLooper(700, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertEquals(n, got.size());
    }

    @Test public void holdOnBanglaKeysGivesCornerCharacter() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getResources().getDisplayMetrics().density = 3.0f;
        KeyboardView kb = new KeyboardView(ctx, null);
        kb.setMode("bn_direct");
        final List<String> got = new java.util.ArrayList<>();
        kb.setListener(new KeyboardView.Listener() {
            @Override public void onKey(String t, int a) { got.add(t); }
            @Override public void onCandidate(int i) {}
        });
        kb.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        kb.layout(0, 0, 1080, kb.getMeasuredHeight());
        kb.draw(new Canvas(Bitmap.createBitmap(1080, kb.getMeasuredHeight(), Bitmap.Config.ARGB_8888)));
        int checked = 0;
        for (int r = 0; r < KeyboardView.BN.length; r++) for (int i = 0; i < KeyboardView.BN[r].length; i++) {
            String base = KeyboardView.BN[r][i], corner = KeyboardView.BN_SHIFT[r][i];
            if (base.equals("য়") || base.equals("য")) continue; // these keys keep their special hold
            float[] p = kb.keyCenter(base);
            got.clear();
            ev(kb, android.view.MotionEvent.ACTION_DOWN, p);
            org.robolectric.shadows.ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
            ev(kb, android.view.MotionEvent.ACTION_UP, p);
            assertEquals("hold " + base, java.util.Arrays.asList(corner), got);
            checked++;
        }
        assertTrue(checked > 25);
    }

    // ---- full chain: touch -> KeyboardView -> KeyController -> real EditText InputConnection ----
    private void snap(KeyboardView kb, String name) throws Exception {
        kb.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        kb.layout(0, 0, 1080, kb.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(1080, kb.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        kb.draw(new Canvas(bmp));
        File out = new File("build/test-renders/" + name);
        out.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(out);
        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
    }

    private void tapKey(KeyboardView kb, String label) {
        kb.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        kb.layout(0, 0, 1080, kb.getMeasuredHeight());
        kb.draw(new Canvas(Bitmap.createBitmap(1080, kb.getMeasuredHeight(), Bitmap.Config.ARGB_8888)));
        float[] p = kb.keyCenter(label);
        assertNotNull("key not on screen: " + label, p);
        ev(kb, android.view.MotionEvent.ACTION_DOWN, p);
        ev(kb, android.view.MotionEvent.ACTION_UP, p);
    }

    private String edText(android.view.inputmethod.InputConnection ic) {
        CharSequence cs = ic.getTextBeforeCursor(1000, 0);
        return cs == null ? "" : cs.toString();
    }

    @Test public void fullChainSuggestionsHoldAndModes() throws Exception {
        final Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getResources().getDisplayMetrics().density = 3.0f;
        ctx.getResources().getDisplayMetrics().densityDpi = 480;
        loadRealFonts(ctx);
        final KeyboardView kb = new KeyboardView(ctx, null);
        kb.setTheme(KeyboardView.themeFor("mono", 0));
        android.widget.EditText et = new android.widget.EditText(ctx);
        final android.view.inputmethod.InputConnection ic = new android.view.inputmethod.BaseInputConnection(et, true);
        final java.util.Map<String, String> pm = new java.util.HashMap<>();
        KeyController ctl = new KeyController(() -> ic, new KeyController.Strip() {
            @Override public void show(List<String> items, boolean styles, boolean phonetic) { kb.setCandidates(items, styles); }
            @Override public void setMode(String m) { kb.setMode(m); }
            @Override public void setShifted(boolean s) { kb.setShifted(s); }
            @Override public boolean shifted() { return kb.isShifted(); }
            @Override public String mode() { return kb.getMode(); }
            @Override public void setActiveStyle(String s) { kb.setActiveStyle(s); }
        }, new KeyController.Prefs() {
            @Override public String get(String k, String d) { return pm.containsKey(k) ? pm.get(k) : d; }
            @Override public void put(String k, String v) { pm.put(k, v); }
        }, null);
        ctl.setDictionary(WordDictionary.load(new java.io.FileInputStream("src/main/assets/words_en.txt")));
        final KeyController c2 = ctl;
        kb.setListener(new KeyboardView.Listener() {
            @Override public void onKey(String t, int a) { c2.onKey(t, a); }
            @Override public void onCandidate(int i) { c2.onCandidate(i); }
        });
        ctl.onStartInput(new android.view.inputmethod.EditorInfo());
        kb.setMode("en");
        snap(kb, "kb_en.png");
        // type "tomor" with real key touches, then tap the suggestion chip with a real touch
        for (char ch : "tomor".toCharArray()) tapKey(kb, String.valueOf(ch));
        assertEquals("tomor", edText(ic));
        snap(kb, "kb_en_suggest.png");
        float[] chip = null; int ci = kb.candidatesForTest().indexOf("tomorrow");
        assertTrue(ci >= 0);
        ev(kb, android.view.MotionEvent.ACTION_DOWN, kb.stripCenter(ci));
        assertEquals("tomorrow ", edText(ic));
        // typo: offered, original text untouched until tapped
        for (char ch : "becuase".toCharArray()) tapKey(kb, String.valueOf(ch));
        assertEquals("tomorrow becuase", edText(ic));
        snap(kb, "kb_en_correct.png");
        ci = kb.candidatesForTest().indexOf("because");
        ev(kb, android.view.MotionEvent.ACTION_DOWN, kb.stripCenter(ci));
        assertEquals("tomorrow because ", edText(ic));
        // Bangla direct: hold a key into the real editor, with the preview bubble drawn while held
        ctl.onKey("", KeyboardView.A_LANG);
        assertEquals("bn_direct", kb.getMode());
        String base = KeyboardView.BN[1][3], corner = KeyboardView.BN_SHIFT[1][3];
        snap(kb, "kb_bn_pre.png");
        float[] p = kb.keyCenter(base);
        ev(kb, android.view.MotionEvent.ACTION_DOWN, p);
        snap(kb, "kb_bn_hold.png");
        org.robolectric.shadows.ShadowLooper.idleMainLooper(600, java.util.concurrent.TimeUnit.MILLISECONDS);
        ev(kb, android.view.MotionEvent.ACTION_UP, p);
        assertTrue(edText(ic).endsWith(corner));
        assertEquals(("tomorrow because " + corner).length(), edText(ic).length()); // hold typed once, no extra tap
        // phonetic mode label + fancy style suggestion
        kb.setCandidates(new java.util.ArrayList<String>(), false);
        kb.setMode("bn_phon");
        snap(kb, "kb_mode_phonetic.png");
        kb.setMode("bn_direct");
        snap(kb, "kb_mode_direct.png");
        ctl.onKey("", KeyboardView.A_LANG); // back to English
        ic.deleteSurroundingText(1000, 0); ctl.onStartInput(new android.view.inputmethod.EditorInfo());
        kb.setMode("en");
        snap(kb, "kb_en_pre.png");
        ctl.onKey("Aa", KeyboardView.A_STYLE); ctl.onCandidate(1); // Bold
        snap(kb, "kb_en_pre2.png");
        assertEquals("Bold", pm.get("style"));
        for (char ch : "tomor".toCharArray()) tapKey(kb, String.valueOf(ch));
        snap(kb, "kb_en_suggest_bold.png");
        ci = kb.candidatesForTest().indexOf("tomorrow");
        assertTrue(ci >= 0);
        ev(kb, android.view.MotionEvent.ACTION_DOWN, kb.stripCenter(ci));
        assertEquals(UnicodeStyles.map('t', "Bold") + UnicodeStyles.map('o', "Bold"), edText(ic).substring(0, 4));
        assertTrue(edText(ic).endsWith(" "));
        assertEquals(17, edText(ic).length());
    }

    // ---- style picker: no overlap, scrollable, tap vs drag, active visible ----
    private KeyboardView styleKb(Context ctx, float density, int widthPx, String active, List<String> tapped) throws Exception {
        ctx.getResources().getDisplayMetrics().density = density;
        loadRealFonts(ctx);
        KeyboardView kb = new KeyboardView(ctx, null);
        kb.setTheme(KeyboardView.themeFor("mono", 0));
        kb.setActiveStyle(active);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String n : UnicodeStyles.NAMES) names.add(n);
        kb.setCandidates(names, true);
        kb.setListener(new KeyboardView.Listener() {
            @Override public void onKey(String t, int a) {}
            @Override public void onCandidate(int i) { tapped.add(UnicodeStyles.NAMES[i]); }
        });
        kb.measure(android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        kb.layout(0, 0, widthPx, kb.getMeasuredHeight());
        kb.draw(new Canvas(Bitmap.createBitmap(widthPx, kb.getMeasuredHeight(), Bitmap.Config.ARGB_8888)));
        return kb;
    }

    @Test public void stylePickerChipsNeverOverlapAtPhoneWidths() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        float[][] cfg = {{3.0f, 1080}, {2.625f, 945}, {2.0f, 720}, {4.0f, 1440}, {3.0f, 1000}};
        for (float[] c : cfg) {
            KeyboardView kb = styleKb(ctx, c[0], (int) c[1], "Normal", new java.util.ArrayList<>());
            assertEquals(8, kb.stripCount());
            for (int i = 1; i < 8; i++) {
                RectF a = kb.stripRectForTest(i - 1), b = kb.stripRectForTest(i);
                assertTrue("overlap at density " + c[0] + " chip " + i, b.left >= a.right);
                assertTrue(b.width() >= 64 * c[0] - 1); // min 64dp, readable
            }
        }
    }

    @Test public void stylePickerScrollsAndTapVsDrag() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        List<String> tapped = new java.util.ArrayList<>();
        KeyboardView kb = styleKb(ctx, 3.0f, 1080, "Normal", tapped);
        assertTrue("last chip starts off-screen so row must scroll", kb.stripRectForTest(7).right > 1080);
        // tap on chip 1 (Bold) selects it
        float[] p = kb.stripCenter(1);
        ev(kb, android.view.MotionEvent.ACTION_DOWN, p);
        ev(kb, android.view.MotionEvent.ACTION_UP, p);
        assertEquals(java.util.Arrays.asList("Bold"), tapped);
        // drag left by 500px scrolls and selects nothing
        tapped.clear();
        float x0 = kb.stripRectForTest(0).left;
        long t = android.os.SystemClock.uptimeMillis();
        kb.onTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, 1000, 60, 0));
        kb.onTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_MOVE, 50, 60, 0));
        kb.onTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_UP, 50, 60, 0));
        kb.draw(new Canvas(Bitmap.createBitmap(1080, kb.getMeasuredHeight(), Bitmap.Config.ARGB_8888)));
        assertTrue(tapped.isEmpty());
        assertTrue("scrolled", kb.stripRectForTest(0).left < x0 - 100);
        assertTrue("last chip now fully visible, right=" + kb.stripRectForTest(7).right + " left0=" + kb.stripRectForTest(0).left, kb.stripRectForTest(7).right <= 1080 - 8 * 3 + 1);
        snap(kb, "kb_style_picker_scrolled.png");
        // after scroll, tapping the last chip selects Mono
        float[] q = kb.stripCenter(7);
        ev(kb, android.view.MotionEvent.ACTION_DOWN, q);
        ev(kb, android.view.MotionEvent.ACTION_UP, q);
        assertEquals(java.util.Arrays.asList("Mono"), tapped);
    }

    @Test public void stylePickerOpensWithActiveStyleVisibleAndHighlighted() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        KeyboardView kb = styleKb(ctx, 3.0f, 1080, "Mono", new java.util.ArrayList<>());
        RectF r = kb.stripRectForTest(7);
        assertTrue(r.left >= 0 && r.right <= 1080);
        snap(kb, "kb_style_picker_open.png");
        KeyboardView kb2 = styleKb(ctx, 3.0f, 1080, "Normal", new java.util.ArrayList<>());
        snap(kb2, "kb_style_picker_normal.png");
    }

    @Test public void clipboardWordTypesFromRealKeysWithHasantaAndReph() throws Exception {
        final Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getResources().getDisplayMetrics().density = 3.0f;
        loadRealFonts(ctx);
        final KeyboardView kb = new KeyboardView(ctx, null);
        android.widget.EditText et = new android.widget.EditText(ctx);
        final android.view.inputmethod.InputConnection ic = new android.view.inputmethod.BaseInputConnection(et, true);
        final java.util.Map<String, String> pm = new java.util.HashMap<>();
        final KeyController ctl = new KeyController(() -> ic, new KeyController.Strip() {
            @Override public void show(List<String> items, boolean styles, boolean phonetic) { kb.setCandidates(items, styles); }
            @Override public void setMode(String m) { kb.setMode(m); }
            @Override public void setShifted(boolean s) { kb.setShifted(s); }
            @Override public boolean shifted() { return kb.isShifted(); }
            @Override public String mode() { return kb.getMode(); }
            @Override public void setActiveStyle(String s) { kb.setActiveStyle(s); }
        }, new KeyController.Prefs() {
            @Override public String get(String k, String d) { return pm.containsKey(k) ? pm.get(k) : d; }
            @Override public void put(String k, String v) { pm.put(k, v); }
        }, null);
        kb.setListener(new KeyboardView.Listener() {
            @Override public void onKey(String t, int a) { ctl.onKey(t, a); }
            @Override public void onCandidate(int i) { ctl.onCandidate(i); }
        });
        ctl.onStartInput(new android.view.inputmethod.EditorInfo());
        kb.setMode("bn_direct");
        String word = "\u0995\u09CD\u09B2\u09BF\u09AA\u09AC\u09CB\u09B0\u09CD\u09A1"; // clipboard in Bangla
        String[] seq = {"\u0995", "\u09CD", "\u09B2", "\u09BF", "\u09AA", "\u09AC", "\u09CB", "\u09B0", "\u09CD", "\u09A1"};
        for (String k : seq) {
            snap(kb, "kb_tmp.png");
            assertNotNull("key present on direct layout: " + k, kb.keyCenter(k));
            tapKey(kb, k);
        }
        assertEquals(word, edText(ic));
    }

    @Test public void clipboardPanelHistoryPickClearAndPasswordLock() throws Exception {
        final Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getResources().getDisplayMetrics().density = 3.0f;
        loadRealFonts(ctx);
        final KeyboardView kb = new KeyboardView(ctx, null);
        kb.setTheme(KeyboardView.themeFor("mono", 0));
        final android.widget.EditText et = new android.widget.EditText(ctx);
        final android.view.inputmethod.InputConnection ic = new android.view.inputmethod.BaseInputConnection(et, true);
        final java.util.Map<String, String> pm = new java.util.HashMap<>();
        final KeyController ctl = new KeyController(() -> ic, new KeyController.Strip() {
            @Override public void show(List<String> items, boolean styles, boolean phonetic) { kb.setCandidates(items, styles); }
            @Override public void setMode(String m) { kb.setMode(m); }
            @Override public void setShifted(boolean s) { kb.setShifted(s); }
            @Override public boolean shifted() { return kb.isShifted(); }
            @Override public String mode() { return kb.getMode(); }
            @Override public void setActiveStyle(String s) { kb.setActiveStyle(s); }
            @Override public void setClips(List<String> c) { kb.setClips(c); }
        }, new KeyController.Prefs() {
            @Override public String get(String k, String d) { return pm.containsKey(k) ? pm.get(k) : d; }
            @Override public void put(String k, String v) { pm.put(k, v); }
        }, null);
        kb.setListener(new KeyboardView.Listener() {
            @Override public void onKey(String t, int a) { ctl.onKey(t, a); }
            @Override public void onCandidate(int i) { ctl.onCandidate(i); }
        });
        ctl.onStartInput(new android.view.inputmethod.EditorInfo());
        ctl.addClip("first copied line");
        ctl.addClip("\u0995\u09CD\u09B2\u09BF\u09AA\u09AC\u09CB\u09B0\u09CD\u09A1 \uD83D\uDE00");
        ctl.addClip("first copied line"); // duplicate moves to front, no second copy
        assertEquals(2, ctl.clipCount());
        ctl.addClip("   "); assertEquals(2, ctl.clipCount());
        kb.setMode("en");
        snap(kb, "kb_pre_sym.png");
        tapKey(kb, "?123");
        snap(kb, "kb_pre_clip.png");
        tapKey(kb, "CLIP");
        assertEquals("clip", kb.getMode());
        snap(kb, "kb_clipboard.png");
        // pick the Bangla+emoji clip (2nd row of items): inserted whole, no splitting
        tapKey(kb, "\u0995\u09CD\u09B2\u09BF\u09AA\u09AC\u09CB\u09B0\u09CD\u09A1 \uD83D\uDE00");
        assertEquals("\u0995\u09CD\u09B2\u09BF\u09AA\u09AC\u09CB\u09B0\u09CD\u09A1 \uD83D\uDE00", edText(ic).length() > 0 ? edText(ic) : "");
        assertEquals("en", kb.getMode()); // back to the letters
        // clear all empties the list
        tapKey(kb, "?123"); tapKey(kb, "CLIP");
        snap(kb, "kb_clip_pre_clear.png");
        tapKey(kb, "CLEAR ALL");
        assertEquals(0, ctl.clipCount());
        snap(kb, "kb_clipboard_empty.png");
        // password field: panel does not open, nothing inserted
        ctl.onKey("", KeyboardView.A_ABC);
        android.view.inputmethod.EditorInfo pw = new android.view.inputmethod.EditorInfo();
        pw.inputType = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
        ctl.addClip("hunter2-like");
        ctl.onStartInput(pw);
        ctl.onKey("", KeyboardView.A_CLIP);
        assertNotEquals("clip", kb.getMode());
        ctl.onKey("0", KeyboardView.A_CLIPPICK);
        assertFalse(edText(ic).contains("hunter2-like"));
        // an open panel closes when a new field starts
        ctl.onStartInput(new android.view.inputmethod.EditorInfo());
        ctl.onKey("", KeyboardView.A_CLIP);
        assertEquals("clip", kb.getMode());
        ctl.onStartInput(new android.view.inputmethod.EditorInfo());
        assertNotEquals("clip", kb.getMode());
    }
}
