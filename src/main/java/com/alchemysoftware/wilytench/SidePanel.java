package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.input.Button;
import com.alchemysoftware.whitetail.input.InputSys;

/**
 * The interface panel to the right of the game view: the player's
 * skills and inventory.
 *
 * Each skill shows its level and experience so far against experience
 * needed for the next. The inventory is one plain box holding items in
 * four columns. Pointing at an item names it; right-clicking drops it,
 * and for now a dropped item is simply gone.
 */
final class SidePanel {
    private static final int MARGIN = 10;
    private static final int TITLE_Y = 6;

    private static final int SKILLS_Y = 30;
    private static final int XP_TEXT_DY = 12;

    private static final int INVENTORY_Y = 64;
    private static final int GRID_DY = 14;
    private static final int COLS = 4;
    /* Each item has a SLOT-sized square of the box to itself. */
    private static final int SLOT = 26;
    private static final int HOVER_GAP = 4;

    private static final int NO_SLOT = -1;
    private static final int STALE = -1;

    private static final String TITLE = Screen.TITLE;
    private static final String INVENTORY_HEADING = "Inventory";
    private static final String DROP_HINT = "Right-click to drop";

    private final Font small, bold, title;
    private final int x, y, w, h;
    private final int gridX, gridY, rows;
    private final Inventory inventory;
    private final Skills skills;
    private final ChatBox chat;

    private int hoverSlot = NO_SLOT;

    /* Rebuilt only when the numbers behind them change, so that
       drawing the panel every frame creates no strings. */
    private int shownXp = STALE, shownUsed = STALE;
    private String levelText, xpText, usedText;

    SidePanel(Font small, Font bold, Font title, int x, int y, int w, int h,
              Inventory inventory, Skills skills, ChatBox chat) {
        this.small = small;
        this.bold = bold;
        this.title = title;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.inventory = inventory;
        this.skills = skills;
        this.chat = chat;

        rows = Inventory.SLOTS / COLS;
        gridX = x + (w - COLS * SLOT) / 2;
        gridY = y + INVENTORY_Y + GRID_DY;
    }

    /**
     * @param mouseX the pointer in picture pixels, or negative if it
     *               is outside the picture
     */
    void Input(int mouseX, int mouseY) {
        hoverSlot = SlotAt(mouseX, mouseY);
        if (hoverSlot == NO_SLOT ||
                inventory.At(hoverSlot) == Item.NONE) return;

        if (InputSys.WasPressed(Button.MOUSE_RIGHT)) {
            int item = inventory.Remove(hoverSlot);
            chat.Add("You drop the " + Item.LowerName(item) + ".",
                    Palette.BONE);
        }
    }

    void Draw() {
        Refresh();

        Gfx.SetClip(x, y, w, h);
        Gfx.FillRect(x, y, w, h, Palette.NIGHT);
        Gfx.FillRect(x, y, 1, h, Palette.SLATE);

        Text.DrawShadowed(title, TITLE, 0, TITLE.length(),
                x + (w - title.Width(TITLE, 0, TITLE.length())) / 2,
                y + TITLE_Y, Palette.AMBER, Palette.INK);

        DrawSkill(Skills.MINING, y + SKILLS_Y);
        DrawInventory();
        Gfx.ClearClip();
    }

    private void DrawSkill(int skill, int top) {
        String name = Skills.Name(skill);
        int left = x + MARGIN, right = x + w - MARGIN;

        Text.Draw(bold, name, 0, name.length(), left, top, Palette.BONE);
        Text.Draw(bold, levelText, 0, levelText.length(),
                right - bold.Width(levelText, 0, levelText.length()), top,
                Palette.AMBER);

        Text.Draw(small, xpText, 0, xpText.length(), left, top + XP_TEXT_DY,
                Palette.ASH);
    }

    private void DrawInventory() {
        int left = x + MARGIN, right = x + w - MARGIN, top = y + INVENTORY_Y;

        Text.Draw(bold, INVENTORY_HEADING, 0, INVENTORY_HEADING.length(),
                left, top, Palette.BONE);
        Text.Draw(small, usedText, 0, usedText.length(),
                right - small.Width(usedText, 0, usedText.length()), top,
                Palette.ASH);

        Gfx.FillRect(gridX, gridY, COLS * SLOT, rows * SLOT, Palette.INK);
        Gfx.Outline(gridX - 1, gridY - 1, COLS * SLOT + 2, rows * SLOT + 2,
                Palette.SLATE);

        for (int slot = 0; slot < Inventory.SLOTS; slot++) {
            int sx = gridX + slot % COLS * SLOT;
            int sy = gridY + slot / COLS * SLOT;
            int item = inventory.At(slot);
            if (item == Item.NONE) continue;

            if (slot == hoverSlot)
                Gfx.Outline(sx + 1, sy + 1, SLOT - 2, SLOT - 2, Palette.ASH);
            Item.DrawIcon(item, sx + (SLOT - Sheet.CELL) / 2,
                    sy + (SLOT - Sheet.CELL) / 2);
        }

        if (hoverSlot == NO_SLOT || inventory.At(hoverSlot) == Item.NONE)
            return;

        String name = Item.Name(inventory.At(hoverSlot));
        int textY = gridY + rows * SLOT + HOVER_GAP;
        Text.Draw(small, name, 0, name.length(), left, textY, Palette.MINT);
        Text.Draw(small, DROP_HINT, 0, DROP_HINT.length(), left,
                textY + small.lineH + 1, Palette.ASH);
    }

    private void Refresh() {
        int xp = skills.XpTenths(Skills.MINING);
        if (xp != shownXp) {
            int level = skills.Level(Skills.MINING);
            shownXp = xp;
            levelText = "Level " + level;

            if (level == Skills.MAX_LEVEL) {
                xpText = Skills.Format(xp) + " xp";
            } else {
                xpText = Skills.Format(xp) + " / " +
                        Skills.XpForLevel(level + 1) + " xp";
            }
        }

        if (inventory.Used() != shownUsed) {
            shownUsed = inventory.Used();
            usedText = shownUsed + " / " + Inventory.SLOTS;
        }
    }

    private int SlotAt(int mouseX, int mouseY) {
        int gx = mouseX - gridX, gy = mouseY - gridY;
        if (mouseX < 0 || gx < 0 || gy < 0) return NO_SLOT;

        int col = gx / SLOT, row = gy / SLOT;
        return col < COLS && row < rows ? row * COLS + col : NO_SLOT;
    }
}
