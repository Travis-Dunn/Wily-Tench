package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.save.SaveData;

/**
 * Turns a Session into a saved game and back, and notices when it has
 * changed.
 *
 * What is saved, under these names:
 *
 *   map            text    the file name of the map the character is
 *                          on, such as "cave.txt"
 *   x, y           whole   the tile the character is on
 *   tick           whole   game ticks since the game was begun
 *   xp.mining      whole   experience in tenths of a point; one such
 *                          value per skill
 *   inventory      bytes   two per slot, high byte first: the item's
 *                          number plus one, or zero for an empty slot
 *   rocks.cave.txt bytes   one such value per map, named after its
 *                          file. Eight bytes per mined-out rock, high
 *                          bytes first: column (2), row (2), and the
 *                          game tick at which it refills (4)
 *
 * A saved value that makes no sense, because the file was edited or a
 * map has since been redrawn, is quietly replaced by something safe:
 * an unknown item by an empty slot, an impossible position by the
 * map's start, a rock that is no longer there by nothing.
 */
final class SaveState {
    private static final String KEY_MAP = "map";
    private static final String KEY_X = "x", KEY_Y = "y";
    private static final String KEY_TICK = "tick";
    private static final String KEY_XP_PREFIX = "xp.";
    private static final String KEY_INVENTORY = "inventory";
    private static final String KEY_ROCKS_PREFIX = "rocks.";

    private static final int BYTES_PER_SLOT = 2;
    private static final int BYTES_PER_ROCK = 8;
    private static final int NEVER = -1;

    private final Session session;

    /* The state as last captured, for spotting changes. */
    private int savedMap = NEVER, savedX, savedY, savedInventoryVersion;
    private final int[] savedXp = new int[Skills.COUNT];
    private final int[] savedWorldVersions;

    SaveState(Session session) {
        this.session = session;
        savedWorldVersions = new int[session.worlds.length];
    }

    /**
     * @return whether anything saved has changed since the last
     *         Capture(), or there has not been one. While any rock is
     *         waiting to refill, every tick counts as a change, because
     *         the tick count is what the wait is measured against.
     */
    boolean Changed() {
        Player player = session.player;
        int i;

        if (savedMap != session.Map() || savedX != player.X() ||
                savedY != player.Y() ||
                savedInventoryVersion != session.inventory.Version())
            return true;
        for (i = 0; i < Skills.COUNT; i++)
            if (savedXp[i] != session.skills.XpTenths(i)) return true;
        for (i = 0; i < session.worlds.length; i++) {
            World world = session.worlds[i];
            if (savedWorldVersions[i] != world.Version() ||
                    PendingRocks(world) > 0) return true;
        }
        return false;
    }

    /** @return the whole state, ready to hand to SaveGame.Queue() */
    SaveData Capture() {
        SaveData data = SaveData.Create();
        byte[] slots = new byte[Inventory.SLOTS * BYTES_PER_SLOT];
        int i;

        data.setString(KEY_MAP, session.Here().id);
        data.setInt(KEY_X, savedX = session.player.X());
        data.setInt(KEY_Y, savedY = session.player.Y());
        data.setInt(KEY_TICK, session.Tick());
        savedMap = session.Map();

        for (i = 0; i < Skills.COUNT; i++) {
            data.setInt(KEY_XP_PREFIX + Skills.SaveKey(i),
                    savedXp[i] = session.skills.XpTenths(i));
        }

        for (i = 0; i < Inventory.SLOTS; i++)
            PutShort(slots, i * BYTES_PER_SLOT, session.inventory.At(i) + 1);
        data.setBytes(KEY_INVENTORY, slots);
        savedInventoryVersion = session.inventory.Version();

        for (i = 0; i < session.worlds.length; i++) {
            World world = session.worlds[i];
            data.setBytes(KEY_ROCKS_PREFIX + world.id, CaptureRocks(world));
            savedWorldVersions[i] = world.Version();
        }
        return data;
    }

