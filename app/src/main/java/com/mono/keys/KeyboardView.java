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
        A_SYM = 4, A_ABC = 5, A_LANG = 6, A_STYLE = 7, A_SPACE = 8, A_NUM = 9, A_BNMODE = 10;

    public interface Listener {
        void onKey(String text, int action);
        void onCandidate(int index);
    }

    static final class Key {
        String label; float weight; int action; String alt; String longText;
        Key(String l, float w, int a) { label = l; weight = w; action = a; }
        Key(String l, String alt, float w, int a) { label = l; this.alt = alt; weight = w; action = a; }
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
    // Direct Bangla layer: Probhat-style positions read from the user's own
    // reference screenshots (QWERTY slots). Base layer, then the shift layer
    // (shown as small corner labels on each key).
    static final String[][] BN = {
        {"দ","ূ","ী","র","ট","এ","ু","ি","ও","প","ে","ো"},
        {"া","স","ড","ত","গ","হ","জ","ক","ল"},
        {"য়","শ","চ","আ","ব","ন","ম","ৃ","্"}};
    static final String[][] BN_SHIFT = {
        {"ধ","ঊ","ঈ","ড়","ঠ","ঐ","উ","ই","ঔ","ফ","ৈ","ৌ"},
        {"অ","ষ","ঢ","থ","ঘ","ঃ","ঝ","খ","ং"},
        {"য","ঢ়","ছ","ঋ","ভ","ণ","ঙ","ৃ","ঁ"}};
    private static final String[] DIG_EN = {"1","2","3","4","5","6","7","8","9","0"};
    private static final String[] DIG_BN = {"১","২","৩","৪","৫","৬","৭","৮","৯","০"};
    private static final String[][] SYM = {
        {"~","±","×","÷","•","°","`","_","{","}"},
        {"@","#","₹","%","&","*","-","=","(",")"},
        {"!","\"","'",":",";","+","/","?","[","]"}};
    private static final String[][] NUM = {
        {"1","2","3"},{"4","5","6"},{"7","8","9"}};

    static final float ROW_DP = 52f, NUMROW_DP = 42f;
    static Typeface overrideDoto, overrideBengali; // test hook

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF rect = new RectF();
    private final Typeface doto, bengali;
    private final float density;

    private Theme theme = themeFor("mono", 0);
    private Listener listener;
    private String mode = "en"; // en | bn_direct | bn_phon | sym | num
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
    private boolean longFired = false;
    private final android.os.Handler lpHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (pressedIdx >= 0 && pressedIdx < flatKeys.size()) {
                Key k = flatKeys.get(pressedIdx);
                if (k.longText != null && listener != null) { longFired = true; listener.onKey(k.longText, A_TEXT); }
            }
        }
    };

    private boolean repeating = false;
    private int repeatCount = 0;
    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            if (!repeating || listener == null) return;
            repeatCount++;
            listener.onKey("⌫", A_BACK);
            lpHandler.postDelayed(this, repeatCount > 12 ? 30 : 60);
        }
    };

    /** Stops press-and-hold repeat (release, cancel, focus change, hide). */
    public void stopRepeat() {
        repeating = false; repeatCount = 0;
        lpHandler.removeCallbacks(repeatDelete);
        lpHandler.removeCallbacks(longPress);
    }

    @Override protected void onDetachedFromWindow() { stopRepeat(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int v) { if (v != VISIBLE) stopRepeat(); super.onWindowVisibilityChanged(v); }

    List<String> candidatesForTest() { return new ArrayList<>(candidates); }

    /** Test hook: centre of strip chip i from the last draw. */
    float[] stripCenter(int i) { return new float[]{stripRects.get(i).centerX(), stripRects.get(i).centerY()}; }

    /** Test hook: centre of the first key with this label from the last draw. */
    float[] keyCenter(String label) {
        for (int i = 0; i < flatKeys.size(); i++)
            if (flatKeys.get(i).label.equals(label)) return new float[]{keyRects.get(i).centerX(), keyRects.get(i).centerY()};
        return null;
    }

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
        if (mode.equals("bn_direct") || mode.equals("bn_phon")) symFromBangla = true;
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
        setMeasuredDimension(MeasureSpec.getSize(w), (int) (dp(44) + dp(NUMROW_DP) + 4 * dp(ROW_DP) + dp(8)));
    }

    private final List<Float> rowHeights = new ArrayList<>();

    private void buildRows() {
        rows.clear(); rowHeights.clear();
        boolean bnDirect = mode.equals("bn_direct");
        boolean bnAny = bnDirect || mode.equals("bn_phon");
        if (mode.equals("num")) { buildNumPad(); return; }
        // permanent number row
        List<Key> nr = new ArrayList<>();
        String[] dig = (bnAny || (mode.equals("sym") && symFromBangla)) ? DIG_BN : DIG_EN;
        for (String d : dig) nr.add(new Key(d, 1f, A_TEXT));
        rows.add(nr); rowHeights.add(NUMROW_DP);
        if (mode.equals("sym")) {
            for (int r = 0; r < 3; r++) {
                List<Key> row = new ArrayList<>();
                for (String k : SYM[r]) row.add(new Key(k, 1f, A_TEXT));
                if (r == 2) row.add(new Key("⌫", 1.4f, A_BACK));
                rows.add(row); rowHeights.add(ROW_DP);
            }
        } else if (bnDirect) {
            String[][] alt = BN_SHIFT;
            for (int r = 0; r < 3; r++) {
                List<Key> row = new ArrayList<>();
                if (r == 2) row.add(new Key(shifted ? "⇩" : "⇧", 1.2f, A_SHIFT));
                for (int i = 0; i < BN[r].length; i++) {
                    String main = shifted ? alt[r][i] : BN[r][i];
                    String small = shifted ? BN[r][i] : alt[r][i];
                    Key bk = new Key(main, small, 1f, A_TEXT);
                    if (main.equals("য়") || main.equals("য")) bk.longText = "্য";
                    if (shifted && main.equals("উ")) bk.longText = "ৎ";
                    if (bk.longText == null && small != null && !small.isEmpty()) bk.longText = small; // hold = corner character
                    row.add(bk);
                }
                if (r == 2) row.add(new Key("⌫", 1.2f, A_BACK));
                rows.add(row); rowHeights.add(ROW_DP);
            }
        } else {
            for (int r = 0; r < 3; r++) {
                List<Key> row = new ArrayList<>();
                if (r == 2) row.add(new Key(shifted ? "⇩" : "⇧", 1.4f, A_SHIFT));
                for (String k : EN[r]) row.add(new Key(shifted ? k.toUpperCase() : k, 1f, A_TEXT));
                if (r == 2) row.add(new Key("⌫", 1.4f, A_BACK));
                rows.add(row); rowHeights.add(ROW_DP);
            }
        }
        List<Key> bottom = new ArrayList<>();
        boolean sym = mode.equals("sym");
        bottom.add(new Key(sym ? "ABC" : "?123", 1.3f, sym ? A_ABC : A_SYM));
        if (sym) bottom.add(new Key("1234", 1.3f, A_NUM));
        else if (mode.equals("en")) bottom.add(new Key("Aa", 1.2f, A_STYLE));
        if (!sym) bottom.add(new Key(bnAny ? "EN" : "অ", 1.2f, A_LANG));
        bottom.add(new Key(bnDirect ? "," : ",", 1f, A_TEXT));
        bottom.add(new Key("space", 4.0f, A_SPACE));
        bottom.add(new Key(bnAny ? "।" : ".", 1f, A_TEXT));
        if (bnAny) bottom.add(new Key(".", 1f, A_TEXT));
        bottom.add(new Key("⏎", 1.4f, A_ENTER));
        rows.add(bottom); rowHeights.add(ROW_DP);
    }

    private void buildNumPad() {
        for (int r = 0; r < 3; r++) {
            List<Key> row = new ArrayList<>();
            for (String k : NUM[r]) row.add(new Key(k, 1f, A_TEXT));
            if (r == 0) row.add(new Key("⌫", 1f, A_BACK));
            if (r == 1) row.add(new Key(".", 1f, A_TEXT));
            if (r == 2) row.add(new Key(",", 1f, A_TEXT));
            rows.add(row); rowHeights.add(62.5f);
        }
        List<Key> b = new ArrayList<>();
        b.add(new Key("ABC", 1f, A_ABC));
        b.add(new Key("0", 1f, A_TEXT));
        b.add(new Key("space", 1f, A_SPACE));
        b.add(new Key("⏎", 1f, A_ENTER));
        rows.add(b); rowHeights.add(62.5f);
    }

    @Override protected void onDraw(Canvas c) {
        buildRows();
        c.drawColor(theme.bg);
        float w = getWidth(), stripH = dp(44), gap = dp(3), pad = dp(4);
        drawStrip(c, w, stripH, gap);

        keyRects.clear(); flatKeys.clear();
        float y = stripH + dp(2);
        for (int ri = 0; ri < rows.size(); ri++) {
            List<Key> row = rows.get(ri);
            float rowH = dp(rowHeights.get(ri));
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
                textPaint.setTypeface((k.action == A_SYM || k.action == A_ABC || k.action == A_NUM || k.action == A_STYLE
                        || (k.action == A_LANG && !bnLabel)) ? doto : bnLabel ? bengali : Typeface.DEFAULT_BOLD);
                textPaint.setColor(idx == pressedIdx ? 0xFFFFFFFF : active ? theme.accent : theme.keyText);
                textPaint.setTextSize(bnLabel ? dp(18) : (k.label.length() >= 3 && k.action != A_SPACE ? dp(13) : dp(17)));
                c.drawText(k.label, rect.centerX(), rect.centerY() + dp(6), textPaint);
                if (k.alt != null) {
                    textPaint.setTextSize(dp(9));
                    textPaint.setTypeface(bengali);
                    textPaint.setColor(idx == pressedIdx ? 0xFFFFFFFF : theme.stripText);
                    c.drawText(k.alt, rect.right - dp(8), rect.top + dp(11), textPaint);
                }
                x += kw + gap;
            }
            y += rowH;
        }
        // while a key with a hold alternate is pressed, preview what holding will type
        if (pressedIdx >= 0 && pressedIdx < flatKeys.size() && flatKeys.get(pressedIdx).longText != null
                && flatKeys.get(pressedIdx).action == A_TEXT) {
            RectF kr = keyRects.get(pressedIdx);
            float bw = Math.max(kr.width(), dp(56)), bh = dp(54);
            float bx = Math.min(Math.max(kr.centerX() - bw / 2, dp(2)), w - bw - dp(2));
            RectF bub = new RectF(bx, Math.max(dp(2), kr.top - bh - dp(6)), bx + bw, Math.max(dp(2), kr.top - bh - dp(6)) + bh);
            keyPaint.setColor(theme.accent);
            c.drawRoundRect(bub, dp(10), dp(10), keyPaint);
            textPaint.setTypeface(bengali);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(dp(28));
            c.drawText(flatKeys.get(pressedIdx).longText, bub.centerX(), bub.centerY() + dp(10), textPaint);
        }
    }

    private void drawStrip(Canvas c, float w, float stripH, float gap) {
        stripRects.clear();
        textPaint.setTextSize(dp(16));
        if (candidates.isEmpty()) {
            textPaint.setTypeface(doto);
            textPaint.setColor(theme.stripText);
            String hint = mode.equals("bn_direct") ? "বাংলা · DIRECT ⇄" : mode.equals("bn_phon") ? "বাংলা · PHONETIC ⇄" : mode.equals("sym") ? "123"
                    : activeStyle.equals("Normal") ? "MONO KEYS" : activeStyle.toUpperCase();
            c.drawText(hint, w / 2, stripH / 2 + dp(6), textPaint);
            return;
        }
        int n = candidates.size();
        float[] ws = new float[n];
        float total = gap * (n - 1);
        for (int i = 0; i < n; i++) {
            textPaint.setTypeface(stripIsStyles ? doto : looksBengali(candidates.get(i)) ? bengali : Typeface.DEFAULT_BOLD);
            ws[i] = Math.min(textPaint.measureText(candidates.get(i)) + dp(candidates.size() > 6 ? 12 : 28), w * 0.6f);
            total += ws[i];
        }
        float avail = w - dp(12);
        if (total > avail) {
            float f = (avail - gap * (n - 1)) / (total - gap * (n - 1));
            for (int i = 0; i < n; i++) ws[i] *= f;
            total = avail;
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
                longFired = false;
                pressedIdx = findKey(x, y);
                if (pressedIdx < 0 && candidates.isEmpty() && y < dp(44)
                        && (mode.equals("bn_direct") || mode.equals("bn_phon")) && listener != null) {
                    listener.onKey("", A_BNMODE); // tap the mode label to switch direct/phonetic
                    return true;
                }
                lpHandler.removeCallbacks(longPress);
                if (pressedIdx >= 0 && flatKeys.get(pressedIdx).longText != null)
                    lpHandler.postDelayed(longPress, 400);
                if (pressedIdx >= 0 && flatKeys.get(pressedIdx).action == A_BACK && listener != null) {
                    stopRepeat();
                    repeating = true; repeatCount = 0; longFired = true; // suppress the on-release tap
                    listener.onKey("⌫", A_BACK);                         // first delete on press
                    lpHandler.postDelayed(repeatDelete, 400);            // then repeat while held
                }
                if (pressedIdx < 0) {
                    int ci = findStrip(x, y);
                    if (ci >= 0 && listener != null) listener.onCandidate(ci);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                { int ni = findKey(x, y); if (ni != pressedIdx) { lpHandler.removeCallbacks(longPress); stopRepeat(); } pressedIdx = ni; }
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                stopRepeat();
                pressedIdx = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                stopRepeat();
                int idx = findKey(x, y);
                if (!longFired && idx >= 0 && idx == pressedIdx && listener != null) {
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
