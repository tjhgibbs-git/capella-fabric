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
| 3 | `cathedral/plan.ts` → `CathedralPlan.resolveLayout`, `Scene`, JSON loader | done (30c7165) |
| 4 | `shell.ts`, `buttress.ts`, `thrust.ts`, `mural.ts`, `kit/wall-walk.ts` → `CathedralShell` etc. | done (767d807) |
| 5 | `BuildingGenerator`, `OracleTest`, generator and fuzz tests, previews | done |
| 6 | Fabric module builds and runs | **next, on the laptop** |
| 7 | README, this handoff | done |

## What is verified (cloud session, 2026-10-30)

- `./gradlew :core:test -PcoreOnly --max-workers=1`: 12 tests, all green.
  - `OracleTest`: on parish, minimal, default, irregular-bays, max-plan, double-transept and
    turned (yaw 90), every element matches Capella field for field. The SHA-256 of every voxel
    after rasterize and normalize matches too. The TS side is the real generator with its footing
    elements dropped (`tools/oracle/dump.mts`); resources are in `core/src/test/resources/oracle/`.
  - `BuildingGeneratorTest`: every preset builds deterministically, quarter turns swap the
    footprint, and the origin sits at the west door threshold.
  - `ParamFuzzTest`: 60 random settings across every slider and choice, at random turns. All build.
- `renderPreviews` output looks right by eye; `docs/images/` holds three of the renders.
- Each preset reproduces its fixture exactly (cathedral = default, minster = double-transept,
  dark = irregular-bays, colossal = max-plan, chapel = minimal, parish = parish).

## What is not verified: the laptop session's job

1. **`./gradlew build`**. `fabric/` has never been compiled: `maven.fabricmc.net` is blocked in the
   cloud. It is Sylvan's mod shell renamed for churches (key H, `/capella`, `Presets`,
   `BuildingGenerator.generate(params, turns)`, `turnsFacing`, `GeneratedBuilding.volume()`).
   Every core call it makes was checked by hand against the core API, but expect some
   Sylvan-to-Capella rename slips in client code.
2. **The GameTests**: `fabric/src/gametest/.../PlacementGameTests.java`.
3. **A running client**: the GUI, Build here (does the west door land on the targeted block, and
   does the nave run the way you face?), `/capella undo`, and each save format. Check that
   `.schem` pastes in Axiom and WorldEdit put the door at the paste point.
4. **Placement speed for colossal** (~500k blocks): Sylvan's per-tick placer has not been tried at
   that size.

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
