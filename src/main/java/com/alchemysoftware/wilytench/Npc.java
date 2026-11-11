package com.alchemysoftware.wilytench;

/**
 * The people of the game, as listed in data/npcs.json. Each has an
 * id, by which map tiles and scripts refer to it, a name, and what
 * examining it says.
 *
 * For now a person stands on one tile of a map, put there by a symbol
 * the map file defines ("!define H npc harl"), and does nothing but
 * be talked to: talking runs the script [opnpc,id]. Walking about, and
 * everything else a person might do, is still to come, and when it
 * does they will stop being part of the map.
 */
final class Npc {
    /** What talking to anyone is called in the hover label. */
    static final String VERB = "Talk-to";

    private static final String[] KEYS = {
        "id", "name", "examine", "sheet", "cell"
    };
    private static final String PLAIN = "Nothing remarkable.";

    private static Npc[] all = new Npc[0];

    /** What scripts and data files call it. An identifier. */
    final String id;
    /** What the player is shown. */
    final String name;
    /** What looking closely at it tells the player. */
    final String examine;
    /** What it looks like, standing. */
    final int picture;

    private Npc(String id, String name, String examine, int picture) {
        this.id = id;
        this.name = name;
        this.examine = examine;
        this.picture = picture;
    }

    /**
     * @param root the contents of data/npcs.json; Sprites must already
     *             be loaded
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "npcs" });

        DataNode[] nodes = root.Groups("npcs", "npc");
        Npc[] loaded = new Npc[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            String id = nodes[i].Str("id");
            DataNode node = nodes[i].Called("npcs.json, npc [" + id + "]");
            node.Only(KEYS);
            CheckIdentifier(node, id);
            for (int j = 0; j < i; j++) {
                if (loaded[j].id.equals(id))
                    throw node.Fail("there are two npcs with this id.");
            }
            loaded[i] = new Npc(id, node.Str("name"),
                    node.OptStr("examine", PLAIN), Sprites.Picture(node,
                    node.Str("sheet"), node.Ints("cell", 2)));
        }
        all = loaded;
    }

    static void Shutdown() { all = new Npc[0]; }

    /** @return the npc with that id, or null */
    static Npc Named(String id) {
        for (int i = 0; i < all.length; i++)
            if (all[i].id.equals(id)) return all[i];
        return null;
    }

    /* Ids become part of script trigger names, which allow only these
       characters. */
    private static void CheckIdentifier(DataNode node, String id) {
        boolean ok = id.length() > 0;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            ok &= (c >= 'a' && c <= 'z') || c == '_' ||
                    (i > 0 && c >= '0' && c <= '9');
        }
        if (!ok) {
            throw node.Fail("an id may use only lower-case letters, " +
                    "digits, and underscores, and may not begin with a " +
                    "digit.");
        }
    }
}
