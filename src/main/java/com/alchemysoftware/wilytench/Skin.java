package com.alchemysoftware.wilytench;

/**
 * The pictures the interface's widgets are put together from: buttons,
 * checkboxes, sliders, and the frame around a menu. They come from the
 * purchased interface art, and which cells are used is written in the
 * "widgets" part of data/interface.json, so that the art can be
 * swapped without touching this.
 *
 * The art is made to be assembled. A button is drawn three cells wide
 * and a little over one tall; here its middle cell is repeated to make
 * a button of any width. A frame is a four-by-four block of cells with
 * a hollow middle, and its edge cells are repeated to make a frame of
 * any size. A slider's track is three cells, an arrow at each end, and
 * again the middle repeats.
 *
 * Everything is measured in cells of 16 pixels, so widgets are always
 * whole cells wide.
 */
final class Skin {
    static final int CELL = Sheet.CELL;

    /* States of a button. */
    static final int NORMAL = 0;
    static final int HOVER = 1;
    static final int PRESSED = 2;
    private static final int BUTTON_STATES = 3;

    /** How tall a button stands, lip and all. */
    static final int BUTTON_H = 20;
    /** How tall the part of a button is that writing goes on. */
    static final int BUTTON_FACE_H = 16;

    private static final int FRAME_CELLS = 4;
    private static final int BUTTON_ROWS = 2, BUTTON_COLS = 3;
    private static final int TRACK_COLS = 3;

    private static final String[] KEYS = {
        "button", "checkbox", "radio", "slider", "slider_knob", "frame"
    };
    private static final String[] BUTTON_KEYS = {
        "sheet", "normal", "hover", "pressed", "pressed_drop"
    };
    private static final String[] BUTTON_STATE_KEYS = {
        "normal", "hover", "pressed"
    };
    private static final String[] TOGGLE_KEYS = { "sheet", "off", "on" };
    private static final String[] TRACK_KEYS = { "sheet", "track" };
    private static final String[] KNOB_KEYS = { "sheet", "cell" };
    private static final String[] FRAME_KEYS = { "sheet", "block", "margin" };

    /* button[state][row][column], frame[row][column]. */
    private static int[][][] button;
    private static int[][] frame;
    private static int[] checkbox, radio, track;
    private static int knob;
    private static int pressedDrop, frameMargin;

    private Skin() {}

    /**
     * @param root the "widgets" group of data/interface.json; Sprites
     *             must already be loaded
     */
    static void Load(DataNode root) {
        root.Only(KEYS);

        DataNode node = root.Obj("button");
        node.Only(BUTTON_KEYS);
        button = new int[BUTTON_STATES][][];
        for (int i = 0; i < BUTTON_STATES; i++) {
            button[i] = Block(node, node.Ints(BUTTON_STATE_KEYS[i], 2),
                    BUTTON_COLS, BUTTON_ROWS);
        }
        pressedDrop = node.Int("pressed_drop");

        checkbox = Toggle(root.Obj("checkbox"));
        radio = Toggle(root.Obj("radio"));

        node = root.Obj("slider");
        node.Only(TRACK_KEYS);
        track = Block(node, node.Ints("track", 2), TRACK_COLS, 1)[0];

        node = root.Obj("slider_knob");
        node.Only(KNOB_KEYS);
        knob = Sprites.Picture(node, node.Str("sheet"),
                node.Ints("cell", 2));

        node = root.Obj("frame");
        node.Only(FRAME_KEYS);
        frame = Block(node, node.Ints("block", 2), FRAME_CELLS,
                FRAME_CELLS);
        frameMargin = node.Int("margin");
        if (frameMargin < 0 || frameMargin >= CELL)
            throw node.Fail("[margin] must be from 0 to " + (CELL - 1) + ".");
    }

    static void Shutdown() {
        button = null;
        frame = null;
        checkbox = radio = track = null;
    }

