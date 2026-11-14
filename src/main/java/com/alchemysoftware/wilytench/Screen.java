package com.alchemysoftware.wilytench;

import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
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
 * The picture is the size data/layout.json gives, 384 by 216 game
 * pixels, and is shown as large as the desktop allows at a whole
 * number of screen pixels to each game pixel. The view of the world
 * is the part of it the layout names; the game frame is drawn over
 * the rest.
 *
 * There are two layers. The picture is the upper one: the frame and
 * everything on it, the whole of a menu. The world is the lower one,
 * the size of the view and a pixel more each way, and is seen through
 * whatever part of the picture is left at Palette.BACKGROUND. A scene
 * that draws the world says so each frame with ShowWorld(), and says
 * how far to slide it up and to the left, in screen pixels less than
 * one game pixel. That is how the view can move by less than a game
 * pixel while every game pixel stays whole: the layers are only put
 * together on the screen, where the pixels are small. At one screen
 * pixel to a game pixel there is nothing to slide by, and the result
 * is what one layer would have shown.
 *
 * There are two ways of putting the layers on the screen. The plain
 * one is here and needs OpenGL 1.1 and nothing later: the picture
 * goes through Whitetail's framebuffer, which draws one texture in one
 * place, and the world layer is a second texture kept here. The other
 * is the CRT filter, in Crt, used when the player has asked for it and
 * the machine can do it.
 *
 * Before either, the picture may be sent down a video cable, in
 * Signal: another thing the player may ask for. That takes the two
 * layers and gives back one picture, finer across than game pixels,
 * which is then shown the plain way or through the CRT filter.
 */
final class Screen {
    static final String TITLE = "Wily Tench";

    /*
     * Version 1.0 is not a floor. It is the one request LWJGL 2 makes
     * the old-fashioned way, without the WGL/GLX_ARB_create_context
     * extension that drivers before about 2008 do not have, and it
     * yields the newest context the driver offers that still has the
     * fixed-function pipeline.
     */
    private static final int GL_MAJOR = 1, GL_MINOR = 0;

    /* How far beyond the view the world layer reaches, right and
       down, so that sliding it uncovers nothing. */
    private static final int WORLD_SPARE = 1;

    private static boolean windowInit, fbInit, crt;

    private static DisplayLayout layout;
    private static GLSourceTexelLayout srcLayout;
    private static byte[] indexBuf;
    private static int[] pixelBuf;

    private static byte[] worldBuf;
    private static int[] worldPixels;
    private static IntBuffer worldUpload;
    private static int worldW, worldH, worldTexW, worldTexH, worldTex;
    private static float worldU, worldV;
    private static boolean worldShown;
    private static int worldFineX, worldFineY;

    /* The video cable, if the picture is sent down one, and the
       texture for showing what comes out when the CRT filter is not
       there to show it. */
    private static Signal signal;
    private static int cableInUse = Signal.RGB;
    private static IntBuffer wideUpload;
    private static int wideW, wideTex;
    private static float wideU, wideV;

    private Screen() {}

    /**
     * Creates the window and its framebuffer.
     *
     * @param wantCrt whether to show the picture through the CRT
     *                filter, if this machine can
     * @param cable how the picture is to reach the screen: Signal.RGB
     *              for directly, or Signal.SVIDEO or Signal.COMPOSITE,
     *              if this machine can
     * @return false after a fatal error has been reported
     */
    static boolean Init(boolean fullscreen, boolean vSync,
                        boolean wantCrt, int cable) {
        /* Each query reports its own fatal error, so stop at the first
           rather than show the player two dialogs for one problem. */
        int desktopW = Window.GetDesktopWidth();
        if (desktopW <= 0) return false;
        int desktopH = Window.GetDesktopHeight();
        if (desktopH <= 0) return false;

        FramebufferPreset[] presets = {
            FramebufferPreset.Create(Layout.pictureW, Layout.pictureH,
                    Layout.viewX, Layout.viewY, Layout.viewW, Layout.viewH)
        };
        if (presets[0] == null) return false;

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
        srcLayout =
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

        if (!InitWorld()) return false;
        if (cable != Signal.RGB && !InitSignal(cable)) return false;
        crt = wantCrt && Crt.Init(layout, srcLayout, worldTex, worldW,
                worldH, worldTexW, worldTexH,
                signal == null ? 1 : signal.Across());

        Logger.LogSession(LogLevel.DEBUG, LogStrLayout(desktopW, desktopH));
        return true;
    }

