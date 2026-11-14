package com.alchemysoftware.wilytench;

/**
 * A scene's push buttons, and the business of clicking one.
 *
 * A button is clicked when the mouse button goes down on it and comes
 * up on it. Going down on it and sliding off before letting go does
 * nothing, which is how a player changes their mind.
 */
final class Buttons {
    static final int NONE = -1;

    private final PushButton[] buttons;
    private final Font font;

    private int over = NONE, held = NONE;

    Buttons(Font font, PushButton[] buttons) {
        this.font = font;
        this.buttons = buttons;
    }

    /**
     * Takes one frame of the mouse.
     *
     * @param px the pointer in picture pixels
     * @param pressed whether the mouse button went down this frame
     * @param released whether it came up this frame
     * @return the button clicked this frame, or NONE
     */
    int Input(int px, int py, boolean pressed, boolean released) {
        int clicked = NONE;

        over = NONE;
        for (int i = 0; i < buttons.length; i++)
            if (buttons[i].Contains(px, py)) over = i;

        if (pressed) held = over;
        if (released) {
            if (held != NONE && held == over) clicked = held;
            held = NONE;
        }
        return clicked;
    }

    /** Forgets a press in progress, as when the scene is left. */
    void Reset() { over = held = NONE; }

    void Draw() {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].Draw(font, i == held && i == over ? Skin.PRESSED :
                    i == over && held == NONE ? Skin.HOVER : Skin.NORMAL);
        }
    }
}
