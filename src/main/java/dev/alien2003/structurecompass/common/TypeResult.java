package dev.alien2003.structurecompass.common;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Nearest instances of one structure type (sorted by distance from the scan center, nearest first). */
public record TypeResult(Identifier type, List<StructureEntry> entries) {
    /** Upper bound for instances per type on the wire. */
    public static final int MAX_ENTRIES = 32;

    public static final StreamCodec<ByteBuf, TypeResult> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, TypeResult::type,
            StructureEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), TypeResult::entries,
            TypeResult::new);
}
