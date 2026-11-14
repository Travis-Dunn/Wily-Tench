package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

/**
 * The video cable: what Ntsc does to a picture, and how Signal puts
 * the two layers onto it. These need no window and no game.
 *
 * That Ntsc matches the C it was ported from, byte for byte, cannot be
 * tested here, there being no C here. These check what any such signal
 * must do, so that a slip in the port would show.
 */
public class SignalTest {
    private static final int ROWS = 8, OUT_W = 768;
    private static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;

    /* A few of the game's own colors, packed 0xAABBGGRR. */
    private static final int[] COLORS = {
        0xFF86B1EC, 0xFF6F8437, 0xFF2525B8, 0xFFE35E2D, 0xFF808080
    };

    public void testAFlatColorComesBackNearItself() {
        for (int wires = 0; wires < 2; wires++) {
            for (int i = 0; i < COLORS.length; i++) {
                int[] out = Send(Flat(COLORS[i]), wires == 1, 0);
                int got = out[ROWS / 2 * OUT_W + OUT_W / 2];

                /* Near, not equal: a strong red loses a good deal on
                   one wire, as it always did. */
                Check("color " + i + " on " + Name(wires) + ": sent " +
                        Integer.toHexString(COLORS[i]) + ", got " +
                        Integer.toHexString(got),
                        Apart(got, COLORS[i]) <= 48);
            }
            int got = Send(Flat(WHITE), wires == 1, 0)
                    [ROWS / 2 * OUT_W + OUT_W / 2];
            Check("white stays white on " + Name(wires) + ": " +
                    Integer.toHexString(got), Apart(got, WHITE) <= 16);
            got = Send(Flat(BLACK), wires == 1, 0)
                    [ROWS / 2 * OUT_W + OUT_W / 2];
            Check("black stays black on " + Name(wires) + ": " +
                    Integer.toHexString(got), Apart(got, BLACK) <= 16);
        }
    }

    public void testFineStripesTurnToColorOnOneWireButNotOnTwo() {
        int[] stripes = new int[Ntsc.SAMPLES * ROWS];

        /* Black and white, two samples each: one cycle of the color
           carrier, which a set on one wire cannot tell from color. */
        for (int i = 0; i < stripes.length; i++)
            stripes[i] = i % Ntsc.SAMPLES / 2 % 2 == 0 ? WHITE : BLACK;

        int one = MostColor(Send(stripes, false, 0));
        int two = MostColor(Send(stripes, true, 0));
        /* Less than a plain set would show, since this one has a comb
           filter (Tuning.COMB), but still plainly color. */
        Check("colored on composite: " + one, one >= 20);
        Check("grey on S-Video: " + two, two <= 10);
    }

    public void testARowDoesNotReachIntoItsNeighbors() {
        int[] picture = Flat(BLACK);

        for (int x = 0; x < Ntsc.SAMPLES; x++)
            picture[3 * Ntsc.SAMPLES + x] = WHITE;
        int[] out = Send(picture, false, 0);

        for (int y = 0; y < ROWS; y++) {
            int got = out[y * OUT_W + OUT_W / 2];
            Check("row " + y + " is " + Integer.toHexString(got),
                    got == (y == 3 ? WHITE : BLACK));
        }
    }

    public void testTheSamePictureComesOutTheSameUnlessThereIsNoise() {
        Ntsc ntsc = new Ntsc(ROWS, OUT_W);
        int[] picture = Flat(COLORS[0]);
        int[] a = new int[OUT_W * ROWS], b = new int[OUT_W * ROWS];

        /* A few frames first: the set takes a moment to settle. */
        for (int frame = 0; frame < 6; frame++) {
            ntsc.Encode(picture, false, frame);
            ntsc.Decode(frame == 4 ? a : b, 0);
        }
        ntsc.Encode(picture, false, 0);
        ntsc.Decode(b, 0);
        Check("steady with a clean signal", java.util.Arrays.equals(a, b));

        ntsc.Encode(picture, false, 0);
        ntsc.Decode(b, 20);
        Check("not with a noisy one", !java.util.Arrays.equals(a, b));
    }

