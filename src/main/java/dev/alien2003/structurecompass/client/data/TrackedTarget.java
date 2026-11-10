package dev.alien2003.structurecompass.client.data;

import dev.alien2003.structurecompass.common.StructureEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The structure instance the player chose to track (big arrow on the HUD, highlighted beam). Identified by
 * dimension + type + start chunk, so it survives result refreshes; the latest entry data is picked up on updates.
 */
public record TrackedTarget(ResourceKey<Level> dimension, Identifier type, StructureEntry entry) {
    private static @Nullable TrackedTarget current;

    public static @Nullable TrackedTarget get() {
        TrackedTarget t = current;
        if (t == null) {
            return null;
        }
        // Refresh the entry with the newest data from the store (e.g. it got generated and now has boxes).
        ClientStructureData.DimensionData data = ClientStructureData.get(t.dimension);
        if (data != null) {
            List<StructureEntry> list = data.results.get(t.type);
            if (list != null) {
                for (StructureEntry e : list) {
                    if (e != t.entry && e.sameInstance(t.entry)) {
                        current = t = new TrackedTarget(t.dimension, t.type, e);
                        break;
                    }
                }
            }
        }
        return t;
    }

    public static void set(@Nullable TrackedTarget target) {
        current = target;
    }

    public static void clear() {
        current = null;
    }

    public boolean matches(ResourceKey<Level> dim, Identifier type, StructureEntry other) {
        return dimension.equals(dim) && this.type.equals(type) && entry.sameInstance(other);
    }
}
