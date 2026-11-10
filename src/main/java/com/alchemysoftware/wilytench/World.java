package com.alchemysoftware.wilytench;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;

/**
 * One map: for every tile, what is drawn there and whether it can be
 * walked on.
 *
 * Maps are plain text files in the "maps" resource folder, one
 * character per tile, so they can be edited in any text editor:
 *
 *   space  nothing; solid
 *   #  %   cave wall, orange or grey; joins up with its neighbors
 *   .      cave floor
 *   ,      paved floor
 *   @      where the player starts
 *   r  R   rocks holding ore: copper or iron
 *   s  S   stalagmites, orange or grey
 *   c      crystal          m  mushrooms
 *   t      torch            b  barrel
 *   p      pot              w  cobweb, which can be walked through
 *
 * Everything from "r" down stands on cave floor and is solid unless
 * noted. Rows may be different lengths; the map is as wide as the
 * longest.
 *
 * The one thing about a map that changes during play is its ore rocks,
 * which are empty for a while after being mined.
 */
final class World {
    static final int NO_FRAME = -1;

    /** How many frames an animated object cycles through. */
    static final int ANIM_FRAMES = 4;

    private static final String FOLDER = "maps";

    /*
     * Wall pieces, as { column, row } on the orange part of the cave
     * sheet, indexed by which neighbors are also wall: north 1, east 2,
     * south 4, west 8. Found by checking which edges of each piece the
     * wall's top surface reaches. The art has no piece for a wall with
     * no neighbors, so slot 0 borrows the east-west one.
     */
    private static final int[][] WALL_PIECES = {
        { 3, 2 }, { 0, 2 }, { 2, 2 }, { 2, 1 }, { 1, 2 }, { 4, 1 },
        { 2, 0 }, { 0, 1 }, { 3, 1 }, { 4, 2 }, { 3, 0 }, { 1, 1 },
        { 4, 0 }, { 1, 0 }, { 0, 0 }, { 5, 0 }
    };
    /* The grey wall pieces sit this many rows below the orange ones. */
    private static final int GREY_WALL_ROW_OFFSET = 3;

    private static final int CRYSTAL_KINDS = 5;
    private static final int MUSHROOM_KINDS = 3;
    private static final int STALAGMITE_KINDS = 5;

    final int w, h;
    final int startX, startY;

    private static final String DEPLETED_NAME = "Rocks";

    private final short[] floor, object;
    private final boolean[] solid, animated;
    private final String[] names;

    /* Per tile: the Ore kind of a rock there, and for a mined-out rock
       the game ticks left until its ore is back. */
    private final byte[] ore;
    private final int[] emptyTicks;
    private int emptyRocks;

    /* Added to an orange rock's frame to get the same rock in grey. */
    private final int greyRockOffset;

    private int version;

    private World(int w, int h, int startX, int startY, short[] floor,
                  short[] object, boolean[] solid, boolean[] animated,
                  String[] names, byte[] ore) {
        this.names = names;
        this.ore = ore;
        emptyTicks = new int[w * h];
        greyRockOffset = Sprites.Cave(0, GREY_WALL_ROW_OFFSET);
        this.w = w;
        this.h = h;
        this.startX = startX;
        this.startY = startY;
        this.floor = floor;
        this.object = object;
        this.solid = solid;
        this.animated = animated;
    }

