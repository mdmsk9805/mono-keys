package com.mono.keys;

import java.util.LinkedHashMap;
import java.util.Map;

/** Simplified Avro-style phonetic roman-to-Bangla converter. Greedy
 *  longest-match tokenizer; consonant clusters join with hasanta (্).
 *  Not a full Avro port - common words covered, edge cases may differ. */
public final class BanglaTransliterator {
    private static final Map<String, String> CONS = new LinkedHashMap<>();
    private static final Map<String, String[]> VOW = new LinkedHashMap<>(); // {independent, kar}
    static {
        CONS.put("shh", "ষ"); CONS.put("Rh", "ঢ়");
        CONS.put("kh", "খ"); CONS.put("gh", "ঘ"); CONS.put("Ng", "ঙ");
        CONS.put("ch", "ছ"); CONS.put("jh", "ঝ"); CONS.put("NG", "ঞ");
        CONS.put("Th", "ঠ"); CONS.put("Dh", "ঢ"); CONS.put("th", "থ");
        CONS.put("dh", "ধ"); CONS.put("ph", "ফ"); CONS.put("bh", "ভ");
        CONS.put("sh", "শ"); CONS.put("Sh", "ষ"); CONS.put("ng", "ং");
        CONS.put("k", "ক"); CONS.put("g", "গ"); CONS.put("c", "চ");
        CONS.put("j", "জ"); CONS.put("T", "ট"); CONS.put("D", "ড");
        CONS.put("N", "ণ"); CONS.put("t", "ত"); CONS.put("d", "দ");
        CONS.put("n", "ন"); CONS.put("p", "প"); CONS.put("f", "ফ");
        CONS.put("b", "ব"); CONS.put("v", "ভ"); CONS.put("m", "ম");
        CONS.put("z", "য"); CONS.put("y", "য়"); CONS.put("r", "র");
        CONS.put("l", "ল"); CONS.put("s", "স"); CONS.put("S", "ষ");
        CONS.put("h", "হ"); CONS.put("R", "ড়"); CONS.put("Y", "য়");
        VOW.put("rri", new String[]{"ঋ", "ৃ"});
        VOW.put("OI", new String[]{"ঐ", "ৈ"});
        VOW.put("ou", new String[]{"ঔ", "ৌ"});
        VOW.put("aa", new String[]{"আ", "া"});
        VOW.put("ee", new String[]{"ঈ", "ী"});
        VOW.put("oo", new String[]{"ঊ", "ূ"});
        VOW.put("a", new String[]{"আ", "া"});
        VOW.put("i", new String[]{"ই", "ি"});
        VOW.put("I", new String[]{"ঈ", "ী"});
        VOW.put("u", new String[]{"উ", "ু"});
        VOW.put("U", new String[]{"ঊ", "ূ"});
        VOW.put("e", new String[]{"এ", "ে"});
        VOW.put("E", new String[]{"এ", "ে"});
        VOW.put("O", new String[]{"ও", "ো"});
        VOW.put("o", new String[]{"অ", ""});
    }
    private static final String HASANTA = "্";

    private BanglaTransliterator() {}

    /** Converts a romanized word to Bangla. Non-mapped chars pass through. */
    public static String convert(String roman) {
        StringBuilder out = new StringBuilder();
        int i = 0, n = roman.length();
        boolean prevConsonant = false; // last emitted unit can take a kar / join
        boolean prevJoinable = false;  // last unit was a consonant letter
        while (i < n) {
            String rest = roman.substring(i);
            String cons = match(CONS, rest);
            if (cons != null) {
                String bn = CONS.get(cons);
                boolean isSign = bn.equals("ং");
                if (prevJoinable && !isSign) out.append(HASANTA);
                out.append(bn);
                prevConsonant = !isSign;
                prevJoinable = !isSign;
                i += cons.length();
                continue;
            }
            String vow = match(VOW, rest);
            if (vow != null) {
                String[] forms = VOW.get(vow);
                out.append(prevConsonant ? forms[1] : forms[0]);
                prevConsonant = false; prevJoinable = false;
                i += vow.length();
                continue;
            }
            if (rest.startsWith("..")) { out.append("।"); i += 2; prevConsonant = prevJoinable = false; continue; }
            char c = roman.charAt(i);
            if (rest.startsWith("w")) { // w = ও, vowel-like
                out.append(prevConsonant ? "ো" : "ও");
                prevConsonant = false; prevJoinable = false; i++; continue;
            }
            out.append(c);
            if (!Character.isLetter(c)) { prevConsonant = false; prevJoinable = false; }
            i++;
        }
        return out.toString();
    }

    private static String match(Map<String, ?> map, String rest) {
        for (String k : map.keySet()) if (rest.startsWith(k)) return k;
        return null;
    }
}
