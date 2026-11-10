package dev.alien2003.structurecompass.network;

import dev.alien2003.structurecompass.StructureCompass;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Server to client: a scan of {@code dimension} started. Lists every structure type that can generate in that
 * dimension (from the world's structure registry, including datapacks and other mods).
 */
public record ScanStartPayload(ResourceKey<Level> dimension, int scanId, int centerX, int centerZ, int radius,
                               List<Identifier> types) implements CustomPacketPayload {
    public static final int MAX_TYPES = 4096;

    public static final Type<ScanStartPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "scan_start"));

    public static final StreamCodec<ByteBuf, ScanStartPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), ScanStartPayload::dimension,
            ByteBufCodecs.VAR_INT, ScanStartPayload::scanId,
            ByteBufCodecs.VAR_INT, ScanStartPayload::centerX,
            ByteBufCodecs.VAR_INT, ScanStartPayload::centerZ,
            ByteBufCodecs.VAR_INT, ScanStartPayload::radius,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TYPES)), ScanStartPayload::types,
            ScanStartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
