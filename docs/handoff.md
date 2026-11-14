# Handoff

Written for whoever picks the project up next, and last brought up to
date during the third working session. It holds what the other documents do not: where
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
`claude/relaxed-ride-n5sh5i` is eleven commits ahead of `master` and
waiting for Travis to merge it: the main menu and options, dialogue and
the first quest, the map file format, the map tools and editor, the 384
by 216 picture and game frame, and Travis's first sprites. Everything in
this file describes that branch. Check `git log origin/master..HEAD`
before starting. Travis opens and merges the pull requests himself; once
one is merged, restart the working branch from `master` rather than
stacking on it.

**Tests.** `mvn verify` runs 152 tests of the rules, with no window. All
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
  tool packs 37 sheets into 2 atlases and the game reads an index; no
  content knows about atlases. After changing `sprites.json`,
  `palette.json`, or a source image, the atlases must be rebuilt, and the
  game says so if they are stale.
- **Item ids are in saved games.** Rename an item by giving it a new id
  and listing the old under `"was"`.
- **Anything that walks owns a `Mover`**, so that every character moves
  alike, and a `Mover` knows which of four ways it faces. Asked to face
  something part way through a step, it keeps facing the way it is
  travelling until the step ends (`ShownFacing()` is what is drawn,
  `Facing()` what it will be): a character arriving beside a rock used
  to slide its last tile already turned to the rock. The sprite and
  animation half of a character is still inside `Player`; it should be
  pulled out the same way when the first NPC walks. The six-picture
  layout of a character is already general.

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
- **The main menu and options screen are laid out inside 384 by 216**,
  which is now the whole picture.
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

## The 384 by 216 picture and the game frame

Done. The picture is 384 by 216 game pixels, and the interface is one
drawing by Travis, `images/frame.png`, laid over the view of the world.

- **Draw order**, in `WorldScene.onRender`: the world into the view; the
  marks on it; the frame, whose rounded corners cover the view's square
  ones; the interface, on the frame; floating menus and tooltips last.
- **`data/layout.json` says where things are on the frame**: the view,
  the parchment, the slots, the tab rows, the open space above the tabs.
  The numbers were measured from the frame's pixels, and `FrameTest`
  checks them against it. When Travis redraws the frame with something
  moved, the file changes and the Java does not. He has said the frame
  will get more polish.
- **`Bitmap` is the second way of drawing**: a whole PNG of any size,
  read at startup with no tool to run. `Gfx.DrawBitmap` draws one.
- **Two palettes for now.** `palette.json` holds the purchased art's
  colors and then the 32 of carp32, Travis's own palette, named
  `carp_...`. The frame is drawn in carp32. Travis intends to redraw
  every sprite in carp32 by hand, after which the old colors go. The
  interface's own colors on the frame are named by use in `Palette`
  (`TEXT`, `TEXT_WARN`, `MARK`, and so on) and all come from carp32.
- **`art/` is for Travis's own source files** that the game does not
  read; `art/carp32.png` is the palette as a picture. What the game
  reads is under `src/main/resources`.

**Two layers.** Since the third session the picture is no longer
finished by the game. `Screen` keeps two index buffers and puts them
together on the screen with OpenGL 1.1:

- **The world layer**, the view and one pixel more to the right and
  below. The sprite renderer draws into it, and `WorldScene` points
  `Gfx` at it for the marks that belong to tiles (the destination
  corners, the hover outline).
- **The picture**, 384 by 216 as before: the frame, the interface,
  menus, the hover label. Wherever it is left at `Palette.BACKGROUND`
  (index 0) the world shows through, in a frame where the scene called
  `Screen.ShowWorld()`; in the menu scenes, which do not, index 0 is
  the background color as it always was. So nothing drawn over the
  world may use index 0 to mean black.
- **The slide.** `ShowWorld(fineX, fineY)` says how many screen pixels
  up and left to shift the world layer, less than one game pixel.
  `Camera` is made with a grain, the screen pixels to a game pixel,
  and gives `X()`, `Y()` in game pixels and `FineX()`, `FineY()` in
  screen pixels. At a grain of one, which is what a small screen
  gives, everything is as it was before.
