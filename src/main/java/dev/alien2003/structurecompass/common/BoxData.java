package dev.alien2003.structurecompass.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Inclusive block-space box (same semantics as vanilla {@link BoundingBox}), safe to share between threads. */
public record BoxData(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final StreamCodec<ByteBuf, BoxData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BoxData::minX,
            ByteBufCodecs.VAR_INT, BoxData::minY,
            ByteBufCodecs.VAR_INT, BoxData::minZ,
            ByteBufCodecs.VAR_INT, BoxData::maxX,
            ByteBufCodecs.VAR_INT, BoxData::maxY,
            ByteBufCodecs.VAR_INT, BoxData::maxZ,
            BoxData::new);

    public static BoxData of(BoundingBox box) {
        return new BoxData(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    public int centerX() {
        return (minX + maxX) >> 1;
    }

    public int centerY() {
        return (minY + maxY) >> 1;
    }

    public int centerZ() {
        return (minZ + maxZ) >> 1;
    }
}
