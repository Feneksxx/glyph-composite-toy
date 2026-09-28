package com.feneksxx.glyphcomposite;

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
        "0000000001111111000000000"
    };

    private static final boolean[][] VALID_LEDS = createLedMap();

    private Phone3LedLayout() { }

    static boolean isValid(int x, int y) {
        return x >= 0 && x < 25 && y >= 0 && y < 25 && VALID_LEDS[y][x];
    }

    private static boolean[][] createLedMap() {
        boolean[][] result = new boolean[25][25];
        for (int y = 0; y < SDK_ALLOCATION.length; y++) {
            for (int x = 0; x < SDK_ALLOCATION[y].length(); x++) {
                result[y][x] = SDK_ALLOCATION[y].charAt(x) == '1';
            }
        }
        return result;
    }
}
