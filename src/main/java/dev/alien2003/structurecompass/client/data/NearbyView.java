package dev.alien2003.structurecompass.client.data;

import dev.alien2003.structurecompass.client.ClientConfig;
import dev.alien2003.structurecompass.client.StructureInfo;
import dev.alien2003.structurecompass.common.StructureEntry;
import dev.alien2003.structurecompass.common.math.NavMath;
import dev.alien2003.structurecompass.common.math.TextFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
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
 * <p>Rebuilt on the client tick (20 Hz). Row objects keep a stable slot for the same data (slots follow the
 * iteration order of the results, which only changes when new results arrive); sorting permutes an index array, not
 * the rows, so each row's strings are rebuilt only when its data or rounded distance changes. HUD and world rendering
 * read plain fields without allocating.
 */
public final class NearbyView {
    /** One displayed structure instance. Mutable and reused. */
    public static final class Row {
        public Identifier type;
        public StructureEntry entry;
        public double distance;
        public int color;
        public String name = "";
        public String distanceText = "";
        public String coordsText = "";
        public boolean tracked;
        boolean listed;
        public int nameWidth;
        public int distanceWidth;
        public int coordsWidth;
        int meters = -1;
        @Nullable StructureEntry textFor;
        @Nullable Identifier textType;

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

    private static Row[] rows = new Row[0];
    private static int[] order = new int[0];
    private static int[] scratch = new int[0];
    private static boolean[] seen = new boolean[0];
    private static int count;
    private static final Row FALLBACK = new Row();
    private static @Nullable Row trackedRow;
    private static String unit = "m";
    private static @Nullable Language language;

    private NearbyView() {
    }

    public static int count() {
        return count;
    }

    /** The {@code index}-th nearest row. */
    public static Row row(int index) {
        return rows[order[index]];
    }

    public static @Nullable Row tracked() {
        return trackedRow;
    }

    private static void ensureCapacity(int size) {
        if (size <= rows.length) {
            return;
        }
        int capacity = Math.max(64, Math.max(size, rows.length * 2));
        Row[] grown = Arrays.copyOf(rows, capacity);
        for (int i = rows.length; i < capacity; i++) {
            grown[i] = new Row();
        }
        rows = grown;
        order = Arrays.copyOf(order, capacity);
        scratch = new int[capacity];
        seen = new boolean[capacity];
    }

    /** Forces all texts to be rebuilt (language, colors or names changed). */
    public static void invalidateText() {
        for (Row row : rows) {
            row.textFor = null;
            row.meters = -1;
        }
        FALLBACK.textFor = null;
        FALLBACK.meters = -1;
    }

    /** Recomputes the view for the player position. Call once per client tick. */
    public static void update(@Nullable ResourceKey<Level> dimension, double px, double pz, Font font) {
        Language current = Language.getInstance();
        if (current != language) {
            language = current;
            unit = I18n.get("structurecompass.unit.meters");
            invalidateText();
        }
        int previousCount = count;
        count = 0;
        trackedRow = null;
        if (dimension == null) {
            return;
        }
        ClientStructureData.DimensionData data = ClientStructureData.get(dimension);
        TrackedTarget target = TrackedTarget.get();
        int n = 0;
        if (data != null) {
            for (Map.Entry<Identifier, List<StructureEntry>> e : data.results.entrySet()) {
                Identifier type = e.getKey();
                boolean enabled = ClientConfig.isTypeEnabled(type);
                for (StructureEntry entry : e.getValue()) {
                    boolean isTracked = target != null && target.matches(dimension, type, entry);
                    if (!enabled && !isTracked) {
                        continue;
                    }
                    ensureCapacity(n + 1);
                    Row row = rows[n++];
                    fill(row, type, entry, isTracked, px, pz, font);
                    row.listed = true;
                }
            }
        }
        count = n;
        sortByDistance(previousCount);
        for (int i = 0; i < count; i++) {
            Row r = row(i);
            if (r.tracked) {
                trackedRow = r;
                break;
            }
        }
        if (trackedRow == null && target != null && target.dimension().equals(dimension)) {
            // The tracked instance is not in the current results (e.g. it dropped out of the top N): keep showing it.
            fill(FALLBACK, target.type(), target.entry(), true, px, pz, font);
            FALLBACK.listed = false;
            trackedRow = FALLBACK;
        }
    }

    private static void fill(Row row, Identifier type, StructureEntry entry, boolean isTracked, double px, double pz, Font font) {
        row.type = type;
        row.entry = entry;
        row.tracked = isTracked;
        row.distance = NavMath.horizontalDistance(entry.x() + 0.5 - px, entry.z() + 0.5 - pz);
        if (row.textFor != entry || row.textType != type) {
            row.color = StructureInfo.color(type);
            row.name = StructureInfo.name(type);
            row.coordsText = TextFormat.coordinates(entry.x(), entry.y(), entry.yKnown(), entry.z());
            row.nameWidth = font.width(row.name);
            row.coordsWidth = font.width(row.coordsText);
            row.textFor = entry;
            row.textType = type;
            row.meters = -1;
        }
        int meters = NavMath.roundMeters(row.distance);
        if (meters != row.meters) {
            row.meters = meters;
            row.distanceText = TextFormat.meters(meters, unit);
            row.distanceWidth = font.width(row.distanceText);
        }
    }

    /**
     * Insertion sort of the index array by distance, seeded with last tick's order (nearly sorted, so ~linear).
     */
    private static void sortByDistance(int previousCount) {
        int m = 0;
        Arrays.fill(seen, 0, count, false);
        for (int i = 0; i < previousCount && i < order.length; i++) {
            int idx = order[i];
            if (idx < count && !seen[idx]) {
                seen[idx] = true;
                scratch[m++] = idx;
            }
        }
        for (int idx = 0; idx < count; idx++) {
            if (!seen[idx]) {
                scratch[m++] = idx;
            }
        }
        for (int i = 1; i < m; i++) {
            int idx = scratch[i];
            double d = rows[idx].distance;
            int j = i - 1;
            while (j >= 0 && rows[scratch[j]].distance > d) {
                scratch[j + 1] = scratch[j];
                j--;
            }
            scratch[j + 1] = idx;
        }
        int[] swap = order;
        order = scratch;
        scratch = swap;
    }

    /** Clears everything (disconnect). */
    public static void reset() {
        count = 0;
        trackedRow = null;
        for (Row r : rows) {
            r.entry = null;
            r.type = null;
            r.textFor = null;
            r.textType = null;
            r.meters = -1;
        }
        FALLBACK.textFor = null;
    }
}
