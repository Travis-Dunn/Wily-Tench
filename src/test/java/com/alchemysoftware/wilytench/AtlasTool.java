package com.alchemysoftware.wilytench;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.alchemysoftware.whitetail.image.Image;
import com.alchemysoftware.whitetail.image.ImageBuilder;
import com.alchemysoftware.whitetail.sprite.SpriteAssetWriter;
import com.alchemysoftware.whitetail.sprite.SpriteAtlas;

/**
 * Development tool, not part of the game: converts regions of the
 * purchased PNG sheets into the indexed .wsa atlases the game loads.
 *
 * Run from the repository root with
 * "mvn test-compile exec:java@atlases", or with the "Build Atlases" run
 * configuration. The output in src/main/resources/sprites is committed,
 * so this only needs running when a sheet or the JOBS table changes.
 *
 * Each pixel becomes a palette index: fully transparent becomes 0 and
 * every other color must be one of Palette's art colors. The region is
 * placed at the top left of the smallest power-of-two canvas that holds
 * it, because that is the only atlas shape Whitetail accepts. The cell
 * at column c, row r of the region is therefore frame
 * r * (canvas width / 16) + c.
 */
public final class AtlasTool {
    private static final int CELL = 16;
    private static final String OUT_DIR = "src/main/resources/sprites";
    private static final String ART_DIR =
            "DD-core/Demonic-Dungeon-16x16/No-Background/";

    /* { source sheet, output name, x, y, width, height } in pixels. */
    private static final Object[][] JOBS = {
        { "Cave.png", "cave.wsa", 0, 0, 160, 304 },
        /* The first hero's eleven rows; the other three columns of the
           sheet are the same poses in other clothes. */
        { "Heroes.png", "heroes.wsa", 0, 0, 64, 176 },
    };

    private AtlasTool() {}

    public static void main(String[] args) throws Exception {
        /* A conversion problem should end the tool with a message, not
           park it behind Whitetail's error dialog. */
        System.setProperty("java.awt.headless", "true");

        for (int i = 0; i < JOBS.length; i++) {
            Object[] j = JOBS[i];
            Convert((String) j[0], (String) j[1],
                    ((Integer) j[2]).intValue(), ((Integer) j[3]).intValue(),
                    ((Integer) j[4]).intValue(), ((Integer) j[5]).intValue());
        }
    }

    private static void Convert(String sheet, String outName, int x0, int y0,
                                int w, int h) throws Exception {
        Image img;
        InputStream in = new FileInputStream(ART_DIR + sheet);
        try {
            img = ImageBuilder.FromPNG(in);
        } finally {
            in.close();
        }
        if (img == null) Fail(sheet + " could not be decoded");
        if (img.format != Image.FORMAT_RGBA_8)
            Fail(sheet + " is not an 8-bit RGBA PNG");
        if (x0 + w > img.width || y0 + h > img.height)
            Fail(sheet + " is smaller than the requested region");

        int canvasW = NextPowerOfTwo(w), canvasH = NextPowerOfTwo(h);
        byte[] texels = new byte[canvasW * canvasH];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = ((y0 + y) * img.width + x0 + x) * 4;
                int r = img.data[p] & 0xFF, g = img.data[p + 1] & 0xFF;
                int b = img.data[p + 2] & 0xFF, a = img.data[p + 3] & 0xFF;
                int index;

                if (a == 0) {
                    index = 0;
                } else {
                    index = Palette.IndexOfArtRgb((r << 16) | (g << 8) | b);
                    if (a != 0xFF || index < 0)
                        Fail(sheet + " pixel (" + (x0 + x) + ", " +
                                (y0 + y) + ") is not transparent and not " +
                                "an opaque palette color");
                }
                texels[y * canvasW + x] = (byte) index;
            }
        }

        SpriteAtlas atlas = SpriteAtlas.Create(canvasW, canvasH, CELL,
                texels);
        if (atlas == null) Fail(outName + " was rejected by SpriteAtlas");

        File out = new File(OUT_DIR, outName);
        OutputStream os = new FileOutputStream(out);
        try {
            if (!SpriteAssetWriter.AtlasToRuntime(os, atlas))
                Fail(outName + " could not be written");
        } finally {
            os.close();
        }

        System.out.println(sheet + " -> " + out + " (" + canvasW + "x" +
                canvasH + ", " + (canvasW / CELL) + " frames per row)");
    }

    private static int NextPowerOfTwo(int n) {
        int p = CELL;
        while (p < n) p <<= 1;
        return p;
    }

    private static void Fail(String why) {
        throw new IllegalStateException("AtlasTool: " + why);
    }
}
