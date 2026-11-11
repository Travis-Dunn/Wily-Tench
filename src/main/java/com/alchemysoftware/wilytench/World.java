package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.error.ErrorHandler;

/**
 * One map, or "worldspace": for every tile, what is drawn there and
 * whether it can be walked on. The game's world is several of these,
 * joined by ladders and stairs.
 *
 * Maps are plain text files in the "maps" resource folder; MapFile
 * describes the format and reads it. This class gives the file's
 * characters their meaning: a space is nothing at all, and solid; "@"
 * is where the player arrives; a character the map file defines means
 * what it says there; every other character means what the map's
 * tileset in data/tiles.json says.
 *
 * Something standing on the floor, a rock or a person or the player's
 * start, does not say what floor is under it. That is worked out from
 * the tiles beside it, unless the map file says otherwise.
 *
 * A map may name spots on itself, "points", for scripts to send the
 * player to: PointNamed().
 *
 * The one thing about a map that changes during play is its locs that
 * can be emptied, the ore rocks, which stay empty for a while after
 * being mined. Each remembers the game tick at which it refills, so a
 * map the player has left needs no attention until the player comes
 * back.
 */
final class World {
    static final int NO_PICTURE = -1;

    /** How many frames an animated object cycles through. */
    static final int ANIM_FRAMES = 4;

    private static final int SYMBOLS = 128;
    /* The four tiles beside a tile, as steps in x and y. */
    private static final int[][] BESIDE = {
        { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 }
    };

    /*
     * Where each piece of a wall sits within the wall's block on its
     * sheet, as { column, row } from the block's top left, indexed by
     * which neighbors are also that wall: north 1, east 2, south 4,
     * west 8. Found by checking which edges of each piece the wall's
     * top surface reaches; every wall in the Demonic Dungeon art is
     * laid out this way. The art has no piece for a wall with no
     * neighbors, so slot 0 borrows the east-west one.
     */
    static final int[][] WALL_PIECES = {
        { 3, 2 }, { 0, 2 }, { 2, 2 }, { 2, 1 }, { 1, 2 }, { 4, 1 },
        { 2, 0 }, { 0, 1 }, { 3, 1 }, { 4, 2 }, { 3, 0 }, { 1, 1 },
        { 4, 0 }, { 1, 0 }, { 0, 0 }, { 5, 0 }
    };

    private static final int FULL = 0;

    /** The map's file name, which is also how saved games refer to it. */
    final String id;
    /** What the place is called. */
    final String name;

    final int w, h;
    final int startX, startY;

    private final int[] floor, object, emptyObject;
    private final byte[] colorMap;
    private final boolean[] solid, animated;
    private final String[] names, examines;

    /* Per tile: the loc there, or null, and for one that has been
       emptied the game tick at which it is full again, or FULL. */
    private final Loc[] locs;
    private final Npc[] npcs;

    /* The named spots, set by Build() once the map is made. */
    private String[] pointNames = new String[0];
    private int[] pointX = new int[0], pointY = new int[0];
    private final int[] emptyUntil;
    private int emptyRocks;

    private int now, version;

    private World(String id, String name, int w, int h, int startX,
                  int startY, int[] floor, int[] object, int[] emptyObject,
                  byte[] colorMap, boolean[] solid, boolean[] animated,
                  String[] names, String[] examines, Loc[] locs,
                  Npc[] npcs) {
        this.examines = examines;
        this.id = id;
        this.name = name;
        this.w = w;
        this.h = h;
        this.startX = startX;
        this.startY = startY;
        this.floor = floor;
        this.object = object;
        this.emptyObject = emptyObject;
        this.colorMap = colorMap;
        this.solid = solid;
        this.animated = animated;
        this.names = names;
        this.locs = locs;
        this.npcs = npcs;
        emptyUntil = new int[w * h];
    }

    /**
     * @param filename a file in the "maps" resource folder
     * @return the map, or null after a fatal error has been reported
     */
    static World Load(String filename) {
        try {
            return Build(filename,
                    MapFile.Parse(MapFile.Read(filename), filename));
        } catch (DataError e) {
            ErrorHandler.LogFatalAndStop(CLASS + " failed to load a map " +
                    "because of a mistake in it. " + e.getMessage() + "\n");
            return null;
        }
    }

