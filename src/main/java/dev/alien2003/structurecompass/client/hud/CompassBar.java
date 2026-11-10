package dev.alien2003.structurecompass.client.hud;

import dev.alien2003.structurecompass.client.ClientSettings;
import dev.alien2003.structurecompass.client.data.NearbyView;
import dev.alien2003.structurecompass.common.math.Colors;
import dev.alien2003.structurecompass.common.math.NavMath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/** Horizontal compass strip at the top of the screen with cardinal directions and structure markers. */
final class CompassBar {
    private static final int HEIGHT = 14;
    private static final int MARKER = 7;
    private static final int TRACKED_MARKER = 11;

    private CompassBar() {
    }

    /** @return the y coordinate just below the bar */
    static int render(GuiGraphics g, Font font, float yaw, double px, double pz, int top) {
        int screenW = g.guiWidth();
        int width = Math.max(80, Math.round(screenW * ClientSettings.compassBarWidth));
        int half = width / 2;
        int cx = screenW / 2;
        int left = cx - half;
        int right = cx + half;
        float range = ClientSettings.compassBarRange;

        g.fill(left, top, right, top + HEIGHT, 0x80000000);
        g.fill(left, top + HEIGHT, right, top + HEIGHT + 1, 0x40FFFFFF);

        // Tick marks every 15 degrees, cardinal letters every 90.
        for (int deg = 0; deg < 360; deg += 15) {
            // yaw of a world direction: north = 180, east = -90 (270), south = 0, west = 90 (heading -> yaw: h - 180)
            float dirYaw = deg - 180.0f;
            float rel = NavMath.wrapDegrees(dirYaw - yaw);
            float off = NavMath.compassBarOffset(rel, range, half);
            if (Float.isNaN(off)) {
                continue;
            }
            int x = cx + Math.round(off);
            if (deg % 90 == 0) {
                String letter = deg == 0 ? HudText.north : deg == 90 ? HudText.east : deg == 180 ? HudText.south : HudText.west;
                int color = deg == 0 ? 0xFFFF5555 : 0xFFFFFFFF;
                g.drawCenteredString(font, letter, x, top + 3, color);
            } else {
                int h = deg % 45 == 0 ? 5 : 3;
                g.fill(x, top + HEIGHT - h, x + 1, top + HEIGHT, 0xA0FFFFFF);
            }
        }

        // Structure markers: nearest first; the nearest ones are drawn last so they end up on top.
        int n = Math.min(NearbyView.count(), Math.max(ClientSettings.hudEntries, ClientSettings.maxMarkers));
        for (int i = n - 1; i >= 0; i--) {
            NearbyView.Row r = NearbyView.row(i);
            if (r.tracked) {
                continue;
            }
            drawMarker(g, cx, half, range, yaw, px, pz, r, MARKER, r.color, false, top);
        }
        NearbyView.Row tracked = NearbyView.tracked();
        if (tracked != null) {
            drawMarker(g, cx, half, range, yaw, px, pz, tracked, TRACKED_MARKER, ClientSettings.trackedColor, true, top);
        }
        // Center notch.
        g.fill(cx, top - 1, cx + 1, top + HEIGHT + 2, 0xFFFFFFFF);
        return top + HEIGHT + 1;
    }

    private static void drawMarker(GuiGraphics g, int cx, int half, float range, float yaw, double px, double pz,
                                   NearbyView.Row r, int size, int color, boolean tracked, int top) {
        float rel = NavMath.relativeYaw(yaw, r.targetX() - px, r.targetZ() - pz);
        boolean outside = Math.abs(rel) > range;
        float off = NavMath.compassBarOffsetClamped(rel, range, half - size / 2.0f);
        int x = cx + Math.round(off) - size / 2;
        int y = top + (HEIGHT - size) / 2;
        int tint = outside ? Colors.withAlpha(color, 0x70) : color;
        g.blit(RenderPipelines.GUI_TEXTURED, HudLayer.MARKER, x, y, 0.0f, 0.0f, size, size, 16, 16, 16, 16, tint);
        if (tracked && !outside) {
            g.fill(x + size / 2, top + HEIGHT, x + size / 2 + 1, top + HEIGHT + 3, color);
        }
    }
}
