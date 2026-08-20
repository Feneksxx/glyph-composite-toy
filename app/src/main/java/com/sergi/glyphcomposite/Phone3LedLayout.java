package com.sergi.glyphcomposite;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Exact Phone (3) LED allocation, generated from the official
 * "Phone 3 Glyph Matrix LED allocation.svg" included with GDK 1.1.
 * A 1 means that the corresponding 25x25 coordinate is a physical LED.
 */
final class Phone3LedLayout {
    private static final String[] SDK_ALLOCATION = {
        "0000000001111111000000000",
        "0000000111111111110000000",
        "0000011111111111111100000",
        "0000111111111111111110000",
        "0001111111111111111111000",
        "0011111111111111111111100",
        "0011111111111111111111100",
        "0111111111111111111111110",
        "0111111111111111111111110",
        "1111111111111111111111111",
        "1111111111111111111111111",
        "1111111111111111111111111",
        "1111111111111111111111111",
        "1111111111111111111111111",
        "1111111111111111111111111",
        "1111111111111111111111111",
        "0111111111111111111111110",
        "0111111111111111111111110",
        "0011111111111111111111100",
        "0011111111111111111111100",
        "0001111111111111111111000",
        "0000111111111111111110000",
        "0000011111111111111100000",
        "0000000111111111110000000",
        "0000000001110111000000000"
    };

    static final Set<Integer> VALID_LEDS = createValidSet();
    static final Set<Integer> EDGE_RING = computeEdgeRing(VALID_LEDS);

    private Phone3LedLayout() { }

    static boolean isValid(int x, int y) { return VALID_LEDS.contains(key(x, y)); }

    private static Set<Integer> createValidSet() {
        Set<Integer> result = new HashSet<>();
        for (int y = 0; y < SDK_ALLOCATION.length; y++) {
            for (int x = 0; x < SDK_ALLOCATION[y].length(); x++) {
                if (SDK_ALLOCATION[y].charAt(x) == '1') result.add(key(x, y));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** A valid LED is on the outline when one of its four direct neighbours is absent. */
    private static Set<Integer> computeEdgeRing(Set<Integer> ledSet) {
        Set<Integer> result = new HashSet<>();
        for (int point : ledSet) {
            int x = point % 25;
            int y = point / 25;
            if (!contains(ledSet, x + 1, y) || !contains(ledSet, x - 1, y)
                    || !contains(ledSet, x, y + 1) || !contains(ledSet, x, y - 1)) {
                result.add(point);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static boolean contains(Set<Integer> ledSet, int x, int y) {
        return x >= 0 && x < 25 && y >= 0 && y < 25 && ledSet.contains(key(x, y));
    }

    static int key(int x, int y) { return y * 25 + x; }
    static int x(int point) { return point % 25; }
    static int y(int point) { return point / 25; }
}
