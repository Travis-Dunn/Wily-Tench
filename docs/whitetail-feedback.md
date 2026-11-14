# Whitetail feedback

Wily Tench exists partly to test Whitetail by using it in earnest. This
file is the running record of what that turns up: bugs, wrong or
misleading documentation, awkward APIs, and anything standing between
Whitetail and its Windows 98 target.

## How to keep this file

**Anyone working on this project, human or AI, reads this file before
starting and updates it before finishing, every time.**

- **Before working:** read it. Several entries describe traps, and several
  are the reason a piece of game code looks the way it does.
- **While working:** when Whitetail surprises you, slows you down, or
  forces a workaround, add an entry. Small things count: a misleading name
  is worth a line.
- **When Travis reports a new Whitetail release:** go through the open
  entries against what he says changed. Mark each fixed one
  `Status: fixed in <version>`, and remove the game's workaround for it if
  the entry lists one. Leave fixed entries in place as history.
- Entry IDs (A1, B3, ...) are permanent. New entries take the next free
  number in their section. Never renumber.

Each entry says how it was found, because some are things that actually
happened and some are predictions:

- **Hit** - it happened while building or running the game.
- **Read** - found by reading Whitetail's source or docs.
- **Predicted** - inferred from evidence, but needs the real machine to
  confirm.

Everything so far was found against **Whitetail 0.1.12**, and tested on
Linux only (Java 8 and Java 21, Mesa software OpenGL). Nothing has been
run on Windows of any age.

## Game workarounds to remove when Whitetail catches up

| Entry | Workaround in the game |
| ----- | ---------------------- |
| A1 | `DataDir.java` picks the Windows 98 data folder itself |
| A4 | `Screen.java` asks for OpenGL 1.0 only, and logs the real driver strings |
| A5 | `Screen.java` picks the `RGBA_UBYTE` layout |
| B6 | `pom.xml` names a version on every Whitetail dependency |
| C1 | `Natives.java` unpacks LWJGL's native libraries |
| C3 | `Screen.java` checks desktop width before asking for height |
| C5, C6 | `Game.java` and `Screen.java` raise their own fatal errors |
| C7 | `Screen.java` calls `GL11.glClear` and `glGetError` directly |
| C8 | `Game.java` has its own frame rate cap |
| C13 | `Game.java` starts `RunFlag` before the scenes initialize |
| C14, C21 | `Sheet.java` reads `.wsa` files itself, and `Gfx.DrawFrame()` draws from them |
| C15, C25 | `AtlasTool.java` converts the art and packs every sheet into a few atlases; `Sprites.java` reads its index |
| C16 | `WorldScene.java` carries fractional milliseconds between frames |
| C18 | `Font.java`, `Text.java`, `Gfx.java`, and `FontTool.java` are the game's own text and drawing code |
| C19 | `ChatBox.java` uses only the direction of the mouse wheel |
| C22 | `Fixture.java` (tests) guards one-time initialization with a static flag |
| C23 | `SaveGame.java` does its writing on a thread of its own |
| C24 | `SaveGame.Load()` calls `Describe()` and then `Read()` |
| C26 | `Scripts.java` keeps the game in progress in a static field for command handlers to reach |
| C27 | `data/game.json` lists every script and map file by name |
| C28 | `Session.java` reruns a script that called the game's `again()` command |
| C29 | `Scripts.java` registers `join` and `str` commands for building messages |
| C41 | `Settings.java` keeps the settings' live values and rewrites `config.ini` itself |
| C42 | `Assets.java` loads what every scene needs before any scene starts |
| C43 | `Assets.DiscardTicks()` throws ticks away in scenes where the world stands still |
| C44 | `OptionsScene.java` asks for a relaunch and calls `JOptionPane` itself |
| C47 | `IndexedPng.java` reads indexed-color PNGs |
| C48 | `Screen.java` keeps a second texture for the world layer, and another for a picture that came down the video cable, and draws both with `GL11` itself; `Crt.java` keeps one for the picture |
| C49 | `Crt.java` compiles and runs its shader with `GL20` calls of its own |
| C37 | `Scripts.java` splits asking and answering into `make_open` and `make_item`, and `choiceN` and `chosen`, with the pick kept in `Session` |
| C45 | `Scripts.java` registers `choice2`, `choice3`, and `choice4` |
| C38 | `Scripts.java` registers `inv_del_n` because a script cannot count |
| C31, C32, C33, C34 | `DataNode.java` wraps `comp-json` with checked, typed reading; data files use lists with ids and a `note` convention |

---

## Entries

### A. Things that threaten the Windows 98 target

Windows 98 is the project's ultimate target, not its current one; for now
the game only has to work on Linux. This section is the list of what has
to change before Windows 98 can work. A1, A4, and A5 are Whitetail's own.
A2 and A3 are in LWJGL 2.9.3's native libraries, so fixing them means
Whitetail changing what it builds on. All five still need confirming on
the real machine.

**A1. `UserDir.Resolve()` cannot succeed on Windows 98.** *(Read;
Predicted)*  
Status: open.  
On Windows it takes its base from the `APPDATA` environment variable and
fails when that is unset. Windows 95, 98, and ME never set it; it arrived
with Windows 2000. So on the target machine the very first call of a
session fails, there is no log directory, and nothing else can start.
The game works around it in `DataDir.java` by using
`<user.home>\Application Data` when `APPDATA` is missing. Suggestion: give
`UserDir` that same fallback, since it is the component's whole job.

**A2. LWJGL 2.9.3's `lwjgl.dll` probably cannot open a window on
Windows 98.** *(Read; Predicted)*  
Status: open.  
I looked inside the DLL that ships in `lwjgl-platform-2.9.3-natives-windows`:

- Its header declares a minimum of Windows 5.0, which is Windows 2000.
- It creates its window with the wide-character calls `RegisterClassW`,
  `CreateWindowExW`, `DefWindowProcW`, `PeekMessageW`, and friends.
  Windows 98 has those names only as stubs that fail.

The likely symptom is an `LWJGLException` saying "Failed to register window
class", which `Window.Init()` will log as a warning before returning false.
Windows 2000 has everything the DLL asks for, so the same laptop under
Windows 2000 should be fine. LWJGL's Java classes themselves are Java 5
bytecode, so the JVM side is not the problem.

