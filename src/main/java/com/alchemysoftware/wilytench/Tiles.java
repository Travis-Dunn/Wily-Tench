package com.alchemysoftware.wilytench;

/**
 * What each character in a map file stands for, as listed in
 * data/tiles.json.
 *
 * A map names a tileset, and the tileset says what its walls and
 * floors look like. "Things" are whatever stands on a floor: scenery,
 * and locs. Things listed inside a tileset belong to it; things listed
 * at the bottom of the file can be used on any map. Two characters are
 * built in and cannot be redefined: a space is nothing at all, and "@"
 * is where the player arrives.
 *
 * Where an entry lists several cells, each tile picks one by its
 * position, so that the choice is varied but the same on every run.
 */
final class Tiles {
    /** A kind of wall, which joins up with neighbors of its own kind. */
    static final class Wall {
        String name, examine;
        String sheet;
        /* The top left cell of the wall's block of pieces. */
        int col, row;
        DataNode node;
    }

    /** A floor pattern. */
    static final class Floor {
        int[] pictures;
        /* Out of a hundred tiles, how many show no picture at all. */
        int barePercent;
    }

    /** Something standing on the floor. */
    static final class Thing {
        String name, examine;
        int[] pictures;
        /* The same thing emptied, cell for cell, or null. */
        int[] emptyPictures;
        int colorMap;
        boolean animated, walkable;
        Loc loc;
        Npc npc;
    }

    /** The meaning of every character for one tileset. */
    static final class Set {
        String id;
        final Wall[] walls = new Wall[SYMBOLS];
        final Floor[] floors = new Floor[SYMBOLS];
        final Thing[] things = new Thing[SYMBOLS];
        /* The floor things stand on: the first one listed. */
        Floor baseFloor;
    }

    static final char NOTHING = ' ', START = '@';

    private static final int SYMBOLS = 128;
    private static final int PERCENT = 100;

    private static final String[] SET_KEYS = {
        "id", "walls", "floors", "things"
    };
    private static final String[] WALL_KEYS = {
        "symbol", "name", "examine", "sheet", "block"
    };
    private static final String PLAIN = "Nothing remarkable.";
    private static final String[] FLOOR_KEYS = {
        "symbol", "sheet", "cells", "bare_percent"
    };
    private static final String[] THING_KEYS = {
        "symbol", "name", "examine", "loc", "npc", "sheet", "cells",
        "empty_cells",
        "color_map", "animated", "walkable"
    };

    private static Set[] sets = new Set[0];

    private Tiles() {}

    /**
     * @param root the contents of data/tiles.json; Sprites and Loc
     *             must already be loaded
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "tilesets", "things" });

        Thing[] shared = new Thing[SYMBOLS];
        ReadThings(root.OptGroups("things", "thing"), "tiles.json",
                shared, null);

        DataNode[] nodes = root.Groups("tilesets", "tileset");
        Set[] loaded = new Set[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            Set set = new Set();
            set.id = nodes[i].Str("id");
            String where = "tiles.json, tileset [" + set.id + "]";
            DataNode node = nodes[i].Called(where);
            node.Only(SET_KEYS);
            for (int j = 0; j < i; j++) {
                if (loaded[j].id.equals(set.id))
                    throw node.Fail("there are two tilesets with this id.");
            }

            ReadWalls(node.Groups("walls", "wall"), where, set);
            ReadFloors(node.Groups("floors", "floor"), where, set);
            if (set.baseFloor == null)
                throw node.Fail("it needs at least one floor.");

            System.arraycopy(shared, 0, set.things, 0, SYMBOLS);
            ReadThings(node.OptGroups("things", "thing"), where,
                    set.things, set);

            for (char c = 0; c < SYMBOLS; c++) {
                if (set.things[c] != null &&
                        (set.walls[c] != null || set.floors[c] != null)) {
                    throw node.Fail("the symbol [" + c + "] is both a " +
                            "thing and a wall or floor.");
                }
            }
            loaded[i] = set;
        }
        sets = loaded;
    }

    static void Shutdown() { sets = new Set[0]; }

    /** @return the tileset called id, or null if there is none */
    static Set Named(String id) {
        for (int i = 0; i < sets.length; i++)
            if (sets[i].id.equals(id)) return sets[i];
        return null;
    }

