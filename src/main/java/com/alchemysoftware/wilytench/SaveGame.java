package com.alchemysoftware.wilytench;

import java.io.File;

import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.log.LogLevel;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.save.SaveData;
import com.alchemysoftware.whitetail.save.SaveInfo;
import com.alchemysoftware.whitetail.save.SaveStore;

/**
 * Keeps the one saved game on disk.
 *
 * There is no "save" for the player to choose: like an online game,
 * what has happened has happened. The game hands over its state after
 * every tick in which anything changed, and it is written at once.
 *
 * Writing is done on a thread of its own. Each write waits for the
 * disk to confirm it, which can take as long as a frame or two, and
 * the game should not stutter every tick for that. If the game hands
 * over states faster than the disk takes them, only the newest waits;
 * the ones it replaces were out of date anyway.
 *
 * The file is Whitetail's save format, which keeps the previous save
 * beside the current one, so that losing power mid-write costs at most
 * one tick. It lives in the data directory as wts-player.a and
 * wts-player.b; deleting both starts a new game.
 */
final class SaveGame {
    /** Raise when the meaning of the saved values changes. */
    static final int VERSION = 2;

    /* Outcomes of Load(). */
    static final int LOAD_NEW_GAME = 0;
    static final int LOAD_OK = 1;
    /** The newest save was damaged; the one before it was loaded. */
    static final int LOAD_OLDER = 2;
    /** A save exists but no copy can be read; a new game was begun. */
    static final int LOAD_UNREADABLE = 3;
    /** The save is from a newer game; a fatal error was reported. */
    static final int LOAD_TOO_NEW = 4;

    private static final String SLOT = "player";
    private static final String DISPLAY_NAME = Screen.TITLE;
    private static final long SHUTDOWN_WAIT_MS = 10000;

    private static final Object lock = new Object();

    /* Guarded by lock. */
    private static SaveData pending;
    private static boolean writing, stopping;

    private static volatile boolean failed;

    private static Thread writer;
    private static SaveData loaded;

    private SaveGame() {}

    /**
     * @param dataDir the directory returned by DataDir.Resolve()
     * @return false after a fatal error has been reported
     */
    static boolean Init(File dataDir) {
        return SaveStore.Init(dataDir);
    }

    /**
     * Reads the saved game, if there is one, and starts the writer.
     * Call once, after Init().
     *
     * @return one of the LOAD_ outcomes
     */
    static int Load() {
        int outcome = LOAD_NEW_GAME;
        SaveInfo info = SaveStore.Describe(SLOT);

        if (info != null) {
            boolean readable = info.currentIntact || info.previousIntact;

            if (readable && info.gameVersion > VERSION) {
                ErrorHandler.LogFatalAndStop(ErrStrTooNew(info.gameVersion));
                return LOAD_TOO_NEW;
            }
            if (readable) loaded = SaveStore.Read(SLOT);

            if (loaded == null) outcome = LOAD_UNREADABLE;
            else outcome = info.currentIntact ? LOAD_OK : LOAD_OLDER;
        }
        Logger.LogSession(LogLevel.DEBUG, LogStrLoaded(outcome));

        /* From here on only the writer thread touches the save store,
           which is not built for two threads at once. */
        writer = new Thread(new Runnable() {
            public void run() { WriteLoop(); }
        }, "save-writer");
        writer.start();
        return outcome;
    }

    /** @return what Load() read, or null if it read nothing */
    static SaveData Loaded() { return loaded; }

    /**
     * Hands over a complete state to be written as soon as possible.
     * The caller must not touch it again.
     */
    static void Queue(SaveData data) {
        synchronized (lock) {
            pending = data;
            lock.notifyAll();
        }
    }

    /**
     * @return true, once, if a write has failed since the last call.
     *         The reason is in session.log.
     */
    static boolean TakeFailure() {
        if (!failed) return false;
        failed = false;
        return true;
    }

    /** Blocks until everything queued so far is on disk. */
    static void Flush() {
        synchronized (lock) {
            try {
                while (pending != null || writing) lock.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Writes anything still queued and stops the writer. */
    static void Shutdown() {
        if (writer == null) return;

        synchronized (lock) {
            stopping = true;
            lock.notifyAll();
        }
        try {
            writer.join(SHUTDOWN_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (writer.isAlive())
            Logger.LogSession(LogLevel.WARNING, ERR_STR_STUCK);

        writer = null;
        loaded = null;
        synchronized (lock) {
            pending = null;
            stopping = false;
        }
    }

    private static void WriteLoop() {
        try {
            for (;;) {
                SaveData data;

                synchronized (lock) {
                    writing = false;
                    lock.notifyAll();
                    while (pending == null && !stopping) lock.wait();
                    if (pending == null) return;

                    data = pending;
                    pending = null;
                    writing = true;
                }
                if (!SaveStore.Write(SLOT, VERSION, DISPLAY_NAME, data))
                    failed = true;
            }
        } catch (InterruptedException e) {
            Logger.LogSession(LogLevel.WARNING, ERR_STR_INTERRUPTED);
        } catch (RuntimeException e) {
            ErrorHandler.LogFatalExcpAndStop(ERR_STR_EXCP, e);
        } catch (Error e) {
            ErrorHandler.LogFatalExcpAndStop(ERR_STR_EXCP, e);
        }
    }

    private static final String CLASS = "SaveGame";
    private static final String ERR_STR_EXCP = CLASS + " failed to keep " +
            "saving because an exception was encountered.\n";
    private static final String ERR_STR_INTERRUPTED = CLASS + " failed " +
            "to keep saving because its thread was interrupted.\n";
    private static final String ERR_STR_STUCK = CLASS + " failed to " +
            "finish the last save because the disk did not answer within [" +
            SHUTDOWN_WAIT_MS + "] milliseconds.\n";
    private static String ErrStrTooNew(int version) {
        return CLASS + " failed to load the saved game because it was " +
                "made by a newer release of the game: its save version [" +
                version + "] is above this release's [" + VERSION + "]. " +
                "It has been left untouched.\n";
    }
    private static String LogStrLoaded(int outcome) {
        switch (outcome) {
            case LOAD_NEW_GAME:
                return CLASS + " found no saved game.\n";
            case LOAD_OK:
                return CLASS + " loaded the saved game.\n";
            case LOAD_OLDER:
                return CLASS + " loaded the previous saved game because " +
                        "the newest was damaged.\n";
            default:
                return CLASS + " failed to load the saved game because " +
                        "no copy of it could be read.\n";
        }
    }
}
