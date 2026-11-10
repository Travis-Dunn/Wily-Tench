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
 * The player gives two kinds of order: WalkTo() a tile, and Use() a
 * loc, which walks up beside it and then runs its script. Advance()
 * moves everything on by one game tick, in a fixed order: the map the
 * player is on, then the player's movement, then the script in
 * progress, or the start of one if the player has just arrived at
 * something to use.
 *
 * Only one script runs at a time. While it does, the player is Busy().
 * A script that has not called interruptible() also has the player
 * Held(): new orders are ignored until it ends. An order given while
 * merely busy cancels the script.
 *
 * The methods named Script... and ...UsedLoc... are for the commands
 * in Scripts, and speak of the script in progress and the loc it is
 * about.
 */
final class Session {
    final World[] worlds;
    final Player player;
    final Skills skills = new Skills();
    final Inventory inventory = new Inventory();
    final PlayerVars vars;
    final ChatBox chat;
    final Random rng;

    private int tick, map;

    /* The script in progress, the loc it is about, and where that is. */
    private ScriptState script;
    private String scriptSubject;
    private Loc usedLoc;
    private World usedWorld;
    private int usedX, usedY;

    private boolean interruptible, againAsked, repeating;

    private Session(World[] worlds, Player player, PlayerVars vars,
                    ChatBox chat, Random rng) {
        this.worlds = worlds;
        this.player = player;
        this.vars = vars;
        this.chat = chat;
        this.rng = rng;
    }

    /**
     * Loads the maps and scripts and puts a new character at the start
     * of the first map. The sprite renderer must be initialized with
     * the game's atlases installed. Close() must be called afterwards,
     * whether or not this succeeds.
     *
     * @param mapFiles files in the "maps" resource folder
     * @param scriptFiles files in the "scripts" resource folder
     * @param rng the dice for everything in the game left to chance
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

    /** @return whether a script is in progress */
    boolean Busy() { return script != null; }

    /** @return whether a script is in progress that orders cannot cancel */
    boolean Held() { return script != null && !interruptible; }

    /**
     * @return whether the thing on a tile of this map can be used,
     *         which is to say whether a script exists for it
     */
    boolean Usable(int tileX, int tileY) {
        return Scripts.UseSubject(Here().LocAt(tileX, tileY)) != null;
    }

    /**
     * Orders a walk to a tile, or as near to it as can be reached,
     * cancelling whatever the player was doing. Ignored while Held().
     *
     * @return false only if the walk will not end on that very tile
     */
    boolean WalkTo(int tileX, int tileY) {
        if (Held()) return true;
        EndScript();
        return player.WalkTo(tileX, tileY);
    }

    /**
     * Orders the player to walk up to the loc on a tile and use it,
     * cancelling whatever the player was doing. Ignored while Held().
     */
    void Use(int tileX, int tileY) {
        if (Held()) return;
        EndScript();
        player.WalkToTarget(tileX, tileY);
    }

    /**
     * Moves the player to the start of a map at once, dropping any
     * walk. Scripts do this through their travel command.
     */
    void Travel(int mapIndex) {
        Arrive(mapIndex, worlds[mapIndex].startX, worlds[mapIndex].startY);
        Here().Tick(tick);
        chat.Add("You arrive in the " + Here().name + ".", Palette.BONE);
    }

    /**
     * Awards experience, and says so in the chat box if it brings a
     * new level.
     */
    void GainXp(int skill, int tenths) {
        if (!skills.AddXp(skill, tenths)) return;

        String name = Skills.Name(skill);
        chat.Add("Congratulations, you just advanced a " + name +
                " level! Your " + name + " level is now " +
                skills.Level(skill) + ".", Palette.AMBER);
    }

    /** Advances the whole game by one tick. */
    void Advance() {
        tick++;
        Here().Tick(tick);
        player.Tick();

        if (script != null) {
            script = Scripts.Tick(script);
            if (script == null) AfterRun();
        } else {
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

    void ScriptInterruptible() { interruptible = true; }

    void ScriptAgain() { againAsked = true; }

    boolean ScriptRepeating() { return repeating; }

    /** @return the loc the script in progress is about */
    Loc UsedLoc() { return usedLoc; }

    boolean UsedLocEmpty() { return usedWorld.RockEmpty(usedX, usedY); }

    void EmptyUsedLoc(int ticks) {
        usedWorld.EmptyRock(usedX, usedY, tick + ticks);
    }

    /* Once the player has finished walking to a target, uses it, if it
       has a script and is within reach. */
    private void UseTarget() {
        if (!player.HasTarget() || player.Walking()) return;

        int x = player.TargetX(), y = player.TargetY();
        Loc loc = Here().LocAt(x, y);
        String subject = Scripts.UseSubject(loc);
        boolean beside = player.BesideTarget();

        player.FaceTarget();
        player.ClearTarget();

        if (subject == null) return;
        if (!beside) {
            chat.Add(MSG_CANT_REACH, Palette.BONE);
            return;
        }

        scriptSubject = subject;
        usedLoc = loc;
        usedWorld = Here();
        usedX = x;
        usedY = y;
        Run();
    }

    /* Starts the script, and tidies up if it is over already. */
    private void Run() {
        script = Scripts.Use(scriptSubject, vars);
        if (script == null) AfterRun();
    }

    /* A run has ended. If it asked for another, starts one, and that
       is all for this tick: a rerun that ends at once is not given a
       third go, whatever it asks. */
    private void AfterRun() {
        if (againAsked) {
            againAsked = false;
            repeating = true;
            script = Scripts.Use(scriptSubject, vars);
            if (script != null) return;
        }
        EndScript();
    }

    /* Forgets the script in progress, finished or not. */
    private void EndScript() {
        script = null;
        interruptible = againAsked = repeating = false;
        player.SetWorking(false);
    }

    private void Arrive(int mapIndex, int tileX, int tileY) {
        map = mapIndex;
        player.EnterWorld(worlds[map], tileX, tileY);
    }

    private static final String MSG_CANT_REACH = "You can't reach that.";
}
