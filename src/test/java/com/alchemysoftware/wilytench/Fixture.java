package com.alchemysoftware.wilytench;

import java.io.File;

import com.alchemysoftware.whitetail.error.RunFlag;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * Shared scaffolding for the tests. Not a test itself: its name does
 * not end in "Test", so Surefire leaves it alone.
 *
 * Start() builds a small world with no window: the sprite renderer
 * draws into a plain array nobody looks at. The map is
 * src/test/resources/maps/test.txt, a single room:
 *
 *     #######
 *     #@.rR.#      @ start (1,1)   r copper (3,1)   R iron (4,1)
 *     #.....#
 *     #######
 */
final class Fixture {
    static final String MAP = "test.txt";

    private static final int BUF_W = 320, BUF_H = 240;

    static World world;
    static Player player;
    static ChatBox chat;

    /** Where tests may write files; emptied by "mvn clean". */
    static File dir;

    private static boolean initOnce;

    private Fixture() {}

    static void Start() {
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
        RunFlag.Start();

        Check("sprites", SpriteRenderer.Init(new byte[BUF_W * BUF_H], BUF_W,
                BUF_H, 0, 0, BUF_W, BUF_H, 64) && Sprites.Install());
        Check("map", (world = World.Load(MAP)) != null);
        Check("player", (player = Player.Create(world)) != null);

        Font font = Font.Load("small.fnt");
        Check("font", font != null);
        chat = new ChatBox(font, 0, 0, BUF_W, Screen.CHAT_H);
    }

    static void Finish() {
        Check("no fatal error", RunFlag.IsRunning());
        SpriteRenderer.Shutdown();
        Sprites.Shutdown();
    }

    static void Check(String what, boolean ok) {
        if (!ok) throw new AssertionError(what);
    }
}
