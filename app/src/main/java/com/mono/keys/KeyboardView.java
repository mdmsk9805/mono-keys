package com.mono.keys;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Canvas-drawn keyboard: suggestion strip + key rows. Nothing-style: flat
 *  rounded keys, big touch targets, dot-matrix accent font (Doto). */
public class KeyboardView extends View {
    public static final int A_TEXT = 0, A_BACK = 1, A_ENTER = 2, A_SHIFT = 3,
        A_SYM = 4, A_ABC = 5, A_LANG = 6, A_STYLE = 7, A_SPACE = 8;

    public interface Listener {
        void onKey(String text, int action);
        void onCandidate(int index);
    }

    static final class Key {
        String label; float weight; int action;
        Key(String l, float w, int a) { label = l; weight = w; action = a; }
    }

    public static final class Theme {
        public int bg, keyBg, keyText, accent, stripText;
        Theme(int bg, int keyBg, int keyText, int accent, int stripText) {
            this.bg = bg; this.keyBg = keyBg; this.keyText = keyText;
            this.accent = accent; this.stripText = stripText;
        }
    }

    public static Theme themeFor(String name, int accentOverride) {
        Theme t;
        switch (name) {
            case "paper":
                t = new Theme(0xFFF2F2F0, 0xFFFFFFFF, 0xFF111111, 0xFFD71920, 0xFF666666); break;
            case "slate":
                t = new Theme(0xFF101418, 0xFF1E242B, 0xFFE6EDF2, 0xFF2DD4BF, 0xFF9AA7B2); break;
            default:
                t = new Theme(0xFF000000, 0xFF1C1C1E, 0xFFFFFFFF, 0xFFD71920, 0xFFAAAAAA);
        }
        if (accentOverride != 0) t.accent = accentOverride;
        return t;
    }

    private static final String[][] EN = {
        {"q","w","e","r","t","y","u","i","o","p"},
        {"a","s","d","f","g","h","j","k","l"},
        {"z","x","c","v","b","n","m"}};
    private static final String[][] BN = {
        {"ক","খ","গ","ঘ","ঙ","চ","ছ","ঝ","ঞ","ট"},
        {"ঠ","ড","ঢ","ণ","ত","থ","দ","ধ","ন","প"},
        {"ফ","ব","ভ","ম","য","র","ল","শ","স","হ"}};
    private static final String[][] BN_SHIFT = {
        {"অ","আ","ই","ঈ","উ","ঊ","ঋ","এ","ঐ","ও"},
        {"া","ি","ী","ু","ূ","ৃ","ে","ৈ","ো","ৌ"},
        {"ষ","ং","ঃ","ঁ","ৎ","ড়","ঢ়","য়","।"}};
    private static final String[][] SYM = {
        {"1","2","3","4","5","6","7","8","9","0"},
        {"@","#","$","%","&","-","+","(",")"},
        {"=","*","?","!","\"","'",":",";"}};
    private static final String[][] SYM_BN = {
        {"১","২","৩","৪","৫","৬","৭","৮","৯","০"},
        {"@","#","$","%","&","-","+","(",")"},
        {"=","*","?","!","\"","'",":",";"}};

    static Typeface overrideDoto, overrideBengali; // test hook

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF rect = new RectF();
    private final Typeface doto, bengali;
    private final float density;

    private Theme theme = themeFor("mono", 0);
    private Listener listener;
    private String mode = "en"; // en | bn_direct | sym
    private boolean shifted = false;
    private boolean symFromBangla = false;
    private String activeStyle = "Normal";
    private final List<String> candidates = new ArrayList<>();
    private boolean stripIsStyles = false;

    private final List<List<Key>> rows = new ArrayList<>();
    private final List<RectF> keyRects = new ArrayList<>();
    private final List<Key> flatKeys = new ArrayList<>();
    private final List<RectF> stripRects = new ArrayList<>();
    private int pressedIdx = -1;