    /**
     * Makes a map of a map file that has been read.
     *
     * @param filename the file's name, which becomes the map's id
     * @throws DataError describing the first mistake found
     */
    static World Build(String filename, MapFile file) {
        Tiles.Set set = Tiles.Named(file.tileset);
        if (set == null) {
            throw new DataError(filename + ": there is no tileset called [" +
                    file.tileset + "] in tiles.json. The tilesets are: " +
                    Tiles.Names() + ".");
        }

        /* What the map's own symbols mean. */
        Tiles.Thing[] local = new Tiles.Thing[SYMBOLS];
        String[] pointOf = new String[SYMBOLS];
        Tiles.Floor[] under = new Tiles.Floor[SYMBOLS];
        int[] seen = new int[SYMBOLS];

        for (int i = 0; i < file.defines.length; i++)
            Define(filename, file.defines[i], set, local, pointOf, under);

        String[] rows = file.rows;
        int w = 0, h = rows.length, x, y;
        for (y = 0; y < h; y++) w = Math.max(w, rows[y].length());
        if (w == 0) throw new DataError(filename + ": the map has no tiles.");

        int[] floor = new int[w * h], object = new int[w * h];
        int[] emptyObject = new int[w * h];
        byte[] colorMap = new byte[w * h];
        boolean[] solid = new boolean[w * h], animated = new boolean[w * h];
        String[] names = new String[w * h], examines = new String[w * h];
        Loc[] locs = new Loc[w * h];
        Npc[] npcs = new Npc[w * h];
        String[] pointNames = new String[file.defines.length];
        int[] pointX = new int[file.defines.length];
        int[] pointY = new int[file.defines.length];
        int points = 0, startX = -1, startY = -1;

        for (y = 0; y < h; y++) {
            for (x = 0; x < w; x++) {
                int i = y * w + x, mix = Mix(x, y);
                char c = At(rows, x, y);
                if (c >= SYMBOLS) throw Unknown(filename, c, x, y, set);

                Tiles.Wall wall = set.walls[c];
                Tiles.Floor ground = set.floors[c];
                Tiles.Thing thing = local[c] != null ? local[c] :
                        set.things[c];

                floor[i] = object[i] = emptyObject[i] = NO_PICTURE;
                solid[i] = true;
                seen[c]++;

                if (c == Tiles.NOTHING) {
                    continue;
                } else if (wall != null) {
                    object[i] = WallPicture(wall,
                            (At(rows, x, y - 1) == c ? 1 : 0) |
                            (At(rows, x + 1, y) == c ? 2 : 0) |
                            (At(rows, x, y + 1) == c ? 4 : 0) |
                            (At(rows, x - 1, y) == c ? 8 : 0));
                    names[i] = wall.name;
                    examines[i] = wall.examine;
                } else if (ground != null) {
                    floor[i] = Tiles.FloorPicture(ground, mix);
                    solid[i] = false;
                } else if (c == Tiles.START || pointOf[c] != null) {
                    floor[i] = Tiles.FloorPicture(
                            Under(rows, x, y, set, under[c]), mix);
                    solid[i] = false;
                    if (c == Tiles.START) {
                        startX = x;
                        startY = y;
                    } else if (seen[c] > 1) {
                        throw new DataError(filename + ": the point [" +
                                pointOf[c] + "], the symbol [" + c +
                                "], is on the map more than once. A " +
                                "point is one place.");
                    } else {
                        pointNames[points] = pointOf[c];
                        pointX[points] = x;
                        pointY[points++] = y;
                    }
                } else if (thing != null) {
                    int variant = Tiles.Variant(thing, mix);

                    floor[i] = Tiles.FloorPicture(
                            Under(rows, x, y, set, under[c]), mix);
                    object[i] = thing.pictures[variant];
                    if (thing.emptyPictures != null)
                        emptyObject[i] = thing.emptyPictures[variant];
                    colorMap[i] = (byte) thing.colorMap;
                    solid[i] = !thing.walkable;
                    animated[i] = thing.animated;
                    names[i] = thing.name;
                    examines[i] = thing.loc != null ? thing.loc.examine :
                            thing.npc != null ? thing.npc.examine :
                            thing.examine;
                    locs[i] = thing.loc;
                    npcs[i] = thing.npc;
                } else {
                    throw Unknown(filename, c, x, y, set);
                }
            }
        }

        if (seen[Tiles.START] != 1) {
            throw new DataError(filename + ": a map needs exactly one " +
                    "player start, the symbol [@], and this has " +
                    seen[Tiles.START] + ".");
        }
        for (int i = 0; i < file.defines.length; i++) {
            MapFile.Define d = file.defines[i];
            if (seen[d.symbol] == 0) {
                throw new DataError(filename + ": the symbol [" + d.symbol +
                        "] is defined but is nowhere on the map.");
            }
        }

        World world = new World(filename, file.name, w, h, startX, startY,
                floor, object, emptyObject, colorMap, solid, animated,
                names, examines, locs, npcs);
        world.pointNames = new String[points];
        world.pointX = new int[points];
        world.pointY = new int[points];
        System.arraycopy(pointNames, 0, world.pointNames, 0, points);
        System.arraycopy(pointX, 0, world.pointX, 0, points);
        System.arraycopy(pointY, 0, world.pointY, 0, points);
        return world;
    }

