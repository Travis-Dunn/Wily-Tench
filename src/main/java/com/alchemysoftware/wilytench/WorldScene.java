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
 * A frame is drawn in this order. The world, into the view. The marks
 * on the world: the walk marker, the outline and label of what the
 * pointer is over. Then the game frame, one picture the size of the
 * whole screen with a hole where the view is. Then the interface, on
 * the frame: the chat box or talk, and the side panel. Last, whatever
 * floats over everything: menus and tooltips.
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
 * Talk with someone is shown by DialogueBox in place of the chat box,
 * and nothing can be typed while it is up.
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

    /* Clear of the frame's rounded corner. */
    private static final int HOVER_TEXT_X = 6, HOVER_TEXT_Y = 4;

    /* The keys that pick the first, second, third, and fourth thing
       offered to say. */
    private static final int[] CHOICE_KEYS = {
        Button.KEY_1, Button.KEY_2, Button.KEY_3, Button.KEY_4
    };

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
    private DialogueBox dialogue;
    private Actions actions;

    /* The pointer in picture pixels, negative when outside it. */
    private int pointerX = NOWHERE, pointerY = NOWHERE;

    /* The tile the option list is about, or NOWHERE if it is not about
       a tile. While a menu is open this is the tile it was opened on. */
    private int optionTileX = NOWHERE, optionTileY = NOWHERE;

    /* Which map the tiles are showing, to notice when it changes. */
    private World shown;

    private Camera camera;

    /* The map pixel at the top left of the viewport, as last drawn,
       and how many screen pixels past it the view really is. */
    private int viewX, viewY, fineX, fineY;

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

        chat = new ChatBox(smallFont, Layout.chatX, Layout.chatY,
                Layout.chatW, Layout.chatH);

        session = Session.Create(Content.Maps(), Content.ScriptFiles(),
                chat, new Random());
        if (session == null) return;
        camera = new Camera(Screen.GrainX(), Screen.GrainY());
        tiles = TileView.Create(session.Here(), Screen.GetWorldW(),
                Screen.GetWorldH());
        if (tiles == null) {
            ErrorHandler.LogFatalAndStop(ERR_STR_SPRITES);
            return;
        }
        panel = new SidePanel(smallFont, boldFont, w, h, session.inventory,
                session.skills);
        menu = new ContextMenu(smallFont, w, h);
        makeMenu = new MakeMenu(smallFont, session, viewPortX, viewPortY,
                viewW, viewH);
        dialogue = new DialogueBox(smallFont, boldFont, session,
                Layout.chatX, Layout.chatY, Layout.chatW, Layout.chatH);
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
                Palette.TEXT);
        chat.Add(MSG_HOW_TO, Palette.TEXT_DIM);
        if (loadOutcome == SaveGame.LOAD_OLDER)
            chat.Add(MSG_LOADED_OLDER, Palette.TEXT_WARN);
        else if (loadOutcome == SaveGame.LOAD_UNREADABLE)
            chat.Add(MSG_UNREADABLE, Palette.TEXT_WARN);
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

        if (session.TalkKind() != Session.TALK_NONE) {
            /* Talk stands where the chat box does and takes its
               clicks and keys. A click anywhere else is an order like
               any other, and ends the talk if it cancels the script. */
            if (TalkInput(left)) return;
        } else {
            chat.Input(pointerX, pointerY);
        }

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
        if (SaveGame.TakeFailure())
            chat.Add(MSG_SAVE_FAILED, Palette.TEXT_WARN);

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
        fineX = camera.FineX();
        fineY = camera.FineY();
        tiles.Show(viewX, viewY, animMs / ANIM_STEP_MS);
    }

    @Override
    public void onRender() {
        Player player = session.player;

        /* The world and the marks on it go into the world's own
           layer, which Screen slides under the picture. */
        SpriteRenderer.Clear(Palette.BACKGROUND);
        SpriteRenderer.Render(viewX, viewY);

        boolean hover = session.OpenMake() == Recipes.NONE &&
                optionTileX != NOWHERE && menu.Count() != 0;

        Gfx.Init(Screen.GetWorldBuf(), Screen.GetWorldW(),
                Screen.GetWorldH());
        if (player.Walking()) {
            Gfx.Corners(player.DestX() * TILE - viewX,
                    player.DestY() * TILE - viewY,
                    TILE, TILE, MARKER_ARM, Palette.AMBER);
        }
        if (hover) DrawHoverMark();
        Gfx.Init(Screen.GetIndexBuf(), w, h);
        Screen.ShowWorld(fineX, fineY);

        /* The picture is see-through where the view is, and the frame
           goes around that, its rounded corners covering the view's
           square ones. The interface is drawn on the frame. */
        Gfx.FillRect(viewPortX, viewPortY, viewW, viewH,
                Palette.BACKGROUND);
        Gfx.DrawBitmap(Assets.frame, 0, 0);

        if (hover && !menu.IsOpen()) {
            Gfx.SetClip(viewPortX, viewPortY, viewW, viewH);
            DrawHoverLabel();
            Gfx.ClearClip();
        }

        if (session.TalkKind() != Session.TALK_NONE)
            dialogue.Draw(pointerX, pointerY);
        else chat.Draw();
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
        camera = null;
        shown = null;
        chat = null;
        panel = null;
        menu = null;
        makeMenu = null;
        dialogue = null;
        actions = null;
        smallFont = boldFont = titleFont = null;
    }

    @Override
    public boolean handleEvent(Event e) { return false; }

    /*
     * Passes on the player's answer to the talk on show: Space, or a
     * click on the box, reads on; a number key, or a click on one of
     * the things offered to say, picks it.
     *
     * Returns whether a click was on the box, and so is used up.
     */
    private boolean TalkInput(boolean left) {
        boolean onBox = dialogue.Contains(pointerX, pointerY);

        if (session.TalkKind() == Session.TALK_CHOICE) {
            int option = left ? dialogue.OptionAt(pointerX, pointerY) :
                    DialogueBox.NO_OPTION;
            for (int i = 0; i < CHOICE_KEYS.length; i++)
                if (InputSys.WasPressed(CHOICE_KEYS[i])) option = i;
            if (option != DialogueBox.NO_OPTION) session.ChooseTalk(option);
        } else if ((left && onBox) ||
                InputSys.WasPressed(Button.KEY_SPACE)) {
            session.ContinueTalk();
        }
        return onBox;
    }

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

    /* Outlines the tile the option list is about, in the world's
       layer. */
    private void DrawHoverMark() {
        World world = session.Here();
        int first = menu.Action(0);
        boolean usable = (first == Actions.USE_LOC ||
                first == Actions.USE_ITEM_ON_LOC) &&
                !world.RockEmpty(optionTileX, optionTileY);

        Gfx.Outline(optionTileX * TILE - viewX, optionTileY * TILE - viewY,
                TILE, TILE, usable ? Palette.MINT :
                world.Solid(optionTileX, optionTileY) ? Palette.RUST :
                Palette.BONE);
    }

    /* Writes the first option at the top left of the view, on the
       picture. Drawn inside the view's clip. */
    private void DrawHoverLabel() {
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
