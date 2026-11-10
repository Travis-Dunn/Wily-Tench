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

You can walk around a small cave. Left click a tile and the character
walks there by the shortest route, one tile per tick, or as close as it can
get if the tile is solid. The view follows. Escape quits.

There are no skills, monsters, items, or interface yet.

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

Things the art does not have, which shape the game: characters face one
way and have no walking animation (they have idle, hurt, angry, and sick
poses), and the scenery is caves and dungeons, with no outdoors.
