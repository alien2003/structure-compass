package dev.alien2003.structurecompass.client.hud;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import org.jspecify.annotations.Nullable;

/**
 * Localized HUD strings, re-resolved only when a new language has actually been loaded (keyed on the
 * {@link Language} instance, which is swapped after the resource reload), so nothing is looked up per frame.
 */
public final class HudText {
    public static String north = "N";
    public static String east = "E";
    public static String south = "S";
    public static String west = "W";
    public static String searching = "";
    public static String nothing = "";
    private static @Nullable Language language;

    private HudText() {
    }

    public static void refresh() {
        Language current = Language.getInstance();
        if (current == language) {
            return;
        }
        language = current;
        north = I18n.get("structurecompass.hud.north");
        east = I18n.get("structurecompass.hud.east");
        south = I18n.get("structurecompass.hud.south");
        west = I18n.get("structurecompass.hud.west");
        searching = I18n.get("structurecompass.hud.searching");
        nothing = I18n.get("structurecompass.hud.nothing");
    }
}
