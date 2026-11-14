package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.display.DisplayLayout;
import com.alchemysoftware.whitetail.input.InputSys;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;
import com.alchemysoftware.whitetail.timer.Timer;

/**
 * What every scene draws with: the picture, the fonts, the sprite
 * renderer, the game frame, and everything in the data files. Loaded
 * once, before the scenes, because the scenes come up in no particular
 * order.
 *
 * Also a few things the menu scenes have in common: where the pointer
 * is, and where on the picture a menu goes.
 */
final class Assets {
    /**
     * The size menus are laid out for, which is the smallest picture
     * the game is meant ever to have. On a larger picture a menu sits
     * in the middle.
     */
    static final int MENU_W = 384, MENU_H = 216;

    /* Sprites beyond the map view: the player, with room to spare for
       the creatures and effects to come. */
    private static final int SPARE_SPRITES = 256;

    static Font small, bold, title;
    /** The game frame, drawn over the view of the world. */
    static Bitmap frame;

    private static boolean spritesInit;

    private Assets() {}

    /**
     * Screen must be initialized.
     *
     * @return false after a fatal error has been reported
     */
    static boolean Init() {
        DisplayLayout layout = Screen.GetLayout();
        byte[] buf = Screen.GetIndexBuf();
        int w = Screen.GetW(), h = Screen.GetH();

        Gfx.Init(buf, w, h);

        if ((small = Font.Load("small.fnt")) == null) return false;
        if ((bold = Font.Load("bold.fnt")) == null) return false;
        if ((title = Font.Load("title.fnt")) == null) return false;

        if (!SpriteRenderer.Init(buf, w, h, layout.viewportX,
                layout.viewportY, layout.viewportWidth,
                layout.viewportHeight, TileView.SpritesNeeded(
                layout.viewportWidth, layout.viewportHeight) +
                SPARE_SPRITES)) return false;
        spritesInit = true;

        if (!Content.Load()) return false;
        return (frame = Bitmap.Load(Layout.frame)) != null;
    }

    /** Safe to call after a failed or partial Init(). */
    static void Shutdown() {
        if (spritesInit) SpriteRenderer.Shutdown();
        spritesInit = false;
        Content.Shutdown();
        Gfx.Shutdown();
        small = bold = title = null;
        frame = null;
    }

    /** @return the left edge of where a menu goes on the picture */
    static int MenuX() { return (Screen.GetW() - MENU_W) / 2; }

    /** @return the top edge of where a menu goes on the picture */
    static int MenuY() { return (Screen.GetH() - MENU_H) / 2; }

    /**
     * @return the pointer in picture pixels, negative when it is
     *         outside the picture
     */
    static int PointerX() {
        return Screen.GetLayout().WindowToFramebufferX(InputSys.GetMouseX());
    }

    static int PointerY() {
        return Screen.GetLayout().WindowToFramebufferY(InputSys.GetMouseY());
    }

    /**
     * Throws away the game ticks that have come due. A scene in which
     * the world stands still calls this every frame, or the world
     * would run through all of them at once on the way back in.
     */
    static void DiscardTicks() {
        while (Timer.Tick()) { /* Not wanted. */ }
    }
}
