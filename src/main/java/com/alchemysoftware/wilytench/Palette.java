package com.alchemysoftware.wilytench;

/**
 * The game's single 256-entry display palette.
 *
 * Everything is drawn as 8-bit color indices; this table turns an index
 * into the pixel Screen uploads. Index 0 is the screen's background.
 * Indices 1 through 17 are the seventeen colors of the Demonic Dungeon
 * art, darkest first, which leaves sprite atlases free to use index 0
 * as "transparent". The remaining entries are unassigned and show as
 * magenta so that a stray index is obvious.
 */
final class Palette {
    static final int SIZE = 256;

    static final int BACKGROUND = 0;

    static final int INK = 1;
    static final int NIGHT = 2;
    static final int PLUM = 3;
    static final int MAROON = 4;
    static final int MOSS = 5;
    static final int SLATE = 6;
    static final int PINE = 7;
    static final int RUST = 8;
    static final int BLOOD = 9;
    static final int ROSE = 10;
    static final int ASH = 11;
    static final int TAN = 12;
    static final int LEAF = 13;
    static final int MINT = 14;
    static final int AMBER = 15;
    static final int PEACH = 16;
    static final int BONE = 17;

    static final int ART_FIRST = INK;
    static final int ART_COUNT = 17;

    /* 0xRRGGBB, in index order starting at ART_FIRST. */
    private static final int[] ART_RGB = {
        0x160D13, 0x31293E, 0x432142, 0x56212A, 0x2F3D3A, 0x5F575E,
        0x4D6660, 0x904B41, 0xAD4030, 0x9B4C63, 0xA69998, 0xD1935F,
        0x95B666, 0x8EB89E, 0xEF9E4E, 0xE79B7C, 0xF6F2C3
    };
    private static final int BACKGROUND_RGB = 0x160D13;
    private static final int UNASSIGNED_RGB = 0xFF00FF;

    /**
     * Opaque pixels packed 0xAABBGGRR, the packing shared by both source
     * layouts Screen may give the framebuffer.
     */
    static final int[] PIXELS = Build();

    private Palette() {}

    private static int[] Build() {
        int[] p = new int[SIZE];
        int i;

        for (i = 0; i < SIZE; i++) p[i] = Pack(UNASSIGNED_RGB);
        p[BACKGROUND] = Pack(BACKGROUND_RGB);
        for (i = 0; i < ART_COUNT; i++) p[ART_FIRST + i] = Pack(ART_RGB[i]);
        return p;
    }

    private static int Pack(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}
