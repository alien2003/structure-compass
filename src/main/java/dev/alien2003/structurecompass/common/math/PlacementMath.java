package dev.alien2003.structurecompass.common.math;

/**
 * Pure grid math used by the structure locator (no Minecraft dependencies, unit-tested).
 *
 * <p>Random-spread structure placement divides the world into square regions of {@code spacing} chunks;
 * every region has at most one candidate chunk. The locator walks regions ring by ring (Chebyshev rings)
 * around the player's region.
 */
public final class PlacementMath {
    private PlacementMath() {
    }

    /** Region coordinate of a chunk coordinate for a given spacing (floor division). */
    public static int regionOf(int chunkCoord, int spacing) {
        return Math.floorDiv(chunkCoord, spacing);
    }

    /** Chunk coordinate of a block coordinate. */
    public static int chunkOf(int blockCoord) {
        return blockCoord >> 4;
    }

    /** Number of cells in Chebyshev ring {@code k} (ring 0 = 1 cell, ring k = 8k cells). */
    public static int ringSize(int k) {
        return k == 0 ? 1 : 8 * k;
    }

    /**
     * The i-th cell of Chebyshev ring {@code k} around (0, 0), as a packed long (x in high int, z in low int).
     * Cells are enumerated without duplicates for i in [0, ringSize(k)).
     */
    public static long ringCell(int k, int i) {
        if (k == 0) {
            return pack(0, 0);
        }
        int side = 2 * k; // cells per side when walking the perimeter
        int edge = i / side;
        int off = i % side;
        return switch (edge) {
            case 0 -> pack(-k + off, -k);      // top edge, left -> right (excluding top-right corner)
            case 1 -> pack(k, -k + off);       // right edge, top -> bottom
            case 2 -> pack(k - off, k);        // bottom edge, right -> left
            default -> pack(-k, k - off);      // left edge, bottom -> top
        };
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    /**
     * Conservative lower bound (in blocks) of the horizontal distance between any block of the player's chunk
     * region and any chunk inside Chebyshev ring {@code k} of regions with the given spacing (chunks).
     * Used to stop the ring walk once enough results closer than this bound are known.
     */
    public static double ringMinDistanceBlocks(int k, int spacing) {
        if (k <= 1) {
            return 0.0;
        }
        return (double) (k - 1) * spacing * 16.0;
    }

    /** Number of rings needed so that the ring lower bound exceeds the radius (blocks). */
    public static int ringsForRadius(int radiusBlocks, int spacing) {
        if (spacing <= 0) {
            return 0;
        }
        int regionSize = spacing * 16;
        return (int) Math.ceil((double) radiusBlocks / regionSize) + 1;
    }
}
