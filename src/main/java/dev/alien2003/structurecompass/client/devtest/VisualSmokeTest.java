package dev.alien2003.structurecompass.client.devtest;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.client.ClientSession;
import dev.alien2003.structurecompass.client.data.ClientStructureData;
import dev.alien2003.structurecompass.client.data.NearbyView;
import dev.alien2003.structurecompass.client.data.TrackedTarget;
import dev.alien2003.structurecompass.client.screen.StructureScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.FileUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Development-only visual smoke test ({@code ./dev runClientSmokeTest}): creates a fresh world with a fixed seed,
 * waits for the structure search, takes screenshots of the HUD, the world highlights around a generated structure,
 * the structure screen and the Nether, then quits. Screenshots go to {@code run/smoketest/screenshots}.
 *
 * <p>Inactive unless the JVM is started with {@code -Dstructurecompass.smoketest=true} in a development environment.
 */
public final class VisualSmokeTest {
    private static final long SEED = 20261109L;
    private static final int TIMEOUT_TICKS = 20 * 240;

    private enum Step { WAIT_TITLE, WAIT_WORLD, HUD_SHOT, GO_TO_STRUCTURE, WAIT_GENERATED, WORLD_SHOT, SCREEN, SCREEN_SHOT,
        NETHER, NETHER_SHOT, DONE }

    private static Step step = Step.WAIT_TITLE;
    private static int stepTicks;
    private static int totalTicks;
    private static NearbyView.@Nullable Row target;
    private static int targetX;
    private static int targetZ;

    private VisualSmokeTest() {
    }

