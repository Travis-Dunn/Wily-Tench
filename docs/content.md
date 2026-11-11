# Adding and changing content

Everything the game *contains*, as opposed to how it works, lives in text
files under `src/main/resources`. None of it needs Java. This page is the
guide to those files, for a person or an AI.

| To change | Edit |
| --------- | ---- |
| What can be carried | `data/items.json` |
| What skills there are | `data/skills.json` |
| The pictures on the side panel's tabs | `data/interface.json` |
| What can be used on a map: rocks, ladders | `data/locs.json` |
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

## tiles.json

This file gives meaning to the characters in map files. A **tileset** is
the look of a kind of place and defines its walls and floors; a map names
the tileset it uses. **Things** stand on the floor. Things listed inside a
tileset exist only there; things in the list at the bottom of the file can
be put on any map.

Two characters are built in and cannot be redefined: a space is nothing at
all, and `@` is where the player arrives. `!` cannot be used either.

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
  there; then Walk here; then Examine, if anything stands there; then
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
| `region` | Optional. The part of the PNG to take: left, top, width, height, in pixels. Without it, the whole image |

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

## palette.json

The game shows a fixed set of colors, and every pixel of every sprite is
one of them.

    { "name": "tan", "rgb": "D1935F" }

`colors` lists them. **The order matters**: converted art stores each
pixel as a position in this list, so add new colors at the end, and
rebuild the atlases after any change. Ten of the names are used by the
game's own interface and must stay: ink, night, slate, rust, ash, leaf,
mint, amber, peach, bone.

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
      "scripts": ["travel.whtl", "mining.whtl", "smithing.whtl"],
      "starting_items": ["bronze_pickaxe"]
    }

Every map and script file must be listed here to be part of the game. A
new game begins at the `@` of the first map.

## Maps

A map is a text file in `maps/`:

    !name Copper Cave
    !tileset cave

       ################
       #.L............#
       #..c........m..#
       #......@.......#
       ################

The lines beginning with `!` are settings: the place's name, and which
tileset of `tiles.json` it uses. Everything after them is the map, one
character per tile. Rows may be different lengths. Every map needs exactly
one `@`.

Walls are one tile thick and join up with their neighbors, so draw rooms
as outlines. Saved games record the map by its file name, so renaming a
map file strands anyone saved there at the first map's start.
