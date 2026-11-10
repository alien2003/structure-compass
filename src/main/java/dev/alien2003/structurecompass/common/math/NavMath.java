package dev.alien2003.structurecompass.common.math;

/**
 * Pure navigation math (no Minecraft dependencies, unit-tested).
 *
 * <p>Minecraft yaw convention: 0 = facing +Z (south), 90 = facing -X (west), 180/-180 = facing -Z (north),
 * -90 = facing +X (east). Yaw grows clockwise when seen from above, i.e. a positive relative angle means
 * "the target is to the right".
 */
public final class NavMath {
    private NavMath() {
    }

    /** Horizontal (XZ) distance. */
    public static double horizontalDistance(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Squared horizontal distance (cheap comparisons). */
    public static double horizontalDistanceSqr(double dx, double dz) {
        return dx * dx + dz * dz;
    }

    /** Yaw (degrees, Minecraft convention, in [-180, 180]) that faces the direction (dx, dz). */
    public static float yawTowards(double dx, double dz) {
        if (dx == 0.0 && dz == 0.0) {
            return 0.0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Wraps an angle in degrees into (-180, 180]. */
    public static float wrapDegrees(float degrees) {
        float d = degrees % 360.0f;
        if (d > 180.0f) {
            d -= 360.0f;
        } else if (d <= -180.0f) {
            d += 360.0f;
        }
        return d;
    }

    /** Wraps an angle in degrees into (-180, 180]. */
    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }

    /**
     * Angle of the target relative to where the player looks, in (-180, 180]. Positive = to the right
     * (clockwise on screen), 0 = straight ahead, 180 = behind.
     */
    public static float relativeYaw(float playerYaw, double dx, double dz) {
        return wrapDegrees(yawTowards(dx, dz) - playerYaw);
    }

    /**
     * Cardinal-ish compass heading in degrees where 0 = north, 90 = east, 180 = south, 270 = west, for a yaw.
     */
    public static float headingFromYaw(float yaw) {
        float h = (yaw + 180.0f) % 360.0f;
        if (h < 0.0f) {
            h += 360.0f;
        }
        return h;
    }

    /**
     * Position of a marker on a horizontal compass bar.
     *
     * @param relativeDeg  angle relative to view direction, (-180, 180]
     * @param halfRangeDeg half of the angular range covered by the bar (e.g. 90 = bar spans 180 degrees)
     * @param halfWidth    half width of the bar in pixels
     * @return x offset from bar center in pixels, or {@link Float#NaN} when outside the bar range
     */
    public static float compassBarOffset(float relativeDeg, float halfRangeDeg, float halfWidth) {
        if (halfRangeDeg <= 0.0f || Math.abs(relativeDeg) > halfRangeDeg) {
            return Float.NaN;
        }
        return relativeDeg / halfRangeDeg * halfWidth;
    }

    /** Same as {@link #compassBarOffset} but clamps markers outside the range to the bar edge. */
    public static float compassBarOffsetClamped(float relativeDeg, float halfRangeDeg, float halfWidth) {
        if (halfRangeDeg <= 0.0f) {
            return 0.0f;
        }
        float clamped = Math.max(-halfRangeDeg, Math.min(halfRangeDeg, relativeDeg));
        return clamped / halfRangeDeg * halfWidth;
    }

    /**
     * Distance at which a world marker should be drawn: the real distance when it is inside the visible range,
     * otherwise pinned to the visible range so it stays on screen ("clamped to the view distance").
     */
    public static double clampMarkerDistance(double distance, double maxVisible) {
        if (maxVisible <= 0.0) {
            return distance;
        }
        return Math.min(distance, maxVisible);
    }

    /** Rounds a distance in blocks (= meters) to an int, saturating. */
    public static int roundMeters(double distance) {
        if (distance >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.round(Math.max(0.0, distance));
    }
}
