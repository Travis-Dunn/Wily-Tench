package com.alchemysoftware.wilytench;

import javax.swing.JOptionPane;

import com.alchemysoftware.whitetail.event.Event;
import com.alchemysoftware.whitetail.input.Button;
import com.alchemysoftware.whitetail.input.InputSys;
import com.alchemysoftware.whitetail.log.LogLevel;
import com.alchemysoftware.whitetail.log.Logger;
import com.alchemysoftware.whitetail.scene.Scene;
import com.alchemysoftware.whitetail.window.Window;

/**
 * The options screen, reached from the main menu: the settings in
 * config.ini, each with a widget to change it.
 *
 * A change is acted on and written to the file as it is made; there is
 * nothing to confirm. Back, or Escape, returns to the main menu.
 *
 * Fullscreen is the exception: the window is made once, when the game
 * starts, so that change waits for the next start. A line beside the
 * checkbox says so while the setting and the window disagree, and on
 * leaving the screen the system's own message box says so once more.
 *
 * The frame rate slider covers the rates worth choosing between. The
 * file allows more; a rate set there by hand that the slider cannot
 * show is left alone unless the slider is moved.
 */
final class OptionsScene extends Scene {
    private static final int FRAME_CELLS_W = 22, FRAME_CELLS_H = 12;
    private static final int TITLE_Y = 16;
    private static final int LABEL_X = 28, CONTROL_X = 144;
    private static final int ROWS_Y = 46, ROW_DY = 24;
    private static final int NOTE_GAP = 8;
    private static final int RADIO_DX = 64;
    private static final int SLIDER_CELLS = 7;
    private static final int BACK_CELLS = 5, BACK_Y = 150;

    private static final int FPS_MIN = 20, FPS_MAX = 300, FPS_STEP = 10;

    private static final int ROW_FULLSCREEN = 0, ROW_VSYNC = 1;
    private static final int ROW_FPS = 2, ROW_WALK = 3;
    private static final int BACK = 0;
    private static final int STALE = -1;

    private static final String TITLE = "Options";
    private static final String[] LABELS = {
        "Fullscreen", "Vertical sync", "Frame rate limit", "Walking"
    };
    private static final String NOTE_RELAUNCH = "After a relaunch";
    private static final String NOTE_SAVE_FAILED =
            "The settings could not be saved. See session.log.";

    private int frameX, frameY;
    private Toggle fullscreen, vSync, steady, pulse;
    private Slider fps;
    private Buttons buttons;

    private int pointerX, pointerY;
    private boolean saveFailed;

    /* The frame rate as last written out, so that drawing it every
       frame creates no strings. */
    private int shownFps = STALE;
    private String fpsText;

    @Override
    public void onInit() {
        Font small = Assets.small;
        int frameW = FRAME_CELLS_W * Skin.CELL;
        int controlX;

        frameX = Assets.MenuX() + (Assets.MENU_W - frameW) / 2;
        frameY = Assets.MenuY() +
                (Assets.MENU_H - FRAME_CELLS_H * Skin.CELL) / 2;
        controlX = frameX + CONTROL_X;

        fullscreen = new Toggle(small, "", controlX, RowY(ROW_FULLSCREEN),
                false);
        vSync = new Toggle(small, "", controlX, RowY(ROW_VSYNC), false);
        fps = new Slider(controlX, RowY(ROW_FPS), SLIDER_CELLS, FPS_MIN,
                FPS_MAX, FPS_STEP);
        steady = new Toggle(small, "Steady", controlX, RowY(ROW_WALK), true);
        pulse = new Toggle(small, "Pulse", controlX + RADIO_DX,
                RowY(ROW_WALK), true);

        buttons = new Buttons(small, new PushButton[] {
            new PushButton("Back",
                    frameX + (frameW - BACK_CELLS * Skin.CELL) / 2,
                    frameY + BACK_Y, BACK_CELLS)
        });
    }

    @Override
    public void onEnter() {
        buttons.Reset();
        fps.Reset();
        fps.SetValue(Settings.MaxFps());
        saveFailed = false;
    }

    @Override
    public void onExit() {}

    @Override
    public void onInput() {
        boolean pressed = InputSys.WasPressed(Button.MOUSE_LEFT);
        boolean changed = false;

        pointerX = Assets.PointerX();
        pointerY = Assets.PointerY();

        if (pressed && fullscreen.Contains(pointerX, pointerY)) {
            Settings.SetFullscreen(!Settings.Fullscreen());
            changed = true;
        }
        if (pressed && vSync.Contains(pointerX, pointerY)) {
            Settings.SetVSync(!Settings.VSync());
            Window.SetVSyncEnabled(Settings.VSync());
            changed = true;
        }
        if (fps.Input(pointerX, pointerY, pressed,
                InputSys.IsDown(Button.MOUSE_LEFT))) {
            Settings.SetMaxFps(fps.Value());
            changed = true;
        }
        if (pressed && steady.Contains(pointerX, pointerY))
            changed |= SetWalk(Mover.STYLE_STEADY);
        if (pressed && pulse.Contains(pointerX, pointerY))
            changed |= SetWalk(Mover.STYLE_PULSE);

        if (changed) saveFailed = !Settings.Save();

        int clicked = buttons.Input(pointerX, pointerY, pressed,
                InputSys.WasReleased(Button.MOUSE_LEFT));
        if (clicked == BACK || InputSys.WasPressed(Button.KEY_ESCAPE))
            Leave();
    }

