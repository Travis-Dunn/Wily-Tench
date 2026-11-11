package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.sprite.SpriteAnimation;
import com.alchemysoftware.whitetail.sprite.SpriteAnimationDefinition;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * The player's character: a Mover, which is how it gets about, plus
 * the sprite that shows it and the thing it is walking toward.
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

    private final Mover mover;
    private final int sprite;
    private final SpriteAnimation anim;
    private final SpriteAnimationDefinition idleDef, workDef;

    private boolean working;

    private boolean hasTarget;
    private int targetX, targetY;

    private Player(World world, int sprite, SpriteAnimation anim,
                   SpriteAnimationDefinition idleDef,
                   SpriteAnimationDefinition workDef) {
        this.sprite = sprite;
        this.anim = anim;
        this.idleDef = idleDef;
        this.workDef = workDef;
        mover = new Mover(world, world.startX, world.startY);
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
        return mover.WalkTo(tileX, tileY);
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
        return hasTarget && Math.abs(targetX - mover.X()) +
                Math.abs(targetY - mover.Y()) == 1;
    }

    /** Turns the character to face its target, if that is to one side. */
    void FaceTarget() { mover.Face(targetX); }

    /** Switches between the idle and the tool-swinging animation. */
    void SetWorking(boolean working) {
        if (working == this.working) return;
        this.working = working;
        anim.setDefinition(sprite, working ? workDef : idleDef);
    }

    /** @return the tile column the character is on */
    int X() { return mover.X(); }

    /** @return the tile row the character is on */
    int Y() { return mover.Y(); }

    /**
     * Puts the character on a tile at once, standing still. For
     * restoring a saved game.
     */
    void Place(int tileX, int tileY) {
        mover.Place(tileX, tileY);
        hasTarget = false;
    }

    /**
     * Moves the character to a tile of another map at once, standing
     * still.
     */
    void EnterWorld(World world, int tileX, int tileY) {
        mover.EnterWorld(world, tileX, tileY);
        hasTarget = false;
    }

    /** Advances one game tick: takes the next step of the walk. */
    void Tick() { mover.Tick(); }

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
        SpriteRenderer.SetFlip(sprite, mover.FacingLeft(), false);
        anim.update(sprite, deltaMs);
    }

    /** @return the map pixel of the sprite's left edge */
    int PixelX(double tickFraction) { return mover.PixelX(tickFraction); }

    /** @return the same before rounding to a pixel */
    double ExactX(double tickFraction) { return mover.ExactX(tickFraction); }

    /** @return the top edge before rounding to a pixel */
    double ExactY(double tickFraction) { return mover.ExactY(tickFraction); }

    /** @return the map pixel of the sprite's top edge */
    int PixelY(double tickFraction) { return mover.PixelY(tickFraction); }

    /** @return whether there are steps of a walk still to take */
    boolean Walking() { return mover.Walking(); }

    /** @return the tile column where the current walk ends */
    int DestX() { return mover.DestX(); }

    /** @return the tile row where the current walk ends */
    int DestY() { return mover.DestY(); }
}
