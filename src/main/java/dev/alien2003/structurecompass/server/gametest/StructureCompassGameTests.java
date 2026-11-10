package dev.alien2003.structurecompass.server.gametest;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.common.BoxData;
import dev.alien2003.structurecompass.common.StructureEntry;
import dev.alien2003.structurecompass.common.TypeResult;
import dev.alien2003.structurecompass.network.ScanRequestPayload;
import dev.alien2003.structurecompass.network.ScanResultsPayload;
import dev.alien2003.structurecompass.network.ScanStartPayload;
import dev.alien2003.structurecompass.server.GeneratedStructures;
import dev.alien2003.structurecompass.server.StructureLocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Game tests, run headless by {@code ./dev runGameTestServer} (and in CI). The game test server always uses seed 0, a
 * flat overworld (villages + strongholds only) and the normal Nether and End generators, so results are deterministic.
 *
 * <p>Registered only in development environments (never in a production game).
 */
public final class StructureCompassGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, StructureCompass.MOD_ID);

    private static final List<DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>> TESTS = new ArrayList<>();

    static {
        test("dimension_types", StructureCompassGameTests::dimensionTypes);
        test("matches_vanilla_locate_overworld", h -> matchesVanilla(h, Level.OVERWORLD,
                List.of(BuiltinStructures.VILLAGE_PLAINS, BuiltinStructures.STRONGHOLD),
                List.of(new BlockPos(0, 64, 0), new BlockPos(5000, 64, -3000))));
        test("matches_vanilla_locate_nether", h -> matchesVanilla(h, Level.NETHER,
                List.of(BuiltinStructures.FORTRESS, BuiltinStructures.BASTION_REMNANT, BuiltinStructures.NETHER_FOSSIL,
                        BuiltinStructures.RUINED_PORTAL_NETHER),
                List.of(new BlockPos(0, 64, 0), new BlockPos(-1500, 64, 2500))));
        test("matches_vanilla_locate_end", h -> matchesVanilla(h, Level.END,
                List.of(BuiltinStructures.END_CITY),
                List.of(new BlockPos(1500, 64, 1500), new BlockPos(-2500, 64, 800))));
        test("nearest_results_are_complete", StructureCompassGameTests::nearestAreComplete);
        test("loaded_start_boxes", StructureCompassGameTests::loadedStartBoxes);
        test("payload_codecs_roundtrip", StructureCompassGameTests::payloadRoundTrip);
        test("full_scan_runs_off_thread", StructureCompassGameTests::fullScanOffThread);
    }

    private StructureCompassGameTests() {
    }

    private static void test(String name, Consumer<GameTestHelper> body) {
        TESTS.add(FUNCTIONS.register(name, () -> body));
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(RegisterGameTestsEvent.class, StructureCompassGameTests::onRegisterGameTests);
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition> environment =
                event.registerEnvironment(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "default"));
        for (DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> holder : TESTS) {
            event.registerTest(holder.getId(), new FunctionGameTestInstance(holder.getKey(),
                    new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 400, 0, true)));
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    private static ServerLevel level(GameTestHelper helper, ResourceKey<Level> key) {
        ServerLevel level = helper.getLevel().getServer().getLevel(key);
        if (level == null) {
            throw new IllegalStateException("Missing dimension " + key.identifier());
        }
        return level;
    }

    private static Holder<Structure> structure(ServerLevel level, ResourceKey<Structure> key) {
        return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
    }

    private static double horizontalDistance(BlockPos from, int x, int z) {
        double dx = x - from.getX();
        double dz = z - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Structure lists per dimension come from the registry + the dimension's structure sets. */
    private static void dimensionTypes(GameTestHelper helper) {
        Set<Identifier> overworld = new HashSet<>(StructureLocator.createIgnoringWorldOptions(level(helper, Level.OVERWORLD)).structureIds());
        Set<Identifier> nether = new HashSet<>(StructureLocator.createIgnoringWorldOptions(level(helper, Level.NETHER)).structureIds());
        Set<Identifier> end = new HashSet<>(StructureLocator.createIgnoringWorldOptions(level(helper, Level.END)).structureIds());
        helper.assertTrue(overworld.contains(BuiltinStructures.VILLAGE_PLAINS.identifier()), "flat overworld must offer plains villages");
        helper.assertTrue(overworld.contains(BuiltinStructures.STRONGHOLD.identifier()), "flat overworld must offer strongholds");
        helper.assertFalse(overworld.contains(BuiltinStructures.FORTRESS.identifier()), "fortress must not be an overworld type");
        helper.assertTrue(nether.containsAll(List.of(BuiltinStructures.FORTRESS.identifier(), BuiltinStructures.BASTION_REMNANT.identifier(),
                BuiltinStructures.NETHER_FOSSIL.identifier(), BuiltinStructures.RUINED_PORTAL_NETHER.identifier())), "nether types: " + nether);
        helper.assertFalse(nether.contains(BuiltinStructures.VILLAGE_PLAINS.identifier()), "no villages in the nether");
        helper.assertTrue(end.contains(BuiltinStructures.END_CITY.identifier()), "end cities in the end: " + end);
        helper.assertFalse(end.contains(BuiltinStructures.FORTRESS.identifier()), "no fortresses in the end");
        // The "generate structures: off" world option must be respected by the production factory.
        helper.assertTrue(StructureLocator.create(level(helper, Level.NETHER)).structures().isEmpty(),
                "the game test world has structures disabled, so the normal locator must report none");
        helper.succeed();
    }

    /**
     * For every structure and search position: vanilla /locate's answer (see {@link VanillaStructureOracle}) is
     * predicted by our locator too, is among our nearest results, and our nearest result is never farther away; all our
     * results really generate according to vanilla world generation.
     */
    private static void matchesVanilla(GameTestHelper helper, ResourceKey<Level> dimension,
                                       List<ResourceKey<Structure>> keys, List<BlockPos> positions) {
        ServerLevel level = level(helper, dimension);
        StructureLocator locator = StructureLocator.createIgnoringWorldOptions(level);
        VanillaStructureOracle oracle = new VanillaStructureOracle(level);
        for (ResourceKey<Structure> key : keys) {
            Holder<Structure> holder = structure(level, key);
            for (BlockPos pos : positions) {
                String what = key.identifier() + " near " + pos.toShortString() + " in " + dimension.identifier();
                BlockPos vanilla = oracle.locate(holder, pos, 100);
                helper.assertTrue(vanilla != null, "vanilla locate found no " + what);
                ChunkPos vanillaChunk = new ChunkPos(vanilla);
                double vanillaDistance = horizontalDistance(pos, vanilla.getX(), vanilla.getZ());

                List<StructureLocator.Located> ours = locator.locate(holder, pos.getX(), pos.getZ(),
                        (int) Math.ceil(vanillaDistance) + 2048, 10, () -> false);
                helper.assertFalse(ours.isEmpty(), "our locator found no " + what);
                helper.assertTrue(locator.predicts(holder, vanillaChunk),
                        "our locator does not predict vanilla's " + what + " at chunk " + vanillaChunk);
                boolean contained = false;
                for (StructureLocator.Located l : ours) {
                    if (l.chunkX() == vanillaChunk.x && l.chunkZ() == vanillaChunk.z) {
                        contained = true;
                        break;
                    }
                }
                helper.assertTrue(contained, "vanilla's " + what + " at " + vanillaChunk + " is not among our 10 nearest " + chunks(ours));
                StructureLocator.Located nearest = ours.getFirst();
                double ourDistance = Math.sqrt(nearest.distanceSqr());
                // Vanilla measures to the chunk corner, we measure to the generation point inside the chunk.
                helper.assertTrue(ourDistance <= vanillaDistance + 24.0,
                        "our nearest " + what + " (" + Math.round(ourDistance) + " m) is farther than vanilla's (" + Math.round(vanillaDistance) + " m)");
                for (int i = 0; i < Math.min(3, ours.size()); i++) {
                    StructureLocator.Located l = ours.get(i);
                    helper.assertTrue(oracle.generates(holder, new ChunkPos(l.chunkX(), l.chunkZ())),
                            "predicted " + what + " #" + (i + 1) + " at chunk " + l.chunkX() + "," + l.chunkZ() + " does not generate");
                }
                StructureCompass.LOGGER.info("[gametest] {}: vanilla {} ({} m), ours {} ({} m)", what, vanilla.toShortString(),
                        Math.round(vanillaDistance), nearest.chunkX() + "," + nearest.chunkZ(), Math.round(ourDistance));
            }
        }
        helper.succeed();
    }

    /**
     * Brute force over every candidate chunk of the placement within the radius of our 3rd result, using vanilla world
     * generation as ground truth: nothing real may be closer than our 3rd result and missing from our list.
     */
    private static void nearestAreComplete(GameTestHelper helper) {
        record Case(ResourceKey<Level> dimension, ResourceKey<Structure> structure, BlockPos pos) {
        }
        List<Case> cases = List.of(
                new Case(Level.OVERWORLD, BuiltinStructures.VILLAGE_PLAINS, new BlockPos(-700, 64, 1300)),
                new Case(Level.NETHER, BuiltinStructures.FORTRESS, new BlockPos(300, 64, -900)),
                new Case(Level.NETHER, BuiltinStructures.BASTION_REMNANT, new BlockPos(300, 64, -900)),
                new Case(Level.NETHER, BuiltinStructures.RUINED_PORTAL_NETHER, new BlockPos(-200, 64, 200)),
                new Case(Level.END, BuiltinStructures.END_CITY, new BlockPos(2000, 64, -1000)));
        for (Case c : cases) {
            ServerLevel level = level(helper, c.dimension());
            StructureLocator locator = StructureLocator.createIgnoringWorldOptions(level);
            VanillaStructureOracle oracle = new VanillaStructureOracle(level);
            Holder<Structure> holder = structure(level, c.structure());
            String what = c.structure().identifier() + " near " + c.pos().toShortString();
            List<StructureLocator.Located> ours = locator.locate(holder, c.pos().getX(), c.pos().getZ(), 6000, 3, () -> false);
            helper.assertTrue(ours.size() == 3, "expected 3 results for " + what + ", got " + ours.size());
            double limit = Math.sqrt(ours.get(2).distanceSqr());
            Set<Long> ourChunks = new HashSet<>();
            for (StructureLocator.Located l : ours) {
                ourChunks.add(ChunkPos.asLong(l.chunkX(), l.chunkZ()));
            }
            for (StructurePlacement placement : level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder)) {
                if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
                    continue;
                }
                int spacing = spread.spacing();
                int rx0 = Math.floorDiv(c.pos().getX() >> 4, spacing);
                int rz0 = Math.floorDiv(c.pos().getZ() >> 4, spacing);
                int rings = (int) Math.ceil(limit / (spacing * 16.0)) + 1;
                for (int rx = rx0 - rings; rx <= rx0 + rings; rx++) {
                    for (int rz = rz0 - rings; rz <= rz0 + rings; rz++) {
                        ChunkPos chunk = spread.getPotentialStructureChunk(level.getSeed(), rx * spacing, rz * spacing);
                        StructureStart start = oracle.start(holder, chunk);
                        if (start == null) {
                            continue;
                        }
                        // distance to the chunk's closest point is a lower bound of the generation point distance
                        double dx = Math.max(0, Math.max(chunk.getMinBlockX() - c.pos().getX(), c.pos().getX() - chunk.getMaxBlockX()));
                        double dz = Math.max(0, Math.max(chunk.getMinBlockZ() - c.pos().getZ(), c.pos().getZ() - chunk.getMaxBlockZ()));
                        double lower = Math.sqrt(dx * dx + dz * dz);
                        if (lower + 1.0 < limit) {
                            helper.assertTrue(ourChunks.contains(chunk.toLong()) || lowerBoundTie(chunk, c.pos(), limit),
                                    "missed real " + what + " at chunk " + chunk + " (" + Math.round(lower) + " m < " + Math.round(limit) + " m)");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /** A chunk whose farthest point is beyond the limit may legitimately rank after our 3rd result. */
    private static boolean lowerBoundTie(ChunkPos chunk, BlockPos pos, double limit) {
        double dx = Math.max(Math.abs(chunk.getMinBlockX() - pos.getX()), Math.abs(chunk.getMaxBlockX() - pos.getX()));
        double dz = Math.max(Math.abs(chunk.getMinBlockZ() - pos.getZ()), Math.abs(chunk.getMaxBlockZ() - pos.getZ()));
        return Math.sqrt(dx * dx + dz * dz) >= limit;
    }

    /** Real starts are converted into tight boxes (union of the pieces) with a centre inside the overall box. */
    private static void loadedStartBoxes(GameTestHelper helper) {
        ServerLevel nether = level(helper, Level.NETHER);
        StructureLocator locator = StructureLocator.createIgnoringWorldOptions(nether);
        VanillaStructureOracle oracle = new VanillaStructureOracle(nether);
        Holder<Structure> fortress = structure(nether, BuiltinStructures.FORTRESS);
        List<StructureLocator.Located> found = locator.locate(fortress, 0, 0, 4000, 1, () -> false);
        helper.assertFalse(found.isEmpty(), "no fortress near spawn");
        StructureLocator.Located l = found.getFirst();
        StructureStart start = oracle.start(fortress, new ChunkPos(l.chunkX(), l.chunkZ()));
        helper.assertTrue(start != null, "fortress start missing");
        StructureEntry entry = GeneratedStructures.toEntry(start);
        BoxData bounds = entry.bounds().orElseThrow();
        helper.assertTrue(entry.generated() && entry.yKnown(), "generated entries know their Y");
        helper.assertTrue(entry.pieces().size() == Math.min(start.getPieces().size(), StructureEntry.MAX_PIECES), "piece count");
        helper.assertTrue(bounds.minX() <= entry.x() && entry.x() <= bounds.maxX() && bounds.minY() <= entry.y()
                && entry.y() <= bounds.maxY() && bounds.minZ() <= entry.z() && entry.z() <= bounds.maxZ(), "centre inside the box");
        for (BoxData piece : entry.pieces()) {
            helper.assertTrue(piece.minX() >= bounds.minX() && piece.maxX() <= bounds.maxX() && piece.minY() >= bounds.minY()
                    && piece.maxY() <= bounds.maxY() && piece.minZ() >= bounds.minZ() && piece.maxZ() <= bounds.maxZ(), "piece inside bounds");
        }
        helper.succeed();
    }

    private static void payloadRoundTrip(GameTestHelper helper) {
        StructureEntry predicted = StructureEntry.predicted(-3, 7, -40, 120);
        StructureEntry generated = new StructureEntry(10, -20, 168, -27, -312, true, true,
                Optional.of(new BoxData(100, -51, -400, 236, -5, -224)),
                List.of(new BoxData(100, -51, -400, 120, -40, -380), new BoxData(-30000000, -64, 29999999, -29999990, 320, 30000000)));
        ScanResultsPayload results = new ScanResultsPayload(Level.NETHER, 42, true, List.of(
                new TypeResult(BuiltinStructures.FORTRESS.identifier(), List.of(predicted, generated)),
                new TypeResult(Identifier.fromNamespaceAndPath("othermod", "deep/tower"), List.of())));
        helper.assertValueEqual(results, roundTrip(ScanResultsPayload.STREAM_CODEC, results), "ScanResultsPayload");
        ScanStartPayload start = new ScanStartPayload(Level.END, 7, -1000, 2000, 4000,
                List.of(BuiltinStructures.END_CITY.identifier()));
        helper.assertValueEqual(start, roundTrip(ScanStartPayload.STREAM_CODEC, start), "ScanStartPayload");
        ScanRequestPayload request = new ScanRequestPayload(Optional.of(Level.OVERWORLD), 4000, 3, 128, true);
        helper.assertValueEqual(request, roundTrip(ScanRequestPayload.STREAM_CODEC, request), "ScanRequestPayload");
        ScanRequestPayload follow = new ScanRequestPayload(Optional.empty(), 256, 1, 16, false);
        helper.assertValueEqual(follow, roundTrip(ScanRequestPayload.STREAM_CODEC, follow), "ScanRequestPayload (no dimension)");
        helper.succeed();
    }

    private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        try {
            codec.encode(buf, value);
            T decoded = codec.decode(buf);
            if (buf.isReadable()) {
                throw new IllegalStateException(buf.readableBytes() + " bytes left after decoding");
            }
            return decoded;
        } finally {
            buf.release();
        }
    }

    /**
     * A full scan of every structure type of the Nether (the same work a player triggers) runs on another thread with
     * no help from the server thread, and finds the key structures.
     */
    private static void fullScanOffThread(GameTestHelper helper) {
        ServerLevel nether = level(helper, Level.NETHER);
        StructureLocator locator = StructureLocator.createIgnoringWorldOptions(nether);
        AtomicReference<List<Identifier>> found = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                List<Identifier> ids = new ArrayList<>();
                for (Holder.Reference<Structure> ref : locator.structures()) {
                    if (!locator.locate(ref, 123, -456, 4000, 3, () -> false).isEmpty()) {
                        ids.add(ref.key().identifier());
                    }
                }
                found.set(ids);
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "StructureCompass-GameTest-Scan");
        long start = System.nanoTime();
        worker.start();
        try {
            worker.join(120_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        helper.assertTrue(failure.get() == null, "scan failed: " + failure.get());
        helper.assertTrue(found.get() != null, "full scan did not finish within 120 s");
        helper.assertTrue(found.get().containsAll(List.of(BuiltinStructures.FORTRESS.identifier(), BuiltinStructures.BASTION_REMNANT.identifier())),
                "full scan results: " + found.get());
        StructureCompass.LOGGER.info("[gametest] full nether scan (radius 4000, 3 per type) took {} ms off-thread: {}", millis, found.get());
        helper.succeed();
    }

    private static String chunks(List<StructureLocator.Located> list) {
        StringBuilder sb = new StringBuilder("[");
        for (StructureLocator.Located l : list) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(l.chunkX()).append(',').append(l.chunkZ()).append(" (").append(Math.round(Math.sqrt(l.distanceSqr()))).append(" m)");
        }
        return sb.append(']').toString();
    }

}
