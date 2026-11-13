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
 *
 * A character's pictures are six cells in a row on a sprite sheet:
 * two of it walking sideways, facing left; two of it walking toward
 * the player; two of it walking away. Facing right is the sideways
 * pair turned over. Standing still is the first of the pair for the
 * way it faces. data/game.json says where the player's six are.
 *
 * There are no pictures of working yet, so swinging a tool is shown
 * as stepping on the spot, quickly.
 */
final class Player {
    /** How many pictures a character has. */
    static final int PICTURES = 6;

    private static final int TILE = Sprites.TILE;
    /* Two walking pictures to a step of one tick. */
    private static final int WALK_FRAME_MS = 300;
    private static final int WORK_FRAME_MS = 150;

    /* Where in a character's six pictures each pair begins. */
    private static final int SIDE = 0, FRONT = 2, BACK = 4;

    private static final int STAND = 0, WALK = 1, WORK = 2;
    private static final int UNSET = -1;

    private final Mover mover;
    private final int sprite;
    private final SpriteAnimation anim;
    /* Indexed by where the pair begins: SIDE, FRONT, or BACK. */
    private final SpriteAnimationDefinition[] walkDefs, workDefs;
    private final int[] standFrames;

    /* What the sprite was last set to show, to change it only when
       that changes. */
    private int shownMode = UNSET, shownPair = UNSET;

    private boolean working;

    private boolean hasTarget;
    private int targetX, targetY;

    private Player(World world, int sprite, SpriteAnimation anim,
                   SpriteAnimationDefinition[] walkDefs,
                   SpriteAnimationDefinition[] workDefs,
                   int[] standFrames) {
        this.sprite = sprite;
        this.anim = anim;
        this.walkDefs = walkDefs;
        this.workDefs = workDefs;
        this.standFrames = standFrames;
        mover = new Mover(world, world.startX, world.startY);
    }

    /**
     * Creates the character at the map's start. The sprite renderer
     * must be initialized with the atlases installed, and Content
     * loaded.
     *
     * @return the player, or null after a fatal error has been reported
     */
    static Player Create(World world) {
        int[] pictures = Content.PlayerPictures();
        SpriteAnimationDefinition[] walkDefs =
                new SpriteAnimationDefinition[PICTURES];
        SpriteAnimationDefinition[] workDefs =
                new SpriteAnimationDefinition[PICTURES];
        int[] standFrames = new int[PICTURES];

        for (int pair = 0; pair < PICTURES; pair += 2) {
            char[] frames = {
                (char) Sprites.FrameOf(pictures[pair]),
                (char) Sprites.FrameOf(pictures[pair + 1])
            };
            walkDefs[pair] = SpriteAnimationDefinition.Create(frames,
                    WALK_FRAME_MS, true);
            workDefs[pair] = SpriteAnimationDefinition.Create(frames,
                    WORK_FRAME_MS, true);
            if (walkDefs[pair] == null || workDefs[pair] == null)
                return null;
            standFrames[pair] = frames[0];
        }

        SpriteAnimation anim = SpriteAnimation.Create(walkDefs[FRONT]);
        if (anim == null) return null;

        int sprite = SpriteRenderer.CreateSprite(world.startX * TILE,
                world.startY * TILE, Sprites.AtlasOf(pictures[0]),
                standFrames[FRONT], Sprites.LAYER_ACTOR,
                Sprites.COLOR_MAP_NONE, false, false, true);
        if (sprite == SpriteRenderer.INVALID_HANDLE) return null;

        return new Player(world, sprite, anim, walkDefs, workDefs,
                standFrames);
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

    /** Turns the character to face its target. */
    void FaceTarget() { mover.Face(targetX, targetY); }

    /** Starts or stops showing the character at work with a tool. */
    void SetWorking(boolean working) { this.working = working; }

    /** @return which way the character faces: a Mover.FACING_ value */
    int Facing() { return mover.Facing(); }

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
        int facing = mover.Facing();
        int pair = facing == Mover.FACING_DOWN ? FRONT :
                facing == Mover.FACING_UP ? BACK : SIDE;
        int mode = mover.Moving() ? WALK : working ? WORK : STAND;

        SpriteRenderer.SetPosition(sprite, PixelX(tickFraction),
                PixelY(tickFraction));
        /* The sideways pictures face left. */
        SpriteRenderer.SetFlip(sprite, facing == Mover.FACING_RIGHT, false);

        if (mode != shownMode || pair != shownPair) {
            shownMode = mode;
            shownPair = pair;
            if (mode == STAND) {
                SpriteRenderer.SetFrame(sprite, standFrames[pair]);
            } else {
                anim.setDefinition(sprite,
                        mode == WALK ? walkDefs[pair] : workDefs[pair]);
            }
        }
        if (mode != STAND) anim.update(sprite, deltaMs);
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