If this turns out as predicted, the options I can see are an older LWJGL 2
whose natives still support Windows 9x, or a second presenter that shows
the software framebuffer through plain AWT. The game is set up so the
second is cheap: it only ever draws into an `int[]`.

**A3. LWJGL 2.9.3's `OpenAL32.dll` will not load on Windows 98 or
Windows 2000.** *(Read; Predicted)*  
Status: open.  
It imports `GetModuleHandleExW`, which first appeared in Windows XP, and
`SwitchToThread`, which Windows 9x lacks. `comp-audio-openal` is therefore
XP-and-later as shipped. Not a problem yet, since the game has no sound.

**A4. `Window.Init()` only works on old drivers when asked for OpenGL
1.0, and the tutorial's fallback chain stops at 1.2.** *(Read)*  
Status: open.  
`Window.Init()` always hands LWJGL a `ContextAttribs`. LWJGL turns that
into a plain old-style context request only for version 1.0 exactly. For
every other version, including 1.1 through 2.1, it requires the driver
extension `WGL_ARB_create_context` (or the GLX one), which drivers from
before roughly 2008 do not have; the DLL's error text is "Could not create
context (WGL_ARB_create_context)". So the chain in `comp-window/tutorial.md`
(3.3, then 2.1, then 1.2) fails all three steps on exactly the machines the
last step is meant for.

Asking for 1.0 is not a downgrade: the driver returns its newest
compatible context. On this Linux box a 1.0 request produced "4.5
(Compatibility Profile)". The game asks for 1.0 only. Suggestions: end the
tutorial's chain with 1.0 and say why; or have `Window` skip `ContextAttribs`
below 3.0.

Related: the session log line reads `Window initialized [1280x720, OpenGL
1.0]`, which is the version requested, not the one obtained. Logging
`GL_VERSION`, `GL_RENDERER`, and `GL_VENDOR` there would make every session
log say what hardware it ran on. The game logs them itself for now.

**A5. `GL12SoftwareFramebuffer` needs OpenGL 1.2 even for a layout that
does not.** *(Read; Predicted)*  
Status: open.  
`Init()` sets `GL_CLAMP_TO_EDGE`, a 1.2 feature, and treats the resulting
`GL_INVALID_ENUM` on a 1.1 driver as fatal. Two places a 1.1 driver can
turn up: Microsoft's built-in software OpenGL, which Windows uses whenever
the real driver is missing, and possibly the ATI Rage Mobility's own
Windows 98 driver. Because the texture uses nearest filtering and is only
ever sampled inside the uploaded area, plain `GL_CLAMP` would give the same
picture. With that one change, the `RGBA_UBYTE` layout would be pure
OpenGL 1.1.

Along the same lines, the tutorial recommends the `UINT_8888` layouts, which
are also 1.2-only. The game picks `RGBA_UBYTE` on little-endian machines
for that reason.

### B. Bugs and mismatches

**B1. `SceneManager.Input()` is documented as `OnInput()`.** *(Hit)*  
Status: open.  
The method is `Input()`. `comp-scene-sys/readme.md` calls it `OnInput()`
twice, and the tutorials for `comp-input-sys`, `comp-input-lwjgl`, and
`comp-input-mapper` all show `SceneManager.OnInput()`. Only the scene
tutorial has the real name. Copying from the input tutorials does not
compile.

**B2. The text-entry example in `comp-input-sys/tutorial.md` does not
compile.** *(Read)*  
Status: open.  
It reads `e.down` and `e.ch`. Those fields are private; the accessors are
`isDown()` and `getChar()`, as the readme says.

**B3. `comp-display-layout/readme.md` refers to `comp-platform`.** *(Read)*  
Status: open.  
Several times, under "Ownership boundaries". There is no such component;
it means `comp-window`.

**B4. Every log entry is followed by a blank line.** *(Hit)*  
Status: open.  
`Logger` ends each entry with its own line break, and the style guide has
every message end in `"\n"`, so the files are double spaced. One of the two
should go.

**B5. `ConfigFileParser.readExistingFile()`** *(Read)*  
Status: open.  
A public static method in camelCase, against rule 3 of the constitution,
and not mentioned in the readme. After reporting "not initialized" it
carries on and returns the flag anyway.

**B6. The parent POM manages only some of the components.** *(Hit)*  
Status: open.  
`dependencyManagement` leaves out `comp-scene-sys`, `comp-config-sys`,
`comp-json`, `comp-timer`, `comp-gc-priest`, `comp-software-framebuffer`,
`comp-camera`, `comp-renderer-gl33`, and `comp-audio-openal`. A game cannot
lean on the parent for versions, so Wily Tench names the version on every
dependency through one property.

**B7. Small ones.** *(Read)*  
Status: open.  
`Timer`'s assertion messages do not end in `"\n"` and do not use
`ErrorStrings`, unlike its neighbors. `Timer.Init()` returns a boolean that
can only be true.

### C. Annoyances and gaps

**C1. Nothing says how LWJGL's native libraries get loaded.** *(Read)*  
Status: open.  
LWJGL 2 finds its natives through `java.library.path` or
`org.lwjgl.librarypath`, and a game that only follows the readmes sets
neither, so its first `Window` call cannot load them. Every game
needs an answer to this, and no component or document offers one. Wily
Tench unpacks the libraries from the classpath at startup (`Natives.java`).
That is about a hundred lines that would sit well in Whitetail, or at
least a paragraph in `comp-window/tutorial.md`.

**C2. On Linux, LWJGL 2 needs the `xrandr` program installed.** *(Hit)*  
Status: open.  
Without it `Window.GetDesktopWidth()` fails with "Window failed to query the
desktop display mode because an exception was encountered" and an `ArrayIndexOutOfBoundsException` from inside LWJGL in
`fatal.log`. Linux Mint has `xrandr` out of the box, so this should not
bite there, but it is worth a line in the readme since the message gives no
hint.

