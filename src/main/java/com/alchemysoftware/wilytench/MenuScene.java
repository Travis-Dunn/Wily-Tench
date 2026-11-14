package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.error.RunFlag;
import com.alchemysoftware.whitetail.event.Event;
import com.alchemysoftware.whitetail.input.Button;
import com.alchemysoftware.whitetail.input.InputSys;
import com.alchemysoftware.whitetail.scene.Scene;

/**
 * The main menu: the first thing shown, and where Escape leads from
 * everywhere else.
 *
 * Play goes to the world. There is one character and one saved game,
 * read when the game starts, so Play continues that game if there was
 * one and begins a new one if not; the line under the buttons says
 * which. Options goes to the options screen. Quit, or Escape, closes
 * the game. Enter does what Play does.
 *
 * The world stands still while the menu is up.
 */
final class MenuScene extends Scene {
    private static final int FRAME_CELLS_W = 12, FRAME_CELLS_H = 10;
    private static final int TITLE_Y = 18;
    private static final int BUTTON_CELLS = 6;
    private static final int BUTTONS_Y = 50, BUTTON_DY = 26;
    private static final int STATUS_Y = 134;

    private static final int PLAY = 0, OPTIONS = 1, QUIT = 2;

    private static final String TITLE = Screen.TITLE;
    private static final String STATUS_NEW = "A new game will be started.";
    private static final String STATUS_CONTINUE =
            "Your saved game will be continued.";

    private int frameX, frameY;
    private Buttons buttons;

    /* Whether Play has been used in this run of the game, after which
       there is a game to continue even if none was loaded. */
    private boolean played;

    @Override
    public void onInit() {
        int frameW = FRAME_CELLS_W * Skin.CELL;
        int buttonX;

        frameX = Assets.MenuX() + (Assets.MENU_W - frameW) / 2;
        frameY = Assets.MenuY() +
                (Assets.MENU_H - FRAME_CELLS_H * Skin.CELL) / 2;
        buttonX = frameX + (frameW - BUTTON_CELLS * Skin.CELL) / 2;

        buttons = new Buttons(Assets.small, new PushButton[] {
            NewButton("Play", buttonX, PLAY),
            NewButton("Options", buttonX, OPTIONS),
            NewButton("Quit", buttonX, QUIT)
        });
    }

    @Override
    public void onEnter() { buttons.Reset(); }

    @Override
    public void onExit() {}

    @Override
    public void onInput() {
        if (InputSys.WasPressed(Button.KEY_ESCAPE)) {
            RunFlag.Stop();
            return;
        }

        int clicked = buttons.Input(Assets.PointerX(), Assets.PointerY(),
                InputSys.WasPressed(Button.MOUSE_LEFT),
                InputSys.WasReleased(Button.MOUSE_LEFT));
        if (InputSys.WasPressed(Button.KEY_ENTER)) clicked = PLAY;

        if (clicked == PLAY) {
            played = true;
            Game.GoTo(Game.SCENE_WORLD);
        } else if (clicked == OPTIONS) {
            Game.GoTo(Game.SCENE_OPTIONS);
        } else if (clicked == QUIT) {
            RunFlag.Stop();
        }
    }

    @Override
    public void onUpdate(double delta) { Assets.DiscardTicks(); }

    @Override
    public void onRender() {
        Font title = Assets.title, small = Assets.small;
        int frameW = FRAME_CELLS_W * Skin.CELL;
        String status = played || SaveGame.Loaded() != null ?
                STATUS_CONTINUE : STATUS_NEW;

        Gfx.ClearClip();
        Gfx.FillRect(0, 0, Screen.GetW(), Screen.GetH(), Palette.NIGHT);
        Skin.DrawFrame(frameX, frameY, FRAME_CELLS_W, FRAME_CELLS_H);

        Text.DrawShadowed(title, TITLE, 0, TITLE.length(),
                frameX + (frameW - title.Width(TITLE, 0, TITLE.length())) / 2,
                frameY + TITLE_Y, Palette.AMBER, Palette.RUST);
        buttons.Draw();
        Text.Draw(small, status, 0, status.length(),
                frameX + (frameW - small.Width(status, 0, status.length()))
                / 2, frameY + STATUS_Y, Palette.ASH);
    }

    @Override
    public void onDestroy() { buttons = null; }

    @Override
    public boolean handleEvent(Event e) { return false; }

    private PushButton NewButton(String label, int x, int place) {
        return new PushButton(label, x,
                frameY + BUTTONS_Y + place * BUTTON_DY, BUTTON_CELLS);
    }
}
