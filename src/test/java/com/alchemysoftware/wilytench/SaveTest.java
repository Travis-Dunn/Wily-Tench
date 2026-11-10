package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;
import static com.alchemysoftware.wilytench.Fixture.Run;

import com.alchemysoftware.whitetail.save.SaveData;

/**
 * Saves a game and loads it back, in memory and through the disk.
 * Fixture describes the two maps.
 */
public class SaveTest {
    private static final int TIMED_WRITES = 50;
    private static final int WIN = 0;

    /* Where Scramble() leaves things. */
    private static final int SCRAMBLED_TICKS = 7;
    private static final int ROCK_UNTIL = 500;

    private Session s;
    private SaveState save;

    public void testStateSurvivesCaptureAndRestore() {
        Start();
        SaveData data = Scramble();

        Start();
        save.Restore(data);
        CheckScrambled();
        Fixture.Finish();
    }

    public void testChangedNoticesEachKindOfChange() {
        Start();
        Check("never saved", save.Changed());
        save.Capture();
        Check("just saved", !save.Changed());

        s.player.Place(2, 2);
        Check("moved", save.Changed());
        save.Capture();

        s.skills.AddXp(Skills.MINING, 175);
        Check("xp", save.Changed());
        save.Capture();

        s.inventory.Add(Item.COPPER_ORE);
        Check("inventory", save.Changed());
        save.Capture();

        s.TravelTo(Fixture.SECOND);
        Run(1);
        Check("map", save.Changed());
        save.Capture();
        Check("settled", !save.Changed());
        Fixture.Finish();
    }

    public void testIdleTicksAreNotChangesUnlessARockIsWaiting() {
        Start();
        save.Capture();
        Run(10);
        Check("nothing happening", !save.Changed());

        s.Here().EmptyRock(3, 1, s.Tick() + 3);
        Check("rock emptied", save.Changed());
        save.Capture();
        Run(1);
        Check("the wait itself is a change", save.Changed());
        save.Capture();

        Run(5);
        save.Capture();
        Run(1);
        Check("quiet again once it refills", !save.Changed());
        Fixture.Finish();
    }

    public void testEmptyRockStaysEmptyForTheRightTimeAfterLoading() {
        Start();
        Run(100);
        s.worlds[Fixture.FIRST].EmptyRock(3, 1, s.Tick() + 10);
        s.worlds[Fixture.SECOND].EmptyRock(3, 1, s.Tick() + 20);
        Run(4);
        SaveData data = save.Capture();

        Start();
        save.Restore(data);
        World first = s.worlds[Fixture.FIRST];
        Check("tick restored", s.Tick() == 104);
        Check("still empty", first.RockEmpty(3, 1));
        Check("named so", first.Name(3, 1).equals("Rocks"));

        Run(5);
        Check("six ticks to go, five gone", first.RockEmpty(3, 1));
        Run(1);
        Check("refills on time", !first.RockEmpty(3, 1));

        s.TravelTo(Fixture.SECOND);
        Run(1);
        Check("other map's rock too", s.Here().RockEmpty(3, 1));
        Run(9);
        Check("and it refills on time", !s.Here().RockEmpty(3, 1));
        Fixture.Finish();
    }

    public void testRefilledRocksAreNotSaved() {
        Start();
        s.Here().EmptyRock(3, 1, s.Tick() + 2);
        Run(5);
        SaveData data = save.Capture();

        Check("nothing to record",
                data.getBytes("rocks.test.txt").length == 0);
        Fixture.Finish();
    }

    public void testNonsenseInAFileIsMadeSafe() {
        Start();
        SaveData data = SaveData.Create();
        byte[] slots = new byte[Inventory.SLOTS * 2];
        byte[] rocks = {
            0, 1, 0, 1, 0, 0, 3, 0,     /* (1,1): bare floor */
            0, 99, 0, 99, 0, 0, 3, 0,   /* off the map */
            0, 3, 0, 1, 0, 0, 3         /* cut short */
        };

        /* Slot 0 holds item number 999; slot 1 a real one. */
        slots[0] = (byte) (1000 >> 8);
        slots[1] = (byte) 1000;
        slots[3] = (byte) (Item.IRON_ORE + 1);
        data.setBytes("inventory", slots);
        data.setBytes("rocks.test.txt", rocks);
        data.setString("map", Fixture.MAPS[Fixture.FIRST]);
        data.setInt("x", 0);    /* inside a wall */
        data.setInt("y", 0);
        data.setInt("tick", -40);
        data.setInt("xp.mining", -5);

        save.Restore(data);
        Check("unknown item dropped", s.inventory.At(0) == Item.NONE);
        Check("known item kept", s.inventory.At(1) == Item.IRON_ORE);
        Check("one item", s.inventory.Used() == 1);
        Check("not in the wall", s.player.X() == s.Here().startX);
        Check("xp not negative", s.skills.XpTenths(Skills.MINING) == 0);
        Check("tick not negative", s.Tick() == 0);
        Check("no phantom rocks", s.Here().EmptyRocks() == 0);
        Fixture.Finish();
    }

