package com.mono.keys;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps plain English letters/digits to Unicode Mathematical Alphanumeric
 *  Symbols so typed text comes out styled (𝐛𝐨𝐥𝐝, 𝑖𝑡𝑎𝑙𝑖𝑐, ...) in apps
 *  that render Unicode. Pure text substitution - no app formatting. */
public final class UnicodeStyles {
    public static final String[] NAMES = {
        "Normal", "Bold", "Italic", "Bold Italic", "Script", "Double", "Sans Bold", "Mono"
    };
    public static final Map<String, int[]> RANGES = new LinkedHashMap<>();
    static {
        RANGES.put("Normal", null);
        RANGES.put("Bold", new int[]{0x1D400, 0x1D41A, 0x1D7CE});
        RANGES.put("Italic", new int[]{0x1D434, 0x1D44E, -1});
        RANGES.put("Bold Italic", new int[]{0x1D468, 0x1D482, -1});
        RANGES.put("Script", new int[]{0x1D49C, 0x1D4B6, -1});
        RANGES.put("Double", new int[]{0x1D538, 0x1D552, 0x1D7D8});
        RANGES.put("Sans Bold", new int[]{0x1D5D4, 0x1D5EE, 0x1D7EC});
        RANGES.put("Mono", new int[]{0x1D670, 0x1D68A, 0x1D7F6});
    }
    // Capital/small exceptions where the math block has holes (letters kept in Letterlike Symbols).
    private static final String ITALIC_EXC = "h";
    private static final int[] ITALIC_EXC_CP = {0x210E};
    private static final String SCRIPT_EXC = "BEFHILMR ego";
    private static final int[] SCRIPT_EXC_CP = {0x212C,0x2130,0x2131,0x210B,0x2110,0x2112,0x2133,0x211B,0x212F,0x210A,0x2134};
    private static final String DOUBLE_EXC = "CHNPQRZ";
    private static final int[] DOUBLE_EXC_CP = {0x2102,0x210D,0x2115,0x2119,0x211A,0x211D,0x2124};

    private UnicodeStyles() {}

    /** Converts one char under the named style; returns null when no mapping. */
    public static String map(char c, String style) {
        if (style == null || style.equals("Normal")) return null;
        int[] r = RANGES.get(style);
        if (r == null) return null;
        String exc; int[] excCp;
        switch (style) {
            case "Italic": exc = ITALIC_EXC; excCp = ITALIC_EXC_CP; break;
            case "Script": exc = SCRIPT_EXC; excCp = SCRIPT_EXC_CP; break;
            case "Double": exc = DOUBLE_EXC; excCp = DOUBLE_EXC_CP; break;
            default: exc = ""; excCp = new int[0];
        }
        int e = exc.indexOf(c);
        if (e >= 0) return new String(Character.toChars(excCp[e]));
        if (c >= 'A' && c <= 'Z') return new String(Character.toChars(r[0] + c - 'A'));
        if (c >= 'a' && c <= 'z') return new String(Character.toChars(r[1] + c - 'a'));
        if (c >= '0' && c <= '9' && r[2] >= 0) return new String(Character.toChars(r[2] + c - '0'));
        return null;
    }

    public static String convert(String s, String style) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String m = map(c, style);
            b.append(m != null ? m : c);
        }
        return b.toString();
    }
}