    public KeyboardView(Context ctx, AttributeSet a) {
        super(ctx, a);
        density = getResources().getDisplayMetrics().density;
        doto = overrideDoto != null ? overrideDoto : safeFont(ctx, "fonts/doto.ttf");
        bengali = overrideBengali != null ? overrideBengali : safeFont(ctx, "fonts/bengali.ttf");
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) { listener = l; }
    public void setTheme(Theme t) { theme = t; invalidate(); }
    public String getMode() { return mode; }
    public boolean isShifted() { return shifted; }
    public void setActiveStyle(String s) { activeStyle = s; }

    public void setMode(String m) {
        if (mode.equals("bn_direct")) symFromBangla = true;
        else if (mode.equals("en")) symFromBangla = false;
        mode = m; shifted = false; invalidate();
    }

    public void setShifted(boolean s) { shifted = s; invalidate(); }

    public void setCandidates(List<String> c, boolean styles) {
        candidates.clear(); candidates.addAll(c); stripIsStyles = styles; invalidate();
    }

    private static Typeface safeFont(Context ctx, String path) {
        try {
            Typeface t = Typeface.createFromAsset(ctx.getAssets(), path);
            return t != null ? t : Typeface.DEFAULT;
        } catch (Throwable t) {
            return Typeface.DEFAULT;
        }
    }

