package com.alchemysoftware.wilytench;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import com.alchemysoftware.whitetail.log.LogLevel;
import com.alchemysoftware.whitetail.log.Logger;

/**
 * The numbers that decide how the CRT filter and the video signal
 * look: every one of them a knob, with a name, a range, a step, and
 * the setting it starts at.
 *
 * The knobs can be turned while the game runs, from TuningPanel, and
 * whatever is turned shows at once. Settings that differ from the
 * defaults are kept in tuning.ini in the data directory, one
 * "key=value" to a line, and read back the next time. Deleting the
 * file puts everything back. The file is also how a look that has
 * been arrived at by eye is handed on: its numbers become the
 * defaults here.
 *
 * Crt gives its knobs to the shader every frame. Signal gives its own
 * to Ntsc whenever Version() has moved on.
 *
 * Before Init(), and in the tests, every knob is at its default.
 */
final class Tuning {
    /* Which part of the picture's journey a knob belongs to, and so
       when it is worth showing. */
    static final int GROUP_CRT = 0, GROUP_SIGNAL = 1;
    static final int GROUP_COMPOSITE = 2, GROUP_SVIDEO = 3;

    static final String[] GROUP_NAMES = {
        "CRT filter", "Video signal", "Composite", "S-Video"
    };

    /* The CRT filter's knobs. shaders/crt.frag says what each does. */
    static final int SCAN_WIDTH = 0, SCAN_DEPTH = 1, MASK_DEPTH = 2;
    static final int MASK_PITCH = 3, STRIPE_WIDTH = 4, CELL_LIT = 5;
    static final int CELL_STAGGER = 6, GLOW = 7, GLOW_SPREAD = 8;
    static final int BLOOM = 9, BEAM_BLUR = 10;

    /* The video signal's, whichever cable. Ntsc says what each does. */
    static final int NOISE = 11, FLIP = 12, LUMA_BAND = 13;
    static final int COLOR_BAND_I = 14, COLOR_BAND_Q = 15, HUE = 16;

    /* The set's, one of each for each cable. */
    static final int COMB = 17, SHARP = 18, SATURATION = 19;
    static final int CONTRAST = 20, BRIGHTNESS = 21;
    static final int SHARP_2 = 22, SATURATION_2 = 23, CONTRAST_2 = 24;
    static final int BRIGHTNESS_2 = 25;

    static final int COUNT = 26;

    private static final String FILENAME = "tuning.ini";

    /* Steps are counted in whole numbers, so that turning a knob up
       and down again lands exactly where it was. A knob's value is
       its count of steps times its step. */
    private static final String[] KEYS = new String[COUNT];
    private static final String[] NAMES = new String[COUNT];
    private static final int[] GROUPS = new int[COUNT];
    private static final double[] STEPS = new double[COUNT];
    private static final int[] LEAST = new int[COUNT];
    private static final int[] MOST = new int[COUNT];
    private static final int[] DEFAULTS = new int[COUNT];
    private static final int[] counts = new int[COUNT];

    private static File file;
    private static int version;

