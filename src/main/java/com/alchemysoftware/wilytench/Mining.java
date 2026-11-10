package com.alchemysoftware.wilytench;

import java.util.Random;

/**
 * The Mining skill: what happens, tick by tick, once the player has
 * been sent to an ore rock.
 *
 * The character walks up to the rock and starts swinging. Every
 * SWING_TICKS ticks a swing lands and the dice are rolled; see Ore for
 * the odds. A winning swing puts one ore in the inventory, awards
 * experience, and leaves the rock empty for a while, which ends the
 * job. Mining needs a hammer, a high enough level, and a free slot.
 */
final class Mining {
    private static final int SWING_TICKS = 3;
    private static final int TOOL = Item.BRONZE_HAMMER;

    private final Session session;
    private final Player player;
    private final Inventory inventory;
    private final Skills skills;
    private final ChatBox chat;
    private final Random rng;

    private boolean swinging;
    private int ticksSwinging;

    Mining(Session session, Random rng) {
        this.session = session;
        this.rng = rng;
        player = session.player;
        inventory = session.inventory;
        skills = session.skills;
        chat = session.chat;
    }

    /** Advances one game tick. Call after the player has moved. */
    void Tick() {
        World world = session.Here();
        int x = player.TargetX(), y = player.TargetY();
        int ore = player.HasTarget() ? world.OreAt(x, y) : Ore.NONE;

        if (ore == Ore.NONE || player.Walking()) {
            Stop();
            return;
        }

        if (!swinging) {
            if (!MayStart(world, ore, x, y)) {
                player.ClearTarget();
                return;
            }
            swinging = true;
            ticksSwinging = 0;
            player.FaceTarget();
            player.SetWorking(true);
            chat.Add(MSG_SWING, Palette.BONE);
            return;
        }

        /* Somebody else's luck, were there anybody else. */
        if (world.RockEmpty(x, y)) {
            Finish();
            return;
        }

        if (++ticksSwinging % SWING_TICKS != 0) return;
        if (!Ore.Roll(ore, skills.Level(Skills.MINING), rng)) return;

        int item = Ore.ItemOf(ore);
        inventory.Add(item);
        world.EmptyRock(x, y, session.Tick() + Ore.RespawnTicks(ore));
        chat.Add("You manage to mine some " + Item.LowerName(item) + ".",
                Palette.BONE);

        if (skills.AddXp(Skills.MINING, Ore.XpTenths(ore))) {
            chat.Add("Congratulations, you just advanced a Mining level! " +
                    "Your Mining level is now " +
                    skills.Level(Skills.MINING) + ".", Palette.AMBER);
        }
        Finish();
    }

    /* Says why not, when the answer is no. */
    private boolean MayStart(World world, int ore, int x, int y) {
        int item = Ore.ItemOf(ore);

        if (!player.BesideTarget()) {
            chat.Add(MSG_CANT_REACH, Palette.BONE);
        } else if (world.RockEmpty(x, y)) {
            chat.Add(MSG_EMPTY, Palette.BONE);
        } else if (!inventory.Has(TOOL)) {
            chat.Add(MSG_NO_TOOL, Palette.BONE);
        } else if (skills.Level(Skills.MINING) < Ore.Level(ore)) {
            chat.Add("You need a Mining level of " + Ore.Level(ore) +
                    " to mine this rock.", Palette.BONE);
        } else if (inventory.Full()) {
            chat.Add("Your inventory is too full to hold any more " +
                    Item.LowerName(item) + ".", Palette.BONE);
        } else {
            return true;
        }
        return false;
    }

    private void Finish() {
        player.ClearTarget();
        Stop();
    }

    private void Stop() {
        swinging = false;
        player.SetWorking(false);
    }

    private static final String MSG_SWING =
            "You swing your hammer at the rock.";
    private static final String MSG_CANT_REACH = "You can't reach that.";
    private static final String MSG_EMPTY =
            "There is no ore left in this rock for the moment.";
    private static final String MSG_NO_TOOL =
            "You need a hammer to mine this rock.";
}
