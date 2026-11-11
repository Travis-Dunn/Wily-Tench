package com.alchemysoftware.wilytench;

/**
 * A small box of writing beside the pointer, saying more about what
 * it is over: a heading and, under it, some lines.
 *
 * It is drawn to look like the right-click menu, ContextMenu, and
 * like it stays inside the picture. It sits below and to the right of
 * the pointer, or above it where there is no room below, so that it
 * never covers what it describes.
 */
final class Tooltip {
    /* The same measurements as ContextMenu's. */
    private static final int PAD_X = 4;
    private static final int ROW_GAP = 2;
    private static final int HEADING_PAD_Y = 2;

    /* How far from the pointer's tip the box keeps. */
    private static final int AWAY_X = 10, AWAY_Y = 14, ABOVE_GAP = 4;

    private Tooltip() {}

    /**
     * @param lines the lines under the heading; only the first count
     *              are drawn
     * @param px where the pointer is, in picture pixels
     * @param screenW the width of the picture, which the box stays in
     */
    static void Draw(Font font, String heading, String[] lines, int count,
                     int px, int py, int screenW, int screenH) {
        int rowH = font.lineH + ROW_GAP;
        int headingH = font.lineH + 2 * HEADING_PAD_Y;
        int w = font.Width(heading, 0, heading.length());

        for (int i = 0; i < count; i++)
            w = Math.max(w, font.Width(lines[i], 0, lines[i].length()));
        w += 2 * PAD_X + 2;
        int h = headingH + 2 + (count > 0 ? count * rowH + ROW_GAP : 0);

        int x = Math.max(0, Math.min(px + AWAY_X, screenW - w));
        int y = py + AWAY_Y;
        if (y + h > screenH) y = Math.max(0, py - ABOVE_GAP - h);

        int textX = x + 1 + PAD_X;
        int rowY = y + 1 + headingH + ROW_GAP;

        Gfx.ClearClip();
        Gfx.FillRect(x, y, w, h, Palette.INK);
        Gfx.FillRect(x + 1, y + 1, w - 2, headingH, Palette.SLATE);
        Gfx.Outline(x, y, w, h, Palette.ASH);
        Text.Draw(font, heading, 0, heading.length(), textX,
                y + 1 + HEADING_PAD_Y, Palette.INK);

        for (int i = 0; i < count; i++, rowY += rowH) {
            Text.Draw(font, lines[i], 0, lines[i].length(), textX, rowY,
                    Palette.BONE);
        }
    }
}
