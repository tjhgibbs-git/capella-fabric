# In-game verification handover — castle buildings, windows and gatehouses

Date: 2026-11-11. This is the current handover; it supersedes the branch, version and
verification-status statements in the older `CASTLE_HANDOFF.md`.

## Start here

Work in `/workspace/capella-fabric`, branch `feature/castle-buildings`, Minecraft **26.3**.
The work starts from `0f8cb23c46e5b676a5b7bdc3d331899e28854099` on `26.3` and is
published on `feature/castle-buildings` in a draft PR targeting `26.3`. Continue from that
branch and preserve any newer working-tree changes. The built mod is
`fabric/build/libs/capella-1.4.0.jar`; build it locally when starting from a fresh checkout.

The user explicitly requested a handover to another agent for in-game verification. The next
step is real client inspection and movement/UI testing, with screenshots and fixes where
needed. Offline renders and server GameTests are already available; they do not establish
client appearance, transparency, collision, lighting, combat navigation or UI feel.

Read `docs/PORTING.md` before editing core code. These castle builders are independent of the
cathedral oracle. Keep the requested work focused on the castle structures and their controls.

## What changed and must be retained

- Improved tower/keep masonry, keep entrance gables and windows. Keeps have flush dressed
  jambs, shallow sills/hoods and inward-widening reveals. Larger keeps have paired upper lights
  with stone mullions; compact keeps use single loops. Blind arches that crowded the facade
  were removed. Short upper storeys cut through projecting bands rather than being obstructed.
- **Gothic turret roof windows:** the old glass was a screen in front of a solid gable/roof.
  `Fortification.gothicDormers()` now cuts actual pointed openings through both roof layers,
  with recessed cyan glass panes at depth 1, a stone mullion, bonded heads and sills, side
  cheeks, and gabled roof caps returning into the main roof. The attic behind the glazing is
  open. Check pane connections, transparency and junctions especially closely in game.
- **Buttress/cornice joins:** full-width capitals join the upper stem to the continuous
  cornice; exposed weathering caps are placed after the body, and plain-wall slabs use their
  bottom half to avoid a half-block gap. Gatehouse abutment collars bond the curtain cornice
  into square or curved towers.
- Two compact towers: `sentrytower`, `timberwatchtower`, body width 7–13, height 8–32.
  Ladder access preserves room inside small footprints. Timber lookout has an open guarded
  gallery under its roof. Their controls deliberately omit the larger towers' floor spacing.
- `barracks` and `stables` in **Garrison**: length 15–65, width 9–21, wall height 5–12;
  hollow roofs, timber framing, stone bases, clear three-wide passages. Furnishings adds bunks
  and lockers, or stall partitions/hay/troughs. Short barracks omit upper bunks. No entities spawn.
- **Gatehouse** is an option on every wall type, with presets `gatehouse`, `roundgatehouse`,
  `roofedgatehouse`. Towers: `square`, `round`, `roofed`; arches: `flat`, `round`, `pointed`;
  gates: `open`, `raised_portcullis`, `closed_portcullis`, `timber`. Closed gates are static
  generated blocks, not animated mechanisms. Very small footprints reduce to compact gate
  piers; rounded outlines require enough tower width. Low walls reduce arch rise to preserve
  at least three blocks of passage headroom. Towers large enough for rooms have floors,
  ground doors off the passage, and ladders to the curtain walk and fighting deck.
- **Gatehouse position slider:** `gate_position`, 0–1, step .01, default .5. The client displays
  percentages. 0% is towards the wall's placement end; 100% is towards its far end. It moves
  the complete gatehouse without shrinking towers and reserves one modular end column on
  both sides. A minimum-length wall may have no spare travel. Older strings retain the centred
  layout. At even lengths the middle is rounded towards the placement end, preserving the old
  centred result. The wall's origin remains the centre of its west/placement end, not the gate.
- Wall walk enabled: the supported route crosses the wall, both gate-tower chambers and the
  bridge at one deck height. Wall walk disabled: outer tower-walk doors are closed. Ordinary
  wall ends have no ladders. Integrated gatehouse ladders are inside the tower chambers.

No terrain, ruin, automatic mobs or loot were added. A separately placed standalone tower is
not automatically connected/carved when another wall is pasted against it; distinguish that
older limitation from the integrated gatehouse, whose junctions are generated together.

## Build and launch

The working cached toolchains are JDK 25 and Gradle 9.8.2; core emits Java 21 bytecode.

```sh
cd /workspace/capella-fabric
export JAVA_HOME=/workspace/toolchains/jdk-25.0.5.0.3
export GRADLE_USER_HOME=/workspace/.gradle
/workspace/toolchains/gradle-9.8.2/bin/gradle :fabric:runClient
```

Gradle needs local sockets and may require the execution tool's normal sandbox escalation.
Do not treat a sandbox wildcard-IP/socket failure as a source/build failure. Fabric Maven and
Mojang dependencies have worked in this environment; recheck live configuration if needed.

