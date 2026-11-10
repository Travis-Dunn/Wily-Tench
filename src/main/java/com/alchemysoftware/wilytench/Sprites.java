package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.sprite.SpriteAtlas;
import com.alchemysoftware.whitetail.sprite.SpriteColorMap;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * The game's sprite sheets, recolorings, and drawing layers.
 *
 * The sheets are made from the purchased art by AtlasTool. A "color
 * map" redraws a sprite in different colors by swapping palette
 * indices, which is how one rock picture serves for several ores.
 */
final class Sprites {
    /** Width and height in pixels of every sprite and map tile. */
    static final int TILE = Sheet.CELL;

    static final int ATLAS_CAVE = 0;
    static final int ATLAS_HEROES = 1;
    static final int ATLAS_ITEMS = 2;
    static final int ATLAS_DUNGEON = 3;
    private static final int ATLAS_COUNT = 4;

    /* Drawn back to front. */
    static final int LAYER_FLOOR = 0;
    static final int LAYER_OBJECT = 1;
    static final int LAYER_ACTOR = 2;

    static final int COLOR_MAP_NONE = 0;
    /** Turns the orange rock into dark red-brown iron rock. */
    static final int COLOR_MAP_IRON = 1;
    private static final int COLOR_MAP_COUNT = 2;

    private static final String[] FILES = {
        "cave.wsa", "heroes.wsa", "items.wsa", "dungeon.wsa"
    };

    /* A "picture" is an atlas and a frame in one number. */
    private static final int PICTURE_ATLAS_SHIFT = 16;
    private static final int PICTURE_FRAME_MASK = 0xFFFF;

    /* { from, to } palette index pairs. */
    private static final int[][] IRON_SWAPS = {
        { Palette.TAN, Palette.RUST }, { Palette.RUST, Palette.MAROON },
        { Palette.MAROON, Palette.PLUM }
    };

    private static final Sheet[] sheets = new Sheet[ATLAS_COUNT];
    private static final byte[][] colorMaps = new byte[COLOR_MAP_COUNT][];

    private Sprites() {}

    /**
     * Loads every sheet and color map and gives them to the sprite
     * renderer, which must be initialized.
     *
     * @return false after a fatal error has been reported
     */
    static boolean Install() {
        for (int i = 0; i < ATLAS_COUNT; i++) {
            if ((sheets[i] = Sheet.Load(FILES[i])) == null) return false;

            SpriteAtlas atlas = SpriteAtlas.Create(sheets[i].w, sheets[i].h,
                    Sheet.CELL, sheets[i].texels);
            if (atlas == null) return false;
            SpriteRenderer.InstallAtlas(i, atlas);
        }

        colorMaps[COLOR_MAP_IRON] = BuildMap(IRON_SWAPS);
        SpriteColorMap iron = SpriteColorMap.Create(colorMaps[COLOR_MAP_IRON]);
        if (iron == null) return false;
        SpriteRenderer.InstallColorMap(COLOR_MAP_IRON, iron);
        return true;
    }

    static void Shutdown() {
        for (int i = 0; i < ATLAS_COUNT; i++) sheets[i] = null;
        for (int i = 0; i < COLOR_MAP_COUNT; i++) colorMaps[i] = null;
    }

    /** @return the sheet behind an atlas, for drawing outside the world */
    static Sheet SheetOf(int atlasId) { return sheets[atlasId]; }

    /** @return a color map's 256 entries, or null for COLOR_MAP_NONE */
    static byte[] ColorMap(int colorMapId) { return colorMaps[colorMapId]; }

    /**
     * @return one number standing for the picture at column col, row
     *         row of an atlas's sheet; never negative
     */
    static int Picture(int atlasId, int col, int row) {
        return atlasId << PICTURE_ATLAS_SHIFT |
                sheets[atlasId].Frame(col, row);
    }

    /** @return the atlas a picture is in */
    static int AtlasOf(int picture) {
        return picture >> PICTURE_ATLAS_SHIFT;
    }

    /** @return a picture's frame within its atlas */
    static int FrameOf(int picture) {
        return picture & PICTURE_FRAME_MASK;
    }

    /** @return the frame for column col, row row of the cave sheet */
    static int Cave(int col, int row) {
        return sheets[ATLAS_CAVE].Frame(col, row);
    }

    /** @return the frame for column col, row row of the first hero */
    static int Hero(int col, int row) {
        return sheets[ATLAS_HEROES].Frame(col, row);
    }

    /** @return the frame for column col, row row of the items sheet */
    static int ItemIcon(int col, int row) {
        return sheets[ATLAS_ITEMS].Frame(col, row);
    }

    private static byte[] BuildMap(int[][] swaps) {
        byte[] map = new byte[Palette.SIZE];
        for (int i = 0; i < map.length; i++) map[i] = (byte) i;
        for (int i = 0; i < swaps.length; i++)
            map[swaps[i][0]] = (byte) swaps[i][1];
        return map;
    }
}
