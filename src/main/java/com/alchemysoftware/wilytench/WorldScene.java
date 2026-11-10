package com.alchemysoftware.wilytench;

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
 * The game world: the map, the player's character, and the view of
 * them.
 *
 * Left click walks the character to the clicked tile, or as close as it
 * can get. The view stays centered on the character. Escape closes the
 * game.
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

    private byte[] buf;
    private int w, h;
    private int viewPortX, viewPortY, viewW, viewH;

    private boolean spritesInit;
    private World world;
    private TileView tiles;
    private Player player;

    /* The map pixel at the top left of the viewport, as last drawn. */
    private int viewX, viewY;

    /* The mouse, in viewport pixels, or NOWHERE when outside it. */
    private int mouseX = NOWHERE, mouseY = NOWHERE;

    private double msCarry;
    private int animMs;

    @Override
    public void onInit() {
        DisplayLayout layout = Screen.GetLayout();

        buf = Screen.GetIndexBuf();
        w = Screen.GetW();
        h = Screen.GetH();
        viewPortX = layout.viewportX;
        viewPortY = layout.viewportY;
        viewW = layout.viewportWidth;
        viewH = layout.viewportHeight;

        if ((world = World.Load(MAP)) == null) return;

        if (!SpriteRenderer.Init(buf, w, h, viewPortX, viewPortY, viewW,
                viewH, TileView.SpritesNeeded(viewW, viewH) +
                SPARE_SPRITES)) return;
        spritesInit = true;

        if (!Sprites.Install()) return;

        tiles = TileView.Create(world, viewW, viewH);
        player = Player.Create(world);
        if (tiles == null || player == null)
            ErrorHandler.LogFatalAndStop(ERR_STR_SPRITES);
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

        if (mouseX != NOWHERE && InputSys.WasPressed(Button.MOUSE_LEFT))
            player.WalkTo(TileUnderMouseX(), TileUnderMouseY());
    }

    @Override
    public void onUpdate(double delta) {
        while (Timer.Tick()) player.Tick();

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
        int clipX1 = viewPortX + viewW, clipY1 = viewPortY + viewH;

        SpriteRenderer.Clear(Palette.BACKGROUND);
        SpriteRenderer.Render(viewX, viewY);

        if (player.Walking()) {
            Gfx.Corners(buf, w,
                    viewPortX + player.DestX() * TILE - viewX,
                    viewPortY + player.DestY() * TILE - viewY,
                    TILE, TILE, MARKER_ARM, Palette.AMBER,
                    viewPortX, viewPortY, clipX1, clipY1);
        }

        if (mouseX != NOWHERE) {
            int tx = TileUnderMouseX(), ty = TileUnderMouseY();
            Gfx.Outline(buf, w,
                    viewPortX + tx * TILE - viewX,
                    viewPortY + ty * TILE - viewY, TILE, TILE,
                    world.Solid(tx, ty) ? Palette.RUST : Palette.BONE,
                    viewPortX, viewPortY, clipX1, clipY1);
        }

        /* The interface panel: empty for now. */
        Gfx.FillRect(buf, w, clipX1, 0, w - clipX1, h, Palette.NIGHT,
                0, 0, w, h);
        Gfx.FillRect(buf, w, clipX1, 0, 1, h, Palette.SLATE, 0, 0, w, h);
    }

    @Override
    public void onDestroy() {
        if (spritesInit) SpriteRenderer.Shutdown();
        spritesInit = false;
        world = null;
        tiles = null;
        player = null;
        buf = null;
    }

    @Override
    public boolean handleEvent(Event e) { return false; }

    private int TileUnderMouseX() {
        return TileView.FloorDiv(viewX + mouseX, TILE);
    }

    private int TileUnderMouseY() {
        return TileView.FloorDiv(viewY + mouseY, TILE);
    }

    private static final String ERR_STR_SPRITES = "WorldScene failed to " +
            "initialize because the sprite renderer ran out of sprites.\n";
}
