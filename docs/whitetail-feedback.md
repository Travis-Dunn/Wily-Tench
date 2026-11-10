# Whitetail feedback

Notes gathered while building Wily Tench on Whitetail 0.1.12. Newest
session first. Each item says how it was found, because some are things I
ran into and some are predictions I could not test.

- **Hit** - it happened to me while building or running the game.
- **Read** - found by reading Whitetail's source or docs.
- **Predicted** - inferred from evidence, but needs the real machine to
  confirm.

---

## Session 1 - reading everything, opening a window

Tested on Linux only (Java 8 and Java 21, Mesa software OpenGL, virtual
display). Nothing here has been run on Windows of any age.

### A. Things that threaten the Windows 98 target

These are the ones to check first when the ThinkPad arrives. Items 1, 4,
and 5 are Whitetail's own; 2 and 3 are in LWJGL 2.9.3's native libraries.

**A1. `UserDir.Resolve()` cannot succeed on Windows 98.** *(Read;
Predicted)*
On Windows it takes its base from the `APPDATA` environment variable and
fails when that is unset. Windows 95, 98, and ME never set it; it arrived
with Windows 2000. So on the target machine the very first call of a
session fails, there is no log directory, and nothing else can start.
The game works around it in `DataDir.java` by using
`<user.home>\Application Data` when `APPDATA` is missing. Suggestion: give
`UserDir` that same fallback, since it is the component's whole job.

**A2. LWJGL 2.9.3's `lwjgl.dll` probably cannot open a window on
Windows 98.** *(Read; Predicted)*
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
It imports `GetModuleHandleExW`, which first appeared in Windows XP, and
`SwitchToThread`, which Windows 9x lacks. `comp-audio-openal` is therefore
XP-and-later as shipped. Not a problem yet, since the game has no sound.

**A4. `Window.Init()` only works on old drivers when asked for OpenGL
1.0, and the tutorial's fallback chain stops at 1.2.** *(Read)*
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
The method is `Input()`. `comp-scene-sys/readme.md` calls it `OnInput()`
twice, and the tutorials for `comp-input-sys`, `comp-input-lwjgl`, and
`comp-input-mapper` all show `SceneManager.OnInput()`. Only the scene
tutorial has the real name. Copying from the input tutorials does not
compile.

**B2. The text-entry example in `comp-input-sys/tutorial.md` does not
compile.** *(Read)*
It reads `e.down` and `e.ch`. Those fields are private; the accessors are
`isDown()` and `getChar()`, as the readme says.

**B3. `comp-display-layout/readme.md` refers to `comp-platform`.** *(Read)*
Several times, under "Ownership boundaries". There is no such component;
it means `comp-window`.

**B4. Every log entry is followed by a blank line.** *(Hit)*
`Logger` ends each entry with its own line break, and the style guide has
every message end in `"\n"`, so the files are double spaced. One of the two
should go.

**B5. `ConfigFileParser.readExistingFile()`** *(Read)*
A public static method in camelCase, against rule 3 of the constitution,
and not mentioned in the readme. After reporting "not initialized" it
carries on and returns the flag anyway.

**B6. The parent POM manages only some of the components.** *(Hit)*
`dependencyManagement` leaves out `comp-scene-sys`, `comp-config-sys`,
`comp-json`, `comp-timer`, `comp-gc-priest`, `comp-software-framebuffer`,
`comp-camera`, `comp-renderer-gl33`, and `comp-audio-openal`. A game cannot
lean on the parent for versions, so Wily Tench names the version on every
dependency through one property.

**B7. Small ones.** *(Read)*
`Timer`'s assertion messages do not end in `"\n"` and do not use
`ErrorStrings`, unlike its neighbors. `Timer.Init()` returns a boolean that
can only be true.

### C. Annoyances and gaps

**C1. Nothing says how LWJGL's native libraries get loaded.** *(Read)*
LWJGL 2 finds its natives through `java.library.path` or
`org.lwjgl.librarypath`, and a game that only follows the readmes sets
neither, so its first `Window` call cannot load them. Every game
needs an answer to this, and no component or document offers one. Wily
Tench unpacks the libraries from the classpath at startup (`Natives.java`).
That is about a hundred lines that would sit well in Whitetail, or at
least a paragraph in `comp-window/tutorial.md`.

**C2. On Linux, LWJGL 2 needs the `xrandr` program installed.** *(Hit)*
Without it `Window.GetDesktopWidth()` fails with "Window failed to query the
desktop display mode because an exception was encountered" and an `ArrayIndexOutOfBoundsException` from inside LWJGL in
`fatal.log`. Linux Mint has `xrandr` out of the box, so this should not
bite there, but it is worth a line in the readme since the message gives no
hint.

**C3. One problem, two dialogs.** *(Hit)*
`GetDesktopWidth()` and `GetDesktopHeight()` each raise their own fatal
dialog. Calling both and then checking, which is the natural way to write
it, shows the player two modal dialogs for one failure. A single query
returning both numbers would avoid it.

**C4. A fatal error during startup freezes a headless or scripted run.**
*(Hit)*
The modal dialog waits for a click forever. That is right for a player. It
made my automated launches hang until I took a thread dump to see why. A
system property to suppress the dialog would help testing and CI.

**C5. `UserDir.Resolve()` fails silently for a player.** *(Read)*
It reports to standard error, which a double-clicked game does not have.
The game has to put up its own dialog, and can only say "look at the
console". Returning the reason, or making it retrievable, would let the
game show it.

**C6. `Window.Init()` returning false is silent too.** *(Read)*
That is deliberate, for fallback chains, and the tutorial says to stop. It
does not say that nothing has been shown to the player at that point, so
the game must raise the fatal error itself.

**C7. A software-rendered game still has to call LWJGL directly.** *(Hit)*
Two things forced `import org.lwjgl.opengl.GL11` into the game:
clearing the window, so the bars beside a letterboxed picture are black
rather than garbage, and checking `glGetError()`, since `Present()`
deliberately does not. A `Clear` and an error check on the framebuffer
component, or on `Window`, would let such a game depend on Whitetail alone.

**C8. No frame limiter.** *(Hit)*
`Window` exposes vSync but nothing like LWJGL's `Display.sync()`. Where
vSync is off or the driver ignores it, the loop spins a full processor. The game has its own sleep-based cap.

**C9. Scenes have no fixed-tick callback.** *(Hit)*
`Timer` provides fixed ticks and `Scene` provides `onUpdate(delta)`, and
nothing joins them. The game drains `Timer.Tick()` inside `onUpdate()`,
which works; a sentence in the scene tutorial would have saved me checking
whether I had missed something.

**C10. `comp-scene-sys` needs `comp-event-sys` for one type.** *(Hit)*
`Scene.handleEvent(Event)` is the only link. A game that never fires an
event still carries the dependency and implements the method.

**C11. `ConfigEntry` is unlike its siblings.** *(Hit)*
A public constructor where everything else uses a static `Create`, bounds
passed as `Object`, and a raw `ArrayList` parameter on
`ConfigFileParser.Init()`.

**C12. The build's JDK requirements are not written down.** *(Read)*
`docs/readme.md` says Whitetail compiles with Temurin 8, and `.sdkmanrc`
says Java 17. Both are true: Maven runs on 17 and compiles through a JDK 8
toolchain. But nothing says a `~/.m2/toolchains.xml` with a 1.8 entry is
required. I wrote one before my first build because the POM asks for
the toolchain; someone who had not read the POM would meet a "no toolchain
found" failure instead.

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
