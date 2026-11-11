> Current handover (2026-11-11): [INGAME_VERIFICATION_HANDOVER.md](INGAME_VERIFICATION_HANDOVER.md).
> It supersedes historical branch, artifact and verification details below.

# Castle towers and modular walls: next-session handoff

Continue on `feat/castle-towers-and-walls`, targeting `26.3`. The implementation commit is
`cf0ce3a`; this handoff is a separate documentation commit. The user wants the next session
to perform in-game testing and improve/finish the UI controls. Keep the PR open for that work.

## Current scope and explicit user requirements

- Three new stand-alone secular towers: `squarekeep`, `roundwatchtower`, `gothicturret`.
- Modular walls: `curtainwall`, `highwall` (larger curtain preset), `arcadedwall`, `plainwall`.
  Length, thickness, height and wall walks are configurable.
- The user requested a substantial visual upgrade. Preserve the differentiated silhouettes,
  projecting masonry, buttresses, turrets, arch surrounds, dormers and pinnacles.
- Towers must be traversable and useful for a combat encounter. They have perimeter stairs,
  landings, secondary ladders, cover, lighting, and galleries/bridges where circulation fits.
  Keep corner turrets have firing rooms on every floor and short climbs to their battlements.
- Floor heights must vary within a tower and between tower types. `floor_height` sets an
  approximate spacing; `vary_floor_heights` redistributes the intervals using the seed.
  Intervals fit the requested deck height, with at least five blocks between floor levels.
- **No wall ladders.** Wall ends are attachment interfaces for towers and adjacent modules.
  Do not restore end ladders or show the tower access control for walls.
- **No pinched final stair tread.** Each flight and its landing retain the same width through
  the exit, with both lanes clear of pillars. Normal footprints use two-block stairs; compact
  footprints use one consistent narrower width. Turn platforms also include the inside lane.

These requests supersede the historical exclusion of castles in `docs/HANDOFF.md`.
No terrain, ruin or automatic mob spawning was added.

## Implemented controls: inspect and refine in the client

The preset list files castle towers under Towers and walls under Walls (foldable collections; see HANDOFF v1.4.0). `Params.basic`, `Params.applies`, and the English
translations expose tower size, floor spacing, floor-height variation and stairs/ladder access.
Furnishings is available under All settings. Walls expose size and Wall walk, without access.
These controls compile but have **not** been manually exercised in a Minecraft client.

Check discoverability, labels, layout/scrolling, enabled states and tooltips. Explain that:

- Height measures to the fighting deck; roofs and battlements extend above it.
- Floor spacing is fitted to total height, rather than an exact storey count or a manual list.
- Access affects tower stairs and ladders; Furnishings affects cover and room lighting.
- Small towers omit galleries when those would obstruct the wider stair route.

Exercise type changes, preset changes, All settings, seeds, Subtle/Wild variations, saving and
reloading parameters, rotation, preview refresh, Build here, Undo, and export. Refine UI behavior
and controls as needed; preserve the explicit requirements above.

## In-game testing still required

Automated geometry checks and server GameTests passed. **No manual client playtest or mob
pathfinding test has been completed for these new structures.**

1. Place all seven presets in a creative test world. Inspect all sides, roofs and windows.
2. Walk every tower from the west doorway to the deck without flight. Check both lanes of
   every tread, every corner/landing, exit headroom, rails and ladder transitions. Specifically
   verify the final tread does not narrow against an exterior pillar.
3. Enter all four keep corner rooms on every level and climb onto their battlements.
4. Try survival movement and a representative melee/ranged encounter. Check mob navigation,
   snagging, fall hazards, sightlines, cover, ability to flank and bridge choke points. The
   generator does not populate mobs or loot; spawn test opponents in the test world as needed.
5. Repeat at compact sizes (13/14 wide), ordinary and large sizes (19/35 wide), multiple
   heights (12/38/96), floor-spacing extremes (5/12), and seeds 1/7/19. Include an even width.
6. Join wall segments and towers at matching deck heights. Check open ends, alignment,
   copings, side buttresses and movement along the wall walk. Confirm there are no wall ladders.
   Automatic connection carving is not implemented: inspect how the wall meets a tower shell
   and identify any needed connector/doorway UX or geometry rather than assuming a join opens it.
7. Inspect at least one Axiom/WorldEdit paste for origins, stair orientation and interior clearance.
8. Capture real in-game screenshots and record problems/fixes in the PR before marking ready.

## Code map

- `core/small/Fortification.java`: exterior masonry, tower/wall dispatch, windows aligned to floors.
- `core/small/TowerInterior.java`: floor schedule, route planning, full-width stair footprints,
  clearance reservation, furnishings, galleries, corner rooms.
- `core/params/Params.java`, `Presets.java`: types, applicability, defaults and presets.
- `fabric/.../client/GeneratorScreen.java`: foldable preset collections (towers under Towers, walls under Walls); controls are generated from Params.
- `core/.../preview/TowerReviewDump.java`: strips the shell from finished generated blocks;
  writes cutaways from two directions, individual floor plans and `floor-heights.csv`.
- `TowerTraversalTest`: 108 type/width/height/seed cases for connected stair runs, both walking
  lanes and three-block headroom, plus per-floor combat-room and corner-room connectivity.
- `FortificationTest`: ladders, rotations, wall decks, modular ends, parameters and encoding.

Read `docs/PORTING.md` before changing core code. These castle builders are independent of the
historical cathedral port; avoid unrelated cathedral changes.

## Reproduction and evidence

Use JDK 25 for the Fabric client/build; the core targets Java 21.

```sh
./gradlew build
./gradlew :core:test -PcoreOnly
./gradlew :core:test --tests '*TowerTraversalTest' --tests '*FortificationTest' -PcoreOnly
./gradlew :core:reviewTowers -PcoreOnly -Pseed=1,7,19
./gradlew :core:renderPreviews -PcoreOnly -Ppreset=squarekeep -Pseed=7
./gradlew :fabric:runClient
```

Press H in game. A sibling block-scraper checkout also enables `:fabric:runBlockScraper`; see
its instructions before using that tooling. Use the actual `GeneratorScreen` class, not the
older `TreeGeneratorScreen` name still mentioned in legacy comments.

Latest complete verification: **35 core tests, zero failures; all 5 required Minecraft server
GameTests passed; full Fabric build succeeded.** Server GameTests validate every preset's block
states and existing placement/export behavior; they do not establish client movement or UI feel.

Committed previews: `docs/images/castles.jpg` and `docs/images/tower-interiors.jpg`.
Regenerated detailed cutaways/floor plans go to `core/build/tower-review/` (not committed).
These are offline average-color geometry views, not Minecraft screenshots.

In this workspace the successful full command used the cached toolchains:

```sh
JAVA_HOME=/workspace/toolchains/jdk-25.0.5.0.3 \
GRADLE_USER_HOME=/workspace/.gradle \
/workspace/toolchains/gradle-9.8.2/bin/gradle build :core:reviewTowers \
  -Pblockscraper.dir=/tmp/no-block-scraper --offline
```

Omit that blockscraper override to use the sibling dev tooling. The mod jar is
`fabric/build/libs/capella-1.2.0.jar`. The old handoff's claim that Fabric Maven is unavailable
is historical; recheck current environment networking rather than treating it as a blocker.
