package com.mono.keys;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Trigger -> saved text snippets. Values are stored AES/GCM-encrypted
 *  (CryptoBox, Android Keystore key); trigger keys stay readable so the
 *  keyboard can match while typing. Nothing leaves the device: the app has
 *  no INTERNET permission and backups are disabled. */
public final class ShortcutStore {
    private static final String PREFS = "shortcuts";
    private final SharedPreferences prefs;
    private final CryptoBox box;

    public ShortcutStore(Context ctx, CryptoBox box) {
        this.prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.box = box;
    }

    /** Test-only: in-memory map backed store. */
    ShortcutStore(SharedPreferences prefs, CryptoBox box) { this.prefs = prefs; this.box = box; }

    public void save(String trigger, String text) throws Exception {
        prefs.edit().putString(trigger, box.encrypt(text)).apply();
    }

    public void delete(String trigger) { prefs.edit().remove(trigger).apply(); }

    public List<String> triggers() {
        List<String> list = new ArrayList<>(prefs.getAll().keySet());
        Collections.sort(list);
        return list;
    }

    public String get(String trigger) {
        String enc = prefs.getString(trigger, null);
        if (enc == null) return null;
        try { return box.decrypt(enc); } catch (Exception e) { return null; }
    }

    /** Triggers that start with the typed prefix (case-insensitive). */
    public List<String> match(String prefix) {
        List<String> hits = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) return hits;
        String p = prefix.toLowerCase();
        for (String t : triggers()) if (t.toLowerCase().startsWith(p)) hits.add(t);
        return hits;
    }
}
