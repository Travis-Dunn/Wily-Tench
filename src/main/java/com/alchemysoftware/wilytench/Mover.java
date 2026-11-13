package com.alchemysoftware.wilytench;

/**
 * How anything walks about a map: the player's character, and every
 * other character to come. Each owns a Mover and so moves alike.
 *
 * Where a mover is, as far as the game's rules are concerned, is
 * always a whole tile, and it changes only on a game tick: one tile per
 * tick along the planned route, north, south, east, or west. Between
 * ticks its picture travels from the previous tile to the current one.
 *
 * How it paces that journey is the walk style, one setting for
 * everything that walks:
 *
 * - STYLE_STEADY: a walk eases away from a standstill on its first
 *   tile, holds a steady speed through the tiles between, and eases to
 *   a stop on its last. A walk of one tile does both.
 *
 * - STYLE_PULSE: every tile is eased into and out of, so a long walk
 *   surges once per tile.
 */
final class Mover {
    static final int STYLE_STEADY = 0;
    static final int STYLE_PULSE = 1;

    /* The ways a mover can face. FACING_DOWN is toward the player. */
    static final int FACING_DOWN = 0;
    static final int FACING_UP = 1;
    static final int FACING_LEFT = 2;
    static final int FACING_RIGHT = 3;

    private static final int TILE = Sprites.TILE;

    /* The shapes a step can have. */
    private static final int PACE_EVEN = 0;
    private static final int PACE_START = 1;
    private static final int PACE_STOP = 2;
    private static final int PACE_START_AND_STOP = 3;

    private static int style = STYLE_STEADY;

    private World world;
    private Pathfinder pathfinder;
    private int[] route;

    private int x, y, prevX, prevY;
    private int routeLen, routePos;
    private int facing = FACING_DOWN;

    /* The shape of the step in progress, and whether it ended at rest,
       which decides whether the next one starts from rest. */
    private int pace = PACE_EVEN;
    private boolean atRest = true;

    /** Creates a mover standing on a tile of a map. */
    Mover(World world, int tileX, int tileY) {
        EnterWorld(world, tileX, tileY);
    }

    /**
     * Chooses how every mover paces its steps.
     *
     * @param style STYLE_STEADY or STYLE_PULSE
     */
    static void SetStyle(int style) { Mover.style = style; }

    /**
     * Plans a walk to a tile, or as near to it as can be reached,
     * replacing any walk under way. Walking starts on the next game
     * tick.
     *
     * @return whether the walk will end on that very tile
     */
    boolean WalkTo(int tileX, int tileY) {
        routeLen = pathfinder.Find(x, y, tileX, tileY, route);
        routePos = 0;

        int endX = routeLen == 0 ? x : DestX();
        int endY = routeLen == 0 ? y : DestY();
        return endX == tileX && endY == tileY;
    }

    /** Puts the mover on a tile of its map at once, standing still. */
    void Place(int tileX, int tileY) {
        x = prevX = tileX;
        y = prevY = tileY;
        routeLen = routePos = 0;
        pace = PACE_EVEN;
        atRest = true;
    }

    /** Puts the mover on a tile of another map at once, standing still. */
    void EnterWorld(World world, int tileX, int tileY) {
        this.world = world;
        pathfinder = new Pathfinder(world);
        route = new int[world.w * world.h];
        Place(tileX, tileY);
    }

    /** Advances one game tick: takes the next step of the walk. */
    void Tick() {
        prevX = x;
        prevY = y;
        if (routePos == routeLen) {
            atRest = true;
            return;
        }

        int next = route[routePos++];
        x = next % world.w;
        y = next / world.w;
        Face(x + (x - prevX), y + (y - prevY), prevX, prevY);

        /* A step starts from rest if the last one ended at rest, and
           ends at rest if it is the last of the route. Should the route
           be extended meanwhile, the next step starts from rest too. */
        boolean stops = routePos == routeLen;
        if (style == STYLE_PULSE) pace = PACE_START_AND_STOP;
        else if (atRest) pace = stops ? PACE_START_AND_STOP : PACE_START;
        else pace = stops ? PACE_STOP : PACE_EVEN;
        atRest = stops || style == STYLE_PULSE;
    }

    /**
     * Turns to face a tile: along whichever of across and up-and-down
     * it lies further off, and across on a tie. Facing the tile stood
     * on changes nothing.
     */
    void Face(int tileX, int tileY) { Face(tileX, tileY, x, y); }

    /** @return whether the mover is part way through a step */
    boolean Moving() { return x != prevX || y != prevY; }

    /** @return which way the mover faces: one of the FACING_ values */
    int Facing() { return facing; }

    /** @return the tile column the mover is on */
    int X() { return x; }

    /** @return the tile row the mover is on */
    int Y() { return y; }

    /** @return whether there are steps of a walk still to take */
    boolean Walking() { return routePos < routeLen; }

    /** @return the tile column where the current walk ends */
    int DestX() { return route[routeLen - 1] % world.w; }

    /** @return the tile row where the current walk ends */
    int DestY() { return route[routeLen - 1] / world.w; }

    /**
     * @param tickFraction how far through the current tick the frame
     *                     falls, from 0 to 1
     * @return where the left edge of the mover's picture is, in map
     *         pixels and fractions of one
     */
    double ExactX(double tickFraction) {
        return prevX * TILE + (x - prevX) * TILE * Progress(tickFraction);
    }

    /** @return where the top edge of the mover's picture is, likewise */
    double ExactY(double tickFraction) {
        return prevY * TILE + (y - prevY) * TILE * Progress(tickFraction);
    }

    /** @return the map pixel the picture's left edge is drawn at */
    int PixelX(double tickFraction) {
        return (int) Math.round(ExactX(tickFraction));
    }

    /** @return the map pixel the picture's top edge is drawn at */
    int PixelY(double tickFraction) {
        return (int) Math.round(ExactY(tickFraction));
    }

    /* Turns to face a tile, as seen from another. */
    private void Face(int tileX, int tileY, int fromX, int fromY) {
        int dx = tileX - fromX, dy = tileY - fromY;

        if (dx == 0 && dy == 0) return;
        if (Math.abs(dx) >= Math.abs(dy))
            facing = dx < 0 ? FACING_LEFT : FACING_RIGHT;
        else facing = dy < 0 ? FACING_UP : FACING_DOWN;
    }

    /*
     * How much of the step in progress is covered, 0 to 1, by a time
     * through the tick, 0 to 1.
     */
    private double Progress(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;

        switch (pace) {
            case PACE_START:          return EaseIn(t);
            case PACE_STOP:           return 1.0 - EaseIn(1.0 - t);
            case PACE_START_AND_STOP: return Ease(t);
            default:                  return t;
        }
    }

    /**
     * A step from rest to rest: slow, then fast, then slow, the same
     * backwards as forwards. Half-way it is at one and a half times
     * its average speed.
     */
    static double Ease(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * A step from rest that ends moving at exactly the steady speed of
     * one tile per tick, so that it runs on into an even step without
     * a jolt. Having started from nothing it must briefly go a third
     * faster than steady to cover its tile in time. Run backwards, it
     * is a step from steady speed to rest.
     */
    static double EaseIn(double t) {
        return t * t * (2.0 - t);
    }
}