    /**
     * Puts a loaded state into effect. Anything missing from it is
     * left as it is.
     */
    void Restore(SaveData data) {
        int tick = Is(data, KEY_TICK, SaveData.TYPE_INT) ?
                Math.max(data.getInt(KEY_TICK), 0) : 0;
        int map = NEVER, i;

        if (Is(data, KEY_MAP, SaveData.TYPE_STRING)) {
            String id = data.getString(KEY_MAP);
            for (i = 0; i < session.worlds.length; i++)
                if (session.worlds[i].id.equals(id)) map = i;
        }

        /* A saved position only means something on the saved map. */
        boolean knownMap = map != NEVER;
        World here = session.worlds[knownMap ? map : (map = 0)];
        int x = here.startX, y = here.startY;
        if (knownMap && Is(data, KEY_X, SaveData.TYPE_INT) &&
                Is(data, KEY_Y, SaveData.TYPE_INT) &&
                !here.Solid(data.getInt(KEY_X), data.getInt(KEY_Y))) {
            x = data.getInt(KEY_X);
            y = data.getInt(KEY_Y);
        }

        /* Rocks before the player arrives, so that arriving brings the
           map up to the saved tick with them in place. */
        for (i = 0; i < session.worlds.length; i++) {
            World world = session.worlds[i];
            String key = KEY_ROCKS_PREFIX + world.id;
            if (Is(data, key, SaveData.TYPE_BYTES))
                RestoreRocks(world, data.getBytes(key), tick);
        }
        session.Resume(tick, map, x, y);

        for (i = 0; i < Skills.COUNT; i++) {
            String key = KEY_XP_PREFIX + Skills.SaveKey(i);
            if (Is(data, key, SaveData.TYPE_INT))
                session.skills.SetXpTenths(i, data.getInt(key));
        }

        if (Is(data, KEY_INVENTORY, SaveData.TYPE_BYTES)) {
            byte[] slots = data.getBytes(KEY_INVENTORY);
            int n = Math.min(slots.length / BYTES_PER_SLOT, Inventory.SLOTS);

            for (i = 0; i < n; i++) {
                int item = GetShort(slots, i * BYTES_PER_SLOT) - 1;
                session.inventory.Set(i,
                        Item.Valid(item) ? item : Item.NONE);
            }
        }
    }

    /* Rocks still waiting at the session's tick. A map the player has
       left may hold some that are overdue; those do not count. */
    private int PendingRocks(World world) {
        if (world.EmptyRocks() == 0) return 0;

        int n = 0;
        for (int y = 0; y < world.h; y++)
            for (int x = 0; x < world.w; x++)
                if (world.RockEmptyUntil(x, y) > session.Tick()) n++;
        return n;
    }

    private byte[] CaptureRocks(World world) {
        byte[] out = new byte[PendingRocks(world) * BYTES_PER_ROCK];
        int at = 0;

        for (int y = 0; at < out.length && y < world.h; y++) {
            for (int x = 0; x < world.w; x++) {
                int until = world.RockEmptyUntil(x, y);
                if (until <= session.Tick()) continue;

                PutShort(out, at, x);
                PutShort(out, at + 2, y);
                PutShort(out, at + 4, until >>> 16);
                PutShort(out, at + 6, until);
                at += BYTES_PER_ROCK;
            }
        }
        return out;
    }

    private static void RestoreRocks(World world, byte[] in, int tick) {
        /* The map must believe it is the saved tick, or it would take
           every saved rock for one whose time has already come. */
        world.Tick(Math.min(world.Now(), tick));

        for (int at = 0; at + BYTES_PER_ROCK <= in.length;
                at += BYTES_PER_ROCK) {
            int until = GetShort(in, at + 4) << 16 | GetShort(in, at + 6);
            world.EmptyRock(GetShort(in, at), GetShort(in, at + 2), until);
        }
    }

    private static void PutShort(byte[] out, int at, int value) {
        out[at] = (byte) (value >> 8);
        out[at + 1] = (byte) value;
    }

    private static int GetShort(byte[] in, int at) {
        return (in[at] & 0xFF) << 8 | (in[at + 1] & 0xFF);
    }

    private static boolean Is(SaveData data, String key, int type) {
        return data.getType(key) == type;
    }
}
