package dev.alien2003.structurecompass.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.client.ClientSession;
import dev.alien2003.structurecompass.client.ClientSettings;
import dev.alien2003.structurecompass.client.data.NearbyView;
import dev.alien2003.structurecompass.common.BoxData;
import dev.alien2003.structurecompass.common.StructureEntry;
import dev.alien2003.structurecompass.common.math.Colors;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.List;

/**
 * In-world highlights: a see-through beam and label at each structure (pulled inside the far plane when the structure
 * is farther away) and see-through bounding boxes of generated structures nearby.
 *
 * <p>Drawn in {@link RenderLevelStageEvent.AfterLevel} (after all world passes and compositing). Beams and box edges
 * are position-colour quads in a custom pipeline without depth test and without fog (box edges are thin quads of a
 * constant on-screen width, so they stay visible beyond the render distance); labels use vanilla's see-through text.
 * Vertices are written camera-relative without per-vertex matrix math, so the frame path does not allocate apart
 * from vanilla's font renderer.
 */
public final class WorldHighlightRenderer {
    private static final RenderPipeline QUADS_SEE_THROUGH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "pipeline/quads_see_through"))
            .withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
    private static final RenderType QUADS_SEE_THROUGH = RenderType.create(StructureCompass.MOD_ID + "_quads_see_through",
            RenderSetup.builder(QUADS_SEE_THROUGH_PIPELINE).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());

    private static final float BEAM_HALF_WIDTH = 0.3f;
    private static final int BEAM_ALPHA = 0x55;
    private static final int TRACKED_BEAM_ALPHA = 0x80;
    private static final int PIECE_ALPHA = 0x90;
    private static final int MAX_PIECES_PER_FRAME = 2048;
    /** Box edge width in screen pixels (overall box / pieces). */
    private static final float BOX_EDGE_PX = 3.0f;
    private static final float PIECE_EDGE_PX = 1.75f;

    private static final int MAX_LABELS = 64;
    private static final NearbyView.Row[] LABEL_ROWS = new NearbyView.Row[MAX_LABELS];

    /** World units of half an edge width per block of distance from the camera (set each frame). */
    private static float halfWidthPerBlock;
    /** Rows of the view rotation (camera-relative position -> view space), set each frame. */
    private static float viewXx;
    private static float viewXy;
    private static float viewXz;
    private static float viewYx;
    private static float viewYy;
    private static float viewYz;
    private static float viewZx;
    private static float viewZy;
    private static float viewZz;
    private static final float NEAR_Z = -0.1f;
    /** Accepted labels in view-tangent space: x, y, half width, half height. */
    private static final float[] LABEL_RECTS = new float[MAX_LABELS * 4];
    private static final float LABEL_SCALE = 0.0036f;

    private WorldHighlightRenderer() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(QUADS_SEE_THROUGH_PIPELINE);
    }

    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ClientSession.isActive() || !ClientSettings.worldEnabled
                || mc.options.hideGui) {
            return;
        }
        int count = NearbyView.count();
        NearbyView.Row tracked = NearbyView.tracked();
        if (count == 0 && tracked == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Vec3 cam = camera.position();
        double camX = cam.x;
        double camY = cam.y;
        double camZ = cam.z;
        float far = mc.gameRenderer.getDepthFar() * 0.9f;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        int minY = mc.level.getMinY();
        int maxY = mc.level.getMaxY() + 1;
        int markerLimit = ClientSettings.markerDistance <= 0 ? Integer.MAX_VALUE : ClientSettings.markerDistance;
        int maxMarkers = ClientSettings.maxMarkers;
        int screenHeight = Math.max(1, mc.getWindow().getHeight());
        halfWidthPerBlock = (float) (Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0) / screenHeight);
        Matrix4f view = event.getModelViewMatrix(); // read only
        viewXx = view.m00();
        viewXy = view.m10();
        viewXz = view.m20();
        viewYx = view.m01();
        viewYy = view.m11();
        viewYz = view.m21();
        viewZx = view.m02();
        viewZy = view.m12();
        viewZz = view.m22();

        // AfterLevel runs with an identity RenderSystem model-view: re-apply the camera rotation like LevelRenderer does.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        try {
            if (ClientSettings.worldBeams || ClientSettings.worldBoxes) {
                VertexConsumer quads = buffers.getBuffer(QUADS_SEE_THROUGH);
                if (ClientSettings.worldBoxes) {
                    // Boxes first: beams and labels are drawn over them.
                    float boxLimit = Math.min(ClientSettings.boxDistance, far * 0.9f);
                    int pieceBudget = MAX_PIECES_PER_FRAME;
                    for (int i = 0; i < count; i++) {
                        NearbyView.Row r = NearbyView.row(i);
                        if (r.distance > boxLimit) {
                            break; // sorted by distance
                        }
                        pieceBudget = boxes(quads, camX, camY, camZ, r, pieceBudget);
                    }
                    if (tracked != null && tracked.distance <= boxLimit && !tracked.isListed()) {
                        boxes(quads, camX, camY, camZ, tracked, pieceBudget);
                    }
                }
                if (ClientSettings.worldBeams) {
                    int drawn = 0;
                    for (int i = 0; i < count && drawn < maxMarkers; i++) {
                        NearbyView.Row r = NearbyView.row(i);
                        if (r.tracked || r.distance > markerLimit) {
                            continue;
                        }
                        beam(quads, camX, camY, camZ, r.targetX(), r.targetZ(), minY, maxY,
                                Colors.withAlpha(r.color, BEAM_ALPHA), far);
                        drawn++;
                    }
                    if (tracked != null) {
                        beam(quads, camX, camY, camZ, tracked.targetX(), tracked.targetZ(), minY, maxY,
                                Colors.withAlpha(ClientSettings.trackedColor, TRACKED_BEAM_ALPHA), far);
                    }
                }
                buffers.endBatch(QUADS_SEE_THROUGH);
            }

            if (ClientSettings.worldLabels) {
                drawLabels(mc, buffers, poseStack, camera, camX, camY, camZ, count, tracked, markerLimit, maxMarkers, far);
            }
        } finally {
            modelView.popMatrix();
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Beams

    /** Camera-facing vertical quad over the world height; pulled radially inside the far plane when farther away. */
    private static void beam(VertexConsumer vc, double camX, double camY, double camZ,
                             double x, double z, double y0, double y1, int argb, float far) {
        double dx = x - camX;
        double dz = z - camZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 2.0) {
            return; // standing in the beam: it would cover the whole screen
        }
        // Horizontal position at most 70% of the far plane, vertical extent limited so every corner stays inside it.
        double hMax = far * 0.7;
        double k = horizontal > hMax ? hMax / horizontal : 1.0;
        double h = horizontal * k;
        double yLimit = Math.sqrt(Math.max(0.0, far * far - h * h));
        float halfWidth = (float) (BEAM_HALF_WIDTH * Math.max(1.0, horizontal / 48.0) * k);
        float rx = (float) (dx * k);
        float rz = (float) (dz * k);
        float ry0 = (float) Math.max((y0 - camY) * k, -yLimit);
        float ry1 = (float) Math.min((y1 - camY) * k, yLimit);
        float px = (float) (-dz / horizontal) * halfWidth;
        float pz = (float) (dx / horizontal) * halfWidth;
        vc.addVertex(rx - px, ry0, rz - pz).setColor(argb);
        vc.addVertex(rx + px, ry0, rz + pz).setColor(argb);
        vc.addVertex(rx + px, ry1, rz + pz).setColor(argb);
        vc.addVertex(rx - px, ry1, rz - pz).setColor(argb);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Boxes

    /** Overall box (thicker) plus piece boxes of a generated structure. Returns the remaining piece budget. */
    private static int boxes(VertexConsumer vc, double camX, double camY, double camZ, NearbyView.Row r, int pieceBudget) {
        StructureEntry entry = r.entry;
        BoxData bounds = entry.boundsOrNull();
        if (bounds == null) {
            return pieceBudget;
        }
        int color = r.tracked ? ClientSettings.trackedColor : r.color;
        box(vc, camX, camY, camZ, bounds, color, BOX_EDGE_PX);
        if (ClientSettings.worldPieceBoxes) {
            int pieceColor = Colors.withAlpha(color, PIECE_ALPHA);
            List<BoxData> pieces = entry.pieces();
            for (int i = 0, n = pieces.size(); i < n && pieceBudget > 0; i++, pieceBudget--) {
                box(vc, camX, camY, camZ, pieces.get(i), pieceColor, PIECE_EDGE_PX);
            }
        }
        return pieceBudget;
    }

    private static void box(VertexConsumer vc, double camX, double camY, double camZ, BoxData b, int argb, float px) {
        float x0 = (float) (b.minX() - camX);
        float y0 = (float) (b.minY() - camY);
        float z0 = (float) (b.minZ() - camZ);
        float x1 = (float) (b.maxX() + 1 - camX);
        float y1 = (float) (b.maxY() + 1 - camY);
        float z1 = (float) (b.maxZ() + 1 - camZ);
        edge(vc, x0, y0, z0, x1, y0, z0, argb, px);
        edge(vc, x0, y0, z0, x0, y1, z0, argb, px);
        edge(vc, x0, y0, z0, x0, y0, z1, argb, px);
        edge(vc, x1, y0, z0, x1, y1, z0, argb, px);
        edge(vc, x1, y1, z0, x0, y1, z0, argb, px);
        edge(vc, x0, y1, z0, x0, y1, z1, argb, px);
        edge(vc, x0, y1, z1, x0, y0, z1, argb, px);
        edge(vc, x0, y0, z1, x1, y0, z1, argb, px);
        edge(vc, x1, y0, z1, x1, y0, z0, argb, px);
        edge(vc, x0, y1, z1, x1, y1, z1, argb, px);
        edge(vc, x1, y0, z1, x1, y1, z1, argb, px);
        edge(vc, x1, y1, z0, x1, y1, z1, argb, px);
    }

    /**
     * A box edge as a thin quad facing the camera, {@code px} screen pixels wide at both ends (the half width grows
     * linearly with the distance of each end). Camera-relative coordinates; the GPU clips what lies behind the camera.
     */
    private static void edge(VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, int argb, float px) {
        // Clip at the near plane first (view space looks down -Z): a quad whose width is derived from the distance of
        // an end point behind the camera would otherwise turn into a huge wedge.
        float za = viewZ(ax, ay, az);
        float zb = viewZ(bx, by, bz);
        boolean aBehind = za > NEAR_Z;
        boolean bBehind = zb > NEAR_Z;
        if (aBehind && bBehind) {
            return;
        }
        if (aBehind || bBehind) {
            float t = (NEAR_Z - za) / (zb - za);
            float cx = ax + (bx - ax) * t;
            float cy = ay + (by - ay) * t;
            float cz = az + (bz - az) * t;
            if (aBehind) {
                ax = cx;
                ay = cy;
                az = cz;
            } else {
                bx = cx;
                by = cy;
                bz = cz;
            }
        }
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        // side vector at A: perpendicular to the edge and to the view ray towards A
        float sax = dy * az - dz * ay;
        float say = dz * ax - dx * az;
        float saz = dx * ay - dy * ax;
        float sbx = dy * bz - dz * by;
        float sby = dz * bx - dx * bz;
        float sbz = dx * by - dy * bx;
        float la = (float) Math.sqrt(sax * sax + say * say + saz * saz);
        float lb = (float) Math.sqrt(sbx * sbx + sby * sby + sbz * sbz);
        if (la < 1.0e-5f || lb < 1.0e-5f) {
            return; // looking exactly along the edge: it is a point on screen
        }
        float wa = px * halfWidthPerBlock * (float) Math.sqrt(ax * ax + ay * ay + az * az) / la;
        float wb = px * halfWidthPerBlock * (float) Math.sqrt(bx * bx + by * by + bz * bz) / lb;
        sax *= wa;
        say *= wa;
        saz *= wa;
        sbx *= wb;
        sby *= wb;
        sbz *= wb;
        vc.addVertex(ax - sax, ay - say, az - saz).setColor(argb);
        vc.addVertex(ax + sax, ay + say, az + saz).setColor(argb);
        vc.addVertex(bx + sbx, by + sby, bz + sbz).setColor(argb);
        vc.addVertex(bx - sbx, by - sby, bz - sbz).setColor(argb);
    }

    private static float viewZ(float x, float y, float z) {
        return viewZx * x + viewZy * y + viewZz * z;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Labels

    /**
     * Picks labels nearest first (a farther label overlapping a nearer one on screen is skipped), then draws
     * them farthest first so nearer labels end up on top; the tracked label is drawn last.
     */
    private static void drawLabels(Minecraft mc, MultiBufferSource.BufferSource buffers, PoseStack poseStack, Camera camera,
                                   double camX, double camY, double camZ, int count, NearbyView.Row tracked,
                                   int markerLimit, int maxMarkers, float far) {
        int accepted = 0;
        if (tracked != null) {
            accept(tracked, camX, camY, camZ, 0);
        }
        int base = tracked != null ? 1 : 0;
        for (int i = 0; i < count && accepted < maxMarkers && base + accepted < MAX_LABELS; i++) {
            NearbyView.Row r = NearbyView.row(i);
            if (r.tracked || r.distance > markerLimit) {
                continue;
            }
            if (accept(r, camX, camY, camZ, base + accepted)) {
                accepted++;
            }
        }
        Font font = mc.font;
        int bg = mc.options.getBackgroundColor(0.35f);
        for (int i = base + accepted - 1; i >= base; i--) {
            label(font, buffers, poseStack, camera, camX, camY, camZ, LABEL_ROWS[i], ClientSettings.textColor, bg, far);
        }
        if (tracked != null) {
            label(font, buffers, poseStack, camera, camX, camY, camZ, tracked, ClientSettings.trackedColor, bg, far);
        }
        buffers.endBatch();
        for (int i = 0; i < base + accepted; i++) {
            LABEL_ROWS[i] = null;
        }
    }

    private static double labelY(NearbyView.Row r, double camY) {
        return r.entry.yKnown() ? r.entry.y() + 3.0 : camY + 6.0;
    }

    /**
     * Records the label's on-screen rectangle (in view-tangent units, so no projection matrix is needed) unless it
     * overlaps an already accepted label. Labels behind the camera are accepted without taking screen space.
     */
    private static boolean accept(NearbyView.Row r, double camX, double camY, double camZ, int slot) {
        float rx = (float) (r.targetX() - camX);
        float ry = (float) (labelY(r, camY) - camY);
        float rz = (float) (r.targetZ() - camZ);
        float dist = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (dist < 1.0e-3f) {
            return false;
        }
        int o = slot * 4;
        float depth = -viewZ(rx, ry, rz);
        if (depth < 0.5f) {
            LABEL_RECTS[o + 2] = -1.0f; // not on screen
            LABEL_ROWS[slot] = r;
            return true;
        }
        // The label is scaled with its distance, so its size in view-tangent units is constant.
        float k = Math.max(dist, 8.0f) / depth;
        float hw = Math.max(r.nameWidth, r.distanceWidth) * LABEL_SCALE * 0.5f * k;
        float hh = 10.0f * LABEL_SCALE * k;
        float sx = (viewXx * rx + viewXy * ry + viewXz * rz) / depth;
        float sy = (viewYx * rx + viewYy * ry + viewYz * rz) / depth - hh; // text hangs below its anchor
        for (int i = 0; i < slot; i++) {
            int p = i * 4;
            float ohw = LABEL_RECTS[p + 2];
            if (ohw >= 0.0f && Math.abs(sx - LABEL_RECTS[p]) < hw + ohw && Math.abs(sy - LABEL_RECTS[p + 1]) < hh + LABEL_RECTS[p + 3]) {
                return false;
            }
        }
        LABEL_RECTS[o] = sx;
        LABEL_RECTS[o + 1] = sy;
        LABEL_RECTS[o + 2] = hw;
        LABEL_RECTS[o + 3] = hh;
        LABEL_ROWS[slot] = r;
        return true;
    }

    /** Billboarded see-through label (name + distance), constant size on screen, pulled inside the far plane. */
    private static void label(Font font, MultiBufferSource buffers, PoseStack poseStack, Camera camera,
                              double camX, double camY, double camZ, NearbyView.Row r, int color, int background,
                              float far) {
        double rx = r.targetX() - camX;
        double ry = labelY(r, camY) - camY;
        double rz = r.targetZ() - camZ;
        double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (dist < 1.0e-3) {
            return;
        }
        double maxDist = far * 0.9;
        if (dist > maxDist) {
            double k = maxDist / dist;
            rx *= k;
            ry *= k;
            rz *= k;
            dist = maxDist;
        }
        float scale = (float) (LABEL_SCALE * Math.max(dist, 8.0));
        poseStack.pushPose();
        poseStack.translate(rx, ry, rz);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(scale, -scale, scale);
        Matrix4f m = poseStack.last().pose();
        font.drawInBatch(r.name, -r.nameWidth / 2.0f, 0.0f, color, false, m, buffers,
                Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
        font.drawInBatch(r.distanceText, -r.distanceWidth / 2.0f, font.lineHeight + 1, 0xFFFFFF55, false, m, buffers,
                Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
