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
 *   !tileset cave          which art its walls and floors use:
 *                          "cave" or "dungeon"
 *
 * Everything after the settings is the map, one character per tile:
 *
 *   space  nothing; solid
 *   #  %   wall, in the tileset's first or second color; joins up
 *          with its neighbors
 *   .  ,   floor, in the tileset's first or second pattern
 *   @      where the player arrives
 *   L      the way out: a ladder in a cave, stairs in a dungeon
 *   r  R   rocks holding ore: copper or iron
 *   s  S   stalagmites, orange or grey
 *   c      crystal          m  mushrooms
 *   t      torch            b  barrel
 *   p      pot              w  cobweb, which can be walked through
 *   k      bookshelf        h  chest
 *
 * Everything from "L" down stands on the first floor pattern and is
 * solid unless noted. Rows may be different lengths; the map is as wide
 * as the longest.
 *
 * "L", "r", and "R" are locs: things the player can use, whose
 * behavior comes from a script. See Loc.
 *
 * The one thing about a map that changes during play is its ore rocks,
 * which are empty for a while after being mined. Each empty rock
 * remembers the game tick at which it refills, so a map the player has
 * left needs no attention until the player comes back.
 */
final class World {
    static final int NO_PICTURE = -1;

    /** How many frames an animated object cycles through. */
    static final int ANIM_FRAMES = 4;

    private static final String FOLDER = "maps";
    private static final char SETTING_MARK = '!';
    private static final String SETTING_NAME = "name";
    private static final String SETTING_TILESET = "tileset";

    private static final int TILESET_CAVE = 0;
    private static final int TILESET_DUNGEON = 1;
    private static final String[] TILESET_NAMES = { "cave", "dungeon" };
    private static final int[] TILESET_ATLASES = {
        Sprites.ATLAS_CAVE, Sprites.ATLAS_DUNGEON
    };
    private static final String[] WALL_NAMES = {
        "Cave wall", "Dungeon wall"
    };
    private static final Loc[] EXITS = { Loc.LADDER, Loc.STAIRS };

    /*
     * Wall pieces, as { column, row } on a tileset's sheet, indexed by
     * which neighbors are also wall: north 1, east 2, south 4, west 8.
     * Found by checking which edges of each piece the wall's top
     * surface reaches. Both tilesets lay their walls out identically.
     * The art has no piece for a wall with no neighbors, so slot 0
     * borrows the east-west one.
     */
    private static final int[][] WALL_PIECES = {
        { 3, 2 }, { 0, 2 }, { 2, 2 }, { 2, 1 }, { 1, 2 }, { 4, 1 },
        { 2, 0 }, { 0, 1 }, { 3, 1 }, { 4, 2 }, { 3, 0 }, { 1, 1 },
        { 4, 0 }, { 1, 0 }, { 0, 0 }, { 5, 0 }
    };
    /* The second wall color sits this many rows below the first. */
    private static final int SECOND_WALL_ROW_OFFSET = 3;
    /* And the grey rock this many rows below the orange. */
    private static final int GREY_ROCK_ROW_OFFSET = 3;

    private static final int CRYSTAL_KINDS = 5;
    private static final int MUSHROOM_KINDS = 3;
    private static final int STALAGMITE_KINDS = 5;
    private static final int BOOKSHELF_KINDS = 5;

    private static final int FULL = 0;

    /** The map's file name, which is also how saved games refer to it. */
    final String id;
    /** What the place is called. */
    final String name;

    final int w, h;
    final int startX, startY;

    private final int[] floor, object;
    private final boolean[] solid, animated;
    private final String[] names;

    /* Per tile: the loc there, or null, and for one that has been
       emptied the game tick at which it is full again, or FULL. */
    private final Loc[] locs;
    private final int[] emptyUntil;
    private int emptyRocks;

    /* Added to an orange rock's picture to get the same rock in grey. */
    private final int greyRockOffset;

    private int now, version;

