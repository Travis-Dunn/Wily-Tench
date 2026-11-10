package com.alchemysoftware.wilytench;

/**
 * What the player is carrying: twenty-eight slots, each empty or
 * holding one item. Items do not stack.
 */
final class Inventory {
    static final int SLOTS = 28;

    private final int[] slots = new int[SLOTS];
    private int used;

    Inventory() {
        for (int i = 0; i < SLOTS; i++) slots[i] = Item.NONE;
    }

    /**
     * Puts an item in the first empty slot.
     *
     * @return false, changing nothing, if every slot is taken
     */
    boolean Add(int item) {
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] != Item.NONE) continue;
            slots[i] = item;
            used++;
            return true;
        }
        return false;
    }

    /**
     * Empties a slot.
     *
     * @return the item that was in it, or Item.NONE
     */
    int Remove(int slot) {
        int item = slots[slot];
        if (item != Item.NONE) used--;
        slots[slot] = Item.NONE;
        return item;
    }

    /** @return the item in a slot, or Item.NONE */
    int At(int slot) { return slots[slot]; }

    /** @return whether at least one of an item is carried */
    boolean Has(int item) {
        for (int i = 0; i < SLOTS; i++) if (slots[i] == item) return true;
        return false;
    }

    /** @return how many slots are taken */
    int Used() { return used; }

    boolean Full() { return used == SLOTS; }
}
