# Handoff

Written at the end of the first long working session, for whoever picks
the project up next. It holds what the other documents do not: where
things stand, what was decided and why, how Travis works, and what comes
next. Keep it current, or delete what has gone stale; a wrong handoff is
worse than none.

Read these first, in this order. They are the durable record, and this
file does not repeat them.

1. `readme.md` - what the project is, how the code is laid out, how to
   run it.
2. `docs/whitetail-feedback.md` - **read before every piece of work,
   update as part of it.** Half the project's purpose.
3. `docs/content.md` - how to add items, rocks, maps, scripts, art.

## Where things stand

The game is a small playable loop: walk around two maps joined by a
ladder, mine copper, tin, and iron for experience, smelt copper and tin
into bronze bars at a furnace, hammer the bars into five bronze items
at an anvil, carry it all in an inventory,
with a chat box, a right-click menu, a tabbed side panel for inventory
and skills, and a save written every tick. The game opens on a main
menu, with an options screen behind it. There is one person to talk to,
Harl, and he gives one small quest.
`readme.md` "Current state" has the full list.

**Unmerged work.** At the time of writing, the branch
`claude/gallant-brown-i8k7px` is six commits ahead of `master`, none yet
merged: all the art and atlas packing, the palette file, movement easing
and the camera, four-way pathfinding, and the right-click menu. The
branch `claude/relaxed-ride-n5sh5i` was started from its tip, not from
`master`, and adds the tabbed side panel, the skill list in data, using
an item on a loc, tin, smelting, and the anvil with its menu. Check
`git log origin/master..HEAD` before starting. Travis opens and merges
the pull requests himself; once one is merged, restart the branch from
`master` rather than stacking on it.

**Tests.** `mvn verify` runs 116 tests of the rules, with no window. All
pass.

**Tested on Linux only**: Java 8 and Java 21, software OpenGL, a virtual
display. Travis runs it on his Linux desktop in IntelliJ. Nothing has
been run on Windows of any age.

## How Travis works

- He directs and tests; he does not write the code. He pulls the branch,
  runs it in IntelliJ, and reports what is not right. He prefers that
  good defaults be chosen and shown to him over being asked to specify
  behavior up front.
- He develops Whitetail by hand, slowly by his own account, in parallel.
  When he releases a version he will say what changed. Then: go through
  the open entries in the feedback file, mark the fixed ones, remove the
  game's workarounds listed in its table, and raise `whitetail.version`
  in `pom.xml`.
- **Do not create art, music, or sound.** He sources assets. Say what is
  missing.
- **After pulling a change that adds a dependency to `pom.xml`, IntelliJ
  needs "Reload All Maven Projects"**, or it reports "package does not
  exist". This has bitten once. Mention it whenever the POM gains a
  dependency.
- Content is plain JSON files, checked strictly at startup. He chose that
  over opaque files with editors: tools get built if and when one is
  needed.
- Behavior goes in scripts where it reasonably can. The right-click menu
  was the stated exception: Java.

## Decisions that are not obvious from the code

- **Windows 98 is the ultimate target, not the current one.** Whitetail
  cannot run there yet; section A of the feedback file says why. So the
  game need not *work* on Windows 98, but must stay *possible*: Java 5
  language and library (the build enforces the library), software
  drawing, nothing a Pentium III could not do.
- **The world is separate named maps** joined by ladders and stairs, not
  one continuous map. Map state is saved per map as "empty until game
  tick N", so a map the player has left needs no ticking.
- **Saving is every tick in which something changed**, on a writer
  thread. There is no save command, deliberately: it stands in for an
  online game's lack of one.
- **One script per category of loc.** Every ordinary ore rock runs
  `[oploc,_ore_rock]` and differs only in data. The script language has
  no loops, so a repeated action reruns its script with `again()`.
