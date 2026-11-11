package com.alchemysoftware.wilytench;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;

/**
 * One map, or "worldspace": for every tile, what is drawn there and
 * whether it can be walked on. The game's world is several of these,
 * joined by ladders and stairs.
 *
 * Maps are plain text files in the "maps" resource folder, so they can
 * be edited in any text editor. A file starts with settings, one per
 * line, each beginning with an exclamation mark:
 *
 *   !name Copper Cave      what the place is called
 *   !tileset cave          which tileset of data/tiles.json it uses
 *
 * Everything after the settings is the map, one character per tile.
 * A space is nothing at all, and solid; "@" is where the player
 * arrives; every other character means what the tileset says it
 * means. Rows may be different lengths; the map is as wide as the
 * longest.
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

    private static final String FOLDER = "maps";
    private static final char SETTING_MARK = '!';
    private static final String SETTING_NAME = "name";
    private static final String SETTING_TILESET = "tileset";

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
    private final int[] emptyUntil;
    private int emptyRocks;

    private int now, version;

    private World(String id, String name, int w, int h, int startX,
                  int startY, int[] floor, int[] object, int[] emptyObject,
                  byte[] colorMap, boolean[] solid, boolean[] animated,
                  String[] names, String[] examines, Loc[] locs) {
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
        emptyUntil = new int[w * h];
    }

    /**
     * @param filename a file in the "maps" resource folder
     * @return the map, or null after a fatal error has been reported
     */
    static World Load(String filename) {
        String[] lines = ReadLines(filename);
        if (lines == null) return null;

        String name = filename;
        Tiles.Set set = null;
        int first = 0;

        for (; first < lines.length && lines[first].length() > 0 &&
                lines[first].charAt(0) == SETTING_MARK; first++) {
            String setting = lines[first].substring(1).trim();
            int space = setting.indexOf(' ');
            String key = space < 0 ? setting : setting.substring(0, space);
            String value = space < 0 ? "" : setting.substring(space).trim();

            if (key.equals(SETTING_NAME) && value.length() > 0) {
                name = value;
            } else if (key.equals(SETTING_TILESET) &&
                    (set = Tiles.Named(value)) != null) {
                continue;
            } else {
                ErrorHandler.LogFatalAndStop(
                        ErrStrSetting(filename, lines[first], first));
                return null;
            }
        }
        if (set == null) {
            ErrorHandler.LogFatalAndStop(ErrStrNoTileset(filename));
            return null;
        }

        String[] rows = new String[lines.length - first];
        System.arraycopy(lines, first, rows, 0, rows.length);

        int w = 0, h = rows.length, x, y;
        for (y = 0; y < h; y++) w = Math.max(w, rows[y].length());
        if (w == 0) {
            ErrorHandler.LogFatalAndStop(ErrStrEmpty(filename));
            return null;
        }

        int[] floor = new int[w * h], object = new int[w * h];
        int[] emptyObject = new int[w * h];
        byte[] colorMap = new byte[w * h];
        boolean[] solid = new boolean[w * h], animated = new boolean[w * h];
        String[] names = new String[w * h], examines = new String[w * h];
        Loc[] locs = new Loc[w * h];
        int startX = -1, startY = -1;

        for (y = 0; y < h; y++) {
            for (x = 0; x < w; x++) {
                int i = y * w + x, mix = Mix(x, y);
                char c = At(rows, x, y);
                Tiles.Wall wall = c < set.walls.length ? set.walls[c] : null;
                Tiles.Floor ground =
                        c < set.floors.length ? set.floors[c] : null;
                Tiles.Thing thing =
                        c < set.things.length ? set.things[c] : null;

                floor[i] = object[i] = emptyObject[i] = NO_PICTURE;
                solid[i] = true;

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
                } else if (c == Tiles.START) {
                    floor[i] = Tiles.FloorPicture(set.baseFloor, mix);
                    solid[i] = false;
                    startX = x;
                    startY = y;
                } else if (thing != null) {
                    int variant = Tiles.Variant(thing, mix);

                    floor[i] = Tiles.FloorPicture(set.baseFloor, mix);
                    object[i] = thing.pictures[variant];
                    if (thing.emptyPictures != null)
                        emptyObject[i] = thing.emptyPictures[variant];
                    colorMap[i] = (byte) thing.colorMap;
                    solid[i] = !thing.walkable;
                    animated[i] = thing.animated;
                    names[i] = thing.name;
                    examines[i] = thing.loc == null ? thing.examine :
                            thing.loc.examine;
                    locs[i] = thing.loc;
                } else {
                    ErrorHandler.LogFatalAndStop(
                            ErrStrSymbol(filename, c, x, first + y, set.id));
                    return null;
                }
            }
        }

        if (startX < 0) {
            ErrorHandler.LogFatalAndStop(ErrStrNoStart(filename));
            return null;
        }
        return new World(filename, name, w, h, startX, startY, floor,
                object, emptyObject, colorMap, solid, animated, names,
                examines, locs);
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

    private static String[] ReadLines(String filename) {
        InputStream in = ResourceStreamResolver.Get(FOLDER, filename);
        if (in == null) return null;

        String text;
        try {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) bytes.write(chunk, 0, n);
                text = bytes.toString("US-ASCII");
            } finally {
                in.close();
            }
        } catch (IOException e) {
            ErrorHandler.LogFatalExcpAndStop(ErrStrRead(filename), e);
            return null;
        }

        String[] lines = text.replace("\r", "").split("\n", -1);
        int n = lines.length;
        while (n > 0 && lines[n - 1].trim().length() == 0) n--;

        String[] trimmed = new String[n];
        System.arraycopy(lines, 0, trimmed, 0, n);
        return trimmed;
    }

    private static final String CLASS = "World";
    private static String ErrStrRead(String filename) {
        return CLASS + " failed to load the map [" + filename +
                "] because an exception was encountered.\n";
    }
    private static String ErrStrEmpty(String filename) {
        return CLASS + " failed to load the map [" + filename +
                "] because it has no tiles.\n";
    }
    private static String ErrStrNoStart(String filename) {
        return CLASS + " failed to load the map [" + filename +
                "] because it has no player start, the symbol [@].\n";
    }
    private static String ErrStrSetting(String filename, String line,
                                        int lineIndex) {
        return CLASS + " failed to load the map [" + filename +
                "] because the setting [" + line + "] on line [" +
                (lineIndex + 1) + "] is not one it knows; settings are " +
                "[!name] followed by some text and [!tileset] followed " +
                "by one of the tilesets in tiles.json, which are [" +
                Tiles.Names() + "].\n";
    }
    private static String ErrStrNoTileset(String filename) {
        return CLASS + " failed to load the map [" + filename +
                "] because it has no [!tileset] line saying which of " +
                "the tilesets in tiles.json it uses; they are [" +
                Tiles.Names() + "].\n";
    }
    private static String ErrStrSymbol(String filename, char c, int x,
                                       int lineIndex, String tileset) {
        return CLASS + " failed to load the map [" + filename +
                "] because the symbol [" + c + "] at column [" + (x + 1) +
                "] of line [" + (lineIndex + 1) + "] means nothing in " +
                "the tileset [" + tileset + "]; see tiles.json.\n";
    }
}
