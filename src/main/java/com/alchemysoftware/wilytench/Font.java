package com.alchemysoftware.wilytench;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;

/**
 * A bitmap font: for each character, a small picture made of "ink" and
 * "no ink", and how far to move along after drawing it.
 *
 * Letters are stored at the exact size they are drawn, one font pixel
 * to one game pixel, so they are never scaled or smoothed and stay as
 * crisp as the rest of the art. Letters have their own widths.
 *
 * Fonts are .fnt files in the "fonts" resource folder, made by FontTool,
 * which documents the format. Characters are those of ISO 8859-1; any
 * other character is shown as a question mark.
 *
 * This class only holds and measures; Text draws.
 */
final class Font {
    private static final String FOLDER = "fonts";
    private static final int MAGIC = 0x54464E31;    /* "TFN1" */
    private static final int CODES = 256;
    private static final char FALLBACK = '?';

    /** Pixels from the top of a line to the baseline letters sit on. */
    final int ascent;
    /** Pixels from the top of one line of text to the top of the next. */
    final int lineH;

    /* Per character code: which glyph, or the fallback's. */
    private final short[] glyphOf;

    /*
     * Per glyph. A glyph's ink is w * h bytes, nonzero where there is
     * ink. Its halo is the ink grown by one pixel in every direction,
     * (w + 2) * (h + 2) bytes, for drawing outlined text.
     */
    final byte[] advance, xOff, yOff;
    final int[] w, h, inkAt, haloAt;
    final byte[] ink, halo;

    private Font(int ascent, int lineH, short[] glyphOf, byte[] advance,
                 byte[] xOff, byte[] yOff, int[] w, int[] h, int[] inkAt,
                 int[] haloAt, byte[] ink, byte[] halo) {
        this.ascent = ascent;
        this.lineH = lineH;
        this.glyphOf = glyphOf;
        this.advance = advance;
        this.xOff = xOff;
        this.yOff = yOff;
        this.w = w;
        this.h = h;
        this.inkAt = inkAt;
        this.haloAt = haloAt;
        this.ink = ink;
        this.halo = halo;
    }

    /**
     * @param filename a file in the "fonts" resource folder
     * @return the font, or null after a fatal error has been reported
     */
    static Font Load(String filename) {
        InputStream raw = ResourceStreamResolver.Get(FOLDER, filename);
        if (raw == null) return null;

        try {
            try {
                return Read(new DataInputStream(raw), filename);
            } finally {
                raw.close();
            }
        } catch (IOException e) {
            ErrorHandler.LogFatalExcpAndStop(ErrStrRead(filename), e);
            return null;
        }
    }

    /** @return the glyph that stands for a character */
    int Glyph(char c) {
        return glyphOf[c < CODES ? c : FALLBACK];
    }

    /**
     * @return the width in pixels of the characters of s from start up
     *         to but not including end
     */
    int Width(CharSequence s, int start, int end) {
        int width = 0;
        for (int i = start; i < end; i++)
            width += advance[Glyph(s.charAt(i))];
        return width;
    }

    /**
     * Finds where to break a line so that it fits a width, for wrapping
     * text. Breaks after a space when it can, and mid-word only when a
     * single word is too long for the line.
     *
     * @return the index one past the last character of the line that
     *         starts at start; always beyond start unless start is the
     *         end of s
     */
    int Fit(CharSequence s, int start, int maxW) {
        int width = 0, lastBreak = -1, n = s.length();

        for (int i = start; i < n; i++) {
            char c = s.charAt(i);
            width += advance[Glyph(c)];
            if (width > maxW && c != ' ') {
                if (lastBreak > start) return lastBreak;
                return i > start ? i : start + 1;
            }
            if (c == ' ') lastBreak = i + 1;
        }
        return n;
    }

    private static Font Read(DataInputStream in, String filename)
            throws IOException {
        if (in.readInt() != MAGIC) {
            ErrorHandler.LogFatalAndStop(ErrStrMagic(filename));
            return null;
        }

        int ascent = in.readUnsignedByte();
        int lineH = ascent + in.readUnsignedByte();
        int n = in.readUnsignedShort(), g, x, y;

        short[] glyphOf = new short[CODES];
        boolean[] present = new boolean[CODES];
        byte[] advance = new byte[n], xOff = new byte[n], yOff = new byte[n];
        int[] w = new int[n], h = new int[n];
        int[] inkAt = new int[n], haloAt = new int[n];
        byte[][] rows = new byte[n][];
        int inkLen = 0, haloLen = 0;

        for (g = 0; g < n; g++) {
            int code = in.readUnsignedShort();
            advance[g] = in.readByte();
            xOff[g] = in.readByte();
            yOff[g] = in.readByte();
            w[g] = in.readUnsignedByte();
            h[g] = in.readUnsignedByte();
            rows[g] = new byte[(w[g] + 7) / 8 * h[g]];
            in.readFully(rows[g]);

            if (code < CODES) {
                glyphOf[code] = (short) g;
                present[code] = true;
            }
            inkAt[g] = inkLen;
            haloAt[g] = haloLen;
            inkLen += w[g] * h[g];
            haloLen += (w[g] + 2) * (h[g] + 2);
        }

        if (!present[FALLBACK]) {
            ErrorHandler.LogFatalAndStop(ErrStrNoFallback(filename));
            return null;
        }
        for (int code = 0; code < CODES; code++)
            if (!present[code]) glyphOf[code] = glyphOf[FALLBACK];

        byte[] ink = new byte[inkLen], halo = new byte[haloLen];
        for (g = 0; g < n; g++) {
            int rowBytes = (w[g] + 7) / 8, haloW = w[g] + 2;

            for (y = 0; y < h[g]; y++) {
                for (x = 0; x < w[g]; x++) {
                    if ((rows[g][y * rowBytes + x / 8] & 0x80 >> x % 8) == 0)
                        continue;
                    ink[inkAt[g] + y * w[g] + x] = 1;

                    /* Ink at (x, y) is at (x + 1, y + 1) in the halo
                       and spreads to the nine pixels around there. */
                    for (int dy = 0; dy < 3; dy++)
                        for (int dx = 0; dx < 3; dx++)
                            halo[haloAt[g] + (y + dy) * haloW + x + dx] = 1;
                }
            }
        }

        return new Font(ascent, lineH, glyphOf, advance, xOff, yOff, w, h,
                inkAt, haloAt, ink, halo);
    }

    private static final String CLASS = "Font";
    private static String ErrStrRead(String filename) {
        return CLASS + " failed to load [" + filename + "] because the " +
                "file is cut short or could not be read.\n";
    }
    private static String ErrStrMagic(String filename) {
        return CLASS + " failed to load [" + filename + "] because it " +
                "is not a TFN1 font file.\n";
    }
    private static String ErrStrNoFallback(String filename) {
        return CLASS + " failed to load [" + filename + "] because it " +
                "has no question mark, which stands in for characters " +
                "the font lacks.\n";
    }
}
