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
import org.joml.Vector4f;

import java.util.List;

/**
 * In-world highlights: a see-through beam and label at each structure (clamped into the visible range when the
 * structure is farther away than the far plane) and see-through bounding boxes of generated structures nearby.
 *
 * <p>Drawn in {@link RenderLevelStageEvent.AfterLevel} (after all world passes and compositing) with custom render
 * pipelines that disable the depth test. All temporary vectors are reused; the only allocations happen inside
 * vanilla's font renderer.
 */
public final class WorldHighlightRenderer {
    private static final RenderPipeline LINES_SEE_THROUGH_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "pipeline/lines_see_through"))
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
    private static final RenderPipeline QUADS_SEE_THROUGH_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "pipeline/quads_see_through"))
            .withCull(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
    private static final RenderType LINES_SEE_THROUGH = RenderType.create(StructureCompass.MOD_ID + "_lines_see_through",
            RenderSetup.builder(LINES_SEE_THROUGH_PIPELINE).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());
    private static final RenderType QUADS_SEE_THROUGH = RenderType.create(StructureCompass.MOD_ID + "_quads_see_through",
            RenderSetup.builder(QUADS_SEE_THROUGH_PIPELINE).sortOnUpload().createRenderSetup());

    /** View-space near threshold used by vanilla gizmos to clip lines. */
    private static final float NEAR_Z = -0.05f;
    private static final float BEAM_HALF_WIDTH = 0.3f;
    private static final int BEAM_ALPHA = 0x55;
    private static final int TRACKED_BEAM_ALPHA = 0x80;
    private static final int PIECE_ALPHA = 0x90;
    private static final int MAX_PIECES_PER_FRAME = 4096;

    private static final Vector4f TMP_A = new Vector4f();
    private static final Vector4f TMP_B = new Vector4f();

    private WorldHighlightRenderer() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(LINES_SEE_THROUGH_PIPELINE);
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
        Matrix4f view = event.getModelViewMatrix(); // camera rotation only - never mutate
        float maxDist = mc.gameRenderer.getDepthFar() * 0.85f;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        int minY = mc.level.getMinY();
        int maxY = mc.level.getMaxY() + 1;
        int markerLimit = ClientSettings.markerDistance <= 0 ? Integer.MAX_VALUE : ClientSettings.markerDistance;
        int maxMarkers = ClientSettings.maxMarkers;

        // AfterLevel runs with an identity RenderSystem model-view: re-apply the camera rotation like LevelRenderer does.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(view);
        try {
            if (ClientSettings.worldBeams) {
                VertexConsumer quads = buffers.getBuffer(QUADS_SEE_THROUGH);
                int drawn = 0;
                for (int i = 0; i < count && drawn < maxMarkers; i++) {
                    NearbyView.Row r = NearbyView.row(i);
                    if (r.tracked || r.distance > markerLimit) {
                        continue;
                    }
                    beam(quads, poseStack.last(), camX, camY, camZ, r.targetX(), r.targetZ(), minY, maxY,
                            Colors.withAlpha(r.color, BEAM_ALPHA), maxDist);
                    drawn++;
                }
                if (tracked != null) {
                    beam(quads, poseStack.last(), camX, camY, camZ, tracked.targetX(), tracked.targetZ(), minY, maxY,
                            Colors.withAlpha(ClientSettings.trackedColor, TRACKED_BEAM_ALPHA), maxDist);
                }
                buffers.endBatch(QUADS_SEE_THROUGH);
            }

            if (ClientSettings.worldBoxes) {
                VertexConsumer lines = buffers.getBuffer(LINES_SEE_THROUGH);
                float width = mc.getWindow().getAppropriateLineWidth();
                int boxLimit = ClientSettings.boxDistance;
                int pieceBudget = MAX_PIECES_PER_FRAME;
                for (int i = 0; i < count; i++) {
                    NearbyView.Row r = NearbyView.row(i);
                    if (r.distance > boxLimit) {
                        break; // sorted by distance
                    }
                    pieceBudget = boxes(lines, poseStack.last(), view, camX, camY, camZ, r, width, pieceBudget);
                }
                if (tracked != null && tracked.distance <= boxLimit && !tracked.isListed()) {
                    boxes(lines, poseStack.last(), view, camX, camY, camZ, tracked, width, pieceBudget);
                }
                buffers.endBatch(LINES_SEE_THROUGH);
            }

            if (ClientSettings.worldLabels) {
                Font font = mc.font;
                int bg = mc.options.getBackgroundColor(0.35f);
                int drawn = 0;
                for (int i = 0; i < count && drawn < maxMarkers; i++) {
                    NearbyView.Row r = NearbyView.row(i);
                    if (r.tracked || r.distance > markerLimit) {
                        continue;
                    }
                    label(font, buffers, poseStack, camera, camX, camY, camZ, r, ClientSettings.textColor, bg, maxDist);
                    drawn++;
                }
                if (tracked != null) {
                    label(font, buffers, poseStack, camera, camX, camY, camZ, tracked, ClientSettings.trackedColor, bg, maxDist);
                }
                buffers.endBatch();
            }
        } finally {
            modelView.popMatrix();
        }
    }

    /** Camera-facing vertical quad over the whole world height; pulled towards the camera when beyond the far plane. */
    private static void beam(VertexConsumer vc, PoseStack.Pose pose, double camX, double camY, double camZ,
                             double x, double z, double y0, double y1, int argb, float maxDist) {
        double dx = x - camX;
        double dz = z - camZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double k = horizontal > maxDist ? maxDist / horizontal : 1.0;
        float halfWidth = (float) (BEAM_HALF_WIDTH * Math.max(1.0, horizontal / 48.0) * k);
        float rx = (float) (dx * k);
        float rz = (float) (dz * k);
        float ry0 = (float) Math.max((y0 - camY) * k, -maxDist);
        float ry1 = (float) Math.min((y1 - camY) * k, maxDist);
        float px;
        float pz;
        if (horizontal < 1.0e-4) {
            px = halfWidth;
            pz = 0.0f;
        } else {
            px = (float) (-dz / horizontal) * halfWidth;
            pz = (float) (dx / horizontal) * halfWidth;
        }
        vc.addVertex(pose, rx - px, ry0, rz - pz).setColor(argb);
        vc.addVertex(pose, rx + px, ry0, rz + pz).setColor(argb);
        vc.addVertex(pose, rx + px, ry1, rz + pz).setColor(argb);
        vc.addVertex(pose, rx - px, ry1, rz - pz).setColor(argb);
    }

    /** Overall box (thicker) plus piece boxes of a generated structure. Returns the remaining piece budget. */
    private static int boxes(VertexConsumer vc, PoseStack.Pose pose, Matrix4f view, double camX, double camY, double camZ,
                             NearbyView.Row r, float width, int pieceBudget) {
        StructureEntry entry = r.entry;
        BoxData bounds = entry.boundsOrNull();
        if (bounds == null) {
            return pieceBudget;
        }
        int color = r.tracked ? ClientSettings.trackedColor : r.color;
        box(vc, pose, view, camX, camY, camZ, bounds, color, width * 1.6f);
        if (ClientSettings.worldPieceBoxes) {
            int pieceColor = Colors.withAlpha(color, PIECE_ALPHA);
            List<BoxData> pieces = entry.pieces();
            for (int i = 0, n = pieces.size(); i < n && pieceBudget > 0; i++, pieceBudget--) {
                box(vc, pose, view, camX, camY, camZ, pieces.get(i), pieceColor, width);
            }
        }
        return pieceBudget;
    }

    private static void box(VertexConsumer vc, PoseStack.Pose pose, Matrix4f view, double camX, double camY, double camZ,
                            BoxData b, int argb, float width) {
        float x0 = (float) (b.minX() - camX);
        float y0 = (float) (b.minY() - camY);
        float z0 = (float) (b.minZ() - camZ);
        float x1 = (float) (b.maxX() + 1 - camX);
        float y1 = (float) (b.maxY() + 1 - camY);
        float z1 = (float) (b.maxZ() + 1 - camZ);
        line(vc, pose, view, x0, y0, z0, x1, y0, z0, argb, width);
        line(vc, pose, view, x0, y0, z0, x0, y1, z0, argb, width);
        line(vc, pose, view, x0, y0, z0, x0, y0, z1, argb, width);
        line(vc, pose, view, x1, y0, z0, x1, y1, z0, argb, width);
        line(vc, pose, view, x1, y1, z0, x0, y1, z0, argb, width);
        line(vc, pose, view, x0, y1, z0, x0, y1, z1, argb, width);
        line(vc, pose, view, x0, y1, z1, x0, y0, z1, argb, width);
        line(vc, pose, view, x0, y0, z1, x1, y0, z1, argb, width);
        line(vc, pose, view, x1, y0, z1, x1, y0, z0, argb, width);
        line(vc, pose, view, x0, y1, z1, x1, y1, z1, argb, width);
        line(vc, pose, view, x1, y0, z1, x1, y1, z1, argb, width);
        line(vc, pose, view, x1, y1, z0, x1, y1, z1, argb, width);
    }

    /** One line segment, clipped against the near plane (like vanilla gizmos) so boxes around the camera stay sane. */
    private static void line(VertexConsumer vc, PoseStack.Pose pose, Matrix4f view,
                             float ax, float ay, float az, float bx, float by, float bz, int argb, float width) {
        float za = TMP_A.set(ax, ay, az, 1.0f).mul(view).z;
        float zb = TMP_B.set(bx, by, bz, 1.0f).mul(view).z;
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
        float nx = bx - ax;
        float ny = by - ay;
        float nz = bz - az;
        vc.addVertex(pose, ax, ay, az).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
        vc.addVertex(pose, bx, by, bz).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    /** Billboarded see-through label (name + distance) above the structure, constant size on screen. */
    private static void label(Font font, MultiBufferSource buffers, PoseStack poseStack, Camera camera,
                              double camX, double camY, double camZ, NearbyView.Row r, int color, int background,
                              float maxDist) {
        double y = r.entry.yKnown() ? r.entry.y() + 3.0 : camY + 6.0;
        double rx = r.targetX() - camX;
        double ry = y - camY;
        double rz = r.targetZ() - camZ;
        double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (dist < 1.0e-3) {
            return;
        }
        if (dist > maxDist) {
            double k = maxDist / dist;
            rx *= k;
            ry *= k;
            rz *= k;
            dist = maxDist;
        }
        float scale = (float) (0.0036 * Math.max(dist, 8.0));
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
