package com.alchemysoftware.wilytench;

/**
 * The kinds of thing that can be carried, as listed in
 * data/items.json. Each has an id, by which data files, scripts, and
 * saved games refer to it; a name to show the player; and an icon.
 *
 * Inside the game an item is a small number, good only for the length
 * of one run: its place in the file.
 *
 * Saved games hold ids, so an id cannot simply be changed once
 * players may have the item. To rename one, give the item its new id
 * and list the old one under "was"; anything that still uses the old
 * id, such as an existing saved game, finds the item by it.
 */
final class Item {
    static final int NONE = -1;

    private static final String[] KEYS = {
        "id", "was", "name", "sheet", "cell", "color_map"
    };

    private static String[] ids = new String[0];
    /* Former ids, and the item each now belongs to. */
    private static String[] oldIds = new String[0];
    private static int[] oldIdItems = new int[0];
    private static String[] names, lowerNames;
    private static int[] pictures, colorMaps;

    private Item() {}

    /**
     * @param root the contents of data/items.json; Sprites must
     *             already be loaded
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "items" });

        DataNode[] nodes = root.Groups("items", "item");
        int n = nodes.length;
        String[] newIds = new String[n];

        names = new String[n];
        lowerNames = new String[n];
        pictures = new int[n];
        colorMaps = new int[n];

        for (int i = 0; i < n; i++) {
            String id = nodes[i].Str("id");
            DataNode node = nodes[i].Called("items.json, item [" + id + "]");
            node.Only(KEYS);
            for (int j = 0; j < i; j++) {
                if (newIds[j].equals(id))
                    throw node.Fail("there are two items with this id.");
            }

            newIds[i] = id;
            names[i] = node.Str("name");
            lowerNames[i] = names[i].toLowerCase();
            pictures[i] = Sprites.Picture(node, node.Str("sheet"),
                    node.Ints("cell", 2));
            colorMaps[i] = node.Has("color_map") ?
                    Sprites.ColorMapNamed(node, node.Str("color_map")) :
                    Sprites.COLOR_MAP_NONE;
        }

        int olds = 0, at = 0;
        for (int i = 0; i < n; i++)
            if (nodes[i].Has("was")) olds += nodes[i].Strs("was").length;
        String[] newOldIds = new String[olds];
        int[] newOldIdItems = new int[olds];

        for (int i = 0; i < n; i++) {
            String[] was = nodes[i].Has("was") ?
                    nodes[i].Strs("was") : new String[0];

            for (int j = 0; j < was.length; j++) {
                boolean taken = false;
                for (int k = 0; k < n; k++) taken |= newIds[k].equals(was[j]);
                for (int k = 0; k < at; k++)
                    taken |= newOldIds[k].equals(was[j]);
                if (taken) {
                    throw nodes[i].Called("items.json, item [" + newIds[i] +
                            "]").Fail("[was] lists the id [" + was[j] +
                            "], which belongs to another item or is " +
                            "listed twice.");
                }
                newOldIds[at] = was[j];
                newOldIdItems[at++] = i;
            }
        }

        ids = newIds;
        oldIds = newOldIds;
        oldIdItems = newOldIdItems;
    }

    static void Shutdown() {
        ids = new String[0];
        oldIds = new String[0];
    }

    /** @return how many kinds of item there are */
    static int Count() { return ids.length; }

    /** @return whether a number is an item's */
    static boolean Valid(int item) { return item >= 0 && item < ids.length; }

    /**
     * @return the item with that id, or that once had it, or NONE if
     *         there is none
     */
    static int Named(String id) {
        int i;
        for (i = 0; i < ids.length; i++) if (ids[i].equals(id)) return i;
        for (i = 0; i < oldIds.length; i++)
            if (oldIds[i].equals(id)) return oldIdItems[i];
        return NONE;
    }

    /** @return the item's id */
    static String Id(int item) { return ids[item]; }

    /** @return the name as it heads a label: "Copper ore" */
    static String Name(int item) { return names[item]; }

    /** @return the name as it reads mid-sentence: "copper ore" */
    static String LowerName(int item) { return lowerNames[item]; }

    /** Draws the item's 16 pixel icon with its top left at (x, y). */
    static void DrawIcon(int item, int x, int y) {
        Sprites.Draw(pictures[item], colorMaps[item], x, y);
    }
}
