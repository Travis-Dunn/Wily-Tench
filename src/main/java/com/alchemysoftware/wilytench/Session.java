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
 * The player gives three kinds of order: WalkTo() a tile; Use() a
 * loc, which walks up beside it and then runs its script; and
 * UseItemOn() a loc, which does the same but runs the script the loc
 * has for having things used on it. Advance()
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
 * A script may also stop for talk: something said, by the person the
 * script is about or by the player's character, which stays up until
 * ContinueTalk(); or a choice of things to say, which stays up until
 * ChooseTalk(). TalkKind() says which, if either. Like everything
 * else, an order that cancels the script ends the talk.
 *
 * A script may stop to ask what the player wants made. OpenMake()
 * then names the list of recipes to show, and nothing more happens
 * until ChooseMake() or CloseMake(), or an order that cancels the
 * script.
 *
 * The methods named Script... and ...Used... are for the commands
 * in Scripts, and speak of the script in progress, the loc it is
 * about, and the item being used on that loc, if one is.
 */
final class Session {
    /* What TalkKind() answers. */
    static final int TALK_NONE = 0;
    /** The person being talked to is saying something. */
    static final int TALK_NPC = 1;
    /** The player's character is saying something. */
    static final int TALK_PLAYER = 2;
    /** The player is being offered things to say. */
    static final int TALK_CHOICE = 3;

    /** What the player's character is called, until it has a name. */
    static final String PLAYER_NAME = "You";

    final World[] worlds;
    final Player player;
    final Skills skills = new Skills();
    final Inventory inventory = new Inventory();
    final PlayerVars vars;
    final ChatBox chat;
    final Random rng;

    private int tick, map;

    /* The item to use on the loc being walked to, or Item.NONE to
       use the loc by itself. */
    private int targetItem = Item.NONE;

    /* The script in progress, the trigger that began it, the loc it
       is about, where that is, and the item used on it if any. */
    private ScriptState script;
    private String scriptTrigger, scriptSubject;
    private int usedItem = Item.NONE;
    private Loc usedLoc;
    private Npc usedNpc;

    /* The talk on show, if any, and the last choice made. */
    private int talkKind = TALK_NONE;
    private String talkText;
    private String[] talkOptions = new String[0];
    private int talkChosen;
    private World usedWorld;
    private int usedX, usedY;

    private boolean interruptible, againAsked, repeating;

