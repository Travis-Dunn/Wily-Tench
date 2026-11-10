package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.input.Button;
import com.alchemysoftware.whitetail.input.ButtonEvent;
import com.alchemysoftware.whitetail.input.InputSys;

/**
 * The chat box under the game view: a scrolling record of what the
 * game has told the player, and a line for typing.
 *
 * Messages longer than the box is wide are wrapped onto several lines.
 * The newest line is at the bottom. The mouse wheel, with the pointer
 * over the box, or Page Up and Page Down, scroll back through older
 * lines; a new message jumps back to the bottom.
 *
 * Whatever is typed appears on the bottom row. Enter says it.
 */
final class ChatBox {
    private static final int HISTORY = 200;
    private static final int INPUT_MAX = 80;

    private static final int PAD = 4;
    /* Space left above the top row and below the bottom one. */
    private static final int HISTORY_MARGIN = 2;
    private static final int LINE_GAP = 1;
    private static final int SCROLLBAR_W = 2;

    private static final String PROMPT = "You: ";
    private static final String CARET = "*";

    private static final char FIRST_PRINTABLE = ' ', LAST_ASCII = '~';
    private static final char FIRST_LATIN1 = 0xA0, LAST_LATIN1 = 0xFF;

    private final Font font;
    private final int x, y, w, h;
    private final int rowH, rows, textW, inputY, ruleY;

    /* A ring of the last HISTORY lines; count of them ever added. */
    private final String[] lines = new String[HISTORY];
    private final byte[] colors = new byte[HISTORY];
    private int added;

    /* How many lines up from the newest the bottom row is showing. */
    private int scroll;

    private final StringBuffer input = new StringBuffer(INPUT_MAX);

    /**
     * @param font the font for everything in the box
     * @param x the left edge of the box, in picture pixels
     * @param y the top edge
     * @param w its width
     * @param h its height
     */
    ChatBox(Font font, int x, int y, int w, int h) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;

        rowH = font.lineH + LINE_GAP;
        inputY = y + h - PAD - font.lineH;
        ruleY = inputY - PAD;
        rows = (ruleY - y - 2 * HISTORY_MARGIN) / rowH;
        textW = w - 2 * PAD - SCROLLBAR_W - PAD;
    }

    /**
     * Adds a message, wrapped to the width of the box, and scrolls to
     * show it.
     *
     * @param color the palette index to write it in
     */
    void Add(String message, int color) {
        int start = 0, n = message.length();

        while (start < n) {
            int end = font.Fit(message, start, textW);
            int slot = added++ % HISTORY;

            lines[slot] = message.substring(start, end);
            colors[slot] = (byte) color;
            start = end;
        }
        scroll = 0;
    }

    /**
     * Takes this frame's typing and scrolling from InputSys.
     *
     * @param mouseX the pointer in picture pixels, or negative if it
     *               is outside the picture
     */
    void Input(int mouseX, int mouseY) {
        for (int i = 0, n = InputSys.GetEventCount(); i < n; i++) {
            ButtonEvent e = InputSys.GetEvent(i);
            if (!e.isDown()) continue;

            char c = e.getChar();
            int button = e.getButton();

            if (button == Button.KEY_ENTER ||
                    button == Button.KEY_NUMPAD_ENTER) {
                if (input.length() > 0) {
                    Add(PROMPT + input, Palette.MINT);
                    input.setLength(0);
                }
            } else if (button == Button.KEY_BACKSPACE) {
                if (input.length() > 0) input.setLength(input.length() - 1);
            } else if (button == Button.KEY_PAGE_UP) {
                Scroll(rows - 1);
            } else if (button == Button.KEY_PAGE_DOWN) {
                Scroll(1 - rows);
            } else if (Printable(c) && input.length() < INPUT_MAX) {
                input.append(c);
            }
        }

        /* The wheel's units vary by system, so only its direction is
           used: one line per frame in which it moved. */
        int wheel = InputSys.GetWheelDelta();
        if (wheel != 0 && mouseX >= x && mouseX < x + w && mouseY >= y &&
                mouseY < y + h) Scroll(wheel > 0 ? 1 : -1);
    }

    void Draw() {
        int stored = Math.min(added, HISTORY);
        int textX = x + PAD, bottomRowY = ruleY - HISTORY_MARGIN - rowH;

        Gfx.SetClip(x, y, w, h);
        Gfx.FillRect(x, y, w, h, Palette.NIGHT);
        Gfx.FillRect(x, y, w, 1, Palette.SLATE);
        Gfx.FillRect(x + PAD, ruleY, w - 2 * PAD, 1, Palette.SLATE);

        for (int row = 0; row < rows; row++) {
            int back = scroll + row;
            if (back >= stored) break;

            int slot = (added - 1 - back) % HISTORY;
            String line = lines[slot];
            Text.Draw(font, line, 0, line.length(), textX,
                    bottomRowY - row * rowH + LINE_GAP, colors[slot]);
        }

        if (stored > rows) {
            int trackH = rows * rowH, trackY = bottomRowY + rowH - trackH;
            int barH = Math.max(trackH * rows / stored, 3);
            int barY = trackY + (trackH - barH) *
                    (stored - rows - scroll) / (stored - rows);
            Gfx.FillRect(x + w - PAD - SCROLLBAR_W, barY, SCROLLBAR_W, barH,
                    Palette.SLATE);
        }

        /* When the typed text outgrows the row, show its tail. */
        int promptEnd = Text.Draw(font, PROMPT, 0, PROMPT.length(), textX,
                inputY, Palette.ASH);
        int room = x + w - PAD - promptEnd -
                font.Width(CARET, 0, CARET.length());
        int from = 0, len = input.length();
        while (font.Width(input, from, len) > room) from++;
        int caretX = Text.Draw(font, input, from, len, promptEnd, inputY,
                Palette.BONE);
        Text.Draw(font, CARET, 0, CARET.length(), caretX, inputY,
                Palette.AMBER);

        Gfx.ClearClip();
    }

    private void Scroll(int by) {
        int max = Math.max(Math.min(added, HISTORY) - rows, 0);
        scroll = Math.max(0, Math.min(scroll + by, max));
    }

    private static boolean Printable(char c) {
        return (c >= FIRST_PRINTABLE && c <= LAST_ASCII) ||
                (c >= FIRST_LATIN1 && c <= LAST_LATIN1);
    }
}
