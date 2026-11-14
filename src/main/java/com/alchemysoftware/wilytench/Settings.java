package com.alchemysoftware.wilytench;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import com.alchemysoftware.whitetail.config.ConfigEntry;
import com.alchemysoftware.whitetail.config.ConfigFileParser;
import com.alchemysoftware.whitetail.log.LogLevel;
import com.alchemysoftware.whitetail.log.Logger;

/**
 * The player's settings, kept in config.ini in the data directory.
 *
 * The file is read once, at startup, by Whitetail's config system,
 * which also creates it and checks it. From then on the values live
 * here, where the options screen changes them, and Save() writes them
 * back. The file may also be edited by hand while the game is closed.
 *
 * Fullscreen, the CRT filter, and the video signal are fixed when the
 * window is made, so a change to any takes effect the next time the
 * game starts. The
 * others can be acted on at once by whoever changes them.
 */
final class Settings {
    static final String KEY_FULLSCREEN = "fullscreen";
    static final String KEY_VSYNC = "vsync";
    static final String KEY_MAX_FPS = "maxFps";
    static final String KEY_WALK_STYLE = "walkStyle";
    static final String KEY_CRT = "crt";
    static final String KEY_SIGNAL = "signal";

    /* The values signal may have, in the order of Signal's numbers. */
    private static final String[] SIGNALS = {
        "rgb", "svideo", "composite"
    };
    private static final int SIGNAL_MAX_LEN = 16;

    /* The values walkStyle may have. */
    static final String WALK_STEADY = "steady";
    static final String WALK_PULSE = "pulse";
    private static final int WALK_STYLE_MAX_LEN = 16;

    static final int MAX_FPS_MIN = 10;
    static final int MAX_FPS_MAX = 1000;
    private static final int MAX_FPS_DEFAULT = 100;

    private static final String FILENAME = "config.ini";

    private static File file;
    private static boolean fullscreen, launchedFullscreen, vSync;
    private static boolean crt, launchedCrt;
    private static int signal, launchedSignal;
    private static int maxFps = MAX_FPS_DEFAULT;
    private static int walkStyle = Mover.STYLE_STEADY;

    private Settings() {}

    /**
     * @param dataDir the directory returned by DataDir.Resolve()
     * @return false after a fatal error has been reported
     */
    static boolean Init(File dataDir) {
        ArrayList<ConfigEntry> entries = new ArrayList<ConfigEntry>();

        entries.add(new ConfigEntry(KEY_FULLSCREEN, ConfigEntry.TYPE_BOOL,
                null, null, Boolean.FALSE));
        entries.add(new ConfigEntry(KEY_VSYNC, ConfigEntry.TYPE_BOOL,
                null, null, Boolean.TRUE));
        entries.add(new ConfigEntry(KEY_MAX_FPS, ConfigEntry.TYPE_INT,
                Integer.valueOf(MAX_FPS_MIN), Integer.valueOf(MAX_FPS_MAX),
                Integer.valueOf(MAX_FPS_DEFAULT)));

        entries.add(new ConfigEntry(KEY_WALK_STYLE, ConfigEntry.TYPE_STR,
                Integer.valueOf(1), Integer.valueOf(WALK_STYLE_MAX_LEN),
                WALK_STEADY));

        entries.add(new ConfigEntry(KEY_CRT, ConfigEntry.TYPE_BOOL,
                null, null, Boolean.FALSE));

        entries.add(new ConfigEntry(KEY_SIGNAL, ConfigEntry.TYPE_STR,
                Integer.valueOf(1), Integer.valueOf(SIGNAL_MAX_LEN),
                SIGNALS[Signal.RGB]));

        if (!ConfigFileParser.Init(dataDir, entries)) return false;

        file = new File(dataDir, FILENAME);
        fullscreen = ConfigFileParser.GetBool(KEY_FULLSCREEN);
        launchedFullscreen = fullscreen;
        vSync = ConfigFileParser.GetBool(KEY_VSYNC);
        maxFps = ConfigFileParser.GetInt(KEY_MAX_FPS);
        walkStyle = ReadWalkStyle(ConfigFileParser.GetStr(KEY_WALK_STYLE));
        crt = ConfigFileParser.GetBool(KEY_CRT);
        launchedCrt = crt;
        signal = ReadSignal(ConfigFileParser.GetStr(KEY_SIGNAL));
        launchedSignal = signal;
        return true;
    }

    static boolean Fullscreen() { return fullscreen; }

    /**
     * @return whether the window this run of the game made is a
     *         fullscreen one, whatever the setting says now
     */
    static boolean LaunchedFullscreen() { return launchedFullscreen; }

    static boolean VSync() { return vSync; }

    /** @return the most frames to draw in a second */
    static int MaxFps() { return maxFps; }

    /** @return Mover.STYLE_STEADY or Mover.STYLE_PULSE */
    static int WalkStyle() { return walkStyle; }

    /** @return whether the CRT filter is wanted */
    static boolean Crt() { return crt; }

    /**
     * @return whether the CRT filter was wanted when this run of the
     *         game began, whatever the setting says now
     */
    static boolean LaunchedCrt() { return launchedCrt; }

    static void SetCrt(boolean on) { crt = on; }

