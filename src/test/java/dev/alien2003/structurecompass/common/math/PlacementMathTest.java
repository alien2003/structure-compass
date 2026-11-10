package dev.alien2003.structurecompass.common.math;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementMathTest {
    @Test
    void regionIsFloorDivision() {
        assertEquals(0, PlacementMath.regionOf(0, 32));
        assertEquals(0, PlacementMath.regionOf(31, 32));
        assertEquals(1, PlacementMath.regionOf(32, 32));
        assertEquals(-1, PlacementMath.regionOf(-1, 32));
        assertEquals(-2, PlacementMath.regionOf(-33, 32));
        assertEquals(-1, PlacementMath.chunkOf(-1));
        assertEquals(1, PlacementMath.chunkOf(16));
    }

    @Test
    void ringsEnumerateEachCellExactlyOnce() {
        Set<Long> seen = new HashSet<>();
        for (int k = 0; k <= 6; k++) {
            for (int i = 0; i < PlacementMath.ringSize(k); i++) {
                long cell = PlacementMath.ringCell(k, i);
                int x = PlacementMath.unpackX(cell);
                int z = PlacementMath.unpackZ(cell);
                assertEquals(k, Math.max(Math.abs(x), Math.abs(z)), "cell not on ring " + k);
                assertTrue(seen.add(cell), "duplicate cell " + x + "," + z);
            }
        }
        assertEquals(13 * 13, seen.size());
    }

    @Test
    void packRoundTripsNegativeValues() {
        long p = PlacementMath.pack(-123456, 789);
        assertEquals(-123456, PlacementMath.unpackX(p));
        assertEquals(789, PlacementMath.unpackZ(p));
        long q = PlacementMath.pack(5, -7);
        assertEquals(5, PlacementMath.unpackX(q));
        assertEquals(-7, PlacementMath.unpackZ(q));
    }

    @Test
    void ringLowerBoundIsConservative() {
        int spacing = 10;
        // brute force: player anywhere in region (0,0), candidate anywhere in a ring-k region
        for (int k = 0; k <= 4; k++) {
            double bound = PlacementMath.ringMinDistanceBlocks(k, spacing);
            double min = Double.MAX_VALUE;
            for (int i = 0; i < PlacementMath.ringSize(k); i++) {
                long cell = PlacementMath.ringCell(k, i);
                int rx = PlacementMath.unpackX(cell);
                int rz = PlacementMath.unpackZ(cell);
                // nearest corner of the candidate region to the player's region, in blocks
                double dx = gap(0, spacing * 16 - 1, rx * spacing * 16, rx * spacing * 16 + spacing * 16 - 1);
                double dz = gap(0, spacing * 16 - 1, rz * spacing * 16, rz * spacing * 16 + spacing * 16 - 1);
                min = Math.min(min, Math.sqrt(dx * dx + dz * dz));
            }
            assertTrue(bound <= min + 1e-9, "ring " + k + " bound " + bound + " > actual " + min);
        }
    }

    @Test
    void ringsForRadiusCoversRadius() {
        int spacing = 32;
        int rings = PlacementMath.ringsForRadius(5000, spacing);
        assertTrue(PlacementMath.ringMinDistanceBlocks(rings + 1, spacing) > 5000);
        assertEquals(0, PlacementMath.ringsForRadius(100, 0));
    }

    private static double gap(int a0, int a1, int b0, int b1) {
        if (b0 > a1) {
            return b0 - a1;
        }
        if (a0 > b1) {
            return a0 - b1;
        }
        return 0;
    }
}
