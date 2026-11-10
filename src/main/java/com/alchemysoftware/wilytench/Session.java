package com.alchemysoftware.wilytench;

import java.util.Random;

import com.alchemysoftware.whitetail.script.PlayerVars;
import com.alchemysoftware.whitetail.script.ScriptState;

/**
 * A game in progress: every map, the player's character, skills,
 * inventory, and script variables, and the count of game ticks so far.
 * This is the game's rules with no window attached; WorldScene shows it
 * and feeds it the player's wishes, and the tests drive it directly.
 *
 * Advance() moves everything on by one game tick, in a fixed order: the
 * map the player is on, then the player's movement, then whichever one
 * thing the player is doing. That is either a script that is part-way
 * through, or else mining, or else using whatever the player has just
 * walked up to, which starts a script.
 *
 * While a script is part-way through, the player is Busy() and should
 * not be given new orders.
 */
final class Session {
    final World[] worlds;
    final Player player;
    final Skills skills = new Skills();
    final Inventory inventory = new Inventory();
    final PlayerVars vars;
    final ChatBox chat;
    final Mining mining;

    private int tick, map;
    private ScriptState script;

    private Session(World[] worlds, Player player, PlayerVars vars,
                    ChatBox chat, Random rng) {
        this.worlds = worlds;
        this.player = player;
        this.vars = vars;
        this.chat = chat;
        mining = new Mining(this, rng);
    }

    /**
     * Loads the maps and scripts and puts a new character at the start
     * of the first map. The sprite renderer must be initialized with
     * the game's atlases installed. Close() must be called afterwards,
     * whether or not this succeeds.
     *
     * @param mapFiles files in the "maps" resource folder
     * @param scriptFiles files in the "scripts" resource folder
     * @return the session, or null after a fatal error has been
     *         reported
     */
    static Session Create(String[] mapFiles, String[] scriptFiles,
                          ChatBox chat, Random rng) {
        World[] worlds = new World[mapFiles.length];
        for (int i = 0; i < worlds.length; i++)
            if ((worlds[i] = World.Load(mapFiles[i])) == null) return null;

        if (!Scripts.Init(scriptFiles)) return null;
        PlayerVars vars = Scripts.NewVars();
        Player player = Player.Create(worlds[0]);
        if (vars == null || player == null) return null;

        Session session = new Session(worlds, player, vars, chat, rng);
        Scripts.Bind(session);
        return session;
    }

    /** Ends the game in progress, if Create() got as far as one. */
    static void Close() {
        Scripts.Shutdown();
    }

    /** @return the map the player is on */
    World Here() { return worlds[map]; }

    /** @return which of worlds the player is on */
    int Map() { return map; }

    /** @return which of worlds has a file name, or -1 if none does */
    int MapNamed(String id) {
        for (int i = 0; i < worlds.length; i++)
            if (worlds[i].id.equals(id)) return i;
        return -1;
    }

    /** @return game ticks since this game was begun */
    int Tick() { return tick; }

    /** @return whether a script is part-way through */
    boolean Busy() { return script != null; }

    /**
     * @return whether the thing on a tile of this map can be used,
     *         which is to say whether a script exists for it
     */
    boolean Usable(int tileX, int tileY) {
        return Scripts.CanUse(Here().LocAt(tileX, tileY));
    }

    /**
     * Moves the player to the start of a map at once, dropping any
     * walk or job. Scripts do this through their travel command.
     */
    void Travel(int mapIndex) {
        Arrive(mapIndex, worlds[mapIndex].startX, worlds[mapIndex].startY);
        Here().Tick(tick);
        chat.Add("You arrive in the " + Here().name + ".", Palette.BONE);
    }

    /** Advances the whole game by one tick. */
    void Advance() {
        tick++;
        Here().Tick(tick);
        player.Tick();

        if (script != null) {
            script = Scripts.Tick(script);
        } else {
            mining.Tick();
            UseTarget();
        }
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

    /* Once the player has finished walking to a target that is not an
       ore rock, uses it, if it has a script and is within reach. */
    private void UseTarget() {
        int x = player.TargetX(), y = player.TargetY();

        if (!player.HasTarget() || player.Walking() ||
                Here().OreAt(x, y) != Ore.NONE) return;

        String loc = Here().LocAt(x, y);
        boolean beside = player.BesideTarget();
        player.FaceTarget();
        player.ClearTarget();

        if (!Scripts.CanUse(loc)) return;
        if (!beside) {
            chat.Add(MSG_CANT_REACH, Palette.BONE);
            return;
        }
        script = Scripts.Use(loc, vars);
    }

    private void Arrive(int mapIndex, int tileX, int tileY) {
        map = mapIndex;
        player.EnterWorld(worlds[map], tileX, tileY);
    }

    private static final String MSG_CANT_REACH = "You can't reach that.";
}
