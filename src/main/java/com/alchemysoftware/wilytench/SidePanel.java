package com.alchemysoftware.wilytench;

/**
 * The interface panel to the right of the game view.
 *
 * Most of it is one box, and a row of buttons above the box chooses
 * what the box shows: the inventory, or the skills. The buttons are
 * drawn as tabs, the chosen one joined to the box. Their pictures come
 * from data/interface.json.
 *
 * The inventory is items in four columns. Pointing at an item names
 * it. What clicking an item does is decided elsewhere; the panel only
 * says which slot the pointer is over.
 *
 * Each skill has a small rectangle holding its icon and its level as
 * it stands over its level as earned, "1/1". Pointing at one brings up
 * a tooltip with the experience earned and the experience at which
 * the next level comes.
 */
final class SidePanel {
    static final int TAB_INVENTORY = 0;
    static final int TAB_SKILLS = 1;
    static final int TABS = 2;

    static final int NO_SLOT = -1;
    static final int NO_SKILL = -1;
    static final int NO_TAB = -1;

    /* What interface.json calls each tab. */
    private static final String[] TAB_IDS = { "inventory", "skills" };
    private static final String[] TAB_KEYS = {
        "id", "name", "sheet", "cell"
    };

    private static final int MARGIN = 10;
    private static final int TITLE_Y = 6;
    private static final int HEADING_Y = 28;

    private static final int TABS_Y = 44;
    private static final int TAB_H = 22;
    /* How much lower the tabs not chosen stand. */
    private static final int TAB_DROP = 2;

    private static final int COLS = 4;
    /* Each item has a SLOT-sized square of the box to itself. */
    private static final int SLOT = 26;
    /* A tab is as wide as a slot, so the two line up. */
    private static final int TAB_W = SLOT;
    private static final int HOVER_GAP = 4;

    /* A skill has the room of two slots side by side. */
    private static final int SKILL_COLS = 2;
    private static final int SKILL_W = 2 * SLOT, SKILL_H = SLOT;
    private static final int SKILL_PAD = 5;

    private static final int TIP_LINES = 2;
    private static final int STALE = -1;

    private static final String TITLE = Screen.TITLE;
    private static final String MENU_HINT = "Right-click for options";

    private static String[] tabNames;
    private static int[] tabPictures;

    private final Font small, bold, title;
    private final int x, y, w, h;
    private final int screenW, screenH;
    private final int gridX, gridY, rows;
    private final Inventory inventory;
    private final Skills skills;

    private int tab = TAB_INVENTORY;
    private int pointerX = -1, pointerY = -1;
    private int hoverTab = NO_TAB, hoverSlot = NO_SLOT;
    private int hoverSkill = NO_SKILL;
    private int selectedSlot = NO_SLOT;

    /* Rebuilt only when the numbers behind them change, so that
       drawing the panel every frame creates no strings. */
    private int shownUsed = STALE;
    private String usedText, totalText;
    private final int[] shownXp;
    private final String[] levelTexts;
    private final String[][] tipLines;
    private final int[] tipCounts;

    /**
     * @param root the contents of data/interface.json; Sprites must
     *             already be loaded
     */
    static void Load(DataNode root) {
        root.Only(new String[] { "tabs" });

        DataNode[] nodes = root.Groups("tabs", "tab");
        String[] names = new String[TABS];
        int[] pictures = new int[TABS];

        for (int i = 0; i < nodes.length; i++) {
            String id = nodes[i].Str("id");
            DataNode node = nodes[i].Called("interface.json, tab [" + id +
                    "]");
            int which = NO_TAB;

            node.Only(TAB_KEYS);
            for (int j = 0; j < TABS; j++)
                if (TAB_IDS[j].equals(id)) which = j;
            if (which == NO_TAB) {
                throw node.Fail("the game has no tab with this id. Its " +
                        "tabs are [inventory] and [skills].");
            }
            if (names[which] != null)
                throw node.Fail("there are two tabs with this id.");

            names[which] = node.Str("name");
            pictures[which] = Sprites.Picture(node, node.Str("sheet"),
                    node.Ints("cell", 2));
        }
        for (int j = 0; j < TABS; j++) {
            if (names[j] == null) {
                throw root.Fail("[tabs] has no tab with the id [" +
                        TAB_IDS[j] + "].");
            }
        }
        tabNames = names;
        tabPictures = pictures;
    }

    static void Shutdown() {
        tabNames = null;
        tabPictures = null;
    }

    /**
     * @param x the panel's left edge in picture pixels
     * @param screenW the width of the whole picture, which tooltips
     *                stay inside
     */
    SidePanel(Font small, Font bold, Font title, int x, int y, int w, int h,
              int screenW, int screenH, Inventory inventory,
              Skills skills) {
        this.small = small;
        this.bold = bold;
        this.title = title;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.screenW = screenW;
        this.screenH = screenH;
        this.inventory = inventory;
        this.skills = skills;

        rows = Inventory.SLOTS / COLS;
        gridX = x + (w - COLS * SLOT) / 2;
        gridY = y + TABS_Y + TAB_H + 1;

        int n = Skills.Count();
        shownXp = new int[n];
        levelTexts = new String[n];
        tipLines = new String[n][TIP_LINES];
        tipCounts = new int[n];
        for (int i = 0; i < n; i++) shownXp[i] = STALE;
    }

