package dev.alien2003.structurecompass.network;

import dev.alien2003.structurecompass.server.ScanService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration (common side). Client-bound handlers are attached separately on the client via
 * {@code RegisterClientPayloadHandlersEvent}, so no client class is referenced from here.
 *
 * <p>The channel is {@code optional()}: a client with this mod can join servers without it (and vice versa); the
 * client then disables itself.
 */
public final class ModNetwork {
    public static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToClient(ServerHelloPayload.TYPE, ServerHelloPayload.STREAM_CODEC);
        registrar.playToClient(ScanStartPayload.TYPE, ScanStartPayload.STREAM_CODEC);
        registrar.playToClient(ScanResultsPayload.TYPE, ScanResultsPayload.STREAM_CODEC);
        registrar.playToServer(ScanRequestPayload.TYPE, ScanRequestPayload.STREAM_CODEC, ModNetwork::onScanRequest);
    }

    private static void onScanRequest(ScanRequestPayload payload, IPayloadContext context) {
        // playToServer handlers run on the server thread by default (HandlerThread.MAIN).
        if (context.player() instanceof ServerPlayer player) {
            ScanService service = ScanService.get();
            if (service != null) {
                service.handleRequest(player, payload);
            }
        }
    }
}
