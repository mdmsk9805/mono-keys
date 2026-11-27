package com.mono.keys;

import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.List;

/** Thin IME shell: all input logic lives in KeyController (unit-tested). */
public class MonoKeysService extends InputMethodService implements KeyboardView.Listener {
    private KeyboardView kb;
    private SharedPreferences prefs;
    private KeyController ctl;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("mono_keys", MODE_PRIVATE);
    }

    @Override public View onCreateInputView() {
        kb = new KeyboardView(this, null);
        kb.setListener(this);
        ShortcutStore store = null;
        try { store = new ShortcutStore(this, new CryptoBox(this)); } catch (Exception ignored) {}
        ctl = new KeyController(new KeyController.Conn() {
                @Override public InputConnection get() { return getCurrentInputConnection(); }
            }, new KeyController.Strip() {
                @Override public void show(List<String> items, boolean styles, boolean phonetic) {
                    kb.setCandidates(items, styles);
                }
                @Override public void setMode(String m) { kb.setMode(m); }
                @Override public void setShifted(boolean s) { kb.setShifted(s); }
                @Override public boolean shifted() { return kb.isShifted(); }
                @Override public String mode() { return kb.getMode(); }
                @Override public void setActiveStyle(String s) { kb.setActiveStyle(s); }
            }, new KeyController.Prefs() {
                @Override public String get(String k, String def) { return prefs.getString(k, def); }
                @Override public void put(String k, String v) { prefs.edit().putString(k, v).apply(); }
            }, store);
        applyTheme();
        return kb;
    }

    private void applyTheme() {
        if (kb != null) kb.setTheme(KeyboardView.themeFor(
                prefs.getString("theme", "mono"), prefs.getInt("accent", 0)));
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        applyTheme();
        if (ctl != null) {
            ctl.bnMode = prefs.getString("bn_mode", "phonetic");
            ctl.style = prefs.getString("style", "Normal");
            ctl.onStartInput(info);
        }
    }

    @Override public void onKey(String text, int action) { if (ctl != null) ctl.onKey(text, action); }
    @Override public void onCandidate(int index) { if (ctl != null) ctl.onCandidate(index); }
}
