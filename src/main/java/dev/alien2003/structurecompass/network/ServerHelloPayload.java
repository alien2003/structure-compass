package dev.alien2003.structurecompass.network;

import dev.alien2003.structurecompass.StructureCompass;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client, sent right after login: tells the client whether structure search is available on this server.
 *
 * @param enabled   true on an integrated (single-player / LAN host) server
 * @param maxRadius largest search radius (blocks) the server accepts
 */
public record ServerHelloPayload(boolean enabled, int maxRadius) implements CustomPacketPayload {
    public static final Type<ServerHelloPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "hello"));

    public static final StreamCodec<ByteBuf, ServerHelloPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ServerHelloPayload::enabled,
            ByteBufCodecs.VAR_INT, ServerHelloPayload::maxRadius,
            ServerHelloPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
