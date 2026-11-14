package com.alchemysoftware.wilytench;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;

/**
 * A map file as it is written: its settings, its own symbols, and its
 * grid of characters. This class reads the text and writes it back; it
 * knows nothing of what the symbols mean, which is World's business.
 *
 * A map file is the only form a map ever has. People write it in a
 * text editor, AIs write it as text, and the map editor reads and
 * writes this same file. There is nothing to export and nothing to
 * keep in step.
 *
 *   !name Copper Cave
 *   !tileset cave
 *
 *   !define 1 point from_dungeon
 *   !define H npc harl
 *
 *   !map
 *   ##########
 *   #.L1...H.#
 *   #...@....#
 *   ##########
 *
 * The lines beginning with "!" are settings. After "!map" comes the
 * grid, one character per tile, to the end of the file. Everything
 * that is anywhere is in the grid, so a map can be checked by looking
 * at it.
 *
 * A character in the grid means what data/tiles.json says it means for
 * the map's tileset, unless the map has given it a meaning of its own
 * with "!define". Defined symbols are for what a tileset cannot say:
 * things that belong to one map, or that need settings of their own.
 *
 *   !define H npc harl          a person from npcs.json
 *   !define 1 point from_cave   a named spot, for arriving at
 *   !define 2 like r on ,       the thing "r", standing on the floor ","
 *
 * Any define may end "on" and a floor symbol, to say what is under it.
 * Without that, World works the floor out from the tiles around it.
 *
 * Write() always lays a file out the same way, the "canonical form":
 * name, tileset, a blank line, the defines in order of symbol, a blank
 * line, "!map", and the rows, with no empty space around the map: no
 * blank rows above or below it, no column at the left that every row
 * leaves blank, and nothing after a row's last character.
 * Every map in the game is kept in that form, which the build checks,
 * so that a file comes out the same whoever or whatever wrote it.
 */
final class MapFile {
    /** One "!define" line. */
    static final class Define {
        char symbol;
        /** KIND_NPC, KIND_POINT, or KIND_LIKE. */
        String kind;
        /** An npc's id, a point's name, or a thing's symbol. */
        String value;
        /** The floor symbol after "on", or NO_FLOOR. */
        char on;
    }

    static final String KIND_NPC = "npc";
    static final String KIND_POINT = "point";
    static final String KIND_LIKE = "like";
    static final char NO_FLOOR = 0;

    private static final String FOLDER = "maps";
    private static final char MARK = '!';
    private static final String NAME = "name", TILESET = "tileset";
    private static final String DEFINE = "define", MAP = "map", ON = "on";
    private static final String KINDS = "npc, point, like";
    private static final char FIRST_SYMBOL = '!', LAST_SYMBOL = '~';

    String name, tileset;
    Define[] defines = new Define[0];
    /** The grid, top row first. Rows may be different lengths. */
    String[] rows = new String[0];

