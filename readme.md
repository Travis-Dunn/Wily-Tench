# Wily Tench

Wily Tench is two projects in one repository.

**It is a game.** A 2D adventure in the spirit of early RuneScape. The
world runs in real time on a 600 ms tick. Whatever you do - fight a
monster, mine a rock, catch a fish (perhaps even a tench), smith a blade -
is checked against a skill level and a roll of the dice, and usually earns
experience in that skill. There will be monsters with random drops,
gathering and crafting skills, and quests.

**It is a test of Whitetail.**
[Whitetail](https://github.com/Travis-Dunn/Whitetail) is Travis's
hand-written Java game library for old hardware. The game is built on it,
on the same stack, and uses as much of it as it sensibly can. Every bug,
annoyance, misleading name, and documentation error met along the way is
written down in [`docs/whitetail-feedback.md`](docs/whitetail-feedback.md)
so that Whitetail can be improved. This goal matters as much as the game.

## If you are an AI working on this project

Travis directs this project and reviews it, but does not write its code;
an AI does. Whichever AI that is, on every turn of work, for as long as
the project lasts:

1. **Read [`docs/whitetail-feedback.md`](docs/whitetail-feedback.md)
   before you start.** It lists traps in Whitetail and explains why parts
   of the game are written the way they are.
2. **Add to it as you work.** Anything about Whitetail that was wrong,
   surprising, awkward, or missing gets an entry, however small. If you
   wrote a workaround, say where.
3. **Amend it when Whitetail changes.** Travis develops Whitetail in
   parallel and will tell you when he releases a version and what it
   fixed. Mark those entries fixed, remove the workarounds they list, and
   raise `whitetail.version` in `pom.xml`.

The file itself spells out the format. Treat it as part of the definition
of done for every change.

When the job is adding or changing content (an item, a rock, a map, a
script), [`docs/content.md`](docs/content.md) is the guide, and `mvn
verify` checks the result.

Some other standing facts:

- **Do not create art, music, or sound.** Use what is in the repository;
  if something is missing, say so and Travis will find it.
- **Target platforms.** The game must run on current Linux Mint and
  Windows 11. Linux is where it is developed and tested day to day. The
  ultimate goal is Windows 98 SE with Java 5 on a ThinkPad A21m, but
  Whitetail is not ready for that yet (section A of the feedback file says
  what is missing), so Windows 98 does not have to *work* today. It does
  have to stay *possible*: keep the code Java 5, keep drawing in software,
  and do not lean on anything a Pentium III could not do.
- **Java 5 language and library only.** The build enforces the library
  part. No lambdas, no try-with-resources, no diamond operator, no
  `String` switch.
- **Code style follows Whitetail's**, in `docs/constitution.md` and
  `docs/style.md` of that repository: static methods in PascalCase, no
  overloads, 80 columns, block comments, messages gathered at the bottom
  of the file.

## Adding content

What the game contains is kept apart from how it works. Items, the things
on a map that can be used, what map characters mean, the maps themselves,
and the scripts that say what happens are all text files under
`src/main/resources`, and adding to them needs no Java.

**[`docs/content.md`](docs/content.md) explains every file**, with an
example of each kind of entry and step-by-step recipes. The files are
checked strictly when the game starts and by `mvn verify`, and a mistake
is reported in plain words naming the file and the entry. To choose a
picture, look it up in the labeled catalog images in `docs/sprites/`.

## Current state

You can walk around two small maps, a cave and a dungeon, mine the
rocks in them, smelt copper and tin into bronze, and hammer the bronze
into weapons and armor.

- **Walking.** Left click a tile and the character walks there by the
  shortest route, one tile per tick, or as close as it can get. Movement
  is north, south, east, or west only. A walk eases in and out, and the
  view follows a little behind and catches up. The name of whatever is under the mouse is shown at the top
  left.
- **Mining.** Left click an ore rock. The character walks up to it and
  swings; every third tick a swing lands and the dice are rolled against
  your Mining level. A win gives one ore and some experience and leaves
  the rock empty for a few ticks. Copper and tin, the grey rock, can be
  mined from level 1; iron, the dark red rock, needs level 15. You need
  the pickaxe you start with and a free inventory slot.
- **Smelting.** The room at the east end of the cave has a furnace. Use
  copper ore or tin ore on it and the character walks over and smelts:
  every three ticks one of each ore becomes a bronze bar and 6.2
  Smithing experience, until one ore runs out or you click elsewhere.
- **Smithing.** Beside the furnace is an anvil. Use a bronze bar on it
  and a menu asks what to make: an axe, gauntlets, a sword, a warhammer,
  or a chainbody, each with the Smithing level it needs and the bars it
  takes. What you lack is shown in red. Click one and the character
  keeps making it, one every three ticks, until the bars run out or you
  click elsewhere. Escape, or a click off the menu, closes it.
- **Using an item on something.** Choosing Use on an item selects it,
  marked by an amber outline. Whatever is pointed at next offers, first,
  to have the item used on it: "Use Copper ore -> Furnace". Doing
  anything, or pressing Escape, lets go of it. Ore on the furnace and
  bars on the anvil are what it does so far.
- **Options.** Whatever the mouse is over has a list of things to do to
  it. A left click does the first, which the label at the top left of the
  view names; a right click opens the whole list as a menu. Tiles offer
  Walk here and, where something stands, its own verb and Examine. Items
  offer Use, Drop, and Examine.
- **Side panel.** One box, and a row of tabs above it that choose what
  it shows: the 28-slot inventory, or the skills. Each skill is a small
  rectangle with its icon and its level, current over earned ("1/1").
  Point at one for a tooltip with the experience earned and the
  experience at which the next level comes. Dropped items vanish, for
  now.
- **Skills.** Mining and Smithing. Their icons, and the tabs', are
  stand-ins from the item art until real ones are drawn.
- **Chat box.** The game writes its messages there; anything you type
  appears on the bottom row and Enter says it. The mouse wheel, or Page Up
  and Page Down, scroll back.

- **Two maps.** The world is made of separate maps joined by ladders and
  stairs. Click the ladder in the cave and the character walks over and
  climbs down to the dungeon; the stairs there lead back. Each map keeps
  its own state, so a rock mined in the cave refills on schedule while you
  are in the dungeon.
- **Scripts.** What mining a rock or climbing a ladder does is not Java
  but a script, and the game's content is meant to be written that way
  from here on. See "Scripts" below.
- **Saving.** There is no save command. As in an online game, what has
  happened has happened: the game writes itself to disk after every tick
  in which something changed, and picks up where it left off next time.
  See "Saved games" below.

- **Main menu.** The game opens on it. Play continues your saved game,
  or begins a new one if there is none; the line under the buttons says
  which. Options opens the options screen. Quit, or Escape, closes the
  game. From the world, Escape comes back here, and the world stands
  still until you press Play again.
- **Options.** Fullscreen, vertical sync, the frame rate limit, and the
  walking style, each with a widget. A change is acted on and written
  to `config.ini` at once. Fullscreen alone waits for the next launch,
  and the game says so with a system message box when you leave the
  screen.

There are no monsters or quests yet.

## Running it in IntelliJ

1. Install Whitetail into your local Maven repository, as usual (its
   `release.sh` does this). The version this game expects is the
   `whitetail.version` property at the top of `pom.xml`.
2. Open this folder in IntelliJ as a Maven project.
3. Set the project SDK to a **JDK 8** (File > Project Structure > Project).
   Newer JDKs refuse to compile Java 5 code.
4. Pick the **Wily Tench** run configuration (it is shared in `.run/`) and
   press Run.

## Running it without an IDE

    mvn package
    java -jar target/wily-tench.jar

`mvn package` needs the same `~/.m2/toolchains.xml` JDK 1.8 entry that
building Whitetail does. To try the game on another machine, copy
`target/wily-tench.jar` and the `target/lib` folder next to each other and
run the same `java -jar` command there. No other setup is needed: the game
unpacks LWJGL's native libraries by itself. On Linux the `xrandr` program
must be installed, which it is by default on Mint.

## Tests

`mvn verify` also runs the tests in `src/test`, which play the game's
rules through tick by tick with no window: walk to a rock, mine it, check
the ore, the experience, the messages, swing by swing; build the option
lists and choose from them; place a menu in every corner of the screen; load the real data
files, maps, and scripts, and confirm that broken data is explained; use a ladder and
follow its script tick by tick; travel between maps; save a game and load it back. They drive a `Session` directly, which is why the
game's rules live there and not in the scene. They use no test framework, in the
same way Whitetail's do: a public class named `*Test`, whose public `test*`
methods pass by returning and fail by throwing. New rules should come with
tests like these.

## Settings and logs

The game keeps a folder per user:

| System | Folder |
| ------ | ------ |
| Linux | `~/.local/share/AlchemySoftware/WilyTench` |
| Windows 2000 and later | `%APPDATA%\AlchemySoftware\WilyTench` |
| Windows 98 | `Application Data\AlchemySoftware\WilyTench` under your home folder, which is normally `C:\WINDOWS` |

- `config.ini` holds the settings. The options screen changes them; the
  file can also be edited by hand while the game is closed.
  - `fullscreen` - `true` or `false`.
  - `vsync` - `true` or `false`.
  - `maxFps` - frame rate cap, used when vsync is off or ignored.
  - `walkStyle` - `steady` or `pulse`. With `steady`, a walk eases away
    from a standstill, holds an even speed, and eases to a stop. With
    `pulse`, every tile is eased into and out of, so a long walk surges
    once per tile.
- `session.log` describes the most recent run, including which graphics
  driver was found and what size the picture ended up. Look here first when
  something is wrong.
- `fatal.log` collects every fatal error, with stack traces.
- `wts-player.a` and `wts-player.b` are the saved game, current and
  previous.

## Saved games

The saved game holds the character's map and tile, the count of game
ticks, the experience in each skill, the inventory, every script player
variable (the `%names` scripts declare, which is where quest progress will
live), and for every map the rocks that are mined out and the tick at
which each refills. `SaveState.java` lists the exact names and shapes. A
script that was paused when the game closed is not saved.

- **To start over,** delete `wts-player.a` and `wts-player.b` from the
  folder above while the game is closed.
- **The format** is Whitetail's `comp-save-sys` container: a small binary
  file of named values with a checksum. It is not obfuscated. Two
  generations are kept, so a crash or power cut mid-write loses at most
  one tick, and the game says so in the chat box if it had to fall back to
  the older one.
- **Changing what is saved.** Adding a value needs no ceremony: loading
  leaves anything missing from a file as it is. Changing what an existing
  value *means* requires raising `SaveGame.VERSION` and converting old
  files on load. Item ids and skill ids are part of the format and
  must never be changed.
- **Cost.** One save is a few hundred bytes and, where measured, about a
  millisecond, on a thread of its own so that a slow disk cannot make the
  game stutter. Standing still with every rock full writes nothing.
- **Maps are saved by file name.** Renaming a map file strands anyone
  saved on it at the first map's start.

## How the game is put together

The game draws everything itself as 8-bit palette indices and hands
OpenGL one finished picture per frame. The art has only seventeen colors,
which is what makes that practical.

| File | What it does |
| ---- | ------------ |
| `Main`, `Game` | Startup, the frame loop, shutdown |
| `Assets` | What every scene shares: the fonts, the sprite renderer, the data files |
| `MenuScene`, `OptionsScene` | The main menu and the options screen |
| `Skin` | The widgets' pictures, assembled from cells of the interface art named in `interface.json` |
| `PushButton`, `Buttons`, `Toggle`, `Slider` | The widgets: push buttons and the clicking of them, checkboxes and option buttons, sliders |
| `DataDir`, `Settings`, `Natives` | The per-user folder, the settings and the writing of `config.ini`, LWJGL's native libraries |
| `Screen`, `Palette` | The window, the picture's size, and its colors, from `palette.json` |
| `Content`, `DataNode`, `DataError` | Loading the data files, strictly, with explanations of mistakes |
| `Sprites` | The sprite sheets and color maps, from `sprites.json`, and where the atlas tool packed each sheet |
| `Tiles` | What each map character means, from `tiles.json` |
| `World` | One map: what is on each tile, whether it is solid, and which rocks are mined out |
| `Session` | A game in progress: all the maps, the player, skills, inventory, script variables, and tick count. The rules, with no window |
| `TileView` | Draws the visible part of the map |
| `Pathfinder`, `Mover` | Finding a four-way route and walking it, one tile per tick, eased according to the `walkStyle` setting. Anything that walks has a `Mover` |
| `Player` | The player's character: a `Mover`, its sprite, and what it is heading for |
| `Camera` | Which part of the map is in view; trails the player by a steady whole number of pixels |
| `Scripts` | The bridge to the script system: the commands scripts can call and the triggers the game fires |
| `WorldScene` | The window onto a `Session`: input, drawing, and saving after each tick |
| `Gfx` | Rectangles and outlines drawn straight into the picture |
| `Font`, `Text` | Bitmap fonts, and drawing writing with them |
| `ChatBox` | The message history and typing line under the view |
| `SidePanel` | The inventory and the skills, on tabs, to the right of the view; the tab pictures come from `interface.json` |
| `Recipes` | The lists of what can be made from what, from `recipes.json` |
| `MakeMenu` | The window that shows one of those lists and asks what to make |
| `Tooltip` | The small box beside the pointer that says more about what it is over |
| `Actions` | What can be done to each tile and item, and the doing of it |
| `ContextMenu` | The list of options for what is under the mouse, and the right-click menu that shows it |
| `Item`, `Inventory` | The kinds of thing that can be carried, from `items.json`, and the 28 slots they are carried in |
| `Skills` | The skills there are, from `skills.json`, and a player's experience and levels in them |
| `Loc` | The kinds of thing on a map that can be used, from `locs.json`, and the values scripts read from them |
| `Sheet` | Reads an atlas file, for both the sprite renderer and interface icons |
| `SaveState` | Turns a `Session` into a saved game and back |
| `SaveGame` | Keeps the saved game on disk, writing on its own thread |

Maps are text files in `src/main/resources/maps`; `docs/content.md`
describes them.

## Scripts

Game content lives in script files, `src/main/resources/scripts/*.whtl`,
written in Whitetail's script language. Its reference and tutorial are in
Whitetail's `comp-script-sys` folder. A script file is a list of *trigger
blocks*; the game decides when each runs. This is all of `travel.whtl`:

    [oploc,ladder]
    mes("You climb down the ladder.")
    delay(1)
    travel("dungeon.txt")

    [oploc,stairs]
    mes("You climb the stairs.")
    delay(1)
    travel("cave.txt")

Whitetail supplies the language and nothing else: every trigger and every
command is defined by the game, in `Scripts.java`, whose opening comment
is the full list of commands.

### Locs, and when scripts run

A *loc* is a kind of thing on a map that the player can use: the ladder,
rocks, the furnace. `data/locs.json` lists them. Each has a script name, a
display name, optionally a *category*, and named values for scripts to
read.

Using an item on a loc works the same way but runs `[oplocu,name]`, and
the script asks `used_item()` which item it was. `smithing.whtl` is the
example. A loc with no such script answers "Nothing interesting happens."

Clicking a loc walks the character up beside it and runs
`[oploc,name]`. If there is no script with the loc's own name, the game
runs the one for its category instead, written with a leading underscore.
That is how **one script serves every ordinary ore rock**:
`mining.whtl` holds a single block, `[oploc,_ore_rock]`, and asks the rock
it is running for what to give:

    inv_add(loc_string("ore"))
    loc_empty_for(loc_int("respawn"))
    stat_xp("mining", loc_int("xp"))

**To add another ordinary rock** (mithril, say) takes a few lines in
three data files and no script; `docs/content.md` has the recipe. A rock
that should behave differently gets its own `[oploc,its_name]` block, which
takes precedence.

### Asking the player to choose

A script can stop and ask what to make. `make_open("anvil_bronze_bar")`
shows that list from `data/recipes.json` as a menu and the script waits
there, for as long as the player takes. When a recipe is picked the
script carries on, and `make_item()`, `make_int("uses")`,
`make_int("level")`, `make_int("xp")`, and `make_string("material")`
describe the pick. If the menu is closed instead, the script simply ends.
The anvil's block in `smithing.whtl` is the example.

The split is deliberate: **what can be made is data, how the menu looks
is Java, and what picking does is script.** `MakeMenu.java` draws any
list the same way, from the list alone.

### Things worth knowing when writing one

- The language has no loops, so one run of the mining script is one
  swing. A swing that fails calls `again()`, and the game runs the script
  once more. `repeating()` tells such a rerun apart from the player's own
  click.
- While a script is in progress the player is busy. Unless the script has
  called `interruptible()`, clicks are ignored until it ends; the ladder
  works that way. Mining is interruptible: clicking elsewhere cancels it.
- `delay(n)` is part of the language and pauses for `n` game ticks.
- Scripts name items by their ids in `items.json`, such as
  `"copper_ore"`, and skills by their ids in `skills.json`, such as
  `"mining"`.
- A mistake in a script's grammar or types is reported when the game
  starts, with the file, line, and a pointer to the spot. Naming an item,
  skill, map, or loc value that does not exist is reported when the line
  runs.

To add a script file, write it and add its name to `scripts` in
`data/game.json`. To give scripts a new ability, register a command in
`Scripts.java` and add it to the list at the top of that file.

## Art

The `DD-...` folders hold the purchased Demonic Dungeon art by VEXED,
untouched: the core pack, the interface pack, and five expansions. The
game does not read those PNGs. `data/sprites.json` lists the images in
use, which is nearly all of them, and `AtlasTool` (under `src/test`)
converts them: it turns every pixel into a position in the game's palette,
packs all the sheets into a few large atlas files in
`src/main/resources/sprites`, records where each sheet went in
`index.json` beside them, and draws a labeled catalog of each sheet into
`docs/sprites/`. All of that is committed. Rerun it whenever
`sprites.json`, `palette.json`, or a source image changes, with the
**Build Atlases** run configuration or:

    mvn test-compile exec:java@atlases

The game checks at startup that its atlases match those files and says so
if they do not. `docs/content.md` has the details.

Things the art does not have, which shape the game:

- Characters face one way and have no walking or working animation. They
  have idle, hurt, angry, and sick poses; mining borrows "angry".
- There are only orange and grey rocks to mine. Grey serves as "mined
  out", and other ores are the orange rock recolored by swapping palette
  colors.

## Fonts

Whitetail has no text rendering, so the game has its own. Fonts are
bitmaps, drawn one font pixel to one game pixel with no smoothing, which
is what keeps writing as crisp as the rest of the pixel art.

| Game font | Made from | Used for |
| --------- | --------- | -------- |
| `small.fnt` | `fonts/helvR08.bdf` | Chat, labels, nearly everything |
| `bold.fnt` | `fonts/helvB08.bdf` | Emphasis (not used yet) |
| `title.fnt` | `fonts/helvB12.bdf` | Headings |

The sources are the 8 and 12 pixel bitmap Helvetica that shipped with the
X Window System, hand-drawn for low resolution screens in the 1980s. Their
permissive license is reproduced in `fonts/readme.txt`.

To change a font, put a `.bdf` bitmap font or a `.ttf` pixel font in
`fonts/`, point a row of the `JOBS` table at the top of `FontTool` at it
(a `.ttf` also needs the pixel size it was designed for), and run the
**Build Fonts** run configuration or:

    mvn test-compile exec:java@fonts

Nothing else needs to change; layouts measure the font they are given.
