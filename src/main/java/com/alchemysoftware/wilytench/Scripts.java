package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.script.CommandHandler;
import com.alchemysoftware.whitetail.script.ExecutionState;
import com.alchemysoftware.whitetail.script.PlayerVars;
import com.alchemysoftware.whitetail.script.ScriptManager;
import com.alchemysoftware.whitetail.script.ScriptState;
import com.alchemysoftware.whitetail.script.ValueType;

/**
 * The bridge between the game and its scripts.
 *
 * Scripts are .whtl files in the "scripts" resource folder, written in
 * Whitetail's script language, and are where the game's content lives:
 * what happens when the player uses a ladder, and in time what a
 * character says and what a quest asks. Whitetail supplies the
 * language; everything a script can actually do is a command defined
 * here.
 *
 * Triggers the game fires:
 *
 *   [oploc,X]   the player used the thing a map calls X ("ladder",
 *               "stairs"), having walked up beside it
 *
 * Commands scripts may call:
 *
 *   mes(text)     writes a line in the chat box
 *   travel(map)   moves the player to the start of a map, named by
 *                 its file, such as "dungeon.txt"
 *
 * There is one game in progress at a time, and commands act on it.
 */
final class Scripts {
    /** The trigger type for using something on the map. */
    static final String TRIGGER_USE = "oploc";

    private static final String FOLDER = "scripts";

    private static boolean init;
    private static Session session;

    private Scripts() {}

    /**
     * Registers the commands and compiles the scripts.
     *
     * @param files files in the "scripts" resource folder
     * @return false after a fatal error has been reported
     */
    static boolean Init(String[] files) {
        if (!ScriptManager.Init()) return false;
        init = true;

        ScriptManager.RegisterCommand("mes",
                new ValueType[] { ValueType.STRING }, ValueType.VOID,
                new CommandHandler() {
                    public void execute(ScriptState state) {
                        session.chat.Add(state.argString(0), Palette.BONE);
                    }
                });
        ScriptManager.RegisterCommand("travel",
                new ValueType[] { ValueType.STRING }, ValueType.VOID,
                new CommandHandler() {
                    public void execute(ScriptState state) {
                        Travel(state);
                    }
                });

        String[] paths = new String[files.length];
        for (int i = 0; i < files.length; i++)
            paths[i] = FOLDER + "/" + files[i];
        return ScriptManager.LoadScripts(paths);
    }

    /** Safe to call whether or not Init() succeeded. */
    static void Shutdown() {
        if (init) ScriptManager.Shutdown();
        init = false;
        session = null;
    }

    /** Names the game in progress that commands act on. */
    static void Bind(Session session) { Scripts.session = session; }

    /** @return fresh storage for a player's script variables */
    static PlayerVars NewVars() { return ScriptManager.NewPlayerVars(); }

    /** @return whether a script exists for using the named thing */
    static boolean CanUse(String loc) {
        return loc != null && ScriptManager.HasTrigger(TRIGGER_USE, loc);
    }

    /**
     * Starts the script for using the named thing, which CanUse() must
     * have answered true for.
     *
     * @return the running script, or null if it has already finished
     */
    static ScriptState Use(String loc, PlayerVars vars) {
        return Unfinished(
                ScriptManager.FireTrigger(TRIGGER_USE, loc, vars));
    }

    /**
     * Gives a waiting script its game tick.
     *
     * @return the script, or null once it has finished
     */
    static ScriptState Tick(ScriptState state) {
        if (state.getState() == ExecutionState.DELAYED)
            ScriptManager.Tick(state);
        return Unfinished(state);
    }

    private static ScriptState Unfinished(ScriptState state) {
        return state == null || state.getState() ==
                ExecutionState.FINISHED ? null : state;
    }

    private static void Travel(ScriptState state) {
        String id = state.argString(0);
        int map = session.MapNamed(id);

        if (map < 0) {
            ErrorHandler.LogFatalAndStop(
                    ErrStrNoMap(id, state.script.trigger()));
            return;
        }
        session.Travel(map);
    }

    private static String ErrStrNoMap(String id, String trigger) {
        return "Script command travel failed to move the player because [" +
                id + "], named in the script " + trigger +
                ", is not one of the game's maps.\n";
    }
}
