package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

/**
 * The feel of movement: how a step is paced, and how the camera
 * follows. Fixture describes the map.
 */
public class MovementTest {
    private static final int TILE = Sprites.TILE;
    private static final double CLOSE = 1e-9;

    public void testAStepStartsSlowPeaksHalfWayAndEndsSlow() {
        Check("starts at the start", Mover.Ease(0.0) == 0.0);
        Check("ends at the end", Mover.Ease(1.0) == 1.0);
        Check("half-way at half time",
                Math.abs(Mover.Ease(0.5) - 0.5) < CLOSE);
        Check("behind a steady pace early", Mover.Ease(0.25) < 0.25);
        Check("ahead of it late", Mover.Ease(0.75) > 0.75);
        Check("the same backwards as forwards", Math.abs(
                Mover.Ease(0.2) + Mover.Ease(0.8) - 1.0) < CLOSE);
        Check("holds outside a tick",
                Mover.Ease(-1.0) == 0.0 && Mover.Ease(3.0) == 1.0);

        double last = 0.0;
        for (int i = 1; i <= 100; i++) {
            double now = Mover.Ease(i / 100.0);
            Check("never goes backwards", now >= last);
            last = now;
        }

        /* The middle fifth of the time covers more ground than the
           first fifth: that is the pulse. */
        Check("quickest in the middle", Mover.Ease(0.6) - Mover.Ease(0.4) >
                2 * (Mover.Ease(0.2) - Mover.Ease(0.0)));
    }

    public void testAMoversPictureTravelsFromTheOldTileToTheNew() {
        Fixture.Start(0);
        Mover mover = new Mover(Fixture.session.Here(), 1, 2);

        Check("route found", mover.WalkTo(3, 2));
        Check("nothing moves until the tick", mover.PixelX(0.5) == TILE);
        mover.Tick();

        Check("rules say the next tile already", mover.X() == 2);
        Check("picture still on the old one", mover.PixelX(0.0) == TILE);
        Check("half-way at half time", mover.PixelX(0.5) == TILE + TILE / 2);
        Check("lagging early", mover.PixelX(0.25) < TILE + TILE / 4);
        Check("arrives", mover.PixelX(1.0) == 2 * TILE);
        Check("no sideways drift", mover.PixelY(0.5) == 2 * TILE);

        mover.Tick();
        mover.Tick();
        Check("stops at the end of the route",
                mover.X() == 3 && !mover.Walking());
        Check("and stands still", mover.PixelX(0.5) == 3 * TILE);
        Fixture.Finish();
    }

    public void testEveryMoverMovesAlike() {
        Fixture.Start(0);
        Session s = Fixture.session;
        Mover other = new Mover(s.Here(), s.player.X(), s.player.Y());

        s.WalkTo(5, 2);
        other.WalkTo(5, 2);
        for (int tick = 0; tick < 6; tick++) {
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

        camera.Follow(200, 50, 0.016);
        Check("moves toward the target", camera.X() > 100);
        Check("but not all the way at once", camera.X() < 120);
        Check("only along the gap", camera.Y() == 50);

        for (int i = 0; i < 300; i++) camera.Follow(200, 50, 0.016);
        Check("catches up in the end", camera.X() == 200);
    }

    public void testTheCameraMovesTheSameAtAnyFrameRate() {
        Camera slow = new Camera(), fast = new Camera();

        slow.Snap(0, 0);
        fast.Snap(0, 0);
        for (int i = 0; i < 10; i++) slow.Follow(1000, 0, 0.05);
        for (int i = 0; i < 100; i++) fast.Follow(1000, 0, 0.005);

        Check("ten long frames match a hundred short ones",
                Math.abs(slow.X() - fast.X()) <= 1);
    }

    public void testTheCameraLagsAWalkByAFewPixels() {
        Camera camera = new Camera();
        double target = 0, speed = TILE / 0.6;     /* a tile per tick */

        camera.Snap(0, 0);
        for (int i = 0; i < 600; i++) {
            target += speed * 0.01;
            camera.Follow((int) Math.round(target), 0, 0.01);
        }
        int lag = (int) Math.round(target) - camera.X();
        Check("behind, by " + lag, lag >= 3 && lag <= 12);
    }
}
