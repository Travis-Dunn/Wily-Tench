package com.alchemysoftware.wilytench;

/**
 * A slider: a track with an arrow at each end and a knob on it, for
 * choosing a number from a range in even steps.
 *
 * Pressing on the track moves the knob there, and holding the mouse
 * button and moving drags it. Pressing an arrow moves one step. The
 * value may be set from outside to something beyond the ends of the
 * range, as when it comes from a file edited by hand; the knob then
 * rests at the nearer end and the value is left alone until the
 * slider is used.
 */
final class Slider {
    /* The arrows take up this much of each end of the track's art. */
    private static final int ARROW_W = 8;
    private static final int KNOB_W = Skin.CELL;

    final int x, y, cells;
    private final int min, max, step;

    private int value;
    private boolean dragging;

    /**
     * @param x the left edge, in picture pixels
     * @param cells the width, in cells of 16 pixels
     * @param max the top of the range, a whole number of steps
     *            above min
     */
    Slider(int x, int y, int cells, int min, int max, int step) {
        this.x = x;
        this.y = y;
        this.cells = cells;
        this.min = min;
        this.max = max;
        this.step = step;
        value = min;
    }

    int Value() { return value; }

    void SetValue(int value) { this.value = value; }

    boolean Contains(int px, int py) {
        return px >= x && px < x + cells * Skin.CELL && py >= y &&
                py < y + Skin.CELL;
    }

    /**
     * Takes one frame of the mouse.
     *
     * @param pressed whether the mouse button went down this frame
     * @param down whether it is down now
     * @return whether the value changed
     */
    boolean Input(int px, int py, boolean pressed, boolean down) {
        int before = value;

        if (!down) dragging = false;
        if (pressed && Contains(px, py)) {
            if (px < x + ARROW_W) value = Clamp(Snap(value) - step);
            else if (px >= x + cells * Skin.CELL - ARROW_W)
                value = Clamp(Snap(value) + step);
            else dragging = true;
        }
        if (dragging) value = ValueAt(px);
        return value != before;
    }

    /** Forgets a drag in progress, as when the scene is left. */
    void Reset() { dragging = false; }

    void Draw() {
        Skin.DrawTrack(x, y, cells);
        Skin.DrawKnob(KnobX(), y);
    }

    /** @return the left edge of the knob for the value as it stands */
    int KnobX() {
        int travel = Travel();
        return x + ARROW_W + (Clamp(value) - min) * travel / (max - min);
    }

    /* How far the knob's left edge can move: the track between the
       arrows, less the knob's own width. */
    private int Travel() {
        return cells * Skin.CELL - 2 * ARROW_W - KNOB_W;
    }

    /* The value whose knob would be centered nearest a pointer. */
    private int ValueAt(int px) {
        int travel = Travel();
        int along = Math.max(0, Math.min(px - KNOB_W / 2 - x - ARROW_W,
                travel));
        int steps = (max - min) / step;

        return min + (along * steps + travel / 2) / travel * step;
    }

    private int Snap(int v) {
        return min + (Clamp(v) - min + step / 2) / step * step;
    }

    private int Clamp(int v) { return Math.max(min, Math.min(v, max)); }
}