**C3. One problem, two dialogs.** *(Hit)*  
Status: open.  
`GetDesktopWidth()` and `GetDesktopHeight()` each raise their own fatal
dialog. Calling both and then checking, which is the natural way to write
it, shows the player two modal dialogs for one failure. A single query
returning both numbers would avoid it.

**C4. A fatal error during startup freezes a headless or scripted run.**
*(Hit)*  
Status: open.  
The modal dialog waits for a click forever. That is right for a player. It
made my automated launches hang until I took a thread dump to see why. A
system property to suppress the dialog would help testing and CI.

**C5. `UserDir.Resolve()` fails silently for a player.** *(Read)*  
Status: open.  
It reports to standard error, which a double-clicked game does not have.
The game has to put up its own dialog, and can only say "look at the
console". Returning the reason, or making it retrievable, would let the
game show it.

**C6. `Window.Init()` returning false is silent too.** *(Read)*  
Status: open.  
That is deliberate, for fallback chains, and the tutorial says to stop. It
does not say that nothing has been shown to the player at that point, so
the game must raise the fatal error itself.

**C7. A software-rendered game still has to call LWJGL directly.** *(Hit)*  
Status: open.  
Two things forced `import org.lwjgl.opengl.GL11` into the game:
clearing the window, so the bars beside a letterboxed picture are black
rather than garbage, and checking `glGetError()`, since `Present()`
deliberately does not. A `Clear` and an error check on the framebuffer
component, or on `Window`, would let such a game depend on Whitetail alone.

**C8. No frame limiter.** *(Hit)*  
Status: open.  
`Window` exposes vSync but nothing like LWJGL's `Display.sync()`. Where
vSync is off or the driver ignores it, the loop spins a full processor. The game has its own sleep-based cap.

**C9. Scenes have no fixed-tick callback.** *(Hit)*  
Status: open.  
`Timer` provides fixed ticks and `Scene` provides `onUpdate(delta)`, and
nothing joins them. The game drains `Timer.Tick()` inside `onUpdate()`,
which works; a sentence in the scene tutorial would have saved me checking
whether I had missed something.

**C10. `comp-scene-sys` needs `comp-event-sys` for one type.** *(Hit)*  
Status: open.  
`Scene.handleEvent(Event)` is the only link. A game that never fires an
event still carries the dependency and implements the method.

**C11. `ConfigEntry` is unlike its siblings.** *(Hit)*  
Status: open.  
A public constructor where everything else uses a static `Create`, bounds
passed as `Object`, and a raw `ArrayList` parameter on
`ConfigFileParser.Init()`.

**C12. The build's JDK requirements are not written down.** *(Read)*  
Status: open.  
`docs/readme.md` says Whitetail compiles with Temurin 8, and `.sdkmanrc`
says Java 17. Both are true: Maven runs on 17 and compiles through a JDK 8
toolchain. But nothing says a `~/.m2/toolchains.xml` with a 1.8 entry is
required. I wrote one before my first build because the POM asks for
the toolchain; someone who had not read the POM would meet a "no toolchain
found" failure instead.

**C13. A scene cannot say that it failed to initialize.** *(Hit)*  
Status: open.  
`Scene.onInit()` and `SceneManager.InitScenes()` both return nothing. A
scene that hits a fatal error while loading has stopped `RunFlag`, but
`comp-error-sys/tutorial.md` says to call `RunFlag.Start()` *after*
initialization, which switches it straight back on and runs the main loop
over a half-built scene. The game starts the flag before initializing and
checks it afterwards. Either the tutorial should say that, or
`InitScenes()` should return a boolean.

**C14. `SpriteAtlas` tells the game nothing about itself.** *(Hit)*  
Status: open.  
Its fields are final but package-private, so after
`SpriteAssetBuilder.AtlasFromResource()` a game cannot ask an atlas for
its width, its frames per row, or its frame count. To turn "column 5, row
1 of the sheet" into a frame number the game has to hard-code the frames
per row for each file and keep it in step with the file by hand. (Since
C21 the game sidesteps this by reading the file itself.) Rule 5 of
the constitution suggests these should simply be public.

**C15. Getting ordinary art into an atlas takes a custom tool.** *(Hit)*  
Status: open.  
The readme is upfront that RGB-to-index conversion is out of scope, but
the practical result is that the first thing any game must do is write a
converter. Mine had to: map RGBA pixels to palette indices, and pad each
sheet to power-of-two dimensions, because `SpriteAtlas` rejects anything
else and bought sprite sheets are never that shape. A small
`AtlasFromRgbaImage(image, palette, spriteSize)` that did both would cover
most cases. `ImageBuilder.FromPNG()` decoded the purchased sheets without
complaint, which was the part I expected trouble from.

**C16. `Timer` speaks seconds and `SpriteAnimation` speaks
milliseconds.** *(Hit)*  
Status: open.  
`Timer.GetDt()` returns seconds as a double; `SpriteAnimation.update()`
takes whole milliseconds. Truncating each frame's delta loses up to a
millisecond a frame, which at high frame rates slows animations
noticeably, so the game carries the remainder itself. `Timer` already has
the exact nanosecond figure internally; a `GetDtMs()` that did the carrying
would remove a subtle bug from every game.

**C17. There is no tile map support, but the sprite pool stood in
well.** *(Hit)*  
Status: open. More an observation than a complaint.  
The map is drawn with a fixed grid of sprites, two per visible tile, that
are re-pointed at different tiles as the view scrolls (`TileView.java`).
That fits the renderer's "work proportional to live sprites" design and
needed nothing new. The cost is three validated calls per tile
(`SetPosition`, `SetFrame`, `SetVisible`) whenever the view crosses a tile
boundary, around 5,000 calls at 640x360. Unmeasurable here; worth timing
on the ThinkPad.

**C18. Notes from writing the text renderer Whitetail does not have.**
*(Hit)*  
Status: open. Whitetail's readme says text is left out because its
contract is not yet clear; this is what the contract turned out to be for
this game, in case it helps.

- *Fonts are bitmaps drawn one font pixel to one screen pixel.* No
  scaling, no smoothing, proportional widths. For the indexed framebuffer
  `comp-sprite-sys` draws into, nothing else looks right. A glyph is an
  advance, an offset, and a 1-bit bitmap; the whole format is a dozen
  lines (see `FontTool.java`).
