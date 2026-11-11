package com.alchemysoftware.wilytench;

import java.util.Random;

import com.alchemysoftware.whitetail.display.DisplayLayout;
import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.event.Event;
import com.alchemysoftware.whitetail.input.Button;
import com.alchemysoftware.whitetail.input.InputSys;
import com.alchemysoftware.whitetail.scene.Scene;
import com.alchemysoftware.whitetail.sprite.SpriteRenderer;
import com.alchemysoftware.whitetail.timer.Timer;

/**
 * The window onto a Session: draws the map the player is on, the
 * character, and the interface around them, and passes on what the
 * player asks for.
 *
 * Whatever the mouse is over, in the view of the world or in the
 * inventory, has a list of things that can be done to it. A left click
 * does the first; a right click opens the list as a menu. The label at
 * the top left of the view names the first. Actions decides what is on
 * the list and carries an option out.
 *
 * When a script asks what the player wants made, MakeMenu shows the
 * choices in the middle of the view and has the mouse to itself until
 * one is picked or it is dismissed.
 *
 * The buttons above the inventory switch the side panel between the
 * inventory and the skills; a click on one goes no further.
 *
 * The view follows the character, trailing a little behind. Typing
 * goes to the chat box. Escape closes the menu if one is open, lets
 * go of the selected item if there is one, and otherwise goes back to
 * the main menu, where the world stands still until Play is chosen
 * again.
 *
 * After every game tick the game is saved, if the tick changed
 * anything worth saving.
 */
final class WorldScene extends Scene {
    private static final int TILE = Sprites.TILE;

    private static final int ANIM_STEP_MS = 150;
    private static final int MARKER_ARM = 4;
    private static final int NOWHERE = -1;

    private static final int HOVER_TEXT_X = 4, HOVER_TEXT_Y = 3;

    private static final String MSG_WELCOME =
            "Welcome to " + Screen.TITLE + ".";
    private static final String MSG_WELCOME_BACK =
            "Welcome back to " + Screen.TITLE + ".";
    private static final String MSG_HOW_TO = "Click the ground to walk " +
            "and a rock to mine it. Ladders and stairs lead elsewhere.";
    private static final String MSG_LOADED_OLDER = "Your newest saved " +
            "game was damaged, so the one before it was loaded.";
    private static final String MSG_UNREADABLE = "Your saved game could " +
            "not be read, so a new game was started.";
    private static final String MSG_SAVE_FAILED = "The game could not " +
            "be saved. The reason is in session.log.";

    /* "/ 2 more options", ready-made so the label costs nothing. */
    private static final String[] MORE = BuildMore();

    private int w, h;
    private int viewPortX, viewPortY, viewW, viewH;

    private Session session;
    private SaveState save;
    private TileView tiles;

    private Font smallFont, boldFont, titleFont;
    private ChatBox chat;
    private SidePanel panel;
    private ContextMenu menu;
    private MakeMenu makeMenu;
    private Actions actions;

    /* The pointer in picture pixels, negative when outside it. */
    private int pointerX = NOWHERE, pointerY = NOWHERE;

    /* The tile the option list is about, or NOWHERE if it is not about
       a tile. While a menu is open this is the tile it was opened on. */
    private int optionTileX = NOWHERE, optionTileY = NOWHERE;

    /* Which map the tiles are showing, to notice when it changes. */
    private World shown;

    private final Camera camera = new Camera();

    /* The map pixel at the top left of the viewport, as last drawn. */
    private int viewX, viewY;

    /* The mouse, in viewport pixels, or NOWHERE when outside it. */
    private int mouseX = NOWHERE, mouseY = NOWHERE;

    private double msCarry;
    private int animMs;

    @Override
    public void onInit() {
        DisplayLayout layout = Screen.GetLayout();

        w = Screen.GetW();
        h = Screen.GetH();
        viewPortX = layout.viewportX;
        viewPortY = layout.viewportY;
        viewW = layout.viewportWidth;
        viewH = layout.viewportHeight;

        smallFont = Assets.small;
        boldFont = Assets.bold;
        titleFont = Assets.title;

        int panelX = viewPortX + viewW;
        chat = new ChatBox(smallFont, viewPortX, viewPortY + viewH, viewW,
                h - viewPortY - viewH);

        session = Session.Create(Content.Maps(), Content.ScriptFiles(),
                chat, new Random());
        if (session == null) return;
        tiles = TileView.Create(session.Here(), viewW, viewH);
        if (tiles == null) {
            ErrorHandler.LogFatalAndStop(ERR_STR_SPRITES);
            return;
        }
        panel = new SidePanel(smallFont, boldFont, titleFont, panelX, 0,
                w - panelX, h, w, h, session.inventory, session.skills);
        menu = new ContextMenu(smallFont, w, h);
        makeMenu = new MakeMenu(smallFont, session, viewPortX, viewPortY,
                viewW, viewH);
        actions = new Actions(session);

        save = new SaveState(session);
        int loadOutcome = SaveGame.Load();
        if (loadOutcome == SaveGame.LOAD_TOO_NEW) return;
        if (SaveGame.Loaded() != null) {
            save.Restore(SaveGame.Loaded());
        } else {
            int[] items = Content.StartingItems();
            for (int i = 0; i < items.length; i++)
                session.inventory.Add(items[i]);
        }

        chat.Add(SaveGame.Loaded() != null ? MSG_WELCOME_BACK : MSG_WELCOME,
                Palette.BONE);
        chat.Add(MSG_HOW_TO, Palette.ASH);
        if (loadOutcome == SaveGame.LOAD_OLDER)
            chat.Add(MSG_LOADED_OLDER, Palette.PEACH);
        else if (loadOutcome == SaveGame.LOAD_UNREADABLE)
            chat.Add(MSG_UNREADABLE, Palette.PEACH);
    }

