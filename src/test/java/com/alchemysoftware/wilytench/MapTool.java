package com.alchemysoftware.wilytench;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.alchemysoftware.whitetail.error.RunFlag;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * Tidies the map files and draws a picture of each. Run it after
 * changing a map by hand, with the "Build Maps" run configuration or
 *
 *     mvn test-compile exec:java@maps
 *
 * from the project's root folder. It does two things.
 *
 * It rewrites every map file in MAP_DIRS in the canonical form MapFile
 * describes, so that a file reads the same whoever wrote it. The build
 * fails on a map that is not in that form, and this is the cure.
 *
 * It then draws every map of the game into docs/maps as a PNG, with
 * the column and row numbers along the edges and the map file's own
 * symbols written on their tiles. The pictures are drawn by the same
 * code that draws the game, from the same data files, so they show
 * what the game will show. They are for looking over a map without
 * running the game: for an AI that has edited one, which should always
 * look at the picture before calling the work done, and for a person
 * reading a change someone else made.
 *
 * A map with a mistake in it is reported and skipped; the tool then
 * carries on and exits with an error at the end.
 */
public final class MapTool {
    private static final String GAME_MAPS = "src/main/resources/maps";
    private static final String[] MAP_DIRS = {
        GAME_MAPS, "src/test/resources/maps"
    };
    private static final String OUT_DIR = "docs/maps";
    private static final String LOG_DIR = "target/maptool";

    private static final int TILE = Sprites.TILE;
    private static final int SCALE = 3;
    private static final int STEP = TILE * SCALE;
    /* Room along the top and left for the numbers. */
    private static final int MARGIN = 24;

    private static final Color BACKGROUND = new Color(0x3C3C46);
    private static final Color GRID = new Color(0x55FFFFFF, true);
    private static final Color NUMBER = new Color(0xFFE66E);
    private static final Color SYMBOL = new Color(0x7DFFB4);
    private static final Color SHADOW = new Color(0x000000);

    private MapTool() {}

    public static void main(String[] args) throws Exception {
        boolean ok = true;

        for (int i = 0; i < MAP_DIRS.length; i++) ok &= Format(MAP_DIRS[i]);

        File logDir = new File(LOG_DIR);
        logDir.mkdirs();
        Check(Logger.Init(logDir), "the logger would not start");
        Check(ResourceStreamResolver.Init(null,
                ResourceStreamResolver.MODE_CLASSPATH),
                "the game's files could not be reached");
        RunFlag.Start();
        Check(SpriteRenderer.Init(new byte[TILE * TILE], TILE, TILE, 0, 0,
                TILE, TILE, 1), "the sprite renderer would not start");
        Check(Content.Load(), "the data files have a mistake in them; " +
                "it is described in " + LOG_DIR + "/fatal.log");

        new File(OUT_DIR).mkdirs();
        String[] maps = Content.Maps();
        String[] pictures = new String[maps.length];
        for (int i = 0; i < maps.length; i++) {
            pictures[i] = maps[i].replaceAll("\\.txt$", "") + ".png";
            ok &= Render(maps[i], pictures[i]);
        }
        RemoveStale(new File(OUT_DIR), pictures);

        SpriteRenderer.Shutdown();
        Content.Shutdown();
        Logger.Shutdown();
        if (!ok) {
            System.err.println("MapTool: some maps have mistakes; see " +
                    "above.");
            System.exit(1);
        }
    }

    /* Rewrites every map in a folder that is not in canonical form. */
    private static boolean Format(String dir) throws IOException {
        File[] files = new File(dir).listFiles();
        boolean ok = true;

        for (int i = 0; files != null && i < files.length; i++) {
            if (!files[i].getName().endsWith(".txt")) continue;
            String text = ReadFile(files[i]);

            try {
                String tidy = MapFile.Parse(text, files[i].getName())
                        .Write();
                if (tidy.equals(text)) continue;
                WriteFile(files[i], tidy);
                System.out.println("MapTool: tidied " + files[i]);
            } catch (DataError e) {
                System.err.println("MapTool: " + e.getMessage());
                ok = false;
            }
        }
        return ok;
    }

