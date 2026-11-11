package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;
import static com.alchemysoftware.wilytench.Fixture.Run;

/**
 * The option lists for tiles and items, what choosing an option does,
 * and how the right-click menu places itself and reads the pointer.
 * Fixture describes the map.
 */
public class MenuTest {
    private static final int SCREEN_W = 384, SCREEN_H = 216;

    private Session s;
    private Actions actions;
    private ContextMenu menu;

    public void testBareFloorOffersWalkingAndCancel() {
        Start();
        actions.ForTile(menu, 2, 2);

        Check("two options", menu.Count() == 2);
        Check("walk first", menu.Verb(0).equals("Walk here") &&
                menu.Subject(0) == null);
        Check("cancel last", menu.Verb(1).equals("Cancel"));
        Fixture.Finish();
    }

    public void testARockOffersMiningFirstThenTheRest() {
        Start();
        actions.ForTile(menu, 3, 1);

        Check("four options", menu.Count() == 4);
        Check("mine first", menu.Verb(0).equals("Mine") &&
                menu.Subject(0).equals("Copper rocks"));
        Check("then walk", menu.Verb(1).equals("Walk here"));
        Check("then examine", menu.Verb(2).equals("Examine") &&
                menu.Subject(2).equals("Copper rocks"));
        Check("cancel last", menu.Verb(3).equals("Cancel"));
        Fixture.Finish();
    }

    public void testAWallCanOnlyBeWalkedTowardAndExamined() {
        Start();
        actions.ForTile(menu, 0, 0);

        Check("three options", menu.Count() == 3);
        Check("walk first", menu.Verb(0).equals("Walk here"));
        Check("examine", menu.Verb(1).equals("Examine") &&
                menu.Subject(1).equals("Cave wall"));
        Fixture.Finish();
    }

    public void testAnItemOffersUseDropExamineCancel() {
        Start();
        s.inventory.Add(Fixture.Item("copper_ore"));
        actions.ForItem(menu, 0);

        Check("four options", menu.Count() == 4);
        Check("use first", menu.Verb(0).equals("Use") &&
                menu.Subject(0).equals("Copper ore"));
        Check("drop", menu.Verb(1).equals("Drop"));
        Check("examine", menu.Verb(2).equals("Examine"));
        Check("cancel", menu.Verb(3).equals("Cancel"));

        menu.Begin();
        actions.ForItem(menu, 5);
        Check("an empty slot offers nothing", menu.Count() == 0);
        Fixture.Finish();
    }

    public void testChoosingAnOptionDoesIt() {
        Start();
        s.inventory.Add(Fixture.Item("bronze_pickaxe"));
        s.inventory.Add(Fixture.Item("copper_ore"));

        actions.ForItem(menu, 1);
        actions.Do(menu, 2);
        Check("examine says the item's line",
                s.chat.Newest().equals("This needs refining."));
        actions.Do(menu, 0);
        Check("use selects the item", actions.SelectedSlot() == 1);
        actions.Do(menu, 1);
        Check("drop drops", s.inventory.At(1) == Item.NONE &&
                s.chat.Newest().equals("You drop the copper ore."));
        actions.Do(menu, 3);
        Check("cancel changes nothing", s.inventory.Used() == 1);

        menu.Begin();
        actions.ForTile(menu, 3, 1);
        actions.Do(menu, 2);
        Check("examining a rock", s.chat.Newest().equals(
                "A rocky outcrop with a coppery glint to it."));
        actions.Do(menu, 0);
        Run(20);
        Check("mining mines", Fixture.Count(Fixture.Item("copper_ore")) == 1);

        menu.Begin();
        actions.ForTile(menu, 5, 2);
        actions.Do(menu, 0);
        Run(10);
        Check("walking walks", s.player.X() == 5 && s.player.Y() == 2);
        Fixture.Finish();
    }

    public void testExaminingAnEmptyRockSaysSo() {
        Start();
        s.Here().EmptyRock(3, 1, s.Tick() + 50);
        actions.ForTile(menu, 3, 1);

        Check("called plain rocks", menu.Subject(0).equals("Rocks"));
        actions.Do(menu, 2);
        Check("and described as empty", s.chat.Newest().equals(
                "There is no ore left in it for the moment."));
        Fixture.Finish();
    }

    public void testTheMenuOpensWithItsHeadingUnderThePointer() {
        Start();
        actions.ForTile(menu, 3, 1);
        menu.Open(200, 100);

        Check("open", menu.IsOpen());
        Check("pointer is not on an option",
                menu.OptionAt(200, 100) == ContextMenu.NO_OPTION);
        Check("the first option is just below",
                menu.OptionAt(200, 100 + 12) == 0);
        Check("and the rest follow", OptionsDownFrom(200, 100) == 4);
        Fixture.Finish();
    }

    public void testTheMenuStaysInsideThePicture() {
        Start();
        actions.ForTile(menu, 3, 1);

        int[][] corners = {
            { 0, 0 }, { SCREEN_W - 1, 0 }, { 0, SCREEN_H - 1 },
            { SCREEN_W - 1, SCREEN_H - 1 }
        };
        for (int c = 0; c < corners.length; c++) {
            menu.Open(corners[c][0], corners[c][1]);

            /* Every option must be reachable somewhere on screen. */
            boolean[] found = new boolean[menu.Count()];
            for (int y = 0; y < SCREEN_H; y++) {
                for (int x = 0; x < SCREEN_W; x++) {
                    int option = menu.OptionAt(x, y);
                    if (option != ContextMenu.NO_OPTION)
                        found[option] = true;
                }
            }
            for (int i = 0; i < found.length; i++)
                Check("option " + i + " on screen at corner " + c, found[i]);
            menu.Close();
        }
        Fixture.Finish();
    }

    public void testStrayingFarFromTheMenuCountsAsLeavingIt() {
        Start();
        actions.ForTile(menu, 2, 2);
        menu.Open(200, 100);

        Check("on it", menu.Near(200, 100));
        Check("a little off it", menu.Near(200, 100 - 12));
        Check("well away", !menu.Near(200, 100 - 40));
        Check("well away sideways", !menu.Near(20, 100));
        Fixture.Finish();
    }

    public void testAnOpenMenusOptionsCannotChangeUnderIt() {
        Start();
        actions.ForTile(menu, 3, 1);
        menu.Open(200, 100);

        menu.Begin();
        actions.ForTile(menu, 2, 2);
        Check("still the rock's four options", menu.Count() == 4 &&
                menu.Verb(0).equals("Mine"));

        menu.Close();
        menu.Begin();
        actions.ForTile(menu, 2, 2);
        Check("replaced once closed", menu.Count() == 2);
        Fixture.Finish();
    }

    public void testAnEmptyListOpensNoMenu() {
        Start();
        menu.Begin();
        menu.Open(100, 100);
        Check("nothing to show", !menu.IsOpen());
        Fixture.Finish();
    }

    private void Start() {
        Fixture.Start(0);
        s = Fixture.session;
        actions = new Actions(s);
        menu = new ContextMenu(Font.Load("small.fnt"), SCREEN_W, SCREEN_H);
    }

    /* Counts the distinct options met going straight down from a
       point, which must come in order with none skipped. */
    private int OptionsDownFrom(int x, int y) {
        int seen = 0;
        for (; y < SCREEN_H; y++) {
            int option = menu.OptionAt(x, y);
            if (option == ContextMenu.NO_OPTION) continue;
            Check("in order", option == seen || option == seen - 1);
            if (option == seen) seen++;
        }
        return seen;
    }
}
