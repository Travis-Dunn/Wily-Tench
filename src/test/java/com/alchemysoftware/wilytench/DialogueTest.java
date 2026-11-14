package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;
import static com.alchemysoftware.wilytench.Fixture.Count;
import static com.alchemysoftware.wilytench.Fixture.Run;
import static com.alchemysoftware.wilytench.Fixture.Said;

/**
 * Talking with someone, and the small quest Harl gives: his real
 * script, on the forge map Fixture describes, where he stands at
 * (5,1). Talk is read through with ContinueTalk() and answered with
 * ChooseTalk(), as the scene does for the player.
 */
public class DialogueTest {
    private static final int HARL_X = 5, HARL_Y = 1;
    private static final int YES = 0, NO = 1;
    private static final int PLENTY_OF_TICKS = 20;
    private static final int REWARD = 2500;

    private static final String MSG_PICKAXE =
            "Harl hands you a bronze pickaxe.";
    private static final String MSG_DONE = "You have completed Harl's Axe.";

    private Session s;
    private int pickaxe, axe, smithing;

    public void testTalkingToSomeoneStartsTalk() {
        Start();
        Check("nothing yet", s.TalkKind() == Session.TALK_NONE);
        TalkToHarl();

        Check("he speaks first", s.TalkKind() == Session.TALK_NPC);
        Check("under his name", s.TalkSpeaker().equals("Harl"));
        Check("his opening words", s.TalkText().startsWith("You there."));
        Check("and it waits, however long", s.Busy());
        Run(PLENTY_OF_TICKS);
        Check("still waiting", s.TalkKind() == Session.TALK_NPC);
        Fixture.Finish();
    }

    public void testAChoiceIsOfferedAndAnswered() {
        Start();
        TalkToHarl();
        s.ContinueTalk();

        Check("a choice", s.TalkKind() == Session.TALK_CHOICE);
        Check("of two", s.TalkOptions() == 2);
        Check("worded", s.TalkOption(YES).equals("I'll make you one.") &&
                s.TalkOption(NO).equals("Not just now."));

        s.ContinueTalk();
        Check("reading on does not answer a choice",
                s.TalkKind() == Session.TALK_CHOICE);
        s.ChooseTalk(2);
        s.ChooseTalk(-1);
        Check("nor does picking what is not offered",
                s.TalkKind() == Session.TALK_CHOICE);

        s.ChooseTalk(NO);
        Check("the player says it", s.TalkKind() == Session.TALK_PLAYER &&
                s.TalkSpeaker().equals(Session.PLAYER_NAME) &&
                s.TalkText().equals("Not just now."));
        s.ChooseTalk(YES);
        Check("picking does not read on",
                s.TalkKind() == Session.TALK_PLAYER);
        s.ContinueTalk();
        Check("he answers", s.TalkKind() == Session.TALK_NPC);
        s.ContinueTalk();
        Check("and that is that", s.TalkKind() == Session.TALK_NONE &&
                !s.Busy());
        Check("the quest was not begun", s.vars.getInt("harl_axe") == 0);
        Fixture.Finish();
    }

    public void testDoingSomethingElseEndsTheTalk() {
        Start();
        TalkToHarl();
        s.WalkTo(1, 1);
        Check("gone", s.TalkKind() == Session.TALK_NONE && !s.Busy());
        s.ContinueTalk();
        s.ChooseTalk(YES);
        Check("and answers to it mean nothing now", !s.Busy());

        TalkToHarl();
        s.ContinueTalk();
        s.ChooseTalk(YES);
        s.WalkTo(1, 1);
        Check("agreeing and walking off before he has explained " +
                "begins nothing", s.vars.getInt("harl_axe") == 0);
        Fixture.Finish();
    }

    public void testAcceptingBeginsTheQuestAndAPickaxeIsGivenIfNeeded() {
        Start();
        Accept();
        Check("begun", s.vars.getInt("harl_axe") == 1);
        Check("he offers a pickaxe", s.TalkKind() == Session.TALK_NPC &&
                s.TalkText().indexOf("pickaxe") >= 0);
        Check("not handed over until that is read", Count(pickaxe) == 0);
        s.ContinueTalk();
        Check("now it is", Count(pickaxe) == 1 && Said(MSG_PICKAXE) == 1);
        Check("talk over", s.TalkKind() == Session.TALK_NONE && !s.Busy());
        Fixture.Finish();
    }

