package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

/**
 * The feel of movement: where a walk may go, how its steps are paced,
 * and how the camera follows. Fixture describes the map.
 */
public class MovementTest {
    private static final int TILE = Sprites.TILE;
    private static final double CLOSE = 1e-9;

    /* Speeds, in tiles per tick, are measured over this sliver of one. */
    private static final double SLIVER = 1e-4;

    public void testWalksGoOnlyNorthSouthEastAndWest() {
        Fixture.Start(0);
        Mover mover = new Mover(Fixture.session.Here(), 1, 1);

        Check("route found", mover.WalkTo(5, 2));
        int steps = 0;
        while (mover.Walking()) {
            int x = mover.X(), y = mover.Y();
            mover.Tick();
            Check("one tile, straight", Math.abs(mover.X() - x) +
                    Math.abs(mover.Y() - y) == 1);
            steps++;
        }
        Check("four across and one down", steps == 5);
        Fixture.Finish();
    }

    public void testACharacterFacesTheWayItWalks() {
        Fixture.Start(0);
        Session s = Fixture.session;        /* at (1,1) of the test map */

        Check("faces the player to begin with",
                s.player.Facing() == Mover.FACING_DOWN);
        s.WalkTo(2, 1);
        Fixture.Run(1);
        Check("right", s.player.Facing() == Mover.FACING_RIGHT);
        s.WalkTo(2, 2);
        Fixture.Run(1);
        Check("down", s.player.Facing() == Mover.FACING_DOWN);
        s.WalkTo(4, 2);
        Fixture.Run(2);
        s.WalkTo(3, 2);
        Fixture.Run(1);
        Check("left", s.player.Facing() == Mover.FACING_LEFT);
        s.WalkTo(2, 2);
        Fixture.Run(1);
        s.WalkTo(2, 1);
        Fixture.Run(1);
        Check("up", s.player.Facing() == Mover.FACING_UP);
        Fixture.Run(3);
        Check("and goes on facing that way once stopped",
                s.player.Facing() == Mover.FACING_UP);
        Fixture.Finish();
    }

    public void testACharacterTurnsToWhatItUses() {
        Fixture.Start(0);
        Session s = Fixture.session;
        s.inventory.Add(Fixture.Item("bronze_pickaxe"));

        s.player.Place(3, 2);               /* under the copper rock */
        s.Use(3, 1);
        Fixture.Run(1);
        Check("up at the rock above", s.player.Facing() == Mover.FACING_UP);

        s.player.Place(2, 1);               /* left of it */
        s.Use(3, 1);
        Fixture.Run(1);
        Check("right at the rock beside",
                s.player.Facing() == Mover.FACING_RIGHT);

        Mover mover = new Mover(s.Here(), 2, 2);
        mover.Face(5, 1);
        Check("mostly across counts as across",
                mover.Facing() == Mover.FACING_RIGHT);
        mover.Face(1, 0);
        Check("mostly up counts as up", mover.Facing() == Mover.FACING_UP);
        mover.Face(1, 3);
        Check("a tie goes across", mover.Facing() == Mover.FACING_LEFT);
        mover.Face(2, 2);
        Check("facing where it stands changes nothing",
                mover.Facing() == Mover.FACING_LEFT);
        Check("standing still", !mover.Moving());
        Fixture.Finish();
    }

    public void testSteadyStyleEasesOnlyAtTheEndsOfAWalk() {
        Fixture.Start(0);
        Mover.SetStyle(Mover.STYLE_STEADY);
        Mover mover = new Mover(Fixture.session.Here(), 1, 2);
        mover.WalkTo(5, 2);

        mover.Tick();
        Check("first step starts from rest", Speed(mover, 0.0) < 0.01);
        Check("and ends at walking speed",
                Math.abs(Speed(mover, 1.0 - SLIVER) - 1.0) < 0.01);

        mover.Tick();
        Check("middle steps hold that speed throughout",
                Math.abs(Speed(mover, 0.0) - 1.0) < 0.01 &&
                Math.abs(Speed(mover, 0.5) - 1.0) < 0.01 &&
                Math.abs(Speed(mover, 1.0 - SLIVER) - 1.0) < 0.01);
        Check("so half-way is half a tile",
                mover.PixelX(0.5) == 2 * TILE + TILE / 2);

        mover.Tick();
        mover.Tick();
        Check("that was the last step", !mover.Walking());
        Check("it starts at walking speed",
                Math.abs(Speed(mover, 0.0) - 1.0) < 0.01);
        Check("and comes to rest", Speed(mover, 1.0 - SLIVER) < 0.01);
        Check("on the tile", mover.PixelX(1.0) == 5 * TILE);

        Mover.SetStyle(Mover.STYLE_STEADY);
        Fixture.Finish();
    }

