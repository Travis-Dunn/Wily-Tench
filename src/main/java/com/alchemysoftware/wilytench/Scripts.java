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
 * what happens when the player uses a ladder or mines a rock, and in
 * time what a character says and what a quest asks. Whitetail supplies
 * the language; everything a script can actually do is a command
 * defined here.
 *
 * Triggers the game fires:
 *
 *   [oploc,X]   the player used a loc, having walked up beside it. X
 *               is the loc's own name ("ladder") or, when no script
 *               has that name, an underscore and its category
 *               ("_ore_rock"). See Loc.
 *   [oplocu,X]  the player used an item on a loc, having walked up
 *               beside it. X is found the same way. used_item() says
 *               which item.
 *   [opnpc,X]   the player talked to a person, having walked up beside
 *               them. X is the person's id in npcs.json.
 *
 * Commands scripts may call. "The loc" is the one being used.
 *
 *   Talking
 *   mes(text)              writes a line in the chat box
 *   join(text, text)       -> text: the two run together
 *   str(whole)             -> text: the number written out
 *
 *   The run itself
 *   interruptible()        lets the player's next click cancel this
 *                          run; without it the player is held until
 *                          the script ends
 *   again()                asks for the script to be run once more as
 *                          soon as this run ends
 *   repeating()            -> 1 if this run was asked for by again(),
 *                          0 if by the player
 *   anim(name)             plays "work" or "idle" on the player until
 *                          the script ends
 *
 *   The loc
 *   loc_int(key)           -> whole: one of the loc's parameters
 *   loc_string(key)        -> text: one of the loc's parameters
 *   loc_empty()            -> 1 if the loc is emptied, 0 if not
 *   loc_empty_for(ticks)   empties the loc for that many game ticks
 *
 *   The player
 *   stat_level(skill)      -> whole: the level of "mining"
 *   stat_xp(skill, tenths) awards experience, announcing a new level
 *   stat_roll(level, chance_at_1, chance_at_99)
 *                          -> 1 or 0: rolls the dice with a chance out
 *                          of 256 that rises evenly from the first
 *                          figure at level 1 to the second at level 99
 *   inv_has(item)          -> 1 if the player carries the item, else 0
 *   inv_free()             -> whole: empty inventory slots
 *   inv_add(item)          gives the player the item, if there is room
 *   inv_del(item)          takes one of the item from the player, if
 *                          one is carried
 *   inv_del_n(item, n)     takes n of the item, or as many as are
 *                          carried
 *   inv_count(item)        -> whole: how many of the item are carried
 *   used_item()            -> text: the item being used on the loc, or
 *                          "" in a script that is not about an item
 *   item_name(item)        -> text: the item's name, in lower case
 *   travel(map)            moves the player to the start of a map,
 *                          named by its file, such as "dungeon.txt"
 *   travel_to(map, point)  moves the player to a point the map file
 *                          defines, such as "from_cave"
 *
 *   Talking with someone. These show in place of the chat box, and
 *   each waits there until the player clicks. If the player does
 *   something else instead, the script ends.
 *   chatnpc(text)          the person being talked to says something
 *   chatplayer(text)       the player's character says something
 *   choice2(text, text)    offers the player things to say; also
 *   choice3(...), choice4(...)
 *   chosen()               -> whole: which was picked, counting from 1
 *
 *   Making things. A "list" is one of those in recipes.json.
 *   make_has(list)         -> 1 if there is a list with that id, else 0
 *   make_open(list)        shows the list as a menu and waits there
 *                          until the player picks a recipe. If the
 *                          menu is closed instead, the script ends
 *   make_item()            -> text: the item the picked recipe makes
 *   make_int(key)          -> whole: the picked recipe's "uses",
 *                          "level", or "xp" (in tenths)
 *   make_string(key)       -> text: the picked recipe's list's
 *                          "material" or "skill"
 *                          The pick is remembered through again(), so
 *                          a rerun can make the same thing unasked.
 *
 * Items and skills are named in scripts by their ids in items.json
 * and skills.json.
 * A script that names something that does not exist is a mistake in
 * the game's content and stops the game with an error saying where.
 *
 * There is one game in progress at a time, and commands act on it.
 */
