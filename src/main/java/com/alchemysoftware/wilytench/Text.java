package com.alchemysoftware.wilytench;

/**
 * Draws writing into the picture, through Gfx's buffer and clip
 * rectangle.
 *
 * Every method draws the characters of s from start up to but not
 * including end, with the top left of the first line of text at (x, y),
 * and returns the x where the next character would go. Nothing here
 * allocates memory, so text may be drawn freely every frame.
 *
 * Three looks are offered:
 *
 * - Draw: plain, for text on a flat background.
 * - DrawShadowed: with a one-pixel drop shadow down and to the right.
 * - DrawOutlined: with a one-pixel border all round, for text laid
 *   over the game world, where the background could be anything.
 */
final class Text {
    private Text() {}

    static int Draw(Font f, CharSequence s, int start, int end, int x,
                    int y, int color) {
        return Pass(f, s, start, end, x, y, color, false);
    }

    static int DrawShadowed(Font f, CharSequence s, int start, int end,
                            int x, int y, int color, int shadowColor) {
        Pass(f, s, start, end, x + 1, y + 1, shadowColor, false);
        return Pass(f, s, start, end, x, y, color, false);
    }

    /* The outline of the whole run is laid down before any of the
       letters, so one letter's outline never covers its neighbor. */
    static int DrawOutlined(Font f, CharSequence s, int start, int end,
                            int x, int y, int color, int outlineColor) {
        Pass(f, s, start, end, x, y, outlineColor, true);
        return Pass(f, s, start, end, x, y, color, false);
    }

    private static int Pass(Font f, CharSequence s, int start, int end,
                            int x, int y, int color, boolean halo) {
        byte[] src = halo ? f.halo : f.ink;
        int grow = halo ? 1 : 0;

        for (int i = start; i < end; i++) {
            int g = f.Glyph(s.charAt(i));
            int w = f.w[g] + 2 * grow, h = f.h[g] + 2 * grow;

            if (f.w[g] != 0) {
                Blit(src, halo ? f.haloAt[g] : f.inkAt[g], w, h,
                        x + f.xOff[g] - grow, y + f.yOff[g] - grow, color);
            }
            x += f.advance[g];
        }
        return x;
    }

    private static void Blit(byte[] src, int srcAt, int w, int h, int x,
                             int y, int color) {
        int x0 = Math.max(x, Gfx.clipX0), y0 = Math.max(y, Gfx.clipY0);
        int x1 = Math.min(x + w, Gfx.clipX1);
        int y1 = Math.min(y + h, Gfx.clipY1);
        byte[] dst = Gfx.buf;
        byte b = (byte) color;

        for (int py = y0; py < y1; py++) {
            int s = srcAt + (py - y) * w + (x0 - x);
            int d = py * Gfx.bufW + x0;
            for (int px = x0; px < x1; px++, s++, d++)
                if (src[s] != 0) dst[d] = b;
        }
    }
}