    public void testSteadyStyleEasesBothEndsOfAOneTileWalk() {
        Fixture.Start(0);
        Mover.SetStyle(Mover.STYLE_STEADY);
        Mover mover = new Mover(Fixture.session.Here(), 1, 2);

        mover.WalkTo(2, 2);
        mover.Tick();
        Check("from rest", Speed(mover, 0.0) < 0.01);
        Check("to rest", Speed(mover, 1.0 - SLIVER) < 0.01);
        Check("quickest half-way", Speed(mover, 0.5) > 1.4);
        Fixture.Finish();
    }

    public void testAWalkExtendedAsItStopsStartsAgainFromRest() {
        Fixture.Start(0);
        Mover.SetStyle(Mover.STYLE_STEADY);
        Mover mover = new Mover(Fixture.session.Here(), 1, 2);

        mover.WalkTo(2, 2);
        mover.Tick();               /* a step that comes to rest... */
        mover.WalkTo(4, 2);         /* ...and then more is asked for */
        mover.Tick();
        Check("no leap from a standstill", Speed(mover, 0.0) < 0.01);
        Fixture.Finish();
    }

    public void testPulseStyleEasesEveryTile() {
        Fixture.Start(0);
        Mover.SetStyle(Mover.STYLE_PULSE);
        Mover mover = new Mover(Fixture.session.Here(), 1, 2);
        mover.WalkTo(5, 2);

        for (int step = 0; step < 4; step++) {
            mover.Tick();
            Check("step " + step + " starts from rest",
                    Speed(mover, 0.0) < 0.01);
            Check("step " + step + " ends at rest",
                    Speed(mover, 1.0 - SLIVER) < 0.01);
            Check("step " + step + " is quickest half-way",
                    Speed(mover, 0.5) > 1.4);
        }

        Mover.SetStyle(Mover.STYLE_STEADY);
        Fixture.Finish();
    }

    public void testEitherWayAStepCoversExactlyItsTile() {
        Check("rest to rest", Mover.Ease(0.0) == 0.0 &&
                Mover.Ease(1.0) == 1.0 &&
                Math.abs(Mover.Ease(0.5) - 0.5) < CLOSE);
        Check("rest to steady", Mover.EaseIn(0.0) == 0.0 &&
                Mover.EaseIn(1.0) == 1.0);

        double last = 0.0;
        for (int i = 1; i <= 100; i++) {
            double now = Mover.EaseIn(i / 100.0);
            Check("never goes backwards", now >= last);
            last = now;
        }
    }

    public void testAMoversPictureHoldsStillOutsideItsStep() {
        Fixture.Start(0);
        Mover mover = new Mover(Fixture.session.Here(), 1, 2);

        mover.WalkTo(3, 2);
        Check("nothing moves until the tick", mover.PixelX(0.5) == TILE);
        mover.Tick();
        Check("rules say the next tile already", mover.X() == 2);
        Check("picture still on the old one", mover.PixelX(0.0) == TILE);
        Check("held before the tick starts", mover.PixelX(-1.0) == TILE);
        Check("held after it ends", mover.PixelX(3.0) == 2 * TILE);
        Check("no sideways drift", mover.PixelY(0.5) == 2 * TILE);

        mover.Tick();
        mover.Tick();
        Check("stands still at the end", mover.PixelX(0.5) == 3 * TILE);
        Fixture.Finish();
    }