    /** @return the ids of every tileset, for error messages */
    static String Names() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < sets.length; i++)
            sb.append(i == 0 ? "" : ", ").append(sets[i].id);
        return sb.toString();
    }

    /**
     * @param mix a scramble of the tile's position; never negative
     * @return the picture a floor shows on that tile, or
     *         World.NO_PICTURE
     */
    static int FloorPicture(Floor floor, int mix) {
        if (mix % PERCENT < floor.barePercent) return World.NO_PICTURE;
        return floor.pictures[mix / PERCENT % floor.pictures.length];
    }

    /** @return which of a thing's pictures a tile shows */
    static int Variant(Thing thing, int mix) {
        return mix / PERCENT % thing.pictures.length;
    }

    private static void ReadWalls(DataNode[] nodes, String where, Set set) {
        for (int i = 0; i < nodes.length; i++) {
            char symbol = Symbol(nodes[i], where, "wall");
            DataNode node = nodes[i].Called(where + ", wall [" + symbol +
                    "]");
            node.Only(WALL_KEYS);
            if (set.walls[symbol] != null || set.floors[symbol] != null)
                throw node.Fail("the symbol is used twice.");

            int[] block = node.Ints("block", 2);
            Wall wall = new Wall();
            wall.name = node.Str("name");
            wall.examine = node.OptStr("examine", PLAIN);
            wall.sheet = node.Str("sheet");
            wall.col = block[0];
            wall.row = block[1];
            wall.node = node;

            /* Checks the whole block is on the sheet, here and now. */
            for (int piece = 0; piece < World.WALL_PIECES.length; piece++)
                World.WallPicture(wall, piece);
            set.walls[symbol] = wall;
        }
    }

    private static void ReadFloors(DataNode[] nodes, String where,
                                   Set set) {
        for (int i = 0; i < nodes.length; i++) {
            char symbol = Symbol(nodes[i], where, "floor");
            DataNode node = nodes[i].Called(where + ", floor [" + symbol +
                    "]");
            node.Only(FLOOR_KEYS);
            if (set.walls[symbol] != null || set.floors[symbol] != null)
                throw node.Fail("the symbol is used twice.");

            Floor floor = new Floor();
            floor.pictures = Pictures(node, "cells");
            floor.barePercent = node.OptInt("bare_percent", 0);
            if (floor.barePercent < 0 || floor.barePercent > PERCENT) {
                throw node.Fail("[bare_percent] must be from 0 to " +
                        PERCENT + ".");
            }
            set.floors[symbol] = floor;
            if (set.baseFloor == null) set.baseFloor = floor;
        }
    }

    /* A thing listed for a tileset may replace a shared one, but two
       in the same list may not share a symbol. */
    private static void ReadThings(DataNode[] nodes, String where,
                                   Thing[] into, Set set) {
        boolean[] seen = new boolean[SYMBOLS];

        for (int i = 0; i < nodes.length; i++) {
            char symbol = Symbol(nodes[i], where, "thing");
            DataNode node = nodes[i].Called(where + ", thing [" + symbol +
                    "]");
            node.Only(THING_KEYS);
            if (seen[symbol]) throw node.Fail("the symbol is used twice.");
            seen[symbol] = true;

            Thing thing = new Thing();
            if (node.Has("loc")) {
                String id = node.Str("loc");
                if ((thing.loc = Loc.Named(id)) == null) {
                    throw node.Fail("there is no loc called [" + id +
                            "] in locs.json.");
                }
            }
            if (node.Has("npc")) {
                String id = node.Str("npc");
                if (thing.loc != null)
                    throw node.Fail("it cannot be both a loc and an npc.");
                if ((thing.npc = Npc.Named(id)) == null) {
                    throw node.Fail("there is no npc called [" + id +
                            "] in npcs.json.");
                }
            }
            if (thing.loc == null && thing.npc == null &&
                    !node.Has("name"))
                throw node.Fail("it needs a [name], a [loc], or an [npc].");
            thing.name = node.OptStr("name",
                    thing.loc != null ? thing.loc.name :
                    thing.npc != null ? thing.npc.name : null);
            if (thing.loc != null && node.Has("examine")) {
                throw node.Fail("it has a [loc], so what examining it " +
                        "says is the loc's [examine], in locs.json.");
            }
            if (thing.npc != null && node.Has("examine")) {
                throw node.Fail("it has an [npc], so what examining it " +
                        "says is the npc's [examine], in npcs.json.");
            }
            thing.examine = node.OptStr("examine", PLAIN);

            thing.pictures = Pictures(node, "cells");
            if (node.Has("empty_cells")) {
                thing.emptyPictures = Pictures(node, "empty_cells");
                if (thing.emptyPictures.length != thing.pictures.length) {
                    throw node.Fail("[empty_cells] must list as many " +
                            "cells as [cells], one for each.");
                }
            }
            if (thing.loc != null && thing.loc.emptyName != null &&
                    thing.emptyPictures == null) {
                throw node.Fail("its loc can be emptied, so it needs " +
                        "[empty_cells] to show when it is.");
            }

            thing.colorMap = node.Has("color_map") ?
                    Sprites.ColorMapNamed(node, node.Str("color_map")) :
                    Sprites.COLOR_MAP_NONE;
            thing.animated = node.OptBool("animated", false);
            thing.walkable = node.OptBool("walkable", false);
            if (thing.animated) {
                /* The frames are the cells to the right of each one. */
                int[] last = node.IntLists("cells", 2)[0];
                Sprites.Picture(node, node.Str("sheet"), new int[] {
                    last[0] + World.ANIM_FRAMES - 1, last[1]
                });
                if (thing.pictures.length != 1)
                    throw node.Fail("an animated thing lists one cell.");
            }
            into[symbol] = thing;
        }
    }

    private static int[] Pictures(DataNode node, String key) {
        int[][] cells = node.IntLists(key, 2);
        int[] pictures = new int[cells.length];
        String sheet = node.Str("sheet");

        for (int i = 0; i < cells.length; i++)
            pictures[i] = Sprites.Picture(node, sheet, cells[i]);
        return pictures;
    }

    private static char Symbol(DataNode node, String where, String what) {
        String s = node.Str("symbol");
        char c = s.charAt(0);

        if (s.length() != 1 || c >= SYMBOLS || c < NOTHING) {
            throw node.Fail("[symbol] must be a single plain character, " +
                    "but is \"" + s + "\".");
        }
        if (c == NOTHING || c == START || c == '!') {
            throw node.Called(where + ", " + what + " [" + c + "]").Fail(
                    "a space, [@], and [!] have fixed meanings in map " +
                    "files and cannot be redefined.");
        }
        return c;
    }
}
