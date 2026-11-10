package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;
import static com.alchemysoftware.wilytench.Fixture.Count;
import static com.alchemysoftware.wilytench.Fixture.Run;

/**
 * Plays the mining script, mining.whtl, through tick by tick. Fixture
 * describes the map.
 */
public class MiningTest {
    private static final int COPPER_X = 3, IRON_X = 4, ROCK_Y = 1;
    private static final int PLENTY_OF_TICKS = 40;
    private static final int WIN = 0, LOSE = 255;
    private static final int COPPER_RESPAWN =
            Loc.COPPER_ROCKS.Int("respawn").intValue();

    private Session s;

    public void testWinningSwingGivesOreAndXpAndEmptiesRock() {
        Start(WIN, true);
        s.Use(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("one ore mined", Count(Item.COPPER_ORE) == 1);
        Check("17.5 xp", s.skills.XpTenths(Skills.MINING) == 175);
        Check("job over", !s.Busy());
        Check("said so", s.chat.Newest().equals(
                "You manage to mine some copper ore."));
        Fixture.Finish();
    }

    public void testRockIsEmptyThenRefills() {
        World world;
        Start(WIN, true);
        world = s.Here();
        s.Use(COPPER_X, ROCK_Y);

        int ticks = 0;
        while (!world.RockEmpty(COPPER_X, ROCK_Y)) {
            Check("mined in time", ++ticks < PLENTY_OF_TICKS);
            Run(1);
        }
        Check("called plain rocks", world.Name(COPPER_X, ROCK_Y)
                .equals("Rocks"));

        /* Clicking it while empty gets a refusal, not an ore. */
        s.Use(COPPER_X, ROCK_Y);
        Run(1);
        Check("refused", s.chat.Newest().startsWith("There is no ore"));

        Run(COPPER_RESPAWN - 2);
        Check("not yet", world.RockEmpty(COPPER_X, ROCK_Y));
        Run(1);
        Check("refilled", !world.RockEmpty(COPPER_X, ROCK_Y));
        Check("named again", world.Name(COPPER_X, ROCK_Y)
                .equals("Copper rocks"));
        Check("still one ore", Count(Item.COPPER_ORE) == 1);
        Fixture.Finish();
    }

    public void testLosingSwingsMineNothing() {
        Start(LOSE, true);
        s.Use(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no ore", Count(Item.COPPER_ORE) == 0);
        Check("no xp", s.skills.XpTenths(Skills.MINING) == 0);
        Check("still trying", s.Busy());
        Check("and can be called off", !s.Held());
        Fixture.Finish();
    }

    public void testSwingsLandEveryThirdTickUntilOneWins() {
        Start(LOSE, true);
        s.Use(COPPER_X, ROCK_Y);

        /* One tick to step beside the rock and start; swings then land
           on ticks 4, 7, 10, and so on. */
        Run(8);
        Check("two lost swings, nothing mined",
                Count(Item.COPPER_ORE) == 0 && s.Busy());

        Fixture.SetRoll(WIN);
        Run(1);
        Check("tick 9 is between swings", Count(Item.COPPER_ORE) == 0);
        Run(1);
        Check("tick 10 wins", Count(Item.COPPER_ORE) == 1 && !s.Busy());
        Check("the swing was announced once, not per swing",
                Fixture.Said("You swing your hammer at the rock.") == 1);
        Fixture.Finish();
    }

    public void testLosingTheHammerStopsTheNextSwing() {
        Start(LOSE, true);
        s.Use(COPPER_X, ROCK_Y);
        Run(2);
        s.inventory.Remove(0);
        Run(3);

        Check("stopped", !s.Busy());
        Check("told why", s.chat.Newest().equals(
                "You need a hammer to mine this rock."));
        Fixture.Finish();
    }

    public void testEveryOreRockSharesTheOneScript() {
        Start(WIN, true);
        s.skills.AddXp(Skills.MINING, Skills.XpForLevel(15) * 10);
        int xp = s.skills.XpTenths(Skills.MINING);

        s.Use(IRON_X, ROCK_Y);
        int ticks = 0;
        while (!s.Here().RockEmpty(IRON_X, ROCK_Y)) {
            Check("mined in time", ++ticks < PLENTY_OF_TICKS);
            Run(1);
        }

        Check("its own respawn time",
                s.Here().RockEmptyUntil(IRON_X, ROCK_Y) - s.Tick() == 9);
        Check("its own ore", Count(Item.IRON_ORE) == 1 &&
                Count(Item.COPPER_ORE) == 0);
        Check("its own experience",
                s.skills.XpTenths(Skills.MINING) == xp + 350);
        Check("its own message", Fixture.Said(
                "You manage to mine some iron ore.") == 1);
        Fixture.Finish();
    }

    public void testLevelUpIsAnnounced() {
        Start(WIN, true);
        s.skills.AddXp(Skills.MINING, 800);     /* 3.0 short of level 2 */
        s.Use(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("level 2", s.skills.Level(Skills.MINING) == 2);
        Check("announced", s.chat.Newest().equals("Congratulations, you " +
                "just advanced a Mining level! Your Mining level is now 2."));
        Fixture.Finish();
    }

    public void testIronNeedsLevelFifteen() {
        Start(WIN, true);
        s.Use(IRON_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no iron", Count(Item.IRON_ORE) == 0);
        Check("told why", s.chat.Newest().equals(
                "You need a Mining level of 15 to mine this rock."));

        s.skills.AddXp(Skills.MINING, Skills.XpForLevel(15) * 10);
        s.Use(IRON_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);
        Check("iron at 15", Count(Item.IRON_ORE) == 1);
        Fixture.Finish();
    }

    public void testNoHammerNoMining() {
        Start(WIN, false);
        s.Use(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no ore", Count(Item.COPPER_ORE) == 0);
        Check("told why", s.chat.Newest().equals(
                "You need a hammer to mine this rock."));
        Fixture.Finish();
    }

    public void testFullInventoryStopsMining() {
        Start(WIN, true);
        while (!s.inventory.Full()) s.inventory.Add(Item.IRON_ORE);
        s.Use(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no ore", Count(Item.COPPER_ORE) == 0);
        Check("told why", s.chat.Newest().startsWith(
                "Your inventory is too full"));
        Check("rock untouched", !s.Here().RockEmpty(COPPER_X, ROCK_Y));
        Fixture.Finish();
    }

    public void testWalkingAwayCancels() {
        Start(LOSE, true);
        s.Use(COPPER_X, ROCK_Y);
        Run(5);
        Check("mining", s.Busy());

        s.WalkTo(2, 2);
        Check("forgotten", !s.Busy());
        Run(PLENTY_OF_TICKS);
        Check("and stays forgotten", !s.Busy() &&
                s.player.X() == 2 && s.player.Y() == 2);
        Fixture.Finish();
    }

    public void testLevelCurve() {
        Skills skills = new Skills();

        Check("level 2 at 83", Skills.XpForLevel(2) == 83);
        Check("level 99 at 13,034,431",
                Skills.XpForLevel(Skills.MAX_LEVEL) == 13034431);
        Check("starts at 1", skills.Level(Skills.MINING) == 1);
        Check("82.9 is not enough", !skills.AddXp(Skills.MINING, 829));
        Check("83.0 is", skills.AddXp(Skills.MINING, 1));
        Check("now 2", skills.Level(Skills.MINING) == 2);
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
        Fixture.Start(roll);
        s = Fixture.session;
        if (withHammer) s.inventory.Add(Item.BRONZE_HAMMER);
    }
}
