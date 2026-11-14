package com.alchemysoftware.wilytench;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.image.Image;
import com.alchemysoftware.whitetail.image.ImageBuilder;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;

/**
 * A whole picture of any size, such as the game frame: the second way
 * of drawing, beside sprites.
 *
 * Sprites are 16 pixel cells packed into atlases by a tool. A bitmap
 * is one PNG file in the "images" resource folder, read as it is when
 * the game starts, with nothing to rebuild after changing it. It may
 * be saved in indexed color, as Aseprite does for art in a palette, or
 * in full color. Gfx
 * draws one anywhere on the picture.
 *
 * The game draws in palette colors only, so every pixel of the PNG
 * must be exactly a color of data/palette.json, or fully see-through.
 * A pixel that is neither stops the game with a message saying which
 * pixel and what color, since the alternative is a picture quietly
 * drawn wrong.
 */
final class Bitmap {
    /** The palette index that means "nothing here". */
    static final int CLEAR = 0;

    private static final String FOLDER = "images";
    private static final int OPAQUE = 255;

    final int w, h;
    /** One palette index per pixel, row by row; CLEAR is see-through. */
    final byte[] texels;

    private Bitmap(int w, int h, byte[] texels) {
        this.w = w;
        this.h = h;
        this.texels = texels;
    }

    /**
     * @param filename a PNG in the "images" resource folder; Palette
     *                 must already be loaded
     * @return the picture, or null after a fatal error has been
     *         reported
     */
    static Bitmap Load(String filename) {
        InputStream in = ResourceStreamResolver.Get(FOLDER, filename);
        if (in == null) return null;

        try {
            Image image = Decode(ReadAll(in), filename);
            return image == null ? null : FromImage(image, filename);
        } catch (DataError e) {
            ErrorHandler.LogFatalAndStop("The game failed to start " +
                    "because of a mistake in one of its pictures. " +
                    e.getMessage() + "\n");
            return null;
        } catch (IOException e) {
            ErrorHandler.LogFatalExcpAndStop("The game failed to start " +
                    "because the picture [" + filename + "] could not " +
                    "be read.\n", e);
            return null;
        }
    }

    /**
     * Decodes a PNG file of any kind the game reads: indexed color by
     * IndexedPng, the rest by Whitetail.
     *
     * @param file the whole file
     * @return its pixels, or null after Whitetail has reported a
     *         fatal error
     * @throws DataError if it is an indexed PNG that cannot be read
     */
    static Image Decode(byte[] file, String filename) {
        return IndexedPng.Is(file) ? IndexedPng.Decode(file, filename) :
                ImageBuilder.FromPNG(new ByteArrayInputStream(file));
    }

    /** @return everything a stream holds; the stream is closed */
    static byte[] ReadAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) bytes.write(chunk, 0, n);
            return bytes.toByteArray();
        } finally {
            in.close();
        }
    }

    /**
     * Turns decoded pixels into palette indices.
     *
     * @throws DataError naming the first pixel that is not a palette
     *                   color
     */
    static Bitmap FromImage(Image image, String filename) {
        int w = image.width, h = image.height;
        int channels = image.format == Image.FORMAT_RGBA_8 ? 4 :
                image.format == Image.FORMAT_RGB_8 ? 3 : 1;
        byte[] src = image.data, texels = new byte[w * h];

        for (int i = 0; i < w * h; i++) {
            int at = i * channels;
            int r = src[at] & 0xFF;
            int g = channels == 1 ? r : src[at + 1] & 0xFF;
            int b = channels == 1 ? r : src[at + 2] & 0xFF;
            int a = channels == 4 ? src[at + 3] & 0xFF : OPAQUE;

            if (a == 0) {
                texels[i] = CLEAR;
                continue;
            }
            int index = Palette.IndexOfRgb(r << 16 | g << 8 | b);
            if (a != OPAQUE || index < 0) {
                throw new DataError(filename + ": the pixel at column " +
                        i % w + ", row " + i / w + " is " + (a != OPAQUE ?
                        "partly see-through. A pixel must be solid or " +
                        "wholly see-through." : "the color " +
                        Hex(r << 16 | g << 8 | b) + ", which is not in " +
                        "palette.json. Every color of a picture must be " +
                        "one of the palette's."));
            }
            texels[i] = (byte) index;
        }
        return new Bitmap(w, h, texels);
    }

    private static String Hex(int rgb) {
        String hex = "00000" + Integer.toHexString(rgb).toUpperCase();
        return hex.substring(hex.length() - 6);
    }
}
