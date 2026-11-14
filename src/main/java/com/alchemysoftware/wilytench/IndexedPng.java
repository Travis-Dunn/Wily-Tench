package com.alchemysoftware.wilytench;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.alchemysoftware.whitetail.image.Image;

/**
 * Reads PNG files of the "indexed color" kind, in which each pixel is
 * a number that picks a color from a list kept in the file.
 *
 * Whitetail's image reader does not read that kind, and it is the
 * kind a drawing program writes for art made in a fixed palette, which
 * all of this game's own art is. So Bitmap brings such files here and
 * leaves every other kind to Whitetail. See C47 in
 * docs/whitetail-feedback.md.
 *
 * This reads what Aseprite writes and no more: 1, 2, 4, or 8 bits to a
 * pixel, not interlaced. The checksums PNG carries are not checked.
 */
final class IndexedPng {
    private static final int SIGNATURE_LEN = 8;
    private static final int COLOR_TYPE_AT = 25;
    private static final int COLOR_TYPE_INDEXED = 3;
    private static final int HEADER_LEN = 13;
    /* Length, name, and checksum, around each chunk's contents. */
    private static final int CHUNK_EXTRA = 12;
    private static final int RGBA = 4;
    private static final int OPAQUE = 255;

    private static final int FILTER_NONE = 0, FILTER_SUB = 1;
    private static final int FILTER_UP = 2, FILTER_AVERAGE = 3;
    private static final int FILTER_PAETH = 4;

    private IndexedPng() {}

    /** @return whether a file's bytes are a PNG of the indexed kind */
    static boolean Is(byte[] file) {
        return file.length > COLOR_TYPE_AT && (file[1] & 0xFF) == 'P' &&
                file[COLOR_TYPE_AT] == COLOR_TYPE_INDEXED;
    }

    /**
     * @param file the whole of a PNG for which Is() is true
     * @param filename what to call it in error messages
     * @return its pixels as red, green, blue, and opacity
     * @throws DataError if the file is damaged or of a sort not read
     */
    static Image Decode(byte[] file, String filename) {
        int w = 0, h = 0, depth = 0, packedLen = 0;
        byte[] colors = null, opacity = new byte[0];
        byte[] packed = new byte[file.length];

        for (int at = SIGNATURE_LEN; at + CHUNK_EXTRA <= file.length; ) {
            int len = Int(file, at), data = at + 8;
            String name = new String(file, at + 4, 4);
            if (len < 0 || data + len > file.length)
                throw Bad(filename, "it is cut short");

            if (name.equals("IHDR") && len == HEADER_LEN) {
                w = Int(file, data);
                h = Int(file, data + 4);
                depth = file[data + 8];
                if (file[data + 12] != 0) {
                    throw Bad(filename, "it is interlaced. Save it " +
                            "without interlacing");
                }
            } else if (name.equals("PLTE")) {
                colors = Slice(file, data, len);
            } else if (name.equals("tRNS")) {
                opacity = Slice(file, data, len);
            } else if (name.equals("IDAT")) {
                System.arraycopy(file, data, packed, packedLen, len);
                packedLen += len;
            }
            at = data + len + 4;
        }
        if (w < 1 || h < 1 || colors == null || depth < 1 || depth > 8)
            throw Bad(filename, "it has no size or no list of colors");

        int rowLen = (w * depth + 7) / 8;
        byte[] raw = Inflate(packed, packedLen, h * (rowLen + 1), filename);
        Unfilter(raw, rowLen, h, filename);

        byte[] out = new byte[w * h * RGBA];
        int mask = (1 << depth) - 1;
        for (int y = 0; y < h; y++) {
            int row = y * (rowLen + 1) + 1;
            for (int x = 0; x < w; x++) {
                int bit = x * depth;
                int index = (raw[row + bit / 8] & 0xFF) >>
                        (8 - depth - bit % 8) & mask;
                int o = (y * w + x) * RGBA;

                if (index * 3 + 2 >= colors.length)
                    throw Bad(filename, "a pixel names a color it lacks");
                out[o] = colors[index * 3];
                out[o + 1] = colors[index * 3 + 1];
                out[o + 2] = colors[index * 3 + 2];
                out[o + 3] = index < opacity.length ? opacity[index] :
                        (byte) OPAQUE;
            }
        }
        return Image.Create(w, h, Image.FORMAT_RGBA_8, out);
    }

    private static byte[] Inflate(byte[] packed, int len, int size,
                                  String filename) {
        Inflater inflater = new Inflater();
        byte[] raw = new byte[size];
        int got = 0;

        try {
            inflater.setInput(packed, 0, len);
            while (got < size && !inflater.finished()) {
                int n = inflater.inflate(raw, got, size - got);
                if (n == 0 && inflater.needsInput()) break;
                got += n;
            }
        } catch (DataFormatException e) {
            throw Bad(filename, "its pixels cannot be unpacked");
        } finally {
            inflater.end();
        }
        if (got != size) throw Bad(filename, "it is cut short");
        return raw;
    }

    /* Undoes PNG's per-row filtering in place. Each row is a filter
       type and then rowLen bytes, and at these depths a pixel's
       neighbor to the left is the byte before. */
    private static void Unfilter(byte[] raw, int rowLen, int h,
                                 String filename) {
        for (int y = 0; y < h; y++) {
            int row = y * (rowLen + 1) + 1, above = row - rowLen - 1;
            int type = raw[row - 1];

            for (int i = 0; i < rowLen; i++) {
                int a = i > 0 ? raw[row + i - 1] & 0xFF : 0;
                int b = y > 0 ? raw[above + i] & 0xFF : 0;
                int c = i > 0 && y > 0 ? raw[above + i - 1] & 0xFF : 0;
                int add;

                if (type == FILTER_NONE) add = 0;
                else if (type == FILTER_SUB) add = a;
                else if (type == FILTER_UP) add = b;
                else if (type == FILTER_AVERAGE) add = (a + b) / 2;
                else if (type == FILTER_PAETH) add = Paeth(a, b, c);
                else throw Bad(filename, "it is damaged");
                raw[row + i] = (byte) (raw[row + i] + add);
            }
        }
    }

    private static int Paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }

    private static int Int(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 |
                (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }

    private static byte[] Slice(byte[] b, int at, int len) {
        byte[] out = new byte[len];
        System.arraycopy(b, at, out, 0, len);
        return out;
    }

    private static DataError Bad(String filename, String why) {
        return new DataError(filename + " could not be read as a " +
                "picture because " + why + ".");
    }
}
