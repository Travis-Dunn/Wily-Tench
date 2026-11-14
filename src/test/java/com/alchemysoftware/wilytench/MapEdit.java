package com.alchemysoftware.wilytench;

/**
 * A map being edited: the map file's contents, the changes that can be
 * made to them, and the means to take changes back.
 *
 * This is the map editor with no window. MapEditor shows it and turns
 * the mouse into calls on it, and the tests call it directly.
 *
 * Changes come in strokes. Begin() starts one, and everything done
 * until the next Begin() is undone together by one Undo(): a whole
 * drag of the pencil, not each tile of it. End() finishes one.
 *
 * The map sits in a field of empty space, so that it can be painted
 * outward in every direction. Settle(), called between strokes, keeps
 * a margin of that space at the left and top by sliding the whole map,
 * and Slid...() says how far it has slid in all, so that a window can
 * keep the map still on the screen. None of the empty space is saved:
 * File() is the map with the space around it cropped away, and
 * Layout() is the map where it sits, for drawing.
 */
final class MapEdit {
    private static final int SYMBOLS = 128;
    private static final int UNDO_DEPTH = 200;
    private static final char FIRST_SYMBOL = '!', LAST_SYMBOL = '~';
    /* Tried first for a new symbol, because they read as labels. */
    private static final String PREFERRED = "123456789";

    /* Everything about the map that a change can alter. */
    private static final class State {
        String name;
        String[] rows;
        MapFile.Define[] defines;
        /* How far Settle() has slid the map in all, right and down. */
        int slidX, slidY;
    }

    private final Tiles.Set set;
    private final String tileset;
    private State now;

    private final State[] undo = new State[UNDO_DEPTH];
    private final State[] redo = new State[UNDO_DEPTH];
    private int undos, redos;
    private State saved;
    /* SlidX() less Left(), and the same downward, as of that save. */
    private int savedSlidX, savedSlidY;

    /**
     * @param file the map to edit; not altered
     * @throws DataError if the file names no tileset of tiles.json
     */
    MapEdit(MapFile file) {
        set = Tiles.Named(file.tileset);
        if (set == null) {
            throw new DataError("There is no tileset called [" +
                    file.tileset + "] in tiles.json.");
        }
        tileset = file.tileset;
        now = new State();
        now.name = file.name;
        now.rows = file.rows.clone();
        now.defines = file.defines.clone();
        MarkSaved();
    }

    /**
     * @return the map as it would be saved: with the empty space
     *         around it cropped away
     */
    MapFile File() {
        MapFile file = Layout();
        file.rows = MapFile.Crop(file.rows);
        return file;
    }

    /**
     * @return the map where it sits among the empty space, tile for
     *         tile as At() gives it; for drawing
     */
    MapFile Layout() {
        MapFile file = new MapFile();
        file.name = now.name;
        file.tileset = tileset;
        file.rows = now.rows.clone();
        file.defines = now.defines.clone();
        return file;
    }

    /** @return the column of the map's leftmost tile; 0 if it has none */
    int Left() {
        int left = Integer.MAX_VALUE;
        for (int y = 0; y < now.rows.length; y++) {
            String row = now.rows[y];
            int indent = 0;
            if (row.length() == 0) continue;
            while (row.charAt(indent) == Tiles.NOTHING) indent++;
            left = Math.min(left, indent);
        }
        return left == Integer.MAX_VALUE ? 0 : left;
    }

    /** @return the row of the map's topmost tile; 0 if it has none */
    int Top() {
        for (int y = 0; y < now.rows.length; y++)
            if (now.rows[y].length() > 0) return y;
        return 0;
    }

    /** @return how far the map has been slid right in all */
    int SlidX() { return now.slidX; }

    /** @return how far the map has been slid down in all */
    int SlidY() { return now.slidY; }

    /**
     * @return how many columns to the right the tiles the map had when
     *         it was last saved, or opened, would now be saved at. Not
     *         zero once the map has grown or shrunk at its left, which
     *         matters to games saved on it
     */
    int MovedX() { return now.slidX - Left() - savedSlidX; }

    /** @return the same, in rows downward */
    int MovedY() { return now.slidY - Top() - savedSlidY; }

    Tiles.Set Set() { return set; }

    String Name() { return now.name; }

    /** @return how many tiles wide the grid is at its widest */
    int Width() {
        int w = 0;
        for (int y = 0; y < now.rows.length; y++)
            w = Math.max(w, now.rows[y].length());
        return w;
    }

    int Height() { return now.rows.length; }

    /** @return the symbol on a tile; a space anywhere off the grid */
    char At(int x, int y) {
        if (y < 0 || y >= now.rows.length) return Tiles.NOTHING;
        String row = now.rows[y];
        return x >= 0 && x < row.length() ? row.charAt(x) : Tiles.NOTHING;
    }

    /** @return the define for a symbol, or null */
    MapFile.Define Defined(char symbol) {
        for (int i = 0; i < now.defines.length; i++)
            if (now.defines[i].symbol == symbol) return now.defines[i];
        return null;
    }

