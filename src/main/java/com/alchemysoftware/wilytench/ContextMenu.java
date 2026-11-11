package com.alchemysoftware.wilytench;

/**
 * The list of things that can be done to whatever is under the mouse,
 * and the right-click menu that shows it.
 *
 * The list is rebuilt, with Begin() and Add(), whenever the mouse is
 * over something new. Its first entry is what a left click does, and
 * what the label at the top of the view announces. A right click opens
 * the list as a menu; from then on the list is left alone until the
 * menu closes, so that what the menu offers cannot change under the
 * player's finger.
 *
 * The menu is drawn from nothing but filled rectangles, outlines, and
 * text. It opens with its heading under the pointer, centered on it
 * sideways, pushed back inside the picture if it would overhang an
 * edge. The option under the pointer is lit. A left click on an option
 * chooses it; a click anywhere else, or moving the pointer well away,
 * closes the menu with nothing done.
 *
 * Each option is a verb and, usually, what it applies to, drawn in two
 * colors: "Mine" and "Copper rocks". What an option does is for
 * whoever built the list to say: each carries an action number and two
 * values of that builder's choosing.
 */
final class ContextMenu {
    static final int NO_OPTION = -1;

    private static final int MAX_OPTIONS = 12;

    private static final String HEADING = "Choose Option";

    private static final int PAD_X = 4;
    private static final int ROW_GAP = 2;
    private static final int HEADING_PAD_Y = 2;
    /* How far outside the menu the pointer may stray before it closes. */
    private static final int STRAY = 10;

    private final Font font;
    private final int screenW, screenH;
    private final int rowH, headingH, spaceW;

    private final String[] verbs = new String[MAX_OPTIONS];
    private final String[] subjects = new String[MAX_OPTIONS];
    private final int[] subjectColors = new int[MAX_OPTIONS];
    private final int[] actions = new int[MAX_OPTIONS];
    private final int[] argsA = new int[MAX_OPTIONS];
    private final int[] argsB = new int[MAX_OPTIONS];
    private int count;

    private boolean open;
    private int x, y, w, h;

    /**
     * @param font the font for the heading and options
     * @param screenW the width of the picture, which the menu stays in
     * @param screenH its height
     */
    ContextMenu(Font font, int screenW, int screenH) {
        this.font = font;
        this.screenW = screenW;
        this.screenH = screenH;
        rowH = font.lineH + ROW_GAP;
        headingH = font.lineH + 2 * HEADING_PAD_Y;
        spaceW = font.Width(" ", 0, 1);
    }

    /** Empties the list. Ignored while the menu is open. */
    void Begin() {
        if (!open) count = 0;
    }

    /**
     * Adds an option to the end of the list. Ignored while the menu is
     * open, or if the list is full.
     *
     * @param verb what the option does: "Mine", "Walk here"
     * @param subject what to, or null: "Copper rocks"
     * @param subjectColor the palette index to write the subject in
     * @param action a number meaning something to the caller
     */
    void Add(String verb, String subject, int subjectColor, int action,
             int argA, int argB) {
        if (open || count == MAX_OPTIONS) return;

        verbs[count] = verb;
        subjects[count] = subject;
        subjectColors[count] = subjectColor;
        actions[count] = action;
        argsA[count] = argA;
        argsB[count] = argB;
        count++;
    }

    /** @return how many options the list holds */
    int Count() { return count; }

    String Verb(int option) { return verbs[option]; }

    /** @return what the option applies to, or null */
    String Subject(int option) { return subjects[option]; }

    int SubjectColor(int option) { return subjectColors[option]; }

    int Action(int option) { return actions[option]; }

    int ArgA(int option) { return argsA[option]; }

    int ArgB(int option) { return argsB[option]; }

    /**
     * Opens the list as a menu at the pointer. Does nothing if the
     * list is empty.
     */
    void Open(int mouseX, int mouseY) {
        if (count == 0) return;

        w = font.Width(HEADING, 0, HEADING.length());
        for (int i = 0; i < count; i++) w = Math.max(w, OptionWidth(i));
        w += 2 * PAD_X + 2;
        h = headingH + count * rowH + ROW_GAP + 2;

        /* The pointer lands in the heading, so that opening a menu
           never puts an option under a finger still on the button. */
        x = Math.max(0, Math.min(mouseX - w / 2, screenW - w));
        y = Math.max(0, Math.min(mouseY - headingH / 2, screenH - h));
        open = true;
    }

    void Close() { open = false; }

    boolean IsOpen() { return open; }

    /**
     * @return the option under a point of the open menu, or NO_OPTION
     */
    int OptionAt(int px, int py) {
        int top = y + 1 + headingH + ROW_GAP / 2;

        if (!open || px <= x || px >= x + w - 1 || py < top)
            return NO_OPTION;
        int option = (py - top) / rowH;
        return option < count ? option : NO_OPTION;
    }

    /**
     * @return whether a point is on the open menu or within a short
     *         way of it; a pointer that is not has wandered off
     */
    boolean Near(int px, int py) {
        return px >= x - STRAY && px < x + w + STRAY &&
                py >= y - STRAY && py < y + h + STRAY;
    }

    /** Draws the open menu over everything else. */
    void Draw(int mouseX, int mouseY) {
        if (!open) return;

        int lit = OptionAt(mouseX, mouseY);
        int textX = x + 1 + PAD_X;
        int rowY = y + 1 + headingH + ROW_GAP / 2;

        Gfx.ClearClip();
        Gfx.FillRect(x, y, w, h, Palette.INK);
        Gfx.FillRect(x + 1, y + 1, w - 2, headingH, Palette.SLATE);
        Gfx.Outline(x, y, w, h, Palette.ASH);
        Text.Draw(font, HEADING, 0, HEADING.length(), textX,
                y + 1 + HEADING_PAD_Y, Palette.INK);

        for (int i = 0; i < count; i++, rowY += rowH) {
            if (i == lit)
                Gfx.FillRect(x + 1, rowY, w - 2, rowH, Palette.NIGHT);

            int after = Text.Draw(font, verbs[i], 0, verbs[i].length(),
                    textX, rowY + ROW_GAP / 2,
                    i == lit ? Palette.AMBER : Palette.BONE);
            if (subjects[i] != null) {
                Text.Draw(font, subjects[i], 0, subjects[i].length(),
                        after + spaceW, rowY + ROW_GAP / 2,
                        subjectColors[i]);
            }
        }
    }

    private int OptionWidth(int i) {
        int width = font.Width(verbs[i], 0, verbs[i].length());
        if (subjects[i] != null) {
            width += spaceW +
                    font.Width(subjects[i], 0, subjects[i].length());
        }
        return width;
    }
}