    private float dp(float v) { return v * density; }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (dp(44) + 4 * dp(58) + dp(8)));
    }

    private void buildRows() {
        rows.clear();
        String[][] base;
        boolean bangla = mode.equals("bn_direct");
        if (mode.equals("sym")) base = symFromBangla ? SYM_BN : SYM;
        else if (bangla) base = shifted ? BN_SHIFT : BN;
        else base = EN;
        for (int r = 0; r < 3; r++) {
            List<Key> row = new ArrayList<>();
            if (r == 2) row.add(new Key(shifted ? "⇩" : "⇧", 1.5f, A_SHIFT));
            for (String k : base[r]) {
                String label = (!bangla && mode.equals("en") && shifted) ? k.toUpperCase() : k;
                row.add(new Key(label, 1f, A_TEXT));
            }
            if (r == 0 && mode.equals("sym")) row.add(new Key("⌫", 1.4f, A_BACK));
            if (r == 1 && !mode.equals("sym")) row.add(new Key("⌫", 1.4f, A_BACK));
            if (r == 1 && mode.equals("sym")) row.add(new Key("⌫", 1.4f, A_BACK));
            if (r == 2) row.add(new Key("⏎", 1.5f, A_ENTER));
            rows.add(row);
        }
        List<Key> bottom = new ArrayList<>();
        if (mode.equals("en")) bottom.add(new Key("Aa", 1.2f, A_STYLE));
        bottom.add(new Key(mode.equals("sym") ? "ABC" : "123", 1.2f, mode.equals("sym") ? A_ABC : A_SYM));
        bottom.add(new Key(bangla ? "EN" : "বাং", 1.4f, A_LANG));
        bottom.add(new Key(bangla ? "্" : ",", 1f, A_TEXT));
        bottom.add(new Key("space", mode.equals("en") ? 4.6f : 4.0f, A_SPACE));
        bottom.add(new Key(".", 1f, A_TEXT));
        rows.add(bottom);
    }

    @Override protected void onDraw(Canvas c) {
        buildRows();
        c.drawColor(theme.bg);
        float w = getWidth(), stripH = dp(44), gap = dp(4), pad = dp(6), rowH = dp(58);
        drawStrip(c, w, stripH, gap);

        keyRects.clear(); flatKeys.clear();
        float y = stripH + dp(2);
        for (List<Key> row : rows) {
            float totalW = 0;
            for (Key k : row) totalW += k.weight;
            float unit = (w - pad * 2 - gap * (row.size() - 1)) / totalW;
            float x = pad;
            for (Key k : row) {
                float kw = k.weight * unit;
                rect.set(x, y, x + kw, y + rowH - gap);
                int idx = flatKeys.size();
                flatKeys.add(k);
                keyRects.add(new RectF(rect));
                boolean special = k.action != A_TEXT && k.action != A_SPACE;
                boolean active = (k.action == A_SHIFT && shifted)
                        || (k.action == A_STYLE && !activeStyle.equals("Normal"));
                keyPaint.setColor(idx == pressedIdx ? theme.accent : special ? shade(theme.keyBg) : theme.keyBg);
                c.drawRoundRect(rect, dp(8), dp(8), keyPaint);
                boolean bnLabel = looksBengali(k.label);
                textPaint.setTypeface((k.action == A_SYM || k.action == A_ABC || k.action == A_STYLE
                        || (k.action == A_LANG && !bnLabel)) ? doto : bnLabel ? bengali : Typeface.DEFAULT_BOLD);
                textPaint.setColor(idx == pressedIdx ? 0xFFFFFFFF : active ? theme.accent : theme.keyText);
                textPaint.setTextSize(bnLabel ? dp(19) : dp(17));
                c.drawText(k.label, rect.centerX(), rect.centerY() + dp(6), textPaint);
                x += kw + gap;
            }
            y += rowH;
        }
    }

    private void drawStrip(Canvas c, float w, float stripH, float gap) {
        stripRects.clear();
        textPaint.setTextSize(dp(16));
        if (candidates.isEmpty()) {
            textPaint.setTypeface(doto);
            textPaint.setColor(theme.stripText);
            String hint = mode.equals("bn_direct") ? "বাংলা" : mode.equals("sym") ? "123"
                    : activeStyle.equals("Normal") ? "MONO KEYS" : activeStyle.toUpperCase();
            c.drawText(hint, w / 2, stripH / 2 + dp(6), textPaint);
            return;
        }
        int n = candidates.size();
        float[] ws = new float[n];
        float total = gap * (n - 1);
        for (int i = 0; i < n; i++) {
            textPaint.setTypeface(stripIsStyles ? doto : looksBengali(candidates.get(i)) ? bengali : Typeface.DEFAULT_BOLD);
            ws[i] = Math.min(textPaint.measureText(candidates.get(i)) + dp(28), w * 0.6f);
            total += ws[i];
        }
        float x = Math.max((w - total) / 2, dp(6));
        for (int i = 0; i < n; i++) {
            RectF r = new RectF(x, dp(5), x + ws[i], stripH - dp(5));
            stripRects.add(r);
            boolean first = i == 0 && !stripIsStyles;
            keyPaint.setColor(first ? theme.accent : theme.keyBg);
            c.drawRoundRect(r, dp(8), dp(8), keyPaint);
            textPaint.setColor(first ? 0xFFFFFFFF : theme.keyText);
            textPaint.setTypeface(stripIsStyles ? doto : looksBengali(candidates.get(i)) ? bengali : Typeface.DEFAULT_BOLD);
            c.drawText(candidates.get(i), r.centerX(), r.centerY() + dp(6), textPaint);
            x += ws[i] + gap;
        }
    }

    private static int shade(int color) {
        int r = Math.min(255, (int) (((color >> 16) & 0xff) * 1.3f));
        int g = Math.min(255, (int) (((color >> 8) & 0xff) * 1.3f));
        int b = Math.min(255, (int) ((color & 0xff) * 1.3f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static boolean looksBengali(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x0980 && ch <= 0x09FF) return true;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedIdx = findKey(x, y);
                if (pressedIdx < 0) {
                    int ci = findStrip(x, y);
                    if (ci >= 0 && listener != null) listener.onCandidate(ci);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                pressedIdx = findKey(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                int idx = findKey(x, y);
                if (idx >= 0 && idx == pressedIdx && listener != null) {
                    Key k = flatKeys.get(idx);
                    listener.onKey(k.label, k.action);
                }
                pressedIdx = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private int findKey(float x, float y) {
        for (int i = 0; i < keyRects.size(); i++) if (keyRects.get(i).contains(x, y)) return i;
        return -1;
    }

    private int findStrip(float x, float y) {
        for (int i = 0; i < stripRects.size(); i++) if (stripRects.get(i).contains(x, y)) return i;
        return -1;
    }
}
