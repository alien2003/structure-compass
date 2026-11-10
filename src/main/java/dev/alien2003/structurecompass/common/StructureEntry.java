package dev.alien2003.structurecompass.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * One located structure instance.
 *
 * @param startChunkX chunk X of the structure start (identity of the instance)
 * @param startChunkZ chunk Z of the structure start
 * @param x           block X of the navigation target
 * @param y           block Y of the navigation target (meaningful only when {@code yKnown})
 * @param z           block Z of the navigation target
 * @param yKnown      whether {@code y} is real (true once the structure is generated and its bounding box is known)
 * @param generated   whether the structure already exists in the world (its start chunk is loaded and has a start)
 * @param bounds      overall bounding box, only for generated structures
 * @param pieces      bounding boxes of individual pieces (possibly truncated), only for generated structures
 */
public record StructureEntry(int startChunkX, int startChunkZ, int x, int y, int z, boolean yKnown, boolean generated,
                             Optional<BoxData> bounds, List<BoxData> pieces) {
    /** Hard cap of piece boxes sent per structure (mineshafts can have hundreds of pieces). */
    public static final int MAX_PIECES = 256;

    public static final StreamCodec<ByteBuf, StructureEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StructureEntry::startChunkX,
            ByteBufCodecs.VAR_INT, StructureEntry::startChunkZ,
            ByteBufCodecs.VAR_INT, StructureEntry::x,
            ByteBufCodecs.VAR_INT, StructureEntry::y,
            ByteBufCodecs.VAR_INT, StructureEntry::z,
            ByteBufCodecs.BOOL, StructureEntry::yKnown,
            ByteBufCodecs.BOOL, StructureEntry::generated,
            ByteBufCodecs.optional(BoxData.STREAM_CODEC), StructureEntry::bounds,
            BoxData.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PIECES)), StructureEntry::pieces,
            StructureEntry::new);

    public static StructureEntry predicted(int chunkX, int chunkZ, int x, int z) {
        return new StructureEntry(chunkX, chunkZ, x, 0, z, false, false, Optional.empty(), List.of());
    }

    public @Nullable BoxData boundsOrNull() {
        return bounds.orElse(null);
    }

    public boolean sameInstance(StructureEntry other) {
        return startChunkX == other.startChunkX && startChunkZ == other.startChunkZ;
    }
}
