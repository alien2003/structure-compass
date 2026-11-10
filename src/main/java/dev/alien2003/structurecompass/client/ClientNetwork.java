package dev.alien2003.structurecompass.client;

import dev.alien2003.structurecompass.client.data.ClientStructureData;
import dev.alien2003.structurecompass.network.ScanRequestPayload;
import dev.alien2003.structurecompass.network.ScanResultsPayload;
import dev.alien2003.structurecompass.network.ScanStartPayload;
import dev.alien2003.structurecompass.network.ServerHelloPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Client side of the networking: payload handlers (main thread) and request sending. */
public final class ClientNetwork {
    private ClientNetwork() {
    }

    public static void registerHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ServerHelloPayload.TYPE, ClientNetwork::onHello);
        event.register(ScanStartPayload.TYPE, ClientNetwork::onScanStart);
        event.register(ScanResultsPayload.TYPE, ClientNetwork::onScanResults);
    }

    private static void onHello(ServerHelloPayload payload, IPayloadContext context) {
        ClientSession.setMaxRadius(payload.maxRadius());
        if (payload.enabled()) {
            ClientSession.setState(ClientSession.State.ACTIVE);
            sendSettings(Optional.empty(), true);
        } else {
            ClientSession.setState(ClientSession.State.DISABLED);
            notifyDisabled("structurecompass.message.dedicated_server");
        }
    }

    private static void onScanStart(ScanStartPayload payload, IPayloadContext context) {
        ClientStructureData.onScanStart(payload);
    }

    private static void onScanResults(ScanResultsPayload payload, IPayloadContext context) {
        ClientStructureData.onScanResults(payload);
    }

    /** True when the connected server knows our channel (it runs Structure Compass). */
    public static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(ScanRequestPayload.TYPE);
    }

    /**
     * Sends the current search settings. {@code dimension} empty = follow the player's dimension; present = one-off
     * scan of another dimension (structure screen).
     */
    public static void sendSettings(Optional<ResourceKey<Level>> dimension, boolean force) {
        if (!ClientSession.isActive() || !serverHasMod()) {
            return;
        }
        int radius = Math.min(ClientConfig.SEARCH_RADIUS.get(), ClientSession.maxRadius());
        ClientPacketDistributor.sendToServer(new ScanRequestPayload(dimension, radius,
                ClientConfig.RESULTS_PER_TYPE.get(), ClientConfig.RESCAN_DISTANCE.get(), force));
    }

    /** Shows the one-time "disabled" chat message. */
    static void notifyDisabled(String key) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), false);
        }
    }

    static @Nullable ResourceKey<Level> currentDimension() {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.dimension();
    }
}
