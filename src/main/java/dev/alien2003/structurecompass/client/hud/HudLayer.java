package dev.alien2003.structurecompass.client.hud;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.client.ClientSession;
import dev.alien2003.structurecompass.client.ClientSettings;
import dev.alien2003.structurecompass.client.data.ClientStructureData;
import dev.alien2003.structurecompass.client.data.NearbyView;
import dev.alien2003.structurecompass.common.math.Colors;
import dev.alien2003.structurecompass.common.math.NavMath;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * HUD: the compass bar at the top of the screen, the list panel with the nearest structures and the big arrow of
 * the tracked target. Reads only pre-computed fields from {@link NearbyView} and {@link ClientSettings}; no
 * allocations happen here.
 */
public final class HudLayer {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "hud");
    static final Identifier ARROW = Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "textures/gui/arrow.png");
    static final Identifier MARKER = Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "textures/gui/marker.png");

    private static final int ROW_HEIGHT = 11;
    private static final int COORD_HEIGHT = 9;
    private static final int PADDING = 3;
    private static final int SWATCH = 6;
    private static final int ARROW_SIZE = 9;
    private static final int GAP = 6;
    private static final int COORD_COLOR = 0xFFB8B8B8;

    private HudLayer() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ClientSession.isActive() || mc.options.hideGui || mc.debugEntries.isOverlayVisible()) {
            return;
        }
        if (!ClientSettings.hudEnabled) {
            return;
        }
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) {
            return; // keep menus (including the structure screen) clean; stay visible while chatting
        }
        float pt = delta.getGameTimeDeltaPartialTick(false);
        float yaw = player.getViewYRot(pt);
        double px = Mth.lerp(pt, player.xo, player.getX());
        double pz = Mth.lerp(pt, player.zo, player.getZ());
        Font font = mc.font;

        g.nextStratum();
        int barBottom = 0;
        if (ClientSettings.compassBar) {
            barBottom = CompassBar.render(g, font, yaw, px, pz);
        }
        renderTracked(g, font, yaw, px, pz, barBottom);
        renderPanel(g, font, yaw, px, pz);
    }

    private static void renderPanel(GuiGraphics g, Font font, float yaw, double px, double pz) {
        int rows = Math.min(ClientSettings.hudEntries, NearbyView.count());
        boolean coords = ClientSettings.hudShowCoordinates;
        int rowHeight = ROW_HEIGHT + (coords ? COORD_HEIGHT : 0);

        String status = null;
        if (rows == 0) {
            ClientStructureData.DimensionData data = Minecraft.getInstance().level == null ? null
                    : ClientStructureData.get(Minecraft.getInstance().level.dimension());
            status = data == null || data.scanning ? HudText.searching : HudText.nothing;
        }

        int nameW = 0;
        int distW = 0;
        int coordW = 0;
        for (int i = 0; i < rows; i++) {
            NearbyView.Row r = NearbyView.row(i);
            nameW = Math.max(nameW, r.nameWidth);
            distW = Math.max(distW, r.distanceWidth);
            coordW = Math.max(coordW, r.coordsWidth);
        }
        int textLeft = SWATCH + 2 + ARROW_SIZE + 3;
        int width;
        int height;
        if (status != null) {
            width = PADDING * 2 + font.width(status);
            height = PADDING * 2 + ROW_HEIGHT - 2;
        } else {
            width = PADDING * 2 + Math.max(textLeft + nameW + GAP + distW, coords ? textLeft + coordW : 0);
            height = PADDING * 2 + rows * rowHeight - 2;
        }

        float scale = ClientSettings.hudScale;
        float sw = width * scale;
        float sh = height * scale;
        float x;
        float y;
        switch (ClientSettings.hudAnchor) {
            case TOP_RIGHT -> {
                x = g.guiWidth() - ClientSettings.hudOffsetX - sw;
                y = ClientSettings.hudOffsetY;
            }
            case BOTTOM_LEFT -> {
                x = ClientSettings.hudOffsetX;
                y = g.guiHeight() - ClientSettings.hudOffsetY - sh;
            }
            case BOTTOM_RIGHT -> {
                x = g.guiWidth() - ClientSettings.hudOffsetX - sw;
                y = g.guiHeight() - ClientSettings.hudOffsetY - sh;
            }
            default -> {
                x = ClientSettings.hudOffsetX;
                y = ClientSettings.hudOffsetY;
            }
        }

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        if ((ClientSettings.hudBackground >>> 24) != 0) {
            g.fill(0, 0, width, height, ClientSettings.hudBackground);
        }
        if (status != null) {
            g.drawString(font, status, PADDING, PADDING, ClientSettings.textColor, true);
            g.pose().popMatrix();
            return;
        }
        int textColor = ClientSettings.textColor;
        for (int i = 0; i < rows; i++) {
            NearbyView.Row r = NearbyView.row(i);
            int ry = PADDING + i * rowHeight;
            int color = r.color;
            g.fill(PADDING, ry + 1, PADDING + SWATCH, ry + 1 + SWATCH, color);
            if (r.tracked) {
                g.renderOutline(PADDING - 1, ry, SWATCH + 2, SWATCH + 2, ClientSettings.trackedColor);
            }
            float rel = NavMath.relativeYaw(yaw, r.targetX() - px, r.targetZ() - pz);
            drawArrow(g, PADDING + SWATCH + 2 + ARROW_SIZE / 2.0f, ry + 3.5f, ARROW_SIZE, rel,
                    r.tracked ? ClientSettings.trackedColor : color);
            int nameColor = r.tracked ? ClientSettings.trackedColor : textColor;
            g.drawString(font, r.name, PADDING + textLeft, ry, nameColor, true);
            g.drawString(font, r.distanceText, width - PADDING - r.distanceWidth, ry, textColor, true);
            if (coords) {
                g.drawString(font, r.coordsText, PADDING + textLeft, ry + ROW_HEIGHT - 1, COORD_COLOR, true);
            }
        }
        g.pose().popMatrix();
    }

    /** Large arrow + name + distance of the tracked target, under the compass bar. */
    private static void renderTracked(GuiGraphics g, Font font, float yaw, double px, double pz, int top) {
        NearbyView.Row r = NearbyView.tracked();
        if (r == null) {
            return;
        }
        int cx = g.guiWidth() / 2;
        int y = top + 4;
        float rel = NavMath.relativeYaw(yaw, r.targetX() - px, r.targetZ() - pz);
        int color = ClientSettings.trackedColor;
        drawArrow(g, cx, y + 11, 22, rel, color);
        int textY = y + 25;
        int w = Math.max(r.nameWidth, r.distanceWidth);
        g.fill(cx - w / 2 - 3, textY - 2, cx + w / 2 + 3, textY + 19, Colors.withAlpha(0, 0x60));
        g.drawCenteredString(font, r.name, cx, textY, color);
        g.drawCenteredString(font, r.distanceText, cx, textY + 10, ClientSettings.textColor);
    }

    /** Draws the arrow texture centred at (cx, cy), pointing "up" rotated by {@code relDegrees} clockwise. */
    static void drawArrow(GuiGraphics g, float cx, float cy, int size, float relDegrees, int color) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(relDegrees * Mth.DEG_TO_RAD);
        int half = size / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, ARROW, -half, -half, 0.0f, 0.0f, size, size, 32, 32, 32, 32, color);
        g.pose().popMatrix();
    }
}
