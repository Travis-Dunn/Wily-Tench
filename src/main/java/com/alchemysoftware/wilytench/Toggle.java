package com.alchemysoftware.wilytench;

/**
 * A checkbox or a round option button, with its words to its right.
 * Clicking the words counts as clicking the box.
 *
 * Like PushButton, it holds no state: the scene that owns it says whether
 * it is on when drawing it, and decides what a click means.
 */
final class Toggle {
    private static final int LABEL_GAP = 4;

    final String label;
    final int x, y;
    private final boolean round;
    private final int w;

    /**
     * @param x the left edge of the box, in picture pixels
     * @param round whether to draw it as one of a set of options, of
     *              which one is chosen, and not as a checkbox
     */
    Toggle(Font font, String label, int x, int y, boolean round) {
        this.label = label;
        this.x = x;
        this.y = y;
        this.round = round;
        w = Skin.CELL + LABEL_GAP + font.Width(label, 0, label.length());
    }

    boolean Contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + Skin.CELL;
    }

    /** @param lit whether the pointer is on it */
    void Draw(Font font, boolean on, boolean lit) {
        if (round) Skin.DrawRadio(x, y, on);
        else Skin.DrawCheckbox(x, y, on);
        Text.Draw(font, label, 0, label.length(), x + Skin.CELL + LABEL_GAP,
                y + (Skin.CELL - font.lineH) / 2,
                lit ? Palette.AMBER : Palette.BONE);
    }
}