- This was agreed with Travis as the seam for what comes later: the
  game hands over two layers and an offset, and what shows them can
  differ by machine. Today there is one presenter, fixed-function
  OpenGL 1.1, which is meant to suit the ThinkPad too. The CRT filter
  is the second, a shader sampling both layers (below). If LWJGL cannot
  open a window on Windows 98 (feedback A2), a third would put the
  layers together in memory and ignore the slide. The mouse still
  reads the world in whole game pixels, so a click can be off by less
  than one.

**The CRT filter.** Built in the third session, as the second
presenter. Travis has written such filters himself, owns CRTs, and
named Bisqwit's write-up (github.com/bisqwit/crt-filter) as the best
he knows; the filter follows that write-up's steps. That repository
has no licence, so none of its code was used.

- **It is an option, off by default**, `crt` in `config.ini` and a
  checkbox on the options screen, decided at launch like fullscreen.
  Travis was firm that it must be optional.
- **`Crt.java` runs it; `shaders/crt.frag` is it.** One fragment
  shader, GLSL 1.20, one pass, no framebuffer objects. The shader's
  opening comment lists the steps, and the constants under "To taste"
  are the look: scanline width and depth, mask depth, glow, beam blur.
  Travis has not seen it yet and will have opinions; expect to tune.
- **It needs OpenGL 2.0 and at least three screen pixels to a game
  pixel.** Without either, the game logs why and carries on unfiltered,
  and the options screen says "Not possible here" beside the checkbox.
- **It moves light and adds none.** Measured on screenshots, every
  flat area averages to the same linear color with the filter as
  without, to three decimals. That took two departures from the
  write-up, both explained in the shader: the scanlines and mask are
  held back for colors with no headroom, and the glow is mixed in, not
  added. A brighter, bloomier picture is a matter of changing that.
- **No curvature, vignette, noise, or flicker**, deliberately: those
  are what make such filters disliked, and curvature would bend the
  mouse.
- **Up and down, the world moves in whole game pixels with the filter
  on** (`Screen.GrainY()` is 1): a row of game pixels is a scanline.
  Across, it still slides by screen pixels. If that looks chunky on a
  vertical walk, the alternative is to blend two rows of the world
  into each scanline, at the cost of a soft world whenever the slide
  is not zero.
- **Only seen on Mesa's software OpenGL**, in still frames.

**The video signal.** Also the third session. Travis named EMMIR's
NTSC-CRT (github.com/LMP88959/NTSC-CRT) and asked for it beside the CRT
filter. They are different things and either can be on alone: this is
the cable, that is the tube. The signal runs first and gives back a
picture, which is then shown the plain way or through the filter.

- **`Ntsc.java` is a port of EMMIR's C**, the standard NTSC system
  only, integers only like the original. His license allows any use
  and asks for a credit, which is in the file and the readme. **It was
  checked against his C byte for byte**: the same picture through both,
  six frames, with noise and without, at his default controls and at
  the ones in use, not one pixel different. The C is not in this
  repository, so `mvn verify` cannot repeat that; whoever changes
  `Ntsc.java` should, by compiling `crt_core.c` and `crt_ntsc.c` with a
  small `main` that sets `raw`, `as_color`, an output 240 rows tall,
  and the saturation and contrast in `Ntsc.java`, and comparing output
  rows 5 to 220.
- **S-Video is this project's own**, not his: brightness and color
  kept in two arrays through the same code, and a sharper brightness
  filter. Travis agreed to it as the fallback should composite make
  the writing unreadable.
- **`Signal.java` is the game's side**: it puts the two layers
  together sample by sample (about two samples to a game pixel, so the
  world still slides by less than a pixel across), turns the carrier's
  phase over sixty times a second by the clock, and keeps the two
  decoded pictures of a scene that has not changed so that standing
  still costs nothing.