- *The source of truth is BDF.* It is plain text, public bitmap fonts
  come in it, and it needs no rasterizer, so the converter is exact. TTF
  is supported as a second route by drawing each glyph once, unsmoothed.
- *Drawing takes a `CharSequence` and a start and end.* That lets a
  `StringBuffer` being typed into, or part of a wrapped message, be drawn
  without creating a string, so drawing text every frame allocates
  nothing.
- *Three calls were enough:* plain, drop-shadowed, and outlined. The
  outline is precomputed per glyph when the font loads, and the whole
  run's outline is drawn before any of its letters, or each letter's
  outline eats its neighbor.
- *Measuring and line-breaking belong with the font,* not the drawing
  code: `Width()` and `Fit()` are used by layout code that never draws.
- *Text needs a clip rectangle,* and so do filled rectangles and
  outlines. `SpriteRenderer` has exactly one, fixed at `Init()`. The game
  ended up with its own small drawing layer (`Gfx.java`) alongside the
  sprite renderer, sharing the same `byte[]`. A Whitetail component for
  "indexed framebuffer primitives" - fill, outline, blit a 1-bit mask,
  with a settable clip - would be the natural home for text and would
  have saved most of this.

**C19. The mouse wheel's units are whatever the system says.** *(Hit)*  
Status: open.
`comp-input-lwjgl` documents the wheel as "LWJGL's raw delta ... in units
the operating system decides". A game cannot turn that into "lines to
scroll" without guessing, so the chat box uses only its sign and scrolls
one line per frame in which the wheel moved, which loses fast spins.
Normalizing to notches in the backend would fix it for everyone.

**C20. `InputSys` has no notion of input being used up.** *(Hit)*  
Status: open. An observation for later rather than a problem today.
The chat box reads typed characters from the event list while the scene
reads `WasPressed()` for the same keys. Today the only key the scene
reads is Escape, so nothing clashes. As soon as the game has key
bindings, typing "m" in chat will also open the map unless the game
invents its own focus rule. `comp-input-mapper` could carry a "text entry
is active, suppress plain-key actions" switch.

The mouse has now met the same thing. A left click on one of the side
panel's tabs must not also be taken as "do the first option for whatever
is under the pointer", so `SidePanel.Click()` returns whether it used the
click and `WorldScene.onInput()` stops there. With one panel that is a
single `if`; each new clickable piece of interface will add another, in
an order that has to be kept right by hand.

The right-click menu met the same thing from the mouse side. While a menu
is open it must have every click to itself: a click that dismisses it
must not also walk the character to the tile underneath. With no way to
mark a press as taken, the scene does this by order, handling the menu
first and returning early, and anything added later that reads
`WasPressed()` has to be slotted into that order by hand.

**C21. The sprite renderer cannot draw outside its one viewport, so
interface icons need a second renderer.** *(Hit)*  
Status: open.
`SpriteRenderer` clips everything to the single viewport given to
`Init()`, which for this game is the view of the world. An inventory icon
in the side panel is the same 16 pixel indexed art from the same atlas,
recolored by the same kind of color map, but it lies outside that
viewport, so the sprite renderer cannot draw it. Nor can the game draw it
by hand from the `SpriteAtlas`, whose pixels are private (C14).

The game now reads `.wsa` files itself (`Sheet.java`), gives the pixel
array to `SpriteAtlas.Create()` for the renderer, and keeps the reference
for its own `Gfx.DrawFrame()`, which repeats the renderer's inner loop
including the color map lookup. That works, and holds nothing twice,
because `Create()` retains the array it is given; but it means
`SpriteAssetBuilder.AtlasFromResource()` is unusable for any game with an
interface. Either of these would remove the duplication: an immediate
`SpriteRenderer.DrawFrame(atlas, frame, x, y, colorMap)` that ignores the
viewport, or public read access to an atlas's and a color map's data.

The interface has since grown tab buttons, skill icons, and tooltips, all
drawn this way, and a tooltip may lie across the edge of the world view.
Everything outside the map itself now goes through the game's own `Gfx`
and none of it through the sprite renderer.

**C22. Components with no `Shutdown()` are awkward to test around.**
*(Hit)*  
Status: open.
The game's rule tests start the pieces they need for each test and stop
them after. `SpriteRenderer` allows that. `ResourceStreamResolver` has no
shutdown and treats a second `Init()` as fatal, so the tests initialize it
once behind a static flag and every test shares it. `SaveStore` is the
same, and so are `Timer` and `ConfigFileParser`, though the tests do not
use those two. A
`Shutdown()` on each, even one that only clears the flag, would let tests
be independent.

**C23. `SaveStore.Write()` waits for the disk, which rules out calling
it from the game loop.** *(Hit)*  
Status: open. Partly an observation.
`Write()` synchronizes the file before returning, which is exactly what
makes it trustworthy, and also means it can take a frame or more on a
slow disk. This game saves after every tick in which something changed,
so it had to move writing to its own thread, with a one-deep "newest
state wins" queue (`SaveGame.java`). The readme does say that a game
writing on a background thread must do its own locking, so this is
allowed for, but every game that autosaves will write the same eighty
lines. A `SaveStore.WriteAsync(slot, version, name, data)` with those
semantics, plus a `Flush()`, would fit the component well.

A smaller point for frequent saving: each `setInt` boxes its value
(`new Integer`), and a payload handed to another thread cannot be reused,
so every save builds a fresh payload. It is not much garbage, but it is
per tick.

**C24. Loading takes two calls that each read and check the whole
file.** *(Hit)*  
Status: open.
`Read()` on a slot that does not exist is fatal, so the game must call
`Describe()` first, which validates every byte, and then `Read()`, which
validates every byte again. `Describe()` is also the only way to learn
the save's `gameVersion`, which is needed before reading to refuse a save
from a newer release. For the usual "load the one save if there is one"
case, a `Read()` that returned null for an absent slot, and a `SaveData`
that carried its own `gameVersion`, would make it one call and one pass.

