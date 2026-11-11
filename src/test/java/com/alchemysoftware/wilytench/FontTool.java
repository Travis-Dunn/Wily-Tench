package com.alchemysoftware.wilytench;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Development tool, not part of the game: converts font files into the
 * .fnt bitmap fonts the game loads.
 *
 * Run from the repository root with "mvn test-compile exec:java@fonts",
 * or with the "Build Fonts" run configuration. The output in
 * src/main/resources/fonts is committed, so this only needs running
 * when a font or the JOBS table changes.
 *
 * Two kinds of source are understood:
 *
 * - .bdf, the plain-text bitmap font format. Glyphs are copied exactly.
 * - .ttf, drawn once at the given pixel size with smoothing off. This
 *   only looks right for "pixel" fonts, at the size they were designed
 *   for or a whole multiple of it.
 *
 * Only the characters of ISO 8859-1 (Western European) are kept.
 *
 * The .fnt format, all multi-byte values big-endian:
 *
 *   4 bytes   the ASCII letters TFN1
 *   1 byte    ascent: pixels from the top of a line to the baseline
 *   1 byte    descent: pixels from the baseline to the bottom of a line
 *   2 bytes   number of glyphs
 *   then for each glyph:
 *   2 bytes   character code
 *   1 byte    advance: pixels to move right after drawing it (signed)
 *   1 byte    x offset of the bitmap from the pen position (signed)
 *   1 byte    y offset of the bitmap from the top of the line (signed)
 *   1 byte    bitmap width
 *   1 byte    bitmap height
 *   n bytes   the bitmap, top row first, each row padded to whole
 *             bytes, leftmost pixel in the highest bit
 */
public final class FontTool {
    private static final String OUT_DIR = "src/main/resources/fonts";
    private static final String SRC_DIR = "fonts";

    /* { source file, output name, pixel size (used for .ttf only) } */
    private static final Object[][] JOBS = {
        { "helvR08.bdf", "small.fnt", 0 },
        { "helvB08.bdf", "bold.fnt", 0 },
        { "helvB12.bdf", "title.fnt", 0 },
    };

    private static final int FIRST_PRINTABLE = 32, LAST_ASCII = 126;
    private static final int FIRST_LATIN1 = 160, LAST_LATIN1 = 255;

    private static final class Glyph {
        int code, advance, xOff, yOff, w, h;
        boolean[] ink;
    }

