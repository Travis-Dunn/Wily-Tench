package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.save.SaveData;

/**
 * Turns the player's state into a saved game and back, and notices
 * when it has changed.
 *
 * What is saved, under these names:
 *
 *   map         text    the map the character is on, such as "cave.txt"
 *   x, y        whole   the tile the character is on
 *   xp.mining   whole   experience in tenths of a point, one per skill
 *   inventory   bytes   two per slot, high byte first: the item's
 *                       number plus one, or zero for an empty slot
 *
 * The state of the map itself, such as which rocks are mined out, is
 * not saved yet.
 *
 * A saved value that makes no sense, because the file was edited or
 * the map has since been redrawn, is quietly replaced by something
 * safe: an unknown item by an empty slot, an impossible position by
 * the map's start.
 */
final class PlayerSave {
    private static final String KEY_MAP = "map";
    private static final String KEY_X = "x", KEY_Y = "y";
    private static final String KEY_XP_PREFIX = "xp.";
    private static final String KEY_INVENTORY = "inventory";

    private static final int BYTES_PER_SLOT = 2;
    private static final int NEVER = -1;

    private final String mapName;
    private final Player player;
    private final Skills skills;
    private final Inventory inventory;

    /* The state as last captured, for spotting changes. */
    private int savedX = NEVER, savedY, savedInventoryVersion;
    private final int[] savedXp = new int[Skills.COUNT];

    PlayerSave(String mapName, Player player, Skills skills,
               Inventory inventory) {
        this.mapName = mapName;
        this.player = player;
        this.skills = skills;
        this.inventory = inventory;
    }

    /**
     * @return whether anything saved has changed since the last
     *         Capture(), or there has not been one
     */
    boolean Changed() {
        if (savedX != player.X() || savedY != player.Y() ||
                savedInventoryVersion != inventory.Version()) return true;
        for (int i = 0; i < Skills.COUNT; i++)
            if (savedXp[i] != skills.XpTenths(i)) return true;
        return false;
    }

    /** @return the whole state, ready to hand to SaveGame.Queue() */
    SaveData Capture() {
        SaveData data = SaveData.Create();
        byte[] slots = new byte[Inventory.SLOTS * BYTES_PER_SLOT];
        int i;

        data.setString(KEY_MAP, mapName);
        data.setInt(KEY_X, savedX = player.X());
        data.setInt(KEY_Y, savedY = player.Y());

        for (i = 0; i < Skills.COUNT; i++) {
            data.setInt(KEY_XP_PREFIX + Skills.SaveKey(i),
                    savedXp[i] = skills.XpTenths(i));
        }

        for (i = 0; i < Inventory.SLOTS; i++) {
            int stored = inventory.At(i) + 1;
            slots[i * BYTES_PER_SLOT] = (byte) (stored >> 8);
            slots[i * BYTES_PER_SLOT + 1] = (byte) stored;
        }
        data.setBytes(KEY_INVENTORY, slots);
        savedInventoryVersion = inventory.Version();
        return data;
    }

    /**
     * Puts a loaded state into effect. Anything missing from it is
     * left as it is.
     */
    void Restore(SaveData data, World world) {
        int i;

        if (Is(data, KEY_MAP, SaveData.TYPE_STRING) &&
                mapName.equals(data.getString(KEY_MAP)) &&
                Is(data, KEY_X, SaveData.TYPE_INT) &&
                Is(data, KEY_Y, SaveData.TYPE_INT)) {
            int x = data.getInt(KEY_X), y = data.getInt(KEY_Y);
            if (!world.Solid(x, y)) player.Place(x, y);
        }

        for (i = 0; i < Skills.COUNT; i++) {
            String key = KEY_XP_PREFIX + Skills.SaveKey(i);
            if (Is(data, key, SaveData.TYPE_INT))
                skills.SetXpTenths(i, data.getInt(key));
        }

        if (Is(data, KEY_INVENTORY, SaveData.TYPE_BYTES)) {
            byte[] slots = data.getBytes(KEY_INVENTORY);
            int n = Math.min(slots.length / BYTES_PER_SLOT, Inventory.SLOTS);

            for (i = 0; i < n; i++) {
                int item = ((slots[i * BYTES_PER_SLOT] & 0xFF) << 8 |
                        (slots[i * BYTES_PER_SLOT + 1] & 0xFF)) - 1;
                inventory.Set(i, Item.Valid(item) ? item : Item.NONE);
            }
        }
    }

    private static boolean Is(SaveData data, String key, int type) {
        return data.getType(key) == type;
    }
}
