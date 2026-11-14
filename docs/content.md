# Adding and changing content

Everything the game *contains*, as opposed to how it works, lives in text
files under `src/main/resources`. None of it needs Java. This page is the
guide to those files, for a person or an AI.

| To change | Edit |
| --------- | ---- |
| What can be carried | `data/items.json` |
| What skills there are | `data/skills.json` |
| The pictures on the side panel's tabs, and the menu widgets | `data/interface.json` |
| What can be made from what | `data/recipes.json` |
| What can be used on a map: rocks, ladders | `data/locs.json` |
| Who can be talked to | `data/npcs.json` |
| What a map-file character means and looks like | `data/tiles.json` |
| Which sprite sheets and recolorings exist | `data/sprites.json` |
| The colors | `data/palette.json` |
| Which maps and scripts the game has; what a new player starts with | `data/game.json` |
| The layout of a place | `maps/*.txt` |
| What happens when something is used | `scripts/*.whtl` |

## Checking your work

**Run the game, or run `mvn verify`.** Both read every file and stop at the
first mistake with a message that names the file, the entry, and the
problem, for example:

> locs.json, loc [silver_rocks], [params]: [ore] names the item [silver_ore],
> and there is no such item in items.json.

`mvn verify` also loads every map and script and checks that everything
usable on a map has a script, so it catches mistakes without having to walk
to them. Nothing is ever silently ignored: a misspelled value name is an
error, not a no-op.

## Rules of the JSON files

The `.json` files are strict JSON, which is unforgiving in a few ways worth
knowing before the error message teaches them:

- Text goes in double quotes. Numbers, `true`, and `false` do not.
- Items in a list or group are separated by commas, with **no comma after
  the last one**.
- There are **no comments**. Instead, any `{ ... }` group may carry a
  `"note": "..."`, which the game ignores. Use it freely.
- Every id (`"id": "copper_ore"`) is lower-case letters, digits, and
  underscores. Other files and scripts refer to things by id, so an id
  should not be changed once it is in use; item ids are also stored in
  players' saved games.

## Pointing at a picture

A picture is named by its **sheet** and its **cell**: column, then row,
both counted from zero.

    "sheet": "items", "cell": [3, 1]

To find a cell, open the catalog image for the sheet in `docs/sprites/`,
such as `docs/sprites/items.png`. Every cell is labeled with its column and
row. There is one catalog for every sheet in `sprites.json`, and the file
is named after the sheet's id. All of the purchased art is there, most of
it not yet used by anything. Where a list of cells is asked for (`"cells": [[5, 1], [6, 1]]`), each
map tile picks one, so that a field of rocks is not all the same rock.

## items.json

    {
      "id": "copper_ore",
      "name": "Copper ore",
      "sheet": "cave",
      "cell": [5, 1]
    }

| Value | Meaning |
| ----- | ------- |
| `id` | How everything else refers to the item |
| `name` | What the player sees, written as it would head a label. Messages use it in lower case |
| `examine` | Optional. What the Examine option says about it. "Nothing remarkable." if left out |
| `sheet`, `cell` | Its icon |
| `color_map` | Optional. A recoloring from `sprites.json` to draw the icon through |
| `was` | Optional. A list of ids the item used to have |

An item's id is written into players' saved games, so it cannot simply be
changed. To rename an item, give it the new id and put the old one under
`was`:

    "id": "bronze_pickaxe", "was": ["bronze_hammer"]

Saved games, scripts, and data that still say `bronze_hammer` then find
the pickaxe.

## skills.json

    {
      "id": "mining",
      "name": "Mining",
      "sheet": "magma_items",
      "cell": [4, 1]
    }

| Value | Meaning |
| ----- | ------- |
| `id` | How scripts refer to the skill, as in `stat_xp("mining", 175)`. Saved games store experience under it, so it must never change |
| `name` | What the player sees |
| `sheet`, `cell` | Optional. Its icon. A skill without one is shown as the first letter of its name |

Skills appear on the skills tab in the order they are listed, two to a
row. Adding one here is all it takes to list it; a script must award
experience in it before it does anything.

The icons in use now are stand-ins borrowed from the item art. Real skill
icons are not in the purchased art and have yet to be drawn; when they
are, add their sheet to `sprites.json` and point each skill at its cell.

## interface.json

    { "id": "skills", "name": "Skills", "sheet": "ui_icons", "cell": [0, 12] }