    private World(String id, String name, int w, int h, int startX,
                  int startY, int[] floor, int[] object, boolean[] solid,
                  boolean[] animated, String[] names, Loc[] locs) {
        this.locs = locs;
        this.id = id;
        this.name = name;
        this.w = w;
        this.h = h;
        this.startX = startX;
        this.startY = startY;
        this.floor = floor;
        this.object = object;
        this.solid = solid;
        this.animated = animated;
        this.names = names;
        emptyUntil = new int[w * h];
        greyRockOffset = Cave(0, GREY_ROCK_ROW_OFFSET) - Cave(0, 0);
    }

    /**
     * @param filename a file in the "maps" resource folder
     * @return the map, or null after a fatal error has been reported
     */
    static World Load(String filename) {
        String[] lines = ReadLines(filename);
        if (lines == null) return null;

        String name = filename;
        int tileset = TILESET_CAVE, first = 0;

        for (; first < lines.length && lines[first].length() > 0 &&
                lines[first].charAt(0) == SETTING_MARK; first++) {
            String setting = lines[first].substring(1).trim();
            int space = setting.indexOf(' ');
            String key = space < 0 ? setting : setting.substring(0, space);
            String value = space < 0 ? "" : setting.substring(space).trim();

            if (key.equals(SETTING_NAME) && value.length() > 0) {
                name = value;
            } else if (key.equals(SETTING_TILESET) &&
                    (tileset = TilesetNamed(value)) >= 0) {
                continue;
            } else {
                ErrorHandler.LogFatalAndStop(
                        ErrStrSetting(filename, lines[first], first));
                return null;
            }
        }

        String[] rows = new String[lines.length - first];
        System.arraycopy(lines, first, rows, 0, rows.length);

        int w = 0, h = rows.length, x, y;
        for (y = 0; y < h; y++) w = Math.max(w, rows[y].length());
        if (w == 0) {
            ErrorHandler.LogFatalAndStop(ErrStrEmpty(filename));
            return null;
        }

        int atlas = TILESET_ATLASES[tileset];
        int[] floor = new int[w * h], object = new int[w * h];
        boolean[] solid = new boolean[w * h], animated = new boolean[w * h];
        String[] names = new String[w * h];
        Loc[] locs = new Loc[w * h];
        int startX = -1, startY = -1;

        for (y = 0; y < h; y++) {
            for (x = 0; x < w; x++) {
                int i = y * w + x, mix = Mix(x, y);
                char c = At(rows, x, y);

                floor[i] = object[i] = NO_PICTURE;
                solid[i] = true;

                switch (c) {
                case ' ':
                    continue;
                case '#':
                case '%':
                    object[i] = WallPicture(rows, x, y, c, atlas);
                    names[i] = WALL_NAMES[tileset];
                    continue;
                case ',':
                    floor[i] = SecondFloor(tileset, mix);
                    solid[i] = false;
                    continue;
                default:
                    break;
                }

                floor[i] = FirstFloor(tileset, mix);

                switch (c) {
                case '.':
                    solid[i] = false;
                    break;
                case '@':
                    solid[i] = false;
                    startX = x;
                    startY = y;
                    break;
                case 'L':
                    locs[i] = EXITS[tileset];
                    names[i] = locs[i].name;
                    object[i] = tileset == TILESET_CAVE ? Cave(0, 11) :
                            Sprites.Picture(Sprites.ATLAS_DUNGEON, 0, 7);
                    break;
                case 'w':
                    names[i] = "Cobweb";
                    solid[i] = false;
                    object[i] = Cave(0, 18);
                    break;
                case 'r':
                case 'R':
                    locs[i] = c == 'r' ? Loc.COPPER_ROCKS : Loc.IRON_ROCKS;
                    names[i] = locs[i].name;
                    object[i] = Cave(5 + (mix >> 4 & 1), 1);
                    break;
                case 's':
                    names[i] = "Stalagmites";
                    object[i] = Cave((mix >>> 4) % STALAGMITE_KINDS, 6);
                    break;
                case 'S':
                    names[i] = "Stalagmites";
                    object[i] = Cave((mix >>> 4) % STALAGMITE_KINDS, 8);
                    break;
                case 'c':
                    names[i] = "Crystal";
                    object[i] = Cave((mix >>> 4) % CRYSTAL_KINDS, 16);
                    break;
                case 'm':
                    names[i] = "Mushrooms";
                    object[i] = Cave((mix >>> 4) % MUSHROOM_KINDS, 17);
                    break;
                case 't':
                    names[i] = "Torch";
                    object[i] = Cave(0, 14);
                    animated[i] = true;
                    break;
                case 'b':
                    names[i] = "Barrel";
                    object[i] = Cave(1, 13);
                    break;
                case 'p':
                    names[i] = "Pot";
                    object[i] = Cave(0, 13);
                    break;
                case 'k':
                    names[i] = "Bookshelf";
                    object[i] = Sprites.Picture(Sprites.ATLAS_DUNGEON,
                            1 + (mix >>> 4) % BOOKSHELF_KINDS, 8);
                    break;
                case 'h':
                    names[i] = "Chest";
                    object[i] = Sprites.Picture(Sprites.ATLAS_DUNGEON, 0, 9);
                    break;
                default:
                    ErrorHandler.LogFatalAndStop(
                            ErrStrSymbol(filename, c, x, first + y));
                    return null;
                }
            }
        }

        if (startX < 0) {
            ErrorHandler.LogFatalAndStop(ErrStrNoStart(filename));
            return null;
        }
        return new World(filename, name, w, h, startX, startY, floor,
                object, solid, animated, names, locs);
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
        if (emptyUntil[i] > now) return object[i] + greyRockOffset;
        return animated[i] ? object[i] + phase : object[i];
    }

