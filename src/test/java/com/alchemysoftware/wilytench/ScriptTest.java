package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;
import static com.alchemysoftware.wilytench.Fixture.Run;

/**
 * Uses the ladder and the stairs, whose behavior is all script.
 * Fixture describes the maps and what the scripts do.
 */
public class ScriptTest {
    private static final int WIN = 0;
    private static final int LADDER_X = 1, LADDER_Y = 2;
    private static final int STAIRS_X = 2, STAIRS_Y = 1;

    public void testLadderScriptRunsOnArrivalAndTravelsAfterItsDelay() {
        Fixture.Start(WIN);
        Session s = Fixture.session;

        Check("ladder is usable", s.Usable(LADDER_X, LADDER_Y));
        Check("floor is not", !s.Usable(2, 2));
        Check("a rock is, through its category", s.Usable(3, 1));
        Check("a wall is not", !s.Usable(0, 0));

        /* The start is directly above the ladder, so no walking. */
        s.Use(LADDER_X, LADDER_Y);
        Run(1);
        Check("script spoke", s.chat.Newest().equals("Down you go."));
        Check("held during the delay", s.Busy() && s.Held());
        s.WalkTo(5, 2);
        Check("orders are ignored meanwhile", !s.player.Walking());
        Check("not there yet", s.Map() == Fixture.FIRST);

        Run(1);
        Check("one tick is not two", s.Map() == Fixture.FIRST && s.Busy());
        Run(1);
        Check("arrived", s.Map() == Fixture.SECOND);
        Check("at the start", s.player.X() == s.Here().startX &&
                s.player.Y() == s.Here().startY);
        Check("script ran to its end", !s.Busy());
        Check("and set its variables", s.vars.getInt("climbed") == 1 &&
                s.vars.getString("last_place").equals("below"));
        Fixture.Finish();
    }

    public void testScriptsBranchOnPlayerVariables() {
        Fixture.Start(WIN);
        Session s = Fixture.session;

        s.Travel(Fixture.SECOND);
        s.Use(STAIRS_X, STAIRS_Y);
        Run(1);
        Check("back up at once", s.Map() == Fixture.FIRST);
        Check("fresh player", Said(s, "First time up."));

        s.vars.setInt("climbed", 1);
        s.Travel(Fixture.SECOND);
        s.Use(STAIRS_X, STAIRS_Y);
        Run(1);
        Check("seasoned player", Said(s, "Back again."));
        Fixture.Finish();
    }

    public void testWalksUpToTheLadderFirst() {
        Fixture.Start(WIN);
        Session s = Fixture.session;

        s.player.Place(5, 2);
        s.Use(LADDER_X, LADDER_Y);
        Run(2);
        Check("still walking, nothing said", s.chat.Newest() == null);
        Run(2);
        Check("spoke on arrival", s.chat.Newest().equals("Down you go."));
        Check("stopped beside it", s.player.X() == 2 && s.player.Y() == 2);
        Fixture.Finish();
    }

    public void testWalkingAwayBeforeArrivingCancels() {
        Fixture.Start(WIN);
        Session s = Fixture.session;

        s.player.Place(5, 2);
        s.Use(LADDER_X, LADDER_Y);
        Run(1);
        s.WalkTo(5, 1);
        Run(10);
        Check("never used", s.chat.Newest() == null &&
                s.Map() == Fixture.FIRST);
        Fixture.Finish();
    }

    public void testMiningStillWorksAlongsideScripts() {
        Fixture.Start(WIN);
        Session s = Fixture.session;

        s.inventory.Add(Item.BRONZE_HAMMER);
        s.Use(3, 1);
        Run(20);
        Check("ore mined", Fixture.Count(Item.COPPER_ORE) == 1);
        Check("no script involved", !s.Busy());
        Fixture.Finish();
    }

    public void testGameCanBeStartedAgainAfterClosing() {
        /* The script manager is shut down and started afresh for each
           game; twice in a row proves it comes back clean. */
        Fixture.Start(WIN);
        Fixture.Finish();
        Fixture.Start(WIN);
        Check("variables start over",
                Fixture.session.vars.getInt("climbed") == 0);
        Fixture.Finish();
    }

    /* The travel command adds its own line after the script's, so the
       script's is the one before the newest. */
    private static boolean Said(Session s, String line) {
        return s.chat.Line(1).equals(line);
    }
}