final class Scripts {
    /** The trigger type for using a loc. */
    static final String TRIGGER_USE = "oploc";
    /** The trigger type for using an item on a loc. */
    static final String TRIGGER_USE_ITEM = "oplocu";
    /** The trigger type for talking to a person. */
    static final String TRIGGER_TALK = "opnpc";

    private static final String FOLDER = "scripts";
    private static final String CATEGORY_MARK = "_";
    private static final int ROLL_SIDES = 256;

    private static final ValueType INT = ValueType.INT;
    private static final ValueType STRING = ValueType.STRING;
    private static final ValueType VOID = ValueType.VOID;

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

        Register();

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

    /**
     * @param trigger TRIGGER_USE or TRIGGER_USE_ITEM
     * @return the X of the [trigger,X] script that doing that to a loc
     *         runs, or null if there is no script for it
     */
    static String Subject(String trigger, Loc loc) {
        if (loc == null) return null;
        if (ScriptManager.HasTrigger(trigger, loc.id)) return loc.id;
        if (loc.category == null) return null;

        String subject = CATEGORY_MARK + loc.category;
        return ScriptManager.HasTrigger(trigger, subject) ? subject : null;
    }

    /**
     * @return the X of the [opnpc,X] script that talking to a person
     *         runs, or null if there is no script for them
     */
    static String TalkSubject(Npc npc) {
        return npc != null && ScriptManager.HasTrigger(TRIGGER_TALK,
                npc.id) ? npc.id : null;
    }

    /**
     * Starts a loc's script.
     *
     * @param subject what Subject() answered for the trigger and loc
     * @return the running script, or null if it has already finished
     */
    static ScriptState Fire(String trigger, String subject,
                            PlayerVars vars) {
        return Unfinished(ScriptManager.FireTrigger(trigger, subject, vars));
    }

    /**
     * Carries on a script that a command had made wait.
     *
     * @return the script, or null once it has finished
     */
    static ScriptState Resume(ScriptState state) {
        ScriptManager.Resume(state);
        return Unfinished(state);
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
        return state == null ||
                state.getState() == ExecutionState.FINISHED ? null : state;
    }