A sibling `/workspace/block-scraper` is available. For driven client inspection, first read
its `AGENTS.md` and README; it is dev tooling and not shipped as a mod dependency. The Capella
build already exposes `:fabric:runBlockScraper` when this sibling is found. The documented path is:

```sh
/workspace/block-scraper/block-scraper start --project-dir /workspace/capella-fabric --task :fabric:runBlockScraper
/workspace/block-scraper/block-scraper status
```

Use its world, widgets, click, screenshot and input commands as documented. Read every captured
PNG; a successful command alone is not visual verification. The actual generator screen class
is `io.github.tjhgibbs.capella.client.GeneratorScreen`. Avoid the stale TreeGeneratorScreen name
in legacy comments. New fields/classes require restarting an already-running stock JVM client.
No Minecraft client/manual playtest was launched; only server GameTests used temporary worlds.

## In-game checks, in priority order

1. **Gothic dormers first.** Place `gothicturret` seed 7. Inspect all four faces close up, from
   outside and inside the roof, in daylight and at night. Verify pointed panes are recessed,
   their arms meet the stone jambs/mullion, nothing opaque sits immediately behind them,
   and the gabled caps/cheeks meet the copper roof without holes, z-fighting, floating trims
   or daylight leaks. Roof pinacles and the main roof should remain distinct. Repeat widths
   13, 14, 19, 35; heights 12 and 38 (96 as a tall stress case); multiple seeds and at least
   one limestone/sandstone version. Include rotations. The glass colour is intentionally cyan.
2. **Position slider and integrated gate joins.** Press H, choose a gatehouse preset, move
   Gatehouse position through 0%, 20%, 50%, 80%, 100%. Confirm visibly unequal wall wings,
   fixed tower size, live preview refresh, no clipping at either end and stable placement
   origin. Try 9×5×5, 10×6×6, 25×5×12, 40×7×24 and a long wall. Minimum modules should stay
   valid even if the slider has no visible travel. Also test gatehouses on arcaded and plain
   walls, and wide-but-short walls (9×11×40) plus long-but-low walls (160×5×5).
3. **Tower/arch/gate combinations.** Exercise all three tower shapes and arch profiles, plus
   each gate state. Open and raised gates must pass a player; try a horse on the three-wide
   minimum passage. Closed portcullis/timber must obstruct the route. These gates are static.
   With Wall walk on, walk end-to-end across both towers and the bridge without flight; then
   enter each ground room and climb the ladder to the walk and upper deck. Check supports,
   ladder backing, exit headroom, guards and roof clearance. With Wall walk off, outer walk
   doors should close without blocking the ground gateway.
4. **Buttresses at parapets.** Inspect curtain, high curtain, arcaded and plain walls on both
   elevations, including heights 5/6/12/24/40. Check all three buttress lanes against the
   cornice, caps at setbacks, and curved gate-tower abutments. No half-block gap should remain
   between a capital and its coping. Inspect wall-end joins and shifted gatehouses at extremes.
5. **Keep windows and traversal.** Verify paired lights or single loops, inner reveals and
   shallow hoods close up. Walk the larger towers' stairs in both lanes, including the final
   tread, landings, keep corner rooms and ladders. Include widths 13/14/19/35 and floor spacing
   5/12, seeds 1/7/19. Check collision and survival movement; offline headroom tests are not a
   substitute. If appropriate, test representative melee/ranged mob navigation separately.
6. **New small buildings.** Walk sentry/timber towers at widths 7/8/13 and heights 8/17/32.
   Inspect timber gallery rails and its ladder exit. Walk barracks/stables at size extremes;
   inspect bunk headroom and support, horse access, stall fronts and water troughs.
7. **UI, save, export and undo.** Check H screen layout/scrolling at small and large GUI sizes,
   preset/type changes, All settings, option labels and percentages, seed/variations, rotation,
   save/reload (including gate position), Build here, Undo, and an Axiom/WorldEdit paste.
   Confirm origins and stair/pane directions after rotation. Preserve existing church controls.

Use an expendable creative test world, space samples apart and record exact parameters and
coordinates. `/capella place <preset> [seed]` and `/capella build <encoded params>` build at the
player's feet facing the player's direction. Examples (paste into game chat, not a shell):

```text
/capella place gothicturret 7
/capella build seed=7;type=gothic_turret;width=14;height=12;stone=deepslate;access=true
/capella build seed=7;type=curtain_wall;length=40;width=5;height=12;gatehouse=true;gate_position=0;gate_towers=round;gate_arch=pointed;gate_style=raised_portcullis;walkable=true
/capella build seed=7;type=curtain_wall;length=40;width=5;height=12;gatehouse=true;gate_position=1;gate_towers=round;gate_arch=pointed;gate_style=open;walkable=true
/capella undo
```

## Code, automated checks and visual evidence

- `core/.../small/Fortification.java`: tower shells, keep windows, Gothic dormers, wall buttresses.
- `CastleGatehouse.java`: derived layout, positioning bounds, replacement footprint, arch/gate,
  abutments, tower interiors and connected walk. `Garrison.java`: compact towers and ranges.
