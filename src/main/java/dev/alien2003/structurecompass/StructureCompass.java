package dev.alien2003.structurecompass;

import com.mojang.logging.LogUtils;
import dev.alien2003.structurecompass.network.ModNetwork;
import dev.alien2003.structurecompass.server.ServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Common entry point (both physical sides). Registers networking and the logical-server structure search.
 * Everything client-only lives in {@link dev.alien2003.structurecompass.client} and is loaded from
 * {@link dev.alien2003.structurecompass.client.StructureCompassClient} (a {@code dist = CLIENT} mod class).
 */
@Mod(StructureCompass.MOD_ID)
public final class StructureCompass {
    public static final String MOD_ID = "structurecompass";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StructureCompass(IEventBus modBus, ModContainer container) {
        modBus.addListener(ModNetwork::register);
        ServerEvents.register();
    }
}
