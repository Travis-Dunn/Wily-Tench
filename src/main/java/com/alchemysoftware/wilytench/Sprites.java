package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.sprite.SpriteAtlas;
import com.alchemysoftware.whitetail.sprite.SpriteColorMap;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * The game's sprite sheets and recolorings, as listed in
 * data/sprites.json, and its drawing layers.
 *
 * A sheet is a grid of 16 pixel pictures made from the purchased art
 * by AtlasTool; data files point at a picture by its sheet's id and
 * the picture's column and row, both counted from zero. docs/sprites
 * holds a labeled image of every sheet for looking them up.
 *
 * A "color map" redraws a sprite in different colors by swapping
 * palette colors, which is how one rock picture serves several ores.
 *
 * Inside the game a "picture" is one number that stands for a sheet
 * and a cell of it.
 */
final class Sprites {
    /** Width and height in pixels of every sprite and map tile. */
    static final int TILE = Sheet.CELL;

    /* Drawn back to front. */
    static final int LAYER_FLOOR = 0;
    static final int LAYER_OBJECT = 1;
    static final int LAYER_ACTOR = 2;

    static final int COLOR_MAP_NONE = 0;

    /* Whitetail's sprite renderer has eight atlas banks, and seven
       color map banks beyond the built-in "no change". */
    private static final int MAX_SHEETS = 8;
    private static final int MAX_COLOR_MAPS = 7;

    private static final String HERO_SHEET = "heroes";

    private static final int PICTURE_ATLAS_SHIFT = 16;
    private static final int PICTURE_FRAME_MASK = 0xFFFF;

    private static final String[] SHEET_KEYS = {
        "id", "file", "source", "region"
    };
    private static final String[] COLOR_MAP_KEYS = { "id", "swaps" };

    private static String[] sheetIds = new String[0];
    private static Sheet[] sheets = new Sheet[0];
    /* How much of each sheet holds pictures; the rest is padding. */
    private static int[] sheetCols, sheetRows;

    /* Index 0 of each is COLOR_MAP_NONE and holds null. */
    private static String[] colorMapIds = new String[1];
    private static byte[][] colorMaps = new byte[1][];

    private static int heroAtlas;

    private Sprites() {}

