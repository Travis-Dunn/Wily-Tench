package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

/**
 * The menu widgets' arithmetic, with nothing drawn: which button a
 * click lands on, where a slider's knob goes and what it reads, and
 * how new settings are put into the lines of config.ini.
 */
public class WidgetTest {
    private static final int FPS_MIN = 20, FPS_MAX = 300, FPS_STEP = 10;
    private static final int SLIDER_X = 100, SLIDER_Y = 50, SLIDER_CELLS = 7;
    private static final int SLIDER_W = SLIDER_CELLS * Skin.CELL;

    public void testAButtonIsClickedByPressingAndLettingGoOnIt() {
        Fixture.Start(0);
        Buttons buttons = TwoButtons();

        Check("nothing yet", buttons.Input(15, 15, false, false) ==
                Buttons.NONE);
        Check("down is not a click", buttons.Input(15, 15, true, false) ==
                Buttons.NONE);
        Check("up on it is", buttons.Input(15, 15, false, true) == 0);

        buttons.Input(15, 55, true, false);
        Check("the second button", buttons.Input(20, 60, false, true) == 1);
        Fixture.Finish();
    }

    public void testSlidingOffAButtonBeforeLettingGoDoesNothing() {
        Fixture.Start(0);
        Buttons buttons = TwoButtons();

        buttons.Input(15, 15, true, false);
        Check("let go beside it", buttons.Input(200, 15, false, true) ==
                Buttons.NONE);

        buttons.Input(15, 15, true, false);
        Check("let go on the other one",
                buttons.Input(15, 55, false, true) == Buttons.NONE);

        buttons.Input(200, 15, true, false);
        Check("pressed elsewhere and let go on one",
                buttons.Input(15, 15, false, true) == Buttons.NONE);

        buttons.Input(15, 15, true, false);
        buttons.Reset();
        Check("a press forgotten on leaving the scene",
                buttons.Input(15, 15, false, true) == Buttons.NONE);
        Fixture.Finish();
    }

    public void testAButtonsEdges() {
        PushButton button = new PushButton("Play", 10, 10, 6);

        Check("top left", button.Contains(10, 10));
        Check("bottom right", button.Contains(10 + 6 * Skin.CELL - 1,
                10 + Skin.BUTTON_H - 1));
        Check("just left", !button.Contains(9, 10));
        Check("just right", !button.Contains(10 + 6 * Skin.CELL, 10));
        Check("just below", !button.Contains(10, 10 + Skin.BUTTON_H));
    }

    public void testASlidersKnobFollowsItsValue() {
        Slider slider = NewSlider();

        slider.SetValue(FPS_MIN);
        int left = slider.KnobX();
        slider.SetValue(FPS_MAX);
        int right = slider.KnobX();
        slider.SetValue((FPS_MIN + FPS_MAX) / 2);
        int middle = slider.KnobX();

        Check("starts clear of the left arrow", left > SLIDER_X);
        Check("ends clear of the right arrow",
                right + Skin.CELL < SLIDER_X + SLIDER_W);
        Check("halfway is halfway", middle == (left + right) / 2);
    }

    public void testPressingTheTrackAndDraggingSetTheValue() {
        Slider slider = NewSlider();
        int midY = SLIDER_Y + 8;
        slider.SetValue(100);

        Check("a press far right changes it", slider.Input(
                SLIDER_X + SLIDER_W - 12, midY, true, true));
        Check("to the top", slider.Value() == FPS_MAX);

        Check("dragging left, off the track's line, still follows",
                slider.Input(SLIDER_X + 12, midY + 40, false, true));
        Check("to the bottom", slider.Value() == FPS_MIN);

        slider.Input(SLIDER_X + SLIDER_W / 2, midY, false, true);
        Check("the middle reads the middle",
                slider.Value() == (FPS_MIN + FPS_MAX) / 2);
        Check("and is a whole step", slider.Value() % FPS_STEP == 0);

        slider.Input(SLIDER_X + 30, midY, false, false);
        int rested = slider.Value();
        Check("letting go ends the drag", !slider.Input(SLIDER_X + 90,
                midY, false, false) && slider.Value() == rested);

        Check("a press off the slider is not its business",
                !slider.Input(SLIDER_X + 50, midY + 40, true, true) &&
                !slider.Input(SLIDER_X + 90, midY, false, true));
    }

    public void testTheArrowsMoveOneStep() {
        Slider slider = NewSlider();
        int midY = SLIDER_Y + 8;
        slider.SetValue(100);

        slider.Input(SLIDER_X + SLIDER_W - 3, midY, true, true);
        Check("right arrow", slider.Value() == 110);
        slider.Input(SLIDER_X + 3, midY, false, false);
        slider.Input(SLIDER_X + 3, midY, true, true);
        slider.Input(SLIDER_X + 3, midY, false, false);
        slider.Input(SLIDER_X + 3, midY, true, true);
        Check("left arrow twice", slider.Value() == 90);

        slider.SetValue(FPS_MAX);
        Check("no further than the top", !slider.Input(
                SLIDER_X + SLIDER_W - 3, midY, true, true));
    }