**C25. Eight atlas banks are far too few for one atlas per sheet.**
*(Hit)*  
Status: open. Worked around in the game.
The purchased art is 36 sheets. The sprite renderer holds eight atlases,
which cannot be replaced during its lifetime, so one atlas per sheet was
never going to last; the game hit four after two maps. `AtlasTool` now
packs every sheet, whole, into as few 2048 by 2048 atlases as it can (two,
at present) and writes an index saying where each went, and `Sprites`
turns "sheet and cell" into "atlas and frame" from that index. Content
still refers to art by its own sheet, so nothing else had to change.

That is a few hundred lines of tool and loader standing between ordinary
purchased art and the renderer. It is noted here because the limit of
eight reads as generous in the readme and turns out to be the first wall
a game with real art meets; and because, together with C15, it suggests
the missing piece is less "more banks" than a supported way to build an
atlas from several images. The format coped well: a 2048 square atlas is
16,384 frames, comfortably inside the 65,536 limit.

**C26. A command handler has no way to reach the game it is acting
on.** *(Hit)*  
Status: open.
`CommandHandler.execute()` receives the `ScriptState` and nothing else,
and `ScriptState` offers the player variables and the script. A handler
for `mes` or `travel` needs the game: the chat box, the maps, the player.
The only route is a static field somewhere that the handler reads
(`Scripts.Bind()`), which is fine for one player and one game in progress
and wrong for anything else, including tests that would like two sessions
side by side. An opaque `Object context` passed to `FireTrigger()` and
readable as `state.context` would let the host hand each script its own
world, at no cost to the component's ignorance of the game.

The mining script made this sharper. A script shared by every ore rock
needs to know *which* rock it is running for, and `state.script.subject`
only says `_ore_rock`. The game keeps "the loc being used" beside the
script in `Session`, and the `loc_...` commands read it from there.

Using an item on a loc added a third thing kept that way: which item. A
trigger cannot be fired with arguments, so the furnace's script learns
the item from a `used_item()` command reading another `Session` field.
`FireTrigger(type, subject, vars, args)` with the arguments readable in
the script as locals would cover both this and the loc.

**C27. Every script file has to be listed by hand.** *(Hit)*  
Status: open.
`LoadScripts()` wants every path in one call, and
`ResourceStreamResolver.List()` does not work in classpath mode, so a game
shipping its scripts inside its jar cannot discover them. The game keeps a
hand-written list of script files (and another of maps), now in
`data/game.json`, that must be edited whenever one is added. The tutorial's promise that a designer can
"drop a modified guard.whtl next to the game" holds for changing a script
but not for adding one. A classpath `List()` is hard in general; a
manifest convention, such as `LoadScriptsListedIn("scripts", "index.txt")`,
would be enough.

The map tool met the other side of this. A development tool has to find
every map, rewrite some, and read them back as they now stand, and the
resolver in classpath mode can do none of the three, so `MapTool` reads
and writes the source folder with `java.io` and hands the text to the
same parser the game uses. That is fine for a tool, but it means the
tool and the game do not open files the same way, and the map editor
will be the same.

**C28. With no loops, anything repeated needs the host's help.**
*(Hit)*  
Status: open. Partly by design.
Mining is "swing until the rock gives". The language has no loops, on
purpose, so one run of the script is one swing, and a failed swing calls
a game command, `again()`, that makes `Session` fire the same trigger
afresh when the run ends, with a `repeating()` command so the script can
skip its opening line the second time round. It works and reads
acceptably, and it matches how the language this one descends from
re-queues itself.

Two things would make it less of a private invention. First, the
tutorial could show the pattern, since "how do I do something until it
works" is the first question a skilling script asks. Second, every rerun
is a new `FireTrigger()` and so a new `ScriptState`; a
`ScriptManager.Restart(state)` that rewound a finished state would keep
the "a run allocates nothing after its state" promise true of repeated
runs as well.

**C29. Building a sentence takes nested commands.** *(Hit)*  
Status: open. Partly by design.
There is no string concatenation and no int-to-string conversion, so the
game registers `join(text, text)` and `str(whole)`, and a message with a
number in it reads

    mes(join(join("You need a Mining level of ", str(loc_int("level"))),
            " to mine this rock."))

Commands have fixed signatures, so `join` cannot take three parts. A
built-in way to splice values into a string literal, checked at compile
time like everything else, would remove most of the noise from content
scripts, which are largely messages.

**C30. A yes-or-no command cannot be used as a condition by itself.**
*(Hit)*  
Status: open. Minor.
Conditions are always `expression op expression`, so a command that
answers yes or no is written `if (loc_empty() = 1)`. It is consistent and
quickly becomes habit; it is also the line most likely to be mistyped as
`if (loc_empty())`, which the compiler rejects with a message about a
missing operator rather than a hint that `= 1` is wanted.

**C31. Strict JSON is a hard format to edit by hand.** *(Hit)*  
Status: open. By design, but worth weighing.
The game's content now lives in JSON files read through `comp-json`,
which enforces RFC 8259 exactly: no comments and no trailing commas. For
files meant to be edited by people those two are the commonest wishes and
the commonest mistakes. The game works around the first with a
convention, a `"note"` value that any group may carry and the loader
skips. Nothing can be done about the second from outside. An opt-in
lenient mode accepting `//` comments and trailing commas would make the
component much friendlier for configuration and content, without
changing its strict default.

**C32. Duplicate keys silently keep the last value.** *(Read)*  
Status: open.
The readme documents this. For hand-edited data it means a
copy-and-paste mistake, two entries with the same name in one group,
loses one of them with no word said. Because of it the game's data files
never key a collection by name: every collection is a list of groups each
with an `"id"`, so that the game can detect duplicates itself. Reporting
a duplicate key as a parse error, or at least offering to, would let
files use the more natural `{ "copper_ore": { ... } }` shape safely.

**C33. A parsed value does not know where it came from.** *(Hit)*  
Status: open.
Parse errors carry line, column, and a caret, which is excellent. But
most mistakes in a content file are not syntax errors: a missing value, a
number where text belongs, a reference to an item that does not exist.
Those are found after parsing, by the game, and `JsonValue` has no
position, so the game's messages can say "locs.json, loc [tin_rocks],
[params]: [xp] is missing" but not which line. A `line` and `column` on
each `JsonValue` would let every consumer give errors as good as the
parser's own.

