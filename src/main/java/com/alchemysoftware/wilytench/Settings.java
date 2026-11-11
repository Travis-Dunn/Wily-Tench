package com.alchemysoftware.wilytench;

import java.io.File;
import java.util.ArrayList;

import com.alchemysoftware.whitetail.config.ConfigEntry;
import com.alchemysoftware.whitetail.config.ConfigFileParser;
import com.alchemysoftware.whitetail.log.LogLevel;
import com.alchemysoftware.whitetail.log.Logger;

/**
 * The player-editable settings in config.ini, in the data directory.
 * Values are read once at startup; edit the file and restart to change
 * them.
 */
final class Settings {
    static final String KEY_FULLSCREEN = "fullscreen";
    static final String KEY_VSYNC = "vsync";
    static final String KEY_MAX_FPS = "maxFps";
    static final String KEY_WALK_STYLE = "walkStyle";

    /* The values walkStyle may have. */
    static final String WALK_STEADY = "steady";
    static final String WALK_PULSE = "pulse";
    private static final int WALK_STYLE_MAX_LEN = 16;

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

        entries.add(new ConfigEntry(KEY_WALK_STYLE, ConfigEntry.TYPE_STR,
                Integer.valueOf(1), Integer.valueOf(WALK_STYLE_MAX_LEN),
                WALK_STEADY));

        return ConfigFileParser.Init(dataDir, entries);
    }

    /**
     * @return the Mover style the walkStyle setting asks for; anything
     *         but "pulse" is taken as "steady", with a warning in the
     *         session log if it was neither
     */
    static int WalkStyle() {
        String value = ConfigFileParser.GetStr(KEY_WALK_STYLE);

        if (WALK_PULSE.equalsIgnoreCase(value)) return Mover.STYLE_PULSE;
        if (!WALK_STEADY.equalsIgnoreCase(value)) {
            Logger.LogSession(LogLevel.WARNING, "Settings failed to " +
                    "understand walkStyle [" + value + "] because it " +
                    "must be [" + WALK_STEADY + "] or [" + WALK_PULSE +
                    "]; [" + WALK_STEADY + "] is being used.\n");
        }
        return Mover.STYLE_STEADY;
    }
}