    /**
     * Notes where the pointer is.
     *
     * @param mouseX the pointer in picture pixels, or negative if it
     *               is outside the picture
     */
    void Input(int mouseX, int mouseY) {
        pointerX = mouseX;
        pointerY = mouseY;
        hoverTab = TabAt(mouseX, mouseY);
        hoverSlot = tab == TAB_INVENTORY ? SlotAt(mouseX, mouseY) : NO_SLOT;
        hoverSkill = tab == TAB_SKILLS ? SkillAt(mouseX, mouseY) : NO_SKILL;
    }

    /**
     * Takes a left click where the pointer was last noted.
     *
     * @return whether the click was on a tab, and so is used up
     */
    boolean Click() {
        if (hoverTab == NO_TAB) return false;

        tab = hoverTab;
        hoverSlot = NO_SLOT;
        hoverSkill = NO_SKILL;
        return true;
    }

    /**
     * Marks an inventory slot as holding the item the player has
     * chosen to use on something.
     *
     * @param slot the slot, or NO_SLOT for none
     */
    void Select(int slot) { selectedSlot = slot; }

    /** @return the tab the box is showing */
    int Tab() { return tab; }

    /**
     * @return the inventory slot under the pointer if it holds an
     *         item, or NO_SLOT
     */
    int HoverSlot() {
        return hoverSlot != NO_SLOT &&
                inventory.At(hoverSlot) != Item.NONE ? hoverSlot : NO_SLOT;
    }

    /** @return the skill under the pointer, or NO_SKILL */
    int HoverSkill() { return hoverSkill; }

    /** @return the left edge of a tab's button */
    int TabX(int which) { return gridX - 1 + which * TAB_W; }

    /** @return the top edge of the tab buttons */
    int TabY() { return y + TABS_Y; }

    /** @return the left edge of an inventory slot's square */
    int SlotX(int slot) { return gridX + slot % COLS * SLOT; }

    int SlotY(int slot) { return gridY + slot / COLS * SLOT; }

    /** @return the left edge of a skill's rectangle */
    int SkillX(int skill) { return gridX + skill % SKILL_COLS * SKILL_W; }

    int SkillY(int skill) { return gridY + skill / SKILL_COLS * SKILL_H; }

    /**
     * @return the heading of the tooltip for what the pointer is
     *         over, or null if that has no tooltip
     */
    String TipHeading() {
        if (hoverTab != NO_TAB) return tabNames[hoverTab];
        if (hoverSkill != NO_SKILL) return Skills.Name(hoverSkill);
        return null;
    }

    /** @return how many lines that tooltip has under its heading */
    int TipLines() {
        Refresh();
        return hoverTab == NO_TAB && hoverSkill != NO_SKILL ?
                tipCounts[hoverSkill] : 0;
    }

    /** @return one of those lines */
    String TipLine(int line) {
        Refresh();
        return tipLines[hoverSkill][line];
    }

    void Draw() {
        Refresh();

        Gfx.SetClip(x, y, w, h);
        Gfx.FillRect(x, y, w, h, Palette.NIGHT);
        Gfx.FillRect(x, y, 1, h, Palette.SLATE);

        Text.DrawShadowed(title, TITLE, 0, TITLE.length(),
                x + (w - title.Width(TITLE, 0, TITLE.length())) / 2,
                y + TITLE_Y, Palette.AMBER, Palette.INK);

        String heading = tabNames[tab];
        String count = tab == TAB_INVENTORY ? usedText : totalText;
        Text.Draw(bold, heading, 0, heading.length(), x + MARGIN,
                y + HEADING_Y, Palette.BONE);
        Text.Draw(small, count, 0, count.length(),
                x + w - MARGIN - small.Width(count, 0, count.length()),
                y + HEADING_Y, Palette.ASH);

        Gfx.FillRect(gridX, gridY, COLS * SLOT, rows * SLOT, Palette.INK);
        Gfx.Outline(gridX - 1, gridY - 1, COLS * SLOT + 2, rows * SLOT + 2,
                Palette.SLATE);
        for (int i = 0; i < TABS; i++) DrawTab(i);

        if (tab == TAB_INVENTORY) DrawInventory();
        else DrawSkills();
        Gfx.ClearClip();
    }

    /**
     * Draws the tooltip for what the pointer is over, if that has one,
     * over everything else.
     */
    void DrawTip() {
        String heading = TipHeading();
        if (heading == null) return;

        Tooltip.Draw(small, heading,
                hoverSkill != NO_SKILL ? tipLines[hoverSkill] : null,
                TipLines(), pointerX, pointerY, screenW, screenH);
    }

