# Wily Tench

A tick-based 2D adventure game in the spirit of early RuneScape, built on
[Whitetail](https://github.com/Travis-Dunn/Whitetail). Everything happens in
real time on a 600 ms game tick: actions are checked against skill levels and
a roll of the dice, and usually pay out experience.

It is meant to run on a ThinkPad A21m under Windows 98 SE with Java 5, and
equally on current Windows and Linux machines. That is why the code is
Java 5, draws everything in software, and asks OpenGL only to put the
finished picture on screen.

**Current state:** the game opens a window and shows a test card. There is no
game yet.

## Running it in IntelliJ

1. Install Whitetail into your local Maven repository, as usual (its
   `release.sh` does this). The version this game expects is the
   `whitetail.version` property at the top of `pom.xml`.
2. Open this folder in IntelliJ as a Maven project.
3. Set the project SDK to a **JDK 8** (File > Project Structure > Project).
   Newer JDKs refuse to compile Java 5 code.
4. Pick the **Wily Tench** run configuration (it is shared in `.run/`) and
   press Run.

You should see a dark checkerboard with an amber square that hops one tile
along the top row every 600 ms, an outline that follows the mouse, and a
grey panel on the right holding the seventeen colors of the art. Escape or
the window's close button quits.

## Running it without an IDE

    mvn package
    java -jar target/wily-tench.jar

`mvn package` needs the same `~/.m2/toolchains.xml` JDK 1.8 entry that
building Whitetail does. To try the game on another machine, copy
`target/wily-tench.jar` and the `target/lib` folder next to each other and
run the same `java -jar` command there. No other setup is needed: the game
unpacks LWJGL's native libraries by itself.

## Settings and logs

The game keeps a folder per user:

| System | Folder |
| ------ | ------ |
| Windows 2000 and later | `%APPDATA%\AlchemySoftware\WilyTench` |
| Windows 98 | `Application Data\AlchemySoftware\WilyTench` under the Java home folder, normally `C:\WINDOWS` |
| Linux | `~/.local/share/AlchemySoftware/WilyTench` |

- `config.ini` holds the settings. Edit it and restart.
  - `fullscreen` - `true` or `false`.
  - `vsync` - `true` or `false`.
  - `maxFps` - frame rate cap, used when vsync is off or ignored.
- `session.log` describes the most recent run, including which graphics
  driver was found and what size the picture ended up. Look here first when
  something is wrong.
- `fatal.log` collects every fatal error, with stack traces.

## Layout of the repository

- `src/main/java` - the game.
- `DD-core`, `DD-UI` - the purchased Demonic Dungeon art, untouched.
- `docs/whitetail-feedback.md` - notes on Whitetail gathered while building
  the game: bugs, surprises, and things that were awkward.
