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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import com.alchemysoftware.whitetail.image.Image;
import com.alchemysoftware.whitetail.sprite.SpriteAssetWriter;
import com.alchemysoftware.whitetail.sprite.SpriteAtlas;

/**
 * Development tool, not part of the game: turns the purchased PNG
 * sheets into what the game and its authors use.
 *
 * Run from the repository root with
 * "mvn test-compile exec:java@atlases", or with the "Build Atlases" run
 * configuration, after changing data/sprites.json, data/palette.json,
 * or any source image. Everything it writes is committed:
 *
 * - src/main/resources/sprites/atlasN.wsa: the art itself. Whitetail's
 *   sprite renderer holds at most eight atlases, far fewer than there
 *   are sheets, so the tool packs every sheet, whole, side by side
 *   into as few large atlases as it can. Each pixel becomes a palette
 *   index: fully transparent becomes 0, and every other color must be
 *   in the palette.
 *
 * - src/main/resources/sprites/index.json: where in which atlas each
 *   sheet ended up, which the game reads to find a sheet's cells. It
 *   also records what each sheet was made from, so that the game can
 *   tell when the atlases are out of date.
 *
 * - docs/sprites/ID.png: a catalog of each sheet for people, enlarged,
 *   with a grid, every cell labeled with the column and row that data
 *   files use to refer to it.
 *
 * Because data files name a picture by its own sheet's id and cell,
 * nothing that refers to art changes when the packing does.
 */
public final class AtlasTool {
    private static final int CELL = 16;

    /* Atlases are this many cells wide, and at most as many tall:
       2048 pixels square, 16,384 cells, about 4 MB in memory each. */
    private static final int ATLAS_CELLS = 128;
    private static final int MAX_ATLASES = 8;

    private static final String DATA_DIR = "src/main/resources/data/";
    private static final String OUT_DIR = "src/main/resources/sprites";
    private static final String CATALOG_DIR = "docs/sprites";
    private static final String INDEX_FILE = "index.json";

    private static final int CATALOG_SCALE = 4;
    private static final Color CATALOG_BACKGROUND = new Color(0x3C3C46);
    private static final Color CATALOG_GRID = new Color(0x6E6E82);
    private static final Color CATALOG_LABEL = new Color(0xFFE66E);
    private static final Color CATALOG_LABEL_SHADOW = new Color(0x000000);

    private static final class Job {
        String id, source;
        int x0, y0, cols, rows;
        byte[] texels;      /* cols * CELL wide, rows * CELL tall */
        int atlas, atCol, atRow;
    }

    private AtlasTool() {}

    public static void main(String[] args) throws Exception {
        /* A problem should end the tool with a message, not park it
           behind Whitetail's error dialog. */
        System.setProperty("java.awt.headless", "true");

        Palette.Load(DataNode.Parse(ReadText(DATA_DIR + "palette.json"),
                "palette.json"));
        DataNode[] nodes = DataNode.Parse(
                ReadText(DATA_DIR + "sprites.json"), "sprites.json")
                .Groups("sheets", "sheet");

        Job[] jobs = new Job[nodes.length];
        for (int i = 0; i < jobs.length; i++) jobs[i] = Read(nodes[i]);

        int atlases = Pack(jobs);
        String[] files = new String[atlases];
        for (int a = 0; a < atlases; a++)
            files[a] = WriteAtlas(a, jobs);
        WriteIndex(jobs, files);
        RemoveStale(new File(OUT_DIR), ".wsa", files);

        new File(CATALOG_DIR).mkdirs();
        String[] catalogs = new String[jobs.length];
        for (int i = 0; i < jobs.length; i++) {
            catalogs[i] = jobs[i].id + ".png";
            WriteCatalog(jobs[i]);
        }
        RemoveStale(new File(CATALOG_DIR), ".png", catalogs);

        System.out.println(jobs.length + " sheets packed into " + atlases +
                " atlas" + (atlases == 1 ? "" : "es") + ".");
    }