- **The setting** is `signal` in `config.ini`, `rgb`, `svideo`, or
  `composite`, and three option buttons on the options screen, decided
  at launch. `signalNoise`, 0 to 100, is in the file only; Travis said
  noise and flicker are a subject for later. It needs two screen
  pixels to a game pixel and nothing of OpenGL beyond 1.1.
- **The set's controls were calibrated**: saturation, contrast, and
  brightness chosen, for each kind of cable, so that the 32 colors of
  carp32 come back nearest themselves. At EMMIR's defaults the game
  looked washed out. Strong reds still lose a good deal on composite.
- **Cost**: about 7 ms a frame on the cloud machine's processor, all on
  the game's one thread, measured outside the game. That is fine at 60
  frames a second and too much for the default cap of 100. Not
  measured on Travis's machine. If it matters, the rows are
  independent once each line's sync is found and could be shared out
  among threads.
- **What it does to this game, seen in still frames**: composite
  fringes the small font with color, readable but busy; the dithered
  walls blend and shimmer. S-Video leaves writing clean. How the crawl
  and the 60-a-second phase flip look in motion nobody has seen yet.
- PAL (EMMIR's PAL-CRT) was not looked at.

**Travis's own sprites have begun to arrive**, in `art/`, in carp32:

- `art/cave.png` is the purchased cave sheet redrawn, cell for cell, and
  has taken its place as the sheet `cave`. Its things (mushrooms, rocks,
  torches, the ladder) still have solid black backgrounds, so each
  paints a black square over its floor. Travis has been told; the rule
  is in `docs/content.md`: see-through for anything that stands on the
  floor. Nothing in the tools works around it.
- `art/wizard.png` is the player's character, in the six-picture layout
  `Player.java` describes: sideways (facing left), away, toward, two
  walking pictures each. (The last two pairs were first wired the wrong
  way round; Travis caught it.) Travis wants characters at about the level of
  Final Fantasy III or IV, and never liked the purchased heroes, which
  can serve as monsters. There are no pictures of working with a tool,
  so mining and smithing show the walk pictures, fast. NPCs will use
  the same layout; Harl is still a purchased picture.
- The iron color map carries pairs for both palettes. The screen's
  empty color is now carp32's black.

Left in the old palette and the purchased interface art, to be redone
when Travis draws for them: the main menu and options screen, the
right-click menu, tooltips, the make menu, the label and outline over
the world. The space above the tabs only names the open tab; it is kept
for a map button, gauges, and a tick spinner. Six of the eight tabs are
empty. Skill and tab icons are still stand-ins.

Chat and talk pack their lines a pixel closer than the font's height,
nine pixels apart, to get five rows and a typing row (or a name, four
lines, and "Click here to continue") onto the 58 pixel parchment. A tall
letter under one with a tail can touch it.

## Map editing: the plan Travis agreed

Travis asked for a way of making maps that is low friction for a person
and that AIs can use too, without two representations drifting apart. The
plan has three steps; the first two are done.

1. **Done: the file format, the checks, and the pictures.** The map file
   is the only form a map has (`MapFile.java`, and "Maps" in
   `docs/content.md`). One grid holds everything; a map defines its own
   symbols for people, named points, and the like. Travis raised the
   danger of things placed by coordinates landing one tile off, so
   nothing is placed by coordinates at all. `MapTool` tidies the files
   and draws each map to `docs/maps/`.
2. **Done: the editor.** A separate desktop tool in Swing, under
   `src/test`, not shipped with the game; Travis chose this over an
   editor scene inside the game. `MapEdit` is the editor with no window:
   the map, every change that can be made to it, and undo, all tested.
   `MapEditor` is the window on it. `MapPicture` draws maps for both the
   editor and `MapTool` with the game's own code. The editor reads and
   writes the map file through `MapFile` and takes what can be painted
   from `tiles.json` and `npcs.json`. "The map editor" in
   `docs/content.md` says how to use it.
3. **Next: "play from here"**, launching the game with the character at
   a chosen tile.

Things the format is meant to grow into, none built: monster spawns with
a wander range, fishing spots, and areas of terrain that scripts change.
Each is a new kind of `!define`. A kind's settings belong on its define
line; when there are several kinds, declare what settings each takes in
data, as loc categories do. An area bigger than a tile has no home yet;
a second grid marking areas is the likely answer.

Wanted in the editor before long, deliberately not built yet. Travis
said to keep likely features in mind without building them:

- **Settings on a define.** A define has a kind, one value, and an
  optional floor. A monster spawn will need more (which monster, how far
  it wanders). The "This map" list and its add buttons are where a
  settings panel belongs; `MapFile.Define` and its one-line syntax will
  need to carry named settings.
- **Areas**, for terrain that scripts change and for wander ranges shown
  on the map: probably a second grid in the file and a second layer in
  the editor. `MapEdit` holds its state in one small class so that a
  layer can be added to what undo covers.
- **Fixing the floor under a thing by painting**, rather than through
  the "thing on a chosen floor" dialog.
- **Editing `game.json` and the data files** from the editor: listing a
  new map, adding a person. Today those are hand edits and a restart.
- **Seeing how maps join**: where each point is arrived at from.
- Flood fill, copy and paste of a region, and a tile-wide brush.

Known limits of the editor as built:

- **Only tried on a virtual display with no window manager.** Painting,
  the rectangle, picking, the lists, the dialogs, undo by Ctrl+Z, new
  map, save, middle-button panning, and painting outward past each edge
  were all driven there and looked at. How it feels on a real desktop,
  at real sizes, is for Travis to say. In particular the window asks to
  be maximized, which only a window manager can grant; here it was seen
  only to size itself to the screen.
- **Travis's first round of changes, done:** the view drags with the
  middle button; the map sits in 48 tiles of empty space on every side
  and is painted outward freely; the Map menu's "add a row", "add a
  column", and "crop" are gone, cropping being part of saving (and of
  the canonical form, so `MapTool` crops too); it starts maximized. He
  has said to expect a steady stream of such changes.
