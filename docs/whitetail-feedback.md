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
| C15 | `AtlasTool.java` converts and pads the art |
| C16 | `WorldScene.java` carries fractional milliseconds between frames |
| C18 | `Font.java`, `Text.java`, `Gfx.java`, and `FontTool.java` are the game's own text and drawing code |
| C19 | `ChatBox.java` uses only the direction of the mouse wheel |
| C22 | `Fixture.java` (tests) guards one-time initialization with a static flag |
| C23 | `SaveGame.java` does its writing on a thread of its own |
| C24 | `SaveGame.Load()` calls `Describe()` and then `Read()` |

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
  the writer thread. Killing the game mid-session and restarting has so
  far always come back to the last tick, which is the property the
  every-tick saving depends on.
