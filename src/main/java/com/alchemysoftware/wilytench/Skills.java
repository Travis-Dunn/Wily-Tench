package com.alchemysoftware.wilytench;

/**
 * The player's skills: how much experience each has earned and the
 * level that comes to.
 *
 * Experience is counted in tenths of a point, so that awards such as
 * 17.5 stay exact. Levels run from 1 to 99 and each takes about a tenth
 * more experience than the one before, the same curve RuneScape uses:
 * level 2 at 83 points, level 50 at about a hundred thousand, level 99
 * at about thirteen million.
 */
final class Skills {
    static final int MINING = 0;
    static final int COUNT = 1;

    static final int MAX_LEVEL = 99;

    private static final String[] NAMES = { "Mining" };

    private static final int TENTHS = 10;
    private static final int MAX_XP_TENTHS = 2000000000;

    /* LEVEL_XP[n] is the whole points needed to reach level n. */
    private static final int[] LEVEL_XP = BuildLevels();

    private final int[] xpTenths = new int[COUNT];

    static String Name(int skill) { return NAMES[skill]; }

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