    /** @return the map's defines, in order of symbol */
    MapFile.Define[] Defines() {
        MapFile.Define[] sorted = now.defines.clone();
        for (int i = 1; i < sorted.length; i++) {
            MapFile.Define d = sorted[i];
            int j = i;
            for (; j > 0 && sorted[j - 1].symbol > d.symbol; j--)
                sorted[j] = sorted[j - 1];
            sorted[j] = d;
        }
        return sorted;
    }

    /**
     * @return whether a symbol means something on this map: nothing,
     *         the start, a define, or anything of the tileset
     */
    boolean Known(char c) {
        return c == Tiles.NOTHING || c == Tiles.START ||
                Defined(c) != null || (c < SYMBOLS && (set.walls[c] != null
                || set.floors[c] != null || set.things[c] != null));
    }

    /**
     * @return whether a symbol may be on the map only once: the start
     *         and points
     */
    boolean OnlyOne(char c) {
        MapFile.Define d = Defined(c);
        return c == Tiles.START ||
                (d != null && d.kind.equals(MapFile.KIND_POINT));
    }

    /** Starts a stroke: what follows is undone as one change. */
    void Begin() {
        if (undos == UNDO_DEPTH) {
            System.arraycopy(undo, 1, undo, 0, UNDO_DEPTH - 1);
            undos--;
        }
        undo[undos++] = now;
        redos = 0;

        State next = new State();
        next.name = now.name;
        next.rows = now.rows.clone();
        next.defines = now.defines.clone();
        next.slidX = now.slidX;
        next.slidY = now.slidY;
        now = next;
    }

    /**
     * Ends a stroke. One that changed nothing is forgotten, so that
     * it does not take an Undo() to get past it.
     */
    void End() {
        if (undos > 0 && FileOf(undo[undos - 1]).Write().equals(
                File().Write())) now = undo[--undos];
    }

    /**
     * Puts a symbol on a tile. A symbol that may be on the map only
     * once is moved: wherever else it was becomes floor. Tiles left
     * of column 0 or above row 0 are ignored, as are symbols that
     * mean nothing here.
     *
     * @return whether anything changed
     */
    boolean Paint(int x, int y, char c) {
        if (x < 0 || y < 0 || !Known(c) || At(x, y) == c) return false;

        if (OnlyOne(c)) {
            for (int oy = 0; oy < now.rows.length; oy++) {
                for (int ox = now.rows[oy].indexOf(c); ox >= 0;
                        ox = now.rows[oy].indexOf(c, ox + 1)) {
                    Put(ox, oy, FloorBeside(ox, oy));
                }
            }
        }
        Put(x, y, c);
        return true;
    }

    /**
     * Puts a symbol on every tile of the straight line between two
     * tiles, both included. For a pencil, whose pointer is reported
     * only now and then and may have crossed several tiles between.
     *
     * @return whether anything changed
     */
    boolean Line(int x0, int y0, int x1, int y1, char c) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int stepX = x0 < x1 ? 1 : -1, stepY = y0 < y1 ? 1 : -1;
        int err = dx - dy, x = x0, y = y0;
        boolean changed = false;

