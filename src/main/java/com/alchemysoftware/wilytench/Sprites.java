package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.sprite.SpriteAtlas;
import com.alchemysoftware.whitetail.sprite.SpriteColorMap;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * The game's art: its sprite sheets and recolorings, as listed in
 * data/sprites.json, and its drawing layers.
 *
 * A sheet is a grid of 16 pixel pictures taken from one of the
 * purchased images. Data files point at a picture by its sheet's id
 * and the picture's column and row, both counted from zero.
 * docs/sprites holds a labeled image of every sheet for looking them
 * up.
 *
 * Whitetail's sprite renderer can hold only eight atlases, so
 * AtlasTool packs all the sheets together into a few large ones and
 * writes sprites/index.json to say where each sheet went. This class
 * reads that index and does the arithmetic, so that nothing else in
 * the game, and nobody writing data files, need know about atlases at
 * all. It also checks the index against sprites.json and the palette,
 * and says so when the atlases need rebuilding.
 *
 * A "color map" redraws a sprite in different colors by swapping
 * palette colors, which is how one rock picture serves several ores.
 *
 * Inside the game a "picture" is one number that stands for an atlas
 * and a frame of it.
 */
final class Sprites {
    /** Width and height in pixels of every sprite and map tile. */
    static final int TILE = Sheet.CELL;

    /* Drawn back to front. */
    static final int LAYER_FLOOR = 0;
    static final int LAYER_OBJECT = 1;
    static final int LAYER_ACTOR = 2;

    static final int COLOR_MAP_NONE = 0;

    /* Whitetail's sprite renderer has seven color map banks beyond
       the built-in "no change". AtlasTool keeps to its eight atlases. */
    private static final int MAX_COLOR_MAPS = 7;

    private static final String SPRITES_FOLDER = "sprites";
    private static final String INDEX_FILE = "index.json";
    private static final String HERO_SHEET = "heroes";
    private static final String REBUILD = " Rebuild the atlases: run " +
            "the \"Build Atlases\" configuration, or \"mvn test-compile " +
            "exec:java@atlases\".";

    private static final int PICTURE_ATLAS_SHIFT = 16;
    private static final int PICTURE_FRAME_MASK = 0xFFFF;

    private static final String[] SHEET_KEYS = { "id", "source", "region" };
    private static final String[] COLOR_MAP_KEYS = { "id", "swaps" };
    private static final String[] INDEX_KEYS = {
        "palette", "atlases", "sheets"
    };
    private static final String[] INDEX_SHEET_KEYS = {
        "id", "source", "region", "atlas", "at"
    };

    private static Sheet[] atlases = new Sheet[0];

    /* Per sheet: its id, which atlas it is in, the cell of that atlas
       where its top left cell sits, and its size in cells. */
    private static String[] sheetIds = new String[0];
    private static int[] sheetAtlas, sheetCol, sheetRow;
    private static int[] sheetCols, sheetRows;

    /* Index 0 of each is COLOR_MAP_NONE and holds null. */
    private static String[] colorMapIds = new String[1];
    private static byte[][] colorMaps = new byte[1][];

    private static int heroSheet;

    private Sprites() {}

    /**
     * Loads the atlases and color maps and gives them to the sprite
     * renderer, which must be initialized. Palette must be loaded.
     *
     * @param root the contents of data/sprites.json
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "sheets", "color_maps" });

        DataNode index = DataNode.LoadFrom(SPRITES_FOLDER, INDEX_FILE);
        index.Only(INDEX_KEYS);
        if (!index.Str("palette").equals(Palette.Signature())) {
            throw root.Fail("palette.json has changed since the atlases " +
                    "were built, so their colors would come out wrong." +
                    REBUILD);
        }

        String[] files = index.Strs("atlases");
        int i;
        atlases = new Sheet[files.length];
        for (i = 0; i < files.length; i++) {
            if ((atlases[i] = Sheet.Load(files[i])) == null)
                throw index.Fail("[" + files[i] + "] could not be loaded.");

            SpriteAtlas atlas = SpriteAtlas.Create(atlases[i].w,
                    atlases[i].h, Sheet.CELL, atlases[i].texels);
            if (atlas == null) {
                throw index.Fail("the sprite renderer rejected [" +
                        files[i] + "].");
            }
            SpriteRenderer.InstallAtlas(i, atlas);
        }

        LoadSheets(root.Groups("sheets", "sheet"),
                index.Groups("sheets", "sheet"));
        LoadColorMaps(root, root.OptGroups("color_maps", "color map"));
        heroSheet = SheetNamed(root, HERO_SHEET);
    }

    static void Shutdown() {
        atlases = new Sheet[0];
        sheetIds = new String[0];
        colorMapIds = new String[1];
        colorMaps = new byte[1][];
    }

    /**
     * @param from the group naming the picture, for error messages
     * @param cell a column and a row of the sheet
     * @return the picture at that cell of the sheet called sheetId
     */
    static int Picture(DataNode from, String sheetId, int[] cell) {
        int sheet = SheetNamed(from, sheetId);

        if (cell[0] < 0 || cell[1] < 0 || cell[0] >= sheetCols[sheet] ||
                cell[1] >= sheetRows[sheet]) {
            throw from.Fail("cell [" + cell[0] + ", " + cell[1] + "] is " +
                    "outside the sheet [" + sheetId + "], which has " +
                    "columns 0 to " + (sheetCols[sheet] - 1) +
                    " and rows 0 to " + (sheetRows[sheet] - 1) + ".");
        }
        return PictureAt(sheet, cell[0], cell[1]);
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
        Gfx.DrawFrame(atlases[AtlasOf(picture)], FrameOf(picture), x, y,
                colorMaps[colorMap]);
    }

