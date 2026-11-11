package com.alchemysoftware.wilytench;

/**
 * Decides which part of the map the view shows.
 *
 * The camera follows a point, the player, but is not bolted to it: each
 * moment it closes a fraction of the distance still to go. So it trails
 * a little behind a walk and drifts to rest after the walk stops.
 *
 * The picture is made of whole pixels, and that takes some care. The
 * player's picture is drawn at a whole pixel and so is the view. If
 * each were rounded on its own, the gap between them would flicker by a
 * pixel from frame to frame whenever the two happened to round
 * differently, and the character would seem to shiver. Instead the
 * camera decides how many whole pixels it is trailing by, and changes
 * its mind only when that has clearly stopped being true. The view is
 * then the player's own pixel position less that trail: while the
 * trail holds, the world scrolls exactly in step with the character.
 */
final class Camera {
    /*
     * How long, in seconds, the camera takes to close about two thirds
     * of any gap. A steady walk of one tile per tick settles with the
     * camera this many seconds of walking behind: at 0.25, about 7
     * pixels.
     */
    private static final double CATCH_UP_SECONDS = 0.25;

    /*
     * How far, in pixels, the true trail must be from the whole number
     * in use before a new whole number is taken. Plain rounding is
     * 0.5; the extra is what stops a trail hovering near a half from
     * flipping back and forth.
     */
    private static final double TRAIL_SLACK = 0.75;

    private double x, y;
    private int trailX, trailY;
    private int viewX, viewY;

    /** Jumps straight to a point, as after arriving somewhere new. */
    void Snap(int aimX, int aimY) {
        x = viewX = aimX;
        y = viewY = aimY;
        trailX = trailY = 0;
    }

    /**
     * Moves toward the point the view would show if it did not trail.
     *
     * @param exactX that point, in pixels and fractions of one
     * @param pixelX the same point as the player's picture is actually
     *               drawn: exactX to the nearest pixel
     * @param seconds the time since the last call
     */
    void Follow(double exactX, double exactY, int pixelX, int pixelY,
                double seconds) {
        double closed = 1.0 - Math.exp(-seconds / CATCH_UP_SECONDS);

        x += (exactX - x) * closed;
        y += (exactY - y) * closed;

        trailX = Settle(trailX, exactX - x);
        trailY = Settle(trailY, exactY - y);
        viewX = pixelX - trailX;
        viewY = pixelY - trailY;
    }

    /** @return the map pixel at the left edge of the view */
    int X() { return viewX; }

    /** @return the map pixel at the top edge of the view */
    int Y() { return viewY; }

    private static int Settle(int trail, double exact) {
        return Math.abs(exact - trail) > TRAIL_SLACK ?
                (int) Math.round(exact) : trail;
    }
}
