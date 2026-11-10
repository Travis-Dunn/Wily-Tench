package com.alchemysoftware.wilytench;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.log.LogLevel;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.os.OsChecker;

/**
 * Unpacks LWJGL's native libraries from the classpath into the data
 * directory and points LWJGL at them, so the game starts from an IDE or
 * a plain "java -jar" with no java.library.path argument.
 *
 * Must run before the first LWJGL class is touched. Both the 32-bit and
 * the 64-bit library are unpacked; LWJGL picks the one the JVM can load.
 */
final class Natives {
    private static final String LIBRARY_PATH_PROP = "org.lwjgl.librarypath";
    private static final String SUBDIR = "natives";

    private static final String[] WINDOWS_LIBS = {
        "lwjgl.dll", "lwjgl64.dll", "OpenAL32.dll", "OpenAL64.dll"
    };
    private static final String[] LINUX_LIBS = {
        "liblwjgl.so", "liblwjgl64.so", "libopenal.so", "libopenal64.so"
    };
    private static final String[] MAC_LIBS = {
        "liblwjgl.dylib", "openal.dylib"
    };

    private static final int COPY_BUF_LEN = 8192;

    private Natives() {}

    /**
     * @param dataDir the directory returned by DataDir.Resolve()
     * @return false after reporting a fatal error
     */
    static boolean Install(File dataDir) {
        String[] libs;
        switch (OsChecker.CURRENT) {
            case OsChecker.WINDOWS: libs = WINDOWS_LIBS; break;
            case OsChecker.LINUX:   libs = LINUX_LIBS;   break;
            case OsChecker.MAC:     libs = MAC_LIBS;     break;
            default:
                ErrorHandler.LogFatalAndStop(ErrStrUnknownOs());
                return false;
        }

        File dir = new File(dataDir, SUBDIR);
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                ErrorHandler.LogFatalAndStop(ErrStrMkdir(dir));
                return false;
            }
            for (int i = 0; i < libs.length; i++) {
                if (!Unpack(libs[i], dir)) return false;
            }
            System.setProperty(LIBRARY_PATH_PROP, dir.getAbsolutePath());
        } catch (IOException e) {
            ErrorHandler.LogFatalExcpAndStop(ErrStrIo(dir), e);
            return false;
        } catch (RuntimeException e) {
            ErrorHandler.LogFatalExcpAndStop(ErrStrIo(dir), e);
            return false;
        }

        Logger.LogSession(LogLevel.DEBUG, LogStrInstalled(dir));
        return true;
    }

    /* A library of the right length is left alone: rewriting it every
       launch is wasted work, and fails on Windows while another copy of
       the game has it loaded. */
    private static boolean Unpack(String name, File dir) throws IOException {
        InputStream in = Natives.class.getResourceAsStream("/" + name);
        if (in == null) {
            ErrorHandler.LogFatalAndStop(ErrStrMissing(name));
            return false;
        }

        byte[] data;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[COPY_BUF_LEN];
            int n;
            while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
            data = bytes.toByteArray();
        } finally {
            in.close();
        }

        File f = new File(dir, name);
        if (f.isFile() && f.length() == data.length) return true;

        OutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
        return true;
    }

    private static final String CLASS = "Natives";
    private static String ErrStrUnknownOs() {
        return CLASS + " failed to install because the operating system [" +
                OsChecker.RAW + "] is not Windows, Linux, or macOS.\n";
    }
    private static String ErrStrMkdir(File dir) {
        return CLASS + " failed to install because the directory [" + dir +
                "] could not be created.\n";
    }
    private static String ErrStrMissing(String name) {
        return CLASS + " failed to install because the library [" + name +
                "] is not on the classpath; the lwjgl-platform natives " +
                "jar for this operating system is missing.\n";
    }
    private static String ErrStrIo(File dir) {
        return CLASS + " failed to install into [" + dir + "] because " +
                "an exception was encountered.\n";
    }
    private static String LogStrInstalled(File dir) {
        return CLASS + " installed into [" + dir + "].\n";
    }
}
