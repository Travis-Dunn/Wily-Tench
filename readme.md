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

## Current state

You can walk around a small cave and mine it.

- **Walking.** Left click a tile and the character walks there by the
  shortest route, one tile per tick, or as close as it can get. The view
  follows. The name of whatever is under the mouse is shown at the top
  left.
- **Mining.** Left click an ore rock. The character walks up to it and
  swings; every third tick a swing lands and the dice are rolled against
  your Mining level. A win gives one ore and some experience and leaves
  the rock empty for a few ticks. Copper can be mined from level 1; iron,
  the dark red rock, needs level 15. You need the hammer you start with
  and a free inventory slot.
- **Side panel.** Mining level, experience so far against experience
  needed, and the 28-slot inventory. Point at an item to see its name;
  right-click to drop it. Dropped items vanish, for now.
- **Chat box.** The game writes its messages there; anything you type
  appears on the bottom row and Enter says it. The mouse wheel, or Page Up
  and Page Down, scroll back.

- **Saving.** There is no save command. As in an online game, what has
  happened has happened: the game writes itself to disk after every tick
  in which something changed, and picks up where it left off next time.
  See "Saved games" below.

Escape quits.

There are no monsters, other skills, or quests yet.

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
the ore, the experience, the messages; save a game and load it back. They use no test framework, in the
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

- `config.ini` holds the settings. Edit it and restart.
  - `fullscreen` - `true` or `false`.
  - `vsync` - `true` or `false`.
  - `maxFps` - frame rate cap, used when vsync is off or ignored.
- `session.log` describes the most recent run, including which graphics
  driver was found and what size the picture ended up. Look here first when
  something is wrong.
- `fatal.log` collects every fatal error, with stack traces.
- `wts-player.a` and `wts-player.b` are the saved game, current and
  previous.

## Saved games

The saved game holds the character's map and tile, the experience in each
skill, and the inventory. `PlayerSave.java` lists the exact names and
shapes. The state of the map itself, such as which rocks are mined out, is
not saved yet; that waits on deciding how the world is divided up.

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
  files on load. Item numbers and skill save names are part of the format
  and must never be changed or reused.
- **Cost.** One save is a few hundred bytes and, where measured, about a
  millisecond, on a thread of its own so that a slow disk cannot make the
  game stutter. Standing still writes nothing.

## How the game is put together

The game draws everything itself as 8-bit palette indices and hands
OpenGL one finished picture per frame. The art has only seventeen colors,
which is what makes that practical.

| File | What it does |
| ---- | ------------ |
| `Main`, `Game` | Startup, the frame loop, shutdown |
| `DataDir`, `Settings`, `Natives` | The per-user folder, `config.ini`, LWJGL's native libraries |
| `Screen`, `Palette` | The window, the picture's size, and its colors |
| `Sprites` | Names for the atlases, layers, and frames |
| `World` | A map: what is on each tile and whether it is solid |
| `TileView` | Draws the visible part of the map |
| `Pathfinder`, `Player` | Finding a route and walking it, one tile per tick |
| `WorldScene` | Ties the above together: input, updating, drawing |
| `Gfx` | Rectangles and outlines drawn straight into the picture |
| `Font`, `Text` | Bitmap fonts, and drawing writing with them |
| `ChatBox` | The message history and typing line under the view |
| `SidePanel` | Skills and inventory, to the right of the view |
| `Item`, `Inventory` | The kinds of thing that can be carried, and the 28 slots they are carried in |
| `Skills` | Experience and levels |
| `Ore`, `Mining` | The ore rocks and their odds, and the mining job itself |
| `Sheet` | Reads a sprite sheet file, for both the sprite renderer and interface icons |
| `PlayerSave` | Turns the player's state into a saved game and back |
| `SaveGame` | Keeps the saved game on disk, writing on its own thread |

Maps are text files in `src/main/resources/maps`, one character per tile;
the key is at the top of `World.java`.

## Art

`DD-core` and `DD-UI` hold the purchased Demonic Dungeon art by VEXED,
untouched. The game does not read those PNGs. `AtlasTool` (under
`src/test`) converts the parts in use into Whitetail's `.wsa` atlas format
in `src/main/resources/sprites`, and those files are committed. Rerun it
only when you need more of the art, with the **Build Atlases** run
configuration or:

    mvn test-compile exec:java@atlases

Things the art does not have, which shape the game:

- Characters face one way and have no walking or working animation. They
  have idle, hurt, angry, and sick poses; mining borrows "angry".
- The scenery is caves and dungeons, with no outdoors.
- There is no picture of ore and none of a pickaxe. Ore in the inventory
  uses the picture of the rock it came from, and rocks are mined with the
  hammer from the weapons art.
- There are only orange and grey rocks. Grey serves as "mined out", and
  other ores are the orange rock recolored by swapping palette colors.

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
