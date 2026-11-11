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
ladder, mine copper and iron for experience, carry ore in an inventory,
with a chat box, a right-click menu, a tabbed side panel for inventory
and skills, and a save written every tick.
`readme.md` "Current state" has the full list.

**Unmerged work.** At the time of writing, the branch
`claude/gallant-brown-i8k7px` is six commits ahead of `master`, none yet
merged: all the art and atlas packing, the palette file, movement easing
and the camera, four-way pathfinding, and the right-click menu. The
branch `claude/relaxed-ride-n5sh5i` was started from its tip, not from
`master`, and adds the tabbed side panel and the skill list in data. Check
`git log origin/master..HEAD` before starting. Travis opens and merges
the pull requests himself; once one is merged, restart the branch from
`master` rather than stacking on it.

**Tests.** `mvn verify` runs 68 tests of the rules, with no window. All
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
- "Use" on an inventory item does nothing yet.
- A script paused in a `delay` is not saved.
- Travel lands on a map's `@`, not beside the ladder arrived by.
- Player animations and the menu's option lists are still in Java, not
  data. The skill list moved to `data/skills.json`.
- **Skill and tab icons are stand-ins.** Mining shows a pickaxe and
  Smithing a hammer from the item art; the tabs show coins and a star.
  Travis will draw real ones; they are not in the asset pack. A skill
  with no picture in `skills.json` is drawn as its first letter.
- **Smithing is listed but cannot be trained.** It was added so the
  skills tab has its rectangle ready. Nothing awards experience in it.
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
4. Smithing itself, which Travis has named as the second skill: a
   furnace and smelting, then an anvil. The skill and its place in the
   interface exist; it needs locs, bar items (the Magma Maze item art has
   ore and bars), a script, and probably "Use item on thing" from step
   3.
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