    static {
        /*   knob, group, key, name, step, least, most, default: the
             last three in the knob's own units. */
        Knob(SCAN_WIDTH, GROUP_CRT, "crt.scanWidth",
                "Scanline width", 0.01, 0.10, 0.60, 0.30);
        Knob(SCAN_DEPTH, GROUP_CRT, "crt.scanDepth",
                "Scanline depth", 0.02, 0.0, 1.0, 0.60);
        Knob(MASK_DEPTH, GROUP_CRT, "crt.maskDepth",
                "Mask depth", 0.02, 0.0, 1.0, 0.30);
        Knob(MASK_PITCH, GROUP_CRT, "crt.maskPitch",
                "Mask triads per pixel", 0.05, 0.25, 3.0, 1.0);
        Knob(STRIPE_WIDTH, GROUP_CRT, "crt.stripeWidth",
                "Mask stripe width", 0.01, 0.05, 0.33, 0.20);
        Knob(CELL_LIT, GROUP_CRT, "crt.cellLit",
                "Mask cell height", 0.01, 0.50, 1.0, 0.83);
        Knob(CELL_STAGGER, GROUP_CRT, "crt.cellStagger",
                "Mask stagger", 0.05, 0.0, 0.5, 0.5);
        Knob(GLOW, GROUP_CRT, "crt.glow",
                "Glow (spread)", 0.02, 0.0, 0.9, 0.22);
        Knob(GLOW_SPREAD, GROUP_CRT, "crt.glowSpread",
                "Glow reach", 0.05, 0.3, 1.5, 0.75);
        Knob(BLOOM, GROUP_CRT, "crt.bloom",
                "Bloom (added)", 0.02, 0.0, 2.0, 0.0);
        Knob(BEAM_BLUR, GROUP_CRT, "crt.beamBlur",
                "Beam blur", 0.1, 0.5, 6.0, 1.5);

        Knob(NOISE, GROUP_SIGNAL, "signal.noise",
                "Noise", 1.0, 0.0, 100.0, 0.0);
        Knob(FLIP, GROUP_SIGNAL, "signal.flip",
                "Carrier flips each field", 1.0, 0.0, 1.0, 1.0);
        Knob(LUMA_BAND, GROUP_SIGNAL, "signal.lumaBand",
                "Brightness band, MHz", 0.1, 0.5, 6.0, 4.2);
        Knob(COLOR_BAND_I, GROUP_SIGNAL, "signal.colorBandI",
                "Color band I, MHz", 0.05, 0.2, 3.0, 1.5);
        Knob(COLOR_BAND_Q, GROUP_SIGNAL, "signal.colorBandQ",
                "Color band Q, MHz", 0.05, 0.2, 3.0, 1.5);
        Knob(HUE, GROUP_SIGNAL, "signal.hue",
                "Hue", 1.0, -45.0, 45.0, 0.0);

        Knob(COMB, GROUP_COMPOSITE, "composite.comb",
                "Comb filter", 8.0, 0.0, 256.0, 176.0);
        Knob(SHARP, GROUP_COMPOSITE, "composite.sharp",
                "Sharpness", 8.0, 0.0, 256.0, 0.0);
        Knob(SATURATION, GROUP_COMPOSITE, "composite.saturation",
                "Saturation", 1.0, 0.0, 40.0, 16.0);
        Knob(CONTRAST, GROUP_COMPOSITE, "composite.contrast",
                "Contrast", 2.0, 100.0, 256.0, 170.0);
        Knob(BRIGHTNESS, GROUP_COMPOSITE, "composite.brightness",
                "Brightness", 1.0, -30.0, 30.0, 0.0);

        Knob(SHARP_2, GROUP_SVIDEO, "svideo.sharp",
                "Sharpness", 8.0, 0.0, 256.0, 0.0);
        Knob(SATURATION_2, GROUP_SVIDEO, "svideo.saturation",
                "Saturation", 1.0, 0.0, 40.0, 15.0);
        Knob(CONTRAST_2, GROUP_SVIDEO, "svideo.contrast",
                "Contrast", 2.0, 100.0, 256.0, 162.0);
        Knob(BRIGHTNESS_2, GROUP_SVIDEO, "svideo.brightness",
                "Brightness", 1.0, -30.0, 30.0, 2.0);
    }

    private Tuning() {}

