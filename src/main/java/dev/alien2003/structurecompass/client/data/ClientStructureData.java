package dev.alien2003.structurecompass.client.data;

import dev.alien2003.structurecompass.common.StructureEntry;
import dev.alien2003.structurecompass.common.TypeResult;
import dev.alien2003.structurecompass.network.ScanResultsPayload;
import dev.alien2003.structurecompass.network.ScanStartPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side store of scan results, per dimension. Only touched on the client main thread (payload handlers run
 * on the main thread), so no synchronisation is needed.
 */
public final class ClientStructureData {
    /** Results of one dimension. */
    public static final class DimensionData {
        public final ResourceKey<Level> dimension;
        public int scanId = -1;
        public int centerX;
        public int centerZ;
        public int radius;
        public boolean scanning;
        public List<Identifier> types = List.of();
        public final Map<Identifier, List<StructureEntry>> results = new LinkedHashMap<>();

        DimensionData(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        public List<StructureEntry> entries(Identifier type) {
            List<StructureEntry> list = results.get(type);
            return list == null ? Collections.emptyList() : list;
        }

        public int foundTypes() {
            int n = 0;
            for (List<StructureEntry> list : results.values()) {
                if (!list.isEmpty()) {
                    n++;
                }
            }
            return n;
        }
    }

    private static final Map<ResourceKey<Level>, DimensionData> DIMENSIONS = new HashMap<>();
    /** Bumped on every change, so views can cheaply detect when to rebuild. */
    private static int revision;

    private ClientStructureData() {
    }

    public static int revision() {
        return revision;
    }

    public static @Nullable DimensionData get(ResourceKey<Level> dimension) {
        return DIMENSIONS.get(dimension);
    }

    public static List<ResourceKey<Level>> knownDimensions() {
        return new ArrayList<>(DIMENSIONS.keySet());
    }

    public static void clear() {
        DIMENSIONS.clear();
        revision++;
    }

    public static void onScanStart(ScanStartPayload payload) {
        DimensionData data = DIMENSIONS.computeIfAbsent(payload.dimension(), DimensionData::new);
        data.scanId = payload.scanId();
        data.centerX = payload.centerX();
        data.centerZ = payload.centerZ();
        data.radius = payload.radius();
        data.scanning = true;
        data.types = List.copyOf(payload.types());
        // Keep the old lists until fresh ones arrive (no flicker while moving); drop types that no longer exist.
        data.results.keySet().retainAll(data.types);
        revision++;
    }

    public static void onScanResults(ScanResultsPayload payload) {
        DimensionData data = DIMENSIONS.get(payload.dimension());
        if (data == null || payload.scanId() != data.scanId) {
            return; // stale results of a superseded scan
        }
        for (TypeResult result : payload.results()) {
            data.results.put(result.type(), result.entries());
        }
        if (payload.done()) {
            data.scanning = false;
        }
        revision++;
    }
}