    public void testNoSecondPickaxeForSomeoneWhoHasOne() {
        Start();
        s.inventory.Add(pickaxe);
        Accept();
        Check("begun, and nothing more to say",
                s.vars.getInt("harl_axe") == 1 &&
                s.TalkKind() == Session.TALK_NONE);
        Check("still one pickaxe", Count(pickaxe) == 1 &&
                Said(MSG_PICKAXE) == 0);
        Fixture.Finish();
    }

    public void testAFullPackGetsNoPickaxeUntilThereIsRoom() {
        Start();
        int ore = Fixture.Item("copper_ore");
        for (int i = 0; i < Inventory.SLOTS; i++) s.inventory.Add(ore);
        Accept();
        Check("told to make room", s.TalkText().indexOf("no room") >= 0);
        s.ContinueTalk();
        Check("none given", Count(pickaxe) == 0 && !s.Busy());

        s.inventory.Remove(0);
        TalkToHarl();
        Check("asked about the axe", s.TalkText().startsWith("No axe yet?"));
        s.ContinueTalk();
        Check("and offered one again",
                s.TalkText().indexOf("take another") >= 0);
        s.ContinueTalk();
        Check("given this time", Count(pickaxe) == 1);
        Fixture.Finish();
    }

    public void testBringingTheAxeCompletesTheQuest() {
        Start();
        s.inventory.Add(pickaxe);
        Accept();

        TalkToHarl();
        Check("nothing to hand over yet",
                s.TalkText().startsWith("No axe yet?"));
        s.ContinueTalk();
        Check("so nothing happens", s.vars.getInt("harl_axe") == 1 &&
                !s.Busy());

        s.inventory.Add(axe);
        TalkToHarl();
        Check("the player offers it",
                s.TalkKind() == Session.TALK_PLAYER &&
                s.TalkText().equals("I've made your axe."));
        s.ContinueTalk();
        Check("he looks it over", s.TalkKind() == Session.TALK_NPC);
        Check("and has not taken it yet", Count(axe) == 1 &&
                s.skills.XpTenths(smithing) == 0);
        s.ContinueTalk();

        Check("the axe is his", Count(axe) == 0);
        Check("done", s.vars.getInt("harl_axe") == 2);
        Check("announced in the chat box", Said(MSG_DONE) == 1);
        Check("250 Smithing xp", s.skills.XpTenths(smithing) == REWARD);
        Check("which is a level or two",
                s.skills.Level(smithing) > 1);

        TalkToHarl();
        Check("afterwards he only chats",
                s.TalkText().startsWith("That axe of yours"));
        s.ContinueTalk();
        Check("and gives nothing more",
                s.skills.XpTenths(smithing) == REWARD && !s.Busy());
        Fixture.Finish();
    }

    public void testAnAxeDroppedMidSentenceIsNotHandedOver() {
        Start();
        s.inventory.Add(pickaxe);
        Accept();
        s.inventory.Add(axe);
        TalkToHarl();
        s.ContinueTalk();
        s.DropItem(1);
        s.ContinueTalk();
        Check("no quest for no axe", s.vars.getInt("harl_axe") == 1 &&
                s.skills.XpTenths(smithing) == 0 && Said(MSG_DONE) == 0);
        Fixture.Finish();
    }

    public void testTheQuestIsRememberedInTheSavedGame() {
        Start();
        s.inventory.Add(pickaxe);
        Accept();
        SaveState save = new SaveState(s);
        com.alchemysoftware.whitetail.save.SaveData data = save.Capture();

        Fixture.Start(0);
        s = Fixture.session;
        new SaveState(s).Restore(data);
        Check("still begun", s.vars.getInt("harl_axe") == 1);
        Fixture.Finish();
    }

    public void testSomeoneToTalkToIsOfferedOnTheMenu() {
        Start();
        Actions actions = new Actions(s);
        ContextMenu menu = new ContextMenu(Font.Load("small.fnt"), 384, 216);

        actions.ForTile(menu, HARL_X, HARL_Y);
        Check("talk first", menu.Verb(0).equals("Talk-to") &&
                menu.Subject(0).equals("Harl"));
        Check("in the color of people",
                menu.SubjectColor(0) == Palette.AMBER);
        Check("then walk, examine, cancel", menu.Count() == 4 &&
                menu.Verb(2).equals("Examine"));

        actions.Do(menu, 2);
        Check("examining him", s.chat.Newest().equals(
                "An old smith. His hands have seen a lot of work."));
        actions.Do(menu, 0);
        Run(PLENTY_OF_TICKS);
        Check("choosing it talks", s.TalkKind() == Session.TALK_NPC);
        Fixture.Finish();
    }

