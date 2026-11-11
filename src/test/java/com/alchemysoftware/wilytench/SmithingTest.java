package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;
import static com.alchemysoftware.wilytench.Fixture.Count;
import static com.alchemysoftware.wilytench.Fixture.Run;
import static com.alchemysoftware.wilytench.Fixture.Said;

/**
 * Using an item on something, and smelting bronze, which is the first
 * thing done that way. Played on the forge map Fixture describes, where
 * the player starts beside the furnace.
 */
public class SmithingTest {
    private static final int WIN = 0;
    private static final int FURNACE_X = 2, FURNACE_Y = 1;
    private static final int TIN_X = 2, TIN_Y = 3;
    private static final int COPPER_X = 4, COPPER_Y = 3;
    /* A tile across the room from the furnace. */
    private static final int FAR_X = 4, FAR_Y = 1;
    /* One tick to start, three for the bar to come out. */
    private static final int ONE_BAR = 4;
    private static final int PLENTY_OF_TICKS = 40;
    private static final int BAR_XP = 62;

    private static final String MSG_PUT_IN =
            "You put the copper and tin into the furnace.";
    private static final String MSG_BAR =
            "You take a bar of bronze out of the furnace.";
    private static final String MSG_NOTHING = "Nothing interesting happens.";

    private Session s;
    private int copper, tin, bar, smithing;

    public void testCopperAndTinBecomeABronzeBar() {
        Start();
        s.inventory.Add(copper);
        s.inventory.Add(tin);

        s.UseItemOn(0, FURNACE_X, FURNACE_Y);
        Run(1);
        Check("in they go", s.chat.Newest().equals(MSG_PUT_IN) && s.Busy());
        Run(ONE_BAR - 2);
        Check("not yet", Count(bar) == 0 && Count(copper) == 1);
        Run(1);
        Check("a bar", Count(bar) == 1);
        Check("the ore is gone", Count(copper) == 0 && Count(tin) == 0);
        Check("6.2 xp", s.skills.XpTenths(smithing) == BAR_XP);
        Check("and that is all there was to smelt", !s.Busy());
        Fixture.Finish();
    }

    public void testEitherOreStartsItAndItGoesOnUntilOneRunsOut() {
        Start();
        s.inventory.Add(copper);
        s.inventory.Add(copper);
        s.inventory.Add(copper);
        s.inventory.Add(tin);
        s.inventory.Add(tin);

        s.UseItemOn(3, FURNACE_X, FURNACE_Y);       /* a tin ore */
        Run(PLENTY_OF_TICKS);
        Check("two bars", Count(bar) == 2);
        Check("the odd copper is left", Count(copper) == 1 &&
                Count(tin) == 0);
        Check("xp for each", s.skills.XpTenths(smithing) == 2 * BAR_XP);
        Check("announced once, not per bar", Said(MSG_PUT_IN) == 1);
        Check("each bar announced", Said(MSG_BAR) == 2);
        Check("stopped without complaint", !s.Busy() &&
                s.chat.Newest().equals(MSG_BAR));
        Fixture.Finish();
    }

    public void testOneOreAloneIsNotEnough() {
        Start();
        s.inventory.Add(copper);
        s.UseItemOn(0, FURNACE_X, FURNACE_Y);
        Run(PLENTY_OF_TICKS);
        Check("told what is missing", s.chat.Newest().equals(
                "You need tin ore as well as copper ore to make bronze."));

        s.inventory.Remove(0);
        s.inventory.Add(tin);
        s.UseItemOn(0, FURNACE_X, FURNACE_Y);
        Run(PLENTY_OF_TICKS);
        Check("and the other way round", s.chat.Newest().equals(
                "You need copper ore as well as tin ore to make bronze."));
        Check("nothing made", Count(bar) == 0 && Count(tin) == 1 &&
                s.skills.XpTenths(smithing) == 0);
        Fixture.Finish();
    }

    public void testThingsThatDoNotGoTogetherDoNothing() {
        Start();
        s.inventory.Add(Fixture.Item("bronze_pickaxe"));
        s.inventory.Add(copper);

        s.UseItemOn(0, FURNACE_X, FURNACE_Y);
        Run(2);
        Check("a pickaxe will not smelt",
                s.chat.Newest().equals(MSG_NOTHING) && !s.Busy());

        s.UseItemOn(1, COPPER_X, COPPER_Y);
        Check("a rock takes nothing, and says so at once",
                Said(MSG_NOTHING) == 2);
        Run(PLENTY_OF_TICKS);
        Check("without a walk", s.player.X() == 1 && s.player.Y() == 1);

        s.UseItemOnItem(0, 1);
        Check("nor do two items combine", Said(MSG_NOTHING) == 3);
        s.UseItemOn(5, FURNACE_X, FURNACE_Y);
        s.UseItemOnItem(0, 5);
        Check("an empty slot is no item at all", Said(MSG_NOTHING) == 3);
        Fixture.Finish();
    }

    public void testThePlayerWalksToTheFurnaceFirst() {
        Start();
        s.inventory.Add(copper);
        s.inventory.Add(tin);
        s.player.Place(FAR_X, FAR_Y);

        s.UseItemOn(0, FURNACE_X, FURNACE_Y);
        Check("not from over here", !s.Busy());
        Run(PLENTY_OF_TICKS);
        Check("walked up beside it", s.player.X() == FURNACE_X + 1 &&
                s.player.Y() == FURNACE_Y);
        Check("and smelted", Count(bar) == 1);
        Fixture.Finish();
    }

