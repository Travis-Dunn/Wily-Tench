package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

import java.util.Random;

/**
 * Checks the game's real data files, maps, and scripts, and that
 * mistakes in data files are caught and explained.
 *
 * The first test is the one that matters day to day: it fails the
 * build when anything in src/main/resources/data, maps, or scripts is
 * broken, with the same message the game would show.
 */
public class ContentTest {
    private static final int WIN = 0;

    public void testTheRealGameLoads() {
        Fixture.Start(WIN);     /* loads every data file */

        /* Swap the test maps and scripts for the game's own. */
        Session.Close();
        Session game = Session.Create(Content.Maps(), Content.ScriptFiles(),
                Fixture.session.chat, new Random());
        Check("every map and script the game lists loads", game != null);

        for (int m = 0; m < game.worlds.length; m++) {
            World world = game.worlds[m];
            Check(world.id + " start is open ground",
                    !world.Solid(world.startX, world.startY));
            for (int y = 0; y < world.h; y++) {
                for (int x = 0; x < world.w; x++) {
                    Loc loc = world.LocAt(x, y);
                    Check(world.id + ": the loc at " + x + "," + y +
                            " has a script", loc == null ||
                            Scripts.UseSubject(loc) != null);
                }
            }
        }
        Check("starting items fit", Content.StartingItems().length > 0);
        Fixture.Finish();
    }

    public void testItemMistakesAreExplained() {
        Fixture.Start(WIN);

        Refused("a misspelled value name", ItemsWith(
                "\"id\": \"gem\", \"nmae\": \"Gem\", \"sheet\": \"items\", " +
                "\"cell\": [0, 0]"),
                "item [gem]: there is a value called [nmae]");
        Refused("a missing value", ItemsWith(
                "\"id\": \"gem\", \"sheet\": \"items\", \"cell\": [0, 0]"),
                "item [gem]: [name] is missing");
        Refused("a cell off the sheet", ItemsWith(
                "\"id\": \"gem\", \"name\": \"Gem\", \"sheet\": \"items\", " +
                "\"cell\": [40, 0]"),
                "cell [40, 0] is outside the sheet [items]");
        Refused("an unknown sheet", ItemsWith(
                "\"id\": \"gem\", \"name\": \"Gem\", \"sheet\": \"gems\", " +
                "\"cell\": [0, 0]"),
                "there is no sheet called [gems]");
        Refused("text where a number belongs", ItemsWith(
                "\"id\": \"gem\", \"name\": \"Gem\", \"sheet\": \"items\", " +
                "\"cell\": [\"0\", 0]"),
                "must hold whole numbers");
        Refused("a duplicated id", "{ \"items\": [" +
                "{ \"id\": \"gem\", \"name\": \"Gem\", \"sheet\": \"items\"," +
                " \"cell\": [0, 0] }," +
                "{ \"id\": \"gem\", \"name\": \"Gem 2\", \"sheet\": " +
                "\"items\", \"cell\": [1, 0] } ] }",
                "there are two items with this id");
        Refused("a former id that is someone else's id", "{ \"items\": [" +
                "{ \"id\": \"gem\", \"name\": \"Gem\", \"sheet\": \"items\"," +
                " \"cell\": [0, 0] }," +
                "{ \"id\": \"jewel\", \"was\": [\"gem\"], \"name\": " +
                "\"Jewel\", \"sheet\": \"items\", \"cell\": [1, 0] } ] }",
                "[was] lists the id [gem], which belongs to another item");
        Refused("broken JSON", "{ \"items\": [ { \"id\": \"gem\", } ] }",
                "items.json is not valid JSON, at line 1");

        Fixture.Finish();
    }

    public void testLocMistakesAreExplained() {
        Fixture.Start(WIN);
        String category = "\"categories\": [ { \"id\": \"ore_rock\", " +
                "\"verb\": \"Mine\", \"empty_name\": \"Rocks\", \"params\": " +
                "{ \"ore\": \"item\", \"xp\": \"tenths\" } } ]";

        RefusedLocs("a missing parameter", category, "\"id\": \"tin_rocks\"," +
                " \"name\": \"Tin rocks\", \"category\": \"ore_rock\", " +
                "\"params\": { \"ore\": \"copper_ore\" }",
                "loc [tin_rocks], [params]: [xp] is missing");
        RefusedLocs("an ore that is not an item", category,
                "\"id\": \"tin_rocks\", \"name\": \"Tin rocks\", " +
                "\"category\": \"ore_rock\", \"params\": { \"ore\": " +
                "\"tin_ore\", \"xp\": 17.5 }",
                "names the item [tin_ore], and there is no such item");
        RefusedLocs("too many decimal places", category,
                "\"id\": \"tin_rocks\", \"name\": \"Tin rocks\", " +
                "\"category\": \"ore_rock\", \"params\": { \"ore\": " +
                "\"copper_ore\", \"xp\": 17.55 }",
                "may have at most one decimal place");
        RefusedLocs("an unknown category", category,
                "\"id\": \"tin_rocks\", \"name\": \"Tin rocks\", " +
                "\"category\": \"gem_rock\"",
                "there is no category called [gem_rock]");
        RefusedLocs("no verb anywhere", category,
                "\"id\": \"sign\", \"name\": \"Sign\"",
                "neither it nor its category has a [verb]");
        RefusedLocs("an id scripts could not name", category,
                "\"id\": \"Tin Rocks\", \"name\": \"Tin rocks\", " +
                "\"verb\": \"Mine\"",
                "an id may use only lower-case letters");

        Fixture.Finish();
    }

    public void testTenthsAreReadExactly() {
        DataNode node = DataNode.Parse(
                "{ \"a\": 17.5, \"b\": 35, \"c\": 0.1, \"d\": 1234.9 }",
                "test.json");

        Check("17.5", node.Tenths("a") == 175);
        Check("35", node.Tenths("b") == 350);
        Check("0.1", node.Tenths("c") == 1);
        Check("1234.9", node.Tenths("d") == 12349);
    }

    public void testNotesAreAllowedAnywhere() {
        DataNode node = DataNode.Parse(
                "{ \"note\": \"for people\", \"a\": 1 }", "test.json");
        node.Only(new String[] { "a" });
        Check("read past the note", node.Int("a") == 1);
    }

    private static String ItemsWith(String fields) {
        return "{ \"items\": [ { " + fields + " } ] }";
    }

    /* Loading the text as items.json must fail with a message that
       contains the expected words. */
    private static void Refused(String what, String json, String expected) {
        try {
            Item.Load(DataNode.Parse(json, "items.json"));
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }

    private static void RefusedLocs(String what, String category,
                                    String fields, String expected) {
        try {
            Loc.Load(DataNode.Parse("{ " + category + ", \"locs\": [ { " +
                    fields + " } ] }", "locs.json"));
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }
}