    @Override
    public void onEnter() {}

    @Override
    public void onExit() {}

    @Override
    public void onInput() {
        boolean left = InputSys.WasPressed(Button.MOUSE_LEFT);
        boolean right = InputSys.WasPressed(Button.MOUSE_RIGHT);

        if (InputSys.WasPressed(Button.KEY_ESCAPE)) {
            if (session.OpenMake() != Recipes.NONE) session.CloseMake();
            else if (menu.IsOpen()) menu.Close();
            else if (actions.SelectedSlot() != Actions.NO_SLOT)
                actions.Deselect();
            else Game.GoTo(Game.SCENE_MENU);
        }

        DisplayLayout layout = Screen.GetLayout();
        pointerX = layout.WindowToFramebufferX(InputSys.GetMouseX());
        pointerY = layout.WindowToFramebufferY(InputSys.GetMouseY());

        if (pointerX >= 0 && pointerY >= 0 &&
                layout.InViewport(pointerX, pointerY)) {
            mouseX = layout.FramebufferToViewportX(pointerX);
            mouseY = layout.FramebufferToViewportY(pointerY);
        } else {
            mouseX = mouseY = NOWHERE;
        }

        chat.Input(pointerX, pointerY);

        if (session.OpenMake() != Recipes.NONE) {
            /* The window asking what to make has the mouse to itself.
               A left click on a recipe picks it; a click of either
               button off the window declines. */
            menu.Close();
            makeMenu.Layout(session.OpenMake());

            int recipe = makeMenu.RecipeAt(pointerX, pointerY);
            if (left && recipe != MakeMenu.NO_RECIPE)
                session.ChooseMake(recipe);
            else if ((left || right) &&
                    !makeMenu.Contains(pointerX, pointerY))
                session.CloseMake();
            return;
        }

        if (menu.IsOpen()) {
            /* The menu has the mouse to itself. A left click chooses
               or dismisses; so does straying. A right click elsewhere
               dismisses too, and then opens a menu there instead. */
            int option = menu.OptionAt(pointerX, pointerY);

            if (left) {
                menu.Close();
                if (option != ContextMenu.NO_OPTION)
                    actions.Do(menu, option);
                return;
            }
            if (!menu.Near(pointerX, pointerY) ||
                    (right && option == ContextMenu.NO_OPTION)) {
                menu.Close();
            } else {
                return;
            }
        }

        panel.Input(pointerX, pointerY);
        if (left && panel.Click()) return;
        BuildOptions();
        if (menu.Count() == 0) return;

        if (right) menu.Open(pointerX, pointerY);
        else if (left) actions.Do(menu, 0);
    }

    @Override
    public void onUpdate(double delta) {
        Player player = session.player;

        while (Timer.Tick()) {
            session.Advance();
            if (save.Changed()) SaveGame.Queue(save.Capture());
        }
        if (SaveGame.TakeFailure()) chat.Add(MSG_SAVE_FAILED, Palette.PEACH);

        boolean arrived = shown != session.Here();
        if (arrived) {
            /* A menu about a tile of the map just left means nothing. */
            menu.Close();
            shown = session.Here();
            tiles.SetWorld(shown);
        }

        /* Animations count whole milliseconds; carry the fraction so
           none of the time is lost. */
        msCarry += delta * 1000.0;
        int deltaMs = (int) msCarry;
        msCarry -= deltaMs;

        animMs = (animMs + deltaMs) % (ANIM_STEP_MS * World.ANIM_FRAMES);

        double tickFraction = Math.min(Timer.GetInterpolationFactor(), 1.0);
        player.Animate(tickFraction, deltaMs);

        /* The camera aims to put the character's middle at the middle
           of the view; on a new map, or a new game, it starts there. */
        int offsetX = TILE / 2 - viewW / 2, offsetY = TILE / 2 - viewH / 2;
        int aimX = player.PixelX(tickFraction) + offsetX;
        int aimY = player.PixelY(tickFraction) + offsetY;
        if (arrived) {
            camera.Snap(aimX, aimY);
        } else {
            camera.Follow(player.ExactX(tickFraction) + offsetX,
                    player.ExactY(tickFraction) + offsetY, aimX, aimY,
                    delta);
        }

        viewX = camera.X();
        viewY = camera.Y();
        tiles.Show(viewX, viewY, animMs / ANIM_STEP_MS);
    }