- `params/Params.java`, `Presets.java`, English lang JSON: controls and starting examples.
- Tests: `GothicDormerTest` (four faces, materials, sizes, open backing, bonded heads/sills,
  rotated caps), `GatehouseTest` (all styles/scales, moved assemblies, passages, cornice,
  deck continuity and tower ladders), `KeepWindowTest`, `TowerTraversalTest`, `GarrisonTest`,
  `FortificationTest`. Server `PlacementGameTests` checks registry states for every preset
  and existing placement/undo/export; it does not playtest client movement.

```sh
# Full checks and distributable jar
/workspace/toolchains/gradle-9.8.2/bin/gradle :core:test :fabric:build
# Fast targeted checks
/workspace/toolchains/gradle-9.8.2/bin/gradle :core:test -PcoreOnly --tests '*GothicDormerTest' --tests '*GatehouseTest' --tests '*TowerTraversalTest'
# Layered reviews; -PreviewDir=<path> can override output for a single review task
/workspace/toolchains/gradle-9.8.2/bin/gradle :core:reviewGothicDormers :core:reviewGatehouses :core:reviewKeepWindows :core:reviewWallJoins -PcoreOnly
```

Review outputs in `core/build/` are ignored/uncommitted. Existing session evidence is under:

- `/workspace/gothic-roof-review/{before,after}/gothic-width{13,14,19,35}-layers.png`
- `/workspace/gatehouse-review/` (exteriors, half-sections, walk and gate-plane layers);
  final task output in `core/build/reviewGatehouses/` also includes `left` and `right` examples.
- `/workspace/keep-review/`, `/workspace/wall-review/`, `/workspace/castle-review/`.

All of these are offline average-colour block geometry, not Minecraft screenshots. The
renderer does not reproduce stained-glass transparency, game lighting or collision perfectly.
Final automated verification on 2026-11-11: **47 core tests reported, 0 failures,
1 intentionally skipped**; all **5 required Minecraft server GameTests passed**; Fabric build
and both final review tasks succeeded. The final command was
`:core:test :fabric:build :core:reviewGatehouses :core:reviewGothicDormers` with the cached JDK
and Gradle above. No manual client verification has been performed for these changes.

## Required next-agent handback

Write an in-game verification report with screenshots, parameter strings, locations and
pass/fail results for the checks above. Fix observed geometry/UI failures, rerun relevant tests
and the Fabric build, and distinguish client-verified results from anything still unverified.
Do not report movement, visuals, mob pathfinding or exports as passed solely from these offline
renders or server tests. The user authorized publishing this branch and a PR into `26.3`. Continue verification fixes
on this PR branch and report updated validation. Do not merge the PR unless requested.

## In-game verification results (2026-11-11, client 26.3, flat creative world)

Checked in the client with block-scraper screenshots, at close range as well as overall views.
Fixed during the pass:

- **Gothic dormers** were solid stone fins with blank gables. They are now copper dormers
  with a dressed stone front: a pointed two-light window (haunch stairs, a wall-block
  mullion, panes recessed one block), a 1:1 coped gable with a finial, and a copper gable
  roof and cheeks that run back into the main roof. Turrets 27 or more blocks wide get two
  dormers per face. Checked at widths 13, 14, 19 and 35, in sandstone, limestone and
  deepslate, from outside and from the attic.
- **Pavilion roof:** stair courses sat on the lower course of each two-course step when the
  height was even, so the roof looked terraced. Stairs now always sit on the upper course.
- **Ground trenches:** the placer wrote air down every occupied column, so crowns, galleries
  and eaves dug a one-block trench in the grass beneath them. Implicit air is now written
  only from the threshold up; excavation stays explicit `cave_air`. Exports are unchanged.
- **Gatehouse:** the flanking towers barely stood proud of the curtain, and round towers did
  not read as round. Towers now project 1–3 blocks beyond both faces, with corbelled
  fighting crowns wherever they are clear of the walk. The arch intrados and the ring
  follow the curve with stairs. Tall flat openings become shouldered arches. The raised
  portcullis hangs in its slot with a level foot, at least three blocks above the passage,
  instead of being fixed to the facade. Loops no longer sit behind the ladder backing.
  Each chamber has a hanging lantern.
- **Walls:** removed the upside-down stair "hooks" that stuck out of the merlons. A wall
  without a walk now has a closed saddleback coping in place of T-shaped crossbars and a
  see-through stair ridge.
- **Compact towers:** a projecting, weathered plinth, flush dressed loops in place of
  floating sill slabs, and a sill beam where the timber frame meets the stone base.
  The hall end lintels are timber, not a floating stone strip.
- GUI sounds: the stonecutter replaces bone meal for new seed and variations, and a
  deepslate-brick placement sound plays for Build here.

Client-verified: the appearance of every castle, garrison and wall preset; the size extremes
listed above; the GUI preset groups, Gatehouse controls and live preview at 0%, 50% and
100%; Build here; `/capella undo`. **Not verified in the client:** walking or collision on
stairs
and ladders, mob pathing, horses in the gate, Axiom/WorldEdit paste of exports, night
lighting. `:core:test :fabric:build` passed, including all 5 server GameTests.
