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

        s.inventory.Add(Fixture.Item("copper_ore"));
        Check("inventory", save.Changed());
        save.Capture();

        s.Travel(Fixture.SECOND);
        Check("map", save.Changed());
        save.Capture();

        s.vars.setInt("climbed", 1);
        Check("script whole number", save.Changed());
        save.Capture();

        s.vars.setString("last_place", "below");
        Check("script text", save.Changed());
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

        s.Travel(Fixture.SECOND);
        Check("other map's rock too", s.Here().RockEmpty(3, 1));
        Run(10);
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
        byte[] rocks = {
            0, 1, 0, 1, 0, 0, 3, 0,     /* (1,1): bare floor */
            0, 99, 0, 99, 0, 0, 3, 0,   /* off the map */
            0, 3, 0, 1, 0, 0, 3         /* cut short */
        };

        /* Slot 0 holds an item there is no such thing as; slot 1 a
           real one; then far more slots than there are. */
        StringBuffer slots = new StringBuffer("philosophers_stone,iron_ore");
        for (int i = 0; i < 100; i++) slots.append(",copper_ore");
        data.setString("inventory", slots.toString());
        data.setBytes("rocks.test.txt", rocks);
        data.setString("map", Fixture.MAPS[Fixture.FIRST]);
        data.setInt("x", 0);    /* inside a wall */
        data.setInt("y", 0);
        data.setInt("tick", -40);
        data.setInt("xp.mining", -5);
        data.setString("var.climbed", "yes");   /* should be a number */
        data.setInt("var.no_such_variable", 7);

        save.Restore(data);
        Check("unknown item dropped", s.inventory.At(0) == Item.NONE);
        Check("known item kept", s.inventory.At(1) == Fixture.Item("iron_ore"));
        Check("no more slots than there are",
                s.inventory.Used() == Inventory.SLOTS - 1);
        Check("not in the wall", s.player.X() == s.Here().startX);
        Check("xp not negative", s.skills.XpTenths(Skills.MINING) == 0);
        Check("tick not negative", s.Tick() == 0);
        Check("no phantom rocks", s.Here().EmptyRocks() == 0);
        Check("mistyped variable ignored", s.vars.getInt("climbed") == 0);
        Fixture.Finish();
    }

    public void testSavesFromBeforeItemsHadIdsStillLoad() {
        Start();
        SaveData data = SaveData.Create();
        byte[] slots = new byte[Inventory.SLOTS * 2];

        /* Two bytes a slot: the old item number plus one. Hammer was
           0, copper ore 1, iron ore 2. */
        slots[1] = 1;
        slots[3] = 3;
        slots[7] = 2;
        slots[9] = 40;      /* never an item */
        data.setBytes("inventory", slots);

        save.Restore(data);
        Check("hammer", s.inventory.At(0) == Fixture.Item("bronze_hammer"));
        Check("iron ore", s.inventory.At(1) == Fixture.Item("iron_ore"));
        Check("gap kept", s.inventory.At(2) == Item.NONE);
        Check("copper ore", s.inventory.At(3) == Fixture.Item("copper_ore"));
        Check("nonsense dropped", s.inventory.Used() == 3);
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
        s.inventory.Add(Fixture.Item("bronze_hammer"));
        save.Restore(SaveData.Create());

        Check("hammer kept", s.inventory.At(0) == Fixture.Item("bronze_hammer"));
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
        Run(SCRAMBLED_TICKS);
        s.Travel(Fixture.SECOND);
        s.player.Place(1, 2);
        s.vars.setInt("climbed", 1);
        s.vars.setString("last_place", "somewhere odd");
        s.skills.AddXp(Skills.MINING, 12345);
        s.inventory.Add(Fixture.Item("bronze_hammer"));
        s.inventory.Add(Fixture.Item("copper_ore"));
        s.inventory.Add(Fixture.Item("iron_ore"));
        s.inventory.Remove(1);
        s.worlds[Fixture.FIRST].EmptyRock(4, 1, ROCK_UNTIL);
        return save.Capture();
    }

    private void CheckScrambled() {
        Check("map", s.Map() == Fixture.SECOND);
        Check("position", s.player.X() == 1 && s.player.Y() == 2);
        Check("tick", s.Tick() == SCRAMBLED_TICKS);
        Check("xp", s.skills.XpTenths(Skills.MINING) == 12345);
        Check("slot 0", s.inventory.At(0) == Fixture.Item("bronze_hammer"));
        Check("slot 1 empty", s.inventory.At(1) == Item.NONE);
        Check("slot 2", s.inventory.At(2) == Fixture.Item("iron_ore"));
        Check("two items", s.inventory.Used() == 2);
        Check("script whole number", s.vars.getInt("climbed") == 1);
        Check("script text", s.vars.getString("last_place")
                .equals("somewhere odd"));
        Check("rock on the map left behind",
                s.worlds[Fixture.FIRST].RockEmptyUntil(4, 1) == ROCK_UNTIL);
    }
}
