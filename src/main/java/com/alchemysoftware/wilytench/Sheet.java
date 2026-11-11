package com.alchemysoftware.wilytench;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;

/**
 * A sheet of 16 pixel pictures, read from a Whitetail .wsa atlas file.
 *
 * The game reads these files itself, rather than through Whitetail's
 * SpriteAssetBuilder, because it needs two things a Whitetail
 * SpriteAtlas keeps to itself: the sheet's width, to turn a column and
 * row into a frame number, and the pixels, to draw icons in the
 * interface, where the sprite renderer cannot reach. The same pixel
 * array is then handed to SpriteAtlas.Create(), so nothing is held
 * twice.
 */
final class Sheet {
    /** Width and height in pixels of every frame. */
    static final int CELL = 16;

    private static final String FOLDER = "sprites";
    private static final int MAGIC = 0x57534131;    /* "WSA1" */
    private static final int HEADER_LEN = 10;

    final int w, h;
    /** How many frames sit side by side across the sheet. */
    final int perRow;
    /** One palette index per pixel, row by row; 0 is see-through. */
    final byte[] texels;

    private Sheet(int w, int h, byte[] texels) {
        this.w = w;
        this.h = h;
        this.texels = texels;
        perRow = w / CELL;
    }

    /**
     * @param filename a file in the "sprites" resource folder
     * @return the sheet, or null after a fatal error has been reported
     */
    static Sheet Load(String filename) {
        InputStream raw = ResourceStreamResolver.Get(FOLDER, filename);
        if (raw == null) return null;

        try {
            try {
                DataInputStream in = new DataInputStream(raw);
                byte[] header = new byte[HEADER_LEN];
                in.readFully(header);

                /* The magic reads the same either way round; the sizes
                   are little-endian. */
                int magic = (header[0] & 0xFF) << 24 |
                        (header[1] & 0xFF) << 16 |
                        (header[2] & 0xFF) << 8 | (header[3] & 0xFF);
                int w = (header[4] & 0xFF) | (header[5] & 0xFF) << 8;
                int h = (header[6] & 0xFF) | (header[7] & 0xFF) << 8;
                int cell = (header[8] & 0xFF) | (header[9] & 0xFF) << 8;

                if (magic != MAGIC || cell != CELL || w == 0 || h == 0) {
                    ErrorHandler.LogFatalAndStop(ErrStrFormat(filename));
                    return null;
                }

                byte[] texels = new byte[w * h];
                in.readFully(texels);
                return new Sheet(w, h, texels);
            } finally {
                raw.close();
            }
        } catch (IOException e) {
            ErrorHandler.LogFatalExcpAndStop(ErrStrRead(filename), e);
            return null;
        }
    }

    /** @return the frame number of the picture at a column and row */
    int Frame(int col, int row) {
        return row * perRow + col;
    }

    private static final String CLASS = "Sheet";
    private static String ErrStrRead(String filename) {
        return CLASS + " failed to load [" + filename + "] because the " +
                "file is cut short or could not be read.\n";
    }
    private static String ErrStrFormat(String filename) {
        return CLASS + " failed to load [" + filename + "] because it " +
                "is not a WSA1 atlas of [" + CELL + "] pixel frames.\n";
    }
}