    /**
     * @param filename a file in the "maps" resource folder
     * @return the map, or null after a fatal error has been reported
     */
    static World Load(String filename) {
        String[] rows = ReadRows(filename);
        if (rows == null) return null;

        int w = 0, h = rows.length, x, y;
        for (y = 0; y < h; y++) w = Math.max(w, rows[y].length());
        if (w == 0) {
            ErrorHandler.LogFatalAndStop(ErrStrEmpty(filename));
            return null;
        }

        short[] floor = new short[w * h], object = new short[w * h];
        boolean[] solid = new boolean[w * h], animated = new boolean[w * h];
        String[] names = new String[w * h];
        byte[] ore = new byte[w * h];
        int startX = -1, startY = -1;

        for (y = 0; y < h; y++) {
            for (x = 0; x < w; x++) {
                int i = y * w + x, mix = Mix(x, y);
                char c = At(rows, x, y);

                floor[i] = object[i] = NO_FRAME;
                solid[i] = true;

                switch (c) {
                case ' ':
                    continue;
                case '#':
                case '%':
                    object[i] = (short) WallFrame(rows, x, y, c);
                    names[i] = "Cave wall";
                    continue;
                case ',':
                    floor[i] = (short) Sprites.Cave(8 + (mix & 1), 3);
                    solid[i] = false;
                    continue;
                default:
                    break;
                }

                /* Everything else stands on cave floor, which is bare
                   in about half the tiles and speckled in the rest. */
                if ((mix & 2) != 0)
                    floor[i] = (short) Sprites.Cave(8 + (mix >> 2 & 1),
                            mix >> 3 & 1);

                switch (c) {
                case '.':
                    solid[i] = false;
                    break;
                case '@':
                    solid[i] = false;
                    startX = x;
                    startY = y;
                    break;
                case 'w':
                    names[i] = "Cobweb";
                    solid[i] = false;
                    object[i] = (short) Sprites.Cave(0, 18);
                    break;
                case 'r':
                case 'R':
                    ore[i] = (byte) (c == 'r' ? Ore.COPPER : Ore.IRON);
                    names[i] = Ore.RockName(ore[i]);
                    object[i] = (short) Sprites.Cave(5 + (mix >> 4 & 1), 1);
                    break;
                case 's':
                    names[i] = "Stalagmites";
                    object[i] = (short) Sprites.Cave(
                            (mix >>> 4) % STALAGMITE_KINDS, 6);
                    break;
                case 'S':
                    names[i] = "Stalagmites";
                    object[i] = (short) Sprites.Cave(
                            (mix >>> 4) % STALAGMITE_KINDS, 8);
                    break;
                case 'c':
                    names[i] = "Crystal";
                    object[i] = (short) Sprites.Cave(
                            (mix >>> 4) % CRYSTAL_KINDS, 16);
                    break;
                case 'm':
                    names[i] = "Mushrooms";
                    object[i] = (short) Sprites.Cave(
                            (mix >>> 4) % MUSHROOM_KINDS, 17);
                    break;
                case 't':
                    names[i] = "Torch";
                    object[i] = (short) Sprites.Cave(0, 14);
                    animated[i] = true;
                    break;
                case 'b':
                    names[i] = "Barrel";
                    object[i] = (short) Sprites.Cave(1, 13);
                    break;
                case 'p':
                    names[i] = "Pot";
                    object[i] = (short) Sprites.Cave(0, 13);
                    break;
                default:
                    ErrorHandler.LogFatalAndStop(
                            ErrStrSymbol(filename, c, x, y));
                    return null;
                }
            }
        }

        if (startX < 0) {
            ErrorHandler.LogFatalAndStop(ErrStrNoStart(filename));
            return null;
        }
        return new World(w, h, startX, startY, floor, object, solid,
                animated, names, ore);
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
        return emptyTicks[y * w + x] > 0 ? DEPLETED_NAME : names[y * w + x];
    }

    /** @return the Ore kind of a rock on the tile, or Ore.NONE */
    int OreAt(int x, int y) {
        return Contains(x, y) ? ore[y * w + x] : Ore.NONE;
    }

    /** @return whether a rock there is mined out for the moment */
    boolean RockEmpty(int x, int y) {
        return emptyTicks[y * w + x] > 0;
    }

    /** Empties the rock on a tile for a number of game ticks. */
    void EmptyRock(int x, int y, int ticks) {
        int i = y * w + x;
        if (emptyTicks[i] == 0) emptyRocks++;
        emptyTicks[i] = ticks;
        version++;
    }

    /** Advances one game tick: counts mined-out rocks toward refilling. */
    void Tick() {
        if (emptyRocks == 0) return;

        for (int i = 0; i < emptyTicks.length; i++) {
            if (emptyTicks[i] == 0 || --emptyTicks[i] > 0) continue;
            emptyRocks--;
            version++;
        }
    }

    /**
     * @return a number that changes whenever anything about how the
     *         map looks has changed
     */
    int Version() { return version; }

    /** @return the color map to draw what stands on the tile with */
    int ObjectColorMap(int x, int y) {
        int i = y * w + x;
        return emptyTicks[i] > 0 ? Sprites.COLOR_MAP_NONE :
                Ore.ColorMap(ore[i]);
    }

    /** @return the cave atlas frame of the ground, or NO_FRAME */
    int FloorFrame(int x, int y) {
        return floor[y * w + x];
    }

    /**
     * @param phase the animation step, 0 to ANIM_FRAMES - 1
     * @return the cave atlas frame of what stands there, or NO_FRAME
     */
    int ObjectFrame(int x, int y, int phase) {
        int i = y * w + x;
        if (emptyTicks[i] > 0) return object[i] + greyRockOffset;
        return animated[i] ? object[i] + phase : object[i];
    }

    private static int WallFrame(String[] rows, int x, int y, char kind) {
        int[] piece = WALL_PIECES[
                (At(rows, x, y - 1) == kind ? 1 : 0) |
                (At(rows, x + 1, y) == kind ? 2 : 0) |
                (At(rows, x, y + 1) == kind ? 4 : 0) |
                (At(rows, x - 1, y) == kind ? 8 : 0)];
        return Sprites.Cave(piece[0],
                piece[1] + (kind == '%' ? GREY_WALL_ROW_OFFSET : 0));
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

    private static String[] ReadRows(String filename) {
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

        String[] rows = text.replace("\r", "").split("\n", -1);
        int h = rows.length;
        while (h > 0 && rows[h - 1].trim().length() == 0) h--;

        String[] trimmed = new String[h];
        System.arraycopy(rows, 0, trimmed, 0, h);
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
    private static String ErrStrSymbol(String filename, char c, int x,
                                       int y) {
        return CLASS + " failed to load the map [" + filename +
                "] because the symbol [" + c + "] at column [" + (x + 1) +
                "] of line [" + (y + 1) + "] is not one it knows.\n";
    }
}
