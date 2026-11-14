package com.alchemysoftware.wilytench;

/**
 * A push button with a word on it, drawn from the Skin.
 *
 * A button does nothing by itself. The scene that owns it asks whether
 * the pointer is on it and draws it in the right state; Buttons works
 * out which of a scene's buttons has been clicked.
 */
final class PushButton {
    final String label;
    final int x, y, cells;

    /**
     * @param x the left edge, in picture pixels
     * @param cells the width, in cells of 16 pixels
     */
    PushButton(String label, int x, int y, int cells) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.cells = cells;
    }

    boolean Contains(int px, int py) {
        return px >= x && px < x + cells * Skin.CELL && py >= y &&
                py < y + Skin.BUTTON_H;
    }

    /** @param state Skin.NORMAL, Skin.HOVER, or Skin.PRESSED */
    void Draw(Font font, int state) {
        int w = cells * Skin.CELL;

        Skin.DrawButton(x, y, cells, state);
        Text.Draw(font, label, 0, label.length(),
                x + (w - font.Width(label, 0, label.length())) / 2,
                y + Skin.ButtonDrop(state) +
                (Skin.BUTTON_FACE_H - font.lineH) / 2, Palette.INK);
    }
}
