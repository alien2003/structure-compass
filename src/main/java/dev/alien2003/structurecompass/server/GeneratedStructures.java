package dev.alien2003.structurecompass.server;

import dev.alien2003.structurecompass.common.BoxData;
import dev.alien2003.structurecompass.common.StructureEntry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-thread helpers that read real structure starts from chunks that are already loaded. Never loads or
 * generates chunks ({@code getChunkNow} only returns chunks that are fully loaded).
 */
public final class GeneratedStructures {
    private GeneratedStructures() {
    }

    /** Result of checking a predicted start against the loaded world. */
    public enum Status { NOT_LOADED, PRESENT, ABSENT }

    public record Check(Status status, @Nullable StructureEntry entry) {
        static final Check NOT_LOADED = new Check(Status.NOT_LOADED, null);
        static final Check ABSENT = new Check(Status.ABSENT, null);
    }

    /** Looks up the real start of {@code structure} in the (loaded) start chunk. */
    public static Check check(ServerLevel level, Structure structure, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return Check.NOT_LOADED;
        }
        StructureStart start = chunk.getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            return Check.ABSENT;
        }
        return new Check(Status.PRESENT, toEntry(start));
    }

    /**
     * Converts a valid start into a network entry with its overall and per-piece boxes. The overall box is the union
     * of the piece boxes: {@link StructureStart#getBoundingBox()} is inflated by 12 blocks for structures with
     * terrain adaptation (villages, ancient cities...), which would look wrong when drawn.
     */
    public static StructureEntry toEntry(StructureStart start) {
        List<StructurePiece> pieces = start.getPieces();
        int n = Math.min(pieces.size(), StructureEntry.MAX_PIECES);
        List<BoxData> pieceBoxes = new ArrayList<>(n);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (StructurePiece piece : pieces) {
            BoundingBox b = piece.getBoundingBox();
            minX = Math.min(minX, b.minX());
            minY = Math.min(minY, b.minY());
            minZ = Math.min(minZ, b.minZ());
            maxX = Math.max(maxX, b.maxX());
            maxY = Math.max(maxY, b.maxY());
            maxZ = Math.max(maxZ, b.maxZ());
            if (pieceBoxes.size() < n) {
                pieceBoxes.add(BoxData.of(b));
            }
        }
        BoxData bounds = pieces.isEmpty()
                ? BoxData.of(start.getBoundingBox())
                : new BoxData(minX, minY, minZ, maxX, maxY, maxZ);
        return new StructureEntry(start.getChunkPos().x, start.getChunkPos().z,
                bounds.centerX(), bounds.centerY(), bounds.centerZ(), true, true,
                Optional.of(bounds), List.copyOf(pieceBoxes));
    }
}
