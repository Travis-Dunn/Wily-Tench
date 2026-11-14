package com.alchemysoftware.wilytench;

import java.io.File;

import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.error.RunFlag;
import com.alchemysoftware.whitetail.gc.GCPriest;
import com.alchemysoftware.whitetail.input.InputSys;
import com.alchemysoftware.whitetail.input.lwjgl.LwjglInput;
import com.alchemysoftware.whitetail.log.LogLevel;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;
import com.alchemysoftware.whitetail.scene.SceneManager;
import com.alchemysoftware.whitetail.timer.Timer;
import com.alchemysoftware.whitetail.window.Window;

/**
 * Starts everything up in order, runs the frame loop, and shuts down
 * whatever was started, in reverse.
 *
 * Game logic advances in fixed ticks of TICK_NS, however fast frames are
 * drawn. Scenes drain the ticks themselves from onUpdate().
 */
final class Game {
    /** One game tick, 600 milliseconds, in nanoseconds. */
    static final long TICK_NS = 600L * 1000L * 1000L;

    static final String SCENE_MENU = "menu";
    static final String SCENE_OPTIONS = "options";
    static final String SCENE_WORLD = "world";

    private static final long NS_PER_SEC = 1000L * 1000L * 1000L;
    private static final long NS_PER_MS = 1000L * 1000L;

    /* How far startup got, so shutdown undoes exactly that much. */
    private static final int STAGE_NONE = 0;
    private static final int STAGE_GC_PRIEST = 1;
    private static final int STAGE_SCREEN = 2;
    private static final int STAGE_INPUT_SYS = 3;
    private static final int STAGE_INPUT_BACKEND = 4;
    private static final int STAGE_SAVE = 5;
    private static final int STAGE_ASSETS = 6;
    private static final int STAGE_SCENES = 7;

    private static int stage = STAGE_NONE;

    private Game() {}

    static void Run() {
        File dataDir = DataDir.Resolve();
        if (dataDir == null) {
            ErrorHandler.LogFatalAndStop(ERR_STR_NO_DATA_DIR);
            return;
        }
        if (!Logger.Init(dataDir)) return;

        try {
            if (Init(dataDir)) Loop();
        } catch (RuntimeException e) {
            ErrorHandler.LogFatalExcpAndStop(ERR_STR_UNCAUGHT, e);
        } catch (Error e) {
            ErrorHandler.LogFatalExcpAndStop(ERR_STR_UNCAUGHT, e);
        }

        try {
            Shutdown();
        } catch (RuntimeException e) {
            Logger.LogSessionExcp(LogLevel.WARNING, ERR_STR_SHUTDOWN, e);
        } catch (Error e) {
            Logger.LogSessionExcp(LogLevel.WARNING, ERR_STR_SHUTDOWN, e);
        }
        Logger.Shutdown();
    }

    private static boolean Init(File dataDir) {
        Logger.LogSession(LogLevel.DEBUG, LogStrSystem());

        /* Started first so that a fatal error anywhere below, which
           stops the flag, is still in force when startup finishes. */
        RunFlag.Start();

        if (!Settings.Init(dataDir)) return false;
        Mover.SetStyle(Settings.WalkStyle());
        if (!Natives.Install(dataDir)) return false;

        /* Art and maps are read from inside the game's own jar. */
        if (!ResourceStreamResolver.Init(null,
                ResourceStreamResolver.MODE_CLASSPATH)) return false;

        /* The size of the picture is in the layout. */
        if (!Layout.Load()) return false;

        if (!GCPriest.Init()) return false;
        stage = STAGE_GC_PRIEST;

        boolean screenOk = Screen.Init(Settings.Fullscreen(),
                Settings.VSync());
        /* Screen cleans up after itself even when only half started. */
        stage = STAGE_SCREEN;
        if (!screenOk) return false;

        if (!InputSys.Init()) return false;
        stage = STAGE_INPUT_SYS;

        if (!LwjglInput.Init()) return false;
        stage = STAGE_INPUT_BACKEND;

        if (!Timer.Init(TICK_NS)) return false;

        if (!SaveGame.Init(dataDir)) return false;
        stage = STAGE_SAVE;

        /* Assets cleans up after itself even when only half loaded. */
        boolean assetsOk = Assets.Init();
        stage = STAGE_ASSETS;
        if (!assetsOk) return false;

        if (!SceneManager.Init()) return false;
        stage = STAGE_SCENES;
        SceneManager.RegisterScene(SCENE_MENU, new MenuScene());
        SceneManager.RegisterScene(SCENE_OPTIONS, new OptionsScene());
        SceneManager.RegisterScene(SCENE_WORLD, new WorldScene());
        SceneManager.InitScenes();
        SceneManager.TransitionTo(SCENE_MENU);

        return RunFlag.IsRunning();
    }

    /** Changes scene, from the start of the next frame. */
    static void GoTo(String scene) { SceneManager.TransitionTo(scene); }

    private static void Loop() {
        long nextFrame = System.nanoTime();

        while (RunFlag.IsRunning() && !Window.ShouldClose()) {
            Window.PollEvents();
            LwjglInput.Update();
            SceneManager.Input();

            Timer.Update();
            SceneManager.Update(Timer.GetDt());

            SceneManager.Render();
            Screen.Present();
            Window.SwapBuffers();

            GCPriest.CheckAndLogGCActivity();

            /* vSync usually paces the loop. This cap is for when it is
               off or the driver ignores it, so the game does not spin a
               whole processor drawing frames nobody can see. */
            nextFrame += NS_PER_SEC / Settings.MaxFps();
            long wait = nextFrame - System.nanoTime();
            if (wait > 0) {
                try {
                    Thread.sleep(wait / NS_PER_MS, (int) (wait % NS_PER_MS));
                } catch (InterruptedException e) {
                    RunFlag.Stop();
                }
            } else {
                nextFrame -= wait;
            }
        }
    }

    private static void Shutdown() {
        if (stage >= STAGE_SCENES) SceneManager.Shutdown();
        if (stage >= STAGE_ASSETS) Assets.Shutdown();
        /* After the scenes, so the last tick's state gets written. */
        if (stage >= STAGE_SAVE) SaveGame.Shutdown();
        if (stage >= STAGE_INPUT_BACKEND) LwjglInput.Shutdown();
        if (stage >= STAGE_INPUT_SYS) InputSys.Shutdown();
        if (stage >= STAGE_SCREEN) Screen.Shutdown();
        if (stage >= STAGE_GC_PRIEST) GCPriest.Shutdown();
        stage = STAGE_NONE;
    }

    private static final String ERR_STR_NO_DATA_DIR = Screen.TITLE +
            " failed to start because it has nowhere to keep its " +
            "settings and logs. The reason was printed to the console.\n";
    private static final String ERR_STR_UNCAUGHT = Screen.TITLE +
            " failed to continue because of an unexpected error.\n";
    private static final String ERR_STR_SHUTDOWN = Screen.TITLE +
            " failed to shut down cleanly because an exception was " +
            "encountered.\n";
    private static String LogStrSystem() {
        return Screen.TITLE + " is starting on Java [" +
                System.getProperty("java.version") + "] by [" +
                System.getProperty("java.vendor") + "], operating system [" +
                System.getProperty("os.name") + " " +
                System.getProperty("os.version") + "], architecture [" +
                System.getProperty("os.arch") + "].\n";
    }
}