**C34. Reading values out safely takes a layer of the game's own.**
*(Hit)*  
Status: open.
`JsonValue` is a tag and five public fields, and `JsonObject.entries` and
`JsonArray.elements` are raw `Map` and `List`. Every read is therefore
"get, check for null, check the tag, read the right field, and for a
whole number check that the double is one". The game wrapped all of that
in `DataNode.java`, about three hundred lines, before it could read its
first file. Much of that is this game's error wording, but the core,
typed getters that fail with a description instead of returning a
meaningless default, is something every consumer will write. Numbers
being only `double` puts the whole-number check on the consumer too.

**C35. `comp-math` has nothing for a 2D game.** *(Read)*  
Status: open. Minor.
Easing the walk and smoothing the camera needed a smoothstep and an
exponential approach, a few lines each. I looked in `comp-math` first: it
is 3D only (quaternions and projection matrices) and depends on LWJGL's
vector classes, so a software-rendered 2D game would be adding an LWJGL
dependency to get nothing it can use. A handful of dependency-free
scalar helpers (clamp, lerp, smoothstep, frame-rate-independent damping,
floor division and modulo for negative numbers) would be used by every
game on the library; this one has written three of those five so far.

Later, a harder helper of the same family: a camera that follows a
character smoothly when the character must be drawn at whole pixels.
The game's first two attempts each had a visible fault (the character
shivering, then the world stepping backwards for single frames while
the camera's lag changed), and the rule that works took a
frame-by-frame simulation to find (`Camera.java`). Every pixel-art game
with a trailing camera will meet both. `comp-camera` is a 3D camera and
is no home for it; something beside `comp-display-layout`, which
already knows the pixel scale the rule needs, might be.

**C36. A setting that must be one of a few words has to be checked by
the game.** *(Hit)*  
Status: open. Minor.
`walkStyle` may be `steady` or `pulse`. `ConfigEntry` offers int, float,
bool, and a string bounded only by length, so the file accepts any text
and the game checks it afterwards, logs its own warning, and falls back
(`Settings.WalkStyle()`). The config system already does exactly this for
out-of-range numbers, with a good log line naming the offending text. A
`TYPE_CHOICE` taking a list of allowed strings would give choices the
same treatment.

`signal` (`rgb`, `svideo`, or `composite`) is now a second setting of
this kind, with a second copy of the same check and warning
(`Settings.ReadSignal()`).

A cosmetic thing seen in passing: when the config system appends a newly
registered key to an existing `config.ini`, it leaves a blank line before
it, so a file that has grown over several releases ends up unevenly
spaced.

**C39. Conditions cannot be combined, and there is no `else if`.**
*(Hit)*  
Status: open. Partly by design. (First written down as a second "C35"
by mistake; C35 is the `comp-math` entry.)
A condition is one comparison. "The item is neither copper ore nor tin
ore" is two nested `if`s, and "the player has both ores" is two blocks,
each with its own copy of what to do otherwise; the smelting script
checks the pair before the delay and again after it, so four near-equal
blocks (`smithing.whtl`). Smithing proper will choose among many
recipes, and without `else if` each further case nests one level deeper,
against a nesting cap of 64. `and` and `or` in conditions, and
`else if`, would shorten real scripts more than anything else has so far.

**C40. Two trigger blocks cannot share code.** *(Hit)*  
Status: open. (First written down as a second "C36" by mistake; C36 is
the entry about settings with a few allowed words.)
The furnace has two ways in: an ore used on it (`[oplocu,furnace]`) and
a plain click (`[oploc,furnace]`). It would be natural for both to smelt.
There are no procedures and one block cannot run another, so that would
take two copies of the script; instead the plain click only explains.
The same will come up for every loc that takes items, and for any two
locs that behave nearly alike but not enough to share a category. A
block that can be called from another, even with no arguments, would do.

**C37. A command that waits cannot answer.** *(Hit)*  
Status: open.
The anvil's menu is the tutorial's dialogue case exactly: a command
shows the choices and calls `suspend()`, and the host calls `Resume()`
on the click. But a handler must push its return value before it
returns, which is before the player has chosen, so the waiting command
has to be `void` and a second command fetches the answer:

    make_open($list)
    ... make_item() ...

The tutorial shows the same pair (`dlg_option` then `get_option`), so
this is known. It still means the host keeps the answer somewhere of its
own between the two (C26 again), and a script that forgets the first
command reads a stale answer. `Resume(state, value)`, supplying the
suspended command's return value, would let it be one line:
`def_string $item = make_choose($list)`.

Dialogue made the same pair a second time: `choice2("Yes", "No")` and
then `chosen()`.

**C38. Scripts cannot do arithmetic.** *(Hit)*  
Status: open. Partly by design.
An expression is a literal, a variable, or a command call; there is no
`+` or `-`. A recipe that uses three bars cannot remove them with three
calls chosen at run time (no loops) or count down (no subtraction), so
the game registers `inv_del_n(item, n)`. "Make five of these" cannot be
written in a script at all without the host counting for it. The game
could register `add(a, b)` and `sub(a, b)` commands, as it did `join`
(C29), but a script language without sums will surprise everyone who
writes content.

**C41. The config system can read settings but not change them.**
*(Hit)*  
Status: open.
`ConfigFileParser` has `Init()` and four `Get` methods, and no way to
set a value or write the file. An options screen is the obvious second
user of a settings file, and it could use none of the component beyond
the first read: the game now copies every value out at startup, keeps
them itself, and rewrites `config.ini` by hand, preserving comments and
unknown lines (`Settings.Save()` and `Settings.Merge()`), about eighty
lines. It also had to learn the file's name and format from the source,
since both are private to the parser. `Set...()` for each type, checked
against the entry's bounds like a line of the file is, and a `Save()`,
would let the component own its file. Related: `Init()` can be called
only once (C22), so none of this can be tested through the component.

