# Handoff

Capella-fabric is a Fabric mod (Minecraft 26.3) that generates a single gothic church or
cathedral, previews it in a GUI modelled on Sylvan (`tjhgibbs-git/tree-generator-fabric`), builds
it where you stand, or saves it as `.schem` / 14erEdit / `.nbt`. The generator in `core/` is a
faithful Java port of the cathedral path of Capella (`tjhgibbs-git/mc-gothic-generator`); read
`docs/PORTING.md` before touching it.

## Decisions (from the user, 2026-10-30)

- Single church/cathedral only. No crypt (neither the structure nor the cathedral's undercroft),
  no castle, citadel, street, site or multi-structure scenes.
- **Pristine only.** Rubble was bad and greenery was bloat: neither is ported. **Ruin is the one
  planned follow-up** (support graph + collapse cascade, as a document-level "Ruin" slider).
- **No terrain.** The building floats, as Sylvan's trees do: no footing, terrace, steps, backfill.
- **Floor tiers are wanted later**: an option for a nave floor that is not one flat plate
  (different tiers, for gameplay). Capella's `cathedral/platform.ts` and `mezzanine.ts` implement
  this from a painted `platform` field; the port would drive them from a param instead.
- GUI: low cognitive load, Sylvan's layout. Output `.schem` and 14erEdit, reusing Sylvan's code.
- Work in gated blocks with a commit per gate.

## State (v1.0.0, 2026-10-30)

All gates are done. Every gate (port, oracle, shell, generator, fabric build, docs) is committed.
After gate 5 the port deliberately diverged from Capella, so `OracleTest` is disabled.

- **Small churches** (`core/small/SmallChurch`, `Style`): chapel, parish church and a free-standing
  tower, built block by block. Tower tops are spire (inside a pinnacled parapet), saddleback, or
  parapet; the windows are plain lancets or traceried lights. `Params.choices` lists the values
  that mean something for each type; the GUI and Variations use it.
- **Cathedrals** fit Capella's plan to the Length/Width/Height sliders.
- **GUI**: presets grouped into Churches and Cathedrals; settings filtered by type; the info line
  shows footprint, height and block count; Variations vary sizes across the type's own range.
- **Verified**: `./gradlew build` (core JUnit and 3 GameTests) passes. In a dev client driven by
  block-scraper (`shots/*.sh`), the presets were placed and inspected, and the GUI, saddleback
  and Variations were exercised.

### Known issue

- Once, in a dev client, a building placed just after `/capella undo` rendered with one chunk
  section missing (the tower's belfry stage). The server had the blocks: they reappeared after the
  chunk was resent. It did not happen again on a retry. If players report holes that vanish on
  relog, look at `BuildingPlacer` sending 25k changes per tick with `UPDATE_CLIENTS`.

### Not yet checked by hand

- `.schem` pastes in Axiom and WorldEdit (door at the paste point).
- Placement speed for colossal (~500k blocks).

## Follow-ups the user has asked for (not started)

- **Ruin**: the support graph and collapse cascade, as one document-level "Ruin" slider. Capella's
  `support.ts`, `collapse.ts` and `ruination` are the sources. The Java `Rasterizer` already takes
  a `RuinMask` and a weathering field, so the raster half exists.
- **Floor tiers**: a nave floor that is not one flat plate. Capella drives this from a painted
  `platform` field (`cathedral/platform.ts`, `mezzanine.ts`); the port would drive it from a param.
  The shell's floors are `Parts.floorSlab` today, exactly where the TS called `levelledSlab`.

## Environment

- **Cloud**: run `./gradlew :core:test -PcoreOnly --max-workers=1`. Maven Central rate-limits
  parallel downloads (HTTP 429); serial works. `maven.fabricmc.net` is blocked, so `fabric/`
  cannot be built there.
- **Oracle**: needs the gothic checkout with `pnpm install`; see `docs/PORTING.md`. `blocks.txt` is
  not committed (up to 26 MB). Regenerate it with `dump.mts` when a digest mismatches, then diff it
  with `tools/oracle/compare.py blocks`.
