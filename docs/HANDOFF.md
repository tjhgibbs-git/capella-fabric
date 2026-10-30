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

## Gates

| # | Gate | State |
|---|---|---|
| 1 | Foundation + kit ported, hash bit-exact, kit element-exact vs TS | done (e1ea68a) |
| 2 | Blockstates, palettes, rasterizer, normalize; oracle blocks match | done (c8ae547) |
| 3 | `cathedral/plan.ts` → `CathedralPlan.resolveLayout`, `Scene`, JSON loader; layout numbers match TS on 7 fixtures | done |
| 4 | `cathedral/shell.ts` + `buttress.ts` + `mural.ts` + `kit/wall-walk.ts`; elements match oracle (less footing) | in progress (see below) |
| 5 | `BuildingGenerator` (params → plan → shell → raster → BlockVolume, yaw from facing, origin at west door); `OracleTest`; preview PNGs | code written, uncompiled |
| 6 | Fabric module compiles against core (cannot run here: no maven.fabricmc.net) | |
| 7 | README, this handoff rewritten for the laptop session | |

## Where gate 4/5 stands (2026-10-30, end of first session)

- Gate 4 was being ported by a background agent: `cathedral/CathedralShell.java` (shell.ts),
  `Buttress.java`, `Mural.java` (done), `kit/WallWalk.java` (done). If `CathedralShell` is missing
  or incomplete, port the rest of `cathedral/shell.ts` + `buttress.ts` per `docs/PORTING.md`:
  `generateShell(Scene, PlanLayout, String prefix) -> ElementTree`, minus crypt, platform cut,
  mezzanines, footing and ways; `levelledSlab(layout.platforms, ...)` becomes `Parts.floorSlab`.
- Verify gate 4 against the oracle: dump with `tools/oracle/dump.mts` (fixtures parish, minimal,
  default, irregular-bays, max-plan, double-transept, turned), load each `.scene.json` with
  `Scene.fromJson`, `CathedralPlan.resolveLayout`, `generateShell`, write with
  `debug/ElementJson`, then `tools/oracle/compare.py elements`. Filter the footing's elements
  (plinth/terrain kinds, ids from footing.ts) out of the TS side first.
- Gate 5 code is committed but **does not compile until `CathedralShell.generateShell` exists**:
  `BuildingGenerator` (params -> plan, yaw from facing, origin at the west door threshold),
  `GeneratedBuilding`, `preview/PreviewDump`. Then add `OracleTest` and look at
  `./gradlew :core:renderPreviews -PcoreOnly --max-workers=1`.
- The fabric module is written against that API (`BuildingGenerator.generate(params, turns)`,
  `turnsFacing`, `GeneratedBuilding.volume()`), key H, presets in `core/params/Presets.java`.

## Environment (cloud session)

- `./gradlew :core:test -PcoreOnly --max-workers=1` — Maven Central rate-limits parallel
  downloads (HTTP 429); serial works. `maven.fabricmc.net` is blocked, so `fabric/` cannot be
  built here.
- The oracle needs the gothic checkout with `pnpm install`; see `docs/PORTING.md`.
