package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.display.DisplayLayout;
import com.alchemysoftware.whitetail.error.RunFlag;
import com.alchemysoftware.whitetail.event.Event;
import com.alchemysoftware.whitetail.input.Button;
import com.alchemysoftware.whitetail.input.InputSys;
import com.alchemysoftware.whitetail.scene.Scene;
import com.alchemysoftware.whitetail.timer.Timer;

/**
 * The game world. For now it is a test card that proves the plumbing:
 *
 * - a checkerboard of 16 pixel tiles filling the world viewport;
 * - an amber tile that hops one square along the top row every game
 *   tick, so the 600 ms heartbeat is visible;
 * - an outline around whichever tile the mouse is over, which exercises
 *   the window-to-game-pixel conversion;
 * - the interface panel on the right, holding one swatch per color of
 *   the art's palette.
 *
 * Escape closes the game.
 */
final class WorldScene extends Scene {
    private static final int TILE = 16;

    private static final int SWATCH_COLS = 6;
    private static final int SWATCH_PITCH = TILE + 3;
    private static final int SWATCH_MARGIN = 10;

    private static final int NO_TILE = -1;

    private byte[] buf;
    private int w, h;
    private int viewW, viewH;

    private long ticks;
    private int hoverCol = NO_TILE, hoverRow = NO_TILE;

    @Override
    public void onInit() {
        DisplayLayout layout = Screen.GetLayout();

        buf = Screen.GetIndexBuf();
        w = Screen.GetW();
        h = Screen.GetH();
        viewW = layout.viewportWidth;
        viewH = layout.viewportHeight;
    }

    @Override
    public void onEnter() {}

    @Override
    public void onExit() {}

    @Override
    public void onInput() {
        if (InputSys.WasPressed(Button.KEY_ESCAPE)) RunFlag.Stop();

        DisplayLayout layout = Screen.GetLayout();
        int fbX = layout.WindowToFramebufferX(InputSys.GetMouseX());
        int fbY = layout.WindowToFramebufferY(InputSys.GetMouseY());

        if (fbX >= 0 && fbY >= 0 && layout.InViewport(fbX, fbY)) {
            hoverCol = layout.FramebufferToViewportX(fbX) / TILE;
            hoverRow = layout.FramebufferToViewportY(fbY) / TILE;
        } else {
            hoverCol = hoverRow = NO_TILE;
        }
    }

    @Override
    public void onUpdate(double delta) {
        while (Timer.Tick()) ticks++;
    }

    @Override
    public void onRender() {
        int cols = (viewW + TILE - 1) / TILE;
        int rows = (viewH + TILE - 1) / TILE;
        int col, row, i;

        for (row = 0; row < rows; row++) {
            for (col = 0; col < cols; col++) {
                FillRect(col * TILE, row * TILE, TILE, TILE,
                        ((col + row) & 1) == 0 ?
                        Palette.NIGHT : Palette.INK, viewW, viewH);
            }
        }

        FillRect((int) (ticks % cols) * TILE, 0, TILE, TILE,
                Palette.AMBER, viewW, viewH);

        if (hoverCol != NO_TILE) {
            int x = hoverCol * TILE, y = hoverRow * TILE;
            FillRect(x, y, TILE, 1, Palette.BONE, viewW, viewH);
            FillRect(x, y + TILE - 1, TILE, 1, Palette.BONE, viewW, viewH);
            FillRect(x, y, 1, TILE, Palette.BONE, viewW, viewH);
            FillRect(x + TILE - 1, y, 1, TILE, Palette.BONE, viewW, viewH);
        }

        FillRect(viewW, 0, w - viewW, h, Palette.SLATE, w, h);
        FillRect(viewW, 0, 1, h, Palette.ASH, w, h);

        for (i = 0; i < Palette.ART_COUNT; i++) {
            int x = viewW + SWATCH_MARGIN + (i % SWATCH_COLS) * SWATCH_PITCH;
            int y = SWATCH_MARGIN + (i / SWATCH_COLS) * SWATCH_PITCH;

            FillRect(x - 1, y - 1, TILE + 2, TILE + 2, Palette.INK, w, h);
            FillRect(x, y, TILE, TILE, Palette.ART_FIRST + i, w, h);
        }
    }

    @Override
    public void onDestroy() {
        buf = null;
    }

    @Override
    public boolean handleEvent(Event e) { return false; }

    /* Fills a rectangle with one palette index, clipped to the area
       from (0, 0) to (clipW, clipH). */
    private void FillRect(int x, int y, int rectW, int rectH, int index,
                          int clipW, int clipH) {
        int x1 = Math.min(x + rectW, clipW), y1 = Math.min(y + rectH, clipH);
        int x0 = Math.max(x, 0), y0 = Math.max(y, 0);
        byte b = (byte) index;

        for (int py = y0; py < y1; py++) {
            int rowStart = py * w;
            for (int px = x0; px < x1; px++) buf[rowStart + px] = b;
        }
    }
}