    /**
     * @param filename a file in the "maps" resource folder
     * @return the file's text
     * @throws DataError if it cannot be read
     */
    static String Read(String filename) {
        InputStream in = ResourceStreamResolver.Get(FOLDER, filename);
        if (in == null) throw new DataError(filename + " could not be found.");

        try {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) != -1) bytes.write(chunk, 0, n);
                return bytes.toString("US-ASCII");
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new DataError(filename + " could not be read: " +
                    e.getMessage());
        }
    }

    /**
     * @param text the whole of a map file
     * @param filename what to call the file in error messages
     * @throws DataError describing the first mistake found
     */
    static MapFile Parse(String text, String filename) {
        String[] lines = text.replace("\r", "").split("\n", -1);
        MapFile map = new MapFile();
        Define[] defines = new Define[lines.length];
        int count = 0, at = 0;
        boolean inGrid = false;

        for (; at < lines.length && !inGrid; at++) {
            String line = lines[at].trim();
            String where = filename + ", line " + (at + 1);
            if (line.length() == 0) continue;
            if (line.charAt(0) != MARK) {
                throw new DataError(where + ": [" + line + "] comes " +
                        "before the [!map] line, and only settings, " +
                        "which begin with [!], may. Put [!map] on a " +
                        "line by itself above the first row of the map.");
            }

            String[] words = line.substring(1).trim().split("\\s+");
            String rest = line.substring(1).trim();
            rest = rest.substring(words[0].length()).trim();

            if (words[0].equals(NAME) && rest.length() > 0) {
                map.name = rest;
            } else if (words[0].equals(TILESET) && words.length == 2) {
                map.tileset = words[1];
            } else if (words[0].equals(DEFINE)) {
                defines[count++] = ParseDefine(words, where);
                for (int i = 0; i < count - 1; i++) {
                    if (defines[i].symbol != defines[count - 1].symbol)
                        continue;
                    throw new DataError(where + ": the symbol [" +
                            defines[i].symbol + "] is defined twice.");
                }
            } else if (words[0].equals(MAP) && words.length == 1) {
                inGrid = true;
            } else {
                throw new DataError(where + ": the setting [" + line +
                        "] is not one a map file has. They are [!name] " +
                        "and some text, [!tileset] and a tileset of " +
                        "tiles.json, [!define], and [!map].");
            }
        }

        if (!inGrid) {
            throw new DataError(filename + ": there is no [!map] line. " +
                    "It goes on a line by itself above the first row " +
                    "of the map.");
        }
        if (map.name == null)
            throw new DataError(filename + ": there is no [!name] line.");
        if (map.tileset == null) {
            throw new DataError(filename + ": there is no [!tileset] " +
                    "line saying which tileset of tiles.json it uses.");
        }

        int end = lines.length;
        while (end > at && lines[end - 1].trim().length() == 0) end--;
        map.rows = new String[end - at];
        for (int i = at; i < end; i++) {
            if (lines[i].indexOf('\t') >= 0) {
                throw new DataError(filename + ", line " + (i + 1) +
                        ": there is a tab in the map. Use spaces.");
            }
            map.rows[i - at] = TrimEnd(lines[i]);
        }

        map.defines = new Define[count];
        System.arraycopy(defines, 0, map.defines, 0, count);
        return map;
    }

    /** @return the define for a symbol, or null */
    Define Defined(char symbol) {
        for (int i = 0; i < defines.length; i++)
            if (defines[i].symbol == symbol) return defines[i];
        return null;
    }

    /** @return the file's text, in canonical form */
    String Write() {
        StringBuffer sb = new StringBuffer();
        Define[] sorted = new Define[defines.length];
        int i, j;

        System.arraycopy(defines, 0, sorted, 0, sorted.length);
        for (i = 1; i < sorted.length; i++) {
            Define d = sorted[i];
            for (j = i; j > 0 && sorted[j - 1].symbol > d.symbol; j--)
                sorted[j] = sorted[j - 1];
            sorted[j] = d;
        }

        sb.append(MARK).append(NAME).append(' ').append(name).append('\n');
        sb.append(MARK).append(TILESET).append(' ').append(tileset)
                .append('\n');
        if (sorted.length > 0) sb.append('\n');
        for (i = 0; i < sorted.length; i++) {
            Define d = sorted[i];
            sb.append(MARK).append(DEFINE).append(' ').append(d.symbol)
                    .append(' ').append(d.kind).append(' ').append(d.value);
            if (d.on != NO_FLOOR)
                sb.append(' ').append(ON).append(' ').append(d.on);
            sb.append('\n');
        }
        sb.append('\n').append(MARK).append(MAP).append('\n');

        String[] cropped = Crop(rows);
        for (i = 0; i < cropped.length; i++)
            sb.append(cropped[i]).append('\n');
        return sb.toString();
    }

    /**
     * @return a grid with the empty space around it taken away: blank
     *         rows at the top and bottom, columns at the left that
     *         every row leaves blank, and spaces at the ends of rows.
     *         Blank rows inside the map are kept
     */
    static String[] Crop(String[] rows) {
        int top = 0, bottom = rows.length, left = Integer.MAX_VALUE;

        while (top < bottom && rows[top].trim().length() == 0) top++;
        while (bottom > top && rows[bottom - 1].trim().length() == 0)
            bottom--;
        for (int y = top; y < bottom; y++) {
            String row = rows[y];
            int indent = 0;
            if (row.trim().length() == 0) continue;
            while (row.charAt(indent) == ' ') indent++;
            left = Math.min(left, indent);
        }

        String[] cropped = new String[bottom - top];
        for (int y = top; y < bottom; y++) {
            String row = TrimEnd(rows[y]);
            cropped[y - top] = row.length() > left ? row.substring(left) :
                    "";
        }
        return cropped;
    }

    /* "define", the symbol, a kind, a value, and perhaps "on" and a
       floor symbol. */
    private static Define ParseDefine(String[] words, String where) {
        Define d = new Define();
        boolean hasOn = words.length == 6 && words[4].equals(ON);

        if ((words.length != 4 && !hasOn) || words[1].length() != 1 ||
                (hasOn && words[5].length() != 1)) {
            throw new DataError(where + ": a define is written [!define " +
                    "H npc harl], or with a floor to stand on, [!define " +
                    "H npc harl on ,]: one symbol, a kind, and a name.");
        }
        d.symbol = words[1].charAt(0);
        d.kind = words[2];
        d.value = words[3];
        d.on = hasOn ? words[5].charAt(0) : NO_FLOOR;

        if (d.symbol < FIRST_SYMBOL || d.symbol > LAST_SYMBOL ||
                d.symbol == MARK || d.symbol == Tiles.START) {
            throw new DataError(where + ": [" + d.symbol + "] cannot be " +
                    "defined. A symbol is one visible character other " +
                    "than [!] and [@].");
        }
        if (!d.kind.equals(KIND_NPC) && !d.kind.equals(KIND_POINT) &&
                !d.kind.equals(KIND_LIKE)) {
            throw new DataError(where + ": there is no kind of define " +
                    "called [" + d.kind + "]. The kinds are: " + KINDS +
                    ".");
        }
        if (d.kind.equals(KIND_LIKE) && d.value.length() != 1) {
            throw new DataError(where + ": [like] is followed by the " +
                    "one symbol of the thing to copy.");
        }
        return d;
    }

    private static String TrimEnd(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') end--;
        return s.substring(0, end);
    }
}