    /**
     * @return how the picture is to reach the screen: Signal.RGB,
     *         Signal.SVIDEO, or Signal.COMPOSITE
     */
    static int Signal() { return signal; }

    /** @return the same as it was when this run of the game began */
    static int LaunchedSignal() { return launchedSignal; }

    /** @param mode Signal.RGB, Signal.SVIDEO, or Signal.COMPOSITE */
    static void SetSignal(int mode) { signal = mode; }

    static void SetFullscreen(boolean on) { fullscreen = on; }

    static void SetVSync(boolean on) { vSync = on; }

    /** @param fps brought into the range the file allows */
    static void SetMaxFps(int fps) {
        maxFps = Math.max(MAX_FPS_MIN, Math.min(fps, MAX_FPS_MAX));
    }

    /** @param style Mover.STYLE_STEADY or Mover.STYLE_PULSE */
    static void SetWalkStyle(int style) { walkStyle = style; }

    /**
     * Writes the settings to config.ini. Everything else in the file,
     * such as comments, is kept as it was.
     *
     * @return false if the file could not be written; the reason is in
     *         session.log
     */
    static boolean Save() {
        ArrayList<String> lines = new ArrayList<String>();
        BufferedReader reader = null;
        PrintWriter writer = null;

        try {
            if (file.exists()) {
                reader = new BufferedReader(new FileReader(file));
                for (String line = reader.readLine(); line != null;
                        line = reader.readLine()) lines.add(line);
                reader.close();
                reader = null;
            }

            String[] merged = Merge(lines.toArray(new String[lines.size()]),
                    new String[] {
                        KEY_FULLSCREEN, KEY_VSYNC, KEY_MAX_FPS,
                        KEY_WALK_STYLE, KEY_CRT, KEY_SIGNAL
                    },
                    new String[] {
                        String.valueOf(fullscreen), String.valueOf(vSync),
                        String.valueOf(maxFps),
                        walkStyle == Mover.STYLE_PULSE ?
                        WALK_PULSE : WALK_STEADY,
                        String.valueOf(crt), SIGNALS[signal]
                    });

            writer = new PrintWriter(new FileWriter(file));
            for (int i = 0; i < merged.length; i++) writer.println(merged[i]);
            /* checkError flushes the writer before checking. */
            if (writer.checkError()) throw new IOException(ERR_STR_WRITE);
            return true;
        } catch (IOException e) {
            Logger.LogSessionExcp(LogLevel.WARNING, ErrStrSave(), e);
            return false;
        } catch (SecurityException e) {
            Logger.LogSessionExcp(LogLevel.WARNING, ErrStrSave(), e);
            return false;
        } finally {
            if (writer != null) writer.close();
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                /* Nothing more can be done about a file being read. */
            }
        }
    }

    /**
     * Puts new values into the lines of a settings file. A line that
     * sets one of the keys is rewritten; where a key is set more than
     * once, every such line is. Keys the file does not have are added
     * at the end. All other lines are kept as they are.
     *
     * @param values one for each of keys
     * @return the file's new lines
     */
    static String[] Merge(String[] lines, String[] keys, String[] values) {
        ArrayList<String> out = new ArrayList<String>();
        boolean[] found = new boolean[keys.length];

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i], trimmed = line.trim();
            int equals = trimmed.indexOf('=');

            if (equals > 0 && !trimmed.startsWith("#")) {
                String key = trimmed.substring(0, equals).trim();
                for (int k = 0; k < keys.length; k++) {
                    if (!keys[k].equals(key)) continue;
                    line = keys[k] + "=" + values[k];
                    found[k] = true;
                }
            }
            out.add(line);
        }
        for (int k = 0; k < keys.length; k++)
            if (!found[k]) out.add(keys[k] + "=" + values[k]);
        return out.toArray(new String[out.size()]);
    }

    /*
     * Anything but "pulse" is taken as "steady", with a warning in
     * the session log if it was neither.
     */
    private static int ReadWalkStyle(String value) {
        if (WALK_PULSE.equalsIgnoreCase(value)) return Mover.STYLE_PULSE;
        if (!WALK_STEADY.equalsIgnoreCase(value)) {
            Logger.LogSession(LogLevel.WARNING, "Settings failed to " +
                    "understand walkStyle [" + value + "] because it " +
                    "must be [" + WALK_STEADY + "] or [" + WALK_PULSE +
                    "]; [" + WALK_STEADY + "] is being used.\n");
        }
        return Mover.STYLE_STEADY;
    }

    private static int ReadSignal(String value) {
        for (int i = 0; i < SIGNALS.length; i++)
            if (SIGNALS[i].equalsIgnoreCase(value)) return i;

        Logger.LogSession(LogLevel.WARNING, "Settings failed to " +
                "understand signal [" + value + "] because it must be [" +
                SIGNALS[Signal.RGB] + "], [" + SIGNALS[Signal.SVIDEO] +
                "], or [" + SIGNALS[Signal.COMPOSITE] + "]; [" +
                SIGNALS[Signal.RGB] + "] is being used.\n");
        return Signal.RGB;
    }

    private static final String ERR_STR_WRITE = "the write did not finish";
    private static String ErrStrSave() {
        return "Settings failed to save [" + file + "] because an " +
                "exception was encountered.\n";
    }
}
