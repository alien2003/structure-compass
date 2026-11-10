package dev.alien2003.structurecompass.common.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavMathTest {
    private static final float EPS = 1.0e-3f;

    @Test
    void distanceIsEuclideanInXZ() {
        assertEquals(5.0, NavMath.horizontalDistance(3, 4), 1e-9);
        assertEquals(25.0, NavMath.horizontalDistanceSqr(-3, 4), 1e-9);
        assertEquals(0.0, NavMath.horizontalDistance(0, 0), 1e-9);
    }

    @Test
    void yawTowardsFollowsMinecraftConvention() {
        assertEquals(0.0f, NavMath.yawTowards(0, 10), EPS);      // south (+Z)
        assertEquals(90.0f, NavMath.yawTowards(-10, 0), EPS);    // west (-X)
        assertEquals(180.0f, Math.abs(NavMath.yawTowards(0, -10)), EPS); // north (-Z)
        assertEquals(-90.0f, NavMath.yawTowards(10, 0), EPS);    // east (+X)
        assertEquals(-45.0f, NavMath.yawTowards(10, 10), EPS);   // south-east
        assertEquals(0.0f, NavMath.yawTowards(0, 0), EPS);
    }

    @Test
    void wrapDegreesStaysInHalfOpenRange() {
        assertEquals(0.0f, NavMath.wrapDegrees(360.0f), EPS);
        assertEquals(180.0f, NavMath.wrapDegrees(-180.0f), EPS);
        assertEquals(180.0f, NavMath.wrapDegrees(180.0f), EPS);
        assertEquals(-170.0f, NavMath.wrapDegrees(190.0f), EPS);
        assertEquals(10.0f, NavMath.wrapDegrees(-710.0f), EPS);
        assertEquals(-90.0, NavMath.wrapDegrees(270.0), 1e-9);
        for (float a = -1000; a < 1000; a += 7.3f) {
            float w = NavMath.wrapDegrees(a);
            assertTrue(w > -180.0f && w <= 180.0f, "wrap(" + a + ") = " + w);
        }
    }

    @Test
    void relativeYawPositiveMeansRight() {
        // Player looks south (+Z). East (+X) is on the player's left, west (-X) on the right.
        assertEquals(90.0f, NavMath.relativeYaw(0.0f, -10, 0), EPS);
        assertEquals(-90.0f, NavMath.relativeYaw(0.0f, 10, 0), EPS);
        assertEquals(0.0f, NavMath.relativeYaw(0.0f, 0, 10), EPS);
        assertEquals(180.0f, Math.abs(NavMath.relativeYaw(0.0f, 0, -10)), EPS);
        // Player looks north (yaw 180); target east is on the right.
        assertEquals(90.0f, NavMath.relativeYaw(180.0f, 10, 0), EPS);
        // Yaw is not normalised by the game: 720 + 180 is still north.
        assertEquals(90.0f, NavMath.relativeYaw(900.0f, 10, 0), EPS);
    }

    @Test
    void headingIsZeroNorthClockwise() {
        assertEquals(0.0f, NavMath.headingFromYaw(180.0f), EPS);  // north
        assertEquals(90.0f, NavMath.headingFromYaw(-90.0f), EPS); // east
        assertEquals(180.0f, NavMath.headingFromYaw(0.0f), EPS);  // south
        assertEquals(270.0f, NavMath.headingFromYaw(90.0f), EPS); // west
    }

    @Test
    void compassBarMapsLinearlyAndHidesOutOfRange() {
        assertEquals(0.0f, NavMath.compassBarOffset(0, 90, 100), EPS);
        assertEquals(50.0f, NavMath.compassBarOffset(45, 90, 100), EPS);
        assertEquals(-100.0f, NavMath.compassBarOffset(-90, 90, 100), EPS);
        assertTrue(Float.isNaN(NavMath.compassBarOffset(120, 90, 100)));
        assertEquals(100.0f, NavMath.compassBarOffsetClamped(170, 90, 100), EPS);
        assertEquals(-100.0f, NavMath.compassBarOffsetClamped(-170, 90, 100), EPS);
    }

    @Test
    void markerDistanceIsClampedToVisibleRange() {
        assertEquals(100.0, NavMath.clampMarkerDistance(100, 200), 1e-9);
        assertEquals(200.0, NavMath.clampMarkerDistance(5000, 200), 1e-9);
        assertEquals(5000.0, NavMath.clampMarkerDistance(5000, 0), 1e-9);
    }

    @Test
    void roundMetersSaturates() {
        assertEquals(12, NavMath.roundMeters(11.6));
        assertEquals(0, NavMath.roundMeters(-3));
        assertEquals(Integer.MAX_VALUE, NavMath.roundMeters(1e12));
    }
}
