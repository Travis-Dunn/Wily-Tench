package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

/**
 * The map file format: reading it, writing it back in one fixed form,
 * the symbols a map defines for itself, the floor worked out under
 * things, and the explanations of mistakes.
 *
 * The maps here are written out in the tests and use the game's real
 * "cave" tileset, in which "#" is a wall, "." and "," are floors, in
 * that order, and "r" is copper rocks.
 */
public class MapTest {
    private static final String HEAD = "!name Test\n!tileset cave\n";

    public void testAFileIsWrittenBackInOneFixedForm() {
        String messy = "!tileset cave\r\n\r\n\r\n!define H npc harl\n" +
                "!name   A Fine Cave  \n!define 1 point here on ,\n" +
                "  !map  \n####   \n#@1H#\n####\n\n   \n";
        String tidy = "!name A Fine Cave\n!tileset cave\n\n" +
                "!define 1 point here on ,\n!define H npc harl\n\n" +
                "!map\n####\n#@1H#\n####\n";

        String written = MapFile.Parse(messy, "m.txt").Write();
        Check("tidied; got:\n" + written, written.equals(tidy));
        Check("and tidying what is tidy changes nothing",
                MapFile.Parse(tidy, "m.txt").Write().equals(tidy));

        String plain = HEAD + "\n!map\n###\n#@#\n###\n";
        Check("a map with no defines has no gap for them",
                MapFile.Parse(plain, "m.txt").Write().equals(plain));
    }

    public void testEmptySpaceAroundAMapIsReadButNotWritten() {
        MapFile file = MapFile.Parse(HEAD + "!map\n\n  ###\n  #@#\n" +
                "\n   ##\n\n", "m.txt");

        Check("read as it is: five rows", file.rows.length == 5);
        Check("the blank one first", file.rows[0].length() == 0);
        Check("indentation and all", file.rows[1].equals("  ###"));

        String written = file.Write();
        Check("written without it; got:\n" + written, written.equals(
                HEAD + "\n!map\n###\n#@#\n\n ##\n"));
    }

    public void testTheGamesOwnMapsAreInThatForm() {
        Fixture.Start(0);
        String[][] lists = { Content.Maps(), Fixture.MAPS };

        for (int l = 0; l < lists.length; l++) {
            for (int i = 0; i < lists[l].length; i++) {
                String name = lists[l][i], text = MapFile.Read(name);
                Check(name + " is not in canonical form. Run the " +
                        "\"Build Maps\" configuration, or \"mvn " +
                        "test-compile exec:java@maps\", which rewrites it.",
                        MapFile.Parse(text, name).Write().equals(text));
            }
        }
        Fixture.Finish();
    }

    public void testMistakesInAFilesLayoutAreExplained() {
        Refused("no !map", HEAD + "###\n#@#\n", "before the [!map] line");
        Refused("nothing but settings", HEAD, "there is no [!map] line");
        Refused("no name", "!tileset cave\n!map\n@\n", "no [!name]");
        Refused("no tileset", "!name X\n!map\n@\n", "no [!tileset]");
        Refused("an unknown setting", HEAD + "!music loud\n!map\n@\n",
                "is not one a map file has");
        Refused("an unknown kind", HEAD + "!define 1 monster rat\n!map\n@\n",
                "no kind of define called [monster]");
        Refused("half a define", HEAD + "!define 1 point\n!map\n@\n",
                "a define is written");
        Refused("a word for a symbol", HEAD + "!define ab point x\n!map\n@",
                "a define is written");
        Refused("the start redefined", HEAD + "!define @ point x\n!map\n@",
                "cannot be defined");
        Refused("a symbol defined twice", HEAD + "!define 1 point a\n" +
                "!define 1 point b\n!map\n@\n", "is defined twice");
        Refused("like a word", HEAD + "!define 1 like rr\n!map\n@\n",
                "the one symbol of the thing to copy");
        Refused("a tab", HEAD + "!map\n#\t#\n", "there is a tab");
    }

    public void testMistakesInWhatAMapSaysAreExplained() {
        Fixture.Start(0);

        RefusedMap("an unknown tileset", "!name X\n!tileset swamp\n!map\n@",
                "no tileset called [swamp]");
        RefusedMap("an unknown symbol", HEAD + "!map\n###\n#@?\n",
                "the symbol [?] at column 3 of row 2");
        RefusedMap("no start", HEAD + "!map\n...\n",
                "exactly one player start");
        RefusedMap("two starts", HEAD + "!map\n@.@\n", "this has 2");
        RefusedMap("no tiles", HEAD + "!map\n", "the map has no tiles");
        RefusedMap("a symbol the tileset has", HEAD +
                "!define r point x\n!map\n@r\n", "already uses that symbol");
        RefusedMap("an unknown npc", HEAD + "!define 1 npc bob\n!map\n@1\n",
                "no npc called [bob]");
        RefusedMap("like something that is not a thing", HEAD +
                "!define 1 like .\n!map\n@1\n", "is not a thing");
        RefusedMap("on something that is not a floor", HEAD +
                "!define 1 like r on #\n!map\n@1\n", "names no floor");
        RefusedMap("a define never used", HEAD +
                "!define 1 point x\n!map\n@.\n", "is nowhere on the map");
        RefusedMap("a point in two places", HEAD +
                "!define 1 point x\n!map\n@11\n", "A point is one place");
        RefusedMap("two points of one name", HEAD + "!define 1 point x\n" +
                "!define 2 point x\n!map\n@12\n", "two points called [x]");
        Fixture.Finish();
    }

