package com.alchemysoftware.wilytench;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.alchemysoftware.whitetail.error.RunFlag;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;

/**
 * Pictures of maps for the map tools, drawn by the game's own code
 * from the game's own data, so that MapTool's pictures and the map
 * editor both show what the game will.
 *
 * Start() loads the game's data with no window, as the tests do.
 * Draw() turns a map into an image, one image pixel to one game pixel.
 * Save() writes the labeled picture kept in docs/maps.
 */
final class MapPicture {
    static final int TILE = Sprites.TILE;

    private static final String LOG_DIR = "target/maptool";
    private static final String OUT_DIR = "docs/maps";

    private static final int SCALE = 3;
    private static final int STEP = TILE * SCALE;
    /* Room along the top and left for the numbers. */
    private static final int MARGIN = 24;

    private static final Color BACKGROUND = new Color(0x3C3C46);
    private static final Color GRID = new Color(0x55FFFFFF, true);
    private static final Color NUMBER = new Color(0xFFE66E);
    private static final Color SYMBOL = new Color(0x7DFFB4);
    private static final Color SHADOW = new Color(0x000000);

    private MapPicture() {}

    /**
     * Loads the game's data files. Call once, from the project's root
     * folder, before anything else here.
     *
     * @throws IllegalStateException saying what went wrong
     */
    static void Start() {
        File logDir = new File(LOG_DIR);
        logDir.mkdirs();
        Check(Logger.Init(logDir), "the logger would not start");
        Check(ResourceStreamResolver.Init(null,
                ResourceStreamResolver.MODE_CLASSPATH),
                "the game's files could not be reached");
        RunFlag.Start();
        Check(SpriteRenderer.Init(new byte[TILE * TILE], TILE, TILE, 0, 0,
                TILE, TILE, 1), "the sprite renderer would not start");
        Check(Content.Load(), "the data files have a mistake in them, " +
                "which is described in " + LOG_DIR + "/fatal.log");
    }

    static void Finish() {
        SpriteRenderer.Shutdown();
        Content.Shutdown();
        Logger.Shutdown();
    }

    /** @return the map as the game would draw it, at its real size */
    static BufferedImage Draw(World world) {
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
        return ToImage(buf, w, h, false);
    }

    /**
     * @return one sprite as a small image, see-through where the
     *         sprite is; for showing in a list
     */
    static BufferedImage Icon(int picture, int colorMap) {
        byte[] buf = new byte[TILE * TILE];

        Gfx.Init(buf, TILE, TILE);
        Gfx.FillRect(0, 0, TILE, TILE, Palette.BACKGROUND);
        if (picture != World.NO_PICTURE)
            Sprites.Draw(picture, colorMap, 0, 0);
        Gfx.Shutdown();
        return ToImage(buf, TILE, TILE, true);
    }

    /**
     * Writes the labeled picture of one of the game's maps to
     * docs/maps: the map three times life size, column and row numbers
     * along the edges, and the start and each symbol the map file
     * defines written on its tile.
     *
     * @param filename the map file's name, such as "cave.txt"
     * @return the picture's file name
     */
    static String Save(String filename, MapFile file, World world)
            throws IOException {
        BufferedImage map = Draw(world);
        BufferedImage img = new BufferedImage(
                MARGIN + map.getWidth() * SCALE,
                MARGIN + map.getHeight() * SCALE,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setColor(BACKGROUND);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(map, MARGIN, MARGIN, map.getWidth() * SCALE,
                map.getHeight() * SCALE, null);

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

        String picture = filename.replaceAll("\\.txt$", "") + ".png";
        new File(OUT_DIR).mkdirs();
        ImageIO.write(img, "png", new File(OUT_DIR, picture));
        return picture;
    }

    /** @return the folder Save() writes to */
    static File OutDir() { return new File(OUT_DIR); }

    private static BufferedImage ToImage(byte[] buf, int w, int h,
                                         boolean clearBackground) {
        BufferedImage img = new BufferedImage(w, h,
                BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = buf[y * w + x] & 0xFF;
                /* The palette holds 0xAABBGGRR. */
                int abgr = Palette.PIXELS[index];
                int rgb = (abgr & 0xFF) << 16 | (abgr & 0xFF00) |
                        (abgr >> 16 & 0xFF);
                img.setRGB(x, y, clearBackground &&
                        index == Palette.BACKGROUND ? 0 : 0xFF000000 | rgb);
            }
        }
        return img;
    }

    private static void Check(boolean ok, String what) {
        if (!ok) throw new IllegalStateException("The map tools could " +
                "not start because " + what + ".");
    }
}