    @Override
    public void onRender() {
        Player player = session.player;

        SpriteRenderer.Clear(Palette.BACKGROUND);
        SpriteRenderer.Render(viewX, viewY);

        Gfx.SetClip(viewPortX, viewPortY, viewW, viewH);
        if (player.Walking()) {
            Gfx.Corners(viewPortX + player.DestX() * TILE - viewX,
                    viewPortY + player.DestY() * TILE - viewY,
                    TILE, TILE, MARKER_ARM, Palette.AMBER);
        }
        if (session.OpenMake() == Recipes.NONE) DrawHover();
        Gfx.ClearClip();

        chat.Draw();
        panel.Select(actions.SelectedSlot());
        panel.Draw();
        if (session.OpenMake() != Recipes.NONE) {
            makeMenu.Layout(session.OpenMake());
            makeMenu.Draw(pointerX, pointerY);
        } else if (menu.IsOpen()) {
            menu.Draw(pointerX, pointerY);
        } else {
            panel.DrawTip();
        }
    }

    @Override
    public void onDestroy() {
        Session.Close();
        session = null;
        save = null;
        tiles = null;
        shown = null;
        chat = null;
        panel = null;
        menu = null;
        makeMenu = null;
        actions = null;
        smallFont = boldFont = titleFont = null;
    }

    @Override
    public boolean handleEvent(Event e) { return false; }

    /*
     * Fills the option list for whatever the pointer is over: an item
     * in the inventory, a tile of the world, or nothing. Does nothing
     * while a menu is open, since the list is then in use.
     */
    private void BuildOptions() {
        if (menu.IsOpen()) return;

        menu.Begin();
        optionTileX = optionTileY = NOWHERE;

        if (panel.HoverSlot() != SidePanel.NO_SLOT) {
            actions.ForItem(menu, panel.HoverSlot());
        } else if (mouseX != NOWHERE) {
            optionTileX = TileUnderMouseX();
            optionTileY = TileUnderMouseY();
            actions.ForTile(menu, optionTileX, optionTileY);
        }
    }

    /*
     * Outlines the tile the option list is about and, unless a menu is
     * open and saying it better, writes the first option at the top
     * left of the view. Drawn inside the view's clip.
     */
    private void DrawHover() {
        if (optionTileX == NOWHERE || menu.Count() == 0) return;

        World world = session.Here();
        int first = menu.Action(0);
        boolean usable = (first == Actions.USE_LOC ||
                first == Actions.USE_ITEM_ON_LOC) &&
                !world.RockEmpty(optionTileX, optionTileY);

        Gfx.Outline(viewPortX + optionTileX * TILE - viewX,
                viewPortY + optionTileY * TILE - viewY, TILE, TILE,
                usable ? Palette.MINT :
                world.Solid(optionTileX, optionTileY) ? Palette.RUST :
                Palette.BONE);
        if (menu.IsOpen()) return;

        String verb = menu.Verb(0), subject = menu.Subject(0);
        int textX = viewPortX + HOVER_TEXT_X;
        int textY = viewPortY + HOVER_TEXT_Y;

        textX = Text.DrawOutlined(smallFont, verb, 0, verb.length(), textX,
                textY, Palette.BONE, Palette.INK);
        if (subject != null) {
            textX = Text.DrawOutlined(smallFont, subject, 0,
                    subject.length(), textX + smallFont.Width(" ", 0, 1),
                    textY, menu.SubjectColor(0), Palette.INK);
        }

        String more = MORE[Math.min(menu.Count() - 1, MORE.length - 1)];
        Text.DrawOutlined(smallFont, more, 0, more.length(), textX, textY,
                Palette.ASH, Palette.INK);
    }

    private static String[] BuildMore() {
        String[] more = new String[16];
        more[0] = "";
        more[1] = " / 1 more option";
        for (int i = 2; i < more.length; i++)
            more[i] = " / " + i + " more options";
        return more;
    }

    private int TileUnderMouseX() {
        return TileView.FloorDiv(viewX + mouseX, TILE);
    }

    private int TileUnderMouseY() {
        return TileView.FloorDiv(viewY + mouseY, TILE);
    }

    private static final String ERR_STR_SPRITES = "WorldScene failed to " +
            "initialize because the sprite renderer ran out of sprites.\n";
}
