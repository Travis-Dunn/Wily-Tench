package com.alchemysoftware.wilytench;

import java.util.Random;

/**
 * A game in progress: every map, the player's character, skills, and
 * inventory, and the count of game ticks so far. This is the game's
 * rules with no window attached; WorldScene shows it and feeds it the
 * player's wishes, and the tests drive it directly.
 *
 * Tick() advances everything by one game tick, in a fixed order:
 * travel between maps, then the map the player is on, then the
 * player's movement, then the player's current job.
 */
final class Session {
    private static final int NO_TRAVEL = -1;

    final World[] worlds;
    final Player player;
    final Skills skills = new Skills();
    final Inventory inventory = new Inventory();
    final ChatBox chat;
    final Mining mining;

    private int tick, map;
    private int travelTo = NO_TRAVEL;

    private Session(World[] worlds, Player player, ChatBox chat,
                    Random rng) {
        this.worlds = worlds;
        this.player = player;
        this.chat = chat;
        mining = new Mining(this, rng);
    }

    /**
     * Loads the maps and puts a new character at the start of the
     * first. The sprite renderer must be initialized with the game's
     * atlases installed.
     *
     * @param mapFiles files in the "maps" resource folder
     * @return the session, or null after a fatal error has been
     *         reported
     */
    static Session Create(String[] mapFiles, ChatBox chat, Random rng) {
        World[] worlds = new World[mapFiles.length];
        for (int i = 0; i < worlds.length; i++)
            if ((worlds[i] = World.Load(mapFiles[i])) == null) return null;

        Player player = Player.Create(worlds[0]);
        return player == null ? null :
                new Session(worlds, player, chat, rng);
    }

    /** @return the map the player is on */
    World Here() { return worlds[map]; }

    /** @return which of worlds the player is on */
    int Map() { return map; }

    /** @return game ticks since this game was begun */
    int Tick() { return tick; }

    /**
     * Asks for the player to be moved to the start of another map. It
     * happens at the beginning of the next tick.
     */
    void TravelTo(int mapIndex) { travelTo = mapIndex; }

    /** Advances the whole game by one tick. */
    void Advance() {
        tick++;

        if (travelTo != NO_TRAVEL) {
            Arrive(travelTo, worlds[travelTo].startX,
                    worlds[travelTo].startY);
            chat.Add("You arrive in the " + Here().name + ".",
                    Palette.BONE);
            travelTo = NO_TRAVEL;
        }

        Here().Tick(tick);
        player.Tick();
        mining.Tick();
    }

    /**
     * Sets the tick count and puts the player on a tile of a map at
     * once. For restoring a saved game.
     */
    void Resume(int tick, int mapIndex, int tileX, int tileY) {
        this.tick = tick;
        Arrive(mapIndex, tileX, tileY);
        Here().Tick(tick);
    }

    private void Arrive(int mapIndex, int tileX, int tileY) {
        map = mapIndex;
        player.EnterWorld(worlds[map], tileX, tileY);
    }
}
