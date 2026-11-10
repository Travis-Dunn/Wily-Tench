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
 * The game world: the map, the player's character, the view of them,
 * and the interface around that view.
 *
 * Left click walks the character to the clicked tile, or as close as it
 * can get; clicking an ore rock walks up to it and mines it. The view
 * stays centered on the character. Typing goes to the chat box. Escape
 * closes the game.
 *
 * Each game tick the map, then the player, then the player's current
 * job are advanced, in that order.
 */
final class WorldScene extends Scene {
    private static final int TILE = Sprites.TILE;

    private static final String MAP = "cave.txt";

    /* Sprites beyond the map view: the player, with room to spare for
       the creatures and effects to come. */
    private static final int SPARE_SPRITES = 256;

    private static final int ANIM_STEP_MS = 150;
    private static final int MARKER_ARM = 4;
    private static final int NOWHERE = -1;

    private static final int HOVER_TEXT_X = 4, HOVER_TEXT_Y = 3;

    private static final String MSG_WELCOME =
            "Welcome to " + Screen.TITLE + ".";
    private static final String MSG_HOW_TO = "Click the ground to walk " +
            "and a rock to mine it.";
    private static final String MSG_CANT_REACH = "You can't reach that.";
    private static final String HOVER_WALK = "Walk here";
    private static final String HOVER_MINE = "Mine ";

    private int w, h;
    private int viewPortX, viewPortY, viewW, viewH;

    private boolean spritesInit;
    private World world;
    private TileView tiles;
    private Player player;

    private Font smallFont, boldFont, titleFont;
    private ChatBox chat;
    private SidePanel panel;

    private Inventory inventory;
    private Skills skills;
    private Mining mining;

    /* What the hover label last said, and about which tile and state of
       the map, so the label is only rebuilt when one of those changes. */
    private String hoverText;
    private int hoverTile = NOWHERE, hoverVersion;

    /* The map pixel at the top left of the viewport, as last drawn. */
    private int viewX, viewY;

    /* The mouse, in viewport pixels, or NOWHERE when outside it. */
    private int mouseX = NOWHERE, mouseY = NOWHERE;

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

        /* The map names its tiles by sprite frame, so sprites first. */
        if (!Sprites.Install()) return;
        if ((world = World.Load(MAP)) == null) return;

        tiles = TileView.Create(world, viewW, viewH);
        player = Player.Create(world);
        if (tiles == null || player == null) {
            ErrorHandler.LogFatalAndStop(ERR_STR_SPRITES);
            return;
        }

        inventory = new Inventory();
        inventory.Add(Item.BRONZE_HAMMER);
        skills = new Skills();

        int panelX = viewPortX + viewW;
        chat = new ChatBox(smallFont, viewPortX, viewPortY + viewH, viewW,
                h - viewPortY - viewH);
        panel = new SidePanel(smallFont, boldFont, titleFont, panelX, 0,
                w - panelX, h, inventory, skills, chat);
        mining = new Mining(world, player, inventory, skills, chat,
                new Random());

        chat.Add(MSG_WELCOME, Palette.BONE);
        chat.Add(MSG_HOW_TO, Palette.ASH);
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

        if (mouseX != NOWHERE && InputSys.WasPressed(Button.MOUSE_LEFT)) {
            int tx = TileUnderMouseX(), ty = TileUnderMouseY();

            if (world.OreAt(tx, ty) != Ore.NONE)
                player.WalkToTarget(tx, ty);
            else if (!player.WalkTo(tx, ty))
                chat.Add(MSG_CANT_REACH, Palette.BONE);
        }

        chat.Input(fbX, fbY);
        panel.Input(fbX, fbY);
    }

    @Override
    public void onUpdate(double delta) {
        while (Timer.Tick()) {
            world.Tick();
            player.Tick();
            mining.Tick();
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
        Sprites.Shutdown();
        Gfx.Shutdown();
        world = null;
        tiles = null;
        player = null;
        chat = null;
        panel = null;
        inventory = null;
        skills = null;
        mining = null;
        smallFont = boldFont = titleFont = null;
    }

    @Override
    public boolean handleEvent(Event e) { return false; }

    /* Outlines the tile under the mouse and names what is on it. */
    private void DrawHover() {
        int tx = TileUnderMouseX(), ty = TileUnderMouseY();
        boolean solid = world.Solid(tx, ty);
        boolean mineable = world.OreAt(tx, ty) != Ore.NONE &&
                !world.RockEmpty(tx, ty);
        String name = HoverText(tx, ty, mineable);

        Gfx.Outline(viewPortX + tx * TILE - viewX,
                viewPortY + ty * TILE - viewY, TILE, TILE,
                mineable ? Palette.MINT :
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

    private String HoverText(int tx, int ty, boolean mineable) {
        int tile = world.Contains(tx, ty) ? ty * world.w + tx : NOWHERE;

        if (tile != hoverTile || world.Version() != hoverVersion) {
            String name = world.Name(tx, ty);
            hoverTile = tile;
            hoverVersion = world.Version();
            hoverText = mineable ? HOVER_MINE + name : name;
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