- **One map at a time**, and the tileset cannot be changed after a map
  is made.
- **It reads the data files as they were when it started**, from the
  build's copy of them.
- **Saving a map that is not in `game.json`** still draws its picture
  into `docs/maps/`, and `MapTool` removes it again on its next run.

Known limits of step 1:

- **A map has about ninety symbols to share** between its tileset and
  its defines. The editor will spend defines freely, one for each
  thing-and-floor pair the inference gets wrong. A big varied map could
  run out.
- **Nothing checks that every place can be reached**, or that a point
  named in a script exists.
- **Saved positions are columns and rows**, so reshaping a map can move
  a saved player or leave a rock's emptied state on the wrong tile. The
  cave and dungeon each moved a few columns left when cropping became
  part of the canonical form.

## Known rough edges

- **Movement.** Two things were done in the third session, and
  Travis confirmed the first by eye.
  *A judder on starting, turning, and stopping* in steady mode: the
  camera trails the character by a whole amount, and whenever that
  grew on a frame in which the character's picture had not itself
  moved, the whole world scrolled backwards for that frame. `Camera`
  now holds a change to what leaves the view still or moving the way
  the camera is travelling; its opening comment has the reasoning.
  *The chunkiness that was left*, the view and the character shifting
  against each other a whole game pixel at a time while the trail
  changes: the trail is now counted in screen pixels, and the world is
  slid by the part of a game pixel left over when it is put on screen
  (see "Two layers" below). Checked by arithmetic and by looking at
  still frames, in which the world sits at offsets that are not whole
  game pixels; **whether it looks smooth in motion is for Travis to
  say.** What it does not do: the character still steps a whole game
  pixel at a time, and in a steady walk the world steps with it. A
  growing trail grows only on the frames where the character's picture
  steps, so setting off is less smooth than stopping. An about-face at
  full speed can still pull the view back a little. Making walking
  itself smooth would mean drawing each moving thing at a part of a
  pixel, on its own quad; not built, and Travis has not asked for it.
