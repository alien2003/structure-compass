package dev.alien2003.structurecompass.network;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.common.TypeResult;
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
 * Server to client: results for some structure types of a scan. Each {@link TypeResult} replaces the client's list
 * for that type. {@code done} marks the last batch of the scan.
 */
public record ScanResultsPayload(ResourceKey<Level> dimension, int scanId, boolean done, List<TypeResult> results)
        implements CustomPacketPayload {
    public static final int MAX_RESULTS = 4096;

    public static final Type<ScanResultsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "scan_results"));

    public static final StreamCodec<ByteBuf, ScanResultsPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), ScanResultsPayload::dimension,
            ByteBufCodecs.VAR_INT, ScanResultsPayload::scanId,
            ByteBufCodecs.BOOL, ScanResultsPayload::done,
            TypeResult.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RESULTS)), ScanResultsPayload::results,
            ScanResultsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
