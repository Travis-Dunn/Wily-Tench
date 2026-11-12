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
 *                          stands there; or "Talk-to Harl", if a
 *                          person with one does
 *   Walk here              always
 *   Examine                if anything stands there
 *   Cancel                 always
 *
 * An inventory slot holding an item offers Use, Drop, Examine, and
 * Cancel.
 *
 * Use picks the item up, so to speak: it becomes the selected item,
 * and from then on whatever is pointed at offers, first of all, to
 * have that item used on it: "Use Copper ore -> Furnace". Doing
 * anything at all lets go of the selected item, as does its leaving
 * the slot it was in.
 */
final class Actions {
    /** Walks to the tile in the option's two values. */
    static final int WALK = 1;
    /** Uses the loc on that tile, or talks to the person there. */
    static final int USE_LOC = 2;
    static final int EXAMINE_TILE = 3;
    /** Selects the item in the slot in the option's first value. */
    static final int USE_ITEM = 4;
    static final int DROP_ITEM = 5;
    static final int EXAMINE_ITEM = 6;
    /** Uses the selected item on the loc on that tile. */
    static final int USE_ITEM_ON_LOC = 7;
    /** Uses the selected item on the item in that slot. */
    static final int USE_ITEM_ON_ITEM = 8;
    static final int CANCEL = 0;

    static final int NO_SLOT = -1;

    private static final String VERB_WALK = "Walk here";
    private static final String VERB_EXAMINE = "Examine";
    private static final String VERB_USE = "Use";
    private static final String VERB_DROP = "Drop";
    private static final String VERB_CANCEL = "Cancel";
    private static final String MSG_CANT_REACH = "You can't reach that.";

    private static final String ONTO = " -> ";

    private final Session session;

    private int selectedSlot = NO_SLOT, selectedItem = Item.NONE;

    /* "Copper ore -> Furnace" as last written out, and what it was
       written for, so that pointing at one thing frame after frame
       builds the words once. */
    private int ontoItem = Item.NONE;
    private String ontoTarget, ontoText;

    Actions(Session session) { this.session = session; }

    /**
     * @return the inventory slot of the selected item, or NO_SLOT if
     *         no item is selected
     */
    int SelectedSlot() {
        if (selectedSlot != NO_SLOT &&
                session.inventory.At(selectedSlot) != selectedItem)
            Deselect();
        return selectedSlot;
    }

    /** Lets go of the selected item, if there is one. */
    void Deselect() {
        selectedSlot = NO_SLOT;
        selectedItem = Item.NONE;
    }

    /** Adds the options for a tile of the map the player is on. */
    void ForTile(ContextMenu menu, int tileX, int tileY) {
        World world = session.Here();
        String name = world.Name(tileX, tileY);

        if (SelectedSlot() != NO_SLOT && world.LocAt(tileX, tileY) != null) {
            menu.Add(VERB_USE, Onto(name), Palette.PEACH, USE_ITEM_ON_LOC,
                    tileX, tileY);
        }
        if (session.Usable(tileX, tileY)) {
            Loc loc = world.LocAt(tileX, tileY);
            menu.Add(loc != null ? loc.verb : Npc.VERB, name,
                    loc != null ? Palette.MINT : Palette.AMBER, USE_LOC,
                    tileX, tileY);
        }
        menu.Add(VERB_WALK, null, 0, WALK, tileX, tileY);
        if (name != null) {
            menu.Add(VERB_EXAMINE, name,
                    world.NpcAt(tileX, tileY) != null ? Palette.AMBER :
                    Palette.MINT, EXAMINE_TILE, tileX, tileY);
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

        if (SelectedSlot() != NO_SLOT && selectedSlot != slot) {
            menu.Add(VERB_USE, Onto(name), Palette.PEACH, USE_ITEM_ON_ITEM,
                    slot, 0);
        }
        menu.Add(VERB_USE, name, Palette.PEACH, USE_ITEM, slot, 0);
        menu.Add(VERB_DROP, name, Palette.PEACH, DROP_ITEM, slot, 0);
        menu.Add(VERB_EXAMINE, name, Palette.PEACH, EXAMINE_ITEM, slot, 0);
        menu.Add(VERB_CANCEL, null, 0, CANCEL, 0, 0);
    }

    /** Carries out an option of a list this class filled. */
    void Do(ContextMenu menu, int option) {
        int a = menu.ArgA(option), b = menu.ArgB(option);
        int selected = SelectedSlot();

        Deselect();
        switch (menu.Action(option)) {
            case WALK:
                if (!session.WalkTo(a, b))
                    session.chat.Add(MSG_CANT_REACH, Palette.TEXT);
                break;
            case USE_LOC:
                session.Use(a, b);
                break;
            case EXAMINE_TILE:
                session.ExamineTile(a, b);
                break;
            case USE_ITEM:
                selectedSlot = a;
                selectedItem = session.inventory.At(a);
                break;
            case USE_ITEM_ON_LOC:
                if (selected != NO_SLOT) session.UseItemOn(selected, a, b);
                break;
            case USE_ITEM_ON_ITEM:
                if (selected != NO_SLOT) session.UseItemOnItem(selected, a);
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

    /* The selected item's name, an arrow, and what it would go on. */
    private String Onto(String target) {
        if (ontoItem != selectedItem || ontoTarget != target) {
            ontoItem = selectedItem;
            ontoTarget = target;
            ontoText = Item.Name(selectedItem) + ONTO + target;
        }
        return ontoText;
    }
}