- **"Click here to continue" not lighting under the pointer**, on some
  screens of talk, reported by Travis in the third session and **not
  reproduced**: driven on a virtual display through Harl's first
  conversation, every prompt lit, and `DialogueTest` now draws every
  speech and checks. The lit and plain colors (`carp_red`, `carp_wine`)
  are close, which may be part of it. Ask which speech and where the
  pointer was.
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
- The menu's option lists are still in Java, not data. Where the
  player's pictures are is in `game.json`; how fast they change is in
  `Player.java`.
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
  another answer.
- **A skill's current level is always its earned level.**
  `Skills.CurrentLevel()` exists so the panel can show "current/earned",
  but nothing raises or lowers a level yet, and scripts' `stat_level`
  reads the earned one.
- Which tab is open is not saved; the game opens on the inventory.

## What Travis has said is coming

- **Menu options as properties of the thing**, in data. He said the
  drawing came first and the options would be ironed out later.
  `Actions.java` builds the lists today.
- More content generally: monsters with random drops, fishing, smithing
  and crafting, quests. NPCs "need to move the same way the player does".
- Music from OpenGameArt, with permissive licenses. Whitetail's audio
  loads WAV only, and its OpenAL library needs Windows XP or later.

## What Travis has asked for and is not built

Gathered from the second session, roughly in the order he raised them:

- **Above the tabs:** a map button that brings up a static "paper" map
  in place of the view; hitpoint and prayer gauges; perhaps a tick
  indicator that turns once a game tick. There will be no minimap.
- **Up to eight tabs**, four above the inventory and four below. Two
  exist. A quest journal or quest tab is wanted, explicitly not yet.
- **Real dialogue portraits**, and real skill and tab icons, which he
  will draw.
- **Several characters to play**, how not yet decided.
- **See-through backgrounds on the cave sheet's things**, which is his
  to draw, and every remaining sprite redrawn in carp32.
- **Working pictures for characters**, at about the level of Final
  Fantasy III or IV.
- **A steady stream of map editor changes**, and step 3 of the map
  plan, "play from here".
- He has doubts about the map format's limits (about ninety symbols a
  map; no home for areas) and said to cross that bridge when it comes.

## Candidate next steps

None is agreed. Travis has more than once asked what to do next and then
chosen something not on the list, so treat this as a menu.

1. NPCs that walk: pull the sprite out of `Player`, give Harl the
   six-picture layout when Travis draws him, then monsters, spawns as
   map defines, and combat. This is what gives mining and smithing a
   purpose.
2. Wearing and wielding the smithed items.
3. Items on the ground: drop, pick up, despawn, saved per map. It fits
   the tick-stamped map state already there, and monster drops need it.
4. "Play from here" in the map editor, and the editor features listed
   above.
5. The widgets above the tabs, once there is something for them to
   show.
6. Option lists from data. More Smithing: iron, which means making the
   furnace's script as data-driven as the anvil's.
7. A quest journal, with quests listed in data.

## Working in the cloud environment

These applied to the session that wrote this; check they still hold.

- Each repository is cloned under `/home/user`. Whitetail is read-only in
  practice: nothing has ever been committed to it from here. A session
  may start without it; Travis can ask for it to be cloned, to
  `/home/user/whitetail`.
- Maven's downloads fail here when made several at once ("Could not
  find artifact" for things that plainly exist). Add `-U
  -Daether.connector.basic.threads=1` the first time; after that `-o`
  works.
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
  there. The window is 1536 by 864 at the screen's center, at scale 4,
  so game pixel (x, y) is screen pixel (192 + 4x, 108 + 4y). The game
  opens on the main menu; Play is at about game pixel (192, 88).
- To see the map editor: the same display, and `java -cp
  "target/classes:target/test-classes:target/lib/*"
  com.alchemysoftware.wilytench.MapEditor cave.txt` after `mvn verify`.
  There is no window manager, so it cannot be seen maximized.
- A system message box (the options screen's, or a fatal error's) blocks
  the game until it is clicked.
- The next free feedback ID is C50.
- A fatal error opens a modal dialog and the run hangs. Read
  `fatal.log` and `session.log` in the data directory.
- Clicking by screen coordinates is error-prone because the view moves.
  Prefer a test that drives `Session` directly; use the live run to look,
  not to prove.