- **Art is addressed by its own sheet and cell everywhere.** The atlas
  tool packs 36 sheets into 2 atlases and the game reads an index; no
  content knows about atlases. After changing `sprites.json`,
  `palette.json`, or a source image, the atlases must be rebuilt, and the
  game says so if they are stale.
- **Item ids are in saved games.** Rename an item by giving it a new id
  and listing the old under `"was"`.
- **Anything that walks owns a `Mover`**, so that every character moves
  alike. The sprite and animation half of a character is still inside
  `Player`; it should be pulled out the same way when the first NPC
  arrives.

- **A menu is three parts: data, Java, script.** Travis asked whether
  the smithing menu should "just be Java". The answer built: what can be
  made is `recipes.json`; one Java window, `MakeMenu`, draws any list
  from the list alone; and the script decides when to show it and what a
  pick does, pausing on `make_open` until the player chooses. Future
  menus (dialogue options, shops) should follow the same split.
- **Lost City is available as a reference.** Travis follows that
  project, a faithful reimplementation of 2004 RuneScape whose source is
  public. It may be consulted for how RuneScape did something. It is not
  a specification: this game is single-player and offline and need not
  match it. It was not consulted for the make menu.

- **Dialogue is Java drawing driven by script**, the same split as the
  make menu. `chatnpc`, `chatplayer`, and `choiceN` pause the script;
  `Session` holds the talk on show; `DialogueBox` draws it where the
  chat box is. Travis asked for it plain, partly because anything
  permanent waits on the 384 by 216 picture: parchment, text, "Click
  here to continue", centered choices lit on hover.
- **The chat box and dialogue are different things**, as in RuneScape.
  The chat box is for game messages ("You swing your pickaxe") and, one
  day, players typing. Dialogue is for talk with people, and now and
  then the character thinking aloud. Quest completion is announced in
  the chat box; there is no banner, journal, or quest tab yet, by
  Travis's choice for now.
- **Quest progress is a script player variable**, `%harl_axe`, saved
  with the game like any other. There is no notion of a quest in Java.
  A journal will want a list of quests in data, each naming its
  variable.
- **Menus are laid out for 384 by 216** and centered on whatever the
  picture is, so they will not need redoing when the picture shrinks.
  The world scene's interface is still laid out for the larger picture.
- **Widgets are whole cells of the purchased interface art**, assembled
  by `Skin.java` from cells named in `interface.json`: buttons, a frame,
  checkboxes, a slider. Anything new on a menu should be built from
  those rather than drawn with rectangles.
- **One character, one save, read at startup.** Travis has said the
  game will support several characters and does not yet know how. Play
  therefore just enters the world; the save was already loaded, or a new
  game begun, before the menu appeared. Do not build on this.
- **Options take effect and are saved as they are changed.** There is no
  Apply or Cancel. Only fullscreen needs a relaunch.
- **Feedback IDs C39 and C40** were first committed as a second C35 and
  C36. Check the last ID in the file before adding an entry; the
  sections are long and the newest entry is not always at the bottom.

## Map editing: the plan Travis agreed

Travis asked for a way of making maps that is low friction for a person
and that AIs can use too, without two representations drifting apart. The
plan has three steps; the first is done.

1. **Done: the file format, the checks, and the pictures.** The map file
   is the only form a map has (`MapFile.java`, and "Maps" in
   `docs/content.md`). One grid holds everything; a map defines its own
   symbols for people, named points, and the like. Travis raised the
   danger of things placed by coordinates landing one tile off, so
   nothing is placed by coordinates at all. `MapTool` tidies the files
   and draws each map to `docs/maps/`.
2. **Next: the editor.** A separate desktop tool in Swing, beside
   `MapTool` under `src/test`, not shipped with the game. Travis chose
   this over an editor scene inside the game. It must read and write the
   same file through `MapFile`, take its palette of things from
   `tiles.json` and `npcs.json`, and draw with the game's own code
   (`World.Build`, `Sprites.Draw`, as `MapTool` does), so that it shows
   what the game shows. Paint walls, floors, and things; place and move
   defined symbols and edit their settings; undo; save.