The tabs above the side panel's box. The game has exactly two,
`inventory` and `skills`, and both must be listed; this file chooses only
each one's name and picture. These pictures are stand-ins too.

### Widgets

The `"widgets"` group of the same file names the cells of the purchased
interface art that the menus' buttons, checkboxes, slider, and frame are
put together from. To reskin the menus, point these at other cells; the
art has grey and red versions of most pieces beside the orange ones.

    "button": {
      "sheet": "ui_inputs",
      "normal": [19, 0], "hover": [19, 4], "pressed": [19, 6],
      "pressed_drop": 3
    }

| Part | What its cells must hold |
| ---- | ------------------------ |
| `button` | For each of `normal`, `hover`, `pressed`: the top left cell of a button drawn three cells wide and two tall. The middle column is repeated to make wider buttons. `pressed_drop` is how many pixels shorter the pressed picture is, so that it is drawn that much lower and sinks rather than shrinks |
| `checkbox`, `radio` | `off` and `on`: one cell each |
| `slider` | `track`: the left cell of a track three cells wide with an arrow at each end. The middle cell is repeated. The arrows must fit in the outer 8 pixels |
| `slider_knob` | `cell`: one cell, drawn over the track |
| `frame` | `block`: the top left cell of a frame four cells square with a hollow middle. Its edge cells are repeated. `margin` is how many pixels of empty space the art leaves outside the frame's border |

## recipes.json

A *list* is what one material can be made into at one kind of place. A
script shows it as a menu and acts on the player's pick.

    {
      "id": "anvil_bronze_bar",
      "title": "What would you like to make?",
      "skill": "smithing",
      "material": "bronze_bar",
      "recipes": [
        { "makes": "bronze_axe", "uses": 1, "level": 1, "xp": 12.5 },
        { "makes": "bronze_warhammer", "uses": 3, "level": 9, "xp": 37.5 }
      ]
    }

| Value | Meaning |
| ----- | ------- |
| `id` | How a script asks for the list. The anvil looks for `anvil_` followed by the id of the item used on it |
| `title` | The words at the top of the menu |
| `skill` | The skill whose level the recipes need |
| `material` | The item every recipe in the list uses up. Its icon is drawn beside each recipe's cost |
| `makes` | The item a recipe makes |
| `uses` | How many of the material it uses up |
| `level` | The level needed, 1 to 99 |
| `xp` | The experience earned, with at most one decimal place |

Recipes are shown in the order written, ten to a column. The menu takes
every name and picture from `items.json`, so renaming an item or giving
it a new picture there changes the menu too.

### Adding something to make from bronze

1. Add the item to `items.json`.
2. Add a recipe for it to the `anvil_bronze_bar` list.

### Adding another metal to the anvil

1. Add the bar and the things made from it to `items.json`.
2. Add a list with the id `anvil_` and the bar's id, such as
   `anvil_iron_bar`, with `"material"` set to the bar.

No script changes: the anvil's block in `smithing.whtl` serves every
list. Getting the bar in the first place, by smelting, does need a
script change for now; the furnace only knows bronze.

## locs.json

A *loc* is a kind of thing on a map that the player can use. What using it
does is a script; see "Scripts" in the readme.

    { "id": "ladder", "name": "Ladder", "verb": "Climb down" }

| Value | Meaning |
| ----- | ------- |
| `id` | How tiles and scripts refer to it. `[oploc,ladder]` is its script |
| `name` | What the player sees |
| `verb` | The word for using it: "Climb down Ladder" is then its first option, the one a left click takes. May be left out if the category supplies one |
| `examine` | Optional. What the Examine option says about it |
| `category` | Optional. A category from the top of the file |
| `params` | The values its category requires. Only for locs with a category |

A **category** is a family of locs that share one script and differ only in
their values. `ore_rock` is the one so far:

    {
      "id": "ore_rock",
      "verb": "Mine",
      "empty_name": "Rocks",
      "params": {
        "ore": "item",
        "level": "whole",
        "xp": "tenths"
      }
    }

`params` lists the values every loc of the category must supply, and what
kind each is. The game checks each loc against it.

| Kind | Accepts |
| ---- | ------- |
| `whole` | A whole number |
| `tenths` | A number with at most one decimal place, such as `17.5`. Scripts receive it multiplied by ten, as 175 |
| `text` | Any text |
| `item` | The id of an item in `items.json` |

