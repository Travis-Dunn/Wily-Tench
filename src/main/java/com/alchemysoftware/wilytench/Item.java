package com.alchemysoftware.wilytench;

/**
 * The kinds of thing that can be carried. An item is just a number;
 * this class knows each one's name and what its icon looks like.
 *
 * The art has no pictures of ore, so ore borrows the picture of the
 * rock it came from, and no pickaxe, so rocks are mined with a hammer.
 */
final class Item {
    static final int NONE = -1;

    static final int BRONZE_HAMMER = 0;
    static final int COPPER_ORE = 1;
    static final int IRON_ORE = 2;
    static final int COUNT = 3;

    private static final String[] NAMES = {
        "Bronze hammer", "Copper ore", "Iron ore"
    };
    private static final int[] ATLASES = {
        Sprites.ATLAS_ITEMS, Sprites.ATLAS_CAVE, Sprites.ATLAS_CAVE
    };
    /* { column, row } on that atlas's sheet. */
    private static final int[][] CELLS = { { 3, 1 }, { 5, 1 }, { 5, 1 } };
    private static final int[] COLOR_MAPS = {
        Sprites.COLOR_MAP_NONE, Sprites.COLOR_MAP_NONE,
        Sprites.COLOR_MAP_IRON
    };

    private Item() {}

    /** @return the name as it heads a label: "Copper ore" */
    static String Name(int item) { return NAMES[item]; }

    /** @return the name as it reads mid-sentence: "copper ore" */
    static String LowerName(int item) { return NAMES[item].toLowerCase(); }

    /** Draws the item's 16 pixel icon with its top left at (x, y). */
    static void DrawIcon(int item, int x, int y) {
        Sheet sheet = Sprites.SheetOf(ATLASES[item]);
        Gfx.DrawFrame(sheet, sheet.Frame(CELLS[item][0], CELLS[item][1]),
                x, y, Sprites.ColorMap(COLOR_MAPS[item]));
    }
}
