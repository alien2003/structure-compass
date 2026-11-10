package dev.alien2003.structurecompass.client;

import dev.alien2003.structurecompass.common.math.Colors;

/**
 * Primitive snapshot of {@link ClientConfig}, refreshed once per client tick, so render code reads plain fields
 * (no boxing, no config lookups) every frame. Runtime toggles from hot keys live here as well.
 */
public final class ClientSettings {
    public static boolean hudEnabled = true;
    public static int hudEntries = 5;
    public static ClientConfig.HudAnchor hudAnchor = ClientConfig.HudAnchor.TOP_LEFT;
    public static int hudOffsetX = 4;
    public static int hudOffsetY = 24;
    public static float hudScale = 1.0f;
    public static boolean hudShowCoordinates = true;
    public static int hudBackground = 0x73000000;
    public static boolean compassBar = true;
    public static float compassBarWidth = 0.4f;
    public static float compassBarRange = 90.0f;

    public static boolean worldEnabled = true;
    public static boolean worldBeams = true;
    public static boolean worldLabels = true;
    public static boolean worldBoxes = true;
    public static boolean worldPieceBoxes = true;
    public static int boxDistance = 192;
    public static int markerDistance = 0;
    public static int maxMarkers = 8;

    public static int trackedColor = 0xFFFFD84A;
    public static int textColor = 0xFFFFFFFF;

    private ClientSettings() {
    }

    public static void refresh() {
        if (!ClientConfig.SPEC.isLoaded()) {
            return;
        }
        hudEnabled = ClientConfig.HUD_ENABLED.get();
        hudEntries = ClientConfig.HUD_ENTRIES.get();
        hudAnchor = ClientConfig.HUD_ANCHOR.get();
        hudOffsetX = ClientConfig.HUD_OFFSET_X.get();
        hudOffsetY = ClientConfig.HUD_OFFSET_Y.get();
        hudScale = (float) (double) ClientConfig.HUD_SCALE.get();
        hudShowCoordinates = ClientConfig.HUD_SHOW_COORDINATES.get();
        hudBackground = Colors.withAlpha(0, (int) Math.round(ClientConfig.HUD_BACKGROUND_OPACITY.get() * 255.0));
        compassBar = ClientConfig.COMPASS_BAR_ENABLED.get();
        compassBarWidth = (float) (double) ClientConfig.COMPASS_BAR_WIDTH.get();
        compassBarRange = ClientConfig.COMPASS_BAR_RANGE.get();

        worldEnabled = ClientConfig.WORLD_ENABLED.get();
        worldBeams = ClientConfig.WORLD_BEAMS.get();
        worldLabels = ClientConfig.WORLD_LABELS.get();
        worldBoxes = ClientConfig.WORLD_BOXES.get();
        worldPieceBoxes = ClientConfig.WORLD_PIECE_BOXES.get();
        boxDistance = ClientConfig.WORLD_BOX_DISTANCE.get();
        markerDistance = ClientConfig.WORLD_MARKER_DISTANCE.get();
        maxMarkers = ClientConfig.WORLD_MAX_MARKERS.get();

        trackedColor = Colors.parse(ClientConfig.TRACKED_COLOR.get(), 0xFFFFD84A) | 0xFF000000;
        textColor = Colors.parse(ClientConfig.TEXT_COLOR.get(), 0xFFFFFFFF) | 0xFF000000;
    }
}