    @Override
    public void onUpdate(double delta) { Assets.DiscardTicks(); }

    @Override
    public void onRender() {
        Font title = Assets.title, small = Assets.small;
        int frameW = FRAME_CELLS_W * Skin.CELL;
        int textDy = (Skin.CELL - small.lineH) / 2;

        if (Settings.MaxFps() != shownFps) {
            shownFps = Settings.MaxFps();
            fpsText = String.valueOf(shownFps);
        }

        Gfx.ClearClip();
        Gfx.FillRect(0, 0, Screen.GetW(), Screen.GetH(), Palette.NIGHT);
        Skin.DrawFrame(frameX, frameY, FRAME_CELLS_W, FRAME_CELLS_H);
        Text.DrawShadowed(title, TITLE, 0, TITLE.length(),
                frameX + (frameW - title.Width(TITLE, 0, TITLE.length())) / 2,
                frameY + TITLE_Y, Palette.AMBER, Palette.RUST);

        for (int row = 0; row < LABELS.length; row++) {
            Text.Draw(small, LABELS[row], 0, LABELS[row].length(),
                    frameX + LABEL_X, RowY(row) + textDy, Palette.BONE);
        }

        fullscreen.Draw(small, Settings.Fullscreen(), false);
        if (Settings.Fullscreen() != Settings.LaunchedFullscreen()) {
            Text.Draw(small, NOTE_RELAUNCH, 0, NOTE_RELAUNCH.length(),
                    fullscreen.x + Skin.CELL + NOTE_GAP,
                    fullscreen.y + textDy, Palette.ASH);
        }
        vSync.Draw(small, Settings.VSync(), false);

        fps.Draw();
        Text.Draw(small, fpsText, 0, fpsText.length(),
                fps.x + SLIDER_CELLS * Skin.CELL + NOTE_GAP, fps.y + textDy,
                Palette.AMBER);

        steady.Draw(small, Settings.WalkStyle() == Mover.STYLE_STEADY,
                steady.Contains(pointerX, pointerY));
        pulse.Draw(small, Settings.WalkStyle() == Mover.STYLE_PULSE,
                pulse.Contains(pointerX, pointerY));

        buttons.Draw();
        if (saveFailed) {
            Text.Draw(small, NOTE_SAVE_FAILED, 0, NOTE_SAVE_FAILED.length(),
                    frameX + (frameW - small.Width(NOTE_SAVE_FAILED, 0,
                    NOTE_SAVE_FAILED.length())) / 2,
                    frameY + BACK_Y - small.lineH - 2, Palette.PEACH);
        }
    }

    @Override
    public void onDestroy() {
        fullscreen = vSync = steady = pulse = null;
        fps = null;
        buttons = null;
    }

    @Override
    public boolean handleEvent(Event e) { return false; }

    private int RowY(int row) { return frameY + ROWS_Y + row * ROW_DY; }

    private boolean SetWalk(int style) {
        if (Settings.WalkStyle() == style) return false;
        Settings.SetWalkStyle(style);
        Mover.SetStyle(style);
        return true;
    }

    /* Goes back to the main menu, first saying, if it is so, that the
       fullscreen setting is waiting for a relaunch. */
    private void Leave() {
        Game.GoTo(Game.SCENE_MENU);
        if (Settings.Fullscreen() == Settings.LaunchedFullscreen()) return;

        try {
            JOptionPane.showMessageDialog(null, Settings.Fullscreen() ?
                    MSG_RELAUNCH_FULLSCREEN : MSG_RELAUNCH_WINDOW,
                    Screen.TITLE, JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException e) {
            /* A machine with no way to show a message box. The line
               on the screen has already said it. */
            Logger.LogSessionExcp(LogLevel.WARNING, ERR_STR_DIALOG, e);
        }
    }

    private static final String MSG_RELAUNCH_FULLSCREEN = Screen.TITLE +
            " will be fullscreen the next time it is started.";
    private static final String MSG_RELAUNCH_WINDOW = Screen.TITLE +
            " will be in a window the next time it is started.";
    private static final String ERR_STR_DIALOG = "OptionsScene failed " +
            "to show a message box because an exception was " +
            "encountered.\n";
}