    public void testTheBoxKnowsWhichChoiceIsUnderThePointer() {
        Start();
        Font font = Font.Load("small.fnt");
        DialogueBox box = new DialogueBox(font, font, s, 0, 100, 384, 88);
        TalkToHarl();

        Check("speech has no choices to point at",
                box.OptionAt(box.MiddleX(), 140) == DialogueBox.NO_OPTION);
        s.ContinueTalk();

        for (int i = 0; i < s.TalkOptions(); i++) {
            Check("option " + i, box.OptionAt(box.MiddleX(),
                    box.OptionY(i) + 3) == i);
        }
        Check("they do not overlap", box.OptionY(1) >=
                box.OptionY(0) + font.lineH);
        Check("the last fits in the box",
                box.OptionY(1) + font.lineH <= 100 + 88);
        Check("far to one side is no option", box.OptionAt(2,
                box.OptionY(0) + 3) == DialogueBox.NO_OPTION);
        Check("nor is anywhere off the box", !box.Contains(10, 50) &&
                box.OptionAt(box.MiddleX(), 50) == DialogueBox.NO_OPTION);
        Fixture.Finish();
    }

    public void testTheContinuePromptIsLitUnderThePointerEveryTime() {
        final int W = 384, H = 216, X = 5, Y = 153, BOX_W = 298, BOX_H = 58;
        byte[] buf = new byte[W * H];
        Font font = Font.Load("small.fnt"), bold = Font.Load("bold.fnt");
        Start();
        DialogueBox box = new DialogueBox(font, bold, s, X, Y, BOX_W, BOX_H);
        int speeches = 0;

        Gfx.Init(buf, W, H);
        TalkToHarl();
        while (s.TalkKind() != Session.TALK_NONE) {
            if (s.TalkKind() == Session.TALK_CHOICE) {
                s.ChooseTalk(YES);
                continue;
            }
            speeches++;

            /* The prompt is the bottom line of the box. Whatever the
               speech above it, it is in the lit color with the pointer
               on the box and the plain one with it off. */
            int row0 = Y + BOX_H - font.lineH - 1;
            java.util.Arrays.fill(buf, (byte) 0);
            box.Draw(X + 20, Y + 20);
            Check("lit, speech " + speeches + ": " + s.TalkText(),
                    Has(buf, W, row0, Y + BOX_H, Palette.TEXT_WARN) &&
                    !Has(buf, W, row0, Y + BOX_H, Palette.TEXT_NEWS));

            java.util.Arrays.fill(buf, (byte) 0);
            box.Draw(X + 20, Y - 20);
            Check("plain, speech " + speeches,
                    Has(buf, W, row0, Y + BOX_H, Palette.TEXT_NEWS) &&
                    !Has(buf, W, row0, Y + BOX_H, Palette.TEXT_WARN));
            s.ContinueTalk();
        }
        Check("several speeches were read: " + speeches, speeches >= 4);
        Gfx.Shutdown();
        Fixture.Finish();
    }

    /* Whether any pixel of some rows of a picture is a given color. */
    private static boolean Has(byte[] buf, int w, int row0, int row1,
                               int color) {
        for (int i = row0 * w; i < row1 * w; i++)
            if ((buf[i] & 0xFF) == color) return true;
        return false;
    }

    private void Start() {
        Fixture.Start(0);
        s = Fixture.session;
        s.Travel(Fixture.FORGE);
        s.player.Place(HARL_X - 1, HARL_Y);
        pickaxe = Fixture.Item("bronze_pickaxe");
        axe = Fixture.Item("bronze_axe");
        smithing = Fixture.Skill("smithing");
    }

    /* Talks to Harl from beside him and waits for his first words. */
    private void TalkToHarl() {
        s.Use(HARL_X, HARL_Y);
        Run(1);
        Check("talk began", s.TalkKind() != Session.TALK_NONE);
    }

    /* Goes through agreeing to make the axe, up to the end of his
       explanation. */
    private void Accept() {
        TalkToHarl();
        s.ContinueTalk();
        s.ChooseTalk(YES);
        Check("the player agrees", s.TalkText().equals("I'll make you one."));
        s.ContinueTalk();
        Check("he explains", s.TalkText().startsWith("Good."));
        s.ContinueTalk();
    }
}