    /**
     * Draws a button with its top left at (x, y).
     *
     * @param cells its width in cells, at least 2
     * @param state NORMAL, HOVER, or PRESSED
     */
    static void DrawButton(int x, int y, int cells, int state) {
        int[][] art = button[state];
        int top = y + ButtonDrop(state);

        for (int row = 0; row < BUTTON_ROWS; row++) {
            for (int i = 0; i < cells; i++) {
                Sprites.Draw(art[row][Along(i, cells, BUTTON_COLS)],
                        Sprites.COLOR_MAP_NONE, x + i * CELL,
                        top + row * CELL);
            }
        }
    }

    /**
     * @return how far down a button in that state is drawn, which is
     *         also how far its writing should move
     */
    static int ButtonDrop(int state) {
        return state == PRESSED ? pressedDrop : 0;
    }

    /** Draws a one-cell checkbox, ticked or empty. */
    static void DrawCheckbox(int x, int y, boolean on) {
        Sprites.Draw(checkbox[on ? 1 : 0], Sprites.COLOR_MAP_NONE, x, y);
    }

    /** Draws a one-cell round button, chosen or not. */
    static void DrawRadio(int x, int y, boolean on) {
        Sprites.Draw(radio[on ? 1 : 0], Sprites.COLOR_MAP_NONE, x, y);
    }

    /**
     * Draws a slider's track, an arrow at each end.
     *
     * @param cells its width in cells, at least 2
     */
    static void DrawTrack(int x, int y, int cells) {
        for (int i = 0; i < cells; i++) {
            Sprites.Draw(track[Along(i, cells, TRACK_COLS)],
                    Sprites.COLOR_MAP_NONE, x + i * CELL, y);
        }
    }

    /** Draws a slider's one-cell knob. */
    static void DrawKnob(int x, int y) {
        Sprites.Draw(knob, Sprites.COLOR_MAP_NONE, x, y);
    }

    /**
     * Draws a frame, filled in, with its top left cell at (x, y). The
     * frame's own border starts FrameMargin() pixels inside that.
     *
     * @param cellsW its width in cells, at least 2
     * @param cellsH its height in cells, at least 2
     */
    static void DrawFrame(int x, int y, int cellsW, int cellsH) {
        Gfx.FillRect(x + frameMargin, y + frameMargin,
                cellsW * CELL - 2 * frameMargin,
                cellsH * CELL - 2 * frameMargin, Palette.INK);

        for (int row = 0; row < cellsH; row++) {
            int artRow = Along(row, cellsH, FRAME_CELLS);
            boolean edgeRow = row == 0 || row == cellsH - 1;

            for (int col = 0; col < cellsW; col++) {
                if (!edgeRow && col > 0 && col < cellsW - 1) continue;
                Sprites.Draw(frame[artRow][Along(col, cellsW, FRAME_CELLS)],
                        Sprites.COLOR_MAP_NONE, x + col * CELL,
                        y + row * CELL);
            }
        }
    }

    /** @return how far inside its cells a frame's border starts */
    static int FrameMargin() { return frameMargin; }

    /*
     * Which cell of a piece of art "of" cells long goes at place i of
     * something "cells" long: the art's first cell first, its last
     * last, and its middle cells taken in turn in between.
     */
    private static int Along(int i, int cells, int of) {
        if (i == 0) return 0;
        if (i == cells - 1) return of - 1;
        return 1 + (i - 1) % (of - 2);
    }

    private static int[] Toggle(DataNode node) {
        node.Only(TOGGLE_KEYS);
        return new int[] {
            Sprites.Picture(node, node.Str("sheet"), node.Ints("off", 2)),
            Sprites.Picture(node, node.Str("sheet"), node.Ints("on", 2))
        };
    }

    /* The pictures of a block of cells, [row][column], given the cell
       at its top left. */
    private static int[][] Block(DataNode node, int[] at, int cols,
                                 int rows) {
        int[][] block = new int[rows][cols];
        String sheet = node.Str("sheet");

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                block[row][col] = Sprites.Picture(node, sheet,
                        new int[] { at[0] + col, at[1] + row });
            }
        }
        return block;
    }
}
