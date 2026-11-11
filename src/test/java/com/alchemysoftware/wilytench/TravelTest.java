package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;
import static com.alchemysoftware.wilytench.Fixture.Count;
import static com.alchemysoftware.wilytench.Fixture.Run;

/**
 * Moves the player between maps. Fixture describes the two maps.
 */
public class TravelTest {
    private static final int WIN = 0;

    public void testTravelPutsThePlayerAtTheOtherMapsStart() {
        Fixture.Start(WIN);
        Session s = Fixture.session;
        World first = s.worlds[Fixture.FIRST];
        World second = s.worlds[Fixture.SECOND];

        Check("begins on the first map", s.Here() == first);
        s.Travel(Fixture.SECOND);

        Check("arrived", s.Here() == second);
        Check("at its start", s.player.X() == second.startX &&
                s.player.Y() == second.startY);
        Check("announced", s.chat.Newest().equals(
                "You arrive in the Second Room."));

        s.Travel(Fixture.FIRST);
        Check("and back", s.Here() == first &&
                s.player.X() == first.startX);
        Fixture.Finish();
    }

    public void testTravelDropsTheWalkAndTheJob() {
        Fixture.Start(WIN);
        Session s = Fixture.session;

        s.inventory.Add(Fixture.Item("bronze_pickaxe"));
        s.Use(3, 1);
        s.Travel(Fixture.SECOND);
        Run(10);

        Check("nothing mined", Count(Fixture.Item("copper_ore")) == 0);
        Check("standing still", !s.player.Walking() && !s.Busy());
        Fixture.Finish();
    }

    public void testWalkingWorksOnTheNewMap() {
        Fixture.Start(WIN);
        Session s = Fixture.session;

        s.Travel(Fixture.SECOND);
        Check("route found", s.WalkTo(1, 1));
        Run(5);
        Check("walked it", s.player.X() == 1 && s.player.Y() == 1);
        Fixture.Finish();
    }

    public void testRocksRefillWhileThePlayerIsAway() {
        Fixture.Start(WIN);
        Session s = Fixture.session;
        World first = s.worlds[Fixture.FIRST];

        s.inventory.Add(Fixture.Item("bronze_pickaxe"));
        s.Use(3, 1);
        while (!first.RockEmpty(3, 1)) Run(1);

        s.Travel(Fixture.SECOND);
        Run(Loc.Named("copper_rocks").Int("respawn").intValue() + 5);
        s.Travel(Fixture.FIRST);

        Check("refilled on return", !first.RockEmpty(3, 1));
        Check("counted as refilled", first.EmptyRocks() == 0);
        Fixture.Finish();
    }

    public void testEachMapKeepsItsOwnRocks() {
        Fixture.Start(WIN);
        Session s = Fixture.session;
        World first = s.worlds[Fixture.FIRST];
        World second = s.worlds[Fixture.SECOND];

        first.EmptyRock(3, 1, 1000);
        Check("first map's rock", first.RockEmpty(3, 1));
        Check("not the second's", !second.RockEmpty(3, 1));
        Check("no rock, no effect", second.EmptyRocks() == 0);

        second.EmptyRock(1, 1, 1000);    /* bare floor */
        Check("floor cannot be emptied", second.EmptyRocks() == 0);
        Fixture.Finish();
    }
}