    /**
     * Reads tuning.ini, if there is one. A line that cannot be
     * understood is passed over, with a warning in session.log.
     *
     * @param dataDir the directory returned by DataDir.Resolve()
     */
    static void Init(File dataDir) {
        BufferedReader reader = null;

        file = new File(dataDir, FILENAME);
        ResetAll();
        if (!file.exists()) return;

        try {
            reader = new BufferedReader(new FileReader(file));
            for (String line = reader.readLine(); line != null;
                    line = reader.readLine()) Read(line.trim());
        } catch (IOException e) {
            Logger.LogSessionExcp(LogLevel.WARNING, ErrStrFile("read"), e);
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                /* Nothing more can be done about a file being read. */
            }
        }
    }

    /**
     * Writes the knobs that are not at their defaults to tuning.ini,
     * or removes the file if none are.
     */
    static void Save() {
        PrintWriter writer = null;
        boolean any = false;

        if (file == null) return;
        for (int knob = 0; knob < COUNT; knob++)
            any |= counts[knob] != DEFAULTS[knob];

        try {
            if (!any) {
                if (file.exists() && !file.delete())
                    throw new IOException("the file would not go");
                return;
            }
            writer = new PrintWriter(new FileWriter(file));
            for (int knob = 0; knob < COUNT; knob++) {
                if (counts[knob] != DEFAULTS[knob])
                    writer.println(KEYS[knob] + "=" + Text(knob));
            }
            if (writer.checkError())
                throw new IOException("the write did not finish");
        } catch (IOException e) {
            Logger.LogSessionExcp(LogLevel.WARNING, ErrStrFile("save"), e);
        } catch (SecurityException e) {
            Logger.LogSessionExcp(LogLevel.WARNING, ErrStrFile("save"), e);
        } finally {
            if (writer != null) writer.close();
        }
    }

    /** @return a knob's setting */
    static double Get(int knob) { return counts[knob] * STEPS[knob]; }

    /** @return a knob's setting, for one that counts in whole numbers */
    static int Int(int knob) { return (int) Math.round(Get(knob)); }

    /**
     * Turns a knob by so many of its steps, as far as it goes.
     *
     * @param steps how many, up if positive and down if negative
     */
    static void Turn(int knob, int steps) {
        Set(knob, Math.max(LEAST[knob],
                Math.min(counts[knob] + steps, MOST[knob])));
    }

    /** Puts a knob back to the setting it starts at. */
    static void Reset(int knob) { Set(knob, DEFAULTS[knob]); }

    /** Puts every knob back. */
    static void ResetAll() {
        for (int knob = 0; knob < COUNT; knob++) Reset(knob);
    }

    /** @return whether a knob is at the setting it starts at */
    static boolean AtDefault(int knob) {
        return counts[knob] == DEFAULTS[knob];
    }

    /**
     * @return a number that goes up whenever any knob is turned, for
     *         noticing that one has been
     */
    static int Version() { return version; }

    static String Name(int knob) { return NAMES[knob]; }

    /** @return which of the GROUP_ values a knob belongs to */
    static int Group(int knob) { return GROUPS[knob]; }

    /** @return a knob's setting as it is written, in the file too */
    static String Text(int knob) {
        double step = STEPS[knob];
        int places = step >= 1.0 ? 0 : step >= 0.1 ? 1 : 2;
        long scaled = Math.round(Get(knob) * (places == 2 ? 100 :
                places == 1 ? 10 : 1));
        String digits = String.valueOf(Math.abs(scaled));

        if (places == 0) return String.valueOf(scaled);
        while (digits.length() <= places) digits = "0" + digits;
        return (scaled < 0 ? "-" : "") +
                digits.substring(0, digits.length() - places) + "." +
                digits.substring(digits.length() - places);
    }

    private static void Set(int knob, int count) {
        if (counts[knob] == count) return;
        counts[knob] = count;
        version++;
    }

    private static void Knob(int knob, int group, String key, String name,
                             double step, double least, double most,
                             double start) {
        KEYS[knob] = key;
        NAMES[knob] = name;
        GROUPS[knob] = group;
        STEPS[knob] = step;
        LEAST[knob] = (int) Math.round(least / step);
        MOST[knob] = (int) Math.round(most / step);
        DEFAULTS[knob] = counts[knob] = (int) Math.round(start / step);
    }

    /* Takes one line of the file. */
    private static void Read(String line) {
        int equals = line.indexOf('=');

        if (line.length() == 0 || line.charAt(0) == '#') return;
        if (equals > 0) {
            String key = line.substring(0, equals).trim();

            for (int knob = 0; knob < COUNT; knob++) {
                if (!KEYS[knob].equals(key)) continue;
                try {
                    double value = Double.parseDouble(
                            line.substring(equals + 1).trim());
                    int count = (int) Math.round(value / STEPS[knob]);

                    Set(knob, Math.max(LEAST[knob],
                            Math.min(count, MOST[knob])));
                    return;
                } catch (NumberFormatException e) {
                    break;
                }
            }
        }
        Logger.LogSession(LogLevel.WARNING, "Tuning failed to " +
                "understand the line [" + line + "] of [" + FILENAME +
                "] because it is not one of its knobs set to a " +
                "number; the line is being passed over.\n");
    }

    private static String ErrStrFile(String what) {
        return "Tuning failed to " + what + " [" + file + "] because an " +
                "exception was encountered.\n";
    }
}