    /** @return the atlas holding the player's character */
    static int HeroAtlas() { return sheetAtlas[heroSheet]; }

    /** @return the frame for column col, row row of the hero sheet */
    static int Hero(int col, int row) {
        return FrameOf(PictureAt(heroSheet, col, row));
    }

    private static int PictureAt(int sheet, int col, int row) {
        int atlas = sheetAtlas[sheet];
        return atlas << PICTURE_ATLAS_SHIFT | atlases[atlas].Frame(
                sheetCol[sheet] + col, sheetRow[sheet] + row);
    }

    private static int SheetNamed(DataNode from, String sheetId) {
        for (int i = 0; i < sheetIds.length; i++)
            if (sheetIds[i].equals(sheetId)) return i;
        throw from.Fail("there is no sheet called [" + sheetId +
                "] in sprites.json.");
    }

    /* Matches each sheet of sprites.json with its entry in the index,
       which must have been built from the same source and region. */
    private static void LoadSheets(DataNode[] nodes, DataNode[] built) {
        int n = nodes.length;
        String[] ids = new String[n];

        sheetAtlas = new int[n];
        sheetCol = new int[n];
        sheetRow = new int[n];
        sheetCols = new int[n];
        sheetRows = new int[n];

        for (int i = 0; i < n; i++) {
            String id = nodes[i].Str("id");
            DataNode node = nodes[i].Called("sprites.json, sheet [" + id +
                    "]");
            node.Only(SHEET_KEYS);
            for (int j = 0; j < i; j++) {
                if (ids[j].equals(id))
                    throw node.Fail("there are two sheets with this id.");
            }

            DataNode entry = null;
            for (int j = 0; entry == null && j < built.length; j++)
                if (built[j].Str("id").equals(id)) entry = built[j];
            if (entry == null) {
                throw node.Fail("it has been added since the atlases " +
                        "were built." + REBUILD);
            }
            entry.Only(INDEX_SHEET_KEYS);

            int[] region = entry.Ints("region", 4);
            int[] asked = node.OptInts("region", 4);
            boolean same = entry.Str("source").equals(node.Str("source"));
            for (int k = 0; same && asked != null && k < asked.length; k++)
                same = asked[k] == region[k];
            if (!same) {
                throw node.Fail("its source or region has changed since " +
                        "the atlases were built." + REBUILD);
            }

            int[] at = entry.Ints("at", 2);
            int atlas = entry.Int("atlas");
            if (atlas < 0 || atlas >= atlases.length)
                throw node.Fail("the index is damaged." + REBUILD);

            ids[i] = id;
            sheetAtlas[i] = atlas;
            sheetCol[i] = at[0];
            sheetRow[i] = at[1];
            sheetCols[i] = region[2] / Sheet.CELL;
            sheetRows[i] = region[3] / Sheet.CELL;
        }
        sheetIds = ids;
    }

    private static void LoadColorMaps(DataNode root, DataNode[] nodes) {
        if (nodes.length > MAX_COLOR_MAPS) {
            throw root.Fail("there are " + nodes.length + " color maps, " +
                    "but the sprite renderer can hold only " +
                    MAX_COLOR_MAPS + ".");
        }

        colorMapIds = new String[nodes.length + 1];
        colorMaps = new byte[nodes.length + 1][];

        for (int i = 0; i < nodes.length; i++) {
            String id = nodes[i].Str("id");
            DataNode node = nodes[i].Called("sprites.json, color map [" +
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
