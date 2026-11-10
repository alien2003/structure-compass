package dev.alien2003.structurecompass.client;

/**
 * Connection state of the mod on the client. Main thread only.
 */
public final class ClientSession {
    public enum State {
        /** Not in a world. */
        OFFLINE,
        /** Joined; waiting for the server hello. */
        WAITING,
        /** The server runs Structure Compass and allows searching. */
        ACTIVE,
        /** The server does not have the mod (or does not allow it): the mod stays silent. */
        DISABLED
    }

    private static State state = State.OFFLINE;
    private static int maxRadius = 32000;

    private ClientSession() {
    }

    public static State state() {
        return state;
    }

    public static boolean isActive() {
        return state == State.ACTIVE;
    }

    public static void setState(State newState) {
        state = newState;
    }

    public static int maxRadius() {
        return maxRadius;
    }

    public static void setMaxRadius(int value) {
        maxRadius = value;
    }
}
