package dev.alien2003.structurecompass.client;

import dev.alien2003.structurecompass.StructureCompass;
import dev.alien2003.structurecompass.client.devtest.VisualSmokeTest;
import dev.alien2003.structurecompass.client.hud.HudLayer;
import dev.alien2003.structurecompass.client.render.WorldHighlightRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry point. Only loaded on the physical client ({@code dist = CLIENT}), so it is the only place that wires
 * client-only classes (HUD, rendering, screens, key bindings, client config).
 */
@Mod(value = StructureCompass.MOD_ID, dist = Dist.CLIENT)
public final class StructureCompassClient {
    public StructureCompassClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(RegisterClientPayloadHandlersEvent.class, ClientNetwork::registerHandlers);
        modBus.addListener(RegisterKeyMappingsEvent.class, KeyBindings::register);
        modBus.addListener(RegisterRenderPipelinesEvent.class, WorldHighlightRenderer::registerPipelines);
        modBus.addListener(RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, HudLayer.ID, HudLayer::render));
        modBus.addListener(ModConfigEvent.Loading.class, event -> onConfigChanged(event.getConfig()));
        modBus.addListener(ModConfigEvent.Reloading.class, event -> onConfigChanged(event.getConfig()));

        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterLevel.class, WorldHighlightRenderer::onAfterLevel);
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Pre.class, HudLayer::onRenderGuiPre);
        NeoForge.EVENT_BUS.addListener(CustomizeGuiOverlayEvent.BossEventProgress.class, HudLayer::onBossBar);
        ClientEvents.register();
        VisualSmokeTest.registerIfEnabled();
    }

    /** May be called from the config file watcher thread: only flag, the client tick does the work. */
    private static void onConfigChanged(ModConfig config) {
        if (config.getSpec() == ClientConfig.SPEC) {
            ClientEvents.markConfigDirty();
        }
    }
}
