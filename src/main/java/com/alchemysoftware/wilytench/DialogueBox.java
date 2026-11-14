package com.alchemysoftware.wilytench;

/**
 * Talk between the player's character and someone else, shown where
 * the chat box is while it lasts: plain writing on the game frame's
 * parchment.
 *
 * There are two things it shows. Speech is a few lines of what
 * somebody says, under their name, beside their portrait: the other
 * person on the left with their words set to the left, the player on
 * the right with theirs set to the right, and "Click here to continue"
 * underneath. A choice is up to four things the player might say,
 * centered one under another, the one under the pointer lit.
 *
 * There are no portraits yet, so each is a square with the speaker's
 * name in it, cut short if it is too long.
 *
 * The box only draws and says what is under the pointer. Session holds
 * the talk itself, which scripts drive with the chat... and choice...
 * commands, and WorldScene passes the clicks on.
 */
final class DialogueBox {
    static final int NO_OPTION = -1;

    private static final int PAD = 8;
    private static final int PORTRAIT = 32;
    private static final int PORTRAIT_PAD = 2;
    private static final int NAME_Y = 1;
    /* Lines are packed a pixel closer than the font's line height, so
       that a name, four lines, and the way on fit the parchment. */
    private static final int LINE_GAP = -1;
    private static final int MAX_LINES = 4;
    private static final int CONTINUE_PAD = 1, CONTINUE_REACH = 2;
    private static final int HEADING_Y = 2;

    private static final String CONTINUE = "Click here to continue";
    private static final String HEADING = "Select an Option";

    private final Font font, bold;
    private final Session session;
    private final int x, y, w, h;
    private final int rowH;

    /**
     * @param font the font for what is said
     * @param bold the font for names and headings
     * @param x the left edge of the box, in picture pixels
     */
    DialogueBox(Font font, Font bold, Session session, int x, int y, int w,
                int h) {
        this.font = font;
        this.bold = bold;
        this.session = session;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        rowH = font.lineH + LINE_GAP;
    }

    /** @return whether a point is on the box */
    boolean Contains(int px, int py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /**
     * @return whether a point is on "Click here to continue", or
     *         within a pixel or two of it: the only part of the box
     *         that a click reads on from
     */
    boolean OnContinue(int px, int py) {
        int width = font.Width(CONTINUE, 0, CONTINUE.length());
        int left = x + (w - width) / 2;
        int top = y + h - CONTINUE_PAD - font.lineH;

        return px >= left - CONTINUE_REACH &&
                px < left + width + CONTINUE_REACH &&
                py >= top - CONTINUE_REACH && py < y + h;
    }

    /**
     * @return which of the choices on show a point is on, counting
     *         from zero, or NO_OPTION
     */
    int OptionAt(int px, int py) {
        int n = session.TalkOptions();
        if (session.TalkKind() != Session.TALK_CHOICE || !Contains(px, py))
            return NO_OPTION;

        for (int i = 0; i < n; i++) {
            String option = session.TalkOption(i);
            int width = font.Width(option, 0, option.length());
            int left = x + (w - width) / 2, top = OptionY(i);

            if (px >= left - PAD && px < left + width + PAD && py >= top &&
                    py < top + rowH) return i;
        }
        return NO_OPTION;
    }

    /** @return the top of the row a choice is written on */
    int OptionY(int option) {
        int top = y + HEADING_Y + bold.lineH + LINE_GAP;
        int room = y + h - PAD - top;
        int n = session.TalkOptions();

        /* Spread evenly over the room there is, and centered in it. */
        int step = Math.max(rowH, Math.min(room / n, 2 * rowH));
        return top + (room - n * step) / 2 + option * step +
                (step - rowH) / 2;
    }

    /** @return the middle of the box, left to right */
    int MiddleX() { return x + w / 2; }

    /** Draws the talk in progress. */
    void Draw(int mouseX, int mouseY) {
        /* The background is the frame's parchment, already drawn. */
        Gfx.SetClip(x, y, w, h);

        if (session.TalkKind() == Session.TALK_CHOICE)
            DrawChoice(mouseX, mouseY);
        else DrawSpeech(mouseX, mouseY);
        Gfx.ClearClip();
    }

    private void DrawSpeech(int mouseX, int mouseY) {
        boolean player = session.TalkKind() == Session.TALK_PLAYER;
        String name = session.TalkSpeaker(), text = session.TalkText();
        int textW = w - 3 * PAD - PORTRAIT;
        int textX = player ? x + PAD : x + 2 * PAD + PORTRAIT;
        int lineY = y + NAME_Y;

        DrawPortrait(name, player ? x + w - PAD - PORTRAIT : x + PAD,
                y + (h - PORTRAIT) / 2);

        Text.Draw(bold, name, 0, name.length(),
                Aligned(bold, name, 0, name.length(), textX, textW, player),
                lineY, Palette.TEXT_NEWS);
        lineY += bold.lineH + LINE_GAP;

        for (int start = 0, line = 0; start < text.length() &&
                line < MAX_LINES; line++, lineY += rowH) {
            int end = font.Fit(text, start, textW);
            int shown = end;

            /* The space a line was broken at is not part of it. */
            while (shown > start && text.charAt(shown - 1) == ' ') shown--;
            Text.Draw(font, text, start, shown,
                    Aligned(font, text, start, shown, textX, textW, player),
                    lineY, Palette.TEXT);
            start = end;
        }

        Text.Draw(font, CONTINUE, 0, CONTINUE.length(),
                x + (w - font.Width(CONTINUE, 0, CONTINUE.length())) / 2,
                y + h - CONTINUE_PAD - font.lineH,
                OnContinue(mouseX, mouseY) ? Palette.TEXT_WARN :
                Palette.TEXT_NEWS);
    }

    private void DrawChoice(int mouseX, int mouseY) {
        int lit = OptionAt(mouseX, mouseY);

        Text.Draw(bold, HEADING, 0, HEADING.length(),
                x + (w - bold.Width(HEADING, 0, HEADING.length())) / 2,
                y + HEADING_Y, Palette.TEXT_NEWS);

        for (int i = 0; i < session.TalkOptions(); i++) {
            String option = session.TalkOption(i);
            Text.Draw(font, option, 0, option.length(),
                    x + (w - font.Width(option, 0, option.length())) / 2,
                    OptionY(i), i == lit ? Palette.TEXT_WARN : Palette.TEXT);
        }
    }

    /* A stand-in for a portrait: a square with as much of the name as
       fits written in the middle of it. */
    private void DrawPortrait(String name, int px, int py) {
        int room = PORTRAIT - 2 * PORTRAIT_PAD, end = name.length();

        while (end > 1 && font.Width(name, 0, end) > room) end--;

        Gfx.FillRect(px, py, PORTRAIT, PORTRAIT, Palette.PORTRAIT);
        Gfx.Outline(px, py, PORTRAIT, PORTRAIT, Palette.PORTRAIT_EDGE);
        Text.Draw(font, name, 0, end,
                px + (PORTRAIT - font.Width(name, 0, end)) / 2,
                py + (PORTRAIT - font.lineH) / 2, Palette.TEXT);
    }

    /* Where a run of text starts so as to sit at the left of a
       column, or at its right. */
    private static int Aligned(Font f, CharSequence s, int start, int end,
                               int left, int width, boolean toRight) {
        return toRight ? left + width - f.Width(s, start, end) : left;
    }
}
