package com.alchemysoftware.wilytench;

import java.util.Random;

/**
 * The kinds of ore rock, and the rules for mining each.
 *
 * Every swing at a rock is a roll of the dice. The chance of success
 * rises steadily with Mining level, from the "level 1" figure to the
 * "level 99" figure, each out of 256.
 */
final class Ore {
    static final int NONE = 0;
    static final int COPPER = 1;
    static final int IRON = 2;

    private static final int ROLL_SIDES = 256;

    private static final String[] ROCK_NAMES = {
        null, "Copper rocks", "Iron rocks"
    };
    private static final int[] ITEMS = {
        Item.NONE, Item.COPPER_ORE, Item.IRON_ORE
    };
    private static final int[] LEVELS = { 0, 1, 15 };
    private static final int[] XP_TENTHS = { 0, 175, 350 };
    private static final int[] CHANCE_AT_1 = { 0, 100, 60 };
    private static final int[] CHANCE_AT_99 = { 0, 400, 300 };
    private static final int[] RESPAWN_TICKS = { 0, 4, 9 };
    private static final int[] COLOR_MAPS = {
        Sprites.COLOR_MAP_NONE, Sprites.COLOR_MAP_NONE,
        Sprites.COLOR_MAP_IRON
    };

    private Ore() {}

    static String RockName(int ore) { return ROCK_NAMES[ore]; }

    /** @return the item a successful swing yields */
    static int ItemOf(int ore) { return ITEMS[ore]; }

    /** @return the Mining level needed to try */
    static int Level(int ore) { return LEVELS[ore]; }

    /** @return the experience for one ore, in tenths of a point */
    static int XpTenths(int ore) { return XP_TENTHS[ore]; }

    /** @return game ticks until a mined-out rock has ore again */
    static int RespawnTicks(int ore) { return RESPAWN_TICKS[ore]; }

    /** @return the color map that paints the rock sprite as this ore */
    static int ColorMap(int ore) { return COLOR_MAPS[ore]; }

    /**
     * Rolls for one swing.
     *
     * @param level the player's Mining level
     * @return whether the swing wins an ore
     */
    static boolean Roll(int ore, int level, Random rng) {
        int span = Skills.MAX_LEVEL - 1;
        int chance = (CHANCE_AT_1[ore] * (Skills.MAX_LEVEL - level) +
                CHANCE_AT_99[ore] * (level - 1)) / span;
        return rng.nextInt(ROLL_SIDES) < chance;
    }
}
