# Capella: gothic church generator for Fabric (Minecraft 26.3)

Generate a wayside chapel, a village church with its west tower, or a full cathedral. Preview it
live in a GUI, then **build it where you stand** or **save it as a schematic** for Axiom,
WorldEdit or 14erEdit.

![The generator](docs/images/gui.png)

![A village church, built in the world](docs/images/village.png)

Small churches (chapel, parish church, bell tower) are built block by block: two-thick walls with
buttresses, a plinth and a cornice, coped gables with crosses, steep roofs, a chancel, a porch, a
bellcote or a staged west tower with a louvred belfry and a bell, and a spire, saddleback or
parapet on top. Inside are pews, trusses, lanterns and an altar.

Cathedrals use a faithful Java port of the cathedral path of
[Capella](https://github.com/tjhgibbs-git/mc-gothic-generator), fitted to the size you ask for.
The GUI, placement and export code come from
[Sylvan](https://github.com/tjhgibbs-git/tree-generator-fabric).

## Using it

Press **H** to open the generator.

- **Presets** (left): *Churches* (wayside chapel, chapel, village church, parish church, bell
  tower) and *Cathedrals* (Romanesque, cathedral, minster, flamboyant, desert basilica, great
  church, colossal). Hover a preset for a description.
- **Preview** (middle): Above / Level / Plan views, and ◀ ▶ to turn the building.
- **Settings** (right): the main settings for the kind of building you have chosen: Building type,
  Length / Width / Height in blocks, then a few choices (bell, tower top, windows, stone).
  **All settings** shows everything that applies to that type, grouped, plus the seed.
- **New seed** re-rolls the stone mix. **Variations** shows six versions side by side
  (*Subtle* or *Wild*); click one to use it, right-click to pick several and save them together.
- **Save...** writes `.schem` (Axiom / WorldEdit), 14erEdit and/or `.nbt`.
- **Build here** (creative only) puts the west door on the block you are looking at, with the
  nave running the way you face. `/capella undo` takes it back.

Commands:

- `/capella place <preset> [seed]`
- `/capella build <encoded params>`
- `/capella export <preset> <sponge|structure|14eredit> [seed]`
- `/capella undo`
- `/capella presets`

What it builds is pristine and stands on no ground: there is no footing, terrain, crypt, ruin or
greenery. That is a decision, not a gap; see the handoff.

## Building

```
./gradlew build                                   # everything (needs Fabric maven + Mojang)
./gradlew :core:test -PcoreOnly                   # core only, any JDK 21+, no Minecraft
./gradlew :core:renderPreviews -PcoreOnly         # PNG of every preset -> core/build/previews
./gradlew :core:renderPreviews -PcoreOnly -Ppreset=cathedral -Pseed=7
```

Read [docs/PORTING.md](docs/PORTING.md) before changing anything under `core/`. It covers the
porting conventions and how to regenerate the oracle from the TypeScript.
