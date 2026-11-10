package com.alchemysoftware.wilytench;

import java.io.File;

import com.alchemysoftware.whitetail.save.SaveData;

/**
 * Saves a game and loads it back, in memory and through the disk.
 */
public class SaveTest {
    private static final int TIMED_WRITES = 50;

    private Skills skills;
    private Inventory inventory;
    private PlayerSave save;

    public void testStateSurvivesCaptureAndRestore() {
        Start();
        SaveData data = Scramble();

        Start();
        save.Restore(data, Fixture.world);
        CheckScrambled();
        Fixture.Finish();
    }

    public void testChangedNoticesEachKindOfChange() {
        Start();
        Check("never saved", save.Changed());
        save.Capture();
        Check("just saved", !save.Changed());

        Fixture.player.Place(2, 2);
        Check("moved", save.Changed());
        save.Capture();

        skills.AddXp(Skills.MINING, 175);
        Check("xp", save.Changed());
        save.Capture();

        inventory.Add(Item.COPPER_ORE);
        Check("inventory", save.Changed());
        save.Capture();
        Check("settled", !save.Changed());
        Fixture.Finish();
    }

    public void testNonsenseInAFileIsMadeSafe() {
        Start();
        SaveData data = SaveData.Create();
        byte[] slots = new byte[Inventory.SLOTS * 2];

        /* Slot 0 holds item number 999; slot 1 a real one. */
        slots[0] = (byte) (1000 >> 8);
        slots[1] = (byte) 1000;
        slots[3] = (byte) (Item.IRON_ORE + 1);
        data.setBytes("inventory", slots);
        data.setString("map", Fixture.MAP);
        data.setInt("x", 0);    /* inside a wall */
        data.setInt("y", 0);
        data.setInt("xp.mining", -5);

        save.Restore(data, Fixture.world);
        Check("unknown item dropped", inventory.At(0) == Item.NONE);
        Check("known item kept", inventory.At(1) == Item.IRON_ORE);
        Check("one item", inventory.Used() == 1);
        Check("not in the wall", Fixture.player.X() == Fixture.world.startX);
        Check("xp not negative", skills.XpTenths(Skills.MINING) == 0);
        Fixture.Finish();
    }

    public void testEmptyFileChangesNothing() {
        Start();
        inventory.Add(Item.BRONZE_HAMMER);
        save.Restore(SaveData.Create(), Fixture.world);

        Check("hammer kept", inventory.At(0) == Item.BRONZE_HAMMER);
        Check("at start", Fixture.player.X() == Fixture.world.startX);
        Fixture.Finish();
    }

    public void testStateSurvivesTheDisk() {
        Start();
        DeleteSaves();

        Check("nothing yet", SaveGame.Load() == SaveGame.LOAD_NEW_GAME);
        Check("nothing loaded", SaveGame.Loaded() == null);
        SaveGame.Queue(Scramble());
        SaveGame.Shutdown();

        Start();
        Check("found", SaveGame.Load() == SaveGame.LOAD_OK);
        save.Restore(SaveGame.Loaded(), Fixture.world);
        CheckScrambled();

        /* How long one save takes on this machine, for the record. */
        long t0 = System.nanoTime();
        for (int i = 0; i < TIMED_WRITES; i++) {
            Fixture.player.Place(1 + i % 2, 1);
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
        DeleteSaves();
        SaveGame.Load();

        for (int x = 1; x <= 5; x++) {
            Fixture.player.Place(x == 3 || x == 4 ? 2 : x, 2);
            SaveGame.Queue(save.Capture());
        }
        SaveGame.Shutdown();

        Start();
        Check("found", SaveGame.Load() == SaveGame.LOAD_OK);
        save.Restore(SaveGame.Loaded(), Fixture.world);
        Check("last position won", Fixture.player.X() == 5 &&
                Fixture.player.Y() == 2);
        SaveGame.Shutdown();
        Fixture.Finish();
    }

    private void Start() {
        if (Fixture.world != null && skills != null) Fixture.Finish();
        Fixture.Start();
        skills = new Skills();
        inventory = new Inventory();
        save = new PlayerSave(Fixture.MAP, Fixture.player, skills,
                inventory);
    }

    /* Puts the state somewhere distinctive and captures it. */
    private SaveData Scramble() {
        Fixture.player.Place(5, 2);
        skills.AddXp(Skills.MINING, 12345);
        inventory.Add(Item.BRONZE_HAMMER);
        inventory.Add(Item.COPPER_ORE);
        inventory.Add(Item.IRON_ORE);
        inventory.Remove(1);
        return save.Capture();
    }

    private void CheckScrambled() {
        Check("position", Fixture.player.X() == 5 && Fixture.player.Y() == 2);
        Check("xp", skills.XpTenths(Skills.MINING) == 12345);
        Check("slot 0", inventory.At(0) == Item.BRONZE_HAMMER);
        Check("slot 1 empty", inventory.At(1) == Item.NONE);
        Check("slot 2", inventory.At(2) == Item.IRON_ORE);
        Check("two items", inventory.Used() == 2);
    }

    private static void DeleteSaves() {
        String[] names = Fixture.dir.list();
        for (int i = 0; names != null && i < names.length; i++)
            if (names[i].startsWith("wts-"))
                new File(Fixture.dir, names[i]).delete();
    }

    private static void Check(String what, boolean ok) {
        Fixture.Check(what, ok);
    }
}
