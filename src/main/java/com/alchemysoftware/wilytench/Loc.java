package com.alchemysoftware.wilytench;

/**
 * A kind of thing on a map that the player can do something to: a
 * ladder, a kind of ore rock. ("Loc" is RuneScape's word for it.)
 *
 * What using one does is decided by a script. The game looks for a
 * script for the loc itself, [oploc,copper_rocks], and failing that
 * for one for its category, [oploc,_ore_rock], so that every loc of a
 * category behaves alike without a script apiece.
 *
 * A loc carries named values, its "parameters", for such a shared
 * script to read: which ore, how much experience. Adding another
 * ordinary ore rock means adding one entry to ALL below and a symbol
 * for it in World; no script changes.
 *
 * Parameters of the "ore_rock" category:
 *
 *   ore           text   the item a successful swing yields
 *   level         whole  the Mining level needed to try
 *   xp            whole  experience per ore, in tenths of a point
 *   chance_at_1   whole  chance of a swing succeeding at level 1 and
 *   chance_at_99  whole  at level 99, each out of 256; levels between
 *                        fall evenly between
 *   respawn       whole  game ticks a mined-out rock stays empty
 */
final class Loc {
    static final String CATEGORY_ORE_ROCK = "ore_rock";

    static final Loc LADDER = new Loc("ladder", "Ladder", "Climb down",
            null, null, Sprites.COLOR_MAP_NONE, null, null);
    static final Loc STAIRS = new Loc("stairs", "Stairs", "Climb", null,
            null, Sprites.COLOR_MAP_NONE, null, null);

    static final Loc COPPER_ROCKS = OreRock("copper_rocks", "Copper rocks",
            Sprites.COLOR_MAP_NONE, "copper_ore", 1, 175, 100, 400, 4);
    static final Loc IRON_ROCKS = OreRock("iron_rocks", "Iron rocks",
            Sprites.COLOR_MAP_IRON, "iron_ore", 15, 350, 60, 300, 9);

    private static final Loc[] ALL = {
        LADDER, STAIRS, COPPER_ROCKS, IRON_ROCKS
    };

    /** What scripts call it: the X of [oploc,X]. An identifier. */
    final String id;
    /** What the player is shown. */
    final String name;
    /** What using it is called in the hover label: "Mine", "Climb". */
    final String verb;
    /** The category it shares a script with, or null. */
    final String category;
    /**
     * What it is called while emptied, or null if it is a kind of
     * thing that cannot be emptied.
     */
    final String emptyName;
    /** The color map its sprite is drawn through. */
    final int colorMap;

    private final String[] keys;
    private final Object[] values;

    private Loc(String id, String name, String verb, String category,
                String emptyName, int colorMap, String[] keys,
                Object[] values) {
        this.id = id;
        this.name = name;
        this.verb = verb;
        this.category = category;
        this.emptyName = emptyName;
        this.colorMap = colorMap;
        this.keys = keys == null ? new String[0] : keys;
        this.values = values;
    }

    /** @return the loc scripts call id, or null if there is none */
    static Loc Named(String id) {
        for (int i = 0; i < ALL.length; i++)
            if (ALL[i].id.equals(id)) return ALL[i];
        return null;
    }

    /** @return the whole-number parameter, or null if it has none */
    Integer Int(String key) {
        Object v = Value(key);
        return v instanceof Integer ? (Integer) v : null;
    }

    /** @return the text parameter, or null if it has none */
    String Str(String key) {
        Object v = Value(key);
        return v instanceof String ? (String) v : null;
    }

    private Object Value(String key) {
        for (int i = 0; i < keys.length; i++)
            if (keys[i].equals(key)) return values[i];
        return null;
    }

    private static Loc OreRock(String id, String name, int colorMap,
                               String ore, int level, int xpTenths,
                               int chanceAt1, int chanceAt99,
                               int respawnTicks) {
        return new Loc(id, name, "Mine", CATEGORY_ORE_ROCK, "Rocks",
                colorMap,
                new String[] {
                    "ore", "level", "xp", "chance_at_1", "chance_at_99",
                    "respawn"
                },
                new Object[] {
                    ore, Integer.valueOf(level), Integer.valueOf(xpTenths),
                    Integer.valueOf(chanceAt1), Integer.valueOf(chanceAt99),
                    Integer.valueOf(respawnTicks)
                });
    }
}
