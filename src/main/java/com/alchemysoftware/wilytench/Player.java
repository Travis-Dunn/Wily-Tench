package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.sprite.SpriteAnimation;
import com.alchemysoftware.whitetail.sprite.SpriteAnimationDefinition;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * The player's character.
 *
 * Where the character is, as far as the game's rules are concerned, is
 * always a whole tile, and it changes only on a game tick: one tile per
 * tick along the planned route. Between ticks the sprite glides from
 * the previous tile to the current one so the movement looks smooth.
 *
 * The character is on one map at a time; EnterWorld() moves it to
 * another.
 *
 * A walk may have a "target": a tile the character is heading for in
 * order to do something to it, such as a rock to mine. The character
 * stops beside a target rather than on it.
 */
final class Player {
    private static final int TILE = Sprites.TILE;
    private static final int IDLE_FRAME_MS = 250;
    private static final int WORK_FRAME_MS = 200;

    private World world;
    private Pathfinder pathfinder;
    private int[] route;
    private final int sprite;
    private final SpriteAnimation anim;
    private final SpriteAnimationDefinition idleDef, workDef;

    private int x, y, prevX, prevY;
    private int routeLen, routePos;
    private boolean facingLeft, working;

    private boolean hasTarget;
    private int targetX, targetY;

    private Player(World world, int sprite, SpriteAnimation anim,
                   SpriteAnimationDefinition idleDef,
                   SpriteAnimationDefinition workDef) {
        this.world = world;
        this.sprite = sprite;
        this.anim = anim;
        this.idleDef = idleDef;
        this.workDef = workDef;
        pathfinder = new Pathfinder(world);
        route = new int[world.w * world.h];
        x = prevX = world.startX;
        y = prevY = world.startY;
    }

    /**
     * Creates the character at the map's start. The sprite renderer
     * must be initialized with the heroes atlas installed.
     *
     * @return the player, or null after a fatal error has been reported
     */
    static Player Create(World world) {
        char[] idle = {
            (char) Sprites.Hero(0, 0), (char) Sprites.Hero(1, 0),
            (char) Sprites.Hero(2, 0)
        };
        /* The art's "anger" pose, arms flung wide, is the nearest thing
           it has to swinging a tool. */
        char[] work = {
            (char) Sprites.Hero(0, 0), (char) Sprites.Hero(0, 8),
            (char) Sprites.Hero(1, 8), (char) Sprites.Hero(2, 8)
        };
        SpriteAnimationDefinition idleDef =
                SpriteAnimationDefinition.Create(idle, IDLE_FRAME_MS, true);
        SpriteAnimationDefinition workDef =
                SpriteAnimationDefinition.Create(work, WORK_FRAME_MS, true);
        if (idleDef == null || workDef == null) return null;

        SpriteAnimation anim = SpriteAnimation.Create(idleDef);
        if (anim == null) return null;

        int sprite = SpriteRenderer.CreateSprite(world.startX * TILE,
                world.startY * TILE, Sprites.HeroAtlas(), idle[0],
                Sprites.LAYER_ACTOR, Sprites.COLOR_MAP_NONE, false, false,
                true);
        if (sprite == SpriteRenderer.INVALID_HANDLE) return null;

        return new Player(world, sprite, anim, idleDef, workDef);
    }

    /**
     * Plans a walk to a tile, or as near to it as can be reached,
     * replacing any walk and forgetting any target. Walking starts on
     * the next game tick.
     *
     * @return whether the walk will end on that very tile
     */
    boolean WalkTo(int tileX, int tileY) {
        hasTarget = false;
        routeLen = pathfinder.Find(x, y, tileX, tileY, route);
        routePos = 0;

        int endX = routeLen == 0 ? x : DestX();
        int endY = routeLen == 0 ? y : DestY();
        return endX == tileX && endY == tileY;
    }

    /**
     * Plans a walk to stand beside a tile and makes that tile the
     * target.
     */
    void WalkToTarget(int tileX, int tileY) {
        WalkTo(tileX, tileY);
        hasTarget = true;
        targetX = tileX;
        targetY = tileY;
    }

    void ClearTarget() { hasTarget = false; }

    boolean HasTarget() { return hasTarget; }

    int TargetX() { return targetX; }

    int TargetY() { return targetY; }

    /**
     * @return whether the character stands directly north, south, east,
     *         or west of its target
     */
    boolean BesideTarget() {
        return hasTarget &&
                Math.abs(targetX - x) + Math.abs(targetY - y) == 1;
    }

    /** Turns the character to face its target, if that is to one side. */
    void FaceTarget() {
        if (targetX != x) facingLeft = targetX < x;
    }

    /** Switches between the idle and the tool-swinging animation. */
    void SetWorking(boolean working) {
        if (working == this.working) return;
        this.working = working;
        anim.setDefinition(sprite, working ? workDef : idleDef);
    }

    /** @return the tile column the character is on */
    int X() { return x; }

    /** @return the tile row the character is on */
    int Y() { return y; }

    /**
     * Puts the character on a tile at once, standing still. For
     * restoring a saved game.
     */
    void Place(int tileX, int tileY) {
        x = prevX = tileX;
        y = prevY = tileY;
        routeLen = routePos = 0;
        hasTarget = false;
    }

    /**
     * Moves the character to a tile of another map at once, standing
     * still.
     */
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

    /**
     * Moves and animates the sprite for this frame.
     *
     * @param tickFraction how far through the current tick the frame
     *                     falls, from 0 to 1
     * @param deltaMs whole milliseconds since the previous frame
     */
    void Animate(double tickFraction, int deltaMs) {
        SpriteRenderer.SetPosition(sprite, PixelX(tickFraction),
                PixelY(tickFraction));
        SpriteRenderer.SetFlip(sprite, facingLeft, false);
        anim.update(sprite, deltaMs);
    }

    /** @return the map pixel of the sprite's left edge */
    int PixelX(double tickFraction) {
        return prevX * TILE +
                (int) Math.round((x - prevX) * TILE * tickFraction);
    }

    /** @return the map pixel of the sprite's top edge */
    int PixelY(double tickFraction) {
        return prevY * TILE +
                (int) Math.round((y - prevY) * TILE * tickFraction);
    }

    /** @return whether there are steps of a walk still to take */
    boolean Walking() { return routePos < routeLen; }

    /** @return the tile column where the current walk ends */
    int DestX() { return route[routeLen - 1] % world.w; }

    /** @return the tile row where the current walk ends */
    int DestY() { return route[routeLen - 1] / world.w; }
}
