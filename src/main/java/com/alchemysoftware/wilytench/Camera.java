package com.alchemysoftware.wilytench;

/**
 * Decides which part of the map the view shows.
 *
 * The camera follows a point, the player, but is not bolted to it: each
 * moment it closes a fraction of the distance still to go. So it trails
 * a little behind a walk, falls further behind the faster the walk,
 * and drifts to rest after the walk stops.
 *
 * It keeps its position in fractions of a pixel so that the motion is
 * the same at any frame rate, and reports it in whole pixels, since
 * the picture is made of them.
 */
final class Camera {
    /*
     * How long, in seconds, the camera takes to close about two thirds
     * of any gap. A steady walk of one tile per tick settles with the
     * camera this many seconds of walking behind: at 0.25, about 7
     * pixels.
     */
    private static final double CATCH_UP_SECONDS = 0.25;

    private double x, y;

    /** Jumps straight to a point, as after arriving somewhere new. */
    void Snap(int targetX, int targetY) {
        x = targetX;
        y = targetY;
    }

    /**
     * Moves toward a point.
     *
     * @param seconds the time since the last call
     */
    void Follow(int targetX, int targetY, double seconds) {
        double closed = 1.0 - Math.exp(-seconds / CATCH_UP_SECONDS);

        x += (targetX - x) * closed;
        y += (targetY - y) * closed;
    }

    /** @return the camera's position, to the nearest pixel */
    int X() { return (int) Math.round(x); }

    int Y() { return (int) Math.round(y); }
}