    /** @return the color map to draw what stands on the tile with */
    int ObjectColorMap(int x, int y) {
        int i = y * w + x;
        return locs[i] == null || emptyUntil[i] > now ?
                Sprites.COLOR_MAP_NONE : locs[i].colorMap;
    }

    private static int Cave(int col, int row) {
        return Sprites.Picture(Sprites.ATLAS_CAVE, col, row);
    }

    /* Cave floor is bare in about half the tiles and speckled in the
       rest; dungeon floor is one dark checkered pattern throughout. */
    private static int FirstFloor(int tileset, int mix) {
        if (tileset == TILESET_DUNGEON)
            return Sprites.Picture(Sprites.ATLAS_DUNGEON, 11, 1);
        return (mix & 2) == 0 ? NO_PICTURE :
                Cave(8 + (mix >> 2 & 1), mix >> 3 & 1);
    }

    private static int SecondFloor(int tileset, int mix) {
        if (tileset == TILESET_DUNGEON)
            return Sprites.Picture(Sprites.ATLAS_DUNGEON, 9, 2);
        return Cave(8 + (mix & 1), 3);
    }

    private static int WallPicture(String[] rows, int x, int y, char kind,
                                   int atlas) {
        int[] piece = WALL_PIECES[
                (At(rows, x, y - 1) == kind ? 1 : 0) |
                (At(rows, x + 1, y) == kind ? 2 : 0) |
                (At(rows, x, y + 1) == kind ? 4 : 0) |
                (At(rows, x - 1, y) == kind ? 8 : 0)];
        return Sprites.Picture(atlas, piece[0],
                piece[1] + (kind == '%' ? SECOND_WALL_ROW_OFFSET : 0));
    }

    private static int TilesetNamed(String name) {
        for (int i = 0; i < TILESET_NAMES.length; i++)
            if (TILESET_NAMES[i].equals(name)) return i;
        return -1;
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
                "by [cave] or [dungeon].\n";
    }
    private static String ErrStrSymbol(String filename, char c, int x,
                                       int lineIndex) {
        return CLASS + " failed to load the map [" + filename +
                "] because the symbol [" + c + "] at column [" + (x + 1) +
                "] of line [" + (lineIndex + 1) + "] is not one it knows.\n";
    }
}