    /* Makes the world layer and the texture it is shown through. */
    private static boolean InitWorld() {
        int texW = 1, texH = 1;

        worldW = layout.viewportWidth + WORLD_SPARE;
        worldH = layout.viewportHeight + WORLD_SPARE;
        while (texW < worldW) texW <<= 1;
        while (texH < worldH) texH <<= 1;
        worldTexW = texW;
        worldTexH = texH;
        worldU = (float) worldW / texW;
        worldV = (float) worldH / texH;

        try {
            worldBuf = new byte[worldW * worldH];
            worldPixels = new int[worldW * worldH];
            worldUpload = BufferUtils.createIntBuffer(worldW * worldH);
        } catch (OutOfMemoryError e) {
            ErrorHandler.LogFatalExcpAndStop(ERR_STR_OOM, e);
            return false;
        }

        /* GL_CLAMP, not the GL_CLAMP_TO_EDGE of OpenGL 1.2: the two
           differ only for texels that are never looked at here. */
        worldTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, texW, texH,
                0, srcLayout.glFormat, srcLayout.glType, (IntBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        int glErr = GL11.glGetError();
        if (worldTex == 0 || glErr != GL11.GL_NO_ERROR) {
            ErrorHandler.LogFatalAndStop(ErrStrWorldTex(glErr));
            return false;
        }
        return true;
    }

    /*
     * Sets up the video cable, and the texture its picture is shown
     * through. Where the machine cannot do it the game carries on
     * without, and that is not a failure; false is for a fatal error.
     */
    private static boolean InitSignal(int cable) {
        int texW = 1, texH = 1;

        wideW = layout.framebufferWidth * layout.pixelScale;
        while (texW < wideW) texW <<= 1;
        while (texH < layout.framebufferHeight) texH <<= 1;

        if (layout.pixelScale < Signal.MIN_SCALE) {
            Logger.LogSession(LogLevel.WARNING,
                    WarnStrSignalScale(layout.pixelScale));
            return true;
        }
        if (texW > GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE)) {
            Logger.LogSession(LogLevel.WARNING, WarnStrSignalTex(texW));
            return true;
        }

        try {
            signal = new Signal(cable, layout.framebufferWidth,
                    layout.framebufferHeight, layout.viewportX,
                    layout.viewportY, layout.viewportHeight, worldW,
                    layout.pixelScale);
            wideUpload = BufferUtils.createIntBuffer(
                    wideW * layout.framebufferHeight);
        } catch (OutOfMemoryError e) {
            ErrorHandler.LogFatalExcpAndStop(ERR_STR_OOM, e);
            return false;
        }
        wideU = (float) wideW / texW;
        wideV = (float) layout.framebufferHeight / texH;

        wideTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, wideTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, texW, texH,
                0, srcLayout.glFormat, srcLayout.glType, (IntBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        int glErr = GL11.glGetError();
        if (wideTex == 0 || glErr != GL11.GL_NO_ERROR) {
            ErrorHandler.LogFatalAndStop(ErrStrWorldTex(glErr));
            return false;
        }
        cableInUse = cable;
        Logger.LogSession(LogLevel.DEBUG, cable == Signal.SVIDEO ?
                LOG_STR_SVIDEO : LOG_STR_COMPOSITE);
        return true;
    }

    /** Safe to call after a failed or partial Init(). */
    static void Shutdown() {
        if (windowInit) Crt.Shutdown();
        if (windowInit && worldTex != 0) GL11.glDeleteTextures(worldTex);
        if (windowInit && wideTex != 0) GL11.glDeleteTextures(wideTex);
        signal = null;
        cableInUse = Signal.RGB;
        wideUpload = null;
        wideTex = wideW = 0;
        if (fbInit) GL12SoftwareFramebuffer.Shutdown();
        if (windowInit) Window.Shutdown();
        fbInit = windowInit = worldShown = crt = false;
        layout = null;
        srcLayout = null;
        indexBuf = null;
        pixelBuf = null;
        worldBuf = null;
        worldPixels = null;
        worldUpload = null;
        worldTex = worldW = worldH = 0;
    }

    /**
     * Says that the world layer is to be shown this frame, under the
     * picture, and how far to slide it. Lasts until the next
     * Present().
     *
     * @param fineX how far left to slide it: 0 to one less than
     *              GrainX(), in steps of that fineness
     * @param fineY how far up, likewise, by GrainY()
     */
    static void ShowWorld(int fineX, int fineY) {
        worldShown = true;
        worldFineX = fineX * (layout.pixelScale / GrainX());
        worldFineY = fineY * (layout.pixelScale / GrainY());
    }

    /**
     * Colors the layers through the palette and shows them. Reports a
     * fatal error if OpenGL raised one, since nothing else checks.
     */
    static void Present() {
        byte[] src = indexBuf;
        int[] dst = pixelBuf, pal = Palette.PIXELS;
        int through = pal[Palette.BACKGROUND];

        /* Blacks out the bars beside a picture that does not fill the
           screen; the framebuffer only draws its own rectangle. */
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        /* With the world beneath it, the picture's background is a
           hole: a pixel with no alpha. */
        if (worldShown) {
            ColorWorld();
            through = 0;
        }
        for (int i = 0, n = src.length; i < n; i++) {
            int index = src[i] & 0xFF;
            dst[i] = index == Palette.BACKGROUND ? through : pal[index];
        }

        if (signal != null) {
            /* The cable takes both layers and gives back one picture
               with no holes, so the world's texture is not needed. */
            int[] received = signal.Run(dst, worldPixels,
                    worldShown ? worldFineX : 0);

            if (crt) Crt.Present(received, 0, 0);
            else DrawWide(received);
        } else if (crt) {
            if (worldShown) UploadWorld();
            Crt.Present(dst, worldFineX, worldFineY);
        } else if (worldShown) {
            /* The alpha test drops the hole's pixels. */
            UploadWorld();
            DrawWorld();
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.5f);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL12SoftwareFramebuffer.Present();
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        } else {
            GL12SoftwareFramebuffer.Present();
        }
        worldShown = false;

        int glErr = GL11.glGetError();
        if (glErr != GL11.GL_NO_ERROR)
            ErrorHandler.LogFatalAndStop(ErrStrGl(glErr));
    }

