package com.alchemysoftware.wilytench;

/**
 * The lists of things that can be made, as written in
 * data/recipes.json.
 *
 * A "make list" is what one kind of material can be turned into at
 * one kind of place: what a bronze bar can become at an anvil. It has
 * an id, a title to head the menu with, the skill it belongs to,
 * the material, and its recipes in the order the menu shows them. A
 * recipe is the item made, how many of the material it uses up, the
 * level needed, and the experience earned.
 *
 * This class only holds the lists. A script decides when one is shown
 * and what choosing from it does, with the make_... commands in
 * Scripts; MakeMenu draws it.
 *
 * Inside the game a list is a small number, its place in the file,
 * and a recipe is its place in its list.
 */
final class Recipes {
    static final int NONE = -1;

    private static final String[] LIST_KEYS = {
        "id", "title", "skill", "material", "recipes"
    };
    private static final String[] RECIPE_KEYS = {
        "makes", "uses", "level", "xp"
    };

    private static String[] ids = new String[0];
    private static String[] titles;
    private static int[] skills, materials;
    private static int[][] products, uses, levels, xpTenths;
    /* "3" and "Lv 11", written out once for MakeMenu to draw. */
    private static String[][] usesTexts, levelTexts;

    private Recipes() {}

    /**
     * @param root the contents of data/recipes.json; Item and Skills
     *             must already be loaded
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "lists" });

        DataNode[] nodes = root.Groups("lists", "list");
        int n = nodes.length;
        String[] newIds = new String[n];

        titles = new String[n];
        skills = new int[n];
        materials = new int[n];
        products = new int[n][];
        uses = new int[n][];
        levels = new int[n][];
        xpTenths = new int[n][];
        usesTexts = new String[n][];
        levelTexts = new String[n][];

        for (int i = 0; i < n; i++) {
            String id = nodes[i].Str("id");
            DataNode node = nodes[i].Called("recipes.json, list [" + id +
                    "]");
            node.Only(LIST_KEYS);
            for (int j = 0; j < i; j++) {
                if (newIds[j].equals(id))
                    throw node.Fail("there are two lists with this id.");
            }

            newIds[i] = id;
            titles[i] = node.Str("title");
            skills[i] = Skills.Named(node.Str("skill"));
            if (skills[i] < 0) {
                throw node.Fail("[skill] names the skill [" +
                        node.Str("skill") + "], and there is no such " +
                        "skill in skills.json.");
            }
            materials[i] = ItemNamed(node, "material");
            LoadRecipes(i, id, node.Groups("recipes", "recipe"), node);
        }
        ids = newIds;
    }

    static void Shutdown() { ids = new String[0]; }

    /** @return the list with that id, or NONE */
    static int Named(String id) {
        for (int i = 0; i < ids.length; i++)
            if (ids[i].equals(id)) return i;
        return NONE;
    }

    /** @return how many lists there are */
    static int Lists() { return ids.length; }

    static String Id(int list) { return ids[list]; }

    /** @return the words that head the list's menu */
    static String Title(int list) { return titles[list]; }

    /** @return the skill whose level the list's recipes need */
    static int Skill(int list) { return skills[list]; }

    /** @return the item every recipe of the list uses up */
    static int Material(int list) { return materials[list]; }

    /** @return how many recipes the list has */
    static int Count(int list) { return products[list].length; }

    /** @return the item a recipe makes */
    static int Product(int list, int recipe) {
        return products[list][recipe];
    }

    /** @return how many of the material a recipe uses up */
    static int Uses(int list, int recipe) { return uses[list][recipe]; }

    /** @return the level a recipe needs */
    static int Level(int list, int recipe) { return levels[list][recipe]; }

    /** @return the experience a recipe earns, in tenths of a point */
    static int XpTenths(int list, int recipe) {
        return xpTenths[list][recipe];
    }

    /** @return Uses() written out: "3" */
    static String UsesText(int list, int recipe) {
        return usesTexts[list][recipe];
    }

    /** @return Level() written out: "Lv 11" */
    static String LevelText(int list, int recipe) {
        return levelTexts[list][recipe];
    }

    private static void LoadRecipes(int list, String id, DataNode[] nodes,
                                    DataNode from) {
        int n = nodes.length;
        if (n == 0) throw from.Fail("[recipes] is empty.");

        products[list] = new int[n];
        uses[list] = new int[n];
        levels[list] = new int[n];
        xpTenths[list] = new int[n];
        usesTexts[list] = new String[n];
        levelTexts[list] = new String[n];

        for (int i = 0; i < n; i++) {
            DataNode node = nodes[i].Called("recipes.json, list [" + id +
                    "], recipe [" + nodes[i].Str("makes") + "]");
            node.Only(RECIPE_KEYS);

            int count = node.Int("uses"), level = node.Int("level");
            if (count < 1 || count > Inventory.SLOTS) {
                throw node.Fail("[uses] must be from 1 to " +
                        Inventory.SLOTS + ".");
            }
            if (level < 1 || level > Skills.MAX_LEVEL) {
                throw node.Fail("[level] must be from 1 to " +
                        Skills.MAX_LEVEL + ".");
            }

            products[list][i] = ItemNamed(node, "makes");
            uses[list][i] = count;
            levels[list][i] = level;
            xpTenths[list][i] = node.Tenths("xp");
            usesTexts[list][i] = String.valueOf(count);
            levelTexts[list][i] = LEVEL + level;
        }
    }

    private static int ItemNamed(DataNode node, String key) {
        int item = Item.Named(node.Str(key));
        if (item == Item.NONE) {
            throw node.Fail("[" + key + "] names the item [" +
                    node.Str(key) + "], and there is no such item in " +
                    "items.json.");
        }
        return item;
    }

    private static final String LEVEL = "Lv ";
}
