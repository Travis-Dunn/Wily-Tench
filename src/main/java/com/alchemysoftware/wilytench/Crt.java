package com.alchemysoftware.wilytench;

import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import com.alchemysoftware.whitetail.display.DisplayLayout;
import com.alchemysoftware.whitetail.framebuffer.GLSourceTexelLayout;
import com.alchemysoftware.whitetail.log.LogLevel;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.resource.ResourceStreamResolver;

/**
 * The CRT filter: the second way Screen has of putting its two layers
 * on the screen, for machines with shaders and screen pixels to spare.
 *
 * It is a setting, off unless the player turns it on, and decided when
 * the game starts. The look is all in shaders/crt.frag, whose opening
 * comment describes it; this class compiles that program, gives it the
 * two layers as textures and the settings of Tuning's knobs, and draws
 * one rectangle with it.
 *
 * It needs OpenGL 2.0, for shaders at all, and at least MIN_SCALE
 * screen pixels to a game pixel, since the scanlines and the shadow
 * mask are made of screen pixels and there must be a few to make them
 * of. Where either is missing, Init() says why in session.log and the
 * game carries on without the filter. Nothing here is a fatal error.
 */
final class Crt {
    /** The fewest screen pixels to a game pixel the filter works at. */
    static final int MIN_SCALE = 3;

    private static final String FOLDER = "shaders";
    private static final String FILENAME = "crt.frag";
    private static final int LOG_MAX = 4096;

    /* Which texture unit each layer is given to the program on. */
    private static final int UNIT_PICTURE = 0, UNIT_WORLD = 1;

    private static boolean init;
    private static DisplayLayout layout;
    private static GLSourceTexelLayout srcLayout;
    private static int program, shader, pictureTex, worldTex;
    private static int pictureW, pictureH;
    private static IntBuffer upload;
    private static int slideAt;

    /* The knobs of Tuning that are the filter's, the name each goes
       by in the program, and where the program keeps it. */
    private static final int[] KNOBS = {
        Tuning.SCAN_WIDTH, Tuning.SCAN_DEPTH, Tuning.MASK_DEPTH,
        Tuning.MASK_PITCH, Tuning.STRIPE_WIDTH, Tuning.CELL_LIT,
        Tuning.CELL_STAGGER, Tuning.GLOW, Tuning.GLOW_SPREAD,
        Tuning.BLOOM, Tuning.BEAM_BLUR
    };
    private static final String[] KNOB_NAMES = {
        "scanWidth", "scanDepth", "maskDepth", "maskPitch", "stripeWidth",
        "cellLit", "cellStagger", "glow", "glowSpread", "bloom", "beamBlur"
    };
    private static final int[] knobAt = new int[KNOBS.length];

    private Crt() {}

    /**
     * Sets the filter up. The window must exist.
     *
     * @param worldTex the texture Screen keeps the world layer in,
     *                 worldTexW by worldTexH texels, of which the top
     *                 left worldW by worldH hold the layer
     * @param across how many pixels of the picture given to Present()
     *               make a game pixel, across: 1, or more when it has
     *               come through Signal
     * @return whether the filter can be used; if not, the reason is in
     *         session.log and nothing is left behind
     */
    static boolean Init(DisplayLayout layout, GLSourceTexelLayout srcLayout,
                        int worldTex, int worldW, int worldH,
                        int worldTexW, int worldTexH, int across) {
        if (layout.pixelScale < MIN_SCALE) {
            Logger.LogSession(LogLevel.WARNING,
                    WarnStrScale(layout.pixelScale));
            return false;
        }
        if (!GLContext.getCapabilities().OpenGL20) {
            Logger.LogSession(LogLevel.WARNING, WARN_STR_NO_SHADERS);
            return false;
        }

        String source = ReadSource();
        if (source == null) return false;

        Crt.layout = layout;
        Crt.srcLayout = srcLayout;
        Crt.worldTex = worldTex;
        pictureW = layout.framebufferWidth * across;
        pictureH = layout.framebufferHeight;

        int texW = 1, texH = 1;
        while (texW < pictureW) texW <<= 1;
        while (texH < pictureH) texH <<= 1;

        if (!Compile(source)) {
            Shutdown();
            return false;
        }

        upload = BufferUtils.createIntBuffer(pictureW * pictureH);
        pictureTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, pictureTex);
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

        /* What the program is told once: everything but the slide. */
        GL20.glUseProgram(program);
        GL20.glUniform1i(At("picture"), UNIT_PICTURE);
        GL20.glUniform1i(At("world"), UNIT_WORLD);
        GL20.glUniform2f(At("pictureTexels"), texW, texH);
        GL20.glUniform2f(At("worldTexels"), worldTexW, worldTexH);
        GL20.glUniform2f(At("pictureSize"), pictureW, pictureH);
        GL20.glUniform2f(At("worldSize"), worldW, worldH);
        GL20.glUniform2f(At("viewOrigin"), layout.viewportX,
                layout.viewportY);
        GL20.glUniform1f(At("scale"), layout.pixelScale);
        GL20.glUniform1f(At("across"), across);
        slideAt = At("slide");
        for (int i = 0; i < KNOBS.length; i++)
            knobAt[i] = At(KNOB_NAMES[i]);
        GL20.glUseProgram(0);

