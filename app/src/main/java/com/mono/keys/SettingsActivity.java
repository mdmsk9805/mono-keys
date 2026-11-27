package com.mono.keys;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    static ShortcutStore testStore; // test hook

    private SharedPreferences prefs;
    private ShortcutStore store;
    private LinearLayout root, shortcutList, gateBox;
    private Typeface doto;
    private boolean unlocked = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Mono Keys");
        prefs = getSharedPreferences("mono_keys", MODE_PRIVATE);
        if (testStore != null) store = testStore;
        else {
            try { store = new ShortcutStore(this, new CryptoBox(this)); } catch (Exception e) { store = null; }
        }
        Typeface loaded = KeyboardView.overrideDoto;
        if (loaded == null) {
            try { loaded = Typeface.createFromAsset(getAssets(), "fonts/doto.ttf"); }
            catch (Throwable t) { loaded = Typeface.DEFAULT; }
        }
        doto = loaded == null ? Typeface.DEFAULT : loaded;

        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 40, 40, 40);
        root.setBackgroundColor(Color.BLACK);
        sv.addView(root);
        setContentView(sv);

        TextView title = txt("MONO KEYS", 34, Color.WHITE);
        title.setTypeface(doto);
        root.addView(title);
        root.addView(txt("Lightweight Bangla + English keyboard", 14, 0xFFAAAAAA));

        root.addView(txt("\nSETUP", 13, 0xFFD71920));
        Button enable = btn("1. Enable keyboard in system settings");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);
        Button pick = btn("2. Switch to Mono Keys");
        pick.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        root.addView(pick);

        root.addView(txt("\nBANGLA TYPING", 13, 0xFFD71920));
        LinearLayout bnChoices = choice("Direct Bangla keys", "bn_mode", "direct", null);
        choice("Phonetic (type roman, get Bangla)", "bn_mode", "phonetic", bnChoices);
        root.addView(bnChoices);

        root.addView(txt("\nTHEME", 13, 0xFFD71920));
        LinearLayout themes = choice("Slate", "theme", "slate", null);
        choice("Paper", "theme", "paper", themes);
        choice("Mono Dark", "theme", "mono", themes);
        root.addView(themes);

        root.addView(txt("\nACCENT", 13, 0xFFD71920));
        root.addView(accents());

        root.addView(txt("\nSAVED TEXT SHORTCUTS", 13, 0xFFD71920));
        root.addView(txt("Type a trigger on the keyboard, tap the suggestion to insert the full text. "
                + "Values are AES-encrypted on this device only (key in Android Keystore); "
                + "no internet, no backups, uninstalling deletes them. "
                + "Viewing and editing here needs your fingerprint or screen lock.",
                12, 0xFF888888));
        gateBox = new LinearLayout(this);
        gateBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(gateBox);
        shortcutList = new LinearLayout(this);
        shortcutList.setOrientation(LinearLayout.VERTICAL);
        root.addView(shortcutList);
        showGate();
    }

    private void showGate() {
        gateBox.removeAllViews();
        shortcutList.removeAllViews();
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        boolean canGate = Build.VERSION.SDK_INT >= 29 && km != null && km.isDeviceSecure();
        if (!canGate) {
            unlocked = true;
            TextView warn = txt(Build.VERSION.SDK_INT < 29
                    ? "No unlock gate on Android below 10. Anyone holding this unlocked phone can open this screen. Do not store sensitive data."
                    : "No screen lock is set, so this screen is not protected. Set a lock, or do not store sensitive data.", 13, 0xFFD71920);
            gateBox.addView(warn);
            refreshList();
            return;
        }
        Button unlock = btn("Unlock with fingerprint or screen lock");
        unlock.setOnClickListener(v -> authenticate());
        gateBox.addView(unlock);
    }

    private void authenticate() {
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Mono Keys")
                .setSubtitle("Manage saved text shortcuts")
                .setDeviceCredentialAllowed(true)
                .build();
        prompt.authenticate(new CancellationSignal(), getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        unlocked = true;
                        gateBox.removeAllViews();
                        refreshList();
                    }
                });
    }

    @Override protected void onResume() { super.onResume(); if (unlocked) refreshList(); }

    private void refreshList() {
        shortcutList.removeAllViews();
        if (store == null) return;
        for (String t : store.triggers()) {
            Button b = btn(t);
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v -> {
                String val = store.get(t);
                new AlertDialog.Builder(this)
                    .setTitle(t)
                    .setMessage(val == null ? "" : val)
                    .setPositiveButton("Edit", (d, w) -> editDialog(t, val))
                    .setNegativeButton("Delete", (d, w) -> { store.delete(t); refreshList(); })
                    .setNeutralButton("Close", null)
                    .show();
            });
            shortcutList.addView(b);
        }
        Button add = btn("+ Add shortcut");
        add.setOnClickListener(v -> editDialog(null, null));
        shortcutList.addView(add);
    }

    private void editDialog(String existing, String val) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 20, 48, 0);
        EditText trig = new EditText(this); trig.setHint("Trigger (e.g. iq)");
        EditText body = new EditText(this); body.setHint("Full text"); body.setMinLines(2);
        if (existing != null) { trig.setText(existing); trig.setEnabled(false); body.setText(val); }
        form.addView(trig); form.addView(body);
        new AlertDialog.Builder(this)
            .setTitle(existing == null ? "Add shortcut" : "Edit shortcut")
            .setView(form)
            .setPositiveButton("Save", (d, w) -> {
                String t = trig.getText().toString().trim();
                String v = body.getText().toString();
                if (store != null && !t.isEmpty() && !v.isEmpty()) {
                    try { store.save(t, v); } catch (Exception ignored) {}
                    refreshList();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private LinearLayout choice(String label, String key, String value, LinearLayout next) {
        LinearLayout row = next == null ? new LinearLayout(this) : next;
        row.setOrientation(LinearLayout.VERTICAL);
        Button b = btn(label + (prefs.getString(key, "").equals(value) ? "  ●" : ""));
        b.setOnClickListener(v -> { prefs.edit().putString(key, value).apply(); recreate(); });
        row.addView(b, 0);
        return row;
    }

    private LinearLayout accents() {
        LinearLayout row = new LinearLayout(this);
        int[] colors = {0xFFD71920, 0xFF2DD4BF, 0xFFF5A623, 0xFFFFFFFF};
        String[] names = {"Red", "Teal", "Amber", "White"};
        for (int i = 0; i < colors.length; i++) {
            final int c = colors[i];
            Button b = btn(names[i] + (prefs.getInt("accent", 0xFFD71920) == c ? "  ●" : ""));
            b.setTextColor(c);
            b.setOnClickListener(v -> { prefs.edit().putInt("accent", c).apply(); recreate(); });
            row.addView(b);
        }
        return row;
    }

    private TextView txt(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setPadding(0, 8, 0, 8);
        return t;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s); b.setAllCaps(false);
        return b;
    }
}