    public static void registerIfEnabled() {
        if (!FMLEnvironment.isProduction() && Boolean.getBoolean("structurecompass.smoketest")) {
            StructureCompass.LOGGER.info("[smoketest] visual smoke test enabled");
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> tick());
        }
    }

    private static void next(Step s) {
        StructureCompass.LOGGER.info("[smoketest] step {} -> {} (tick {})", step, s, totalTicks);
        step = s;
        stepTicks = 0;
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1,
                message -> StructureCompass.LOGGER.info("[smoketest] {}", message.getString()));
    }

    private static void command(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (mc.player.mayFly() && !mc.player.getAbilities().flying) {
                mc.player.getAbilities().flying = true; // creative: hover where we teleport to
                mc.player.onUpdateAbilities();
            }
            StructureCompass.LOGGER.info("[smoketest] /{}", command);
            mc.player.connection.sendCommand(command);
        }
    }

    private static boolean scanDone(Minecraft mc) {
        if (mc.level == null) {
            return false;
        }
        ClientStructureData.DimensionData data = ClientStructureData.get(mc.level.dimension());
        return data != null && !data.scanning;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        totalTicks++;
        stepTicks++;
        if (step != Step.DONE && totalTicks > TIMEOUT_TICKS) {
            StructureCompass.LOGGER.error("[smoketest] FAILED: timeout in step {}", step);
            next(Step.DONE);
            mc.stop();
            return;
        }
        switch (step) {
            case WAIT_TITLE -> {
                if (mc.getOverlay() == null && stepTicks > 40 && (mc.screen instanceof TitleScreen || stepTicks > 200)) {
                    createWorld(mc);
                    next(Step.WAIT_WORLD);
                }
            }
            case WAIT_WORLD -> {
                if (mc.player != null && ClientSession.isActive() && scanDone(mc) && stepTicks > 20) {
                    command("time set noon");
                    command("weather clear");
                    command("tp @s ~ 120 ~ -45 25");
                    next(Step.HUD_SHOT);
                }
            }
            case HUD_SHOT -> {
                if (stepTicks == 80) {
                    shot("1_hud_overworld");
                    StructureCompass.LOGGER.info("[smoketest] HUD rows: {}", describeRows());
                    next(Step.GO_TO_STRUCTURE);
                }
            }
            case GO_TO_STRUCTURE -> {
                NearbyView.Row row = pickTarget();
                if (row == null) {
                    StructureCompass.LOGGER.error("[smoketest] FAILED: no structure found in the overworld");
                    next(Step.SCREEN);
                    return;
                }
                target = row;
                targetX = row.entry.x();
                targetZ = row.entry.z();
                if (mc.level != null) {
                    TrackedTarget.set(new TrackedTarget(mc.level.dimension(), row.type, row.entry));
                }
                StructureCompass.LOGGER.info("[smoketest] going to {} at {} {}", row.type, targetX, targetZ);
                command("tp @s " + (targetX - 40) + " 140 " + (targetZ - 40) + " facing " + targetX + " 64 " + targetZ);
                next(Step.WAIT_GENERATED);
            }
            case WAIT_GENERATED -> {
                NearbyView.Row tracked = NearbyView.tracked();
                boolean generated = tracked != null && tracked.entry.generated();
                if (generated && stepTicks > 60) {
                    int y = tracked.entry.y();
                    command("tp @s " + (targetX - 36) + " " + (y + 34) + " " + (targetZ - 36) + " facing " + targetX + " " + y + " " + targetZ);
                    next(Step.WORLD_SHOT);
                } else if (stepTicks > 20 * 60) {
                    StructureCompass.LOGGER.error("[smoketest] FAILED: tracked structure did not become generated");
                    next(Step.WORLD_SHOT);
                }
            }
            case WORLD_SHOT -> {
                if (stepTicks == 60) {
                    shot("2_world_highlights");
                    NearbyView.Row tracked = NearbyView.tracked();
                    StructureCompass.LOGGER.info("[smoketest] tracked: {} generated={} pieces={} y={} bounds={} listed={}",
                            tracked == null ? "none" : tracked.type, tracked != null && tracked.entry.generated(),
                            tracked == null ? 0 : tracked.entry.pieces().size(), tracked == null ? 0 : tracked.entry.y(),
                            tracked == null ? null : tracked.entry.bounds(), tracked != null && tracked.isListed());
                    StructureCompass.LOGGER.info("[smoketest] renderDistance={} chunks, depthFar={}, camera={}",
                            mc.options.getEffectiveRenderDistance(), mc.gameRenderer.getDepthFar(),
                            mc.gameRenderer.getMainCamera().position());
                } else if (stepTicks == 62) {
                    TrackedTarget.clear();
                } else if (stepTicks == 100) {
                    shot("2b_world_untracked");
                    next(Step.SCREEN);
                }
            }
            case SCREEN -> {
                mc.setScreen(new StructureScreen());
                next(Step.SCREEN_SHOT);
            }
            case SCREEN_SHOT -> {
                if (stepTicks == 30) {
                    shot("3_structure_screen");
                    mc.setScreen(null);
                    command("execute in minecraft:the_nether run tp @s 0 70 0 0 10");
                    next(Step.NETHER);
                }
            }
            case NETHER -> {
                if (mc.level != null && mc.level.dimension() == Level.NETHER && scanDone(mc) && stepTicks > 100) {
                    next(Step.NETHER_SHOT);
                }
            }
            case NETHER_SHOT -> {
                if (stepTicks == 40) {
                    shot("4_hud_nether");
                    StructureCompass.LOGGER.info("[smoketest] Nether HUD rows: {}", describeRows());
                    StructureCompass.LOGGER.info("[smoketest] OK: finished in {} ticks", totalTicks);
                    next(Step.DONE);
                    mc.stop();
                }
            }
            case DONE -> {
            }
        }
    }

    /** Nearest structure that generates quickly near the surface: prefer villages/outposts/portals, else nearest. */
    private static NearbyView.@Nullable Row pickTarget() {
        int count = NearbyView.count();
        for (int i = 0; i < count; i++) {
            NearbyView.Row r = NearbyView.row(i);
            String path = r.type.getPath();
            if (path.startsWith("village") || path.equals("pillager_outpost") || path.startsWith("ruined_portal")
                    || path.equals("desert_pyramid") || path.equals("jungle_pyramid") || path.equals("swamp_hut")) {
                return r;
            }
        }
        return count > 0 ? NearbyView.row(0) : null;
    }

    private static String describeRows() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(8, NearbyView.count()); i++) {
            NearbyView.Row r = NearbyView.row(i);
            sb.append("\n  ").append(r.name).append(" | ").append(r.distanceText).append(" | ").append(r.coordsText)
                    .append(r.entry.generated() ? " | generated" : " | predicted");
        }
        return sb.toString();
    }

    private static void createWorld(Minecraft mc) {
        try {
            LevelSettings settings = new LevelSettings("SC Smoke Test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
            String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), "SC Smoke Test", "");
            StructureCompass.LOGGER.info("[smoketest] creating world '{}' with seed {}", folder, SEED);
            mc.createWorldOpenFlows().createFreshLevel(folder, settings, new WorldOptions(SEED, true, false),
                    WorldPresets::createNormalWorldDimensions, new TitleScreen());
        } catch (Exception e) {
            StructureCompass.LOGGER.error("[smoketest] FAILED: cannot create the world", e);
            mc.stop();
        }
    }
}