    public void testEveryMoverMovesAlike() {
        Fixture.Start(0);
        Session s = Fixture.session;
        Mover other = new Mover(s.Here(), s.player.X(), s.player.Y());

        s.WalkTo(5, 2);
        other.WalkTo(5, 2);
        for (int tick = 0; tick < 7; tick++) {
            s.Advance();
            other.Tick();
            for (int i = 0; i <= 4; i++) {
                double f = i / 4.0;
                Check("same place at tick " + tick,
                        s.player.PixelX(f) == other.PixelX(f) &&
                        s.player.PixelY(f) == other.PixelY(f));
            }
        }
        Check("both arrived", other.X() == 5 && other.Y() == 2);
        Fixture.Finish();
    }

    public void testTheCameraTrailsThenCatchesUp() {
        Camera camera = new Camera(1, 1);

        camera.Snap(100, 50);
        Check("snaps exactly", camera.X() == 100 && camera.Y() == 50);

        camera.Follow(200.0, 50.0, 200, 50, 0.016);
        Check("moves toward the target", camera.X() > 100);
        Check("but not all the way at once", camera.X() < 120);
        Check("only along the gap", camera.Y() == 50);

        for (int i = 0; i < 300; i++)
            camera.Follow(200.0, 50.0, 200, 50, 0.016);
        Check("catches up in the end", camera.X() == 200);
    }

    public void testTheCameraMovesTheSameAtAnyFrameRate() {
        Camera slow = new Camera(1, 1), fast = new Camera(1, 1);

        slow.Snap(0, 0);
        fast.Snap(0, 0);
        for (int i = 0; i < 10; i++) slow.Follow(1000.0, 0.0, 1000, 0, 0.05);
        for (int i = 0; i < 100; i++)
            fast.Follow(1000.0, 0.0, 1000, 0, 0.005);

        Check("ten long frames match a hundred short ones",
                Math.abs(slow.X() - fast.X()) <= 1);
    }

    public void testTheCharacterDoesNotShiverOnScreenDuringASteadyWalk() {
        Camera camera = new Camera(1, 1);
        double exact = 0, speed = TILE / 0.6;      /* a tile per tick */
        int changes = 0, reversals = 0, last = 0, lastDirection = 0;

        /* Ten seconds of walking at 60 frames a second. Where the
           character is drawn on screen is its pixel less the view's. */
        camera.Snap(0, 0);
        for (int frame = 0; frame < 600; frame++) {
            exact += speed / 60.0;
            int pixel = (int) Math.round(exact);
            camera.Follow(exact, 0.0, pixel, 0, 1.0 / 60.0);

            int onScreen = pixel - camera.X();
            if (onScreen != last) {
                int direction = onScreen > last ? 1 : -1;
                if (lastDirection != 0 && direction != lastDirection)
                    reversals++;
                lastDirection = direction;
                changes++;
                last = onScreen;
            }
        }

        Check("drifts ahead of the camera, to " + last,
                last >= 3 && last <= 12);
        Check("getting there in single steps: " + changes, changes == last);
        Check("and never steps back", reversals == 0);
    }

    public void testTheWorldScrollsInStepWithTheCharacterOnceSettled() {
        Camera camera = new Camera(1, 1);
        double exact = 0, speed = TILE / 0.6;
        int frame;

        camera.Snap(0, 0);
        for (frame = 0; frame < 300; frame++) {
            exact += speed / 60.0;
            camera.Follow(exact, 0.0, (int) Math.round(exact), 0, 1.0 / 60.0);
        }

        int offset = (int) Math.round(exact) - camera.X();
        for (; frame < 600; frame++) {
            exact += speed / 60.0;
            int pixel = (int) Math.round(exact);
            camera.Follow(exact, 0.0, pixel, 0, 1.0 / 60.0);
            Check("locked together", pixel - camera.X() == offset);
        }
    }

    public void testTheWorldNeverScrollsBackwardsWhenAWalkChangesPace() {
        int[] rates = { 30, 60, 75, 144 };
        int[] grains = { 1, 2, 3, 4, 5 };

        for (int i = 0; i < rates.length; i++)
            for (int j = 0; j < grains.length; j++)
                WalkACorner(rates[i], grains[j]);
    }

