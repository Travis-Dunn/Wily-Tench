package com.alchemysoftware.wilytench;

/**
 * Direct drawing into the index buffer, for the things that are not
 * sprites: panels, outlines, and markers.
 */
final class Gfx {
    private Gfx() {}

    /**
     * Fills a rectangle with one palette index. Only the part inside
     * the clip rectangle, which runs from (clipX0, clipY0) up to but
     * not including (clipX1, clipY1), is drawn.
     *
     * @param buf the index buffer from Screen.GetIndexBuf()
     * @param bufW the width of that buffer in pixels
     */
    static void FillRect(byte[] buf, int bufW, int x, int y, int w, int h,
                         int index, int clipX0, int clipY0, int clipX1,
                         int clipY1) {
        int x0 = Math.max(x, clipX0), y0 = Math.max(y, clipY0);
        int x1 = Math.min(x + w, clipX1), y1 = Math.min(y + h, clipY1);
        byte b = (byte) index;

        for (int py = y0; py < y1; py++) {
            int rowStart = py * bufW;
            for (int px = x0; px < x1; px++) buf[rowStart + px] = b;
        }
    }

    /**
     * Draws the one-pixel outline of a rectangle, clipped as FillRect().
     */
    static void Outline(byte[] buf, int bufW, int x, int y, int w, int h,
                        int index, int clipX0, int clipY0, int clipX1,
                        int clipY1) {
        FillRect(buf, bufW, x, y, w, 1, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, x, y + h - 1, w, 1, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, x, y, 1, h, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, x + w - 1, y, 1, h, index,
                clipX0, clipY0, clipX1, clipY1);
    }

    /**
     * Draws only the corners of a rectangle's outline, each arm len
     * pixels long, clipped as FillRect().
     */
    static void Corners(byte[] buf, int bufW, int x, int y, int w, int h,
                        int len, int index, int clipX0, int clipY0,
                        int clipX1, int clipY1) {
        int r = x + w - 1, b = y + h - 1;

        FillRect(buf, bufW, x, y, len, 1, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, x, y, 1, len, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, r - len + 1, y, len, 1, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, r, y, 1, len, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, x, b, len, 1, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, x, b - len + 1, 1, len, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, r - len + 1, b, len, 1, index,
                clipX0, clipY0, clipX1, clipY1);
        FillRect(buf, bufW, r, b - len + 1, 1, len, index,
                clipX0, clipY0, clipX1, clipY1);
    }
}