`empty_name` makes the category's locs something that can be emptied for a
while, and is what they are called meanwhile. `empty_examine` is what
examining one says while it is empty.

### Adding another ordinary ore rock

1. Add its ore to `items.json`.
2. Add the rock to `locs.json` with `"category": "ore_rock"` and all six
   `params`: `ore`, `level` (Mining level needed), `xp`, `chance_at_1` and
   `chance_at_99` (the chance of a swing succeeding at those levels, each
   out of 256), and `respawn` (game ticks it stays empty; a tick is 0.6
   seconds).
3. Give it a character in `tiles.json`, as a thing with `"loc"` set to its
   id, `cells`, and `empty_cells`.
4. Put that character on a map.

No script changes: `mining.whtl` serves the whole category. Tin was
added exactly this way.

### Making a loc take items

Give it an `[oplocu,its_id]` block in a script file, beside or instead of
its `[oploc,its_id]`. `used_item()` gives the id of the item used, so one
block handles every item the loc cares about and answers "Nothing
interesting happens." for the rest. `inv_del` takes an item away and
`inv_add` gives one. `smithing.whtl`, the furnace's script, is the model.
Every loc still needs a `verb` and an `[oploc,...]` script for a plain
click, even if all that does is explain.

## npcs.json

A person. For now people stand still on one tile and can only be talked
to; what they say is the script `[opnpc,id]`. See "Talk" in the readme.

    {
      "id": "harl",
      "name": "Harl",
      "examine": "An old smith. His hands have seen a lot of work."
    }

| Value | Meaning |
| ----- | ------- |
| `id` | How tiles and scripts refer to them. `[opnpc,harl]` is his script |
| `name` | What the player sees, and what heads their side of a talk |
| `examine` | Optional. What the Examine option says |
| `sheet`, `cell` | What they look like |

### Adding someone to talk to

1. Add them to `npcs.json`.
2. In the map file, define a symbol for them, `!define H npc harl`, and
   put it on the grid. See "Maps".
3. Run the map tool and look at the picture.
4. Write their `[opnpc,id]` block in a script file, and list the file in
   `game.json` if it is new.

Keep each `chatnpc` and `chatplayer` to what fits the talk box: four
lines, about fifty words. Anything longer is cut off, and nothing checks
this for you. Split a long speech into two.

## tiles.json

This file gives meaning to the characters in map files. A **tileset** is
the look of a kind of place and defines its walls and floors; a map names
the tileset it uses. **Things** stand on the floor. Things listed inside a
tileset exist only there; things in the list at the bottom of the file can
be put on any map.

Two characters are built in and cannot be redefined: a space is nothing at
all, and `@` is where the player arrives. `!` cannot be used either. A
map file may define further characters for itself; see "Maps".

A wall:

    { "symbol": "#", "name": "Cave wall", "sheet": "cave", "block": [0, 0] }

`block` is the top left cell of the wall's block of pieces on its sheet. A
wall block is six cells wide and three tall, and the game picks the piece
that joins up with neighboring walls of the same character. In the
catalogs the blocks are easy to spot: they look like a small maze.

A floor:

    {
      "symbol": ".",
      "sheet": "cave",
      "cells": [[8, 0], [9, 0], [8, 1], [9, 1]],
      "bare_percent": 50
    }

`bare_percent` is optional: out of a hundred tiles, how many show no
picture at all. The first floor listed is the one that things, and the
player's starting tile, stand on.

A thing:

    {
      "symbol": "r",
      "loc": "copper_rocks",
      "sheet": "cave",
      "cells": [[5, 1], [6, 1]],
      "empty_cells": [[5, 4], [6, 4]]
    }

| Value | Meaning |
| ----- | ------- |
| `symbol` | The single character that puts it on a map |
| `name` | What the player sees it called. Needed unless it has a `loc`, which has a name of its own |
| `examine` | Optional. What the Examine option says about it. Not allowed with a `loc`, which has its own. Walls may have one too |
| `loc` | Optional. Makes it usable, as that loc |
| `sheet`, `cells` | What it looks like |
| `empty_cells` | What it looks like when emptied: one cell for each of `cells`. Required if its loc can be emptied |
| `color_map` | Optional. A recoloring from `sprites.json` |
| `animated` | Optional, `true` or `false`. If true, `cells` holds one cell, and the animation is that cell and the three to its right |
| `walkable` | Optional. If true the player can walk through it. Things are solid otherwise |

