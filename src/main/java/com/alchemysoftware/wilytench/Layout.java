package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.error.ErrorHandler;

/**
 * Where things are on the picture, as written in data/layout.json.
 *
 * The game's interface is one drawing, the game frame, laid over the
 * view of the world. Everything else has to line up with it: the hole
 * the world shows through, the parchment the chat is written on, the
 * squares the inventory's items sit in. Those places are measured from
 * the frame and written in the file, so that when the frame is redrawn
 * only the file need change.
 *
 * Loaded before the window is made, since the size of the picture is
 * here too.
 */
final class Layout {
    /** How many slots and tabs there are to a row. */
    static final int ACROSS = 4;
    /** The width and height of a slot or tab. */
    static final int CELL = Sheet.CELL;

    private static final String[] KEYS = {
        "picture", "frame", "view", "widgets", "chat", "slots", "top_tabs",
        "bottom_tabs", "pitch"
    };

    /** The size of the whole picture, in game pixels. */
    static int pictureW, pictureH;
    /** The frame's file, in the "images" resource folder. */
    static String frame;

    static int viewX, viewY, viewW, viewH;
    static int widgetsX, widgetsY, widgetsW, widgetsH;
    static int chatX, chatY, chatW, chatH;
    static int slotsX, slotsY;
    static int topTabsX, topTabsY, bottomTabsX, bottomTabsY;
    /** From one slot or tab's left edge to the next's. */
    static int pitch;

    private Layout() {}

    /**
     * Reads data/layout.json. ResourceStreamResolver must be
     * initialized.
     *
     * @return false after a fatal error has been reported
     */
    static boolean Load() {
        try {
            LoadOrThrow(DataNode.Load("layout.json"));
            return true;
        } catch (DataError e) {
            ErrorHandler.LogFatalAndStop("The game failed to start " +
                    "because of a mistake in one of its data files. " +
                    e.getMessage() + "\n");
            return false;
        }
    }

    /**
     * @param root the contents of data/layout.json
     * @throws DataError describing the first mistake found
     */
    static void LoadOrThrow(DataNode root) {
        root.Only(KEYS);

        int[] picture = root.Ints("picture", 2);
        int[] view = Rect(root, "view", picture);
        int[] widgets = Rect(root, "widgets", picture);
        int[] chat = Rect(root, "chat", picture);
        int[] slots = root.Ints("slots", 2);
        int[] top = root.Ints("top_tabs", 2);
        int[] bottom = root.Ints("bottom_tabs", 2);
        int step = root.Int("pitch");

        if (picture[0] < 1 || picture[1] < 1)
            throw root.Fail("[picture] must be a width and a height.");
        if (step < CELL) {
            throw root.Fail("[pitch] must be at least " + CELL +
                    ", the width of a slot.");
        }

        pictureW = picture[0];
        pictureH = picture[1];
        frame = root.Str("frame");
        viewX = view[0];
        viewY = view[1];
        viewW = view[2];
        viewH = view[3];
        widgetsX = widgets[0];
        widgetsY = widgets[1];
        widgetsW = widgets[2];
        widgetsH = widgets[3];
        chatX = chat[0];
        chatY = chat[1];
        chatW = chat[2];
        chatH = chat[3];
        slotsX = slots[0];
        slotsY = slots[1];
        topTabsX = top[0];
        topTabsY = top[1];
        bottomTabsX = bottom[0];
        bottomTabsY = bottom[1];
        pitch = step;
    }

    /* A rectangle that must lie inside the picture. */
    private static int[] Rect(DataNode root, String key, int[] picture) {
        int[] r = root.Ints(key, 4);

        if (r[0] < 0 || r[1] < 0 || r[2] < 1 || r[3] < 1 ||
                r[0] + r[2] > picture[0] || r[1] + r[3] > picture[1]) {
            throw root.Fail("[" + key + "] must be a rectangle, as left, " +
                    "top, width, height, that lies inside the picture.");
        }
        return r;
    }
}
