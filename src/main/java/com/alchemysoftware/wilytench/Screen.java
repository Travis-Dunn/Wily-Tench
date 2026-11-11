package com.alchemysoftware.wilytench;

import java.nio.ByteOrder;

import org.lwjgl.opengl.GL11;

import com.alchemysoftware.whitetail.display.DisplayLayout;
import com.alchemysoftware.whitetail.display.DisplayLayoutBuilder;
import com.alchemysoftware.whitetail.display.FramebufferPreset;
import com.alchemysoftware.whitetail.display.ResolutionMapping;
import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.framebuffer.GL12SoftwareFramebuffer;
import com.alchemysoftware.whitetail.framebuffer.GLSourceTexelLayout;
import com.alchemysoftware.whitetail.framebuffer.GLTextureTexelLayout;
import com.alchemysoftware.whitetail.log.LogLevel;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.window.GLProfile;
import com.alchemysoftware.whitetail.window.Window;

/**
 * Owns the window and the picture in it.
 *
 * The game draws 8-bit palette indices into GetIndexBuf(). Present()
 * turns them into real colors through Palette and shows the result,
 * scaled by a whole number so that every game pixel stays square.
 *
 * The game pixel grid is chosen from PRESETS to suit the desktop: the
 * one that covers the most of the screen wins. Each preset reserves a
 * side panel PANEL_W wide on the right for the interface and a chat
 * box CHAT_H tall along the bottom; the rest is the viewport onto the
 * world.
 */
final class Screen {
    static final String TITLE = "Wily Tench";

    /** Width in game pixels of the interface panel right of the world. */
    static final int PANEL_W = 128;

    /** Height in game pixels of the chat box below the world. */
    static final int CHAT_H = 88;

    /*
     * 512x384 doubles to exactly 1024x768, the usual laptop panel of
     * the Windows 98 years.
     * 640x360 fits 720p, 1080p, 1440p and 4K exactly, and 640x400 does
     * the same for 16:10 displays.
     */
    private static final int[][] PRESET_SIZES = {
        { 512, 384 }, { 640, 360 }, { 640, 400 }
    };

    /*
     * Version 1.0 is not a floor. It is the one request LWJGL 2 makes
     * the old-fashioned way, without the WGL/GLX_ARB_create_context
     * extension that drivers before about 2008 do not have, and it
     * yields the newest context the driver offers that still has the
     * fixed-function pipeline.
     */
    private static final int GL_MAJOR = 1, GL_MINOR = 0;

    private static boolean windowInit, fbInit;

    private static DisplayLayout layout;
    private static byte[] indexBuf;
    private static int[] pixelBuf;

    private Screen() {}

    /**
     * Creates the window and its framebuffer.
     *
     * @return false after a fatal error has been reported
     */
    static boolean Init(boolean fullscreen, boolean vSync) {
        /* Each query reports its own fatal error, so stop at the first
           rather than show the player two dialogs for one problem. */
        int desktopW = Window.GetDesktopWidth();
        if (desktopW <= 0) return false;
        int desktopH = Window.GetDesktopHeight();
        if (desktopH <= 0) return false;

        FramebufferPreset[] presets =
                new FramebufferPreset[PRESET_SIZES.length];
        for (int i = 0; i < presets.length; i++) {
            int w = PRESET_SIZES[i][0], h = PRESET_SIZES[i][1];
            presets[i] = FramebufferPreset.Create(w, h, 0, 0,
                    w - PANEL_W, h - CHAT_H);
            if (presets[i] == null) return false;
        }

        layout = DisplayLayoutBuilder.Resolve(presets,
                new ResolutionMapping[0], true, desktopW, desktopH,
                fullscreen);
        if (layout == null) return false;

        if (!Window.Init(TITLE, layout.windowWidth, layout.windowHeight,
                vSync, fullscreen, false, GL_MAJOR, GL_MINOR,
                GLProfile.IGNORED)) {
            /* Window only warns in the session log when the driver
               refuses; a game with no window has to stop. */
            ErrorHandler.LogFatalAndStop(ERR_STR_NO_WINDOW);
            return false;
        }
        windowInit = true;

        Logger.LogSession(LogLevel.DEBUG, LogStrDriver());

        /* Both layouts take 0xAABBGGRR ints on a little-endian machine.
           RGBA_UBYTE is preferred because it only needs OpenGL 1.1. */
        GLSourceTexelLayout srcLayout =
                ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ?
                GLSourceTexelLayout.RGBA_UBYTE :
                GLSourceTexelLayout.RGBA_UINT_8888_REV;

        if (!GL12SoftwareFramebuffer.Init(srcLayout,
                GLTextureTexelLayout.RGBA8,
                layout.framebufferWidth, layout.framebufferHeight,
                layout.presentationX, layout.presentationY,
                layout.presentationWidth, layout.presentationHeight,
                layout.windowWidth, layout.windowHeight)) return false;
        fbInit = true;

        pixelBuf = GL12SoftwareFramebuffer.GetBuf();
        try {
            indexBuf = new byte[pixelBuf.length];
        } catch (OutOfMemoryError e) {
            ErrorHandler.LogFatalExcpAndStop(ERR_STR_OOM, e);
            return false;
        }

        Logger.LogSession(LogLevel.DEBUG, LogStrLayout(desktopW, desktopH));
        return true;
    }

