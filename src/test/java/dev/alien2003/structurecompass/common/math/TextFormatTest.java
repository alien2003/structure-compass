package dev.alien2003.structurecompass.common.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFormatTest {
    @Test
    void groupsThousands() {
        assertEquals("0", TextFormat.groupThousands(0));
        assertEquals("999", TextFormat.groupThousands(999));
        assertEquals("1 000", TextFormat.groupThousands(1000));
        assertEquals("12 345", TextFormat.groupThousands(12345));
        assertEquals("1 234 567", TextFormat.groupThousands(1234567));
        assertEquals("-1 234", TextFormat.groupThousands(-1234));
    }

    @Test
    void formatsMetersWithUnit() {
        assertEquals("523 m", TextFormat.meters(523, "m"));
        assertEquals("1 523 м", TextFormat.meters(1523, "м"));
    }

    @Test
    void formatsCoordinatesWithUnknownY() {
        assertEquals("X 100  Y 64  Z -200", TextFormat.coordinates(100, 64, true, -200));
        assertEquals("X 100  Y ~  Z -200", TextFormat.coordinates(100, 0, false, -200));
        assertEquals("100 -27 5", TextFormat.clipboardCoordinates(100, -27, true, 5));
        assertEquals("100 ~ 5", TextFormat.clipboardCoordinates(100, 12, false, 5));
    }

    @Test
    void humanizesRegistryPaths() {
        assertEquals("Village Plains", TextFormat.humanize("village_plains"));
        assertEquals("Ruins Big Tower", TextFormat.humanize("ruins/big-tower"));
        assertEquals("Ancient City", TextFormat.humanize("ancient_city"));
        assertEquals("A B", TextFormat.humanize("__a__b__"));
        assertEquals("", TextFormat.humanize(""));
        assertEquals("", TextFormat.humanize(null));
    }

    @Test
    void normalizesSearch() {
        assertEquals("ancient city", TextFormat.normalizeForSearch("  Ancient CITY "));
        assertEquals("", TextFormat.normalizeForSearch(null));
    }
}
