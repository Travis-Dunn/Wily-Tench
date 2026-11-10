package com.alchemysoftware.wilytench;

/**
 * Direct drawing into the picture, for the things that are not sprites:
 * panels, outlines, markers, and (through Text) writing.
 *
 * Everything drawn is cut off at the clip rectangle, which starts as
 * the whole picture.
 */
final class Gfx {
    /* Shared with Text, which draws into the same picture. */
    static byte[] buf;
    static int bufW, bufH;
    static int clipX0, clipY0, clipX1, clipY1;

    private Gfx() {}

    /**
     * @param buf the index buffer from Screen.GetIndexBuf()
     * @param w the width of that buffer in pixels
     * @param h the height of that buffer in pixels
     */
    static void Init(byte[] buf, int w, int h) {
        Gfx.buf = buf;
        bufW = w;
        bufH = h;
        ClearClip();
    }

    static void Shutdown() {
        buf = null;
        bufW = bufH = clipX0 = clipY0 = clipX1 = clipY1 = 0;
    }

    /**
     * Limits drawing to a rectangle, itself limited to the picture.
     */
    static void SetClip(int x, int y, int w, int h) {
        clipX0 = Math.max(x, 0);
        clipY0 = Math.max(y, 0);
        clipX1 = Math.min(x + w, bufW);
        clipY1 = Math.min(y + h, bufH);
    }

    /** Lets drawing reach the whole picture again. */
    static void ClearClip() {
        clipX0 = clipY0 = 0;
        clipX1 = bufW;
        clipY1 = bufH;
    }

    /** Fills a rectangle with one palette index. */
    static void FillRect(int x, int y, int w, int h, int index) {
        int x0 = Math.max(x, clipX0), y0 = Math.max(y, clipY0);
        int x1 = Math.min(x + w, clipX1), y1 = Math.min(y + h, clipY1);
        byte b = (byte) index;

        for (int py = y0; py < y1; py++) {
            int rowStart = py * bufW;
            for (int px = x0; px < x1; px++) buf[rowStart + px] = b;
        }
    }

    /** Draws the one-pixel outline of a rectangle. */
    static void Outline(int x, int y, int w, int h, int index) {
        FillRect(x, y, w, 1, index);
        FillRect(x, y + h - 1, w, 1, index);
        FillRect(x, y, 1, h, index);
        FillRect(x + w - 1, y, 1, h, index);
    }

    /**
     * Draws only the corners of a rectangle's outline, each arm len
     * pixels long.
     */
    static void Corners(int x, int y, int w, int h, int len, int index) {
        int r = x + w - 1, b = y + h - 1;

        FillRect(x, y, len, 1, index);
        FillRect(x, y, 1, len, index);
        FillRect(r - len + 1, y, len, 1, index);
        FillRect(r, y, 1, len, index);
        FillRect(x, b, len, 1, index);
        FillRect(x, b - len + 1, 1, len, index);
        FillRect(r - len + 1, b, len, 1, index);
        FillRect(r, b - len + 1, 1, len, index);
    }
}