## What the player can do to things

Pointing at something gives a list of options. A left click takes the
first; a right click opens the whole list as a menu. For now the lists
are fixed by kind:

- A tile of the map: the loc's `verb`, if a loc with a script stands
  there, or Talk-to, if a person with a script does; then Walk here; then Examine, if anything stands there; then
  Cancel.
- An item in the inventory: Use, Drop, Examine, Cancel.

Use selects the item. While one is selected, every loc and every other
item offers "Use *item* -> *thing*" ahead of its usual list. On a loc
that runs the loc's `[oplocu,...]` script, if it has one; on another
item it does nothing yet.

So the only parts of this that data controls yet are a loc's `verb` and
the `examine` lines. `Actions.java` builds the lists.

## sprites.json

A sheet is one of the purchased images, cut into 16 pixel cells:

    {
      "id": "cave",
      "source": "DD-core/Demonic-Dungeon-16x16/No-Background/Cave.png"
    }

| Value | Meaning |
| ----- | ------- |
| `id` | What `"sheet"` values elsewhere refer to, and the name of its catalog image |
| `source` | The original PNG, from the repository's root |
| `region` | Optional. The part of the PNG to take: left, top, width, height, in pixels. Without it, everything from the top left corner as far as anything is drawn, so a sheet drawn in the corner of a larger canvas needs no region |

A sheet's width and height must be multiples of 16, and its pixels must
each be fully transparent or an opaque palette color.

**After changing this file, `palette.json`, or a source image, rebuild the
atlases:** run the **Build Atlases** run configuration, or

    mvn test-compile exec:java@atlases

The game cannot read PNGs. The tool converts every sheet, packs them
together into a few large *atlas* files that the game can load quickly,
and redraws the catalogs. If you forget, the game notices that its
atlases are out of date and says so when it starts. The tool's own
complaints are specific: an unknown color, for instance, is reported with
its value and how many pixels use it.

You never need to know which atlas a sheet ended up in. Everything refers
to a picture by its own sheet and cell, and that does not change when the
packing does.

All the purchased art is listed except a few images the game cannot use
as they stand: the mini-map and particle images, which are drawn on an 8
pixel grid rather than 16, and the interface pack's `Other.png`, which
has partly transparent pixels.

**Room.** The tool fills atlases of 2048 by 2048 pixels and the game can
hold eight of them. All the art so far takes two. If that ever runs out,
give large sheets a `region` covering only what is used.

A color map redraws a picture with some palette colors swapped:

    { "id": "iron", "swaps": [["tan", "rust"], ["rust", "maroon"]] }

Each pair is "this color becomes that one", using the names in
`palette.json`. There is room for seven color maps.

## The game frame: images and layout.json

The interface is one picture, `images/frame.png`, the size of the whole
screen, drawn over the view of the world. To change it, save a new PNG
over it and run the game; there is no tool to run. A picture in `images/`
must obey two rules, and the game stops with a message naming the pixel
if it does not:

- Every pixel is exactly a color of `palette.json`, or wholly
  see-through. No partly see-through pixels.
- It may be saved as indexed color or as full color (RGB or RGBA), not
  interlaced.

`layout.json` says where things are on the frame, in game pixels, so that
the game lines up with the drawing:

| Value | Meaning |
| ----- | ------- |
| `picture` | The size of the whole picture: width, height |
| `frame` | The frame's file in `images/` |
| `view` | The hole the world shows through: left, top, width, height. The frame is drawn over the world, so its rounded corners cover the corners of this rectangle |
| `chat` | The parchment that the chat box and talk are written on |
| `widgets` | The open space above the tabs |
| `slots` | The top left of the first inventory slot |
| `top_tabs`, `bottom_tabs` | The top left of the first tab in each row |
| `pitch` | From one slot's or tab's left edge to the next's. They are 16 pixels square, four to a row |

**If the frame is redrawn with anything moved, change these numbers to
match.** `mvn verify` checks the file against the frame: that the view is
a hole with frame on every side, that the parchment fills its rectangle,
and that every slot and tab is a square of one color with a gap around
it.

### Drawing sprites: what a sheet must obey

A sheet may be one of the purchased images or one of Travis's own in
`art/`, saved from Aseprite as it is, indexed color or full color.