**C42. Scenes are initialized in no particular order.** *(Hit)*  
Status: open.
`SceneManager` keeps its scenes in a `HashMap`, and `InitScenes()` walks
its values, so the order of `onInit()` calls is whatever the hash of the
keys gives, not the order of `RegisterScene()`. With one scene that was
invisible. With three, the two menu scenes need the fonts, the sprite
renderer, and the data files that the world scene used to load in its
own `onInit()`, and nothing guarantees that it runs first. The game
moved all shared loading out of the scenes (`Assets.java`), which is
probably where it belonged anyway, but registration order is what anyone
would assume. A `LinkedHashMap` would make it true; the readme should
say either way.

**C43. `Timer` cannot be paused.** *(Hit)*  
Status: open.
The fixed-tick accumulator fills whenever `Update()` is called, which
the main loop does every frame. While the main menu is up nothing wants
those ticks, and if they are left, the world runs through minutes of
them in one frame when the player returns. So every scene in which the
world stands still drains and discards them each frame
(`Assets.DiscardTicks()`), and a new such scene that forgets will show a
confusing bug much later. `Timer.SetTicking(false)`, or a
`DiscardTicks()`, would say what is meant. The same will be wanted for a
pause, and after a modal dialog, which stalls the loop for as long as it
is up.

**C44. Fullscreen is decided once, and only fatal errors get a message
box.** *(Hit)*  
Status: open. Two small gaps met together.
`Window` has no way to change between a window and fullscreen after
`Init()`, and `Screen` could not simply shut the window down and make
another without rebuilding everything that holds the picture. So a
change to that one setting waits for a relaunch, and the game says so.
For saying so, Travis asked for the system's message box. Whitetail
shows one only as part of `ErrorHandler`'s fatal path, so the game
calls `JOptionPane` directly, which is the first Swing import in the
game. An `ErrorHandler`-style `Notify(message)` would keep that in one
place, and matters for Windows 98, where whether Swing dialogs behave
over an LWJGL window is one more thing to find out.

**C45. A command takes a fixed number of arguments.** *(Hit)*  
Status: open. Minor.
Offering the player two, three, or four things to say is one idea and
three commands, `choice2`, `choice3`, `choice4`, because a signature is
a fixed list of types and names cannot be overloaded. (The tutorial's
answer is one string split on `|`, which loses the compiler's check that
each option is text and makes a `|` in an option a bug.) `join` has the
same limit (C29). Allowing a command to be registered with a last
parameter that repeats would cover both.

**C46. A long piece of text has to be one long line.** *(Hit)*  
Status: open.
String literals are single-line and there is nothing to join two of
them at compile time, so every speech in a dialogue script is one line
of the file, commonly 150 characters. Dialogue is where most of a
game's text will live, and those files will not fit in any editor
window or diff. Letting adjacent literals run together, as C does
(`"first half " "second half"`), would fix it with no run-time cost.

Related, and more the game's problem than Whitetail's: the game cannot
see a script's text until the line runs, so it cannot check at startup
that every speech fits the talk box. A way to walk the string constants
of a compiled script, with the command each is an argument of, would let
a host check its content the way `mvn verify` checks the data files.

**C47. `ImageBuilder.FromPNG()` does not read indexed-color PNGs.**
*(Hit)*  
Status: open.
It reads color types 0, 2, and 6 (grey, RGB, RGBA) and reports type 3 as
unsupported. Type 3, a palette in the file and one index per pixel, is
what Aseprite and every other pixel-art program writes for a picture in
indexed mode, which is the natural mode for art made in a fixed palette.
The first picture Travis drew for this game was one, and the game
stopped at startup. The purchased sheets had all happened to be RGBA.

It is also the one PNG kind that suits this renderer exactly: the game
wants indices, the file holds indices, and going through RGBA and
matching each pixel back to a palette color is a round trip. The game
now reads type 3 itself (`IndexedPng.java`, about 150 lines: chunks,
`java.util.zip.Inflater`, the five row filters, 1 to 8 bits) and leaves
the other types to Whitetail. Supporting type 3 in `comp-image`, with
the file's palette and transparency available as well as the expanded
pixels, would let every palette-art game drop that.

The atlas tool had the same trouble a day later, with the first sprite
sheets Travis drew: it read sheets with `FromPNG()` and would have
refused them. It now decodes through the game's `Bitmap.Decode()`.

Related: `FromPNG()` takes a stream and decides the kind inside, so a
caller who needs to know the kind first must read the whole stream into
memory and look at the header byte itself, as `Bitmap.Load()` does.

**C48. The software framebuffer is one picture in one place.** *(Hit)*
Status: open.
To move the view of the world by less than a game pixel, the game now
keeps two layers, the world and the interface over it, and puts them
together on the screen, where the world can be slid by whole screen
pixels. `GL12SoftwareFramebuffer` is a static singleton with one buffer,
one texture, and one rectangle fixed at `Init()`, so it can show only
one of them. The interface goes through it; the world layer is a second
texture that `Screen.java` creates, uploads, and draws with `GL11`
calls of its own, about a hundred lines that mostly repeat the
component's `Present()`.

What would have let the component do both: instances, or a `Create()`
returning a handle, in place of static state; a rectangle that can be
given per `Present()` rather than once; and a scissor or clip rectangle.
`Present()` leaving the alpha test alone, and saying so in its state
contract, is what made the upper layer possible at all: the game turns
the test on, gives its background pixels no alpha, and the component
draws a picture with a hole in it without knowing.

Two things for the Windows 98 target. The game's own texture uses
`GL_CLAMP`, so its half of this is OpenGL 1.1 (A5 is the same point
about the component). And the CRT filter Travis plans will want these
same two layers as shader inputs, so a framebuffer component that can
hold several pictures and hand their textures to a later stage would
serve both the old path and the new one.

The CRT filter came next and could use none of the component. A shader
needs the picture as a texture it can sample, and the component's
texture is private, so with the filter on the game uploads the picture
to a texture of its own as well (`Crt.java`) and
`GL12SoftwareFramebuffer` only lends its `int[]`. A `GetTexture()`, or
a `Upload()` separate from the drawing in `Present()`, would have been
enough.