    /*
     * The part of an image that has anything drawn in it, from its
     * top left corner, rounded out to whole cells. A sheet drawn in
     * the corner of a larger canvas needs no "region" for that reason.
     */
    private static int[] Used(Image img) {
        int right = 0, bottom = 0;

        for (int y = 0; y < img.height; y++) {
            for (int x = 0; x < img.width; x++) {
                if (img.data[(y * img.width + x) * 4 + 3] == 0) continue;
                right = Math.max(right, x + 1);
                bottom = Math.max(bottom, y + 1);
            }
        }
        return new int[] {
            0, 0, Math.min((right + CELL - 1) / CELL * CELL, img.width),
            Math.min((bottom + CELL - 1) / CELL * CELL, img.height)
        };
    }

    /* Loads a sheet's image and turns its pixels into palette indices. */
    private static Job Read(DataNode node) throws Exception {
        Job job = new Job();
        job.id = node.Str("id");
        job.source = node.Str("source");

        Image img = null;
        try {
            img = Bitmap.Decode(Bitmap.ReadAll(
                    new FileInputStream(job.source)), job.source);
        } catch (DataError e) {
            Fail(job.id, e.getMessage());
        }
        if (img == null) Fail(job.id, job.source + " could not be decoded");
        if (img.format != Image.FORMAT_RGBA_8)
            Fail(job.id, job.source + " is not an 8-bit RGBA PNG");

        int[] region = node.OptInts("region", 4);
        if (region == null) region = Used(img);
        int w = region[2], h = region[3];
        job.x0 = region[0];
        job.y0 = region[1];

        if (w <= 0 || h <= 0 || w % CELL != 0 || h % CELL != 0) {
            Fail(job.id, "it is " + w + " by " + h + " pixels, but the " +
                    "width and height must be multiples of " + CELL +
                    "; give it a \"region\" that is");
        }
        if (job.x0 < 0 || job.y0 < 0 || job.x0 + w > img.width ||
                job.y0 + h > img.height)
            Fail(job.id, "its region reaches outside " + job.source);
        if (w > ATLAS_CELLS * CELL || h > ATLAS_CELLS * CELL)
            Fail(job.id, "it is larger than an atlas");

        job.cols = w / CELL;
        job.rows = h / CELL;
        job.texels = new byte[w * h];

        /* Every color the palette lacks, with how many pixels use it,
           so that they can all be dealt with in one go. */
        Map<Integer, Integer> unknown = new TreeMap<Integer, Integer>();
        int partAlpha = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = ((job.y0 + y) * img.width + job.x0 + x) * 4;
                int a = img.data[p + 3] & 0xFF;
                int rgb = (img.data[p] & 0xFF) << 16 |
                        (img.data[p + 1] & 0xFF) << 8 |
                        (img.data[p + 2] & 0xFF);
                int index = 0;

                if (a == 0) continue;
                if (a != 0xFF) {
                    partAlpha++;
                    continue;
                }
                if ((index = Palette.IndexOfRgb(rgb)) < 0) {
                    Integer n = unknown.get(Integer.valueOf(rgb));
                    unknown.put(Integer.valueOf(rgb), Integer.valueOf(
                            n == null ? 1 : n.intValue() + 1));
                    continue;
                }
                job.texels[y * w + x] = (byte) index;
            }
        }

        if (partAlpha > 0) {
            Fail(job.id, partAlpha + " of its pixels are partly " +
                    "transparent; the game can only show a pixel or not");
        }
        if (!unknown.isEmpty()) {
            StringBuffer sb = new StringBuffer("it uses colors that are " +
                    "not in palette.json:");
            for (Iterator<Map.Entry<Integer, Integer>> it =
                    unknown.entrySet().iterator(); it.hasNext();) {
                Map.Entry<Integer, Integer> e = it.next();
                String hex = "00000" + Integer.toHexString(
                        e.getKey().intValue()).toUpperCase();
                sb.append(" ").append(hex.substring(hex.length() - 6))
                        .append(" (").append(e.getValue())
                        .append(" pixels)");
            }
            sb.append(". Add each to \"colors\", or, if it is a stray " +
                    "pixel, to \"corrections\"");
            Fail(job.id, sb.toString());
        }
        return job;
    }

    /*
     * Places every sheet in an atlas. Sheets go tallest first into
     * "shelves": horizontal bands as tall as the first sheet put on
     * them. Each sheet takes the first shelf, in any atlas, that it
     * fits on; failing that a new shelf in the first atlas with room
     * below its last one; failing that a new atlas. Simple, repeatable,
     * and good enough for sheets of similar shapes. Returns the number
     * of atlases used.
     */
    private static int Pack(Job[] jobs) {
        Job[] order = jobs.clone();
        Arrays.sort(order, new Comparator<Job>() {
            public int compare(Job a, Job b) {
                if (a.rows != b.rows) return b.rows - a.rows;
                return a.id.compareTo(b.id);
            }
        });

        /* Per shelf: its atlas, top row, height, and columns taken. */
        int max = order.length;
        int[] shelfAtlas = new int[max], shelfRow = new int[max];
        int[] shelfRows = new int[max], shelfCols = new int[max];
        int[] atlasRows = new int[MAX_ATLASES + 1];
        int shelves = 0, atlases = 0;

        for (int i = 0; i < order.length; i++) {
            Job j = order[i];
            int shelf = -1;

            for (int k = 0; shelf < 0 && k < shelves; k++) {
                if (shelfRows[k] >= j.rows &&
                        shelfCols[k] + j.cols <= ATLAS_CELLS) shelf = k;
            }
            if (shelf < 0) {
                int atlas = 0;
                while (atlas < atlases &&
                        atlasRows[atlas] + j.rows > ATLAS_CELLS) atlas++;
                if (atlas == MAX_ATLASES) {
                    throw new IllegalStateException("AtlasTool: the art " +
                            "needs more than the " + MAX_ATLASES +
                            " atlases the sprite renderer holds. Give " +
                            "large sheets a \"region\" covering only " +
                            "what is used.");
                }
                atlases = Math.max(atlases, atlas + 1);

                shelf = shelves++;
                shelfAtlas[shelf] = atlas;
                shelfRow[shelf] = atlasRows[atlas];
                shelfRows[shelf] = j.rows;
                atlasRows[atlas] += j.rows;
            }

            j.atlas = shelfAtlas[shelf];
            j.atCol = shelfCols[shelf];
            j.atRow = shelfRow[shelf];
            shelfCols[shelf] += j.cols;
        }
        return atlases;
    }

    private static String WriteAtlas(int index, Job[] jobs)
            throws Exception {
        int usedRows = 0, i;
        for (i = 0; i < jobs.length; i++) {
            if (jobs[i].atlas == index)
                usedRows = Math.max(usedRows, jobs[i].atRow + jobs[i].rows);
        }

        int w = ATLAS_CELLS * CELL, h = NextPowerOfTwo(usedRows * CELL);
        byte[] texels = new byte[w * h];

        for (i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            if (j.atlas != index) continue;

            int jw = j.cols * CELL;
            for (int y = 0; y < j.rows * CELL; y++) {
                System.arraycopy(j.texels, y * jw, texels,
                        (j.atRow * CELL + y) * w + j.atCol * CELL, jw);
            }
        }

        SpriteAtlas atlas = SpriteAtlas.Create(w, h, CELL, texels);
        if (atlas == null) {
            throw new IllegalStateException("AtlasTool: atlas " + index +
                    " was rejected by SpriteAtlas");
        }

        String name = "atlas" + index + ".wsa";
        OutputStream os = new FileOutputStream(new File(OUT_DIR, name));
        try {
            if (!SpriteAssetWriter.AtlasToRuntime(os, atlas)) {
                throw new IllegalStateException("AtlasTool: " + name +
                        " could not be written");
            }
        } finally {
            os.close();
        }
        System.out.println(name + ": " + w + " by " + h + " pixels");
        return name;
    }

    /* Sheets are listed in the order sprites.json lists them. */
    private static void WriteIndex(Job[] jobs, String[] files)
            throws Exception {
        StringBuffer sb = new StringBuffer();
        int i;

        sb.append("{\n  \"note\": \"Written by AtlasTool. Do not edit; ")
                .append("change data/sprites.json and rebuild the ")
                .append("atlases.\",\n  \"palette\": \"")
                .append(Palette.Signature()).append("\",\n  \"atlases\": [");
        for (i = 0; i < files.length; i++)
            sb.append(i == 0 ? "" : ", ").append('"').append(files[i])
                    .append('"');
        sb.append("],\n  \"sheets\": [\n");

        for (i = 0; i < jobs.length; i++) {
            Job j = jobs[i];
            sb.append("    { \"id\": \"").append(j.id)
                    .append("\", \"source\": \"").append(j.source)
                    .append("\", \"region\": [").append(j.x0).append(", ")
                    .append(j.y0).append(", ").append(j.cols * CELL)
                    .append(", ").append(j.rows * CELL)
                    .append("], \"atlas\": ").append(j.atlas)
                    .append(", \"at\": [").append(j.atCol).append(", ")
                    .append(j.atRow).append("] }")
                    .append(i + 1 < jobs.length ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");

        Writer out = new OutputStreamWriter(
                new FileOutputStream(new File(OUT_DIR, INDEX_FILE)), "UTF-8");
        try {
            out.write(sb.toString());
        } finally {
            out.close();
        }
    }

    private static void WriteCatalog(Job j) throws Exception {
        int w = j.cols * CELL, h = j.rows * CELL, step = CELL * CATALOG_SCALE;
        BufferedImage img = new BufferedImage(w * CATALOG_SCALE,
                h * CATALOG_SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setColor(CATALOG_BACKGROUND);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = j.texels[y * w + x] & 0xFF;
                if (index == 0) continue;
                g.setColor(new Color(Palette.RgbOf(index)));
                g.fillRect(x * CATALOG_SCALE, y * CATALOG_SCALE,
                        CATALOG_SCALE, CATALOG_SCALE);
            }
        }

        g.setColor(CATALOG_GRID);
        for (int c = 0; c <= j.cols; c++)
            g.drawLine(c * step, 0, c * step, j.rows * step);
        for (int r = 0; r <= j.rows; r++)
            g.drawLine(0, r * step, j.cols * step, r * step);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (int r = 0; r < j.rows; r++) {
            for (int c = 0; c < j.cols; c++) {
                String label = c + "," + r;
                g.setColor(CATALOG_LABEL_SHADOW);
                g.drawString(label, c * step + 3, r * step + 11);
                g.setColor(CATALOG_LABEL);
                g.drawString(label, c * step + 2, r * step + 10);
            }
        }
        g.dispose();
        ImageIO.write(img, "png", new File(CATALOG_DIR, j.id + ".png"));
    }

    /* Deletes files of a kind that this run did not write, left over
       from sheets since renamed or removed. */
    private static void RemoveStale(File dir, String extension,
                                    String[] keep) {
        String[] names = dir.list();
        for (int i = 0; names != null && i < names.length; i++) {
            if (!names[i].endsWith(extension)) continue;
            if (!Arrays.asList(keep).contains(names[i]))
                new File(dir, names[i]).delete();
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

    private static void Fail(String sheet, String why) {
        throw new IllegalStateException("AtlasTool: sheet [" + sheet +
                "]: " + why + ".");
    }
}
