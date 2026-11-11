package com.alchemysoftware.wilytench;

/**
 * The game's single display palette, as listed in data/palette.json.
 *
 * Everything is drawn as 8-bit color indices; this table turns an
 * index into the pixel Screen uploads. Index 0 is the screen's
 * background. The colors of the file follow from index 1, in the
 * file's order, which leaves sprite sheets free to use index 0 as
 * "transparent". The remaining entries are unassigned and show as
 * magenta so that a stray index is obvious.
 *
 * The file may also list "corrections": colors that turn up in the
 * art by accident, a stray pixel a shade off, and which palette color
 * each should be read as. They affect only how art is converted.
 *
 * The named constants are the colors the game's own drawing code
 * uses for the interface. They are looked up by name when the palette
 * loads, and do not change afterwards.
 */
final class Palette {
    static final int SIZE = 256;

    static final int BACKGROUND = 0;

    static int INK, NIGHT, SLATE, RUST, ASH, LEAF, MINT, AMBER, PEACH, BONE;

    private static final int FIRST = 1;
    private static final int RGB_DIGITS = 6;
    private static final int UNASSIGNED_RGB = 0xFF00FF;

    private static final String[] COLOR_KEYS = { "name", "rgb" };
    private static final String[] CORRECTION_KEYS = { "from", "to" };

    /**
     * Opaque pixels packed 0xAABBGGRR, the packing shared by both source
     * layouts Screen may give the framebuffer. All magenta until Load().
     */
    static final int[] PIXELS = new int[SIZE];

    private static String[] names = new String[0];
    private static int[] rgbs = new int[0];
    private static int[] correctFrom = new int[0], correctTo = new int[0];

    static {
        Shutdown();
    }

    private Palette() {}

    /**
     * @param root the contents of data/palette.json
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "background", "colors", "corrections" });

        DataNode[] nodes = root.Groups("colors", "color");
        int n = nodes.length, i;
        if (n > SIZE - FIRST) {
            throw root.Fail("there are " + n + " colors, but there is " +
                    "room for only " + (SIZE - FIRST) + ".");
        }

        String[] newNames = new String[n];
        int[] newRgbs = new int[n];
        for (i = 0; i < n; i++) {
            newNames[i] = nodes[i].Str("name");
            DataNode node = nodes[i].Called("palette.json, color [" +
                    newNames[i] + "]");
            node.Only(COLOR_KEYS);
            newRgbs[i] = Rgb(node, "rgb");

            for (int j = 0; j < i; j++) {
                if (newNames[j].equals(newNames[i]))
                    throw node.Fail("there are two colors with this name.");
                if (newRgbs[j] == newRgbs[i]) {
                    throw node.Fail("it is the same color as [" +
                            newNames[j] + "].");
                }
            }
        }
        names = newNames;
        rgbs = newRgbs;

        DataNode[] fixes = root.OptGroups("corrections", "correction");
        correctFrom = new int[fixes.length];
        correctTo = new int[fixes.length];
        for (i = 0; i < fixes.length; i++) {
            fixes[i].Only(CORRECTION_KEYS);
            correctFrom[i] = Rgb(fixes[i], "from");
            correctTo[i] = Named(fixes[i].Str("to"));
            if (correctTo[i] < 0) {
                throw fixes[i].Fail("[to] names the color [" +
                        fixes[i].Str("to") + "], and there is no such " +
                        "color. The colors are: " + Names() + ".");
            }
        }

        for (i = 0; i < SIZE; i++) PIXELS[i] = Pack(UNASSIGNED_RGB);
        PIXELS[BACKGROUND] = Pack(Rgb(root, "background"));
        for (i = 0; i < n; i++) PIXELS[FIRST + i] = Pack(rgbs[i]);

        INK = Need(root, "ink");
        NIGHT = Need(root, "night");
        SLATE = Need(root, "slate");
        RUST = Need(root, "rust");
        ASH = Need(root, "ash");
        LEAF = Need(root, "leaf");
        MINT = Need(root, "mint");
        AMBER = Need(root, "amber");
        PEACH = Need(root, "peach");
        BONE = Need(root, "bone");
    }

    static void Shutdown() {
        names = new String[0];
        rgbs = new int[0];
        correctFrom = new int[0];
        correctTo = new int[0];
        for (int i = 0; i < SIZE; i++) PIXELS[i] = Pack(UNASSIGNED_RGB);
    }

    /** @return the palette index of the color called name, or -1 */
    static int Named(String name) {
        for (int i = 0; i < names.length; i++)
            if (names[i].equals(name)) return FIRST + i;
        return -1;
    }

    /** @return every color's name, for error messages */
    static String Names() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < names.length; i++)
            sb.append(i == 0 ? "" : ", ").append(names[i]);
        return sb.toString();
    }

    /**
     * For converting art.
     *
     * @param rgb a color as 0xRRGGBB
     * @return the palette index of that color, after corrections, or
     *         -1 if the palette does not have it
     */
    static int IndexOfRgb(int rgb) {
        int i;
        for (i = 0; i < rgbs.length; i++)
            if (rgbs[i] == rgb) return FIRST + i;
        for (i = 0; i < correctFrom.length; i++)
            if (correctFrom[i] == rgb) return correctTo[i];
        return -1;
    }

    /** @return the color at a palette index as 0xRRGGBB */
    static int RgbOf(int index) {
        int p = PIXELS[index];
        return (p & 0xFF) << 16 | (p & 0xFF00) | (p >> 16 & 0xFF);
    }

    /**
     * @return text that changes whenever a change to the palette
     *         would change what converted art means: the colors in
     *         order, and the corrections
     */
    static String Signature() {
        StringBuffer sb = new StringBuffer();
        int i;
        for (i = 0; i < rgbs.length; i++)
            sb.append(Integer.toHexString(rgbs[i])).append(' ');
        for (i = 0; i < correctFrom.length; i++) {
            sb.append(Integer.toHexString(correctFrom[i])).append('>')
                    .append(correctTo[i]).append(' ');
        }
        return sb.toString().trim();
    }

    private static int Need(DataNode root, String name) {
        int index = Named(name);
        if (index < 0) {
            throw root.Fail("there is no color called [" + name +
                    "], which the game's interface is drawn with.");
        }
        return index;
    }

    private static int Rgb(DataNode node, String key) {
        String hex = node.Str(key);
        try {
            if (hex.length() == RGB_DIGITS) return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            /* Reported below. */
        }
        throw node.Fail("[" + key + "] must be six hexadecimal digits, " +
                "like \"D1935F\", but is \"" + hex + "\".");
    }

    private static int Pack(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}
