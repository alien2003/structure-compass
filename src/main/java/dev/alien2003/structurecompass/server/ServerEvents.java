package dev.alien2003.structurecompass.server;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus listeners of the logical server. Registered from the common mod constructor. */
public final class ServerEvents {
    private ServerEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerStopping);
        // Also on "stopped": after a crash only ServerStoppedEvent fires, and the worker must not outlive the server.
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ScanService.stop());
        NeoForge.EVENT_BUS.addListener(ServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(ServerEvents::onTagsUpdated);
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        ScanService service = ScanService.get();
        if (service != null && event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            service.onDataReloaded();
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ScanService.start(event.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ScanService.stop();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ScanService service = ScanService.get();
        if (service != null) {
            service.tick();
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        ScanService service = ScanService.get();
        if (service != null && event.getEntity() instanceof ServerPlayer player) {
            service.onPlayerLoggedIn(player);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ScanService service = ScanService.get();
        if (service != null && event.getEntity() instanceof ServerPlayer player) {
            service.onPlayerLoggedOut(player);
        }
    }
}
