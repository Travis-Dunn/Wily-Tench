package com.alchemysoftware.wilytench;

/**
 * What can be done to each thing the player can point at, and the
 * doing of it.
 *
 * ForTile() and ForItem() fill a ContextMenu's list with the options
 * for a tile of the map or an inventory slot. The order matters: the
 * first option is the one a plain left click takes. Do() carries out
 * an option from such a list.
 *
 * A tile of the map offers, in this order:
 *
 *   the loc's own verb     "Mine Copper rocks", if a loc with a script
 *                          stands there
 *   Walk here              always
 *   Examine                if anything stands there
 *   Cancel                 always
 *
 * An inventory slot holding an item offers Use, Drop, Examine, and
 * Cancel.
 */
final class Actions {
    /** Walks to the tile in the option's two values. */
    static final int WALK = 1;
    /** Uses the loc on that tile. */
    static final int USE_LOC = 2;
    static final int EXAMINE_TILE = 3;
    /** Uses the item in the slot in the option's first value. */
    static final int USE_ITEM = 4;
    static final int DROP_ITEM = 5;
    static final int EXAMINE_ITEM = 6;
    static final int CANCEL = 0;

    private static final String VERB_WALK = "Walk here";
    private static final String VERB_EXAMINE = "Examine";
    private static final String VERB_USE = "Use";
    private static final String VERB_DROP = "Drop";
    private static final String VERB_CANCEL = "Cancel";
    private static final String MSG_CANT_REACH = "You can't reach that.";

    private final Session session;

    Actions(Session session) { this.session = session; }

    /** Adds the options for a tile of the map the player is on. */
    void ForTile(ContextMenu menu, int tileX, int tileY) {
        World world = session.Here();
        String name = world.Name(tileX, tileY);

        if (session.Usable(tileX, tileY)) {
            menu.Add(world.LocAt(tileX, tileY).verb, name, Palette.MINT,
                    USE_LOC, tileX, tileY);
        }
        menu.Add(VERB_WALK, null, 0, WALK, tileX, tileY);
        if (name != null) {
            menu.Add(VERB_EXAMINE, name, Palette.MINT, EXAMINE_TILE, tileX,
                    tileY);
        }
        menu.Add(VERB_CANCEL, null, 0, CANCEL, 0, 0);
    }

    /**
     * Adds the options for an inventory slot; none if it is empty.
     */
    void ForItem(ContextMenu menu, int slot) {
        int item = session.inventory.At(slot);
        if (item == Item.NONE) return;
        String name = Item.Name(item);

        menu.Add(VERB_USE, name, Palette.PEACH, USE_ITEM, slot, 0);
        menu.Add(VERB_DROP, name, Palette.PEACH, DROP_ITEM, slot, 0);
        menu.Add(VERB_EXAMINE, name, Palette.PEACH, EXAMINE_ITEM, slot, 0);
        menu.Add(VERB_CANCEL, null, 0, CANCEL, 0, 0);
    }

    /** Carries out an option of a list this class filled. */
    void Do(ContextMenu menu, int option) {
        int a = menu.ArgA(option), b = menu.ArgB(option);

        switch (menu.Action(option)) {
            case WALK:
                if (!session.WalkTo(a, b))
                    session.chat.Add(MSG_CANT_REACH, Palette.BONE);
                break;
            case USE_LOC:
                session.Use(a, b);
                break;
            case EXAMINE_TILE:
                session.ExamineTile(a, b);
                break;
            case USE_ITEM:
                session.UseItem(a);
                break;
            case DROP_ITEM:
                session.DropItem(a);
                break;
            case EXAMINE_ITEM:
                session.ExamineItem(a);
                break;
            default:
                break;
        }
    }
}
