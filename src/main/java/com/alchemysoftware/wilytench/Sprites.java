package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.sprite.SpriteAssetBuilder;
import com.alchemysoftware.whitetail.sprite.SpriteAtlas;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * The names the game uses for its sprite atlases, layers, and frames.
 *
 * The atlases are made from the purchased art by AtlasTool, which
 * explains how a cell of a sheet becomes a frame number.
 */
final class Sprites {
    /** Width and height in pixels of every sprite and map tile. */
    static final int TILE = 16;

    static final int ATLAS_CAVE = 0;
    static final int ATLAS_HEROES = 1;

    /* Drawn back to front. */
    static final int LAYER_FLOOR = 0;
    static final int LAYER_OBJECT = 1;
    static final int LAYER_ACTOR = 2;

    static final int COLOR_MAP_NONE = 0;

    private static final String FOLDER = "sprites";

    private static final int CAVE_FRAMES_PER_ROW = 16;
    private static final int HEROES_FRAMES_PER_ROW = 4;

    private Sprites() {}

    /**
     * Loads every atlas into the sprite renderer, which must be
     * initialized.
     *
     * @return false after a fatal error has been reported
     */
    static boolean Install() {
        return Install(ATLAS_CAVE, "cave.wsa") &&
                Install(ATLAS_HEROES, "heroes.wsa");
    }

    /** @return the frame for column col, row row of the cave sheet */
    static int Cave(int col, int row) {
        return row * CAVE_FRAMES_PER_ROW + col;
    }

    /** @return the frame for column col, row row of the first hero */
    static int Hero(int col, int row) {
        return row * HEROES_FRAMES_PER_ROW + col;
    }

    private static boolean Install(int atlasId, String filename) {
        SpriteAtlas atlas =
                SpriteAssetBuilder.AtlasFromResource(FOLDER, filename);
        if (atlas == null) return false;
        SpriteRenderer.InstallAtlas(atlasId, atlas);
        return true;
    }
}