        while (true) {
            changed |= Paint(x, y, c);
            if ((x == x1 && y == y1) || OnlyOne(c)) return changed;
            int twice = 2 * err;
            if (twice > -dy) {
                err -= dy;
                x += stepX;
            }
            if (twice < dx) {
                err += dx;
                y += stepY;
            }
        }
    }

    /**
     * Puts a symbol on every tile of a rectangle, given by any two
     * opposite corners. A symbol that may be on the map only once goes
     * on the first corner alone.
     */
    void Fill(int x0, int y0, int x1, int y1, char c) {
        if (OnlyOne(c)) {
            Paint(x0, y0, c);
            return;
        }
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                Paint(x, y, c);
    }

    /**
     * Slides the whole map so that it has exactly a margin of empty
     * space at its left and top, whichever way that is. Part of the
     * stroke in progress, if there is one. A map with no tiles is left
     * alone.
     *
     * @param margin columns and rows of empty space to leave
     */
    void Settle(int margin) {
        if (Width() == 0) return;

        int dx = margin - Left(), dy = margin - Top();
        String[] rows = new String[now.rows.length + dy];
        StringBuffer pad = new StringBuffer();

        for (int i = 0; i < dx; i++) pad.append(Tiles.NOTHING);
        for (int y = 0; y < rows.length; y++) {
            String row = y < dy ? "" : now.rows[y - dy];
            rows[y] = row.length() == 0 ? "" :
                    dx >= 0 ? pad + row : row.substring(-dx);
        }
        now.rows = rows;
        now.slidX += dx;
        now.slidY += dy;
    }

    void SetName(String name) { now.name = name; }

    /**
     * @return a symbol that means nothing yet on this map, for a new
     *         define; 0 if every symbol is taken
     */
    char FreeSymbol() {
        for (int i = 0; i < PREFERRED.length(); i++)
            if (!Known(PREFERRED.charAt(i))) return PREFERRED.charAt(i);
        for (char c = FIRST_SYMBOL; c <= LAST_SYMBOL; c++)
            if (c != '!' && !Known(c)) return c;
        return 0;
    }

    /**
     * Gives a symbol a meaning on this map.
     *
     * @param kind one of the MapFile.KIND_ values
     * @param value an npc's id, a point's name, or a thing's symbol
     * @param on the floor symbol to stand on, or MapFile.NO_FLOOR to
     *           have it worked out
     * @return the symbol chosen for it, or 0 if none is free
     */
    char AddDefine(String kind, String value, char on) {
        char symbol = FreeSymbol();
        if (symbol == 0) return 0;

        MapFile.Define d = new MapFile.Define();
        d.symbol = symbol;
        d.kind = kind;
        d.value = value;
        d.on = on;

        MapFile.Define[] defines =
                new MapFile.Define[now.defines.length + 1];
        System.arraycopy(now.defines, 0, defines, 0, now.defines.length);
        defines[now.defines.length] = d;
        now.defines = defines;
        return symbol;
    }

    /**
     * Takes a define away. Wherever its symbol was on the map becomes
     * floor.
     */
    void RemoveDefine(char symbol) {
        if (Defined(symbol) == null) return;

        for (int y = 0; y < now.rows.length; y++) {
            for (int x = now.rows[y].indexOf(symbol); x >= 0;
                    x = now.rows[y].indexOf(symbol, x + 1)) {
                Put(x, y, FloorBeside(x, y));
            }
        }

        MapFile.Define[] defines =
                new MapFile.Define[now.defines.length - 1];
        for (int i = 0, at = 0; i < now.defines.length; i++)
            if (now.defines[i].symbol != symbol) defines[at++] = now.defines[i];
        now.defines = defines;
    }

    /** @return how many times a symbol is on the map */
    int Count(char symbol) {
        int n = 0;
        for (int y = 0; y < now.rows.length; y++)
            for (int x = 0; x < now.rows[y].length(); x++)
                if (now.rows[y].charAt(x) == symbol) n++;
        return n;
    }

    boolean CanUndo() { return undos > 0; }

    boolean CanRedo() { return redos > 0; }

    /** Takes back the last stroke. */
    void Undo() {
        if (undos == 0) return;
        redo[redos++] = now;
        now = undo[--undos];
    }

    /** Puts back the last stroke taken back. */
    void Redo() {
        if (redos == 0) return;
        undo[undos++] = now;
        now = redo[--redos];
    }

    /** @return whether the map differs from when it was last saved */
    boolean Changed() {
        return !File().Write().equals(FileOf(saved).Write());
    }

    /** Notes that the map as it stands is what is on disk. */
    void MarkSaved() {
        saved = now;
        savedSlidX = now.slidX - Left();
        savedSlidY = now.slidY - Top();
    }

    /**
     * @return the floor symbol most of the four tiles beside a tile
     *         are; on a tie the tileset's earlier floor; the
     *         tileset's first floor if none of them is floor
     */
    char FloorBeside(int x, int y) {
        int[][] beside = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
        char best = 0;
        int bestVotes = 0;

        for (int a = 0; a < beside.length; a++) {
            char ca = At(x + beside[a][0], y + beside[a][1]);
            int votes = 0;
            if (ca >= SYMBOLS || set.floors[ca] == null) continue;

            for (int b = 0; b < beside.length; b++)
                if (At(x + beside[b][0], y + beside[b][1]) == ca) votes++;
            if (votes > bestVotes || (votes == bestVotes &&
                    set.floors[ca].order < set.floors[best].order)) {
                best = ca;
                bestVotes = votes;
            }
        }
        if (best != 0) return best;

        for (char c = 0; c < SYMBOLS; c++)
            if (set.floors[c] == set.baseFloor) return c;
        return Tiles.NOTHING;
    }

    /* A state as it would be saved. */
    private MapFile FileOf(State state) {
        MapFile file = new MapFile();
        file.name = state.name;
        file.tileset = tileset;
        file.rows = MapFile.Crop(state.rows);
        file.defines = state.defines;
        return file;
    }

    /* Writes one tile, growing the grid to reach it. */
    private void Put(int x, int y, char c) {
        if (y >= now.rows.length) {
            String[] rows = new String[y + 1];
            System.arraycopy(now.rows, 0, rows, 0, now.rows.length);
            for (int i = now.rows.length; i <= y; i++) rows[i] = "";
            now.rows = rows;
        }

        StringBuffer row = new StringBuffer(now.rows[y]);
        while (row.length() <= x) row.append(Tiles.NOTHING);
        row.setCharAt(x, c);
        now.rows[y] = TrimEnd(row.toString());
    }

    private static String TrimEnd(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == Tiles.NOTHING) end--;
        return s.substring(0, end);
    }
}
