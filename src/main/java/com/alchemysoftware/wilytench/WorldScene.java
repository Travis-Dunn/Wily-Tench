package com.alchemysoftware.wilytench;

import java.util.Random;

import com.alchemysoftware.whitetail.display.DisplayLayout;
import com.alchemysoftware.whitetail.error.ErrorHandler;
import com.alchemysoftware.whitetail.error.RunFlag;
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
 * Left click walks the character to the clicked tile, or as close as it
 * can get; clicking an ore rock walks up to it and mines it; clicking
 * anything a script exists for, such as a ladder, walks up to it and
 * runs the script. The view stays centered on the character. Typing
 * goes to the chat box. Escape closes the game.
 *
 * After every game tick the game is saved, if the tick changed
 * anything worth saving.
 */
final class WorldScene extends Scene {
    private static final int TILE = Sprites.TILE;

    /* The first is where a new game begins. */
    private static final String[] MAPS = { "cave.txt", "dungeon.txt" };
    private static final String[] SCRIPTS = { "travel.whtl" };

    /* Sprites beyond the map view: the player, with room to spare for
       the creatures and effects to come. */
    private static final int SPARE_SPRITES = 256;

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
    private static final String MSG_CANT_REACH = "You can't reach that.";
    private static final String HOVER_WALK = "Walk here";
    private static final String HOVER_MINE = "Mine ";
    private static final String HOVER_USE = "Use ";

    private int w, h;
    private int viewPortX, viewPortY, viewW, viewH;

    private boolean spritesInit;
    private Session session;
    private SaveState save;
    private TileView tiles;

    private Font smallFont, boldFont, titleFont;
    private ChatBox chat;
    private SidePanel panel;

    /* Which map the tiles are showing, to notice when it changes. */
    private World shown;

    /* The map pixel at the top left of the viewport, as last drawn. */
    private int viewX, viewY;

    /* The mouse, in viewport pixels, or NOWHERE when outside it. */
    private int mouseX = NOWHERE, mouseY = NOWHERE;

    /* What the hover label last said, and about which tile and state of
       the map, so the label is only rebuilt when one of those changes. */
    private String hoverText;
    private int hoverTile = NOWHERE, hoverVersion;

    private double msCarry;
    private int animMs;

    @Override
    public void onInit() {
        DisplayLayout layout = Screen.GetLayout();
        byte[] buf = Screen.GetIndexBuf();

        w = Screen.GetW();
        h = Screen.GetH();
        viewPortX = layout.viewportX;
        viewPortY = layout.viewportY;
        viewW = layout.viewportWidth;
        viewH = layout.viewportHeight;

        Gfx.Init(buf, w, h);

        if ((smallFont = Font.Load("small.fnt")) == null) return;
        if ((boldFont = Font.Load("bold.fnt")) == null) return;
        if ((titleFont = Font.Load("title.fnt")) == null) return;

        if (!SpriteRenderer.Init(buf, w, h, viewPortX, viewPortY, viewW,
                viewH, TileView.SpritesNeeded(viewW, viewH) +
                SPARE_SPRITES)) return;
        spritesInit = true;

        /* The maps name their tiles by sprite, so sprites come first. */
        if (!Sprites.Install()) return;

        int panelX = viewPortX + viewW;
        chat = new ChatBox(smallFont, viewPortX, viewPortY + viewH, viewW,
                h - viewPortY - viewH);

        session = Session.Create(MAPS, SCRIPTS, chat, new Random());
        if (session == null) return;
        tiles = TileView.Create(session.Here(), viewW, viewH);
        if (tiles == null) {
            ErrorHandler.LogFatalAndStop(ERR_STR_SPRITES);
            return;
        }
        panel = new SidePanel(smallFont, boldFont, titleFont, panelX, 0,
                w - panelX, h, session.inventory, session.skills, chat);

        save = new SaveState(session);
        int loadOutcome = SaveGame.Load();
        if (loadOutcome == SaveGame.LOAD_TOO_NEW) return;
        if (SaveGame.Loaded() != null) save.Restore(SaveGame.Loaded());
        else session.inventory.Add(Item.BRONZE_HAMMER);

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
        if (InputSys.WasPressed(Button.KEY_ESCAPE)) RunFlag.Stop();

        DisplayLayout layout = Screen.GetLayout();
        int fbX = layout.WindowToFramebufferX(InputSys.GetMouseX());
        int fbY = layout.WindowToFramebufferY(InputSys.GetMouseY());

        if (fbX >= 0 && fbY >= 0 && layout.InViewport(fbX, fbY)) {
            mouseX = layout.FramebufferToViewportX(fbX);
            mouseY = layout.FramebufferToViewportY(fbY);
        } else {
            mouseX = mouseY = NOWHERE;
        }

        /* A script that is part-way through has the player's attention;
           clicks wait until it is done. */
        if (mouseX != NOWHERE && InputSys.WasPressed(Button.MOUSE_LEFT) &&
                !session.Busy()) {
            int tx = TileUnderMouseX(), ty = TileUnderMouseY();

            if (session.Here().OreAt(tx, ty) != Ore.NONE ||
                    session.Usable(tx, ty))
                session.player.WalkToTarget(tx, ty);
            else if (!session.player.WalkTo(tx, ty))
                chat.Add(MSG_CANT_REACH, Palette.BONE);
        }

        chat.Input(fbX, fbY);
        panel.Input(fbX, fbY);
    }

