package com.alchemysoftware.wilytench;

/**
 * The window that asks what to make: one of the lists in Recipes,
 * drawn in the middle of the view of the world.
 *
 * Every list is drawn the same way, from the list alone, so a new
 * list, a new recipe, or a new picture for an item needs nothing
 * here. Each recipe is a row: the item's icon and name, the level it
 * needs, and how many of the material it uses up, shown as a number
 * beside the material's icon. Long lists run on into further columns.
 *
 * What the player lacks is written in red, and the name of a recipe
 * that cannot be made just now is dimmed. Such a row can still be
 * clicked; saying why not is the script's business.
 *
 * The window only draws and says what is under the pointer. Session
 * knows which list is open, and WorldScene passes the clicks on.
 */
final class MakeMenu {
    static final int NO_RECIPE = -1;

    private static final int ROWS_PER_COLUMN = 10;
    private static final int ROW_H = Sheet.CELL + 2;
    private static final int PAD = 4;
    private static final int ICON_GAP = 4;
    /* Between a recipe's name, its level, and its cost. */
    private static final int FIELD_GAP = 10;
    private static final int COUNT_GAP = 2;
    private static final int COLUMN_GAP = 8;
    private static final int HEADING_PAD_Y = 2;

    private final Font font;
    private final Session session;
    private final int areaX, areaY, areaW, areaH;
    private final int headingH;

    /* The layout of the list last given to Layout(). */
    private int list = Recipes.NONE;
    private int x, y, w, h;
    private int rows, columns, columnW, nameW, levelW, countW;

    /**
     * @param areaX the left edge of the rectangle to center the window
     *              in, in picture pixels
     */
    MakeMenu(Font font, Session session, int areaX, int areaY, int areaW,
             int areaH) {
        this.font = font;
        this.session = session;
        this.areaX = areaX;
        this.areaY = areaY;
        this.areaW = areaW;
        this.areaH = areaH;
        headingH = font.lineH + 2 * HEADING_PAD_Y;
    }

    /**
     * Works out where everything goes for a list. Must be called
     * before the other methods, and again when the list changes.
     */
    void Layout(int list) {
        if (list == this.list) return;
        this.list = list;

        int n = Recipes.Count(list);
        String title = Recipes.Title(list);

        nameW = levelW = countW = 0;
        for (int i = 0; i < n; i++) {
            String name = Item.Name(Recipes.Product(list, i));
            String level = Recipes.LevelText(list, i);
            String count = Recipes.UsesText(list, i);

            nameW = Math.max(nameW, font.Width(name, 0, name.length()));
            levelW = Math.max(levelW, font.Width(level, 0, level.length()));
            countW = Math.max(countW, font.Width(count, 0, count.length()));
        }

        rows = Math.min(n, ROWS_PER_COLUMN);
        columns = (n + ROWS_PER_COLUMN - 1) / ROWS_PER_COLUMN;
        columnW = PAD + Sheet.CELL + ICON_GAP + nameW + FIELD_GAP + levelW +
                FIELD_GAP + countW + COUNT_GAP + Sheet.CELL + PAD;

        w = Math.max(columns * columnW + (columns - 1) * COLUMN_GAP,
                font.Width(title, 0, title.length()) + 2 * PAD) + 2;
        h = headingH + rows * ROW_H + 2 + 2;
        x = areaX + (areaW - w) / 2;
        y = areaY + (areaH - h) / 2;
    }

    /** @return whether a point is on the window */
    boolean Contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** @return the recipe under a point, or NO_RECIPE */
    int RecipeAt(int px, int py) {
        int gx = px - (x + 1), gy = py - RowsY();
        if (gx < 0 || gy < 0 || gy >= rows * ROW_H) return NO_RECIPE;

        int column = gx / (columnW + COLUMN_GAP);
        if (column >= columns || gx % (columnW + COLUMN_GAP) >= columnW)
            return NO_RECIPE;
        int recipe = column * ROWS_PER_COLUMN + gy / ROW_H;
        return recipe < Recipes.Count(list) ? recipe : NO_RECIPE;
    }

    /** @return the left edge of a recipe's row */
    int RowX(int recipe) {
        return x + 1 + recipe / ROWS_PER_COLUMN * (columnW + COLUMN_GAP);
    }

    /** @return the top edge of a recipe's row */
    int RowY(int recipe) {
        return RowsY() + recipe % ROWS_PER_COLUMN * ROW_H;
    }

    /**
     * @return whether the player has the level and the material for a
     *         recipe of the list
     */
    boolean CanMake(int recipe) {
        return HasLevel(recipe) && HasMaterial(recipe);
    }

    /** Draws the window over the view of the world. */
    void Draw(int mouseX, int mouseY) {
        int lit = RecipeAt(mouseX, mouseY);
        int material = Recipes.Material(list);
        String title = Recipes.Title(list);

        Gfx.ClearClip();
        Gfx.FillRect(x, y, w, h, Palette.INK);
        Gfx.FillRect(x + 1, y + 1, w - 2, headingH, Palette.SLATE);
        Gfx.Outline(x, y, w, h, Palette.ASH);
        Text.Draw(font, title, 0, title.length(), x + 1 + PAD,
                y + 1 + HEADING_PAD_Y, Palette.INK);

        for (int i = 0; i < Recipes.Count(list); i++) {
            int rx = RowX(i), ry = RowY(i);
            int textY = ry + (ROW_H - font.lineH) / 2;
            int item = Recipes.Product(list, i);
            String name = Item.Name(item);
            String level = Recipes.LevelText(list, i);
            String count = Recipes.UsesText(list, i);

            if (i == lit)
                Gfx.FillRect(rx, ry, columnW, ROW_H, Palette.NIGHT);

            int at = rx + PAD;
            Item.DrawIcon(item, at, ry + 1);
            at += Sheet.CELL + ICON_GAP;
            Text.Draw(font, name, 0, name.length(), at, textY,
                    !CanMake(i) ? Palette.ASH :
                    i == lit ? Palette.AMBER : Palette.BONE);

            at += nameW + FIELD_GAP + levelW;
            Text.Draw(font, level, 0, level.length(),
                    at - font.Width(level, 0, level.length()), textY,
                    HasLevel(i) ? Palette.ASH : Palette.RUST);

            at += FIELD_GAP + countW;
            Text.Draw(font, count, 0, count.length(),
                    at - font.Width(count, 0, count.length()), textY,
                    HasMaterial(i) ? Palette.ASH : Palette.RUST);
            Item.DrawIcon(material, at + COUNT_GAP, ry + 1);
        }
    }

    private int RowsY() { return y + 1 + headingH + 1; }

    private boolean HasLevel(int recipe) {
        return session.skills.Level(Recipes.Skill(list)) >=
                Recipes.Level(list, recipe);
    }

    private boolean HasMaterial(int recipe) {
        return session.inventory.Count(Recipes.Material(list)) >=
                Recipes.Uses(list, recipe);
    }
}
