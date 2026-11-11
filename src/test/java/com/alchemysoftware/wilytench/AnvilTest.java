package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;
import static com.alchemysoftware.wilytench.Fixture.Count;
import static com.alchemysoftware.wilytench.Fixture.Run;
import static com.alchemysoftware.wilytench.Fixture.Said;

/**
 * Making things from bars at an anvil: the list of recipes a script
 * shows, picking from it, and the window that draws it. Played on the
 * forge map Fixture describes, with the game's real recipes.
 */
public class AnvilTest {
    private static final int ANVIL_X = 5, ANVIL_Y = 2;
    /* The recipes of the bronze list, in the order the file has them. */
    private static final int AXE = 0, SWORD = 2, WARHAMMER = 3;
    private static final int PLENTY_OF_TICKS = 40;
    private static final int AXE_XP = 125;

    private static final String MSG_BEGIN = "You set to work at the anvil.";

    private Session s;
    private int bar, axe, smithing, bronze;

    public void testABarUsedOnTheAnvilAsksWhatToMake() {
        Start(1);
        s.UseItemOn(0, ANVIL_X, ANVIL_Y);
        Check("not before arriving", s.OpenMake() == Recipes.NONE);
        Run(PLENTY_OF_TICKS);

        Check("the bronze list is offered", s.OpenMake() == bronze);
        Check("and waited at, however long", s.Busy() && Count(bar) == 1);
        Check("nothing said yet", Said(MSG_BEGIN) == 0);
        Fixture.Finish();
    }

    public void testPickingARecipeMakesIt() {
        Start(1);
        OpenList();

        s.ChooseMake(AXE);
        Check("the menu is gone", s.OpenMake() == Recipes.NONE);
        Check("work begins", s.chat.Newest().equals(MSG_BEGIN) && s.Busy());
        Run(2);
        Check("not yet", Count(axe) == 0);
        Run(1);
        Check("an axe", Count(axe) == 1 && Count(bar) == 0);
        Check("said so",
                s.chat.Newest().equals("You finish the bronze axe."));
        Check("12.5 xp", s.skills.XpTenths(smithing) == AXE_XP);
        Check("and no bar left for another", !s.Busy());
        Fixture.Finish();
    }

    public void testItGoesOnMakingTheSameThingWithoutAskingAgain() {
        Start(3);
        OpenList();
        s.ChooseMake(AXE);
        Run(PLENTY_OF_TICKS);

        Check("three axes", Count(axe) == 3 && Count(bar) == 0);
        Check("asked once", Said(MSG_BEGIN) == 1);
        Check("never asked again", s.OpenMake() == Recipes.NONE &&
                !s.Busy());
        Fixture.Finish();
    }

    public void testARecipeMayTakeSeveralBars() {
        Start(7);
        s.skills.AddXp(smithing, Skills.XpForLevel(9) * 10);
        int before = s.skills.XpTenths(smithing);
        OpenList();
        s.ChooseMake(WARHAMMER);
        Run(PLENTY_OF_TICKS);

        Check("two warhammers from seven bars",
                Count(Fixture.Item("bronze_warhammer")) == 2);
        Check("one bar over", Count(bar) == 1);
        Check("xp for each",
                s.skills.XpTenths(smithing) == before + 2 * 375);
        Fixture.Finish();
    }

    public void testTooLowALevelOrTooFewBarsIsExplained() {
        Start(1);
        OpenList();
        s.ChooseMake(SWORD);
        Check("level", s.chat.Newest().equals(
                "You need a Smithing level of 4 to make that."));
        Check("nothing made", Count(bar) == 1 && !s.Busy());

        s.skills.AddXp(smithing, Skills.XpForLevel(9) * 10);
        OpenList();
        s.ChooseMake(WARHAMMER);
        Check("bars", s.chat.Newest().equals(
                "You don't have enough bronze bars to make that."));
        Check("still nothing made", Count(bar) == 1 && !s.Busy());
        Fixture.Finish();
    }

    public void testTheMenuCanBeDeclined() {
        Start(1);
        OpenList();
        s.CloseMake();
        Check("closed", s.OpenMake() == Recipes.NONE && !s.Busy());

        OpenList();
        s.WalkTo(1, 1);
        Check("walking off closes it too", s.OpenMake() == Recipes.NONE);
        Run(PLENTY_OF_TICKS);
        Check("and nothing was made", Count(bar) == 1 &&
                Said(MSG_BEGIN) == 0);

        s.ChooseMake(AXE);
        Check("a pick with no menu open is ignored", !s.Busy());
        Fixture.Finish();
    }

    public void testOnlyThingsWithAListCanBeWorked() {
        Start(0);
        s.inventory.Add(Fixture.Item("copper_ore"));
        s.UseItemOn(0, ANVIL_X, ANVIL_Y);
        Run(PLENTY_OF_TICKS);
        Check("ore is not for anvils", s.OpenMake() == Recipes.NONE &&
                s.chat.Newest().equals("Nothing interesting happens."));

        s.Use(ANVIL_X, ANVIL_Y);
        Run(2);
        Check("bare hands get a hint", s.chat.Newest().equals(
                "Use a bar of metal on the anvil to work it."));
        Fixture.Finish();
    }