    /* Colors the world layer. */
    private static void ColorWorld() {
        byte[] src = worldBuf;
        int[] dst = worldPixels, pal = Palette.PIXELS;

        for (int i = 0, n = src.length; i < n; i++)
            dst[i] = pal[src[i] & 0xFF];
    }

    /* Puts the colored world layer in its texture. */
    private static void UploadWorld() {
        worldUpload.clear();
        worldUpload.put(worldPixels);
        worldUpload.flip();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldTex);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, worldW, worldH,
                srcLayout.glFormat, srcLayout.glType, worldUpload);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /* Shows a picture that came down the video cable: one layer,
       filling the picture's place, a screen pixel to each of its
       pixels across. */
    private static void DrawWide(int[] pixels) {
        wideUpload.clear();
        wideUpload.put(pixels);
        wideUpload.flip();

        GL11.glViewport(layout.presentationX, layout.presentationY,
                layout.presentationWidth, layout.presentationHeight);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, wideTex);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, wideW,
                layout.framebufferHeight, srcLayout.glFormat,
                srcLayout.glType, wideUpload);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, wideV); GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glTexCoord2f(wideU, wideV); GL11.glVertex2f(1.0f, -1.0f);
        GL11.glTexCoord2f(wideU, 0.0f); GL11.glVertex2f(1.0f, 1.0f);
        GL11.glTexCoord2f(0.0f, 0.0f); GL11.glVertex2f(-1.0f, 1.0f);
        GL11.glEnd();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glViewport(0, 0, layout.windowWidth, layout.windowHeight);
    }

    /*
     * Draws the world layer where the view is, slid up and to the left
     * by the amount last given to ShowWorld(), and cut off at the
     * view's own edges.
     */
    private static void DrawWorld() {
        int scale = layout.pixelScale;

        /* Window positions count up from the bottom of the window. */
        int viewLeft = layout.presentationX + layout.viewportX * scale;
        int viewTop = layout.presentationY + layout.presentationHeight -
                layout.viewportY * scale;
        int viewW = layout.viewportWidth * scale;
        int viewH = layout.viewportHeight * scale;
        int left = viewLeft - worldFineX, top = viewTop + worldFineY;
        int right = left + worldW * scale, bottom = top - worldH * scale;

        GL11.glViewport(0, 0, layout.windowWidth, layout.windowHeight);
        GL11.glScissor(viewLeft, viewTop - viewH, viewW, viewH);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldTex);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, layout.windowWidth, 0.0, layout.windowHeight,
                -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, worldV); GL11.glVertex2f(left, bottom);
        GL11.glTexCoord2f(worldU, worldV); GL11.glVertex2f(right, bottom);
        GL11.glTexCoord2f(worldU, 0.0f); GL11.glVertex2f(right, top);
        GL11.glTexCoord2f(0.0f, 0.0f); GL11.glVertex2f(left, top);
        GL11.glEnd();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
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

    /**
     * @return the live world layer, one palette index per game pixel,
     *         row 0 at the top: GetWorldW() by GetWorldH(), which is
     *         the view and a pixel more to the right and below
     */
    static byte[] GetWorldBuf() { return worldBuf; }

    /** @return the width of the world layer in game pixels */
    static int GetWorldW() { return worldW; }

    /** @return the height of the world layer in game pixels */
    static int GetWorldH() { return worldH; }

    /**
     * @return by how small a step the world can be slid across: how
     *         many of them make a game pixel
     */
    static int GrainX() { return layout.pixelScale; }

    /**
     * @return the same, up and down. Through the CRT filter, or down
     *         a video cable, that is whole game pixels only: a row of
     *         game pixels is a scanline, and neither a tube nor a
     *         signal has anything between its lines.
     */
    static int GrainY() {
        return crt || signal != null ? 1 : layout.pixelScale;
    }

    /** @return whether the picture is sent down a video cable */
    static boolean SignalInUse() { return signal != null; }

    /**
     * @return which cable the picture is in fact sent down:
     *         Signal.RGB for none
     */
    static int Cable() { return cableInUse; }

    /** @return whether the picture is shown through the CRT filter */
    static boolean CrtInUse() { return crt; }

    /** @return the layout, for converting mouse positions */
    static DisplayLayout GetLayout() { return layout; }

    private static final String CLASS = "Screen";
    private static final String ERR_STR_NO_WINDOW = CLASS + " failed to " +
            "initialize because the graphics driver would not create an " +
            "OpenGL window. The reason it gave is in session.log.\n";
    private static final String ERR_STR_OOM = CLASS + " failed to " +
            "initialize because Java is out of heap memory.\n";
    private static final String LOG_STR_SVIDEO = CLASS + " is sending " +
            "the picture as S-Video.\n";
    private static final String LOG_STR_COMPOSITE = CLASS + " is " +
            "sending the picture as composite video.\n";
    private static String WarnStrSignalScale(int scale) {
        return CLASS + " is not sending the picture as a video signal " +
                "because it is shown at [" + scale + "] screen pixels " +
                "to a game pixel and that needs at least [" +
                Signal.MIN_SCALE + "].\n";
    }
    private static String WarnStrSignalTex(int texW) {
        return CLASS + " is not sending the picture as a video signal " +
                "because the graphics driver has no texture [" + texW +
                "] pixels wide.\n";
    }
    private static String ErrStrWorldTex(int glErr) {
        return CLASS + " failed to initialize because OpenGL would not " +
                "make the texture for the view of the world, reporting " +
                "error code [" + glErr + "].\n";
    }
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
