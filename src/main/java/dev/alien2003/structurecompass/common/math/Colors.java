package dev.alien2003.structurecompass.common.math;

/** Pure ARGB color helpers (no Minecraft dependencies, unit-tested). */
public final class Colors {
    private Colors() {
    }

    /** Opaque ARGB color derived deterministically from an id string (stable across runs). */
    public static int fromId(String id) {
        // FNV-1a: stable, independent of String.hashCode implementation details.
        int h = 0x811C9DC5;
        for (int i = 0; i < id.length(); i++) {
            h ^= id.charAt(i);
            h *= 0x01000193;
        }
        float hue = ((h >>> 8) & 0xFFFF) / 65536.0f;
        float sat = 0.55f + ((h & 0xFF) / 255.0f) * 0.25f;
        return hsvToArgb(hue, sat, 0.95f);
    }

    /** HSV (all components 0..1) to opaque ARGB. */
    public static int hsvToArgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6.0f;
        int sector = (int) hh;
        float f = hh - sector;
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * f);
        float t = v * (1.0f - s * (1.0f - f));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return 0xFF000000 | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    private static int clamp255(float c) {
        return Math.max(0, Math.min(255, Math.round(c * 255.0f)));
    }

    /**
     * Parses "#RRGGBB", "RRGGBB", "#AARRGGBB" or "0xRRGGBB". RGB-only inputs become opaque.
     *
     * @return the ARGB color or {@code fallback} when the text is not a valid color
     */
    public static int parse(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() != 6 && s.length() != 8) {
            return fallback;
        }
        long value;
        try {
            value = Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
        return s.length() == 6 ? (int) (0xFF000000L | value) : (int) value;
    }

    /** True when {@link #parse} accepts the text. */
    public static boolean isValid(String text) {
        return parse(text, 1) != 1 || parse(text, 2) != 2;
    }

    /** "#RRGGBB" for an ARGB color (alpha dropped). */
    public static String toHex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    /** Replaces the alpha channel. */
    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** Multiplies RGB by a factor (0..1+), keeps alpha. */
    public static int scaleRgb(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public static float red(int argb) {
        return ((argb >> 16) & 0xFF) / 255.0f;
    }

    public static float green(int argb) {
        return ((argb >> 8) & 0xFF) / 255.0f;
    }

    public static float blue(int argb) {
        return (argb & 0xFF) / 255.0f;
    }

    public static float alpha(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0f;
    }
}
