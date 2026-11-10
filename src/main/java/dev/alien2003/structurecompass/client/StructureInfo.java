package dev.alien2003.structurecompass.client;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.common.math.Colors;
import dev.alien2003.structurecompass.common.math.TextFormat;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Display names and colors of structure types. Values are cached per id and rebuilt when the language or the
 * color config changes, so the render loop never formats or looks up translations.
 */
public final class StructureInfo {
    private static final Map<Identifier, String> NAMES = new HashMap<>();
    private static final Map<Identifier, Integer> COLORS = new HashMap<>();
    private static final Map<String, Integer> BUILTIN_COLORS = new HashMap<>();
    private static String languageCode = "";
    private static int overridesHash;

    static {
        String[][] builtin = {
                {"pillager_outpost", "#B0B0B0"}, {"mineshaft", "#C08A4A"}, {"mineshaft_mesa", "#E07A35"},
                {"mansion", "#A86F3F"}, {"jungle_pyramid", "#7BC043"}, {"desert_pyramid", "#EED67A"},
                {"igloo", "#D4F1FF"}, {"shipwreck", "#9C7A64"}, {"shipwreck_beached", "#B8977E"},
                {"swamp_hut", "#6E9E45"}, {"stronghold", "#A6B4F0"}, {"monument", "#2ED3E6"},
                {"ocean_ruin_cold", "#5CC8FA"}, {"ocean_ruin_warm", "#FFB955"}, {"fortress", "#E53935"},
                {"nether_fossil", "#F2F2F2"}, {"end_city", "#D59CE0"}, {"buried_treasure", "#FFD740"},
                {"bastion_remnant", "#C9B458"}, {"village_plains", "#5FD35F"}, {"village_desert", "#D8C060"},
                {"village_savanna", "#B6C85A"}, {"village_snowy", "#BEEFE0"}, {"village_taiga", "#4FAF7A"},
                {"ruined_portal", "#9575CD"}, {"ruined_portal_desert", "#B39DDB"}, {"ruined_portal_jungle", "#7E57C2"},
                {"ruined_portal_swamp", "#8E6CC9"}, {"ruined_portal_mountain", "#A58BE0"},
                {"ruined_portal_ocean", "#7C6FE0"}, {"ruined_portal_nether", "#B061E8"},
                {"ancient_city", "#1DE9B6"}, {"trail_ruins", "#D9A06A"}, {"trial_chambers", "#F0803C"},
        };
        for (String[] e : builtin) {
            BUILTIN_COLORS.put("minecraft:" + e[0], Colors.parse(e[1], 0xFFFFFFFF));
        }
    }

    private StructureInfo() {
    }

    /** Translation key used for a structure type. */
    public static String translationKey(Identifier id) {
        return StructureCompass.MOD_ID + ".structure." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    /** Localized name; falls back to a humanized id for structures of other mods/datapacks. */
    public static String name(Identifier id) {
        checkLanguage();
        String cached = NAMES.get(id);
        if (cached == null) {
            cached = computeName(id);
            NAMES.put(id, cached);
        }
        return cached;
    }

    private static String computeName(Identifier id) {
        String key = translationKey(id);
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        // Some mods ship their own names under a "structure.<ns>.<path>" key.
        String alt = "structure." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        if (I18n.exists(alt)) {
            return I18n.get(alt);
        }
        return TextFormat.humanize(id.getPath());
    }

    /** "Name" for vanilla, "Name (modid)" for other namespaces - used where the origin matters (screen). */
    public static String nameWithNamespace(Identifier id) {
        String name = name(id);
        return Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace()) ? name : name + " (" + id.getNamespace() + ")";
    }

    /** Opaque ARGB color: config override, then built-in palette, then a stable color derived from the id. */
    public static int color(Identifier id) {
        checkOverrides();
        Integer cached = COLORS.get(id);
        if (cached == null) {
            cached = computeColor(id);
            COLORS.put(id, cached);
        }
        return cached;
    }

    private static int computeColor(Identifier id) {
        String key = id.toString();
        if (ClientConfig.SPEC.isLoaded()) {
            for (String entry : ClientConfig.COLOR_OVERRIDES.get()) {
                int eq = entry.indexOf('=');
                if (eq > 0 && entry.substring(0, eq).trim().equals(key)) {
                    int c = Colors.parse(entry.substring(eq + 1), 0);
                    if (c != 0) {
                        return c | 0xFF000000;
                    }
                }
            }
        }
        Integer builtin = BUILTIN_COLORS.get(key);
        return builtin != null ? builtin : Colors.fromId(key);
    }

    private static void checkLanguage() {
        String code = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        if (!code.equals(languageCode)) {
            languageCode = code;
            NAMES.clear();
        }
    }

    private static void checkOverrides() {
        int hash = ClientConfig.SPEC.isLoaded() ? hashOf(ClientConfig.COLOR_OVERRIDES.get()) : 0;
        if (hash != overridesHash) {
            overridesHash = hash;
            COLORS.clear();
        }
    }

    private static int hashOf(List<? extends String> list) {
        return list.hashCode();
    }

    /** Drops all caches (resource reload, config reload). */
    public static void invalidate() {
        NAMES.clear();
        COLORS.clear();
        languageCode = "";
    }
}
