package dev.alien2003.structurecompass.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alien2003.structurecompass.StructureCompass;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Hot keys (rebindable in Options - Controls - Key Binds, category "Structure Compass"). */
public final class KeyBindings {
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(StructureCompass.MOD_ID, "main"));

    public static final KeyMapping OPEN_SCREEN = new KeyMapping("key.structurecompass.open_screen",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.structurecompass.toggle_hud",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping TOGGLE_WORLD = new KeyMapping("key.structurecompass.toggle_world",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping NEXT_TARGET = new KeyMapping("key.structurecompass.next_target",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    private KeyBindings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_SCREEN);
        event.register(TOGGLE_HUD);
        event.register(TOGGLE_WORLD);
        event.register(NEXT_TARGET);
    }
}
