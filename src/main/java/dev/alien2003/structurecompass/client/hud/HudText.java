package dev.alien2003.structurecompass.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

/** Localized HUD strings, re-resolved only when the language changes (no lookups per frame). */
public final class HudText {
    public static String north = "N";
    public static String east = "E";
    public static String south = "S";
    public static String west = "W";
    public static String searching = "";
    public static String nothing = "";
    public static String tracking = "";
    public static String yUnknownHint = "";
    private static String language = "";

    private HudText() {
    }

    public static void refresh() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        if (code.equals(language)) {
            return;
        }
        language = code;
        north = I18n.get("structurecompass.hud.north");
        east = I18n.get("structurecompass.hud.east");
        south = I18n.get("structurecompass.hud.south");
        west = I18n.get("structurecompass.hud.west");
        searching = I18n.get("structurecompass.hud.searching");
        nothing = I18n.get("structurecompass.hud.nothing");
        tracking = I18n.get("structurecompass.hud.tracking");
        yUnknownHint = I18n.get("structurecompass.hud.y_unknown");
    }
}
