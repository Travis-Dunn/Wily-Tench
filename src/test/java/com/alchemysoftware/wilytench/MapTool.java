package com.alchemysoftware.wilytench;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

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
    static final String GAME_MAPS = "src/main/resources/maps";
    private static final String[] MAP_DIRS = {
        GAME_MAPS, "src/test/resources/maps"
    };

    private MapTool() {}

    public static void main(String[] args) throws Exception {
        boolean ok = true;

        for (int i = 0; i < MAP_DIRS.length; i++) ok &= Format(MAP_DIRS[i]);

        MapPicture.Start();

        String[] maps = Content.Maps();
        String[] pictures = new String[maps.length];
        for (int i = 0; i < maps.length; i++) {
            try {
                MapFile file = MapFile.Parse(
                        ReadFile(new File(GAME_MAPS, maps[i])), maps[i]);
                pictures[i] = MapPicture.Save(maps[i], file,
                        World.Build(maps[i], file, true));
                System.out.println("MapTool: drew " + pictures[i]);
            } catch (DataError e) {
                System.err.println("MapTool: " + e.getMessage());
                ok = false;
            }
        }
        RemoveStale(MapPicture.OutDir(), pictures);
        MapPicture.Finish();
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

    private static void RemoveStale(File dir, String[] keep) {
        File[] files = dir.listFiles();

        for (int i = 0; files != null && i < files.length; i++) {
            boolean wanted = !files[i].getName().endsWith(".png");
            for (int k = 0; k < keep.length; k++)
                wanted |= files[i].getName().equals(keep[k]);
            if (!wanted) files[i].delete();
        }
    }

    static String ReadFile(File file) throws IOException {
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

    static void WriteFile(File file, String text)
            throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes("US-ASCII"));
        } finally {
            out.close();
        }
    }
}
