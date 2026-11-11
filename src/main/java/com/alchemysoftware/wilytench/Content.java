package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.error.ErrorHandler;

/**
 * Loads everything in the "data" resource folder: the files that say
 * what the game contains, as opposed to how it works.
 *
 *   game.json      which maps and scripts make up the game, and what
 *                  a new player starts with
 *   palette.json   the colors
 *   sprites.json   the sprite sheets and color maps
 *   items.json     the things that can be carried
 *   skills.json    the skills
 *   interface.json the pictures on the interface's tabs and
 *                  widgets
 *   recipes.json   what can be made from what
 *   locs.json      the things on a map that can be used
 *   tiles.json     what each character in a map file stands for
 *
 * docs/content.md explains each file for someone editing it. A mistake
 * in any of them stops the game at startup with a message that names
 * the file, the entry, and what is wrong with it.
 */
final class Content {
    private static final String[] GAME_KEYS = {
        "maps", "scripts", "starting_items"
    };

    private static final String[] INTERFACE_KEYS = { "tabs", "widgets" };

    private static String[] maps, scripts;
    private static int[] startingItems;

    private Content() {}

    /**
     * Loads every data file. The sprite renderer must be initialized.
     *
     * @return false after a fatal error has been reported
     */
    static boolean Load() {
        try {
            LoadOrThrow();
            return true;
        } catch (DataError e) {
            ErrorHandler.LogFatalAndStop(ERR_STR_PREFIX + e.getMessage() +
                    "\n");
            return false;
        } catch (RuntimeException e) {
            /* A fault in the loader itself, not in the data. */
            ErrorHandler.LogFatalExcpAndStop(ERR_STR_EXCP, e);
            return false;
        }
    }

    /**
     * Loads every data file, in the order their references to one
     * another require.
     *
     * @throws DataError describing the first mistake found
     */
    static void LoadOrThrow() {
        Palette.Load(DataNode.Load("palette.json"));
        Sprites.Load(DataNode.Load("sprites.json"));
        Item.Load(DataNode.Load("items.json"));
        Skills.Load(DataNode.Load("skills.json"));
        DataNode ui = DataNode.Load("interface.json");
        ui.Only(INTERFACE_KEYS);
        SidePanel.Load(ui);
        Skin.Load(ui.Obj("widgets"));
        Recipes.Load(DataNode.Load("recipes.json"));
        Loc.Load(DataNode.Load("locs.json"));
        Tiles.Load(DataNode.Load("tiles.json"));

        DataNode game = DataNode.Load("game.json");
        game.Only(GAME_KEYS);
        maps = game.Strs("maps");
        scripts = game.Strs("scripts");
        if (maps.length == 0) throw game.Fail("[maps] lists no maps.");

        String[] starting = game.Strs("starting_items");
        startingItems = new int[starting.length];
        for (int i = 0; i < starting.length; i++) {
            startingItems[i] = Item.Named(starting[i]);
            if (startingItems[i] == Item.NONE) {
                throw game.Fail("[starting_items] names the item [" +
                        starting[i] + "], and there is no such item in " +
                        "items.json.");
            }
        }
        if (starting.length > Inventory.SLOTS) {
            throw game.Fail("[starting_items] lists more items than the " +
                    "inventory's " + Inventory.SLOTS + " slots.");
        }
    }

    static void Shutdown() {
        Tiles.Shutdown();
        Loc.Shutdown();
        Recipes.Shutdown();
        Skin.Shutdown();
        SidePanel.Shutdown();
        Skills.Shutdown();
        Item.Shutdown();
        Sprites.Shutdown();
        Palette.Shutdown();
        maps = scripts = null;
        startingItems = null;
    }

    /** @return the game's map files; a new game begins on the first */
    static String[] Maps() { return maps; }

    /** @return the game's script files */
    static String[] ScriptFiles() { return scripts; }

    /** @return the items a new player begins with */
    static int[] StartingItems() { return startingItems; }

    private static final String ERR_STR_EXCP = "The game failed to " +
            "start because an exception was encountered while reading " +
            "its data files.\n";
    private static final String ERR_STR_PREFIX = "The game failed to " +
            "start because of a mistake in one of its data files. ";
}
