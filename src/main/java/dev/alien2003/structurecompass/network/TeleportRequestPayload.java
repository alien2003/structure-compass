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

/**
 * Client to server: "teleport me to this structure". Only honoured for players with command permission level 2
 * (cheats). The server picks a safe Y at X/Z (surface, or an air gap under a ceiling) and runs vanilla {@code /tp}.
 */
public record TeleportRequestPayload(ResourceKey<Level> dimension, int x, int z) implements CustomPacketPayload {
    public static final Type<TeleportRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "teleport"));

    public static final StreamCodec<ByteBuf, TeleportRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), TeleportRequestPayload::dimension,
            ByteBufCodecs.VAR_INT, TeleportRequestPayload::x,
            ByteBufCodecs.VAR_INT, TeleportRequestPayload::z,
            TeleportRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
