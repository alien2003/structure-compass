package dev.alien2003.structurecompass.common.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorsTest {
    @Test
    void idColorsAreStableOpaqueAndDistinct() {
        int a = Colors.fromId("minecraft:village_plains");
        assertEquals(a, Colors.fromId("minecraft:village_plains"));
        assertEquals(0xFF, a >>> 24);
        assertNotEquals(a, Colors.fromId("minecraft:ancient_city"));
    }

    @Test
    void hsvPrimaries() {
        assertEquals(0xFFFF0000, Colors.hsvToArgb(0.0f, 1.0f, 1.0f));
        assertEquals(0xFF00FF00, Colors.hsvToArgb(1.0f / 3.0f, 1.0f, 1.0f));
        assertEquals(0xFF0000FF, Colors.hsvToArgb(2.0f / 3.0f, 1.0f, 1.0f));
        assertEquals(0xFFFFFFFF, Colors.hsvToArgb(0.5f, 0.0f, 1.0f));
    }

    @Test
    void parsesHexForms() {
        assertEquals(0xFF12AB34, Colors.parse("#12AB34", 0));
        assertEquals(0xFF12AB34, Colors.parse("12ab34", 0));
        assertEquals(0x8012AB34, Colors.parse("#8012AB34", 0));
        assertEquals(0xFF12AB34, Colors.parse("0x12AB34", 0));
        assertEquals(7, Colors.parse("#12AB3", 7));
        assertEquals(7, Colors.parse("#GGGGGG", 7));
        assertEquals(7, Colors.parse(null, 7));
        assertTrue(Colors.isValid("#FFFFFF"));
        assertFalse(Colors.isValid("white"));
    }

    @Test
    void hexRoundTripAndChannels() {
        assertEquals("#12AB34", Colors.toHex(0xFF12AB34));
        assertEquals(0x4012AB34, Colors.withAlpha(0xFF12AB34, 0x40));
        assertEquals(0xFF081018, Colors.scaleRgb(0xFF102030, 0.5f));
        assertEquals(1.0f, Colors.alpha(0xFF000000), 1e-6);
        assertEquals(1.0f, Colors.red(0x00FF0000), 1e-6);
    }
}