    public void testAMapsOwnSymbolsPlacePeopleAndCopies() {
        Fixture.Start(0);
        World world = Build(HEAD + "!define 2 like r\n!define H npc harl\n" +
                "!map\n#####\n#@H2#\n#####\n");

        Check("Harl is there", world.NpcAt(2, 1) == Npc.Named("harl"));
        Check("named and solid", world.Name(2, 1).equals("Harl") &&
                world.Solid(2, 1));
        Check("examined as the npc file says", world.Examine(2, 1).equals(
                "An old smith. His hands have seen a lot of work."));
        Check("drawn", world.ObjectPicture(2, 1, 0) != World.NO_PICTURE);

        Check("the copy is copper rocks",
                world.LocAt(3, 1) == Loc.Named("copper_rocks"));
        Check("and nobody", world.NpcAt(3, 1) == null);
        Fixture.Finish();
    }

    public void testPointsNameSpotsThatCanBeStoodOn() {
        Fixture.Start(0);
        World world = Build(HEAD + "!define 1 point by_door\n" +
                "!define 2 point far_end\n!map\n######\n#@1.2#\n######\n");

        Check("two points", world.Points() == 2);
        int door = world.PointNamed("by_door");
        int far = world.PointNamed("far_end");
        Check("found", door >= 0 && far >= 0 && door != far);
        Check("where they were put", world.PointX(door) == 2 &&
                world.PointY(door) == 1 && world.PointX(far) == 4);
        Check("open ground", !world.Solid(2, 1) && world.Name(2, 1) == null);
        Check("no such point", world.PointNamed("cellar") == -1);

        Session s = Fixture.session;
        int map = s.MapNamed(Fixture.MAPS[Fixture.SECOND]);
        s.TravelTo(map, 1, 2);
        Check("travel can land on a chosen tile", s.Map() == map &&
                s.player.X() == 1 && s.player.Y() == 2);
        Fixture.Finish();
    }

    public void testTheFloorUnderAThingComesFromTheTilesBesideIt() {
        Fixture.Start(0);
        /* Each map differs from "plain" only in what is at (2,2), so
           that tile's floor can be compared like for like. */
        String allFine = "#####\n#@,,#\n#,%s,#\n#,,,#\n#####\n";
        String allRough = "#####\n#@..#\n#.%s.#\n#...#\n#####\n";
        String mostlyFine = "#####\n#@,.#\n#,%s.#\n#,,.#\n#####\n";
        String half = "#####\n#@,.#\n#,%s.#\n#,..#\n#####\n";

        int fine = FloorAt(allFine, ",", "");
        int rough = FloorAt(allRough, ".", "");
        Check("the two floors look different here", fine != rough);

        Check("a rock among fine floor stands on fine floor",
                FloorAt(allFine, "r", "") == fine);
        Check("a rock among rough floor stands on rough floor",
                FloorAt(allRough, "r", "") == rough);
        Check("most of the neighbors win, even the later floor",
                FloorAt(mostlyFine, "r", "") ==
                FloorAt(mostlyFine, ",", ""));
        Check("a tie goes to the tileset's earlier floor",
                FloorAt(half, "r", "") == FloorAt(half, ".", ""));
        Check("unless the map says otherwise",
                FloorAt(allRough, "2", "!define 2 like r on ,\n") == fine);
        Check("a point gets a floor the same way",
                FloorAt(allFine, "1", "!define 1 point x\n") == fine);


        String walled = "#####\n#@###\n##%s##\n#####\n";
        Check("with no floor beside it, the tileset's first floor",
                FloorAt(walled, "r", "") == FloorAt(walled, ".", ""));
        Fixture.Finish();
    }

    /* The floor picture at (2,2) of a map made by putting a symbol
       into a grid, with any defines it needs. */
    private static int FloorAt(String grid, String symbol, String defines) {
        return Build(HEAD + defines + "!map\n" +
                grid.replace("%s", symbol)).FloorPicture(2, 2);
    }

    private static World Build(String text) {
        return World.Build("m.txt", MapFile.Parse(text, "m.txt"), true);
    }

    private static void Refused(String what, String text, String expected) {
        try {
            MapFile.Parse(text, "m.txt");
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }

    private static void RefusedMap(String what, String text,
                                   String expected) {
        try {
            Build(text);
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }
}
