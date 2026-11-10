package dev.alien2003.structurecompass.server.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ground truth for the tests, built only from vanilla code.
 *
 * <p>{@link #generates} runs vanilla {@code ChunkGenerator#createStructures} - the exact world-generation step that
 * creates structure starts - on a detached {@link ProtoChunk}, so nothing is written into the world. (The game test
 * server runs with "generate structures" off, so real chunks never contain starts there.)
 *
 * <p>{@link #locate} is a line-by-line port of vanilla {@code ChunkGenerator#findNearestMapStructure} (what
 * {@code /locate structure} uses) for one structure, where the "load the chunk to STRUCTURE_STARTS and read its start"
 * step is replaced by {@link #generates}. It therefore returns what {@code /locate} would return in a world with the
 * same seed and generator but with structures enabled.
 */
final class VanillaStructureOracle {
    private final ServerLevel level;
    private final ChunkGeneratorStructureState state;
    private final Map<Long, Map<Structure, StructureStart>> cache = new HashMap<>();

    VanillaStructureOracle(ServerLevel level) {
        this.level = level;
        this.state = level.getChunkSource().getGeneratorState();
    }

    /** The real start of {@code structure} in {@code chunk} according to vanilla world generation, or null. */
    @Nullable StructureStart start(Holder<Structure> structure, ChunkPos chunk) {
        Map<Structure, StructureStart> starts = cache.computeIfAbsent(chunk.toLong(), key -> {
            ProtoChunk proto = new ProtoChunk(chunk, UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
            level.getChunkSource().getGenerator().createStructures(level.registryAccess(), state, level.structureManager(),
                    proto, level.getStructureManager(), level.dimension());
            return new HashMap<>(proto.getAllStarts());
        });
        StructureStart start = starts.get(structure.value());
        return start != null && start.isValid() ? start : null;
    }

    boolean generates(Holder<Structure> structure, ChunkPos chunk) {
        return start(structure, chunk) != null;
    }

    /** Vanilla's generation point (the stub position world generation computes) of the structure in that chunk. */
    @Nullable BlockPos generationPoint(Holder<Structure> structure, ChunkPos chunk) {
        Structure s = structure.value();
        var generator = level.getChunkSource().getGenerator();
        return s.findValidGenerationPoint(new Structure.GenerationContext(level.registryAccess(), generator,
                        generator.getBiomeSource(), state.randomState(), level.getStructureManager(), state.getLevelSeed(),
                        chunk, level, s.biomes()::contains))
                .map(Structure.GenerationStub::position)
                .orElse(null);
    }

    /** Port of {@code ChunkGenerator#findNearestMapStructure} for a single structure; returns the locate position. */
    @Nullable BlockPos locate(Holder<Structure> structure, BlockPos pos, int searchRadius) {
        List<StructurePlacement> placements = state.getPlacementsForStructure(structure);
        if (placements.isEmpty()) {
            return null;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        List<RandomSpreadStructurePlacement> spreads = new ArrayList<>();
        for (StructurePlacement placement : placements) {
            if (placement instanceof ConcentricRingsStructurePlacement rings) {
                BlockPos found = nearestOnRings(structure, pos, rings);
                if (found != null) {
                    double d = pos.distSqr(found);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = found;
                    }
                }
            } else if (placement instanceof RandomSpreadStructurePlacement spread) {
                spreads.add(spread);
            }
        }
        if (!spreads.isEmpty()) {
            int sectionX = SectionPos.blockToSectionCoord(pos.getX());
            int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());
            for (int k = 0; k <= searchRadius; k++) {
                boolean found = false;
                for (RandomSpreadStructurePlacement spread : spreads) {
                    BlockPos hit = firstOnRing(structure, sectionX, sectionZ, k, spread);
                    if (hit != null) {
                        found = true;
                        double d = pos.distSqr(hit);
                        if (d < bestDistance) {
                            bestDistance = d;
                            best = hit;
                        }
                    }
                }
                if (found) {
                    return best;
                }
            }
        }
        return best;
    }

    private @Nullable BlockPos nearestOnRings(Holder<Structure> structure, BlockPos pos, ConcentricRingsStructurePlacement placement) {
        List<ChunkPos> positions = state.getRingPositionsFor(placement);
        if (positions == null) {
            return null;
        }
        BlockPos result = null;
        double resultDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (ChunkPos chunk : positions) {
            mutable.set(SectionPos.sectionToBlockCoord(chunk.x, 8), 32, SectionPos.sectionToBlockCoord(chunk.z, 8));
            double d = mutable.distSqr(pos);
            if ((result == null || d < resultDistance) && generates(structure, chunk)) {
                result = placement.getLocatePos(chunk);
                resultDistance = d;
            }
        }
        return result;
    }

    private @Nullable BlockPos firstOnRing(Holder<Structure> structure, int x, int z, int ring,
                                           RandomSpreadStructurePlacement placement) {
        int spacing = placement.spacing();
        for (int j = -ring; j <= ring; j++) {
            boolean edgeJ = j == -ring || j == ring;
            for (int k = -ring; k <= ring; k++) {
                boolean edgeK = k == -ring || k == ring;
                if (edgeJ || edgeK) {
                    ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(), x + spacing * j, z + spacing * k);
                    if (generates(structure, chunk)) {
                        return placement.getLocatePos(chunk);
                    }
                }
            }
        }
        return null;
    }
}
