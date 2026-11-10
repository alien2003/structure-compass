package dev.alien2003.structurecompass.client.data;

import dev.alien2003.structurecompass.client.ClientConfig;
import dev.alien2003.structurecompass.client.StructureInfo;
import dev.alien2003.structurecompass.common.StructureEntry;
import dev.alien2003.structurecompass.common.math.NavMath;
import dev.alien2003.structurecompass.common.math.TextFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Pre-computed, distance-sorted list of the structures around the player in the current dimension.
 *
 * <p>Rebuilt on the client tick (20 Hz). Rows are pre-allocated and reused, and their strings are rebuilt only when
 * the rounded distance or the data changes, so HUD and world rendering read plain fields without allocating.
 */
public final class NearbyView {
    public static final int CAPACITY = 256;

    /** One displayed structure instance. Mutable and reused. */
    public static final class Row {
        public Identifier type;
        public StructureEntry entry;
        public double distance;
        public int color;
        public String name = "";
        public String distanceText = "";
        public String coordsText = "";
        public String labelText = "";
        public boolean tracked;
        boolean listed;
        public int nameWidth;
        public int distanceWidth;
        public int coordsWidth;
        public int labelWidth;
        int meters = -1;
        StructureEntry textFor;

        /** False for the tracked target when it is not part of the visible (top N) list. */
        public boolean isListed() {
            return listed;
        }

        public double targetX() {
            return entry.x() + 0.5;
        }

        public double targetZ() {
            return entry.z() + 0.5;
        }
    }

    private static final Row[] ROWS = new Row[CAPACITY];
    private static int count;
    private static @Nullable Row trackedRow;
    private static String unit = "m";
    private static String languageCode = "";

    static {
        for (int i = 0; i < CAPACITY; i++) {
            ROWS[i] = new Row();
        }
    }

    private NearbyView() {
    }

    public static int count() {
        return count;
    }

    public static Row row(int index) {
        return ROWS[index];
    }

    public static @Nullable Row tracked() {
        return trackedRow;
    }

    /** Recomputes the view for the player position. Call once per client tick. */
    public static void update(@Nullable ResourceKey<Level> dimension, double px, double pz, String language, Font font) {
        if (!language.equals(languageCode)) {
            languageCode = language;
            unit = I18n.get("structurecompass.unit.meters");
            for (Row row : ROWS) {
                row.meters = -1;
                row.textFor = null;
            }
        }
        count = 0;
        trackedRow = null;
        if (dimension == null) {
            return;
        }
        ClientStructureData.DimensionData data = ClientStructureData.get(dimension);
        if (data == null) {
            return;
        }
        TrackedTarget target = TrackedTarget.get();
        for (Map.Entry<Identifier, List<StructureEntry>> e : data.results.entrySet()) {
            Identifier type = e.getKey();
            boolean enabled = ClientConfig.isTypeEnabled(type);
            for (StructureEntry entry : e.getValue()) {
                boolean isTracked = target != null && target.matches(dimension, type, entry);
                if (!enabled && !isTracked) {
                    continue;
                }
                if (count >= CAPACITY) {
                    break;
                }
                Row row = ROWS[count++];
                boolean sameEntry = row.entry == entry && row.type == type;
                row.type = type;
                row.entry = entry;
                row.tracked = isTracked;
                row.listed = true;
                row.distance = NavMath.horizontalDistance(entry.x() + 0.5 - px, entry.z() + 0.5 - pz);
                if (!sameEntry || row.textFor != entry) {
                    row.color = StructureInfo.color(type);
                    row.name = StructureInfo.name(type);
                    row.coordsText = TextFormat.coordinates(entry.x(), entry.y(), entry.yKnown(), entry.z());
                    row.nameWidth = font.width(row.name);
                    row.coordsWidth = font.width(row.coordsText);
                    row.textFor = entry;
                    row.meters = -1;
                }
                int meters = NavMath.roundMeters(row.distance);
                if (meters != row.meters) {
                    row.meters = meters;
                    row.distanceText = TextFormat.meters(meters, unit);
                    row.labelText = row.name + " - " + row.distanceText;
                    row.distanceWidth = font.width(row.distanceText);
                    row.labelWidth = font.width(row.labelText);
                }
            }
        }
        // Insertion sort by distance: the list is nearly sorted from the previous tick.
        for (int i = 1; i < count; i++) {
            Row r = ROWS[i];
            int j = i - 1;
            while (j >= 0 && ROWS[j].distance > r.distance) {
                ROWS[j + 1] = ROWS[j];
                j--;
            }
            ROWS[j + 1] = r;
        }
        for (int i = 0; i < count; i++) {
            if (ROWS[i].tracked) {
                trackedRow = ROWS[i];
                break;
            }
        }
        if (trackedRow == null && target != null && target.dimension().equals(dimension)) {
            // The tracked instance is no longer in the results (e.g. it dropped out of the top N): keep showing it.
            if (count < CAPACITY) {
                Row row = ROWS[count];
                if (row.entry != target.entry() || row.type != target.type()) {
                    row.type = target.type();
                    row.entry = target.entry();
                    row.color = StructureInfo.color(target.type());
                    row.name = StructureInfo.name(target.type());
                    row.coordsText = TextFormat.coordinates(row.entry.x(), row.entry.y(), row.entry.yKnown(), row.entry.z());
                    row.nameWidth = font.width(row.name);
                    row.coordsWidth = font.width(row.coordsText);
                    row.textFor = row.entry;
                    row.meters = -1;
                }
                row.tracked = true;
                row.listed = false;
                row.distance = NavMath.horizontalDistance(row.entry.x() + 0.5 - px, row.entry.z() + 0.5 - pz);
                int meters = NavMath.roundMeters(row.distance);
                if (meters != row.meters) {
                    row.meters = meters;
                    row.distanceText = TextFormat.meters(meters, unit);
                    row.labelText = row.name + " - " + row.distanceText;
                    row.distanceWidth = font.width(row.distanceText);
                    row.labelWidth = font.width(row.labelText);
                }
                trackedRow = row; // not counted in the visible list
            }
        }
    }

    /** Clears everything (disconnect). */
    public static void reset() {
        count = 0;
        trackedRow = null;
        Arrays.stream(ROWS).forEach(r -> {
            r.entry = null;
            r.type = null;
            r.textFor = null;
            r.meters = -1;
        });
    }
}
