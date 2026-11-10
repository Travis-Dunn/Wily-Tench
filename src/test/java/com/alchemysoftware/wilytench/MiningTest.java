package com.alchemysoftware.wilytench;

import java.io.File;
import java.util.Random;

import com.alchemysoftware.whitetail.error.RunFlag;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * Plays the mining rules through, tick by tick, with no window: the
 * sprite renderer draws into a plain array nobody looks at.
 *
 * The map is src/test/resources/maps/test.txt, a single room:
 *
 *     #######
 *     #@.rR.#      @ start (1,1)   r copper (3,1)   R iron (4,1)
 *     #.....#
 *     #######
 */
public class MiningTest {
    private static final int BUF_W = 320, BUF_H = 240;
    private static final int COPPER_X = 3, IRON_X = 4, ROCK_Y = 1;
    private static final int PLENTY_OF_TICKS = 40;

    /* Dice that always come up the same: 0 always wins a swing, 255
       never does. */
    private static final class FixedDice extends Random {
        private static final long serialVersionUID = 1L;
        private final int roll;
        FixedDice(int roll) { this.roll = roll; }
        public int nextInt(int n) { return roll; }
    }

    private static boolean resourcesInit;

    private World world;
    private Player player;
    private Inventory inventory;
    private Skills skills;
    private ChatBox chat;
    private Mining mining;

    public void testWinningSwingGivesOreAndXpAndEmptiesRock() {
        Start(0, true);
        player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("one ore mined", Count(Item.COPPER_ORE) == 1);
        Check("17.5 xp", skills.XpTenths(Skills.MINING) == 175);
        Check("job over", !player.HasTarget());
        Check("said so", chat.Newest().equals(
                "You manage to mine some copper ore."));
        Finish();
    }

    public void testRockIsEmptyThenRefills() {
        Start(0, true);
        player.WalkToTarget(COPPER_X, ROCK_Y);

        int ticks = 0;
        while (!world.RockEmpty(COPPER_X, ROCK_Y)) {
            Check("mined in time", ++ticks < PLENTY_OF_TICKS);
            Run(1);
        }
        Check("called plain rocks", world.Name(COPPER_X, ROCK_Y)
                .equals("Rocks"));

        /* Clicking it while empty gets a refusal, not an ore. */
        player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(1);
        Check("refused", chat.Newest().startsWith("There is no ore"));

        Run(Ore.RespawnTicks(Ore.COPPER));
        Check("refilled", !world.RockEmpty(COPPER_X, ROCK_Y));
        Check("named again", world.Name(COPPER_X, ROCK_Y)
                .equals("Copper rocks"));
        Check("still one ore", Count(Item.COPPER_ORE) == 1);
        Finish();
    }

    public void testLosingSwingsMineNothing() {
        Start(255, true);
        player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no ore", Count(Item.COPPER_ORE) == 0);
        Check("no xp", skills.XpTenths(Skills.MINING) == 0);
        Check("still trying", player.HasTarget());
        Finish();
    }

    public void testIronNeedsLevelFifteen() {
        Start(0, true);
        player.WalkToTarget(IRON_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no iron", Count(Item.IRON_ORE) == 0);
        Check("told why", chat.Newest().equals(
                "You need a Mining level of 15 to mine this rock."));

        skills.AddXp(Skills.MINING, Skills.XpForLevel(15) * 10);
        player.WalkToTarget(IRON_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);
        Check("iron at 15", Count(Item.IRON_ORE) == 1);
        Finish();
    }

    public void testNoHammerNoMining() {
        Start(0, false);
        player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no ore", Count(Item.COPPER_ORE) == 0);
        Check("told why", chat.Newest().equals(
                "You need a hammer to mine this rock."));
        Finish();
    }

    public void testFullInventoryStopsMining() {
        Start(0, true);
        while (!inventory.Full()) inventory.Add(Item.IRON_ORE);
        player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no ore", Count(Item.COPPER_ORE) == 0);
        Check("told why", chat.Newest().startsWith(
                "Your inventory is too full"));
        Check("rock untouched", !world.RockEmpty(COPPER_X, ROCK_Y));
        Finish();
    }

    public void testWalkingAwayCancels() {
        Start(255, true);
        player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(5);
        Check("mining", player.HasTarget());

        player.WalkTo(1, 2);
        Check("forgotten", !player.HasTarget());
        Finish();
    }

    public void testLevelCurve() {
        Skills s = new Skills();

        Check("level 2 at 83", Skills.XpForLevel(2) == 83);
        Check("level 99 at 13,034,431",
                Skills.XpForLevel(Skills.MAX_LEVEL) == 13034431);
        Check("starts at 1", s.Level(Skills.MINING) == 1);
        Check("82.9 is not enough", !s.AddXp(Skills.MINING, 829));
        Check("83.0 is", s.AddXp(Skills.MINING, 1));
        Check("now 2", s.Level(Skills.MINING) == 2);
        Check("formats tenths", Skills.Format(175).equals("17.5"));
    }

    public void testInventory() {
        Inventory inv = new Inventory();

        for (int i = 0; i < Inventory.SLOTS; i++)
            Check("room", inv.Add(Item.COPPER_ORE));
        Check("full", inv.Full() && !inv.Add(Item.COPPER_ORE));
        Check("removed", inv.Remove(3) == Item.COPPER_ORE);
        Check("empty slot", inv.Remove(3) == Item.NONE);
        Check("count", inv.Used() == Inventory.SLOTS - 1);
        Check("fills the gap", inv.Add(Item.IRON_ORE) &&
                inv.At(3) == Item.IRON_ORE);
    }

    private void Start(int roll, boolean withHammer) {
        if (!resourcesInit) {
            File logs = new File(System.getProperty("wilytench.test.dir",
                    System.getProperty("java.io.tmpdir")));
            logs.mkdirs();
            Check("logger", Logger.Init(logs));
            Check("resources", ResourceStreamResolver.Init(null,
                    ResourceStreamResolver.MODE_CLASSPATH));
            resourcesInit = true;
        }
        RunFlag.Start();

        Check("sprites", SpriteRenderer.Init(new byte[BUF_W * BUF_H], BUF_W,
                BUF_H, 0, 0, BUF_W, BUF_H, 64) && Sprites.Install());
        Check("map", (world = World.Load("test.txt")) != null);
        Check("player", (player = Player.Create(world)) != null);

        Font font = Font.Load("small.fnt");
        Check("font", font != null);
        chat = new ChatBox(font, 0, 0, BUF_W, Screen.CHAT_H);

        inventory = new Inventory();
        if (withHammer) inventory.Add(Item.BRONZE_HAMMER);
        skills = new Skills();
        mining = new Mining(world, player, inventory, skills, chat,
                new FixedDice(roll));
    }

    private void Finish() {
        Check("no fatal error", RunFlag.IsRunning());
        SpriteRenderer.Shutdown();
        Sprites.Shutdown();
    }

    /* The same order WorldScene advances things in. */
    private void Run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.Tick();
            player.Tick();
            mining.Tick();
        }
    }

    private int Count(int item) {
        int n = 0;
        for (int i = 0; i < Inventory.SLOTS; i++)
            if (inventory.At(i) == item) n++;
        return n;
    }

    private static void Check(String what, boolean ok) {
        if (!ok) throw new AssertionError(what);
    }
}