- Cells are 16 pixels square, counted from the sheet's top left corner.
- Every pixel is exactly a palette color, or wholly see-through.
- **See-through means see-through, and black means black.** The game
  always draws the floor first and then whatever stands on it. So
  anything that stands on the floor (a mushroom, a rock, a torch, a
  ladder, a person) must have a see-through background, or it will paint
  a black square over the floor. A black outline around such a thing is
  a good idea: it keeps it readable on any floor.
- Floors and walls fill their whole tile, and their background may be
  solid.

### Characters

A character is six cells in a row: two of it walking sideways **facing
left**, two walking away from the player, two walking toward the player. The game turns
the sideways pair over for walking right, and stands the character in
the first picture of each pair. `"player"` in `game.json` says where the
player's six begin:

    "player": { "sheet": "wizard", "cell": [0, 0] }

## palette.json

The game shows a fixed set of colors, and every pixel of every sprite is
one of them.

    { "name": "tan", "rgb": "D1935F" }

`colors` lists them. **The order matters**: converted art stores each
pixel as a position in this list, so add new colors at the end, and
rebuild the atlases after any change. Some of the names are used by the
game's own interface and must stay; the game says which if one goes.

The list is two palettes end to end. First the colors of the purchased
art. Then the 32 colors of carp32, Travis's own palette, named
`carp_black`, `carp_parchment`, and so on, in the order of
`art/carp32.png`. The game frame is drawn in carp32, and in time every
sprite will be.

When new art brings a new color, the atlas tool refuses it and reports
the color and how many pixels use it. If it is a real color of the art,
add it to `colors`. If it is a handful of stray pixels a shade away from
a real color, add it to `corrections` instead, which reads it as the color
it was meant to be without touching the artist's file:

    { "from": "17111A", "to": "ink" }

`background` is the color of empty screen.

## game.json

    {
      "maps": ["cave.txt", "dungeon.txt"],
      "scripts": ["travel.whtl", "mining.whtl", "smithing.whtl", "harl.whtl"],
      "starting_items": ["bronze_pickaxe"],
      "player": { "sheet": "wizard", "cell": [0, 0] }
    }

Every map and script file must be listed here to be part of the game. A
new game begins at the `@` of the first map.

## Maps

A map is a text file in `maps/`, and that file is the only form the map
ever has. People edit it as text or in the map editor, AIs write it as
text, and the editor reads and writes this same file.

    !name Copper Cave
    !tileset cave

    !define 1 point from_dungeon
    !define H npc harl

    !map
       ################
       #.L1...........#
       #..c........m..#
       #......@....H..#
       ################

The lines beginning with `!` are settings. After `!map` comes the grid,
one character per tile, to the end of the file. Rows may be different
lengths. A map is kept with no empty space around it: the first row and
the first column each have something in them.

**Everything that is anywhere is in the grid.** Nothing is placed by
coordinates, so a map can be checked by looking at it: if a person is in
the wrong room, the grid shows them there.

| Setting | Meaning |
| ------- | ------- |
| `!name` | What the place is called |
| `!tileset` | Which tileset of `tiles.json` gives its characters their meaning |
| `!define` | Gives a character a meaning for this map only. See below |
| `!map` | On a line by itself. Everything after it is the grid |

A character in the grid is, in this order: a space (nothing at all, and
solid); `@` (where a new game begins, and where `travel` arrives; every
map needs exactly one); a symbol this map defines; or a wall, floor, or
thing of the tileset.

Walls are one tile thick and join up with their neighbors, so draw rooms
as outlines.

### The map editor

A desktop program for painting maps. Start it with the **Map Editor**
run configuration, or

    mvn test-compile exec:java@editor

from the project's root folder. It opens the first map; File > Open
lists the rest. It shows the map as the game will draw it, because it
draws with the game's own code from the same data files.

- **Painting.** Pick something from the lists on the left and paint with
  the left mouse button. The Pencil paints tile by tile as you drag. The
  Rectangle fills the area you drag over. The right mouse button picks
  up whatever is under it.
- **What can be painted.** "Ground" is nothing, the tileset's floors,
  and its walls. "Things" is everything else `tiles.json` offers this
  tileset. "This map" is the player's start and the symbols this map
  defines; the buttons under it add a person from `npcs.json`, a named
  point, or a thing on a chosen floor, and remove one.