3. **Then: "play from here"**, launching the game with the character at
   a chosen tile.

Things the format is meant to grow into, none built: monster spawns with
a wander range, fishing spots, and areas of terrain that scripts change.
Each is a new kind of `!define`. A kind's settings belong on its define
line; when there are several kinds, declare what settings each takes in
data, as loc categories do. An area bigger than a tile has no home yet;
a second grid marking areas is the likely answer.

Known limits of step 1:

- **A map has about ninety symbols to share** between its tileset and
  its defines. The editor will spend defines freely, one for each
  thing-and-floor pair the inference gets wrong. A big varied map could
  run out.
- **Nothing checks that every place can be reached**, or that a point
  named in a script exists.
- **Saved positions are columns and rows**, so reshaping a map can move
  a saved player or leave a rock's emptied state on the wrong tile.

## Known rough edges

- **Movement still looks slightly "gritty"** to Travis, after one round
  of fixing. The camera no longer makes the character shiver; what
  remains is most likely whole-pixel stepping at low speed, possibly
  frame pacing if vsync is not taking effect on his machine (`maxFps` in
  `config.ini` set to the refresh rate would test that). He expects the
  planned CRT filter to soften it. Motion cannot be judged from still
  frames; say so rather than claim it is fixed.
- The apostrophe in the small font sits wide: "can't" has a gap.
- Dropped items vanish; there are no items on the ground.
- Using one item on another does nothing yet; only item on loc has a
  trigger (`oplocu`).
- **The "->" in "Use Copper ore -> Furnace" shows a gap** between the
  dash and the angle bracket in the small font, like the apostrophe.
- **A plain click on the furnace only explains.** Smelting needs an ore
  used on it, as Travis asked. The script language cannot share code
  between two trigger blocks (feedback C40), so making the plain click
  smelt too would mean a second copy of the script.
- **Iron ore's icon changed.** The grey ore picture is now tin's; iron
  ore is the copper ore picture through the `iron` color map. Not looked
  at closely in the running game.
- Copper and iron rocks still use the cave sheet's rock with a plain
  grey rock for "mined out". Tin uses the Magma Maze rocks, which have
  proper mined-out pictures in copper, grey, and gold; the others could
  move to those.
- A script paused in a `delay` is not saved.
- Player animations and the menu's option lists are still in Java, not
  data. The skill list moved to `data/skills.json`.
- **Skill and tab icons are stand-ins.** Mining shows a pickaxe and
  Smithing a hammer from the item art; the tabs show coins and a star.
  Travis will draw real ones; they are not in the asset pack. A skill
  with no picture in `skills.json` is drawn as its first letter.
- **Smithing is bronze only.** The furnace's script knows one alloy;
  the anvil's serves any list in `recipes.json`. Smelting bronze has no
  level requirement and never fails. No hammer is needed at the anvil.
- **The anvil's picture is a stand-in**, `frozen_cavern` [0,23], a grey
  iron weight. Every sheet was searched and the art has no anvil.
- **The made items do nothing.** They cannot be worn or wielded. Their
  levels and costs are placeholders loosely after RuneScape: one bar
  and 12.5 experience for small things, three bars for large.
- **The make menu makes until the bars run out.** There is no "make 1 /
  5 / 10". Clicking elsewhere stops it. Counting down a number would
  need arithmetic the script language lacks (feedback C38).
- **People are part of the map.** Harl is a symbol his map file defines
  (`!define H npc harl`), standing on a tile like a rock. He cannot move, and the
  character sprite is still inside `Player`. Real NPCs that walk will
  replace this; `Npc.java` and the `[opnpc,...]` trigger should survive
  it, the tile placement will not.
- **Portraits are stand-ins**: a 32 pixel square with the name in it.
  Travis intends real portraits. The player is called "You".
