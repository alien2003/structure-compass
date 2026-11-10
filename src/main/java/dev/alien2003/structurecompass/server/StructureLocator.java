package dev.alien2003.structurecompass.server;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.common.math.PlacementMath;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Predicts where structures generate, using the same placement math and validity checks the game uses when it
 * generates structure starts ({@code ChunkGenerator#createStructures}), but without loading or generating chunks.
 *
 * <p>An instance is a snapshot of one dimension's generator. It is created on the server thread and is then safe to
 * use from the locator worker thread: everything it touches (placement math, noise/biome sampling through
 * {@link RandomState}, {@link StructureTemplateManager}, {@link Structure#findValidGenerationPoint}) is what vanilla
 * itself runs concurrently on world-generation worker threads. Results are cached per (structure set, chunk).
 */
public final class StructureLocator {
    /** Upper bound for the chunk-by-chunk search used for placement types this mod does not know. */
    static final int GENERIC_MAX_CHUNK_RADIUS = 96;
    /** Cache entries per dimension before the cache is flushed (keeps memory bounded on long sessions). */
    private static final int MAX_CACHE_ENTRIES = 200_000;

    private final ResourceKey<Level> dimension;
    private final RegistryAccess registryAccess;
    private final ChunkGenerator generator;
    private final BiomeSource biomeSource;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState state;
    private final StructureTemplateManager templates;
    private final LevelHeightAccessor heightAccessor;
    private final long seed;
    private final List<StructureSet> sets;
    private final List<Holder.Reference<Structure>> structures;
    private final Map<Structure, List<PlacementRef>> placements = new IdentityHashMap<>();
    private final ConcurrentHashMap<Long, Outcome> cache = new ConcurrentHashMap<>();
    private final Set<Structure> failedStructures = ConcurrentHashMap.newKeySet();

    /** A structure set the structure belongs to, with the set's placement. */
    record PlacementRef(int setIndex, StructurePlacement placement) {
    }

    /** What generates for a (set, chunk): the selected set entry index or -1, and the generation point. */
    record Outcome(int entryIndex, int x, int y, int z) {
        static final Outcome NONE = new Outcome(-1, 0, 0, 0);
    }

    /** A predicted structure start. */
    public record Located(Holder<Structure> structure, int setIndex, int chunkX, int chunkZ, int x, int y, int z,
                          double distanceSqr) {
    }

    private StructureLocator(ServerLevel level) {
        this.dimension = level.dimension();
        this.registryAccess = level.registryAccess();
        this.generator = level.getChunkSource().getGenerator();
        this.biomeSource = generator.getBiomeSource();
        this.state = level.getChunkSource().getGeneratorState();
        this.randomState = state.randomState();
        this.templates = level.getStructureManager();
        this.heightAccessor = LevelHeightAccessor.create(level.getMinY(), level.getHeight());
        this.seed = state.getLevelSeed();

        // Not thread-safe in vanilla: make sure the lazily computed placement tables exist before going async.
        state.ensureStructuresGenerated();

        List<Holder<StructureSet>> possible = state.possibleStructureSets();
        this.sets = new ArrayList<>(possible.size());
        for (Holder<StructureSet> holder : possible) {
            sets.add(holder.value());
        }

        List<Holder.Reference<Structure>> found = new ArrayList<>();
        level.registryAccess().lookupOrThrow(Registries.STRUCTURE).listElements().forEach(ref -> {
            List<StructurePlacement> structurePlacements = state.getPlacementsForStructure(ref);
            if (structurePlacements.isEmpty()) {
                return;
            }
            List<PlacementRef> refs = new ArrayList<>(structurePlacements.size());
            for (StructurePlacement placement : structurePlacements) {
                int setIndex = findSet(placement, ref.value());
                if (setIndex >= 0) {
                    refs.add(new PlacementRef(setIndex, placement));
                }
            }
            if (!refs.isEmpty()) {
                placements.put(ref.value(), List.copyOf(refs));
                found.add(ref);
            }
        });
        found.sort(Comparator.comparing(ref -> ref.key().identifier().toString()));
        this.structures = List.copyOf(found);
    }

    /** Must be called on the server thread. */
    public static StructureLocator create(ServerLevel level) {
        return new StructureLocator(level);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /** All structure types that can generate in this dimension, sorted by id. */
    public List<Holder.Reference<Structure>> structures() {
        return structures;
    }

    public List<Identifier> structureIds() {
        List<Identifier> ids = new ArrayList<>(structures.size());
        for (Holder.Reference<Structure> ref : structures) {
            ids.add(ref.key().identifier());
        }
        return ids;
    }

    public long seed() {
        return seed;
    }

    private int findSet(StructurePlacement placement, Structure structure) {
        for (int i = 0; i < sets.size(); i++) {
            StructureSet set = sets.get(i);
            if (set.placement() != placement) {
                continue;
            }
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                if (entry.structure().value() == structure) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Finds up to {@code count} nearest predicted starts of {@code structure} within {@code radius} blocks
     * (horizontal distance) of the given block position, nearest first.
     */
    public List<Located> locate(Holder<Structure> structure, int centerX, int centerZ, int radius, int count,
                                BooleanSupplier cancelled) {
        List<PlacementRef> refs = placements.get(structure.value());
        if (refs == null || count <= 0) {
            return List.of();
        }
        List<Located> results = new ArrayList<>();
        for (PlacementRef ref : refs) {
            if (cancelled.getAsBoolean()) {
                return List.of();
            }
            StructurePlacement placement = ref.placement();
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                locateRandomSpread(structure, ref.setIndex(), spread, centerX, centerZ, radius, count, cancelled, results);
            } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
                locateRings(structure, ref.setIndex(), rings, centerX, centerZ, radius, results, cancelled);
            } else {
                locateGeneric(structure, ref.setIndex(), placement, centerX, centerZ, radius, count, cancelled, results);
            }
        }
        results.sort(Comparator.comparingDouble(Located::distanceSqr));
        // Two placements of the same structure may predict the same chunk: keep the first.
        List<Located> unique = new ArrayList<>(Math.min(count, results.size()));
        for (Located located : results) {
            boolean duplicate = false;
            for (Located u : unique) {
                if (u.chunkX() == located.chunkX() && u.chunkZ() == located.chunkZ()) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                unique.add(located);
                if (unique.size() >= count) {
                    break;
                }
            }
        }
        return unique;
    }

    private void locateRandomSpread(Holder<Structure> structure, int setIndex, RandomSpreadStructurePlacement placement,
                                    int centerX, int centerZ, int radius, int count, BooleanSupplier cancelled,
                                    List<Located> out) {
        int spacing = Math.max(1, placement.spacing());
        int regionX0 = PlacementMath.regionOf(PlacementMath.chunkOf(centerX), spacing);
        int regionZ0 = PlacementMath.regionOf(PlacementMath.chunkOf(centerZ), spacing);
        int maxRing = PlacementMath.ringsForRadius(radius, spacing);
        double radiusSqr = (double) radius * radius;
        List<Located> local = new ArrayList<>();
        for (int k = 0; k <= maxRing; k++) {
            if (cancelled.getAsBoolean()) {
                return;
            }
            if (local.size() >= count) {
                local.sort(Comparator.comparingDouble(Located::distanceSqr));
                double kth = Math.sqrt(local.get(count - 1).distanceSqr());
                if (kth <= PlacementMath.ringMinDistanceBlocks(k, spacing)) {
                    break;
                }
            }
            int cells = PlacementMath.ringSize(k);
            for (int i = 0; i < cells; i++) {
                long cell = PlacementMath.ringCell(k, i);
                int regionX = regionX0 + PlacementMath.unpackX(cell);
                int regionZ = regionZ0 + PlacementMath.unpackZ(cell);
                ChunkPos chunk = placement.getPotentialStructureChunk(seed, regionX, regionZ);
                if (!withinRadius(chunk, centerX, centerZ, radiusSqr)) {
                    continue;
                }
                Located located = evaluateFor(structure, setIndex, chunk, centerX, centerZ);
                if (located != null && located.distanceSqr() <= radiusSqr) {
                    local.add(located);
                }
            }
        }
        out.addAll(local);
    }

    private void locateRings(Holder<Structure> structure, int setIndex, ConcentricRingsStructurePlacement placement,
                             int centerX, int centerZ, int radius, List<Located> out, BooleanSupplier cancelled) {
        List<ChunkPos> positions = state.getRingPositionsFor(placement);
        if (positions == null) {
            return;
        }
        double radiusSqr = (double) radius * radius;
        List<ChunkPos> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.comparingDouble(c -> chunkDistanceSqr(c, centerX, centerZ)));
        for (ChunkPos chunk : sorted) {
            if (cancelled.getAsBoolean()) {
                return;
            }
            if (!withinRadius(chunk, centerX, centerZ, radiusSqr)) {
                break; // sorted: everything after is further away
            }
            Located located = evaluateFor(structure, setIndex, chunk, centerX, centerZ);
            if (located != null && located.distanceSqr() <= radiusSqr) {
                out.add(located);
            }
        }
    }

    /** Fallback for modded placement types: test every chunk ring by ring with {@code isStructureChunk}. */
    private void locateGeneric(Holder<Structure> structure, int setIndex, StructurePlacement placement,
                               int centerX, int centerZ, int radius, int count, BooleanSupplier cancelled,
                               List<Located> out) {
        int chunkX0 = PlacementMath.chunkOf(centerX);
        int chunkZ0 = PlacementMath.chunkOf(centerZ);
        int maxRing = Math.min(GENERIC_MAX_CHUNK_RADIUS, (radius >> 4) + 1);
        double radiusSqr = (double) radius * radius;
        List<Located> local = new ArrayList<>();
        for (int k = 0; k <= maxRing; k++) {
            if (cancelled.getAsBoolean()) {
                return;
            }
            if (local.size() >= count) {
                local.sort(Comparator.comparingDouble(Located::distanceSqr));
                if (Math.sqrt(local.get(count - 1).distanceSqr()) <= PlacementMath.ringMinDistanceBlocks(k, 1)) {
                    break;
                }
            }
            int cells = PlacementMath.ringSize(k);
            for (int i = 0; i < cells; i++) {
                long cell = PlacementMath.ringCell(k, i);
                ChunkPos chunk = new ChunkPos(chunkX0 + PlacementMath.unpackX(cell), chunkZ0 + PlacementMath.unpackZ(cell));
                if (!withinRadius(chunk, centerX, centerZ, radiusSqr)) {
                    continue;
                }
                Located located = evaluateFor(structure, setIndex, chunk, centerX, centerZ);
                if (located != null && located.distanceSqr() <= radiusSqr) {
                    local.add(located);
                }
            }
        }
        out.addAll(local);
    }

    private static boolean withinRadius(ChunkPos chunk, int centerX, int centerZ, double radiusSqr) {
        // Generation points lie inside their start chunk: compare against the closest point of the chunk.
        double dx = Math.max(0, Math.max(chunk.getMinBlockX() - centerX, centerX - chunk.getMaxBlockX()));
        double dz = Math.max(0, Math.max(chunk.getMinBlockZ() - centerZ, centerZ - chunk.getMaxBlockZ()));
        return dx * dx + dz * dz <= radiusSqr;
    }

    private static double chunkDistanceSqr(ChunkPos chunk, int x, int z) {
        double dx = chunk.getMiddleBlockX() - x;
        double dz = chunk.getMiddleBlockZ() - z;
        return dx * dx + dz * dz;
    }

    private @Nullable Located evaluateFor(Holder<Structure> structure, int setIndex, ChunkPos chunk, int centerX, int centerZ) {
        Outcome outcome = outcome(setIndex, chunk);
        if (outcome.entryIndex() < 0) {
            return null;
        }
        StructureSet set = sets.get(setIndex);
        if (set.structures().get(outcome.entryIndex()).structure().value() != structure.value()) {
            return null;
        }
        double dx = outcome.x() - centerX;
        double dz = outcome.z() - centerZ;
        return new Located(structure, setIndex, chunk.x, chunk.z, outcome.x(), outcome.y(), outcome.z(), dx * dx + dz * dz);
    }

    /** Cached: which entry of the set (if any) generates its start in this chunk. */
    Outcome outcome(int setIndex, ChunkPos chunk) {
        long key = cacheKey(setIndex, chunk.x, chunk.z);
        Outcome cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Outcome computed = compute(sets.get(setIndex), chunk);
        if (cache.size() > MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, computed);
        return computed;
    }

    /** Marks a prediction as wrong (the start chunk is loaded and has no such structure). */
    void invalidate(int setIndex, int chunkX, int chunkZ) {
        cache.put(cacheKey(setIndex, chunkX, chunkZ), Outcome.NONE);
    }

    private static long cacheKey(int setIndex, int chunkX, int chunkZ) {
        // 22 bits per chunk coordinate is enough for the 30M block world border (1.875M chunks).
        return ((long) setIndex << 44) | ((long) (chunkX & 0x3FFFFF) << 22) | (chunkZ & 0x3FFFFF);
    }

    /** Mirrors {@code ChunkGenerator#createStructures} for one structure set and one chunk. */
    private Outcome compute(StructureSet set, ChunkPos chunk) {
        StructurePlacement placement = set.placement();
        if (!placement.isStructureChunk(state, chunk.x, chunk.z)) {
            return Outcome.NONE;
        }
        List<StructureSet.StructureSelectionEntry> entries = set.structures();
        if (entries.size() == 1) {
            return tryEntry(entries.get(0), 0, chunk);
        }
        // Weighted selection exactly as vanilla does it: same random, same order, same removal on failure.
        List<Integer> remaining = new ArrayList<>(entries.size());
        int totalWeight = 0;
        for (int i = 0; i < entries.size(); i++) {
            remaining.add(i);
            totalWeight += entries.get(i).weight();
        }
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunk.x, chunk.z);
        while (!remaining.isEmpty()) {
            int roll = random.nextInt(totalWeight);
            int pick = 0;
            for (int index : remaining) {
                roll -= entries.get(index).weight();
                if (roll < 0) {
                    break;
                }
                pick++;
            }
            int entryIndex = remaining.get(pick);
            StructureSet.StructureSelectionEntry entry = entries.get(entryIndex);
            Outcome outcome = tryEntry(entry, entryIndex, chunk);
            if (outcome.entryIndex() >= 0) {
                return outcome;
            }
            remaining.remove(pick);
            totalWeight -= entry.weight();
        }
        return Outcome.NONE;
    }

    private Outcome tryEntry(StructureSet.StructureSelectionEntry entry, int entryIndex, ChunkPos chunk) {
        Structure structure = entry.structure().value();
        if (failedStructures.contains(structure)) {
            return Outcome.NONE;
        }
        try {
            Structure.GenerationContext context = new Structure.GenerationContext(
                    registryAccess, generator, biomeSource, randomState, templates, seed, chunk, heightAccessor,
                    structure.biomes()::contains);
            Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(context);
            if (stub.isEmpty()) {
                return Outcome.NONE;
            }
            var pos = stub.get().position();
            return new Outcome(entryIndex, pos.getX(), pos.getY(), pos.getZ());
        } catch (RuntimeException | StackOverflowError e) {
            if (failedStructures.add(structure)) {
                StructureCompass.LOGGER.warn("Structure Compass: cannot predict {} in {} ({}); it will be skipped",
                        entry.structure().unwrapKey().map(k -> k.identifier().toString()).orElse("?"),
                        dimension.identifier(), e.toString());
            }
            return Outcome.NONE;
        }
    }

    /** Index of the set entry that holds the structure, used to verify generated chunks. */
    @Nullable StructureSet set(int setIndex) {
        return setIndex >= 0 && setIndex < sets.size() ? sets.get(setIndex) : null;
    }

    /** Placement of a given set (for {@code getLocatePos}). */
    StructurePlacement placement(int setIndex) {
        return sets.get(setIndex).placement();
    }

    /** Debug/test helper: number of cached (set, chunk) outcomes. */
    int cacheSize() {
        return cache.size();
    }

    /** Visible for tests: all placements known for a structure. */
    List<StructurePlacement> placementsOf(Structure structure) {
        List<PlacementRef> refs = placements.get(structure);
        if (refs == null) {
            return Collections.emptyList();
        }
        List<StructurePlacement> list = new ArrayList<>(refs.size());
        for (PlacementRef ref : refs) {
            list.add(ref.placement());
        }
        return list;
    }
}