- **The start and points move.** Painting `@` or a point somewhere new
  takes it from where it was.
- **Moving the view.** Drag with the middle mouse button, or use the
  scroll bars. The mouse wheel zooms in and out.
- **Size.** The map lies in a wide field of empty space and can be
  painted outward in any direction; the yellow line marks its extent.
  The empty space is the editor's alone. A map is saved with none
  around it, so there is nothing to crop or to make room for by hand.
- **Fast strokes leave no gaps.** The pencil fills in every tile between
  where the pointer was and where it is.
- **Undo.** Ctrl+Z and Ctrl+Y, a whole drag at a time.
- **The bottom lines.** The first says what is under the pointer and its
  column and row. The second, in red, says what stops the game using the
  map as it stands, such as a symbol defined but not placed.
- **Saving.** Ctrl+S writes the file in the tidy layout and redraws its
  picture in `docs/maps/`. If the map has grown or shrunk at its left or
  top since it was last saved, every tile's column or row has changed;
  the bottom line says by how much, because a game saved on the map
  will then have the player and mined-out rocks out of place. A map with a problem can be saved, after a
  warning, so that work is not lost; the game will not start until it is
  put right.
- **A new map** (File > New map) starts as a small walled room. The
  game does not know of it until its file is added to `"maps"` in
  `game.json`, and the player cannot reach it until a script sends them
  to a point on it.

The editor changes map files only. New kinds of rock, people, floors,
and so on are still added in the JSON files; restart the editor to see
them. It cannot open a map with a mistake the game could not draw past,
such as an unknown symbol; fix that in a text editor first.

### Symbols a map defines for itself

`tiles.json` says what a rock or a barrel looks like everywhere. A
`!define` is for what belongs to one map or needs saying per map. The
symbol must be one the tileset does not already use; digits are a good
choice. Each kind of thing has one home: a rock is always a `tiles.json`
symbol, a person is always a define.

| Define | Meaning |
| ------ | ------- |
| `!define H npc harl` | A person from `npcs.json`. May be on the map more than once |
| `!define 1 point from_cave` | A named spot. Open floor to the player; scripts send the player there with `travel_to("cave.txt", "from_cave")`. On the map exactly once |
| `!define 2 like r` | A copy of the tileset's thing `r`. Only useful with `on` |

More kinds will come as the game grows: monster spawns with a range to
wander in, fishing spots, and so on. Each will be a define with its
settings on its line.

### What floor is under a thing

A rock, a person, a point, or the `@` takes up its tile's one character,
so the map does not say what floor it stands on. The game works it out:
whichever floor most of the four tiles beside it are. On a tie, the floor
listed earlier in the tileset; if none of the four is floor, the
tileset's first floor.

That is right nearly always. Where it is not, say so with `on` and a
floor's symbol at the end of any define:

    !define 2 like r on ,
    !define H npc harl on .

### After changing a map: tidy it and look at it

Run the **Build Maps** run configuration, or

    mvn test-compile exec:java@maps

It does two things. It rewrites every map file in one fixed layout
(settings, defines in order, `!map`, rows with no trailing spaces and no
empty space around the map), which
`mvn verify` insists on, so that a file is the same whoever wrote it. And
it draws each map to `docs/maps/` as a picture, drawn by the game's own
code, with column and row numbers and each defined symbol marked.

**If you are an AI: always do this, and always open the picture, before
calling a map change done.** Counting characters in a grid goes wrong by
one more easily than it seems. Check in the picture that each thing you
placed is in the room you meant, on the floor you meant, that doors line
up with corridors, and that nothing blocks the way to anything. The
pictures are committed, so a reviewer sees the change too.

### What the build checks

`mvn verify`, and the game at startup, refuse a map with: an unknown
setting or kind of define; a symbol that means nothing; a define the
tileset already uses, or that is nowhere on the map; an npc, thing, or
floor that does not exist; a point on the map twice or two points of one
name; no `@` or more than one; a tab; or a layout that is not the tidy
one. Each is explained in words that name the file.

It does not yet check that every place can be reached, or that a point a
script names exists; the second shows only when the script runs.

### Saved games and maps

Saved games record the map by its file name, so renaming a map file
strands anyone saved there at the first map's start. They record the
player and emptied rocks by column and row, so a map that grows or
shrinks at its top or left moves a saved player; one who lands in a wall
is put at the `@`.
