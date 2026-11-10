package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * Shows the part of the map that is on screen.
 *
 * It owns just enough sprites to cover the viewport, two per tile: one
 * for the ground and one for whatever stands on it. As the view moves
 * those same sprites are pointed at different map tiles, so a large map
 * costs no more to draw than a small one.
 */
final class TileView {
    private static final int TILE = Sprites.TILE;
    private static final int NO_PHASE = -1;

    private final World world;
    private final int cols, rows;
    private final int[] floorSprites, objectSprites;

    private int shownCol, shownRow, shownPhase = NO_PHASE, shownVersion;

    private TileView(World world, int cols, int rows, int[] floorSprites,
                     int[] objectSprites) {
        this.world = world;
        this.cols = cols;
        this.rows = rows;
        this.floorSprites = floorSprites;
        this.objectSprites = objectSprites;
    }

    /**
     * @return how many sprites a view of this size needs
     */
    static int SpritesNeeded(int viewW, int viewH) {
        return Cols(viewW) * Rows(viewH) * 2;
    }

    /**
     * Creates the view's sprites. The sprite renderer must be
     * initialized with the cave atlas installed.
     *
     * @return the view, or null after a fatal error has been reported
     */
    static TileView Create(World world, int viewW, int viewH) {
        int cols = Cols(viewW), rows = Rows(viewH), n = cols * rows;
        int[] floorSprites = new int[n], objectSprites = new int[n];

        /* All the ground first, then all the objects, keeps each layer's
           sprites together in drawing order. */
        for (int i = 0; i < n; i++) {
            floorSprites[i] = SpriteRenderer.CreateSprite(0, 0,
                    Sprites.ATLAS_CAVE, 0, Sprites.LAYER_FLOOR,
                    Sprites.COLOR_MAP_NONE, false, false, false);
            if (floorSprites[i] == SpriteRenderer.INVALID_HANDLE)
                return null;
        }
        for (int i = 0; i < n; i++) {
            objectSprites[i] = SpriteRenderer.CreateSprite(0, 0,
                    Sprites.ATLAS_CAVE, 0, Sprites.LAYER_OBJECT,
                    Sprites.COLOR_MAP_NONE, false, false, false);
            if (objectSprites[i] == SpriteRenderer.INVALID_HANDLE)
                return null;
        }
        return new TileView(world, cols, rows, floorSprites, objectSprites);
    }

    /**
     * Points the sprites at the tiles now in view. Does nothing unless
     * the view has crossed a tile boundary, the animation has stepped,
     * or the map itself has changed.
     *
     * @param viewX the map pixel at the left edge of the viewport
     * @param viewY the map pixel at the top edge of the viewport
     * @param phase the animation step, 0 to World.ANIM_FRAMES - 1
     */
    void Show(int viewX, int viewY, int phase) {
        int col0 = FloorDiv(viewX, TILE), row0 = FloorDiv(viewY, TILE);
        if (col0 == shownCol && row0 == shownRow && phase == shownPhase &&
                world.Version() == shownVersion) return;
        shownVersion = world.Version();
        shownCol = col0;
        shownRow = row0;
        shownPhase = phase;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c, x = col0 + c, y = row0 + r;

                if (!world.Contains(x, y)) {
                    SpriteRenderer.SetVisible(floorSprites[i], false);
                    SpriteRenderer.SetVisible(objectSprites[i], false);
                    continue;
                }
                Point(floorSprites[i], x, y, world.FloorFrame(x, y));
                Point(objectSprites[i], x, y,
                        world.ObjectFrame(x, y, phase));
                SpriteRenderer.SetColorMap(objectSprites[i],
                        world.ObjectColorMap(x, y));
            }
        }
    }

    /** @return the largest whole number not above a / b, for b above 0 */
    static int FloorDiv(int a, int b) {
        int q = a / b;
        return a % b < 0 ? q - 1 : q;
    }

    private static void Point(int sprite, int x, int y, int frame) {
        if (frame == World.NO_FRAME) {
            SpriteRenderer.SetVisible(sprite, false);
            return;
        }
        SpriteRenderer.SetPosition(sprite, x * TILE, y * TILE);
        SpriteRenderer.SetFrame(sprite, frame);
        SpriteRenderer.SetVisible(sprite, true);
    }

    /* One more than fits, for the partly visible tile at each edge. */
    private static int Cols(int viewW) { return viewW / TILE + 2; }
    private static int Rows(int viewH) { return viewH / TILE + 2; }
}
