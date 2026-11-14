package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

/**
 * The knobs of the CRT filter and the video signal. No file is used:
 * Tuning.Init() is not called, so Save() does nothing.
 */
public class TuningTest {
    public void testAKnobTurnsByItsStepsAndStopsAtItsEnds() {
        Tuning.ResetAll();
        Check("starts where it should",
                Tuning.Text(Tuning.SCAN_WIDTH).equals("0.30") &&
                Tuning.AtDefault(Tuning.SCAN_WIDTH));

        Tuning.Turn(Tuning.SCAN_WIDTH, 3);
        Check("three steps up: " + Tuning.Text(Tuning.SCAN_WIDTH),
                Tuning.Text(Tuning.SCAN_WIDTH).equals("0.33") &&
                !Tuning.AtDefault(Tuning.SCAN_WIDTH));
        Tuning.Turn(Tuning.SCAN_WIDTH, -3);
        Check("and down again is exactly back",
                Tuning.AtDefault(Tuning.SCAN_WIDTH) &&
                Tuning.Get(Tuning.SCAN_WIDTH) == 30 * 0.01);

        Tuning.Turn(Tuning.SCAN_WIDTH, 1000);
        Check("stops at the top: " + Tuning.Text(Tuning.SCAN_WIDTH),
                Tuning.Text(Tuning.SCAN_WIDTH).equals("0.60"));
        Tuning.Turn(Tuning.SCAN_WIDTH, -1000);
        Check("and at the bottom: " + Tuning.Text(Tuning.SCAN_WIDTH),
                Tuning.Text(Tuning.SCAN_WIDTH).equals("0.10"));

        Tuning.Reset(Tuning.SCAN_WIDTH);
        Check("and can be put back", Tuning.AtDefault(Tuning.SCAN_WIDTH));
    }

    public void testSettingsAreWrittenAsTheirStepsAre() {
        Tuning.ResetAll();
        Check("whole numbers: " + Tuning.Text(Tuning.COMB),
                Tuning.Text(Tuning.COMB).equals("176") &&
                Tuning.Int(Tuning.COMB) == 176);
        Check("tenths: " + Tuning.Text(Tuning.LUMA_BAND),
                Tuning.Text(Tuning.LUMA_BAND).equals("4.2"));
        Tuning.Turn(Tuning.HUE, -7);
        Check("below nothing: " + Tuning.Text(Tuning.HUE),
                Tuning.Text(Tuning.HUE).equals("-7") &&
                Tuning.Int(Tuning.HUE) == -7);
        Tuning.ResetAll();
    }

    public void testTurningAKnobIsNoticedAndNotTurningItIsNot() {
        Tuning.ResetAll();
        int before = Tuning.Version();

        Tuning.Turn(Tuning.GLOW, 0);
        Tuning.Reset(Tuning.GLOW);
        Check("nothing changed", Tuning.Version() == before);
        Tuning.Turn(Tuning.GLOW, 1);
        Check("something did", Tuning.Version() != before);
        Tuning.ResetAll();
    }

    public void testEveryKnobHasANameAndAPlace() {
        for (int knob = 0; knob < Tuning.COUNT; knob++) {
            Check("knob " + knob + " is named", Tuning.Name(knob) != null);
            Check("and in a group", Tuning.Group(knob) >= 0 &&
                    Tuning.Group(knob) < Tuning.GROUP_NAMES.length);
            Check("and no group comes back once left", knob == 0 ||
                    Tuning.Group(knob) >= Tuning.Group(knob - 1));
        }
    }

    public void testTheSignalTakesUpAKnobThatIsTurned() {
        final int W = 96, H = 4;
        Signal signal = new Signal(Signal.COMPOSITE, W, H, 0, 0, H, W + 1,
                4);
        int[] picture = new int[W * H], world = new int[(W + 1) * H];

        /* A strong color, which saturation plainly changes. */
        Tuning.ResetAll();
        java.util.Arrays.fill(picture, 0xFF2525B8);
        int[] out = signal.Run(picture, world, 0);
        int normal = out[W * 4 * 2 + W * 2] & 0xFF;

        Tuning.Turn(Tuning.SATURATION, -16);
        out = signal.Run(picture, world, 0);
        int grey = out[W * 4 * 2 + W * 2];
        Tuning.ResetAll();

        Check("with no saturation red is grey: " +
                Integer.toHexString(grey),
                Math.abs((grey & 0xFF) - (grey >> 16 & 0xFF)) <= 6 &&
                (grey & 0xFF) < normal - 40);
    }
}