    public void testTheWorldShowsThroughTheHoleAndSlidesByLessThanAPixel() {
        final int W = 96, H = 8, VIEW_X = 8, VIEW_Y = 2, VIEW_H = 4;
        final int WORLD_W = 81, SCALE = 4, EDGE = 40;
        Signal signal = new Signal(Signal.SVIDEO, W, H, VIEW_X, VIEW_Y,
                VIEW_H, WORLD_W, SCALE);
        int[] picture = new int[W * H], world = new int[WORLD_W * 5];

        /* A grey frame with a hole where the view is, and under it a
           world white on the left and black on the right. */
        java.util.Arrays.fill(picture, 0xFF808080);
        for (int y = VIEW_Y; y < VIEW_Y + VIEW_H; y++)
            for (int x = VIEW_X; x < VIEW_X + WORLD_W - 1; x++)
                picture[y * W + x] = 0;
        for (int i = 0; i < world.length; i++)
            world[i] = i % WORLD_W < EDGE ? WHITE : BLACK;

        int outW = W * signal.Across(), row = (VIEW_Y + 1) * outW;
        int[] out = signal.Run(picture, world, 0);
        int still = EdgeAt(out, row, outW);

        Check("the frame's row is the frame's grey",
                Apart(out[outW / 2], 0xFF808080) <= 32);
        Check("the world's edge is where it belongs: " + still,
                Math.abs(still - (VIEW_X + EDGE) * SCALE) <= 6);

        out = signal.Run(picture, world, 2);
        int slid = EdgeAt(out, row, outW);
        Check("half a game pixel of slide moves it two screen pixels " +
                "left: from " + still + " to " + slid,
                still - slid >= 1 && still - slid <= 3);
    }

    public void testEveryColumnOfThePictureCarriesTheSameWeight() {
        final int W = 384, H = 4, SCALE = 4, SPAN = 8;
        Signal signal = new Signal(Signal.SVIDEO, W, H, 0, 0, H, W + 1,
                SCALE);
        int[] picture = new int[W * H], world = new int[(W + 1) * H];

        /* Columns black and white by turns. A signal line is not a
           whole number of samples to a column, and if each sample
           took one column whole, some columns would get half the
           samples of the rest and show as stripes down the screen.
           So: any stretch of eight columns must come out as bright as
           any other. */
        for (int i = 0; i < picture.length; i++)
            picture[i] = i % W % 2 == 0 ? WHITE : BLACK;

        int[] out = signal.Run(picture, world, 0);
        int least = Integer.MAX_VALUE, most = 0;
        for (int col = 2 * SPAN; col + SPAN <= W - 2 * SPAN; col += 2) {
            int sum = 0;
            for (int x = col * SCALE; x < (col + SPAN) * SCALE; x++)
                sum += out[W * SCALE + x] >> 8 & 0xFF;
            sum /= SPAN * SCALE;
            least = Math.min(least, sum);
            most = Math.max(most, sum);
        }
        Check("evenly bright, from " + least + " to " + most,
                most - least <= 6);
    }

    /* Sends a picture a few times over, so the set has settled, and
       gives what came out last. */
    private static int[] Send(int[] picture, boolean twoWires, int noise) {
        Ntsc ntsc = new Ntsc(ROWS, OUT_W);
        int[] out = new int[OUT_W * ROWS];

        /* Set up as the game sets it up, with every knob where it
           starts. */
        Tuning.ResetAll();
        ntsc.SetBands((int) Math.round(Tuning.Get(Tuning.LUMA_BAND) * 1e5),
                (int) Math.round(Tuning.Get(Tuning.COLOR_BAND_I) * 1e5),
                (int) Math.round(Tuning.Get(Tuning.COLOR_BAND_Q) * 1e5));
        ntsc.Set(Tuning.Int(Tuning.COMB), 0, Tuning.Int(twoWires ?
                Tuning.SATURATION_2 : Tuning.SATURATION),
                Tuning.Int(twoWires ? Tuning.CONTRAST_2 : Tuning.CONTRAST),
                Tuning.Int(twoWires ? Tuning.BRIGHTNESS_2 :
                Tuning.BRIGHTNESS), 0);

        for (int frame = 0; frame < 4; frame++) {
            ntsc.Encode(picture, twoWires, frame);
            ntsc.Decode(out, noise);
        }
        return out;
    }

    private static int[] Flat(int color) {
        int[] picture = new int[Ntsc.SAMPLES * ROWS];

        java.util.Arrays.fill(picture, color);
        return picture;
    }

    /* The most any one of red, green, and blue differs. */
    private static int Apart(int a, int b) {
        int most = 0;

        for (int shift = 0; shift < 24; shift += 8) {
            most = Math.max(most, Math.abs((a >> shift & 0xFF) -
                    (b >> shift & 0xFF)));
        }
        return most;
    }

    /* How far the most colored pixel of the middle of a row is from
       grey. */
    private static int MostColor(int[] out) {
        int most = 0;

        for (int x = OUT_W / 4; x < OUT_W * 3 / 4; x++) {
            int p = out[ROWS / 2 * OUT_W + x];
            int r = p & 0xFF, g = p >> 8 & 0xFF, b = p >> 16 & 0xFF;

            most = Math.max(most, Math.max(Math.abs(r - g),
                    Math.abs(g - b)));
        }
        return most;
    }

    /* Where along a row the picture goes from light to dark. */
    private static int EdgeAt(int[] out, int row, int outW) {
        for (int x = outW / 4; x < outW; x++)
            if ((out[row + x] >> 8 & 0xFF) < 128) return x;
        return -1;
    }

    private static String Name(int wires) {
        return wires == 1 ? "S-Video" : "composite";
    }
}
