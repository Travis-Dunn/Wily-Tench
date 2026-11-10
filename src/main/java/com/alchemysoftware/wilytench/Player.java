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
 */
final class Player {
    private static final int TILE = Sprites.TILE;
    private static final int IDLE_FRAME_MS = 250;

    private final World world;
    private final Pathfinder pathfinder;
    private final int[] route;
    private final int sprite;
    private final SpriteAnimation anim;

    private int x, y, prevX, prevY;
    private int routeLen, routePos;
    private boolean facingLeft;

    private Player(World world, int sprite, SpriteAnimation anim) {
        this.world = world;
        this.sprite = sprite;
        this.anim = anim;
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
        SpriteAnimationDefinition def =
                SpriteAnimationDefinition.Create(idle, IDLE_FRAME_MS, true);
        if (def == null) return null;

        SpriteAnimation anim = SpriteAnimation.Create(def);
        if (anim == null) return null;

        int sprite = SpriteRenderer.CreateSprite(world.startX * TILE,
                world.startY * TILE, Sprites.ATLAS_HEROES, idle[0],
                Sprites.LAYER_ACTOR, Sprites.COLOR_MAP_NONE, false, false,
                true);
        if (sprite == SpriteRenderer.INVALID_HANDLE) return null;

        return new Player(world, sprite, anim);
    }

    /**
     * Plans a walk to a tile, or as near to it as can be reached,
     * replacing any walk already under way. Walking starts on the next
     * game tick.
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
