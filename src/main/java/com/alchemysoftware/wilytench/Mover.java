package com.alchemysoftware.wilytench;

/**
 * How anything walks about a map: the player's character, and every
 * other character to come. Each owns a Mover and so moves alike.
 *
 * Where a mover is, as far as the game's rules are concerned, is
 * always a whole tile, and it changes only on a game tick: one tile per
 * tick along the planned route. Between ticks its picture travels from
 * the previous tile to the current one. It does not do so at a steady
 * speed: it starts slowly, is quickest half-way, and slows into the
 * new tile, so that a walk has a step to it instead of a glide.
 */
final class Mover {
    private static final int TILE = Sprites.TILE;

    private World world;
    private Pathfinder pathfinder;
    private int[] route;

    private int x, y, prevX, prevY;
    private int routeLen, routePos;
    private boolean facingLeft;

    /** Creates a mover standing on a tile of a map. */
    Mover(World world, int tileX, int tileY) {
        EnterWorld(world, tileX, tileY);
    }

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
        if (routePos == routeLen) return;

        int next = route[routePos++];
        x = next % world.w;
        y = next / world.w;
        if (x != prevX) facingLeft = x < prevX;
    }

    /** Turns to face a tile, if it is to one side. */
    void Face(int tileX) {
        if (tileX != x) facingLeft = tileX < x;
    }

    /** @return the tile column the mover is on */
    int X() { return x; }

    /** @return the tile row the mover is on */
    int Y() { return y; }

    boolean FacingLeft() { return facingLeft; }

    /** @return whether there are steps of a walk still to take */
    boolean Walking() { return routePos < routeLen; }

    /** @return the tile column where the current walk ends */
    int DestX() { return route[routeLen - 1] % world.w; }

    /** @return the tile row where the current walk ends */
    int DestY() { return route[routeLen - 1] / world.w; }

    /**
     * @param tickFraction how far through the current tick the frame
     *                     falls, from 0 to 1
     * @return the map pixel of the left edge of the mover's picture
     */
    int PixelX(double tickFraction) {
        return prevX * TILE +
                (int) Math.round((x - prevX) * TILE * Ease(tickFraction));
    }

    /** @return the map pixel of the top edge of the mover's picture */
    int PixelY(double tickFraction) {
        return prevY * TILE +
                (int) Math.round((y - prevY) * TILE * Ease(tickFraction));
    }

    /**
     * The shape of a step. Turns time through a tick, 0 to 1, into
     * distance covered, 0 to 1: slow, then fast, then slow, and the
     * same backwards as forwards. At its quickest, half-way, the mover
     * travels at one and a half times its average speed.
     */
    static double Ease(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t * t * (3.0 - 2.0 * t);
    }
}
