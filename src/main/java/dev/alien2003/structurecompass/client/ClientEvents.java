package dev.alien2003.structurecompass.client;

import dev.alien2003.structurecompass.client.data.ClientStructureData;
import dev.alien2003.structurecompass.client.data.NearbyView;
import dev.alien2003.structurecompass.client.data.TrackedTarget;
import dev.alien2003.structurecompass.client.hud.HudText;
import dev.alien2003.structurecompass.client.screen.StructureScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;

/** Game-bus client listeners: connection lifecycle, per-tick view update and hot keys. */
public final class ClientEvents {
    private static int sentRadius = -1;
    private static int sentPerType = -1;
    private static int sentRescan = -1;
    private static volatile boolean configDirty = true;

    private ClientEvents() {
    }

    static void markConfigDirty() {
        configDirty = true;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, ClientEvents::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, ClientEvents::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ClientEvents::onClientTick);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        resetSession();
        if (event.getPlayer().connection.hasChannel(dev.alien2003.structurecompass.network.ScanRequestPayload.TYPE)) {
            ClientSession.setState(ClientSession.State.WAITING); // the server hello follows right after login
        } else {
            ClientSession.setState(ClientSession.State.DISABLED);
            ClientNetwork.notifyDisabled("structurecompass.message.no_server_mod");
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetSession();
        ClientSession.setState(ClientSession.State.OFFLINE);
    }

    private static void resetSession() {
        ClientStructureData.clear();
        TrackedTarget.clear();
        NearbyView.reset();
        sentRadius = -1;
        sentPerType = -1;
        sentRescan = -1;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (configDirty) {
            configDirty = false;
            ClientConfig.TypeFilterCache.invalidate();
            StructureInfo.invalidate();
        }
        ClientSettings.refresh();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            consumeAll();
            return;
        }
        HudText.refresh();
        handleKeys(mc);
        if (!ClientSession.isActive()) {
            return;
        }
        resendIfSettingsChanged();
        NearbyView.update(mc.level.dimension(), player.getX(), player.getZ(),
                mc.getLanguageManager().getSelected(), mc.font);
    }

    private static void resendIfSettingsChanged() {
        int radius = ClientConfig.SEARCH_RADIUS.get();
        int perType = ClientConfig.RESULTS_PER_TYPE.get();
        int rescan = ClientConfig.RESCAN_DISTANCE.get();
        if (radius != sentRadius || perType != sentPerType || rescan != sentRescan) {
            boolean first = sentRadius < 0;
            sentRadius = radius;
            sentPerType = perType;
            sentRescan = rescan;
            if (!first) {
                ClientNetwork.sendSettings(Optional.empty(), true);
            }
        }
    }

    private static void consumeAll() {
        while (KeyBindings.OPEN_SCREEN.consumeClick()) {
            // drop
        }
        while (KeyBindings.TOGGLE_HUD.consumeClick()) {
            // drop
        }
        while (KeyBindings.TOGGLE_WORLD.consumeClick()) {
            // drop
        }
        while (KeyBindings.NEXT_TARGET.consumeClick()) {
            // drop
        }
    }

    private static void handleKeys(Minecraft mc) {
        while (KeyBindings.OPEN_SCREEN.consumeClick()) {
            if (ClientSession.isActive()) {
                mc.setScreen(new StructureScreen());
            } else {
                actionBar(Component.translatable("structurecompass.message.unavailable"));
            }
        }
        while (KeyBindings.TOGGLE_HUD.consumeClick()) {
            boolean value = !ClientConfig.HUD_ENABLED.get();
            ClientConfig.HUD_ENABLED.set(value);
            ClientConfig.HUD_ENABLED.save();
            actionBar(Component.translatable(value ? "structurecompass.message.hud_on" : "structurecompass.message.hud_off"));
        }
        while (KeyBindings.TOGGLE_WORLD.consumeClick()) {
            boolean value = !ClientConfig.WORLD_ENABLED.get();
            ClientConfig.WORLD_ENABLED.set(value);
            ClientConfig.WORLD_ENABLED.save();
            actionBar(Component.translatable(value ? "structurecompass.message.world_on" : "structurecompass.message.world_off"));
        }
        while (KeyBindings.NEXT_TARGET.consumeClick()) {
            cycleTarget(mc);
        }
    }

    /** Tracks the nearest structure, then the next one by distance, then stops tracking. */
    private static void cycleTarget(Minecraft mc) {
        if (!ClientSession.isActive() || mc.level == null) {
            return;
        }
        int count = Math.min(NearbyView.count(), Math.max(ClientSettings.hudEntries, ClientSettings.maxMarkers));
        if (count == 0) {
            actionBar(Component.translatable("structurecompass.message.no_targets"));
            return;
        }
        TrackedTarget current = TrackedTarget.get();
        int next = 0;
        if (current != null) {
            int index = -1;
            for (int i = 0; i < count; i++) {
                if (NearbyView.row(i).tracked) {
                    index = i;
                    break;
                }
            }
            next = index + 1;
        }
        if (next >= count) {
            TrackedTarget.clear();
            actionBar(Component.translatable("structurecompass.message.tracking_off"));
            return;
        }
        NearbyView.Row row = NearbyView.row(next);
        TrackedTarget.set(new TrackedTarget(mc.level.dimension(), row.type, row.entry));
        actionBar(Component.translatable("structurecompass.message.tracking", row.name, row.distanceText));
    }

    static void actionBar(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }
}