    public void testTheWindowKnowsWhatIsUnderThePointer() {
        Start(2);
        MakeMenu menu = new MakeMenu(Font.Load("small.fnt"), s, 0, 0, 256,
                128);
        menu.Layout(bronze);

        for (int i = 0; i < Recipes.Count(bronze); i++) {
            Check("row " + i, menu.RecipeAt(menu.RowX(i) + 3,
                    menu.RowY(i) + 3) == i);
            Check("row " + i + " is on the window",
                    menu.Contains(menu.RowX(i) + 3, menu.RowY(i) + 3));
        }
        Check("the heading is no recipe", menu.Contains(menu.RowX(0) + 3,
                menu.RowY(0) - 6) && menu.RecipeAt(menu.RowX(0) + 3,
                menu.RowY(0) - 6) == MakeMenu.NO_RECIPE);
        Check("nor is anywhere off the window",
                !menu.Contains(0, 0) &&
                menu.RecipeAt(0, 0) == MakeMenu.NO_RECIPE);

        Check("an axe can be made", menu.CanMake(AXE));
        Check("a sword needs level 4", !menu.CanMake(SWORD));
        s.skills.AddXp(smithing, Skills.XpForLevel(9) * 10);
        Check("now it can", menu.CanMake(SWORD));
        Check("a warhammer needs a third bar", !menu.CanMake(WARHAMMER));
        s.inventory.Add(bar);
        Check("and has it", menu.CanMake(WARHAMMER));
        Fixture.Finish();
    }

    public void testMistakesInTheRecipeFileAreExplained() {
        Fixture.Start(0);
        String head = "\"id\": \"a\", \"title\": \"Make\", ";
        String good = "{ \"makes\": \"bronze_axe\", \"uses\": 1, " +
                "\"level\": 1, \"xp\": 12.5 }";

        Refused("a skill that is not one", "{ " + head + "\"skill\": " +
                "\"smelling\", \"material\": \"bronze_bar\", " +
                "\"recipes\": [ " + good + " ] }",
                "names the skill [smelling]");
        Refused("a material that is not an item", "{ " + head +
                "\"skill\": \"smithing\", \"material\": \"gold_bar\", " +
                "\"recipes\": [ " + good + " ] }",
                "[material] names the item [gold_bar]");
        Refused("no recipes", "{ " + head + "\"skill\": \"smithing\", " +
                "\"material\": \"bronze_bar\", \"recipes\": [ ] }",
                "[recipes] is empty");
        Refused("a product that is not an item", "{ " + head +
                "\"skill\": \"smithing\", \"material\": \"bronze_bar\", " +
                "\"recipes\": [ { \"makes\": \"bronze_spoon\", " +
                "\"uses\": 1, \"level\": 1, \"xp\": 1 } ] }",
                "recipe [bronze_spoon]: [makes] names the item");
        Refused("a recipe that uses nothing", "{ " + head +
                "\"skill\": \"smithing\", \"material\": \"bronze_bar\", " +
                "\"recipes\": [ { \"makes\": \"bronze_axe\", " +
                "\"uses\": 0, \"level\": 1, \"xp\": 1 } ] }",
                "[uses] must be from 1 to");
        Refused("a level past the top", "{ " + head +
                "\"skill\": \"smithing\", \"material\": \"bronze_bar\", " +
                "\"recipes\": [ { \"makes\": \"bronze_axe\", " +
                "\"uses\": 1, \"level\": 100, \"xp\": 1 } ] }",
                "[level] must be from 1 to 99");

        /* Put the real file back for whatever test runs next. */
        Recipes.Load(DataNode.Load("recipes.json"));
        Fixture.Finish();
    }

    private void Start(int bars) {
        Fixture.Start(0);
        s = Fixture.session;
        s.Travel(Fixture.FORGE);
        s.player.Place(ANVIL_X - 1, ANVIL_Y);
        bar = Fixture.Item("bronze_bar");
        axe = Fixture.Item("bronze_axe");
        smithing = Fixture.Skill("smithing");
        bronze = Recipes.Named("anvil_bronze_bar");
        Check("the bronze list exists", bronze != Recipes.NONE);
        for (int i = 0; i < bars; i++) s.inventory.Add(bar);
    }

    /* Uses the first bar on the anvil, from beside it, and waits for
       the menu. */
    private void OpenList() {
        s.UseItemOn(0, ANVIL_X, ANVIL_Y);
        Run(1);
        Check("the menu opened", s.OpenMake() == bronze);
    }

    private static void Refused(String what, String list, String expected) {
        try {
            Recipes.Load(DataNode.Parse("{ \"lists\": [ " + list + " ] }",
                    "recipes.json"));
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }
}
