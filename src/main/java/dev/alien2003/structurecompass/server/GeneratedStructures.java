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

    /** Converts a valid start into a network entry with its overall and per-piece boxes. */
    public static StructureEntry toEntry(StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        BoxData bounds = BoxData.of(box);
        List<StructurePiece> pieces = start.getPieces();
        int n = Math.min(pieces.size(), StructureEntry.MAX_PIECES);
        List<BoxData> pieceBoxes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pieceBoxes.add(BoxData.of(pieces.get(i).getBoundingBox()));
        }
        return new StructureEntry(start.getChunkPos().x, start.getChunkPos().z,
                bounds.centerX(), bounds.centerY(), bounds.centerZ(), true, true,
                Optional.of(bounds), List.copyOf(pieceBoxes));
    }
}
