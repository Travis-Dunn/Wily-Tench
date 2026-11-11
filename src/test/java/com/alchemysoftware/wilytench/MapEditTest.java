package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

/**
 * The map editor's changes to a map, with no window: painting,
 * growing, the symbols a map defines, and undoing. The maps use the
 * game's real "cave" tileset, in which "#" is a wall, "." and "," are
 * floors, in that order, and "r" is copper rocks.
 */
public class MapEditTest {
    private static final String ROOM = "!name Room\n!tileset cave\n\n" +
            "!map\n#####\n#@..#\n#...#\n#####\n";

    public void testPaintingChangesATile() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        Check("changed", edit.Paint(2, 2, 'r'));
        Check("there", edit.At(2, 2) == 'r');
        Check("painting the same again changes nothing",
                !edit.Paint(2, 2, 'r'));
        Check("a symbol that means nothing is refused",
                !edit.Paint(3, 2, '?') && edit.At(3, 2) == '.');
        Check("as is anywhere left of or above the map",
                !edit.Paint(-1, 2, 'r') && !edit.Paint(2, -1, 'r'));
        Check("off the grid reads as nothing", edit.At(40, 40) == ' ' &&
                edit.At(-1, 0) == ' ');
        Fixture.Finish();
    }

    public void testTheGridGrowsRightAndDownAsItIsPainted() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        edit.Paint(8, 1, '.');
        edit.Paint(2, 6, '#');
        Check("wider", edit.Width() == 9);
        Check("taller", edit.Height() == 7);
        Check("the gap is nothing", edit.At(6, 1) == ' ' &&
                edit.At(2, 5) == ' ');

        edit.Paint(8, 1, ' ');
        Check("painting nothing at the edge shrinks the row again",
                edit.File().rows[1].equals("#@..#"));
        Check("and what is written has no trailing space", edit.File()
                .Write().indexOf(" \n") < 0);
        Fixture.Finish();
    }

    public void testALineLeavesNoGaps() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        Check("changed", edit.Line(6, 1, 12, 4, '#'));
        Check("both ends", edit.At(6, 1) == '#' && edit.At(12, 4) == '#');
        for (int x = 6; x <= 12; x++) {
            int inColumn = 0;
            for (int y = 0; y < edit.Height(); y++)
                if (edit.At(x, y) == '#' && x > 4) inColumn++;
            Check("something in column " + x, inColumn >= 1);
        }
        Check("backward works as well", edit.Line(12, 6, 6, 6, '.') &&
                edit.At(9, 6) == '.');
        Check("a line of one tile", edit.Line(8, 8, 8, 8, '.') &&
                edit.At(8, 8) == '.');
        Check("a line of the start is one start",
                edit.Line(1, 2, 3, 2, '@') && edit.Count('@') == 1);
        Fixture.Finish();
    }

    public void testFillingARectangle() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        edit.Fill(3, 2, 2, 1, ',');
        Check("all four, whichever corners were given",
                edit.At(2, 1) == ',' && edit.At(3, 1) == ',' &&
                edit.At(2, 2) == ',' && edit.At(3, 2) == ',');
        Check("and nothing else", edit.At(1, 2) == '.');
        Fixture.Finish();
    }

    public void testTheStartAndPointsMoveRatherThanMultiply() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        edit.Paint(3, 2, '@');
        Check("one start", edit.Count('@') == 1 && edit.At(3, 2) == '@');
        Check("where it was is floor again", edit.At(1, 1) == '.');

        char point = edit.AddDefine(MapFile.KIND_POINT, "door",
                MapFile.NO_FLOOR);
        edit.Paint(1, 1, point);
        edit.Paint(2, 1, point);
        Check("one point", edit.Count(point) == 1 &&
                edit.At(2, 1) == point);
        edit.Fill(1, 2, 2, 2, point);
        Check("a rectangle of a point is one point",
                edit.Count(point) == 1 && edit.At(1, 2) == point);

        char harl = edit.AddDefine(MapFile.KIND_NPC, "harl",
                MapFile.NO_FLOOR);
        edit.Paint(1, 1, harl);
        edit.Paint(2, 1, harl);
        Check("people may be many", edit.Count(harl) == 2);
        Fixture.Finish();
    }

    public void testWhatIsLeftBehindMatchesTheFloorAroundIt() {
        MapEdit edit = Start("!name R\n!tileset cave\n\n!map\n" +
                "#####\n#,,,#\n#,@,#\n#,,,#\n#####\n");

        edit.Begin();
        edit.Paint(1, 1, '@');
        Check("the fine floor of the room, not the tileset's first",
                edit.At(2, 2) == ',');
        Fixture.Finish();
    }

    public void testDefinesGetFreeSymbolsAndCanBeRemoved() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        char a = edit.AddDefine(MapFile.KIND_NPC, "harl", MapFile.NO_FLOOR);
        char b = edit.AddDefine(MapFile.KIND_POINT, "door", ',');
        Check("digits first", a == '1' && b == '2');
        Check("known now", edit.Known(a) && edit.Defined(b).on == ',');

        edit.Paint(2, 1, a);
        edit.Paint(3, 1, a);
        edit.Paint(2, 2, b);
        String text = edit.File().Write();
        Check("written into the file; got:\n" + text, text.indexOf(
                "!define 1 npc harl\n!define 2 point door on ,\n") > 0);
        Check("and the file is a good map", World.Build("r.txt",
                MapFile.Parse(text, "r.txt"), true) != null);

        edit.RemoveDefine(a);
        Check("gone from the list", edit.Defined(a) == null &&
                edit.Defines().length == 1);
        Check("and from the map, leaving floor", edit.Count(a) == 0 &&
                edit.At(2, 1) == '.' && edit.At(3, 1) == '.');
        Check("its symbol is free again", edit.FreeSymbol() == a);
        Fixture.Finish();
    }

    public void testAFreeSymbolIsNeverOneTheTilesetUses() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        for (int i = 0; i < 40; i++) {
            char c = edit.AddDefine(MapFile.KIND_POINT, "p" + i,
                    MapFile.NO_FLOOR);
            Check("free symbol " + i, c != 0 && c != '!' && c != '@' &&
                    c != ' ' && c != '#' && c != '.' && c != 'r');
        }
        Check("all different", edit.Defines().length == 40);
        Fixture.Finish();
    }

    public void testUndoTakesBackAWholeStroke() {
        MapEdit edit = Start(ROOM);
        Check("nothing to undo at first", !edit.CanUndo() &&
                !edit.Changed());

        edit.Begin();
        edit.Paint(2, 1, 'r');
        edit.Paint(3, 1, 'r');
        edit.Begin();
        edit.Paint(2, 2, ',');
        Check("changed", edit.Changed());

        edit.Undo();
        Check("the second stroke is gone", edit.At(2, 2) == '.' &&
                edit.At(2, 1) == 'r');
        edit.Undo();
        Check("then all of the first", edit.At(2, 1) == '.' &&
                edit.At(3, 1) == '.');
        Check("which is how it was saved", !edit.Changed() &&
                !edit.CanUndo());

        edit.Redo();
        edit.Redo();
        Check("redo puts both back", edit.At(3, 1) == 'r' &&
                edit.At(2, 2) == ',' && !edit.CanRedo());

        edit.Undo();
        edit.Begin();
        edit.Paint(1, 2, 'r');
        Check("a new stroke forgets what was undone", !edit.CanRedo());
        Fixture.Finish();
    }

    public void testAStrokeThatChangesNothingLeavesNothingToUndo() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        edit.Paint(1, 2, '.');
        edit.End();
        Check("nothing happened, nothing to undo", !edit.CanUndo());

        edit.Begin();
        edit.Paint(1, 2, 'r');
        edit.End();
        Check("a real stroke stays", edit.CanUndo() &&
                edit.At(1, 2) == 'r');
        Fixture.Finish();
    }

    public void testUndoCoversDefinesAndTheName() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        edit.SetName("Elsewhere");
        char c = edit.AddDefine(MapFile.KIND_NPC, "harl", MapFile.NO_FLOOR);
        edit.Undo();
        Check("name back", edit.Name().equals("Room"));
        Check("define gone", edit.Defined(c) == null);
        Fixture.Finish();
    }

    public void testSavingIsRememberedAcrossUndo() {
        MapEdit edit = Start(ROOM);

        edit.Begin();
        edit.Paint(2, 1, 'r');
        edit.MarkSaved();
        Check("saved", !edit.Changed());
        edit.Undo();
        Check("undoing past a save is a change", edit.Changed());
        edit.Redo();
        Check("and redoing is not", !edit.Changed());
        Fixture.Finish();
    }

    public void testWhatIsSavedHasNoEmptySpaceAroundIt() {
        MapEdit edit = Start("!name R\n!tileset cave\n\n!map\n\n\n" +
                "   ###\n   #@#\n\n    ###\n");

        String[] rows = edit.File().rows;
        Check("four rows", rows.length == 4);
        Check("moved to the corner", rows[0].equals("###") &&
                rows[1].equals("#@#"));
        Check("a blank row inside is kept", rows[2].equals(""));
        Check("as is indentation beyond the least", rows[3].equals(" ###"));
        Check("the layout still has the space", edit.Layout().rows.length
                == 6 && edit.At(3, 2) == '#');
        Fixture.Finish();
    }

    public void testSettlingKeepsAMarginWithoutChangingWhatIsSaved() {
        MapEdit edit = Start(ROOM);
        String before = edit.File().Write();

        edit.Settle(10);
        Check("ten clear at the left and top", edit.Left() == 10 &&
                edit.Top() == 10 && edit.At(11, 11) == '@');
        Check("slid by ten", edit.SlidX() == 10 && edit.SlidY() == 10);
        Check("saved the same", edit.File().Write().equals(before));
        Check("so nothing has changed or moved", !edit.Changed() &&
                edit.MovedX() == 0 && edit.MovedY() == 0);

        edit.Settle(4);
        Check("it slides back as readily", edit.Left() == 4 &&
                edit.SlidX() == 4 && edit.At(5, 5) == '@');
        Fixture.Finish();
    }

    public void testTheMapCanBePaintedOutwardInEveryDirection() {
        MapEdit edit = Start(ROOM);
        edit.Settle(10);

        edit.Begin();
        edit.Paint(8, 11, '#');         /* two columns left of the room */
        edit.Paint(11, 7, '#');         /* three rows above it */
        edit.Settle(10);
        edit.End();

        Check("the margin is back", edit.Left() == 10 && edit.Top() == 10);
        Check("by sliding two right and three down",
                edit.SlidX() == 12 && edit.SlidY() == 13);
        Check("what was painted went with it", edit.At(10, 14) == '#' &&
                edit.At(13, 10) == '#');
        String[] rows = edit.File().rows;
        Check("saved from its new corner; got " + rows[0] + "|" + rows[4],
                rows.length == 7 && rows[0].equals("   #") &&
                rows[3].equals("  #####") && rows[4].equals("# #@..#"));
        Check("the old tiles are saved two right and three down",
                edit.MovedX() == 2 && edit.MovedY() == 3);

        edit.MarkSaved();
        Check("until that is saved", edit.MovedX() == 0 &&
                edit.MovedY() == 0);

        edit.Undo();
        Check("undo slides the map back with the stroke",
                edit.SlidX() == 10 && edit.At(11, 11) == '@');
        Check("and that is two left and three up of the save",
                edit.MovedX() == -2 && edit.MovedY() == -3);
        Fixture.Finish();
    }

    public void testErasingAnEdgeDrawsTheMarginIn() {
        MapEdit edit = Start(ROOM);
        edit.Settle(10);

        edit.Begin();
        edit.Fill(10, 10, 10, 13, ' ');     /* the room's left wall */
        edit.Settle(10);
        edit.End();
        Check("the map starts a column later, so slides one left",
                edit.Left() == 10 && edit.SlidX() == 9);
        Check("and the old tiles are saved one to the left",
                edit.MovedX() == -1);
        Fixture.Finish();
    }

    private static MapEdit Start(String text) {
        Fixture.Start(0);
        return new MapEdit(MapFile.Parse(text, "r.txt"));
    }
}
