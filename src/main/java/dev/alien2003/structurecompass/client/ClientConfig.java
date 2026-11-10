package dev.alien2003.structurecompass.client;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.common.math.Colors;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Client configuration ({@code config/structurecompass-client.toml}), editable from the NeoForge mod list. */
public final class ClientConfig {
    public enum HudAnchor implements TranslatableEnum {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        @Override
        public Component getTranslatedName() {
            return Component.translatable(T + "anchor." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Prefix of all config-screen translation keys. */
    private static final String T = StructureCompass.MOD_ID + ".configuration.";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- search ---------------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue RESULTS_PER_TYPE;
    public static final ModConfigSpec.IntValue RESCAN_DISTANCE;

    // --- hud ------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.IntValue HUD_ENTRIES;
    public static final ModConfigSpec.EnumValue<HudAnchor> HUD_ANCHOR;
    public static final ModConfigSpec.IntValue HUD_OFFSET_X;
    public static final ModConfigSpec.IntValue HUD_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.BooleanValue HUD_SHOW_COORDINATES;
    public static final ModConfigSpec.DoubleValue HUD_BACKGROUND_OPACITY;
    public static final ModConfigSpec.BooleanValue COMPASS_BAR_ENABLED;
    public static final ModConfigSpec.DoubleValue COMPASS_BAR_WIDTH;
    public static final ModConfigSpec.IntValue COMPASS_BAR_RANGE;

    // --- world ----------------------------------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue WORLD_ENABLED;
    public static final ModConfigSpec.BooleanValue WORLD_BEAMS;
    public static final ModConfigSpec.BooleanValue WORLD_LABELS;
    public static final ModConfigSpec.BooleanValue WORLD_BOXES;
    public static final ModConfigSpec.BooleanValue WORLD_PIECE_BOXES;
    public static final ModConfigSpec.IntValue WORLD_BOX_DISTANCE;
    public static final ModConfigSpec.IntValue WORLD_MARKER_DISTANCE;
    public static final ModConfigSpec.IntValue WORLD_MAX_MARKERS;

    // --- colors ---------------------------------------------------------------------------------------------
    public static final ModConfigSpec.ConfigValue<String> TRACKED_COLOR;
    public static final ModConfigSpec.ConfigValue<String> TEXT_COLOR;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COLOR_OVERRIDES;

    // --- filter ---------------------------------------------------------------------------------------------
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_TYPES;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Where and how much to search.").translation(T + "search").push("search");
        SEARCH_RADIUS = BUILDER
                .comment("Search radius around the player, in blocks.")
                .translation(T + "search.radius").defineInRange("radius", 4000, 256, 32000);
        RESULTS_PER_TYPE = BUILDER
                .comment("How many nearest instances to find for every structure type.")
                .translation(T + "search.resultsPerType").defineInRange("resultsPerType", 3, 1, 10);
        RESCAN_DISTANCE = BUILDER
                .comment("Search again after moving this many blocks away from the last search position.")
                .translation(T + "search.rescanDistance").defineInRange("rescanDistance", 128, 16, 4096);
        BUILDER.pop();

        BUILDER.comment("On-screen list, compass bar and target arrow.").translation(T + "hud").push("hud");
        HUD_ENABLED = BUILDER
                .comment("Show the list of nearest structures on screen.")
                .translation(T + "hud.enabled").define("enabled", true);
        HUD_ENTRIES = BUILDER
                .comment("Number of structures listed in the HUD panel.")
                .translation(T + "hud.entries").defineInRange("entries", 5, 1, 20);
        HUD_ANCHOR = BUILDER
                .comment("Screen corner the HUD panel is attached to.")
                .translation(T + "hud.anchor").defineEnum("anchor", HudAnchor.TOP_LEFT);
        HUD_OFFSET_X = BUILDER
                .comment("Horizontal distance of the HUD panel from its corner, in GUI pixels.")
                .translation(T + "hud.offsetX").defineInRange("offsetX", 4, 0, 4000);
        HUD_OFFSET_Y = BUILDER
                .comment("Vertical distance of the HUD panel from its corner, in GUI pixels.")
                .translation(T + "hud.offsetY").defineInRange("offsetY", 24, 0, 4000);
        HUD_SCALE = BUILDER
                .comment("Scale of the HUD panel.")
                .translation(T + "hud.scale").defineInRange("scale", 1.0, 0.5, 3.0);
        HUD_SHOW_COORDINATES = BUILDER
                .comment("Show X/Y/Z coordinates in the HUD panel.")
                .translation(T + "hud.showCoordinates").define("showCoordinates", true);
        HUD_BACKGROUND_OPACITY = BUILDER
                .comment("Opacity of the HUD panel background (0 = transparent, 1 = opaque).")
                .translation(T + "hud.backgroundOpacity").defineInRange("backgroundOpacity", 0.45, 0.0, 1.0);
        COMPASS_BAR_ENABLED = BUILDER
                .comment("Show the compass bar with structure markers at the top of the screen.")
                .translation(T + "hud.compassBar").define("compassBar", true);
        COMPASS_BAR_WIDTH = BUILDER
                .comment("Width of the compass bar as a fraction of the screen width.")
                .translation(T + "hud.compassBarWidth").defineInRange("compassBarWidth", 0.4, 0.15, 1.0);
        COMPASS_BAR_RANGE = BUILDER
                .comment("Degrees to each side of the view direction covered by the compass bar.")
                .translation(T + "hud.compassBarRange").defineInRange("compassBarRange", 90, 30, 180);
        BUILDER.pop();

        BUILDER.comment("Beams, labels and bounding boxes drawn in the world.").translation(T + "world").push("world");
        WORLD_ENABLED = BUILDER
                .comment("Highlight structures in the world (beams, labels, bounding boxes).")
                .translation(T + "world.enabled").define("enabled", true);
        WORLD_BEAMS = BUILDER
                .comment("Draw a vertical beam at each structure, visible through blocks.")
                .translation(T + "world.beams").define("beams", true);
        WORLD_LABELS = BUILDER
                .comment("Draw the name and distance above each beam.")
                .translation(T + "world.labels").define("labels", true);
        WORLD_BOXES = BUILDER
                .comment("Draw the overall bounding box of generated structures nearby, visible through walls.")
                .translation(T + "world.boxes").define("boxes", true);
        WORLD_PIECE_BOXES = BUILDER
                .comment("Also draw the bounding boxes of the individual structure pieces.")
                .translation(T + "world.pieceBoxes").define("pieceBoxes", true);
        WORLD_BOX_DISTANCE = BUILDER
                .comment("Bounding boxes are drawn for structures closer than this many blocks.")
                .translation(T + "world.boxDistance").defineInRange("boxDistance", 192, 16, 1024);
        WORLD_MARKER_DISTANCE = BUILDER
                .comment("Beams/labels are drawn for structures closer than this many blocks (0 = whole search radius).")
                .translation(T + "world.markerDistance").defineInRange("markerDistance", 0, 0, 32000);
        WORLD_MAX_MARKERS = BUILDER
                .comment("Maximum number of beams/labels drawn at once (nearest first, the tracked target is always drawn).")
                .translation(T + "world.maxMarkers").defineInRange("maxMarkers", 8, 1, 64);
        BUILDER.pop();

        BUILDER.comment("Colors.").translation(T + "colors").push("colors");
        TRACKED_COLOR = BUILDER
                .comment("Color of the tracked target (#RRGGBB).")
                .translation(T + "colors.trackedColor").define("trackedColor", "#FFD84A", ClientConfig::isColor);
        TEXT_COLOR = BUILDER
                .comment("Text color of the HUD panel (#RRGGBB).")
                .translation(T + "colors.textColor").define("textColor", "#FFFFFF", ClientConfig::isColor);
        COLOR_OVERRIDES = BUILDER
                .comment("Per-type colors, entries like \"minecraft:village_plains=#55FF55\". Types without an entry use a built-in or generated color.")
                .translation(T + "colors.typeColors").defineListAllowEmpty("typeColors", List.of(), () -> "minecraft:village_plains=#55FF55", ClientConfig::isColorOverride);
        BUILDER.pop();

        BUILDER.comment("Which structure types are shown.").translation(T + "filter").push("filter");
        DISABLED_TYPES = BUILDER
                .comment("Structure types that are hidden (ids like \"minecraft:mineshaft\"). Everything else is shown. Also editable in the structure screen.")
                .translation(T + "filter.disabledTypes").defineListAllowEmpty("disabledTypes", List.of(), () -> "minecraft:mineshaft", ClientConfig::isIdentifier);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private ClientConfig() {
    }

    private static boolean isColor(Object o) {
        return o instanceof String s && Colors.isValid(s);
    }

    private static boolean isIdentifier(Object o) {
        return o instanceof String s && Identifier.tryParse(s) != null;
    }

    private static boolean isColorOverride(Object o) {
        if (!(o instanceof String s)) {
            return false;
        }
        int eq = s.indexOf('=');
        return eq > 0 && Identifier.tryParse(s.substring(0, eq).trim()) != null && Colors.isValid(s.substring(eq + 1));
    }

    // --- helpers used by the UI -------------------------------------------------------------------------------

    public static boolean isTypeEnabled(Identifier type) {
        return !TypeFilterCache.disabled().contains(type);
    }

    /** Enables/disables a type and saves the config file. */
    public static void setTypeEnabled(Identifier type, boolean enabled) {
        Set<Identifier> disabled = new HashSet<>(TypeFilterCache.disabled());
        boolean changed = enabled ? disabled.remove(type) : disabled.add(type);
        if (!changed) {
            return;
        }
        List<String> out = new ArrayList<>(disabled.size());
        for (Identifier id : disabled) {
            out.add(id.toString());
        }
        out.sort(String::compareTo);
        DISABLED_TYPES.set(out);
        DISABLED_TYPES.save();
        TypeFilterCache.invalidate();
    }

    public static void setAll(Iterable<Identifier> types, boolean enabled) {
        Set<Identifier> disabled = new HashSet<>(TypeFilterCache.disabled());
        for (Identifier type : types) {
            if (enabled) {
                disabled.remove(type);
            } else {
                disabled.add(type);
            }
        }
        List<String> out = new ArrayList<>(disabled.size());
        for (Identifier id : disabled) {
            out.add(id.toString());
        }
        out.sort(String::compareTo);
        DISABLED_TYPES.set(out);
        DISABLED_TYPES.save();
        TypeFilterCache.invalidate();
    }

    /** Parsed view of {@link #DISABLED_TYPES}, rebuilt only when the config changes. */
    public static final class TypeFilterCache {
        private static Set<Identifier> disabled;

        private TypeFilterCache() {
        }

        public static Set<Identifier> disabled() {
            Set<Identifier> set = disabled;
            if (set == null) {
                set = new HashSet<>();
                if (SPEC.isLoaded()) {
                    for (String s : DISABLED_TYPES.get()) {
                        Identifier id = Identifier.tryParse(s);
                        if (id != null) {
                            set.add(id);
                        }
                    }
                }
                disabled = set;
            }
            return set;
        }

        public static void invalidate() {
            disabled = null;
        }
    }
}