    /**
     * Loads every sheet and color map the file lists and gives them
     * to the sprite renderer, which must be initialized.
     *
     * @param root the contents of data/sprites.json
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "sheets", "color_maps" });

        DataNode[] sheetNodes = root.Groups("sheets", "sheet");
        int n = sheetNodes.length, i;
        if (n > MAX_SHEETS) {
            throw root.Fail("there are " + n + " sheets, but the sprite " +
                    "renderer can hold only " + MAX_SHEETS + ". Sheets " +
                    "will have to be combined.");
        }

        sheetIds = new String[n];
        sheets = new Sheet[n];
        sheetCols = new int[n];
        sheetRows = new int[n];

        for (i = 0; i < n; i++) {
            String id = sheetNodes[i].Str("id");
            DataNode node = sheetNodes[i].Called("sprites.json, sheet [" +
                    id + "]");
            node.Only(SHEET_KEYS);
            for (int j = 0; j < i; j++) {
                if (sheetIds[j].equals(id))
                    throw node.Fail("there are two sheets with this id.");
            }

            int[] region = node.Ints("region", 4);
            Sheet sheet = Sheet.Load(node.Str("file"));
            if (sheet == null)
                throw node.Fail("its file could not be loaded.");
            if (region[2] > sheet.w || region[3] > sheet.h) {
                throw node.Fail("its region is larger than its file; " +
                        "rebuild the atlases.");
            }

            SpriteAtlas atlas = SpriteAtlas.Create(sheet.w, sheet.h,
                    Sheet.CELL, sheet.texels);
            if (atlas == null)
                throw node.Fail("the sprite renderer rejected it.");
            SpriteRenderer.InstallAtlas(i, atlas);

            sheetIds[i] = id;
            sheets[i] = sheet;
            sheetCols[i] = region[2] / Sheet.CELL;
            sheetRows[i] = region[3] / Sheet.CELL;
        }

        DataNode[] mapNodes = root.OptGroups("color_maps", "color map");
        if (mapNodes.length > MAX_COLOR_MAPS) {
            throw root.Fail("there are " + mapNodes.length + " color " +
                    "maps, but the sprite renderer can hold only " +
                    MAX_COLOR_MAPS + ".");
        }

        colorMapIds = new String[mapNodes.length + 1];
        colorMaps = new byte[mapNodes.length + 1][];

        for (i = 0; i < mapNodes.length; i++) {
            String id = mapNodes[i].Str("id");
            DataNode node = mapNodes[i].Called("sprites.json, color map [" +
                    id + "]");
            node.Only(COLOR_MAP_KEYS);
            for (int j = 1; j <= i; j++) {
                if (colorMapIds[j].equals(id))
                    throw node.Fail("there are two color maps with this id.");
            }

            byte[] map = BuildMap(node, node.StrPairs("swaps"));
            SpriteColorMap installed = SpriteColorMap.Create(map);
            if (installed == null)
                throw node.Fail("the sprite renderer rejected it.");
            SpriteRenderer.InstallColorMap(i + 1, installed);

            colorMapIds[i + 1] = id;
            colorMaps[i + 1] = map;
        }

        heroAtlas = Atlas(root, HERO_SHEET);
    }

    static void Shutdown() {
        sheetIds = new String[0];
        sheets = new Sheet[0];
        colorMapIds = new String[1];
        colorMaps = new byte[1][];
    }

    /**
     * @param from the group naming the picture, for error messages
     * @param cell a column and a row
     * @return the picture at that cell of the sheet called sheetId
     */
    static int Picture(DataNode from, String sheetId, int[] cell) {
        int atlas = Atlas(from, sheetId);

        if (cell[0] < 0 || cell[1] < 0 || cell[0] >= sheetCols[atlas] ||
                cell[1] >= sheetRows[atlas]) {
            throw from.Fail("cell [" + cell[0] + ", " + cell[1] + "] is " +
                    "outside the sheet [" + sheetId + "], which has " +
                    "columns 0 to " + (sheetCols[atlas] - 1) +
                    " and rows 0 to " + (sheetRows[atlas] - 1) + ".");
        }
        return atlas << PICTURE_ATLAS_SHIFT |
                sheets[atlas].Frame(cell[0], cell[1]);
    }

    /**
     * @param from the group naming the color map, for error messages
     * @return the color map called id
     */
    static int ColorMapNamed(DataNode from, String id) {
        for (int i = 1; i < colorMapIds.length; i++)
            if (colorMapIds[i].equals(id)) return i;
        throw from.Fail("there is no color map called [" + id +
                "] in sprites.json.");
    }

    /** @return the atlas a picture is in */
    static int AtlasOf(int picture) {
        return picture >> PICTURE_ATLAS_SHIFT;
    }

    /** @return a picture's frame within its atlas */
    static int FrameOf(int picture) {
        return picture & PICTURE_FRAME_MASK;
    }

    /** Draws a picture outside the world view, top left at (x, y). */
    static void Draw(int picture, int colorMap, int x, int y) {
        Gfx.DrawFrame(sheets[AtlasOf(picture)], FrameOf(picture), x, y,
                colorMaps[colorMap]);
    }

    /** @return the atlas holding the player's character */
    static int HeroAtlas() { return heroAtlas; }

    /** @return the frame for column col, row row of the hero sheet */
    static int Hero(int col, int row) {
        return sheets[heroAtlas].Frame(col, row);
    }

    private static int Atlas(DataNode from, String sheetId) {
        for (int i = 0; i < sheetIds.length; i++)
            if (sheetIds[i].equals(sheetId)) return i;
        throw from.Fail("there is no sheet called [" + sheetId +
                "] in sprites.json.");
    }

    private static byte[] BuildMap(DataNode node, String[][] swaps) {
        byte[] map = new byte[Palette.SIZE];
        for (int i = 0; i < map.length; i++) map[i] = (byte) i;

        for (int i = 0; i < swaps.length; i++) {
            int from = Palette.Named(swaps[i][0]);
            int to = Palette.Named(swaps[i][1]);
            if (from < 0 || to < 0) {
                throw node.Fail("[" + (from < 0 ? swaps[i][0] : swaps[i][1]) +
                        "] is not a palette color. The colors are: " +
                        Palette.Names() + ".");
            }
            map[from] = (byte) to;
        }
        return map;
    }
}