The video signal (`Signal.java`) made a third texture in `Screen.java`
for the same reason from the other side: what comes off the cable is
wider than the picture, one pixel to every screen pixel across, and the
component's buffer is one size fixed at `Init()`. So there are now
three ways the picture reaches the screen and the component serves
one of them.

A small one met on the way: the component's fatal checks and GL error
strings (`CheckGlErrorInit`, `GlErrorString`) are private, so the game's
second texture reports a bare error number.

**C49. The shader support is for an OpenGL 3.3 core context only.**
*(Read)*
Status: open.
`comp-renderer-gl33` has a `ShaderProgram`, but the component is written
for a 3.3 core context, and this game cannot ask for one: it asks for
1.0, the only request old drivers answer (A4), and takes whatever
compatibility context comes back. The CRT filter is one fragment shader
in GLSL 1.20 over fixed-function vertices, which any OpenGL 2.0 driver
runs, so the game compiles, links, and sets uniforms with `GL20` calls
of its own (`Crt.java`), a hundred lines every game with a
post-processing effect on the software framebuffer will repeat. A small
shader class with no assumptions about profile, or a framebuffer
component that takes an optional fragment shader for its `Present()`,
would cover it. Whether `ShaderProgram` would in fact work in a
compatibility context was not tried.

Related: `Window` reports nothing about what the driver can do. The
game asks LWJGL directly (`GLContext.getCapabilities().OpenGL20`) to
decide whether the filter can be offered.

### D. Things that went well

Worth recording, so they are not "fixed" by accident.

- The readmes are precise enough that I wrote most of the game from them
  and only opened source to confirm signatures.
- `comp-display-layout` and `comp-software-framebuffer` fit together
  exactly as described; the picture was right, including colors, on the
  first run.
- The session log on its own told me what every run had done.
- The art uses only 17 colors, so `comp-sprite-sys`'s indexed design suits
  it with no quantization.
- The sprite renderer, atlas format, and animation classes all worked
  first time from the readme alone, and a session of walking around the
  map logged no garbage collections at all.
- Text entry through `InputSys.GetEvent()` worked first time, including
  held-backspace repeat, exactly as the readme describes.
- `SpriteRenderer` runs happily with no window, drawing into a plain
  array, which made it possible to test the game's rules tick by tick.
  Whitetail's framework-free Surefire test style copied over in minutes.
- Color maps did exactly what they promise: iron rock is the copper rock
  sprite with three palette indices swapped, set per sprite, at no cost.
- `comp-save-sys` worked first time from its readme, including through
  the writer thread. Quitting and relaunching resumed correctly. Killing
  the game mid-write, the case its two generations exist for, has not
  been tried yet.
- `comp-script-sys` worked first time from its tutorial: two commands,
  two trigger blocks, and the ladder was scripted. The delay counts came
  out exactly as documented, which the tests pin down tick by tick, and
  `Shutdown()` followed by `Init()` comes back clean, so each test gets a
  fresh script manager.
- On the script system shipping no commands: from the using side this was
  no obstacle and reads as the right boundary. Everything the game has
  needed so far (`mes`, `travel`) is about this game's chat box and this
  game's maps. The tutorial's `mes` example was enough to see the
  pattern. If people are surprised, the cheapest fix is probably one
  sentence at the top of the readme saying so outright, since at present
  it is implied by "the host registers the commands" rather than stated.
- Moving mining from Java to a script went smoothly: twenty commands
  registered, a forty-line script, and it compiled and passed the
  existing mining tests unchanged on the first run. Statements may span
  lines freely, which the long `mes(join(...))` calls needed.
- A second kind of trigger, `oplocu`, cost nothing on Whitetail's side:
  trigger types are just names, `HasTrigger()` answered whether a loc
  takes items, and the smelting script compiled and ran first time.
  String locals and string comparison did what the readme says.
- `SpriteRenderer` with a one-tile dummy buffer was enough to load the
  whole game's data outside the game, and the game's own drawing then
  rendered full maps into a plain array for the map tool to save as
  pictures. Software drawing into memory you own makes tools like this
  nearly free.
  The map editor is the same trick kept running: it redraws the whole
  map through the game's code after every stroke, and on these maps
  that is instant. The one awkwardness is that everything is a static
  singleton. The game's drawing writes to one buffer set by an `Init()`,
  so a tool must point it at each picture in turn and could never draw
  two at once; that is the game's `Gfx` here, but `SpriteRenderer` is
  built the same way and a tool that used it for drawing would meet the
  same wall (C22 is the testing side of this).
- Shrinking the picture to 384 by 216 and moving the view to a hole
  inside it was one `FramebufferPreset` with different numbers.
  `comp-display-layout` chose scale 4 on a 1080p desktop, mouse
  positions kept converting correctly, and the sprite renderer clipped
  the world to the new viewport without a change. Drawing the frame
  over the world needed nothing from Whitetail, since the game owns the
  index buffer and the order it writes to it.
- Dialogue needed nothing new from the script system. `suspend()` and
  `Resume()`, already used for the make menu, carried a whole
  conversation: nine commands' worth of talk, a choice, and a quest, with
  the script reading top to bottom like the conversation it is. Player
  variables declared in the script were saved and restored by code
  written months before quests existed. The quest's eleven tests passed
  the first time they ran.
- Three scenes and the transitions between them worked first time:
  `TransitionTo()` from inside `onInput()`, `onEnter()` for resetting a
  scene's widgets. `Window.SetVSyncEnabled()` took effect at once from
  the options screen. `InputSys.IsDown()`, `WasPressed()`, and
  `WasReleased()` were all a slider and a push button needed.
- `suspend()` and `Resume()` worked first time and exactly as the
  tutorial describes. A script that stops at a menu for as long as the
  player likes needed no change to how the game ticks scripts, because
  a suspended state is simply left alone. Cancelling one is just
  dropping the state.
- `comp-json` parsed every data file first time, and its parse errors,
  with line, column, and a caret under the spot, are passed straight
  through to whoever edited the file. They need no rewording.
- `ImageBuilder.FromPNG()` decoded all 36 purchased sheets, from five
  different packs, without a single rejection, and two 4 MB atlases load
  and install with no visible delay inside a 64 MB heap.