- **Speech longer than four lines is cut off**, silently. Nothing
  measures a script's text against the box.
- **Dialogue shows the font's apostrophe gap** more than anything else
  has: "aren 't", "I 'll".
- **An item cannot be used on a person** yet; only talking.
- **The system message box has only been seen on Linux**, over a
  window. Whether it comes to the front over a fullscreen game, and on
  Windows, is untested.
- **Escape in the world goes to the menu, where Escape quits**, so two
  quick presses close the game. There is no "are you sure".
- **The main menu is bare**: a frame, a title in the heading font, three
  buttons. The art pack has banners and embellishments not yet used.
- **The make menu has no scrolling.** It runs to a new column every ten
  recipes, which fits two columns in today's view. A long list will need
  another answer, more so at 384 by 216.
- **A skill's current level is always its earned level.**
  `Skills.CurrentLevel()` exists so the panel can show "current/earned",
  but nothing raises or lowers a level yet, and scripts' `stat_level`
  reads the earned one.
- Which tab is open is not saved; the game opens on the inventory.

## What Travis has said is coming

- **A smaller picture: 384 by 216 game pixels**, not started. He has
  checked that everything fits. The reason is a **post-processing CRT
  filter** for modern machines, which looks better the more real pixels
  there are per game pixel (5 by 5 rather than 2 by 2). The chat box and
  side panel will need laying out again, and `Screen.PRESET_SIZES` and
  its viewport arithmetic will change. Once the picture is presented
  through a shader, the camera's leftover fraction of a pixel can be
  applied there, which is the real cure for the grit.
- **Menu options as properties of the thing**, in data. He said the
  drawing came first and the options would be ironed out later.
  `Actions.java` builds the lists today.
- More content generally: monsters with random drops, fishing, smithing
  and crafting, quests. NPCs "need to move the same way the player does".
- Music from OpenGameArt, with permissive licenses. Whitetail's audio
  loads WAV only, and its OpenAL library needs Windows XP or later.

## Candidate next steps

None is agreed; they are in rough order of how well they fit what
exists.

1. The 384 by 216 picture and the interface re-layout, since everything
   visual built before it will need redoing after it.
2. Items on the ground: drop, pick up, despawn, saved per map. It fits
   the tick-stamped map state already there.
3. Option lists from data, with "Use item on thing".
4. More Smithing: iron smelting, which means making the furnace's
   script as data-driven as the anvil's; more metals and items. The
   Magma Maze item art has a grey and a gold bar besides the one used
   for bronze.
5. The first NPC or monster, which forces the character sprite out of
   `Player`.

## Working in the cloud environment

These applied to the session that wrote this; check they still hold.

- Each repository is cloned under `/home/user`. Whitetail is read-only in
  practice: nothing has ever been committed to it from here.
- The game needs Whitetail installed in the local Maven repository:
  `mvn -q install` in the Whitetail checkout. That needs a JDK 8
  (`apt-get install openjdk-8-jdk`) and a `~/.m2/toolchains.xml` naming
  it as version 1.8. `apt-get update` has to be run first, or the
  packages are not found.
- To see the game: start `Xvfb :77 -screen 0 1920x1080x24`, install
  `x11-xserver-utils` (LWJGL 2 needs the `xrandr` program) and `xdotool`,
  run `java -ea -jar target/wily-tench.jar` with `DISPLAY=:77`, drive it
  with `xdotool`, and capture with ImageMagick's `import -window root`.
  Set `XDG_DATA_HOME` to a scratch directory so the save and logs go
  there. The window is 1280 by 720 at the screen's center, at scale 2.
- A fatal error opens a modal dialog and the run hangs. Read
  `fatal.log` and `session.log` in the data directory.
- Clicking by screen coordinates is error-prone because the view moves.
  Prefer a test that drives `Session` directly; use the live run to look,
  not to prove.