    private FontTool() {}

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        for (int i = 0; i < JOBS.length; i++) {
            String src = (String) JOBS[i][0], out = (String) JOBS[i][1];
            int px = ((Integer) JOBS[i][2]).intValue();
            int[] metrics = new int[2];
            List<Glyph> glyphs = new ArrayList<Glyph>();

            if (src.endsWith(".bdf")) ReadBdf(src, metrics, glyphs);
            else ReadTtf(src, px, metrics, glyphs);
            Write(out, metrics, glyphs);

            System.out.println(src + " -> " + new File(OUT_DIR, out) + " (" +
                    glyphs.size() + " glyphs, line height " +
                    (metrics[0] + metrics[1]) + ")");
        }
    }

    private static boolean Wanted(int code) {
        return (code >= FIRST_PRINTABLE && code <= LAST_ASCII) ||
                (code >= FIRST_LATIN1 && code <= LAST_LATIN1);
    }

    private static void ReadBdf(String src, int[] metrics, List<Glyph> out)
            throws Exception {
        BufferedReader in =
                new BufferedReader(new FileReader(new File(SRC_DIR, src)));
        try {
            Glyph g = null;
            int bbY = 0, row = -1;
            String line;

            while ((line = in.readLine()) != null) {
                String[] t = line.trim().split("\\s+");

                if (row >= 0 && g != null && !t[0].equals("ENDCHAR")) {
                    /* A bitmap row: hex digits, leftmost pixel first. */
                    for (int x = 0; x < g.w; x++) {
                        int nibble = Character.digit(t[0].charAt(x / 4), 16);
                        g.ink[row * g.w + x] =
                                (nibble >> (3 - x % 4) & 1) != 0;
                    }
                    row++;
                } else if (t[0].equals("FONT_ASCENT")) {
                    metrics[0] = Integer.parseInt(t[1]);
                } else if (t[0].equals("FONT_DESCENT")) {
                    metrics[1] = Integer.parseInt(t[1]);
                } else if (t[0].equals("STARTCHAR")) {
                    g = new Glyph();
                } else if (t[0].equals("ENCODING")) {
                    g.code = Integer.parseInt(t[1]);
                } else if (t[0].equals("DWIDTH")) {
                    g.advance = Integer.parseInt(t[1]);
                } else if (t[0].equals("BBX")) {
                    g.w = Integer.parseInt(t[1]);
                    g.h = Integer.parseInt(t[2]);
                    g.xOff = Integer.parseInt(t[3]);
                    bbY = Integer.parseInt(t[4]);
                    g.ink = new boolean[g.w * g.h];
                } else if (t[0].equals("BITMAP")) {
                    row = 0;
                } else if (t[0].equals("ENDCHAR")) {
                    /* BDF measures up from the baseline to the bitmap's
                       bottom edge; the game measures down from the top
                       of the line to its top edge. */
                    g.yOff = metrics[0] - (bbY + g.h);
                    if (Wanted(g.code)) out.add(g);
                    g = null;
                    row = -1;
                }
            }
        } finally {
            in.close();
        }
        if (metrics[0] + metrics[1] == 0 || out.isEmpty())
            Fail(src + " is not a usable BDF font");
    }

    private static void ReadTtf(String src, int px, int[] metrics,
                                List<Glyph> out) throws Exception {
        java.awt.Font font = java.awt.Font.createFont(
                java.awt.Font.TRUETYPE_FONT, new File(SRC_DIR, src))
                .deriveFont((float) px);
        int pad = px, size = px * 3;
        BufferedImage img =
                new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        g2.setFont(font);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        FontMetrics fm = g2.getFontMetrics();
        metrics[0] = fm.getAscent();
        metrics[1] = fm.getDescent();

        for (int code = FIRST_PRINTABLE; code <= LAST_LATIN1; code++) {
            if (!Wanted(code) || !font.canDisplay((char) code)) continue;

            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, size, size);
            g2.setColor(Color.WHITE);
            g2.drawString(String.valueOf((char) code), pad,
                    pad + metrics[0]);

            int x0 = size, y0 = size, x1 = -1, y1 = -1;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if ((img.getRGB(x, y) & 0xFF) < 128) continue;
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                }
            }

            Glyph g = new Glyph();
            g.code = code;
            g.advance = fm.charWidth((char) code);
            if (x1 >= 0) {
                g.w = x1 - x0 + 1;
                g.h = y1 - y0 + 1;
                g.xOff = x0 - pad;
                g.yOff = y0 - pad;
            }
            g.ink = new boolean[g.w * g.h];
            for (int y = 0; y < g.h; y++)
                for (int x = 0; x < g.w; x++)
                    g.ink[y * g.w + x] =
                            (img.getRGB(x0 + x, y0 + y) & 0xFF) >= 128;
            out.add(g);
        }
        g2.dispose();
        if (out.isEmpty()) Fail(src + " has no usable glyphs");
    }

    private static void Write(String outName, int[] metrics,
                              List<Glyph> glyphs) throws Exception {
        DataOutputStream out = new DataOutputStream(
                new FileOutputStream(new File(OUT_DIR, outName)));
        try {
            out.writeBytes("TFN1");
            out.writeByte(metrics[0]);
            out.writeByte(metrics[1]);
            out.writeShort(glyphs.size());

            for (int i = 0; i < glyphs.size(); i++) {
                Glyph g = glyphs.get(i);
                if (g.w > 255 || g.h > 255) Fail(outName + " glyph too big");

                out.writeShort(g.code);
                out.writeByte(g.advance);
                out.writeByte(g.xOff);
                out.writeByte(g.yOff);
                out.writeByte(g.w);
                out.writeByte(g.h);
                for (int y = 0; y < g.h; y++) {
                    for (int x = 0; x < g.w; x += 8) {
                        int b = 0;
                        for (int bit = 0; bit < 8 && x + bit < g.w; bit++)
                            if (g.ink[y * g.w + x + bit]) b |= 0x80 >> bit;
                        out.writeByte(b);
                    }
                }
            }
        } finally {
            out.close();
        }
    }

    private static void Fail(String why) {
        throw new IllegalStateException("FontTool: " + why);
    }
}