    /* The list of recipes whose menu the script in progress is waiting
       at, and the list and recipe last picked from such a menu. */
    private int openMake = Recipes.NONE;
    private int madeList = Recipes.NONE, madeRecipe;

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
        return Scripts.Subject(Scripts.TRIGGER_USE,
                Here().LocAt(tileX, tileY)) != null ||
                Scripts.TalkSubject(Here().NpcAt(tileX, tileY)) != null;
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
        targetItem = Item.NONE;
        player.WalkToTarget(tileX, tileY);
    }

    /**
     * Orders the player to walk up to the loc on a tile and use the
     * item in an inventory slot on it, cancelling whatever the player
     * was doing. If the loc has no script for that, or nothing stands
     * there, says that nothing happens and goes nowhere. Ignored while
     * Held().
     */
    void UseItemOn(int slot, int tileX, int tileY) {
        int item = inventory.At(slot);
        if (Held() || item == Item.NONE) return;
        EndScript();

        if (Scripts.Subject(Scripts.TRIGGER_USE_ITEM,
                Here().LocAt(tileX, tileY)) == null) {
            chat.Add(MSG_NOTHING_HAPPENS, Palette.TEXT);
            return;
        }
        targetItem = item;
        player.WalkToTarget(tileX, tileY);
    }

    /**
     * Uses the item in one inventory slot on the item in another.
     * Nothing can be combined that way yet.
     */
    void UseItemOnItem(int slot, int otherSlot) {
        if (inventory.At(slot) != Item.NONE &&
                inventory.At(otherSlot) != Item.NONE)
            chat.Add(MSG_NOTHING_HAPPENS, Palette.TEXT);
    }

    /** @return which kind of talk is on show: one of the TALK_ values */
    int TalkKind() { return talkKind; }

    /** @return what is being said */
    String TalkText() { return talkText; }

    /** @return the name of whoever is saying it */
    String TalkSpeaker() {
        return talkKind == TALK_NPC ? usedNpc.name : PLAYER_NAME;
    }

    /** @return how many things the player is being offered to say */
    int TalkOptions() {
        return talkKind == TALK_CHOICE ? talkOptions.length : 0;
    }

    /** @param option counted from zero */
    String TalkOption(int option) { return talkOptions[option]; }

    /** Has the player read what was said; the script carries on. */
    void ContinueTalk() {
        if (talkKind != TALK_NPC && talkKind != TALK_PLAYER) return;
        talkKind = TALK_NONE;
        ResumeScript();
    }

    /**
     * Picks one of the things offered to say; the script carries on.
     *
     * @param option counted from zero; anything not on offer is
     *               ignored
     */
    void ChooseTalk(int option) {
        if (talkKind != TALK_CHOICE || option < 0 ||
                option >= talkOptions.length) return;
        talkChosen = option + 1;
        talkKind = TALK_NONE;
        ResumeScript();
    }

    /**
     * @return the list of recipes the player is being asked to pick
     *         from, or Recipes.NONE
     */
    int OpenMake() { return openMake; }

    /**
     * Picks a recipe from the list being shown, and lets the script
     * that showed it carry on.
     */
    void ChooseMake(int recipe) {
        if (openMake == Recipes.NONE) return;

        madeList = openMake;
        madeRecipe = recipe;
        openMake = Recipes.NONE;
        ResumeScript();
    }

    /** Declines to pick, which ends the script that asked. */
    void CloseMake() {
        if (openMake != Recipes.NONE) EndScript();
    }

    /**
     * Looks closely at what stands on a tile of this map, and says
     * what there is to see. Costs no time and interrupts nothing.
     */
    void ExamineTile(int tileX, int tileY) {
        String text = Here().Examine(tileX, tileY);
        if (text != null) chat.Add(text, Palette.TEXT_DIM);
    }

    /** Looks closely at the item in an inventory slot. */
    void ExamineItem(int slot) {
        int item = inventory.At(slot);
        if (item != Item.NONE) chat.Add(Item.Examine(item), Palette.TEXT_DIM);
    }

    /**
     * Drops the item in an inventory slot. For now a dropped item is
     * simply gone.
     */
    void DropItem(int slot) {
        int item = inventory.Remove(slot);
        if (item == Item.NONE) return;
        chat.Add("You drop the " + Item.LowerName(item) + ".",
                Palette.TEXT);
    }

    /**
     * Moves the player to the start of a map at once, dropping any
     * walk. Scripts do this through their travel command.
     */
    void Travel(int mapIndex) {
        Arrive(mapIndex, worlds[mapIndex].startX, worlds[mapIndex].startY);
        Here().Tick(tick);
        chat.Add("You arrive in the " + Here().name + ".", Palette.TEXT);
    }

    /**
     * Moves the player to a tile of a map at once, as Travel() does to
     * its start. Scripts do this through their travel_to command, for
     * arriving beside the ladder that was climbed.
     */
    void TravelTo(int mapIndex, int tileX, int tileY) {
        Arrive(mapIndex, tileX, tileY);
        Here().Tick(tick);
        chat.Add("You arrive in the " + Here().name + ".", Palette.TEXT);
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
                skills.Level(skill) + ".", Palette.TEXT_NEWS);
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

    void ScriptOpenMake(int list) { openMake = list; }

    /** @param kind TALK_NPC or TALK_PLAYER */
    void ScriptSay(int kind, String text) {
        talkKind = kind;
        talkText = text;
    }

    void ScriptAsk(String[] options) {
        talkKind = TALK_CHOICE;
        talkOptions = options;
    }

    /** @return the last thing picked with ChooseTalk(), from 1 */
    int TalkChosen() { return talkChosen; }

    /** @return the person the script in progress is about, or null */
    Npc UsedNpc() { return usedNpc; }

    /** @return the list last picked from, or Recipes.NONE */
    int MadeList() { return madeList; }

    int MadeRecipe() { return madeRecipe; }

    /** @return the loc the script in progress is about */
    Loc UsedLoc() { return usedLoc; }

    /** @return the item being used on that loc, or Item.NONE */
    int UsedItem() { return usedItem; }

    boolean UsedLocEmpty() { return usedWorld.RockEmpty(usedX, usedY); }

    void EmptyUsedLoc(int ticks) {
        usedWorld.EmptyRock(usedX, usedY, tick + ticks);
    }

    /* Once the player has finished walking to a target, uses it, or
       uses on it the item the order named, if it has a script for
       that and is within reach. An item that has left the inventory
       on the way is not used. */
    private void UseTarget() {
        if (!player.HasTarget() || player.Walking()) return;

        int x = player.TargetX(), y = player.TargetY();
        Loc loc = Here().LocAt(x, y);
        Npc npc = Here().NpcAt(x, y);
        String trigger = targetItem == Item.NONE ?
                Scripts.TRIGGER_USE : Scripts.TRIGGER_USE_ITEM;
        String subject = Scripts.Subject(trigger, loc);
        boolean beside = player.BesideTarget();
        if (targetItem != Item.NONE && !inventory.Has(targetItem))
            subject = null;
        if (npc != null && targetItem == Item.NONE) {
            trigger = Scripts.TRIGGER_TALK;
            subject = Scripts.TalkSubject(npc);
        }

        player.FaceTarget();
        player.ClearTarget();

        if (subject == null) return;
        if (!beside) {
            chat.Add(MSG_CANT_REACH, Palette.TEXT);
            return;
        }

        scriptTrigger = trigger;
        scriptSubject = subject;
        usedItem = targetItem;
        usedLoc = loc;
        usedNpc = npc;
        usedWorld = Here();
        usedX = x;
        usedY = y;
        Run();
    }

    /* Starts the script, and tidies up if it is over already. */
    private void Run() {
        script = Scripts.Fire(scriptTrigger, scriptSubject, vars);
        if (script == null) AfterRun();
    }

    /* A run has ended. If it asked for another, starts one, and that
       is all for this tick: a rerun that ends at once is not given a
       third go, whatever it asks. */
    private void AfterRun() {
        if (againAsked) {
            againAsked = false;
            repeating = true;
            script = Scripts.Fire(scriptTrigger, scriptSubject, vars);
            if (script != null) return;
        }
        EndScript();
    }

    /* Forgets the script in progress, finished or not. */
    private void EndScript() {
        script = null;
        openMake = Recipes.NONE;
        talkKind = TALK_NONE;
        interruptible = againAsked = repeating = false;
        player.SetWorking(false);
    }

    /* Lets a script that was waiting on the player carry on. */
    private void ResumeScript() {
        script = Scripts.Resume(script);
        if (script == null) AfterRun();
    }

    private void Arrive(int mapIndex, int tileX, int tileY) {
        map = mapIndex;
        player.EnterWorld(worlds[map], tileX, tileY);
    }

    private static final String MSG_CANT_REACH = "You can't reach that.";
    private static final String MSG_NOTHING_HAPPENS =
            "Nothing interesting happens.";
}