    private static void Register() {
        Add("mes", new ValueType[] { STRING }, VOID, new CommandHandler() {
            public void execute(ScriptState s) {
                session.chat.Add(s.argString(0), Palette.BONE);
            }
        });
        Add("join", new ValueType[] { STRING, STRING }, STRING,
                new CommandHandler() {
            public void execute(ScriptState s) {
                s.pushString(s.argString(0) + s.argString(1));
            }
        });
        Add("str", new ValueType[] { INT }, STRING, new CommandHandler() {
            public void execute(ScriptState s) {
                s.pushString(String.valueOf(s.argInt(0)));
            }
        });

        Add("interruptible", new ValueType[0], VOID, new CommandHandler() {
            public void execute(ScriptState s) {
                session.ScriptInterruptible();
            }
        });
        Add("again", new ValueType[0], VOID, new CommandHandler() {
            public void execute(ScriptState s) { session.ScriptAgain(); }
        });
        Add("repeating", new ValueType[0], INT, new CommandHandler() {
            public void execute(ScriptState s) {
                s.pushInt(session.ScriptRepeating() ? 1 : 0);
            }
        });
        Add("anim", new ValueType[] { STRING }, VOID, new CommandHandler() {
            public void execute(ScriptState s) {
                String name = s.argString(0);
                boolean work = name.equals("work");

                if (!work && !name.equals("idle")) {
                    Fail(s, "animation", name);
                    return;
                }
                session.player.SetWorking(work);
            }
        });

        Add("loc_int", new ValueType[] { STRING }, INT, new CommandHandler() {
            public void execute(ScriptState s) {
                Integer v = session.UsedLoc().Int(s.argString(0));

                if (v == null) Fail(s, "whole-number parameter of the " +
                        "loc [" + session.UsedLoc().id + "]", s.argString(0));
                s.pushInt(v == null ? 0 : v.intValue());
            }
        });
        Add("loc_string", new ValueType[] { STRING }, STRING,
                new CommandHandler() {
            public void execute(ScriptState s) {
                String v = session.UsedLoc().Str(s.argString(0));

                if (v == null) Fail(s, "text parameter of the loc [" +
                        session.UsedLoc().id + "]", s.argString(0));
                s.pushString(v == null ? "" : v);
            }
        });
        Add("loc_empty", new ValueType[0], INT, new CommandHandler() {
            public void execute(ScriptState s) {
                s.pushInt(session.UsedLocEmpty() ? 1 : 0);
            }
        });
        Add("loc_empty_for", new ValueType[] { INT }, VOID,
                new CommandHandler() {
            public void execute(ScriptState s) {
                session.EmptyUsedLoc(s.argInt(0));
            }
        });

        Add("stat_level", new ValueType[] { STRING }, INT,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int skill = Skill(s, 0);
                s.pushInt(skill < 0 ? 1 : session.skills.Level(skill));
            }
        });
        Add("stat_xp", new ValueType[] { STRING, INT }, VOID,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int skill = Skill(s, 0);
                if (skill >= 0) session.GainXp(skill, s.argInt(1));
            }
        });
        Add("stat_roll", new ValueType[] { INT, INT, INT }, INT,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int level = s.argInt(0), span = Skills.MAX_LEVEL - 1;
                int chance = (s.argInt(1) * (Skills.MAX_LEVEL - level) +
                        s.argInt(2) * (level - 1)) / span;
                s.pushInt(session.rng.nextInt(ROLL_SIDES) < chance ? 1 : 0);
            }
        });
        Add("inv_has", new ValueType[] { STRING }, INT, new CommandHandler() {
            public void execute(ScriptState s) {
                int item = ItemArg(s, 0);
                s.pushInt(item != Item.NONE &&
                        session.inventory.Has(item) ? 1 : 0);
            }
        });
        Add("inv_free", new ValueType[0], INT, new CommandHandler() {
            public void execute(ScriptState s) {
                s.pushInt(Inventory.SLOTS - session.inventory.Used());
            }
        });
        Add("inv_add", new ValueType[] { STRING }, VOID,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int item = ItemArg(s, 0);
                if (item != Item.NONE) session.inventory.Add(item);
            }
        });
        Add("inv_del", new ValueType[] { STRING }, VOID,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int item = ItemArg(s, 0);
                if (item != Item.NONE) session.inventory.RemoveOne(item);
            }
        });
        Add("inv_del_n", new ValueType[] { STRING, INT }, VOID,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int item = ItemArg(s, 0);
                for (int i = 0; item != Item.NONE && i < s.argInt(1); i++)
                    session.inventory.RemoveOne(item);
            }
        });
        Add("inv_count", new ValueType[] { STRING }, INT,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int item = ItemArg(s, 0);
                s.pushInt(item == Item.NONE ? 0 :
                        session.inventory.Count(item));
            }
        });
        Add("chatnpc", new ValueType[] { STRING }, VOID,
                new CommandHandler() {
            public void execute(ScriptState s) {
                if (session.UsedNpc() == null) {
                    Fail(s, "person to speak, there being nobody this " +
                            "script is about, for the command", "chatnpc");
                    return;
                }
                session.ScriptSay(Session.TALK_NPC, s.argString(0));
                s.suspend();
            }
        });
        Add("chatplayer", new ValueType[] { STRING }, VOID,
                new CommandHandler() {
            public void execute(ScriptState s) {
                session.ScriptSay(Session.TALK_PLAYER, s.argString(0));
                s.suspend();
            }
        });
        AddChoice("choice2", 2);
        AddChoice("choice3", 3);
        AddChoice("choice4", 4);
        Add("chosen", new ValueType[0], INT, new CommandHandler() {
            public void execute(ScriptState s) {
                s.pushInt(session.TalkChosen());
            }
        });
        Add("make_has", new ValueType[] { STRING }, INT,
                new CommandHandler() {
            public void execute(ScriptState s) {
                s.pushInt(Recipes.Named(s.argString(0)) ==
                        Recipes.NONE ? 0 : 1);
            }
        });
        Add("make_open", new ValueType[] { STRING }, VOID,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int list = Recipes.Named(s.argString(0));

                if (list == Recipes.NONE) {
                    Fail(s, "list of recipes", s.argString(0));
                    return;
                }
                session.ScriptOpenMake(list);
                s.suspend();
            }
        });
        Add("make_item", new ValueType[0], STRING, new CommandHandler() {
            public void execute(ScriptState s) {
                int list = Made(s, "item");
                s.pushString(list == Recipes.NONE ? "" : Item.Id(
                        Recipes.Product(list, session.MadeRecipe())));
            }
        });
        Add("make_int", new ValueType[] { STRING }, INT,
                new CommandHandler() {
            public void execute(ScriptState s) {
                String key = s.argString(0);
                int list = Made(s, key), recipe = session.MadeRecipe();
                int v = 0;

                if (list == Recipes.NONE) v = 0;
                else if (key.equals("uses")) v = Recipes.Uses(list, recipe);
                else if (key.equals("level"))
                    v = Recipes.Level(list, recipe);
                else if (key.equals("xp"))
                    v = Recipes.XpTenths(list, recipe);
                else Fail(s, "whole-number value of a recipe", key);
                s.pushInt(v);
            }
        });
        Add("make_string", new ValueType[] { STRING }, STRING,
                new CommandHandler() {
            public void execute(ScriptState s) {
                String key = s.argString(0), v = "";
                int list = Made(s, key);

                if (list == Recipes.NONE) v = "";
                else if (key.equals("material"))
                    v = Item.Id(Recipes.Material(list));
                else if (key.equals("skill"))
                    v = Skills.Id(Recipes.Skill(list));
                else Fail(s, "text value of a list of recipes", key);
                s.pushString(v);
            }
        });
        Add("used_item", new ValueType[0], STRING, new CommandHandler() {
            public void execute(ScriptState s) {
                int item = session.UsedItem();
                s.pushString(item == Item.NONE ? "" : Item.Id(item));
            }
        });
        Add("item_name", new ValueType[] { STRING }, STRING,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int item = ItemArg(s, 0);
                s.pushString(item == Item.NONE ? "" : Item.LowerName(item));
            }
        });
        Add("travel_to", new ValueType[] { STRING, STRING }, VOID,
                new CommandHandler() {
            public void execute(ScriptState s) {
                int map = session.MapNamed(s.argString(0));
                int point = map < 0 ? -1 :
                        session.worlds[map].PointNamed(s.argString(1));

                if (map < 0) {
                    Fail(s, "map", s.argString(0));
                } else if (point < 0) {
                    Fail(s, "point on the map [" + s.argString(0) + "]",
                            s.argString(1));
                } else {
                    session.TravelTo(map, session.worlds[map].PointX(point),
                            session.worlds[map].PointY(point));
                }
            }
        });
        Add("travel", new ValueType[] { STRING }, VOID, new CommandHandler() {
            public void execute(ScriptState s) {
                int map = session.MapNamed(s.argString(0));

                if (map < 0) Fail(s, "map", s.argString(0));
                else session.Travel(map);
            }
        });
    }

    /* Registers a command that offers n things to say. */
    private static void AddChoice(String name, final int n) {
        ValueType[] params = new ValueType[n];
        for (int i = 0; i < n; i++) params[i] = STRING;

        Add(name, params, VOID, new CommandHandler() {
            public void execute(ScriptState s) {
                String[] options = new String[n];
                for (int i = 0; i < n; i++) options[i] = s.argString(i);
                session.ScriptAsk(options);
                s.suspend();
            }
        });
    }

    private static void Add(String name, ValueType[] params, ValueType ret,
                            CommandHandler handler) {
        ScriptManager.RegisterCommand(name, params, ret, handler);
    }

    /* The skill named by a text argument, or -1 after reporting. */
    private static int Skill(ScriptState s, int arg) {
        int skill = Skills.Named(s.argString(arg));
        if (skill < 0) Fail(s, "skill", s.argString(arg));
        return skill;
    }

    /* The item named by a text argument, or NONE after reporting. */
    private static int ItemArg(ScriptState s, int arg) {
        int item = Item.Named(s.argString(arg));
        if (item == Item.NONE) Fail(s, "item", s.argString(arg));
        return item;
    }

    /* The list the picked recipe is in, or NONE after reporting that
       nothing has been picked. */
    private static int Made(ScriptState s, String key) {
        int list = session.MadeList();
        if (list == Recipes.NONE)
            Fail(s, "value of a recipe not yet picked with make_open", key);
        return list;
    }

    private static void Fail(ScriptState s, String kind, String name) {
        ErrorHandler.LogFatalAndStop("The script " + s.script.trigger() +
                " in [" + s.script.sourceName + "] failed to run because " +
                "it names the " + kind + " [" + name + "], and there is " +
                "no such thing.\n");
    }
}