    public void testUnknownMapFallsBackToTheFirstMapsStart() {
        Start();
        SaveData data = SaveData.Create();
        data.setString("map", "atlantis.txt");
        data.setInt("x", 2);
        data.setInt("y", 2);

        save.Restore(data);
        Check("first map", s.Map() == Fixture.FIRST);
        Check("its start, not the saved tile",
                s.player.X() == s.Here().startX &&
                s.player.Y() == s.Here().startY);
        Fixture.Finish();
    }

    public void testEmptyFileChangesNothing() {
        Start();
        s.inventory.Add(Item.BRONZE_HAMMER);
        save.Restore(SaveData.Create());

        Check("hammer kept", s.inventory.At(0) == Item.BRONZE_HAMMER);
        Check("at start", s.player.X() == s.Here().startX);
        Fixture.Finish();
    }

    public void testStateSurvivesTheDisk() {
        Start();
        Fixture.DeleteSaves();

        Check("nothing yet", SaveGame.Load() == SaveGame.LOAD_NEW_GAME);
        Check("nothing loaded", SaveGame.Loaded() == null);
        SaveGame.Queue(Scramble());
        SaveGame.Shutdown();

        Start();
        Check("found", SaveGame.Load() == SaveGame.LOAD_OK);
        save.Restore(SaveGame.Loaded());
        CheckScrambled();

        /* How long one save takes on this machine, for the record. */
        long t0 = System.nanoTime();
        for (int i = 0; i < TIMED_WRITES; i++) {
            s.player.Place(1 + i % 2, 1);
            SaveGame.Queue(save.Capture());
            SaveGame.Flush();
        }
        System.out.println("SaveTest: one save takes about " +
                (System.nanoTime() - t0) / TIMED_WRITES / 1000 +
                " microseconds here");

        Check("no failures", !SaveGame.TakeFailure());
        SaveGame.Shutdown();
        Fixture.Finish();
    }

    public void testOnlyTheNewestQueuedStateMatters() {
        Start();
        Fixture.DeleteSaves();
        SaveGame.Load();

        for (int x = 1; x <= 5; x++) {
            s.player.Place(x, 2);
            SaveGame.Queue(save.Capture());
        }
        SaveGame.Shutdown();

        Start();
        Check("found", SaveGame.Load() == SaveGame.LOAD_OK);
        save.Restore(SaveGame.Loaded());
        Check("last position won", s.player.X() == 5 && s.player.Y() == 2);
        SaveGame.Shutdown();
        Fixture.Finish();
    }

    private void Start() {
        Fixture.Start(WIN);
        s = Fixture.session;
        save = new SaveState(s);
    }

    /* Puts the state somewhere distinctive and captures it. */
    private SaveData Scramble() {
        Run(SCRAMBLED_TICKS - 1);
        s.TravelTo(Fixture.SECOND);
        Run(1);
        s.player.Place(1, 2);
        s.skills.AddXp(Skills.MINING, 12345);
        s.inventory.Add(Item.BRONZE_HAMMER);
        s.inventory.Add(Item.COPPER_ORE);
        s.inventory.Add(Item.IRON_ORE);
        s.inventory.Remove(1);
        s.worlds[Fixture.FIRST].EmptyRock(4, 1, ROCK_UNTIL);
        return save.Capture();
    }

    private void CheckScrambled() {
        Check("map", s.Map() == Fixture.SECOND);
        Check("position", s.player.X() == 1 && s.player.Y() == 2);
        Check("tick", s.Tick() == SCRAMBLED_TICKS);
        Check("xp", s.skills.XpTenths(Skills.MINING) == 12345);
        Check("slot 0", s.inventory.At(0) == Item.BRONZE_HAMMER);
        Check("slot 1 empty", s.inventory.At(1) == Item.NONE);
        Check("slot 2", s.inventory.At(2) == Item.IRON_ORE);
        Check("two items", s.inventory.Used() == 2);
        Check("rock on the map left behind",
                s.worlds[Fixture.FIRST].RockEmptyUntil(4, 1) == ROCK_UNTIL);
    }
}