    /*
     * The chosen tab stands full height and opens into the box below
     * it; the others stand a little lower and are closed.
     */
    private void DrawTab(int which) {
        int left = TabX(which), top = TabY(), across = TAB_W + 1;
        int iconX = left + (across - Sheet.CELL) / 2;
        int iconY = top + (TAB_H - Sheet.CELL) / 2 + 1;

        if (which == tab) {
            Gfx.FillRect(left, top, across, TAB_H + 1, Palette.INK);
            Gfx.FillRect(left, top, across, 1, Palette.SLATE);
            Gfx.FillRect(left, top, 1, TAB_H + 1, Palette.SLATE);
            Gfx.FillRect(left + across - 1, top, 1, TAB_H + 1,
                    Palette.SLATE);
        } else {
            Gfx.Outline(left, top + TAB_DROP, across, TAB_H - TAB_DROP + 1,
                    which == hoverTab ? Palette.ASH : Palette.SLATE);
            iconY += TAB_DROP / 2;
        }
        Sprites.Draw(tabPictures[which], Sprites.COLOR_MAP_NONE, iconX,
                iconY);
    }

    private void DrawInventory() {
        for (int slot = 0; slot < Inventory.SLOTS; slot++) {
            int sx = SlotX(slot), sy = SlotY(slot);
            int item = inventory.At(slot);
            if (item == Item.NONE) continue;

            if (slot == selectedSlot) {
                Gfx.Outline(sx + 1, sy + 1, SLOT - 2, SLOT - 2,
                        Palette.AMBER);
            } else if (slot == hoverSlot) {
                Gfx.Outline(sx + 1, sy + 1, SLOT - 2, SLOT - 2, Palette.ASH);
            }
            Item.DrawIcon(item, sx + (SLOT - Sheet.CELL) / 2,
                    sy + (SLOT - Sheet.CELL) / 2);
        }

        if (hoverSlot == NO_SLOT || inventory.At(hoverSlot) == Item.NONE)
            return;

        String name = Item.Name(inventory.At(hoverSlot));
        int left = x + MARGIN;
        int textY = gridY + rows * SLOT + HOVER_GAP;
        Text.Draw(small, name, 0, name.length(), left, textY, Palette.MINT);
        Text.Draw(small, MENU_HINT, 0, MENU_HINT.length(), left,
                textY + small.lineH + 1, Palette.ASH);
    }

    private void DrawSkills() {
        for (int skill = 0; skill < Skills.Count(); skill++) {
            int sx = SkillX(skill), sy = SkillY(skill);
            String level = levelTexts[skill];

            Gfx.Outline(sx + 1, sy + 1, SKILL_W - 2, SKILL_H - 2,
                    skill == hoverSkill ? Palette.ASH : Palette.SLATE);
            Skills.DrawIcon(skill, title, sx + SKILL_PAD,
                    sy + (SKILL_H - Sheet.CELL) / 2);
            Text.Draw(small, level, 0, level.length(),
                    sx + SKILL_W - SKILL_PAD -
                    small.Width(level, 0, level.length()),
                    sy + (SKILL_H - small.lineH) / 2, Palette.AMBER);
        }
    }

    private void Refresh() {
        boolean changed = totalText == null;
        int total = 0;

        for (int skill = 0; skill < Skills.Count(); skill++) {
            int xp = skills.XpTenths(skill), level = skills.Level(skill);

            total += level;
            if (xp == shownXp[skill]) continue;
            shownXp[skill] = xp;
            changed = true;

            levelTexts[skill] = skills.CurrentLevel(skill) + "/" + level;
            tipLines[skill][0] = TIP_XP + Skills.Format(xp);
            tipCounts[skill] = 1;
            if (level < Skills.MAX_LEVEL) {
                tipLines[skill][1] = TIP_NEXT +
                        Skills.XpForLevel(level + 1);
                tipCounts[skill] = 2;
            }
        }
        if (changed) totalText = TOTAL + total;

        if (inventory.Used() != shownUsed) {
            shownUsed = inventory.Used();
            usedText = shownUsed + " / " + Inventory.SLOTS;
        }
    }

    private int TabAt(int mouseX, int mouseY) {
        int tx = mouseX - TabX(0), ty = mouseY - TabY();
        if (mouseX < 0 || tx < 0 || ty < 0 || ty >= TAB_H) return NO_TAB;

        return tx / TAB_W < TABS ? tx / TAB_W : NO_TAB;
    }

    private int SlotAt(int mouseX, int mouseY) {
        int gx = mouseX - gridX, gy = mouseY - gridY;
        if (mouseX < 0 || gx < 0 || gy < 0) return NO_SLOT;

        int col = gx / SLOT, row = gy / SLOT;
        return col < COLS && row < rows ? row * COLS + col : NO_SLOT;
    }

    private int SkillAt(int mouseX, int mouseY) {
        int gx = mouseX - gridX, gy = mouseY - gridY;
        if (mouseX < 0 || gx < 0 || gy < 0) return NO_SKILL;

        int col = gx / SKILL_W, row = gy / SKILL_H;
        int skill = row * SKILL_COLS + col;
        return col < SKILL_COLS && skill < Skills.Count() ?
                skill : NO_SKILL;
    }

    private static final String TIP_XP = "Experience: ";
    private static final String TIP_NEXT = "Next level at: ";
    private static final String TOTAL = "Total level ";
}
