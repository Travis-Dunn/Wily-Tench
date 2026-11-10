package com.alchemysoftware.wilytench;

import java.io.File;

import com.alchemysoftware.whitetail.os.OsChecker;
import com.alchemysoftware.whitetail.userdir.UserDir;

/**
 * Finds the per-user directory that holds logs, settings, and unpacked
 * native libraries.
 *
 * UserDir does the work everywhere except Windows 95, 98, and ME, which
 * have no APPDATA environment variable. There the shell's own
 * "Application Data" folder under the user's home is used instead.
 *
 * Runs before the logger exists, so failures go to standard error.
 */
final class DataDir {
    static final String VENDOR = "AlchemySoftware";
    static final String APP = "WilyTench";

    private static final String WIN9X_BASE = "Application Data";

    private DataDir() {}

    /**
     * @return the existing, writable data directory, or null after
     *         reporting the failure to standard error
     */
    static File Resolve() {
        if (OsChecker.CURRENT == OsChecker.WINDOWS && !HasAppData())
            return ResolveWin9x();
        return UserDir.Resolve(VENDOR, APP);
    }

    private static boolean HasAppData() {
        try {
            String s = System.getenv("APPDATA");
            return s != null && s.length() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static File ResolveWin9x() {
        try {
            String home = System.getProperty("user.home");
            if (home == null || home.length() == 0) {
                System.err.print(ERR_STR_NO_HOME);
                return null;
            }

            File dir = new File(new File(new File(home, WIN9X_BASE),
                    VENDOR), APP);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                System.err.print(ErrStrCreate(dir));
                return null;
            }
            return dir.getCanonicalFile();
        } catch (Exception e) {
            System.err.print(ERR_STR_EXCP);
            e.printStackTrace();
            return null;
        }
    }

    private static final String CLASS = "DataDir";
    private static final String ERR_STR_NO_HOME = CLASS + " failed to " +
            "resolve because neither the APPDATA environment variable " +
            "nor the user.home system property is set.\n";
    private static final String ERR_STR_EXCP = CLASS + " failed to " +
            "resolve because an exception was encountered.\n";
    private static String ErrStrCreate(File dir) {
        return CLASS + " failed to resolve because the directory [" +
                dir + "] could not be created.\n";
    }
}