    public void testAFinerGrainMovesTheViewInSmallerSteps() {
        Check("whole game pixels at a grain of one: " + WalkACorner(60, 1),
                WalkACorner(60, 1) == 0);
        Check("parts of one at a grain of four: " + WalkACorner(60, 4),
                WalkACorner(60, 4) > 40);
    }

    public void testASteadyWalkStaysLockedAtAFineGrain() {
        Camera camera = new Camera(4, 4);
        double exact = 0, speed = TILE / 0.6;
        int frame;

        camera.Snap(0, 0);
        for (frame = 0; frame < 300; frame++) {
            exact += speed / 60.0;
            camera.Follow(exact, 0.0, (int) Math.round(exact), 0, 1.0 / 60.0);
        }

        int pixel = (int) Math.round(exact);
        int offset = pixel * 4 - (camera.X() * 4 + camera.FineX());
        for (; frame < 600; frame++) {
            exact += speed / 60.0;
            pixel = (int) Math.round(exact);
            camera.Follow(exact, 0.0, pixel, 0, 1.0 / 60.0);
            Check("locked together, to the screen pixel", pixel * 4 -
                    (camera.X() * 4 + camera.FineX()) == offset);
        }
    }

    /*
     * A steady walk as Mover paces it, seen through the camera: away
     * from rest and two more tiles east, three tiles south, a last
     * one south to rest, then three ticks standing while the camera
     * catches up. The camera only ever travels east and south, so the
     * view must too.
     *
     * Returns in how many frames the view moved by a part of a game
     * pixel, across or down.
     */
    private static int WalkACorner(int framesPerSecond, int grain) {
        final int EAST = 3, SOUTH = 4, TICKS = EAST + SOUTH + 3;
        Camera camera = new Camera(grain, grain);
        int frames = (int) (TICKS * 0.6 * framesPerSecond);
        int most = (framesPerSecond >= 60 ? 1 : 2) * grain;
        int pixelX = 0, pixelY = 0, parts = 0;
        String at = " at " + framesPerSecond + " frames a second, grain " +
                grain;

        camera.Snap(0, 0);
        for (int frame = 1; frame < frames; frame++) {
            double ticks = frame / (0.6 * framesPerSecond);
            int tick = (int) ticks;
            double t = ticks - tick;
            double exactX, exactY;

            if (tick == 0) exactX = Mover.EaseIn(t);
            else exactX = Math.min(ticks, EAST);
            if (tick < EAST) exactY = 0.0;
            else if (tick < EAST + SOUTH - 1) exactY = ticks - EAST;
            else if (tick == EAST + SOUTH - 1)
                exactY = SOUTH - Mover.EaseIn(1.0 - t);
            else exactY = SOUTH;
            exactX *= TILE;
            exactY *= TILE;

            int wasX = camera.X() * grain + camera.FineX();
            int wasY = camera.Y() * grain + camera.FineY();
            pixelX = (int) Math.round(exactX);
            pixelY = (int) Math.round(exactY);
            camera.Follow(exactX, exactY, pixelX, pixelY,
                    1.0 / framesPerSecond);

            int movedX = camera.X() * grain + camera.FineX() - wasX;
            int movedY = camera.Y() * grain + camera.FineY() - wasY;
            Check("never west, frame " + frame + at, movedX >= 0);
            Check("never north, frame " + frame + at, movedY >= 0);
            Check("no lurch, frame " + frame + at,
                    movedX <= most && movedY <= most);
            Check("the parts are in range, frame " + frame + at,
                    camera.FineX() >= 0 && camera.FineX() < grain &&
                    camera.FineY() >= 0 && camera.FineY() < grain);
            if (movedX % grain != 0 || movedY % grain != 0) parts++;
        }
        Check("comes to rest on the character" + at,
                camera.X() == pixelX && camera.Y() == pixelY &&
                camera.FineX() == 0 && camera.FineY() == 0);
        return parts;
    }

    /* How fast a mover's picture is travelling at a moment of its
       step, in tiles per tick. */
    private static double Speed(Mover mover, double at) {
        return (mover.ExactX(at + SLIVER) - mover.ExactX(at)) /
                SLIVER / TILE;
    }
}
