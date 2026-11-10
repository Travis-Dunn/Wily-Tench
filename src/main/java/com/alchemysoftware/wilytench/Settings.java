package com.alchemysoftware.wilytench;

import java.io.File;
import java.util.ArrayList;

import com.alchemysoftware.whitetail.config.ConfigEntry;
import com.alchemysoftware.whitetail.config.ConfigFileParser;

/**
 * The player-editable settings in config.ini, in the data directory.
 * Values are read once at startup; edit the file and restart to change
 * them.
 */
final class Settings {
    static final String KEY_FULLSCREEN = "fullscreen";
    static final String KEY_VSYNC = "vsync";
    static final String KEY_MAX_FPS = "maxFps";

    private static final int MAX_FPS_MIN = 10;
    private static final int MAX_FPS_MAX = 1000;
    private static final int MAX_FPS_DEFAULT = 100;

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

        return ConfigFileParser.Init(dataDir, entries);
    }
}
