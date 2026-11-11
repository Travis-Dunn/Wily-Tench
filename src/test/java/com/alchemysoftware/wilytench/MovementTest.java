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
        Camera camera = new Camera();

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
        Camera slow = new Camera(), fast = new Camera();

        slow.Snap(0, 0);
        fast.Snap(0, 0);
        for (int i = 0; i < 10; i++) slow.Follow(1000.0, 0.0, 1000, 0, 0.05);
        for (int i = 0; i < 100; i++)
            fast.Follow(1000.0, 0.0, 1000, 0, 0.005);

        Check("ten long frames match a hundred short ones",
                Math.abs(slow.X() - fast.X()) <= 1);
    }

    public void testTheCharacterDoesNotShiverOnScreenDuringASteadyWalk() {
        Camera camera = new Camera();
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
        Camera camera = new Camera();
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

    /* How fast a mover's picture is travelling at a moment of its
       step, in tiles per tick. */
    private static double Speed(Mover mover, double at) {
        return (mover.ExactX(at + SLIVER) - mover.ExactX(at)) /
                SLIVER / TILE;
    }
}