    @Override
    public void onUpdate(double delta) {
        Player player = session.player;

        while (Timer.Tick()) {
            session.Advance();
            if (save.Changed()) SaveGame.Queue(save.Capture());
        }
        if (SaveGame.TakeFailure()) chat.Add(MSG_SAVE_FAILED, Palette.PEACH);

        if (shown != session.Here()) {
            shown = session.Here();
            tiles.SetWorld(shown);
            hoverTile = NOWHERE;
        }

        /* Animations count whole milliseconds; carry the fraction so
           none of the time is lost. */
        msCarry += delta * 1000.0;
        int deltaMs = (int) msCarry;
        msCarry -= deltaMs;

        animMs = (animMs + deltaMs) % (ANIM_STEP_MS * World.ANIM_FRAMES);

        double tickFraction = Math.min(Timer.GetInterpolationFactor(), 1.0);
        player.Animate(tickFraction, deltaMs);

        viewX = player.PixelX(tickFraction) + TILE / 2 - viewW / 2;
        viewY = player.PixelY(tickFraction) + TILE / 2 - viewH / 2;
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
        if (mouseX != NOWHERE) DrawHover();
        Gfx.ClearClip();

        chat.Draw();
        panel.Draw();
    }

    @Override
    public void onDestroy() {
        if (spritesInit) SpriteRenderer.Shutdown();
        spritesInit = false;
        Session.Close();
        Sprites.Shutdown();
        Gfx.Shutdown();
        session = null;
        save = null;
        tiles = null;
        shown = null;
        chat = null;
        panel = null;
        smallFont = boldFont = titleFont = null;
    }

    @Override
    public boolean handleEvent(Event e) { return false; }

    /* Outlines the tile under the mouse and names what is on it. */
    private void DrawHover() {
        World world = session.Here();
        int tx = TileUnderMouseX(), ty = TileUnderMouseY();
        boolean solid = world.Solid(tx, ty);
        boolean mineable = world.OreAt(tx, ty) != Ore.NONE &&
                !world.RockEmpty(tx, ty);
        boolean usable = session.Usable(tx, ty);
        String name = HoverText(world, tx, ty, mineable, usable);

        Gfx.Outline(viewPortX + tx * TILE - viewX,
                viewPortY + ty * TILE - viewY, TILE, TILE,
                mineable || usable ? Palette.MINT :
                solid ? Palette.RUST : Palette.BONE);

        if (name != null) {
            Text.DrawOutlined(smallFont, name, 0, name.length(),
                    viewPortX + HOVER_TEXT_X, viewPortY + HOVER_TEXT_Y,
                    Palette.MINT, Palette.INK);
        } else if (!solid) {
            Text.DrawOutlined(smallFont, HOVER_WALK, 0, HOVER_WALK.length(),
                    viewPortX + HOVER_TEXT_X, viewPortY + HOVER_TEXT_Y,
                    Palette.BONE, Palette.INK);
        }
    }

    private String HoverText(World world, int tx, int ty,
                             boolean mineable, boolean usable) {
        int tile = world.Contains(tx, ty) ? ty * world.w + tx : NOWHERE;

        if (tile != hoverTile || world.Version() != hoverVersion) {
            String name = world.Name(tx, ty);
            hoverTile = tile;
            hoverVersion = world.Version();
            hoverText = mineable ? HOVER_MINE + name :
                    usable ? HOVER_USE + name : name;
        }
        return hoverText;
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
