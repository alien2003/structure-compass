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

import java.util.Optional;

/**
 * Client to server: search settings, and a request to (re)scan.
 *
 * @param dimension      empty = the dimension the player is in (and keep it updated automatically while moving);
 *                       present = one-off scan of another dimension around the equivalent position
 * @param radius         search radius in blocks
 * @param perType        how many nearest instances per structure type
 * @param rescanDistance re-scan after the player moved this many blocks from the last scan center
 * @param force          drop the previous results and scan again even if the player did not move
 */
public record ScanRequestPayload(Optional<ResourceKey<Level>> dimension, int radius, int perType, int rescanDistance,
                                 boolean force) implements CustomPacketPayload {
    public static final Type<ScanRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "scan_request"));

    public static final StreamCodec<ByteBuf, ScanRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceKey.streamCodec(Registries.DIMENSION)), ScanRequestPayload::dimension,
            ByteBufCodecs.VAR_INT, ScanRequestPayload::radius,
            ByteBufCodecs.VAR_INT, ScanRequestPayload::perType,
            ByteBufCodecs.VAR_INT, ScanRequestPayload::rescanDistance,
            ByteBufCodecs.BOOL, ScanRequestPayload::force,
            ScanRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