    /**
     * @param neighbors which neighbors are the same wall: north 1,
     *                  east 2, south 4, west 8, added together
     * @return the picture of the piece of a wall that joins them
     */
    static int WallPicture(Tiles.Wall wall, int neighbors) {
        int[] piece = WALL_PIECES[neighbors];
        return Sprites.Picture(wall.node, wall.sheet, new int[] {
            wall.col + piece[0], wall.row + piece[1]
        });
    }

    /** @return whether the tile is inside the map */
    boolean Contains(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    /** @return true if nothing can stand there; true outside the map */
    boolean Solid(int x, int y) {
        return !Contains(x, y) || solid[y * w + x];
    }

    /** @return what stands on the tile, or null if nothing does */
    String Name(int x, int y) {
        if (!Contains(x, y)) return null;
        int i = y * w + x;
        return emptyUntil[i] > now ? locs[i].emptyName : names[i];
    }

    /**
     * @return what looking closely at what stands on the tile tells
     *         the player, or null if nothing stands there
     */
    String Examine(int x, int y) {
        if (!Contains(x, y)) return null;
        int i = y * w + x;
        return emptyUntil[i] > now ? locs[i].emptyExamine : examines[i];
    }

    /** @return how many named spots the map has */
    int Points() { return pointNames.length; }

    /** @return which of the map's points has a name, or -1 */
    int PointNamed(String name) {
        for (int i = 0; i < pointNames.length; i++)
            if (pointNames[i].equals(name)) return i;
        return -1;
    }

    String PointName(int point) { return pointNames[point]; }

    int PointX(int point) { return pointX[point]; }

    int PointY(int point) { return pointY[point]; }

    /** @return the person on the tile, or null if there is none */
    Npc NpcAt(int x, int y) {
        return Contains(x, y) ? npcs[y * w + x] : null;
    }

    /** @return the loc on the tile, or null if there is none */
    Loc LocAt(int x, int y) {
        return Contains(x, y) ? locs[y * w + x] : null;
    }

    /** @return whether a rock there is mined out for the moment */
    boolean RockEmpty(int x, int y) {
        return emptyUntil[y * w + x] > now;
    }

    /**
     * @return the game tick at which the rock on a tile has ore again,
     *         if it is mined out; any earlier tick otherwise
     */
    int RockEmptyUntil(int x, int y) {
        return emptyUntil[y * w + x];
    }

    /**
     * Empties the rock on a tile until a game tick. Does nothing if
     * what is there cannot be emptied or the tick has already come,
     * which makes it safe to feed from a saved game.
     */
    void EmptyRock(int x, int y, int untilTick) {
        Loc loc = LocAt(x, y);
        if (loc == null || loc.emptyName == null || untilTick <= now)
            return;

        int i = y * w + x;
        if (emptyUntil[i] == FULL) emptyRocks++;
        emptyUntil[i] = untilTick;
        version++;
    }

    /** @return how many rocks are mined out at the moment */
    int EmptyRocks() { return emptyRocks; }

    /**
     * Brings the map up to a game tick, refilling the rocks that are
     * due. Call every tick while the player is here, and once on
     * arrival to catch up on the ticks spent elsewhere.
     */
    void Tick(int tick) {
        now = tick;
        if (emptyRocks == 0) return;

        for (int i = 0; i < emptyUntil.length; i++) {
            if (emptyUntil[i] == FULL || emptyUntil[i] > now) continue;
            emptyUntil[i] = FULL;
            emptyRocks--;
            version++;
        }
    }

    /** @return the game tick the map was last brought up to */
    int Now() { return now; }

    /**
     * @return a number that changes whenever anything about the map
     *         has changed
     */
    int Version() { return version; }

    /** @return the picture of the ground, or NO_PICTURE */
    int FloorPicture(int x, int y) {
        return floor[y * w + x];
    }

    /**
     * @param phase the animation step, 0 to ANIM_FRAMES - 1
     * @return the picture of what stands there, or NO_PICTURE
     */
    int ObjectPicture(int x, int y, int phase) {
        int i = y * w + x;
        if (emptyUntil[i] > now) return emptyObject[i];
        return animated[i] ? object[i] + phase : object[i];
    }

    /** @return the color map to draw what stands on the tile with */
    int ObjectColorMap(int x, int y) {
        int i = y * w + x;
        return emptyUntil[i] > now ? Sprites.COLOR_MAP_NONE : colorMap[i];
    }

    private static char At(String[] rows, int x, int y) {
        if (y < 0 || y >= rows.length) return ' ';
        return x >= 0 && x < rows[y].length() ? rows[y].charAt(x) : ' ';
    }

    /* Scrambles a position into bits that pick between look-alike
       tiles, so the choice is varied but the same on every run. */
    private static int Mix(int x, int y) {
        int n = x * 73856093 ^ y * 19349663;
        n ^= n >>> 13;
        n *= 0x5bd1e995;
        return (n ^ n >>> 15) & 0x7FFFFFFF;
    }

    /*
     * Gives one of a map's own symbols its meaning, filling in the
     * tables Build() reads: the thing it stands for, or the point it
     * names, and the floor it was told to stand on, if it was.
     */
    private static void Define(String filename, MapFile.Define d,
                               Tiles.Set set, Tiles.Thing[] local,
                               String[] pointOf, Tiles.Floor[] under) {
        String where = filename + ", the define of [" + d.symbol + "]: ";

        if (set.walls[d.symbol] != null || set.floors[d.symbol] != null ||
                set.things[d.symbol] != null) {
            throw new DataError(where + "the tileset [" + set.id +
                    "] already uses that symbol. Pick one that " +
                    "tiles.json does not.");
        }
        if (d.on != MapFile.NO_FLOOR) {
            if (d.on >= SYMBOLS || (under[d.symbol] = set.floors[d.on])
                    == null) {
                throw new DataError(where + "[on " + d.on + "] names no " +
                        "floor of the tileset [" + set.id + "].");
            }
        }

        if (d.kind.equals(MapFile.KIND_NPC)) {
            Npc npc = Npc.Named(d.value);
            if (npc == null) {
                throw new DataError(where + "there is no npc called [" +
                        d.value + "] in npcs.json.");
            }
            Tiles.Thing thing = new Tiles.Thing();
            thing.name = npc.name;
            thing.pictures = new int[] { npc.picture };
            thing.colorMap = Sprites.COLOR_MAP_NONE;
            thing.npc = npc;
            local[d.symbol] = thing;
        } else if (d.kind.equals(MapFile.KIND_LIKE)) {
            char of = d.value.charAt(0);
            if (of >= SYMBOLS || set.things[of] == null) {
                throw new DataError(where + "[like " + of + "] copies a " +
                        "thing, and [" + of + "] is not a thing in the " +
                        "tileset [" + set.id + "].");
            }
            local[d.symbol] = set.things[of];
        } else {
            for (int c = 0; c < SYMBOLS; c++) {
                if (d.value.equals(pointOf[c])) {
                    throw new DataError(where + "there are two points " +
                            "called [" + d.value + "].");
                }
            }
            pointOf[d.symbol] = d.value;
        }
    }

    /*
     * The floor under something that stands on the floor. If the map
     * said, that. Otherwise whichever floor most of the four tiles
     * beside it are, the tileset's earlier floor winning a tie; and
     * the tileset's first floor if none of them is floor at all.
     */
    private static Tiles.Floor Under(String[] rows, int x, int y,
                                     Tiles.Set set, Tiles.Floor told) {
        if (told != null) return told;

        Tiles.Floor best = null;
        int bestVotes = 0;

        for (int a = 0; a < BESIDE.length; a++) {
            char ca = At(rows, x + BESIDE[a][0], y + BESIDE[a][1]);
            Tiles.Floor candidate = ca < SYMBOLS ? set.floors[ca] : null;
            int votes = 0;
            if (candidate == null) continue;

            for (int b = 0; b < BESIDE.length; b++) {
                char cb = At(rows, x + BESIDE[b][0], y + BESIDE[b][1]);
                if (cb < SYMBOLS && set.floors[cb] == candidate) votes++;
            }
            if (votes > bestVotes || (votes == bestVotes &&
                    candidate.order < best.order)) {
                best = candidate;
                bestVotes = votes;
            }
        }
        return best != null ? best : set.baseFloor;
    }

    private static DataError Unknown(String filename, char c, int x, int y,
                                     Tiles.Set set) {
        return new DataError(filename + ": the symbol [" + c + "] at " +
                "column " + (x + 1) + " of row " + (y + 1) + " of the map " +
                "means nothing. It is not in the tileset [" + set.id +
                "] in tiles.json, and the map does not define it.");
    }

    private static final String CLASS = "World";
}
