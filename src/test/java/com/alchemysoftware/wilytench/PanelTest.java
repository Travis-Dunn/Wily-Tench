package com.alchemysoftware.wilytench;

import static com.alchemysoftware.wilytench.Fixture.Check;

/**
 * The side panel's tabs, what the pointer is taken to be over on each,
 * and what the skill tooltips say. Nothing here draws.
 */
public class PanelTest {
    private static final int PANEL_X = 256, PANEL_W = 128;
    private static final int SCREEN_W = 384, SCREEN_H = 360;
    /* Far enough inside a button, slot, or skill to be clearly on it. */
    private static final int IN = 5;

    private Session s;
    private SidePanel panel;

    public void testTheTabsSwitchWhatTheBoxShows() {
        Start();
        Check("opens on the inventory",
                panel.Tab() == SidePanel.TAB_INVENTORY);

        PointAtTab(SidePanel.TAB_SKILLS);
        Check("a tab names itself", panel.TipHeading().equals("Skills") &&
                panel.TipLines() == 0);
        Check("a click on a tab is used up", panel.Click());
        Check("skills showing", panel.Tab() == SidePanel.TAB_SKILLS);

        PointAtTab(SidePanel.TAB_INVENTORY);
        Check("and back", panel.Click() &&
                panel.Tab() == SidePanel.TAB_INVENTORY);

        panel.Input(panel.SlotX(0) + IN, panel.SlotY(0) + IN);
        Check("a click elsewhere is not the panel's", !panel.Click());
        panel.Input(-1, -1);
        Check("nor one outside the picture", !panel.Click() &&
                panel.TipHeading() == null);
        Fixture.Finish();
    }

    public void testItemsAreOnlyUnderThePointerOnTheInventoryTab() {
        Start();
        s.inventory.Add(Fixture.Item("copper_ore"));

        panel.Input(panel.SlotX(0) + IN, panel.SlotY(0) + IN);
        Check("the ore is pointed at", panel.HoverSlot() == 0 &&
                panel.HoverSkill() == SidePanel.NO_SKILL);
        Check("items have no tooltip", panel.TipHeading() == null);

        PointAtTab(SidePanel.TAB_SKILLS);
        panel.Click();
        panel.Input(panel.SlotX(0) + IN, panel.SlotY(0) + IN);
        Check("the same spot is now a skill",
                panel.HoverSlot() == SidePanel.NO_SLOT &&
                panel.HoverSkill() == Fixture.Mining());
        Fixture.Finish();
    }

    public void testASkillTooltipGivesExperienceAndTheNextLevel() {
        Start();
        int mining = Fixture.Mining(), smithing = Fixture.Skill("smithing");
        PointAtTab(SidePanel.TAB_SKILLS);
        panel.Click();

        PointAtSkill(mining);
        Check("headed by the skill", panel.TipHeading().equals("Mining"));
        Check("two lines", panel.TipLines() == 2);
        Check("no experience yet",
                panel.TipLine(0).equals("Experience: 0.0"));
        Check("level 2 comes at 83",
                panel.TipLine(1).equals("Next level at: 83"));

        s.skills.AddXp(mining, 830);
        Check("it keeps up", panel.TipLine(0).equals("Experience: 83.0") &&
                panel.TipLine(1).equals("Next level at: 174"));

        PointAtSkill(smithing);
        Check("each skill has its own",
                panel.TipHeading().equals("Smithing") &&
                panel.TipLine(0).equals("Experience: 0.0"));

        s.skills.AddXp(smithing,
                Skills.XpForLevel(Skills.MAX_LEVEL) * 10);
        Check("at the top there is no next level", panel.TipLines() == 1);

        panel.Input(panel.SkillX(Skills.Count()) + IN,
                panel.SkillY(Skills.Count()) + IN);
        Check("past the last skill is nothing",
                panel.HoverSkill() == SidePanel.NO_SKILL &&
                panel.TipHeading() == null);
        Fixture.Finish();
    }

    public void testMistakesInTheSkillAndInterfaceFilesAreExplained() {
        Fixture.Start(0);
        String mining = "{ \"id\": \"mining\", \"name\": \"Mining\" }";

        RefusedSkills("a repeated skill", mining + ", " + mining,
                "there are two skills with this id");
        RefusedSkills("half a picture", "{ \"id\": \"mining\", " +
                "\"name\": \"Mining\", \"sheet\": \"items\" }",
                "[cell]");
        RefusedSkills("a misspelled value", "{ \"id\": \"mining\", " +
                "\"name\": \"Mining\", \"icon\": \"items\" }", "[icon]");

        String inventory = "{ \"id\": \"inventory\", \"name\": \"Pack\", " +
                "\"sheet\": \"items\", \"cell\": [0, 0] }";
        RefusedTabs("a missing tab", inventory,
                "no tab with the id [skills]");
        RefusedTabs("a tab the game lacks", inventory + ", { \"id\": " +
                "\"magic\", \"name\": \"Magic\", \"sheet\": \"items\", " +
                "\"cell\": [0, 0] }", "the game has no tab with this id");
        RefusedTabs("a repeated tab", inventory + ", " + inventory,
                "there are two tabs with this id");

        /* Put the real ones back for whatever test runs next. */
        Skills.Load(DataNode.Load("skills.json"));
        SidePanel.Load(DataNode.Load("interface.json"));
        Fixture.Finish();
    }

    private void Start() {
        Fixture.Start(0);
        s = Fixture.session;
        Font font = Font.Load("small.fnt");
        panel = new SidePanel(font, font, font, PANEL_X, 0, PANEL_W,
                SCREEN_H, SCREEN_W, SCREEN_H, s.inventory, s.skills);
    }

    private void PointAtTab(int tab) {
        panel.Input(panel.TabX(tab) + IN, panel.TabY() + IN);
    }

    private void PointAtSkill(int skill) {
        panel.Input(panel.SkillX(skill) + IN, panel.SkillY(skill) + IN);
    }

    private static void RefusedSkills(String what, String skills,
                                      String expected) {
        try {
            Skills.Load(DataNode.Parse("{ \"skills\": [ " + skills + " ] }",
                    "skills.json"));
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }

    private static void RefusedTabs(String what, String tabs,
                                    String expected) {
        try {
            SidePanel.Load(DataNode.Parse("{ \"tabs\": [ " + tabs + " ] }",
                    "interface.json"));
        } catch (DataError e) {
            Check(what + " explained; said: " + e.getMessage(),
                    e.getMessage().indexOf(expected) >= 0);
            return;
        }
        Check(what + " was accepted", false);
    }
}
