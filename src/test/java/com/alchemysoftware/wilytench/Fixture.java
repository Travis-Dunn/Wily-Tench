package com.alchemysoftware.wilytench;

import java.io.File;
import java.util.Random;

import com.alchemysoftware.whitetail.error.RunFlag;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * Shared scaffolding for the tests. Not a test itself: its name does
 * not end in "Test", so Surefire leaves it alone.
 *
 * Start() builds a Session with no window: the sprite renderer draws
 * into a plain array nobody looks at. Its two maps are in
 * src/test/resources/maps:
 *
 *     test.txt           test2.txt
 *     #######            #####
 *     #@.rR.#            #.LR#
 *     #L....#            #.@.#
 *     #######            #####
 *
 *     @ start (1,1)      @ start (2,2)
 *     r copper (3,1)     R iron (3,1)
 *     R iron (4,1)       L stairs (2,1)
 *     L ladder (1,2)
 *
 * and its scripts are src/test/resources/scripts/test.whtl: the ladder
 * leads down to the second map after two ticks, and the stairs lead
 * straight back.
 */
final class Fixture {
    static final String[] MAPS = { "test.txt", "test2.txt" };
    /* The second is the game's real mining script. */
    static final String[] SCRIPTS = { "test.whtl", "mining.whtl" };
    static final int FIRST = 0, SECOND = 1;

    private static final int BUF_W = 320, BUF_H = 240;

    /* Dice that come up whatever the test last said: 0 always wins a
       swing, 255 never does. */
    private static final class LoadedDice extends Random {
        private static final long serialVersionUID = 1L;
        public int nextInt(int n) { return roll; }
    }

    private static int roll;

    static Session session;

    /** Where tests may write files; emptied by "mvn clean". */
    static File dir;

    private static boolean initOnce, started;

    private Fixture() {}

    /**
     * Begins a fresh game, ending the previous one if a test left it
     * running.
     *
     * @param roll what every roll of the dice will come up
     */
    static void Start(int roll) {
        /* These Whitetail components can be started only once per
           run, so every test shares them. */
        if (!initOnce) {
            dir = new File(System.getProperty("wilytench.test.dir",
                    System.getProperty("java.io.tmpdir")).trim());
            dir.mkdirs();
            Check("logger", Logger.Init(dir));
            Check("resources", ResourceStreamResolver.Init(null,
                    ResourceStreamResolver.MODE_CLASSPATH));
            Check("save store", SaveGame.Init(dir));
            initOnce = true;
        }
        if (started) Finish();
        RunFlag.Start();

        Check("sprite renderer", SpriteRenderer.Init(
                new byte[BUF_W * BUF_H], BUF_W, BUF_H, 0, 0, BUF_W, BUF_H,
                64));
        /* From here on a failure must not leave the renderer running,
           or every later test fails for the wrong reason. */
        started = true;
        Check("data files", Content.Load());

        Font font = Font.Load("small.fnt");
        Check("font", font != null);
        session = Session.Create(MAPS, SCRIPTS,
                new ChatBox(font, 0, 0, BUF_W, Screen.CHAT_H),
                new LoadedDice());
        Fixture.roll = roll;
        Check("session", session != null);
    }

    static void Finish() {
        boolean clean = RunFlag.IsRunning();

        Session.Close();
        SpriteRenderer.Shutdown();
        Content.Shutdown();
        started = false;
        Check("no fatal error", clean);
    }

    /** Changes what every roll of the dice comes up from now on. */
    static void SetRoll(int roll) { Fixture.roll = roll; }

    /** @return how many lines in the chat box read exactly so */
    static int Said(String line) {
        int n = 0;
        for (int i = 0; session.chat.Line(i) != null; i++)
            if (session.chat.Line(i).equals(line)) n++;
        return n;
    }

    static void Run(int ticks) {
        for (int i = 0; i < ticks; i++) session.Advance();
    }

    /** @return how many of an item the player carries */
    static int Count(int item) {
        int n = 0;
        for (int i = 0; i < Inventory.SLOTS; i++)
            if (session.inventory.At(i) == item) n++;
        return n;
    }

    /** @return the item with that id, which must exist */
    static int Item(String id) {
        int item = com.alchemysoftware.wilytench.Item.Named(id);
        Check("item " + id + " exists",
                item != com.alchemysoftware.wilytench.Item.NONE);
        return item;
    }

    /** @return the skill with that id, which must exist */
    static int Skill(String id) {
        int skill = Skills.Named(id);
        Check("skill " + id + " exists", skill >= 0);
        return skill;
    }

    static int Mining() { return Skill("mining"); }

    static void DeleteSaves() {
        String[] names = dir.list();
        for (int i = 0; names != null && i < names.length; i++)
            if (names[i].startsWith("wts-"))
                new File(dir, names[i]).delete();
    }

    static void Check(String what, boolean ok) {
        if (!ok) throw new AssertionError(what);
    }
}
