package com.alchemysoftware.wilytench;

/**
 * The interface panel to the right of the game view, as the game
 * frame draws it: a row of tabs, the 28 squares of the inventory, and
 * a second row of tabs under them. Layout says where on the frame
 * those are.
 *
 * A tab chooses what the squares show: the inventory, or the skills.
 * There is room for eight tabs, four above and four below, and so far
 * there are two. Their pictures come from data/interface.json. The
 * chosen tab is outlined.
 *
 * The inventory is one item to a square. What clicking an item does is
 * decided elsewhere; the panel only says which slot the pointer is
 * over, and outlines it.
 *
 * Each skill takes two squares side by side: its icon in the first,
 * and in the second its level as it stands over its level as earned,
 * "1/1". Pointing at one brings up a tooltip with the experience
 * earned and the experience at which the next level comes.
 *
 * The space above the tabs is kept for things to come. For now it
 * names the tab that is open and gives a figure for it.
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

    private static final int CELL = Layout.CELL;
    private static final int COLS = Layout.ACROSS;
    /* A skill has two squares side by side. */
    private static final int SKILL_COLS = COLS / 2;

    private static final int HEADING_PAD = 4;
    private static final int TIP_LINES = 2;
    private static final int STALE = -1;

    private static String[] tabNames;
    private static int[] tabPictures;

    private final Font small, bold;
    private final int screenW, screenH;
    private final int rows;
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
     * Layout must be loaded: it says where the panel's parts are.
     *
     * @param small the font for figures
     * @param bold the font for the open tab's name
     * @param screenW the width of the whole picture, which tooltips
     *                stay inside
     */
    SidePanel(Font small, Font bold, int screenW, int screenH,
              Inventory inventory, Skills skills) {
        this.small = small;
        this.bold = bold;
        this.screenW = screenW;
        this.screenH = screenH;
        this.inventory = inventory;
        this.skills = skills;

        rows = Inventory.SLOTS / COLS;

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

    /** @return the left edge of a tab's square */
    int TabX(int which) {
        return (which < COLS ? Layout.topTabsX : Layout.bottomTabsX) +
                which % COLS * Layout.pitch;
    }

    /** @return the top edge of a tab's square */
    int TabY(int which) {
        return which < COLS ? Layout.topTabsY : Layout.bottomTabsY;
    }

    /** @return the left edge of an inventory slot's square */
    int SlotX(int slot) { return Layout.slotsX + slot % COLS * Layout.pitch; }

    int SlotY(int slot) { return Layout.slotsY + slot / COLS * Layout.pitch; }

    /** @return the left edge of the first of a skill's two squares */
    int SkillX(int skill) {
        return Layout.slotsX + skill % SKILL_COLS * 2 * Layout.pitch;
    }

    int SkillY(int skill) {
        return Layout.slotsY + skill / SKILL_COLS * Layout.pitch;
    }

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

    /** Draws over the game frame, which must already be drawn. */
    void Draw() {
        Refresh();
        Gfx.ClearClip();

        /* The frame leaves this space open, for what is to come. */
        Gfx.FillRect(Layout.widgetsX, Layout.widgetsY, Layout.widgetsW,
                Layout.widgetsH, Palette.VOID);
        String heading = tabNames[tab];
        String figure = tab == TAB_INVENTORY ? usedText : totalText;
        Text.Draw(bold, heading, 0, heading.length(),
                Layout.widgetsX + HEADING_PAD, Layout.widgetsY + HEADING_PAD,
                Palette.TEXT_LIGHT);
        Text.Draw(small, figure, 0, figure.length(),
                Layout.widgetsX + HEADING_PAD,
                Layout.widgetsY + HEADING_PAD + bold.lineH + 2,
                Palette.TEXT_LIGHT);

        for (int i = 0; i < TABS; i++) {
            int tx = TabX(i), ty = TabY(i);
            Sprites.Draw(tabPictures[i], Sprites.COLOR_MAP_NONE, tx, ty);
            if (i == tab) Ring(tx, ty, CELL, Palette.CHOSEN);
            else if (i == hoverTab) Ring(tx, ty, CELL, Palette.MARK);
        }

        if (tab == TAB_INVENTORY) DrawInventory();
        else DrawSkills();
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

    private void DrawInventory() {
        for (int slot = 0; slot < Inventory.SLOTS; slot++) {
            int sx = SlotX(slot), sy = SlotY(slot);
            int item = inventory.At(slot);
            if (item == Item.NONE) continue;

            Item.DrawIcon(item, sx, sy);
            if (slot == selectedSlot) Ring(sx, sy, CELL, Palette.CHOSEN);
            else if (slot == hoverSlot) Ring(sx, sy, CELL, Palette.MARK);
        }
    }

    private void DrawSkills() {
        for (int skill = 0; skill < Skills.Count(); skill++) {
            int sx = SkillX(skill), sy = SkillY(skill);
            String level = levelTexts[skill];
            int secondX = sx + Layout.pitch;

            Skills.DrawIcon(skill, bold, sx, sy);
            Text.Draw(small, level, 0, level.length(),
                    secondX + (CELL - small.Width(level, 0, level.length()))
                    / 2, sy + (CELL - small.lineH) / 2, Palette.TEXT_LIGHT);
            if (skill == hoverSkill)
                Ring(sx, sy, Layout.pitch + CELL, Palette.MARK);
        }
    }

    /* Outlines squares of the frame, in the gap around them: w pixels
       of them across, from (x, y). */
    private static void Ring(int x, int y, int w, int color) {
        Gfx.Outline(x - 1, y - 1, w + 2, CELL + 2, color);
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
        for (int i = 0; i < TABS; i++) {
            if (mouseX >= TabX(i) && mouseX < TabX(i) + CELL &&
                    mouseY >= TabY(i) && mouseY < TabY(i) + CELL) return i;
        }
        return NO_TAB;
    }

    /* The slot whose square, or the gap after it, a point is in. */
    private int SlotAt(int mouseX, int mouseY) {
        int gx = mouseX - Layout.slotsX, gy = mouseY - Layout.slotsY;
        if (mouseX < 0 || gx < 0 || gy < 0) return NO_SLOT;

        int col = gx / Layout.pitch, row = gy / Layout.pitch;
        return col < COLS && row < rows ? row * COLS + col : NO_SLOT;
    }

    private int SkillAt(int mouseX, int mouseY) {
        int slot = SlotAt(mouseX, mouseY);
        if (slot == NO_SLOT) return NO_SKILL;

        int skill = slot / COLS * SKILL_COLS + slot % COLS / 2;
        return skill < Skills.Count() ? skill : NO_SKILL;
    }

    private static final String TIP_XP = "Experience: ";
    private static final String TIP_NEXT = "Next level at: ";
    private static final String TOTAL = "Total level ";
}
