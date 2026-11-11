package com.alchemysoftware.wilytench;

/**
 * A kind of thing on a map that the player can do something to: a
 * ladder, a kind of ore rock. ("Loc" is RuneScape's word for it.) They
 * are listed in data/locs.json.
 *
 * What using one does is decided by a script. The game looks for a
 * script for the loc itself, [oploc,copper_rocks], and failing that
 * for one for its category, [oploc,_ore_rock], so that every loc of a
 * category behaves alike without a script apiece.
 *
 * A loc carries named values, its "parameters", for such a shared
 * script to read: which ore, how much experience. Its category says
 * which parameters it must have and what kind each is, and the file is
 * checked against that when the game starts:
 *
 *   "whole"   a whole number
 *   "tenths"  a number with at most one decimal place, such as 17.5;
 *             scripts receive it multiplied by ten
 *   "text"    any text
 *   "item"    the id of an item in items.json
 */
final class Loc {
    private static final String KIND_WHOLE = "whole";
    private static final String KIND_TENTHS = "tenths";
    private static final String KIND_TEXT = "text";
    private static final String KIND_ITEM = "item";
    private static final String KINDS = "whole, tenths, text, item";

    private static final String[] CATEGORY_KEYS = {
        "id", "verb", "empty_name", "empty_examine", "params"
    };
    private static final String[] LOC_KEYS = {
        "id", "name", "verb", "examine", "category", "params"
    };
    private static final String PLAIN = "Nothing remarkable.";

    private static Loc[] all = new Loc[0];

    /** What scripts and data files call it. An identifier. */
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
    /** What looking closely at it tells the player. */
    final String examine;
    /** The same while it is emptied, if it can be. */
    final String emptyExamine;

    private final String[] keys;
    private final Object[] values;

    private Loc(String id, String name, String verb, String category,
                String emptyName, String examine, String emptyExamine,
                String[] keys, Object[] values) {
        this.examine = examine;
        this.emptyExamine = emptyExamine;
        this.id = id;
        this.name = name;
        this.verb = verb;
        this.category = category;
        this.emptyName = emptyName;
        this.keys = keys;
        this.values = values;
    }

    /**
     * @param root the contents of data/locs.json; Item must already be
     *             loaded
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "categories", "locs" });

        DataNode[] categories = root.OptGroups("categories", "category");
        String[] categoryIds = new String[categories.length];
        int i, j;

        for (i = 0; i < categories.length; i++) {
            categoryIds[i] = categories[i].Str("id");
            categories[i] = categories[i].Called("locs.json, category [" +
                    categoryIds[i] + "]");
            categories[i].Only(CATEGORY_KEYS);
            CheckIdentifier(categories[i], categoryIds[i]);
            for (j = 0; j < i; j++) {
                if (categoryIds[j].equals(categoryIds[i])) {
                    throw categories[i].Fail(
                            "there are two categories with this id.");
                }
            }
        }

        DataNode[] nodes = root.Groups("locs", "loc");
        Loc[] locs = new Loc[nodes.length];

        for (i = 0; i < nodes.length; i++) {
            String id = nodes[i].Str("id");
            DataNode node = nodes[i].Called("locs.json, loc [" + id + "]");
            node.Only(LOC_KEYS);
            CheckIdentifier(node, id);
            for (j = 0; j < i; j++) {
                if (locs[j].id.equals(id))
                    throw node.Fail("there are two locs with this id.");
            }

            String category = node.OptStr("category", null);
            DataNode spec = null;
            for (j = 0; category != null && j < categories.length; j++)
                if (categoryIds[j].equals(category)) spec = categories[j];
            if (category != null && spec == null) {
                throw node.Fail("there is no category called [" + category +
                        "].");
            }

            String verb = node.OptStr("verb",
                    spec == null ? null : spec.OptStr("verb", null));
            if (verb == null) {
                throw node.Fail("neither it nor its category has a " +
                        "[verb], the word for using it, such as " +
                        "\"Climb\".");
            }

            String[] keys = new String[0];
            Object[] values = new Object[0];
            if (spec != null && spec.Has("params")) {
                DataNode kinds = spec.Obj("params");
                DataNode params = node.Obj("params");
                keys = kinds.Keys();
                values = new Object[keys.length];
                params.Only(keys);
                for (j = 0; j < keys.length; j++)
                    values[j] = Read(params, keys[j], kinds.Str(keys[j]));
            } else if (node.Has("params")) {
                throw node.Fail("it has [params], but " + (spec == null ?
                        "no category" : "its category lists none") +
                        " to say what they should be.");
            }

            locs[i] = new Loc(id, node.Str("name"), verb, category,
                    spec == null ? null : spec.OptStr("empty_name", null),
                    node.OptStr("examine", PLAIN),
                    spec == null ? PLAIN :
                    spec.OptStr("empty_examine", PLAIN), keys, values);
        }
        all = locs;
    }

    static void Shutdown() { all = new Loc[0]; }

    /** @return the loc called id, or null if there is none */
    static Loc Named(String id) {
        for (int i = 0; i < all.length; i++)
            if (all[i].id.equals(id)) return all[i];
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

    private static Object Read(DataNode params, String key, String kind) {
        if (kind.equals(KIND_WHOLE)) return Integer.valueOf(params.Int(key));
        if (kind.equals(KIND_TENTHS))
            return Integer.valueOf(params.Tenths(key));
        if (kind.equals(KIND_TEXT)) return params.Str(key);
        if (kind.equals(KIND_ITEM)) {
            String item = params.Str(key);
            if (Item.Named(item) == Item.NONE) {
                throw params.Fail("[" + key + "] names the item [" + item +
                        "], and there is no such item in items.json.");
            }
            return item;
        }
        throw params.Fail("its category gives [" + key + "] the kind [" +
                kind + "], which is not one of: " + KINDS + ".");
    }

    /* Ids become part of script trigger names, which allow only these
       characters. */
    private static void CheckIdentifier(DataNode node, String id) {
        boolean ok = true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            ok &= (c >= 'a' && c <= 'z') || c == '_' ||
                    (i > 0 && c >= '0' && c <= '9');
        }
        if (!ok) {
            throw node.Fail("an id may use only lower-case letters, " +
                    "digits, and underscores, and may not start with a " +
                    "digit.");
        }
    }
}
