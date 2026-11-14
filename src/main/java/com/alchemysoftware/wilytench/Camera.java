package com.alchemysoftware.wilytench;

/**
 * Decides which part of the map the view shows.
 *
 * The camera follows a point, the player, but is not bolted to it: each
 * moment it closes a fraction of the distance still to go. So it trails
 * a little behind a walk and drifts to rest after the walk stops.
 *
 * Positions here are counted in fine pixels: the grain is how many of
 * them make a game pixel, and is by how small a step Screen can slide
 * the world, which need not be the same across as up and down. The
 * character's picture is always drawn at a whole game pixel. The view
 * may sit at any fine pixel, because Screen can slide the picture of
 * the world by less than a game pixel. At a grain of one the two are
 * the same and the view moves in whole game pixels too.
 *
 * That takes some care. If the view simply went wherever the camera
 * was, the gap between it and the character would flicker from frame
 * to frame whenever the two happened to round differently, and the
 * character would seem to shiver. Instead the camera decides how far
 * it is trailing, in fine pixels, and changes its mind only when that
 * has clearly stopped being true. The view is then the character's own
 * pixel position less that trail: while the trail holds, the world
 * scrolls exactly in step with the character.
 *
 * The trail does have to change: it grows as a walk sets off, shrinks
 * as one stops, and at a corner does one across while doing the other
 * up and down. When it changes matters as much as whether. Lengthening
 * the trail pulls the view back, and on a frame in which the
 * character's picture has not itself advanced, that scrolls the whole
 * world backwards for one frame, which is seen as a judder. So a
 * change is held to what leaves the view either where it was or
 * further along the way the camera is truly travelling, and by no
 * more than a game pixel in a frame. A growing trail therefore grows
 * on the frames in which the character's picture steps, taking part of
 * each step; a shrinking one shrinks a little every frame.
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
     * How far, in fine pixels, the true trail must be from the one in
     * use before a new one is taken. Plain rounding is 0.5; the extra
     * is what stops a trail hovering near a half from flipping back
     * and forth.
     */
    private static final double TRAIL_SLACK = 0.75;

    /*
     * How far, in game pixels, the true trail may get from the one in
     * use before that is set right at once, without waiting for a good
     * frame. Walking never comes near it at any frame rate worth
     * playing at; it is for long frames and sudden jumps.
     */
    private static final double TRAIL_LIMIT = 3.0;

    private final int grainX, grainY;

    /* Where the camera truly is, in game pixels. */
    private double x, y;

    /* The trail in use and where that puts the view, in fine pixels. */
    private int trailX, trailY;
    private int viewX, viewY;

    /**
     * @param grainX how many fine pixels make a game pixel across: at
     *               least 1
     * @param grainY how many do up and down
     */
    Camera(int grainX, int grainY) {
        this.grainX = grainX;
        this.grainY = grainY;
    }

    /** Jumps straight to a point, as after arriving somewhere new. */
    void Snap(int aimX, int aimY) {
        x = aimX;
        y = aimY;
        viewX = aimX * grainX;
        viewY = aimY * grainY;
        trailX = trailY = 0;
    }

    /**
     * Moves toward the point the view would show if it did not trail.
     *
     * @param exactX that point, in game pixels and fractions of one
     * @param pixelX the same point as the player's picture is actually
     *               drawn: exactX to the nearest game pixel
     * @param seconds the time since the last call
     */
    void Follow(double exactX, double exactY, int pixelX, int pixelY,
                double seconds) {
        double closed = 1.0 - Math.exp(-seconds / CATCH_UP_SECONDS);
        double travelX = (exactX - x) * closed;
        double travelY = (exactY - y) * closed;

        x += travelX;
        y += travelY;

        /* With the trail left alone, the view moves as the picture
           did. */
        trailX = Settle(trailX, (exactX - x) * grainX,
                pixelX * grainX - trailX - viewX, travelX, grainX);
        trailY = Settle(trailY, (exactY - y) * grainY,
                pixelY * grainY - trailY - viewY, travelY, grainY);
        viewX = pixelX * grainX - trailX;
        viewY = pixelY * grainY - trailY;
    }

    /**
     * @return the map pixel at the left edge of the view: the game
     *         pixel the view is at or just past
     */
    int X() { return TileView.FloorDiv(viewX, grainX); }

    /** @return the map pixel at the top edge of the view, likewise */
    int Y() { return TileView.FloorDiv(viewY, grainY); }

    /**
     * @return how many fine pixels the view is past X(): 0 to one
     *         less than the grain
     */
    int FineX() { return viewX - X() * grainX; }

    /** @return how many fine pixels the view is past Y(), likewise */
    int FineY() { return viewY - Y() * grainY; }

    /*
     * Decides the trail for one frame, across or up and down, in fine
     * pixels.
     *
     * trail  - the trail in use
     * exact  - the true trail, in fine pixels and fractions of one
     * held   - how far the view moves this frame if the trail is kept
     * travel - how far the camera truly moved this frame; only its
     *          direction is used
     * grain  - how many fine pixels make a game pixel
     */
    private static int Settle(int trail, double exact, int held,
                              double travel, int grain) {
        double off = Math.abs(exact - trail);

        if (off <= TRAIL_SLACK) return trail;
        if (off > TRAIL_LIMIT * grain) return (int) Math.round(exact);

        /* Changing the trail by so much moves the view by as much the
           other way, on top of what it was moving anyway. */
        int wanted = (int) Math.round(exact) - trail;
        int most = Math.max(Math.abs(held), grain);
        int moved = held - wanted;

        if (travel >= 0.0) moved = Math.max(0, Math.min(moved, most));
        else moved = Math.min(0, Math.max(moved, -most));

        /* What that leaves of the change. Should it now be the wrong
           way, wait; should it be more than was wanted, the view is
           being carried against the camera, as when the character
           turns right round, and the truth is the better choice. */
        int change = held - moved;

        if (change == 0 || (change > 0) != (wanted > 0)) return trail;
        if (Math.abs(change) > Math.abs(wanted)) return trail + wanted;
        return trail + change;
    }
}
