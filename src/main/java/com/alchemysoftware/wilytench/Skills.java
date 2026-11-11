package com.alchemysoftware.wilytench;

/**
 * The skills there are, as listed in data/skills.json, and how much
 * experience a player has earned in each.
 *
 * Each skill has an id, by which scripts and saved games refer to it;
 * a name to show the player; and an icon. Inside the game a skill is
 * a small number, good only for the length of one run: its place in
 * the file. An object of this class is one player's experience.
 *
 * Experience is counted in tenths of a point, so that awards such as
 * 17.5 stay exact. Levels run from 1 to 99 and each takes about a tenth
 * more experience than the one before, the same curve RuneScape uses:
 * level 2 at 83 points, level 50 at about a hundred thousand, level 99
 * at about thirteen million.
 */
final class Skills {
    static final int MAX_LEVEL = 99;

    private static final int NO_PICTURE = -1;

    private static final String[] KEYS = { "id", "name", "sheet", "cell" };

    private static final int TENTHS = 10;
    private static final int MAX_XP_TENTHS = 2000000000;

    /* LEVEL_XP[n] is the whole points needed to reach level n. */
    private static final int[] LEVEL_XP = BuildLevels();

    private static String[] ids = new String[0];
    private static String[] names, initials;
    private static int[] pictures;

    private final int[] xpTenths = new int[ids.length];

    /**
     * @param root the contents of data/skills.json; Sprites must
     *             already be loaded
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "skills" });

        DataNode[] nodes = root.Groups("skills", "skill");
        int n = nodes.length;
        String[] newIds = new String[n];

        names = new String[n];
        initials = new String[n];
        pictures = new int[n];

        for (int i = 0; i < n; i++) {
            String id = nodes[i].Str("id");
            DataNode node = nodes[i].Called("skills.json, skill [" + id +
                    "]");
            node.Only(KEYS);
            for (int j = 0; j < i; j++) {
                if (newIds[j].equals(id))
                    throw node.Fail("there are two skills with this id.");
            }

            newIds[i] = id;
            names[i] = node.Str("name");
            if (names[i].length() == 0)
                throw node.Fail("[name] is empty.");
            initials[i] = names[i].substring(0, 1);
            pictures[i] = node.Has("sheet") ? Sprites.Picture(node,
                    node.Str("sheet"), node.Ints("cell", 2)) : NO_PICTURE;
        }
        ids = newIds;
    }

    static void Shutdown() { ids = new String[0]; }

    /** @return how many skills there are */
    static int Count() { return ids.length; }

    static String Name(int skill) { return names[skill]; }

    /** @return the skill's id, which is also its name in saved games */
    static String Id(int skill) { return ids[skill]; }

    /** @return the skill with that id, or -1 */
    static int Named(String id) {
        for (int i = 0; i < ids.length; i++)
            if (ids[i].equals(id)) return i;
        return -1;
    }

    /**
     * Draws the skill's 16 pixel icon with its top left at (x, y). A
     * skill with no picture yet gets the first letter of its name.
     *
     * @param letters the font for such a letter
     */
    static void DrawIcon(int skill, Font letters, int x, int y) {
        if (pictures[skill] != NO_PICTURE) {
            Sprites.Draw(pictures[skill], Sprites.COLOR_MAP_NONE, x, y);
            return;
        }
        String initial = initials[skill];
        Text.Draw(letters, initial, 0, 1,
                x + (Sheet.CELL - letters.Width(initial, 0, 1)) / 2,
                y + (Sheet.CELL - letters.lineH) / 2, Palette.AMBER);
    }

    /**
     * Sets a skill's experience outright. For restoring a saved game.
     *
     * @param tenths experience in tenths of a point; out-of-range
     *               values are brought into range
     */
    void SetXpTenths(int skill, int tenths) {
        xpTenths[skill] = Math.max(0, Math.min(tenths, MAX_XP_TENTHS));
    }

    /** @return whole experience points needed to reach a level */
    static int XpForLevel(int level) { return LEVEL_XP[level]; }

    /**
     * Awards experience.
     *
     * @return true if the award carried the skill into a new level
     */
    boolean AddXp(int skill, int tenths) {
        int before = Level(skill);
        xpTenths[skill] = Math.min(xpTenths[skill], MAX_XP_TENTHS - tenths)
                + tenths;
        return Level(skill) > before;
    }

    /** @return the skill's experience in tenths of a point */
    int XpTenths(int skill) { return xpTenths[skill]; }

    /** @return the skill's level, 1 to MAX_LEVEL */
    int Level(int skill) {
        int level = 1, xp = xpTenths[skill] / TENTHS;
        while (level < MAX_LEVEL && xp >= LEVEL_XP[level + 1]) level++;
        return level;
    }

    /**
     * @return the level the skill is at just now, which is what the
     *         player sees over Level(). Nothing raises or lowers a
     *         skill for a while yet, so the two are the same
     */
    int CurrentLevel(int skill) { return Level(skill); }

    /** @return experience in tenths written out, such as "17.5" */
    static String Format(int tenths) {
        return tenths / TENTHS + "." + tenths % TENTHS;
    }

    private static int[] BuildLevels() {
        int[] xp = new int[MAX_LEVEL + 1];
        double points = 0;

        for (int level = 1; level < MAX_LEVEL; level++) {
            points += Math.floor(level + 300.0 * Math.pow(2.0, level / 7.0));
            xp[level + 1] = (int) Math.floor(points / 4.0);
        }
        return xp;
    }
}
