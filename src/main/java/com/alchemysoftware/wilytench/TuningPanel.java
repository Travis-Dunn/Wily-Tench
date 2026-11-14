package com.alchemysoftware.wilytench;

import com.alchemysoftware.whitetail.input.Button;
import com.alchemysoftware.whitetail.input.InputSys;

/**
 * The list of Tuning's knobs, drawn over the view of the world, and
 * the keys that turn them. F1 opens and closes it.
 *
 * Up and Down choose a knob. Left and Right turn it a step, and go on
 * turning while held; with Shift, ten steps at a time. Delete puts
 * the chosen knob back where it started, and F2 puts them all back.
 * Every turn shows at once. The settings are written to tuning.ini
 * when the panel is closed.
 *
 * Only the knobs of what is in use are listed: the CRT filter's if
 * the picture is going through it, the video signal's if it is going
 * down a cable, and of the set's, those for that cable. Which of
 * those is so is decided when the game starts, on the options screen.
 */
final class TuningPanel {
    private static final int W = 150, PAD = 3;

    /* How long a key is held before it repeats, and how often it then
       does, in nanoseconds. */
    private static final long REPEAT_AFTER = 350000000L;
    private static final long REPEAT_EVERY = 40000000L;
    private static final int COARSE = 10;

    private static final String TITLE = "Tuning";
    private static final String[] HELP = {
        "Up Down: choose   Left Right: turn",
        "Shift: x10   Del: reset   F2: reset all"
    };
    private static final String NOTHING = "Nothing to tune: turn on " +
            "the CRT filter or a video signal in Options.";

    private final Font font;
    private final int x, y, h;

    /* The knobs listed, in order, and which is chosen. */
    private final int[] knobs = new int[Tuning.COUNT];
    private int count, chosen;
    private boolean open;

    /* The arrow being held, or 0, which way it turns, and when it
       next repeats. */
    private int held, heldTurn;
    private long repeatAt;

    /**
     * @param x the left edge of the space the panel may use, in
     *          picture pixels: the view of the world
     * @param y its top edge
     * @param h its height
     */
    TuningPanel(Font font, int x, int y, int h) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.h = h;
    }

    boolean IsOpen() { return open; }

    /**
     * Opens the panel, or closes it and saves the settings.
     *
     * @param crt whether the picture is going through the CRT filter
     * @param cable which cable it is going down: Signal.RGB for none
     */
    void Toggle(boolean crt, int cable) {
        open = !open;
        held = 0;
        if (!open) {
            Tuning.Save();
            return;
        }

        count = 0;
        for (int knob = 0; knob < Tuning.COUNT; knob++) {
            int group = Tuning.Group(knob);

            if (group == Tuning.GROUP_CRT ? crt :
                    group == Tuning.GROUP_SIGNAL ? cable != Signal.RGB :
                    group == Tuning.GROUP_COMPOSITE ?
                    cable == Signal.COMPOSITE : cable == Signal.SVIDEO)
                knobs[count++] = knob;
        }
        chosen = Math.min(chosen, Math.max(count - 1, 0));
    }

    /** Takes the keys. Call once a frame while the panel is open. */
    void Input() {
        if (count == 0) return;

        if (InputSys.WasPressed(Button.KEY_UP))
            chosen = (chosen + count - 1) % count;
        if (InputSys.WasPressed(Button.KEY_DOWN))
            chosen = (chosen + 1) % count;
        if (InputSys.WasPressed(Button.KEY_DELETE))
            Tuning.Reset(knobs[chosen]);
        if (InputSys.WasPressed(Button.KEY_F2)) Tuning.ResetAll();

        int steps = InputSys.IsDown(Button.KEY_LEFT_SHIFT) ||
                InputSys.IsDown(Button.KEY_RIGHT_SHIFT) ? COARSE : 1;
        long now = System.nanoTime();

        /* A press turns at once, even one so short that the key is
           up again by now; holding goes on turning after a pause. */
        if (InputSys.WasPressed(Button.KEY_LEFT)) {
            held = Button.KEY_LEFT;
            heldTurn = -1;
        } else if (InputSys.WasPressed(Button.KEY_RIGHT)) {
            held = Button.KEY_RIGHT;
            heldTurn = 1;
        } else if (held != 0 && !InputSys.IsDown(held)) {
            held = 0;
        } else {
            while (held != 0 && now >= repeatAt) {
                Tuning.Turn(knobs[chosen], heldTurn * steps);
                repeatAt += REPEAT_EVERY;
            }
            return;
        }
        if (held != 0) {
            Tuning.Turn(knobs[chosen], heldTurn * steps);
            repeatAt = now + REPEAT_AFTER;
        }
    }

    /** Draws the panel, if it is open. */
    void Draw() {
        if (!open) return;

        int rowH = font.lineH - 1;
        int top = y + PAD + (1 + HELP.length) * rowH + PAD;
        int room = (y + h - PAD - top) / rowH;

        Gfx.SetClip(x, y, W, h);
        Gfx.FillRect(x, y, W, h, Palette.INK);
        Gfx.Outline(x, y, W, h, Palette.SLATE);
        Text.Draw(font, TITLE, 0, TITLE.length(), x + PAD, y + PAD,
                Palette.AMBER);
        for (int i = 0; i < HELP.length; i++) {
            Text.Draw(font, HELP[i], 0, HELP[i].length(), x + PAD,
                    y + PAD + (1 + i) * rowH, Palette.ASH);
        }

        if (count == 0) {
            /* Too long for one line of so narrow a panel. */
            for (int start = 0, line = 0; start < NOTHING.length();
                    line++) {
                int end = font.Fit(NOTHING, start, W - 2 * PAD);

                Text.Draw(font, NOTHING, start, end, x + PAD,
                        top + line * rowH, Palette.BONE);
                start = end;
            }
            Gfx.ClearClip();
            return;
        }

        /* A heading goes above the first knob of each group, and
           takes a row. As many rows as fit are shown, the chosen knob
           kept in the middle of them where that is possible. */
        int rows = 0, chosenRow = 0;
        for (int i = 0; i < count; i++) {
            if (Heads(i)) rows++;
            if (i == chosen) chosenRow = rows;
            rows++;
        }
        int first = Math.max(0, Math.min(chosenRow - room / 2,
                rows - room));

        int row = 0;
        for (int i = 0; i < count; i++) {
            int knob = knobs[i];

            if (Heads(i)) {
                String name = Tuning.GROUP_NAMES[Tuning.Group(knob)];

                if (row >= first && row < first + room) {
                    Text.Draw(font, name, 0, name.length(), x + PAD,
                            top + (row - first) * rowH, Palette.RUST);
                }
                row++;
            }
            if (row >= first && row < first + room) {
                String name = Tuning.Name(knob), value = Tuning.Text(knob);
                int rowY = top + (row - first) * rowH;
                int color = i == chosen ? Palette.AMBER :
                        Tuning.AtDefault(knob) ? Palette.BONE :
                        Palette.MINT;

                if (i == chosen) {
                    Gfx.FillRect(x + 1, rowY, W - 2, rowH, Palette.SLATE);
                }
                Text.Draw(font, name, 0, name.length(), x + 2 * PAD, rowY,
                        color);
                Text.Draw(font, value, 0, value.length(), x + W - PAD -
                        font.Width(value, 0, value.length()), rowY, color);
            }
            row++;
        }
        Gfx.ClearClip();
    }

    /* Whether the knob at a place in the list is the first of its
       group, and so has a heading above it. */
    private boolean Heads(int i) {
        return i == 0 || Tuning.Group(knobs[i]) !=
                Tuning.Group(knobs[i - 1]);
    }
}
