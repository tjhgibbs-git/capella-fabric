# Porting Capella's cathedral to Java

The generator in `core/` is a **faithful port** of the single-cathedral path of Capella
(`tjhgibbs-git/mc-gothic-generator`, TypeScript, `src/core/`). Faithful means: same element ids,
same geometry, same hash keys, so the same scene and seed produce the same blocks. That is what
lets the TypeScript act as an oracle (see *The oracle* below), and it is worth more than any
cleverness a rewrite could add.

What is **not** ported, by decision (2026-10-30):

- every other structure type (crypt, wall, keep, house, tower, site, street) and composition;
- the cathedral's own undercroft crypt (`cathedral/crypt*.ts`, `kit/undercroft.ts`, `kit/maze.ts`);
- the tail: collapse/support/rubble, relief, vegetation, trees, clutter, debris, water, nav,
  traversal, reach, metrics, brushes, reroll, regional regeneration;
- **terrain, entirely** (user, 2026-10-30): the building floats, as Sylvan's trees do. No footing,
  earthwork, terrace, approach steps, descents, backfill or structure void (`footing.ts`,
  `earthwork.ts`, `kit/descent.ts`, `gradient.ts`, `backfill.ts`, `structure-void.ts`). The oracle's
  `plinth`/`terrain` elements and below-floor blocks that come from those are expected differences;
- painted fields other than `terrainHeight`, and with it `platform.ts` / `mezzanine.ts` (floor
  tiers) and `path.ts` (ways). Ruin is the planned follow-up; floor tiers are in the handoff.

## Conventions (read before porting a file)

- **One TS module → one Java class**, same name in PascalCase, in the package listed in the map
  below. Exported functions become `public static` methods with the same names (`buildRoof`,
  `resolveLayout`). Private helpers stay private with the same names.
- **TS interfaces become Java final classes with `public final` fields of the same names**, not
  records, so `layout.levels.floorY` reads the same in both languages. Nested anonymous object
  types get a small named class. Optional fields are nullable (`Double`, `String`, objects) and
  documented. Readonly arrays become `List<T>` (immutable) or primitive arrays where the TS used
  them for grids.
- **`number` is `double`** unless the value is plainly a count or an index used to index an
  array, then `int`. When in doubt, `double`. Never let Java integer division happen where the TS
  divided: `Math.floor(a / 2)` with int `a` must be `Math.floorDiv(a, 2)` or double arithmetic.
- **Strings built from numbers use `Js.str(n)`** (`util/Js`): `` `bay-${i}` `` must give `bay-1`,
  never `bay-1.0`. This applies to every element id and every hash key. Hash keys may be passed as
  `Number`s directly; `Hash` mixes integral doubles exactly like JS.
- **`Math.round` is `Js.round`** (returns double, half up). `Math.sign` is `Math.signum`.
- **String-literal unions become enums** with an `id()` giving the TS string (`TowerCap.SPIRE.id()`
  is `"spire"`). Element `params` still hold the TS strings (`"facing" -> "north"`), because the
  rasterizer reads them as block properties.
- **`s.add({...})`** becomes `s.add(new AddOptions(id, kind, role, level, geometry).parent(..)
  .supports(..).param("facing", "north"))`, or the five-argument shorthand.
- **Geometry**: `{ kind: 'box', box }` is `new Geometry.Solid(box)` (or `Parts.solid`),
  `heightfield` is `Geometry.HeightField` (NaN = no column), etc. `box(x0,y0,z0,x1,y1,z1)` is
  `Box.of(...)`.
- **Iteration order matters.** Use `LinkedHashMap`/`ArrayList` where the TS iterated a `Map`,
  object or array; sort with the same comparator. Element order decides nothing in the
  rasterizer (it sorts by pass, kind, id) but it decides `supports` resolution.
- Keep the TS doc comments that explain *why*; drop the ones that narrate phase history.
- Where the port drops a branch (crypt, platforms, mezzanines, ways), delete it cleanly rather
  than stubbing it, and leave a one-line comment naming what was there.

## Map

| Java package | TS sources |
|---|---|
| `geom`, `hash`, `util` | `types.ts`, `hash.ts` |
| `element` | `elements.ts` |
| `kit` | `kit/geometry.ts`, `kit/parts.ts`, `kit/frame.ts`, `kit/roof.ts`, `kit/cap.ts`, `kit/vault.ts`, `kit/tower.ts`, `kit/turret.ts`, `kit/pinnacle.ts`, `kit/pier.ts`, `kit/wall-walk.ts` |
| `palette`, `blockstate` | `palette.ts`, `blockstates.ts`, `generated/blockstate-data.ts` |
| `raster` | `volume.ts`, `raster/primitives.ts`, `raster/rasterize.ts`, `raster/normalize.ts`, `settle.ts` |
| `field`, `terrain`, `layout` | what `cathedral/plan.ts` needs of `field.ts` and `terrain.ts` (a flat datum), `structure-layout.ts` |
| `cathedral` | `scene.ts` (cathedral params only), `cathedral/plan.ts`, `cathedral/shell.ts`, `cathedral/buttress.ts`, `cathedral/mural.ts` |
| root | `generate.ts`, reduced to the pristine pipeline |

## The oracle

`tools/oracle/dump.mts` runs the TypeScript generator on a scene (crypt forced off, fields reduced
to `terrainHeight`, `detail: 'shell'`). It drops the footing's elements, then rasterizes and
normalizes the rest the way the port does. It writes `<name>.scene.json`, `<name>.elements.json.gz`,
`<name>.oracle.txt` (a block digest) and `<name>.blocks.txt`. `OracleTest` checks the port against
the first three, which are committed under `core/src/test/resources/oracle/`. Delete
`blocks.txt` after use; it is only for `compare.py blocks` when a digest mismatches. Run it from a checkout of the gothic generator:

```sh
cd ../mc-gothic-generator && pnpm install
npx tsx ../capella-fabric/tools/oracle/dump.mts fixtures/scenes/parish.json ../capella-fabric/core/src/test/resources/oracle/parish
```

## Fabric architecture updates (2026-11-01)

The Fabric generator now intentionally closes the high transept shoulders between the aisle
leads and nave eaves. Its gallery stairs use thin supported landings, a deck exit and three
blocks of headroom; unsupported diagonal wall rails were removed. These are Fabric fixes,
not a change to the historical TypeScript oracle.

SmallChurch is an independent builder. Its seeded sunken floors and balconies supersede the
earlier exclusion of floor tiers for small churches. No terrain or cathedral crypt generator
is included. Negative-space cells below the datum export as cave_air, preserving excavation
while empty cells outside the church remain untouched in Axiom blueprints.