        int glErr = GL11.glGetError();
        if (pictureTex == 0 || glErr != GL11.GL_NO_ERROR) {
            Logger.LogSession(LogLevel.WARNING, WarnStrGl(glErr));
            Shutdown();
            return false;
        }

        Logger.LogSession(LogLevel.DEBUG, LOG_STR_INIT);
        return init = true;
    }

    /** Safe to call after a failed Init(), or with none. */
    static void Shutdown() {
        if (program != 0) GL20.glDeleteProgram(program);
        if (shader != 0) GL20.glDeleteShader(shader);
        if (pictureTex != 0) GL11.glDeleteTextures(pictureTex);
        program = shader = pictureTex = worldTex = 0;
        layout = null;
        srcLayout = null;
        upload = null;
        init = false;
    }

    /**
     * Shows the two layers through the filter. The world layer must
     * already be in its texture, if any of the picture is see-through.
     *
     * @param picture the picture's pixels, with no alpha where the
     *                world is to show: as many to a game pixel across
     *                as Init() was told
     * @param fineX how far left the world is slid, in screen pixels
     * @param fineY how far up, likewise
     */
    static void Present(int[] picture, int fineX, int fineY) {
        float scale = layout.pixelScale;

        upload.clear();
        upload.put(picture);
        upload.flip();

        GL11.glViewport(layout.presentationX, layout.presentationY,
                layout.presentationWidth, layout.presentationHeight);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);

        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_WORLD);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_PICTURE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, pictureTex);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, pictureW,
                pictureH, srcLayout.glFormat, srcLayout.glType, upload);

        GL20.glUseProgram(program);
        GL20.glUniform2f(slideAt, fineX / scale, fineY / scale);
        for (int i = 0; i < KNOBS.length; i++)
            GL20.glUniform1f(knobAt[i], (float) Tuning.Get(KNOBS[i]));

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        /* The program is given each place on the rectangle in game
           pixels, counted from the top left. */
        GL11.glBegin(GL11.GL_QUADS);
        float w = layout.framebufferWidth;
        GL11.glTexCoord2f(0.0f, pictureH); GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glTexCoord2f(w, pictureH); GL11.glVertex2f(1.0f, -1.0f);
        GL11.glTexCoord2f(w, 0.0f); GL11.glVertex2f(1.0f, 1.0f);
        GL11.glTexCoord2f(0.0f, 0.0f); GL11.glVertex2f(-1.0f, 1.0f);
        GL11.glEnd();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_WORLD);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + UNIT_PICTURE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glViewport(0, 0, layout.windowWidth, layout.windowHeight);
    }

    /* Builds the program from the shader's text. The vertices are left
       to OpenGL's own handling, which passes positions and texture
       coordinates through as they are given. */
    private static boolean Compile(String source) {
        shader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) ==
                GL11.GL_FALSE) {
            Logger.LogSession(LogLevel.WARNING, WarnStrCompile(
                    GL20.glGetShaderInfoLog(shader, LOG_MAX)));
            return false;
        }

        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, shader);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) ==
                GL11.GL_FALSE) {
            Logger.LogSession(LogLevel.WARNING, WarnStrCompile(
                    GL20.glGetProgramInfoLog(program, LOG_MAX)));
            return false;
        }
        return true;
    }

    private static int At(String uniform) {
        return GL20.glGetUniformLocation(program, uniform);
    }

    private static String ReadSource() {
        InputStream in = ResourceStreamResolver.Get(FOLDER, FILENAME);
        if (in == null) return null;

        try {
            return new String(Bitmap.ReadAll(in), "US-ASCII");
        } catch (IOException e) {
            Logger.LogSessionExcp(LogLevel.WARNING, WARN_STR_READ, e);
            return null;
        }
    }

    private static final String CLASS = "Crt";
    private static final String LOG_STR_INIT = CLASS + " initialized.\n";
    private static final String WARN_STR_NO_SHADERS = CLASS + " is not " +
            "in use because the graphics driver offers no shaders " +
            "(OpenGL 2.0).\n";
    private static final String WARN_STR_READ = CLASS + " is not in use " +
            "because [" + FOLDER + "/" + FILENAME + "] could not be " +
            "read.\n";
    private static String WarnStrScale(int scale) {
        return CLASS + " is not in use because the picture is shown at " +
                "[" + scale + "] screen pixels to a game pixel and the " +
                "filter needs at least [" + MIN_SCALE + "].\n";
    }
    private static String WarnStrCompile(String log) {
        return CLASS + " is not in use because the graphics driver " +
                "would not build [" + FOLDER + "/" + FILENAME + "]: " +
                log + "\n";
    }
    private static String WarnStrGl(int glErr) {
        return CLASS + " is not in use because OpenGL reported error " +
                "code [" + glErr + "] while it was set up.\n";
    }
}