    public void testOreDroppedOnTheWayOrInTheFireMakesNothing() {
        Start();
        s.inventory.Add(copper);
        s.inventory.Add(tin);
        s.player.Place(FAR_X, FAR_Y);

        s.UseItemOn(0, FURNACE_X, FURNACE_Y);
        s.DropItem(0);
        Run(PLENTY_OF_TICKS);
        Check("dropped on the way: nothing begins",
                Said(MSG_PUT_IN) == 0 && Count(bar) == 0);

        s.inventory.Add(copper);
        s.UseItemOn(0, FURNACE_X, FURNACE_Y);
        Run(2);
        Check("begun", s.Busy());
        s.DropItem(1);
        Run(PLENTY_OF_TICKS);
        Check("dropped while melting: no bar from one ore",
                Count(bar) == 0 && Count(copper) == 1 &&
                s.skills.XpTenths(smithing) == 0);
        Fixture.Finish();
    }

    public void testWalkingAwayStopsTheSmelting() {
        Start();
        for (int i = 0; i < 3; i++) {
            s.inventory.Add(copper);
            s.inventory.Add(tin);
        }
        s.UseItemOn(0, FURNACE_X, FURNACE_Y);
        Run(ONE_BAR + 1);
        Check("one bar in", Count(bar) == 1 && s.Busy());

        s.WalkTo(1, 2);
        Run(PLENTY_OF_TICKS);
        Check("and no more", Count(bar) == 1 && Count(copper) == 2);
        Fixture.Finish();
    }

    public void testAFurnaceUsedBareHandedExplainsItself() {
        Start();
        s.Use(FURNACE_X, FURNACE_Y);
        Run(2);
        Check("a hint", s.chat.Newest().equals(
                "The furnace is hot. Use an ore on it to smelt it."));
        Fixture.Finish();
    }

    public void testMiningBothOresAndSmeltingThem() {
        Start();
        s.inventory.Add(Fixture.Item("bronze_pickaxe"));

        s.Use(TIN_X, TIN_Y);
        Run(PLENTY_OF_TICKS);
        s.Use(COPPER_X, COPPER_Y);
        Run(PLENTY_OF_TICKS);
        Check("one of each mined", Count(tin) == 1 && Count(copper) == 1);
        Check("mining xp for both",
                s.skills.XpTenths(Fixture.Mining()) == 350);

        s.UseItemOn(1, FURNACE_X, FURNACE_Y);
        Run(PLENTY_OF_TICKS);
        Check("and smelted into a bar", Count(bar) == 1 &&
                Count(tin) == 0 && Count(copper) == 0);
        Fixture.Finish();
    }

    public void testUseSelectsAnItemAndTheNextClickUsesItOnSomething() {
        Start();
        Actions actions = new Actions(s);
        ContextMenu menu = new ContextMenu(Font.Load("small.fnt"), 384, 216);
        s.inventory.Add(copper);
        s.inventory.Add(tin);

        actions.ForItem(menu, 0);
        actions.Do(menu, 0);
        Check("copper selected", actions.SelectedSlot() == 0);

        menu.Begin();
        actions.ForTile(menu, FURNACE_X, FURNACE_Y);
        Check("the furnace offers it first", menu.Verb(0).equals("Use") &&
                menu.Subject(0).equals("Copper ore -> Furnace"));
        Check("then its own options", menu.Verb(1).equals("Smelt") &&
                menu.Count() == 5);

        menu.Begin();
        actions.ForTile(menu, 1, 2);
        Check("bare floor offers nothing new", menu.Count() == 2);

        menu.Begin();
        actions.ForItem(menu, 1);
        Check("another item offers it too",
                menu.Subject(0).equals("Copper ore -> Tin ore") &&
                menu.Count() == 5);
        menu.Begin();
        actions.ForItem(menu, 0);
        Check("but not the item itself", menu.Count() == 4);

        menu.Begin();
        actions.ForTile(menu, FURNACE_X, FURNACE_Y);
        actions.Do(menu, 0);
        Check("doing it lets go of the item",
                actions.SelectedSlot() == Actions.NO_SLOT);
        Run(PLENTY_OF_TICKS);
        Check("and smelts", Count(bar) == 1);
        Fixture.Finish();
    }

    public void testTheSelectionIsLetGoOfEasily() {
        Start();
        Actions actions = new Actions(s);
        ContextMenu menu = new ContextMenu(Font.Load("small.fnt"), 384, 216);
        s.inventory.Add(copper);
        s.inventory.Add(tin);

        actions.ForItem(menu, 0);
        actions.Do(menu, 0);
        menu.Begin();
        actions.ForTile(menu, 1, 2);
        actions.Do(menu, 0);
        Check("by walking somewhere",
                actions.SelectedSlot() == Actions.NO_SLOT);

        menu.Begin();
        actions.ForItem(menu, 0);
        actions.Do(menu, 0);
        s.DropItem(0);
        Check("by the item leaving its slot",
                actions.SelectedSlot() == Actions.NO_SLOT);

        menu.Begin();
        actions.ForItem(menu, 1);
        actions.Do(menu, 0);
        actions.Deselect();
        Check("or when told to",
                actions.SelectedSlot() == Actions.NO_SLOT);
        Fixture.Finish();
    }

    private void Start() {
        Fixture.Start(WIN);
        s = Fixture.session;
        s.Travel(Fixture.FORGE);
        copper = Fixture.Item("copper_ore");
        tin = Fixture.Item("tin_ore");
        bar = Fixture.Item("bronze_bar");
        smithing = Fixture.Skill("smithing");
    }
}