    public void testAValueTheSliderCannotShowIsLeftAloneUntilItIsUsed() {
        Slider slider = NewSlider();
        int midY = SLIDER_Y + 8;

        slider.SetValue(1000);
        int atEnd = slider.KnobX();
        slider.SetValue(FPS_MAX);
        Check("the knob rests at the end", atEnd == slider.KnobX());

        slider.SetValue(1000);
        slider.Input(SLIDER_X + 60, midY + 40, false, false);
        Check("looking does not change it", slider.Value() == 1000);
        slider.Input(SLIDER_X + 3, midY, true, true);
        Check("one step down from the end it rests at",
                slider.Value() == FPS_MAX - FPS_STEP);

        slider.SetValue(87);
        slider.Input(SLIDER_X + SLIDER_W - 3, midY, true, true);
        Check("an in-between value steps from the nearest step",
                slider.Value() == 100);
    }

    public void testNewSettingsReplaceOldLinesAndKeepEverythingElse() {
        String[] keys = { "fullscreen", "maxFps", "walkStyle" };
        String[] values = { "true", "120", "pulse" };
        String[] merged = Settings.Merge(new String[] {
            "# My settings", "fullscreen=false", "", "  maxFps = 60  ",
            "colour=blue", "#maxFps=30", "maxFps=75"
        }, keys, values);

        Check("eight lines", merged.length == 8);
        Check("comment kept", merged[0].equals("# My settings"));
        Check("value replaced", merged[1].equals("fullscreen=true"));
        Check("blank line kept", merged[2].equals(""));
        Check("spaced line replaced", merged[3].equals("maxFps=120"));
        Check("unknown key kept", merged[4].equals("colour=blue"));
        Check("commented-out key kept", merged[5].equals("#maxFps=30"));
        Check("a second setting of a key replaced too",
                merged[6].equals("maxFps=120"));
        Check("missing key added", merged[7].equals("walkStyle=pulse"));

        merged = Settings.Merge(new String[0], keys, values);
        Check("an empty file gets every key", merged.length == 3 &&
                merged[0].equals("fullscreen=true"));
    }

    public void testMistakesInTheWidgetArtAreExplained() {
        Fixture.Start(0);
        String toggle = "{ \"sheet\": \"ui_inputs\", \"off\": [19, 24], " +
                "\"on\": [21, 24] }";
        String most = "\"button\": { \"sheet\": \"ui_inputs\", " +
                "\"normal\": [19, 0], \"hover\": [19, 4], \"pressed\": " +
                "[19, 6], \"pressed_drop\": 3 }, \"checkbox\": " + toggle +
                ", \"radio\": " + toggle + ", \"slider\": { \"sheet\": " +
                "\"ui_bars_sliders_scrollbars\", \"track\": [24, 18] }, " +
                "\"slider_knob\": { \"sheet\": \"ui_inputs\", \"cell\": " +
                "[20, 20] }";

        Refused("no frame", "{ " + most + " }", "[frame]");
        Refused("a frame off the edge of its sheet", "{ " + most +
                ", \"frame\": { \"sheet\": \"ui_frames\", \"block\": " +
                "[9999, 8], \"margin\": 6 } }", "is outside the sheet");
        Refused("a margin wider than a cell", "{ " + most +
                ", \"frame\": { \"sheet\": \"ui_frames\", \"block\": " +
                "[30, 8], \"margin\": 16 } }", "[margin] must be from 0");
        Refused("a part the game has no use for", "{ " + most +
                ", \"frame\": { \"sheet\": \"ui_frames\", \"block\": " +
                "[30, 8], \"margin\": 6 }, \"dial\": { } }", "[dial]");

        /* Put the real one back for whatever test runs next. */
        Skin.Load(DataNode.Load("interface.json").Obj("widgets"));
        Fixture.Finish();
    }

    private static Buttons TwoButtons() {
        Font font = Font.Load("small.fnt");
        return new Buttons(font, new PushButton[] {
            new PushButton("One", 10, 10, 6),
            new PushButton("Two", 10, 50, 6)
        });
    }

    private static Slider NewSlider() {
        return new Slider(SLIDER_X, SLIDER_Y, SLIDER_CELLS, FPS_MIN,
                FPS_MAX, FPS_STEP);
    }

    private static void Refused(String what, String widgets,
                                String expected) {
        try {
            Skin.Load(DataNode.Parse(widgets, "interface.json"));
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }
}
