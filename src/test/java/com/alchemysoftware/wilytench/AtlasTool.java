package com.alchemysoftware.wilytench;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import javax.imageio.ImageIO;

import com.alchemysoftware.whitetail.image.Image;
import com.alchemysoftware.whitetail.image.ImageBuilder;
import com.alchemysoftware.whitetail.sprite.SpriteAssetWriter;
import com.alchemysoftware.whitetail.sprite.SpriteAtlas;

/**
 * Development tool, not part of the game: turns the purchased PNG
 * sheets into what the game and its authors use.
 *
 * Run from the repository root with
 * "mvn test-compile exec:java@atlases", or with the "Build Atlases" run
 * configuration, after adding a sheet to
 * src/main/resources/data/sprites.json or changing one's source or
 * region there. For every sheet listed it writes two files, both of
 * which are committed:
 *
 * - src/main/resources/sprites/NAME.wsa, the indexed atlas the game
 *   loads. Each pixel becomes a palette index: fully transparent
 *   becomes 0 and every other color must be one of Palette's art
 *   colors. The region is placed at the top left of the smallest
 *   power-of-two canvas that holds it, because that is the only atlas
 *   shape Whitetail accepts.
 *
 * - docs/sprites/ID.png, a catalog for people: the sheet enlarged,
 *   with a grid, and every cell labeled with the column and row that
 *   data files use to refer to it.
 */
public final class AtlasTool {
    private static final int CELL = 16;
    private static final String DATA_FILE =
            "src/main/resources/data/sprites.json";
    private static final String OUT_DIR = "src/main/resources/sprites";
    private static final String CATALOG_DIR = "docs/sprites";

    private static final int CATALOG_SCALE = 4;
    private static final Color CATALOG_BACKGROUND = new Color(0x3C3C46);
    private static final Color CATALOG_GRID = new Color(0x6E6E82);
    private static final Color CATALOG_LABEL = new Color(0xFFE66E);
    private static final Color CATALOG_LABEL_SHADOW = new Color(0x000000);

    private AtlasTool() {}

    public static void main(String[] args) throws Exception {
        /* A problem should end the tool with a message, not park it
           behind Whitetail's error dialog. */
        System.setProperty("java.awt.headless", "true");

        DataNode[] sheets = DataNode.Parse(ReadText(DATA_FILE),
                "sprites.json").Groups("sheets", "sheet");
        new File(CATALOG_DIR).mkdirs();

        for (int i = 0; i < sheets.length; i++) {
            int[] region = sheets[i].Ints("region", 4);
            Convert(sheets[i].Str("id"), sheets[i].Str("source"),
                    sheets[i].Str("file"), region[0], region[1], region[2],
                    region[3]);
        }
    }

    private static void Convert(String id, String source, String outName,
                                int x0, int y0, int w, int h)
            throws Exception {
        Image img;
        InputStream in = new FileInputStream(source);
        try {
            img = ImageBuilder.FromPNG(in);
        } finally {
            in.close();
        }
        if (img == null) Fail(source + " could not be decoded");
        if (img.format != Image.FORMAT_RGBA_8)
            Fail(source + " is not an 8-bit RGBA PNG");
        if (w % CELL != 0 || h % CELL != 0)
            Fail(id + ": the region's width and height must be " +
                    "multiples of " + CELL);
        if (x0 + w > img.width || y0 + h > img.height)
            Fail(source + " is smaller than the region asked for");

        int canvasW = NextPowerOfTwo(w), canvasH = NextPowerOfTwo(h);
        byte[] texels = new byte[canvasW * canvasH];
        BufferedImage catalog = new BufferedImage(w * CATALOG_SCALE,
                h * CATALOG_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = catalog.createGraphics();
        g.setColor(CATALOG_BACKGROUND);
        g.fillRect(0, 0, catalog.getWidth(), catalog.getHeight());

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = ((y0 + y) * img.width + x0 + x) * 4;
                int r = img.data[p] & 0xFF, gr = img.data[p + 1] & 0xFF;
                int b = img.data[p + 2] & 0xFF, a = img.data[p + 3] & 0xFF;
                int rgb = (r << 16) | (gr << 8) | b, index = 0;

                if (a != 0) {
                    index = Palette.IndexOfArtRgb(rgb);
                    if (a != 0xFF || index < 0)
                        Fail(source + " pixel (" + (x0 + x) + ", " +
                                (y0 + y) + ") is not transparent and not " +
                                "an opaque palette color");
                    g.setColor(new Color(rgb));
                    g.fillRect(x * CATALOG_SCALE, y * CATALOG_SCALE,
                            CATALOG_SCALE, CATALOG_SCALE);
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

        LabelCatalog(g, w / CELL, h / CELL);
        g.dispose();
        File catalogFile = new File(CATALOG_DIR, id + ".png");
        ImageIO.write(catalog, "png", catalogFile);

        System.out.println(source + " -> " + out + " and " + catalogFile +
                " (" + (w / CELL) + " columns, " + (h / CELL) + " rows)");
    }

    private static void LabelCatalog(Graphics2D g, int cols, int rows) {
        int step = CELL * CATALOG_SCALE;

        g.setColor(CATALOG_GRID);
        for (int c = 0; c <= cols; c++)
            g.drawLine(c * step, 0, c * step, rows * step);
        for (int r = 0; r <= rows; r++)
            g.drawLine(0, r * step, cols * step, r * step);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                String label = c + "," + r;
                g.setColor(CATALOG_LABEL_SHADOW);
                g.drawString(label, c * step + 3, r * step + 11);
                g.setColor(CATALOG_LABEL);
                g.drawString(label, c * step + 2, r * step + 10);
            }
        }
    }

    private static String ReadText(String path) throws Exception {
        InputStream in = new FileInputStream(path);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) != -1) bytes.write(chunk, 0, n);
            return bytes.toString("UTF-8");
        } finally {
            in.close();
        }
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
