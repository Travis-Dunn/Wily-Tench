package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;
import static com.alchemysoftware.wilytench.Fixture.Count;
import static com.alchemysoftware.wilytench.Fixture.Run;

/**
 * Plays the mining rules through, tick by tick. Fixture describes the
 * map.
 */
public class MiningTest {
    private static final int COPPER_X = 3, IRON_X = 4, ROCK_Y = 1;
    private static final int PLENTY_OF_TICKS = 40;
    private static final int WIN = 0, LOSE = 255;

    private Session s;

    public void testWinningSwingGivesOreAndXpAndEmptiesRock() {
        Start(WIN, true);
        s.player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("one ore mined", Count(Item.COPPER_ORE) == 1);
        Check("17.5 xp", s.skills.XpTenths(Skills.MINING) == 175);
        Check("job over", !s.player.HasTarget());
        Check("said so", s.chat.Newest().equals(
                "You manage to mine some copper ore."));
        Fixture.Finish();
    }

    public void testRockIsEmptyThenRefills() {
        World world;
        Start(WIN, true);
        world = s.Here();
        s.player.WalkToTarget(COPPER_X, ROCK_Y);

        int ticks = 0;
        while (!world.RockEmpty(COPPER_X, ROCK_Y)) {
            Check("mined in time", ++ticks < PLENTY_OF_TICKS);
            Run(1);
        }
        Check("called plain rocks", world.Name(COPPER_X, ROCK_Y)
                .equals("Rocks"));

        /* Clicking it while empty gets a refusal, not an ore. */
        s.player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(1);
        Check("refused", s.chat.Newest().startsWith("There is no ore"));

        Run(Ore.RespawnTicks(Ore.COPPER) - 2);
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
        s.player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no ore", Count(Item.COPPER_ORE) == 0);
        Check("no xp", s.skills.XpTenths(Skills.MINING) == 0);
        Check("still trying", s.player.HasTarget());
        Fixture.Finish();
    }

    public void testIronNeedsLevelFifteen() {
        Start(WIN, true);
        s.player.WalkToTarget(IRON_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no iron", Count(Item.IRON_ORE) == 0);
        Check("told why", s.chat.Newest().equals(
                "You need a Mining level of 15 to mine this rock."));

        s.skills.AddXp(Skills.MINING, Skills.XpForLevel(15) * 10);
        s.player.WalkToTarget(IRON_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);
        Check("iron at 15", Count(Item.IRON_ORE) == 1);
        Fixture.Finish();
    }

    public void testNoHammerNoMining() {
        Start(WIN, false);
        s.player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no ore", Count(Item.COPPER_ORE) == 0);
        Check("told why", s.chat.Newest().equals(
                "You need a hammer to mine this rock."));
        Fixture.Finish();
    }

    public void testFullInventoryStopsMining() {
        Start(WIN, true);
        while (!s.inventory.Full()) s.inventory.Add(Item.IRON_ORE);
        s.player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(PLENTY_OF_TICKS);

        Check("no ore", Count(Item.COPPER_ORE) == 0);
        Check("told why", s.chat.Newest().startsWith(
                "Your inventory is too full"));
        Check("rock untouched", !s.Here().RockEmpty(COPPER_X, ROCK_Y));
        Fixture.Finish();
    }

    public void testWalkingAwayCancels() {
        Start(LOSE, true);
        s.player.WalkToTarget(COPPER_X, ROCK_Y);
        Run(5);
        Check("mining", s.player.HasTarget());

        s.player.WalkTo(1, 2);
        Check("forgotten", !s.player.HasTarget());
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