    /* Draws one of the game's maps, as its file on disk now stands. */
    private static boolean Render(String filename, String picture)
            throws IOException {
        MapFile file;
        World world;

        try {
            file = MapFile.Parse(ReadFile(new File(GAME_MAPS, filename)),
                    filename);
            world = World.Build(filename, file);
        } catch (DataError e) {
            System.err.println("MapTool: " + e.getMessage());
            return false;
        }

        int w = world.w * TILE, h = world.h * TILE;
        byte[] buf = new byte[w * h];

        Gfx.Init(buf, w, h);
        Gfx.FillRect(0, 0, w, h, Palette.BACKGROUND);
        for (int y = 0; y < world.h; y++) {
            for (int x = 0; x < world.w; x++) {
                int floor = world.FloorPicture(x, y);
                int object = world.ObjectPicture(x, y, 0);

                if (floor != World.NO_PICTURE) {
                    Sprites.Draw(floor, Sprites.COLOR_MAP_NONE, x * TILE,
                            y * TILE);
                }
                if (object != World.NO_PICTURE) {
                    Sprites.Draw(object, world.ObjectColorMap(x, y),
                            x * TILE, y * TILE);
                }
            }
        }
        Gfx.Shutdown();

        BufferedImage img = new BufferedImage(MARGIN + w * SCALE,
                MARGIN + h * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                /* The palette holds 0xAABBGGRR. */
                int abgr = Palette.PIXELS[buf[y * w + x] & 0xFF];
                g.setColor(new Color(abgr & 0xFF, abgr >> 8 & 0xFF,
                        abgr >> 16 & 0xFF));
                g.fillRect(MARGIN + x * SCALE, MARGIN + y * SCALE, SCALE,
                        SCALE);
            }
        }

        g.setColor(GRID);
        for (int x = 0; x <= world.w; x++) {
            g.drawLine(MARGIN + x * STEP, MARGIN, MARGIN + x * STEP,
                    img.getHeight());
        }
        for (int y = 0; y <= world.h; y++) {
            g.drawLine(MARGIN, MARGIN + y * STEP, img.getWidth(),
                    MARGIN + y * STEP);
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(NUMBER);
        for (int x = 0; x < world.w; x++)
            g.drawString(String.valueOf(x), MARGIN + x * STEP + 4, 15);
        for (int y = 0; y < world.h; y++)
            g.drawString(String.valueOf(y), 3, MARGIN + y * STEP + 28);

        /* The start, and whatever the map file defines for itself,
           with the symbol it is written as. */
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        for (int y = 0; y < file.rows.length; y++) {
            for (int x = 0; x < file.rows[y].length(); x++) {
                char c = file.rows[y].charAt(x);
                if (c != Tiles.START && file.Defined(c) == null) continue;

                String label = String.valueOf(c);
                int lx = MARGIN + x * STEP + 3, ly = MARGIN + y * STEP + 14;
                g.setColor(SHADOW);
                g.drawString(label, lx + 1, ly + 1);
                g.setColor(SYMBOL);
                g.drawString(label, lx, ly);
            }
        }
        g.dispose();

        ImageIO.write(img, "png", new File(OUT_DIR, picture));
        System.out.println("MapTool: drew " + OUT_DIR + "/" + picture);
        return true;
    }

    private static void RemoveStale(File dir, String[] keep) {
        File[] files = dir.listFiles();

        for (int i = 0; files != null && i < files.length; i++) {
            boolean wanted = !files[i].getName().endsWith(".png");
            for (int k = 0; k < keep.length; k++)
                wanted |= files[i].getName().equals(keep[k]);
            if (!wanted) files[i].delete();
        }
    }

    private static String ReadFile(File file) throws IOException {
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] bytes = new byte[(int) file.length()];
            int at = 0, n;
            while (at < bytes.length &&
                    (n = in.read(bytes, at, bytes.length - at)) > 0) at += n;
            return new String(bytes, 0, at, "US-ASCII");
        } finally {
            in.close();
        }
    }

    private static void WriteFile(File file, String text)
            throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes("US-ASCII"));
        } finally {
            out.close();
        }
    }

    private static void Check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("MapTool: " + what + ".");
    }
}
