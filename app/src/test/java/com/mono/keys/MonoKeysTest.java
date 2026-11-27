package com.mono.keys;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;

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
}
