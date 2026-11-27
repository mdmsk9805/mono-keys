package com.mono.keys;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Offline English word list, ordered most frequent first. Suggestions only; never changes text by itself. */
public final class WordDictionary {
    private final List<String> words = new ArrayList<>();
    private final Set<String> set = new HashSet<>();

    public static WordDictionary load(InputStream in) {
        WordDictionary d = new WordDictionary();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String l;
            while ((l = r.readLine()) != null) { l = l.trim(); if (!l.isEmpty()) { d.words.add(l); d.set.add(l); } }
        } catch (IOException ignored) { }
        return d;
    }

    public int size() { return words.size(); }
    public boolean contains(String w) { return set.contains(w); }

    /** Up to max suggestions: completions of the prefix first, then close spellings (one edit away). */
    public List<String> suggest(String typed, int max) {
        List<String> out = new ArrayList<>();
        if (typed == null || typed.isEmpty() || max <= 0) return out;
        String t = typed.toLowerCase();
        boolean known = set.contains(t);
        for (String w : words) {
            if (out.size() >= max) break;
            if (w.length() > t.length() && w.startsWith(t)) out.add(w);
        }
        if (!known && t.length() >= 3) {
            for (String w : words) {
                if (out.size() >= max) break;
                if (Math.abs(w.length() - t.length()) <= 1 && !out.contains(w) && oneEdit(t, w)) out.add(w);
            }
        }
        return out;
    }

    /** True if a and b differ by one insert, delete, replace, or adjacent swap. */
    static boolean oneEdit(String a, String b) {
        int la = a.length(), lb = b.length();
        if (a.equals(b)) return false;
        int i = 0;
        while (i < la && i < lb && a.charAt(i) == b.charAt(i)) i++;
        if (la == lb) {
            if (a.substring(i + 1).equals(b.substring(i + 1))) return true;
            return i + 1 < la && a.charAt(i) == b.charAt(i + 1) && a.charAt(i + 1) == b.charAt(i)
                && a.substring(i + 2).equals(b.substring(i + 2));
        }
        if (la + 1 == lb) return a.substring(i).equals(b.substring(i + 1));
        if (lb + 1 == la) return b.substring(i).equals(a.substring(i + 1));
        return false;
    }
}