    /** Safe to call after a failed or partial Init(). */
    static void Shutdown() {
        if (fbInit) GL12SoftwareFramebuffer.Shutdown();
        if (windowInit) Window.Shutdown();
        fbInit = windowInit = false;
        layout = null;
        indexBuf = null;
        pixelBuf = null;
    }

    /**
     * Colors the index buffer through the palette and shows it. Reports
     * a fatal error if OpenGL raised one, since nothing else checks.
     */
    static void Present() {
        byte[] src = indexBuf;
        int[] dst = pixelBuf, pal = Palette.PIXELS;

        for (int i = 0, n = src.length; i < n; i++)
            dst[i] = pal[src[i] & 0xFF];

        /* Blacks out the bars beside a picture that does not fill the
           screen; the framebuffer only draws its own rectangle. */
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL12SoftwareFramebuffer.Present();

        int glErr = GL11.glGetError();
        if (glErr != GL11.GL_NO_ERROR)
            ErrorHandler.LogFatalAndStop(ErrStrGl(glErr));
    }

    /**
     * @return the live picture, one palette index per game pixel, row 0
     *         at the top: the pixel at (x, y) is buf[y * GetW() + x]
     */
    static byte[] GetIndexBuf() { return indexBuf; }

    /** @return the width of the picture in game pixels */
    static int GetW() { return layout.framebufferWidth; }

    /** @return the height of the picture in game pixels */
    static int GetH() { return layout.framebufferHeight; }

    /** @return the layout, for converting mouse positions */
    static DisplayLayout GetLayout() { return layout; }

    private static final String CLASS = "Screen";
    private static final String ERR_STR_NO_WINDOW = CLASS + " failed to " +
            "initialize because the graphics driver would not create an " +
            "OpenGL window. The reason it gave is in session.log.\n";
    private static final String ERR_STR_OOM = CLASS + " failed to " +
            "initialize because Java is out of heap memory.\n";
    private static String ErrStrGl(int glErr) {
        return CLASS + " failed to present because OpenGL reported " +
                "error code [" + glErr + "].\n";
    }
    private static String LogStrDriver() {
        return CLASS + " is using OpenGL [" +
                GL11.glGetString(GL11.GL_VERSION) + "] on [" +
                GL11.glGetString(GL11.GL_RENDERER) + "] by [" +
                GL11.glGetString(GL11.GL_VENDOR) + "].\n";
    }
    private static String LogStrLayout(int desktopW, int desktopH) {
        return CLASS + " initialized [" + layout.framebufferWidth + "x" +
                layout.framebufferHeight + " game pixels at scale " +
                layout.pixelScale + " in a " + layout.windowWidth + "x" +
                layout.windowHeight + " window on a " + desktopW + "x" +
                desktopH + " desktop].\n";
    }
}
