package dev.alien2003.structurecompass.common.math;

import java.util.Locale;

/** Pure text formatting helpers (no Minecraft dependencies, unit-tested). */
public final class TextFormat {
    /** Placeholder shown when the Y coordinate of a structure is not known yet. */
    public static final String UNKNOWN_Y = "~";

    private TextFormat() {
    }

    /** "523 m", "12 345 m" (thin grouping with regular spaces). Unit is localized by the caller. */
    public static String meters(int meters, String unit) {
        return groupThousands(meters) + " " + unit;
    }

    /** Groups digits by three with spaces: 1234567 -> "1 234 567", -1234 -> "-1 234". */
    public static String groupThousands(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder sb = new StringBuilder(digits.length() + digits.length() / 3 + 1);
        if (value < 0) {
            sb.append('-');
        }
        int lead = digits.length() % 3;
        if (lead == 0) {
            lead = 3;
        }
        sb.append(digits, 0, lead);
        for (int i = lead; i < digits.length(); i += 3) {
            sb.append(' ').append(digits, i, i + 3);
        }
        return sb.toString();
    }

    /** "X 100  Y 64  Z -200" or with unknown Y: "X 100  Y ~  Z -200". */
    public static String coordinates(int x, int y, boolean yKnown, int z) {
        return "X " + x + "  Y " + (yKnown ? Integer.toString(y) : UNKNOWN_Y) + "  Z " + z;
    }

    /** Coordinates suitable for the clipboard / commands: "100 64 -200" or "100 ~ -200". */
    public static String clipboardCoordinates(int x, int y, boolean yKnown, int z) {
        return x + " " + (yKnown ? Integer.toString(y) : UNKNOWN_Y) + " " + z;
    }

    /**
     * Human readable fallback name from a registry path, e.g. "village_plains" -> "Village Plains",
     * "ruins/big-tower" -> "Ruins Big Tower".
     */
    public static String humanize(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(path.length());
        boolean upperNext = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_' || c == '/' || c == '-' || c == '.' || c == ' ') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') {
                    sb.append(' ');
                }
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == ' ') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }

    /** Lower-cased search normalisation used by the structure screen filter. */
    public static String normalizeForSearch(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }
}
