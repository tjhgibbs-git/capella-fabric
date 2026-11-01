# Reviewing large cathedral interiors

Use player-eye views and inspect joints, floor routes and useful room detail. A distant preview
hides a wall blocking a gallery, a missing aisle end, or a stair whose last tread has no landing.

The offline review task renders the **actual generated block volume** at player-eye height:

```sh
source /workspace/environment/activate.sh
gradle :core:reviewInteriors -PcoreOnly -Ppreset=cathedral,minster,dark,colossal -Pseed=1 -PreviewDir=/workspace/review-after
```

It writes 18 perspective views per preset, a contact sheet, floor and gallery clearance maps,
and a camera manifest. Green map cells have a supporting block and two air cells; brown cells
are blocked. Red cells have an obstruction without a full-block support immediately beneath it.
These maps are deliberately conservative: slabs, stairs, railings and furniture require visual
inspection and physical traversal tests. Red does not automatically mean a construction defect.

These are **offline geometry diagnostics**, not Minecraft screenshots. Average block colours,
approximate shapes (straight stairs, slabs, fences, walls and fixtures), and neutral ambient
lighting expose structure. They do not model texture assets, stair corner collision, transparent
glass rendering, mob spawning, or Minecraft's propagated light. Inspect actual lighting and
walk the routes in Minecraft before declaring an in-game walkthrough passed.

A camera manifest gives plan-frame x/z, absolute plan-frame eye Y, yaw and pitch. For an exact
before/after comparison, rerun one preset using the previous CSV; this prevents camera relocation
when new furnishing occupies the former standing cell:

```sh
gradle :core:reviewInteriors -PcoreOnly -Ppreset=cathedral -Pseed=1 \
  -PreviewDir=/workspace/review-after \
  -PreviewCameras=/workspace/review-before/cathedral-seed1-turn0-cameras.csv
```

The baseline generator's compiled classes and resources were preserved at
`/workspace/review-baseline` before the architectural edits. Baseline views are in
`/workspace/review-before`; these are local review artifacts, not committed binaries.

## Baseline findings, seed 1

- **Cathedral outer aisle, looking east:** the nonambulatory apse leaves the side aisle open to
  the sky. The aisle should end in a masonry wall with a deliberate window or portal.
- **Cathedral/minster galleries approaching a transept:** the long gallery abruptly loses its
  deck across the crossing. A usable connection needs a supported continuous route and guards.
- **West aisle stair area:** construction occupies an entire aisle bay but provides little
  readable route guidance. Inspect both turns, the final tread, exit and overhead clearance.
- **Ground nave to choir:** a raised choir must have proper approaches for the central and side
  routes. A sheer plinth across an aisle is a circulation failure.
- **Transept and choir spaces:** large, repetitive floor expanses lack destinations at player
  height; existing fixtures hang high overhead. Add restrained, purposeful focal points and
  supported lower lighting, maintaining open circulation.
- **Tower and roof joins:** inspect the underside of the crossing, west tower deck edges and
  open doorways through tower walls. Exterior silhouettes alone cannot verify these joints.

Review the same locations after each fix, include the double-transept minster and wide colossal,
and repeat with another seed and quarter turn. An attractive screenshot does not replace
continuous-route/headroom tests; passing route tests does not replace looking at the result.

## Corrections from this review

| Player-scale fault | Correction |
| --- | --- |
| Aisles beside a plain apse end outdoors | Terminal masonry walls with glazed lancets; ambulatory entries stay open. |
| Gallery decks stop at the transepts | Supported bridges, guarded edges and broad turning aprons at their ends. |
| Raised choir plinth blocks the side aisles | Separate approaches in every aisle. |
| Stair arrives at an isolated block or under the gallery ceiling | Continuous exit deck, landing headroom and guarded stair apertures. |
| Tower floors leave a fall trench beside the walls | Decks meet the shaft, with a deliberate ladder well. |
| Tower string courses cross an intended church-facing opening | Courses respect the open face below the abutting roof. |
| Crossing shaft seals the attic route | Supported attic thresholds and clear doorways through the shaft walls. |
| Fixtures hang too high to light the walking routes | Lower aisle/nave fittings, gallery lights and supported four-candle stands. |
| Repetitive empty bays and uninterrupted pew barriers | Transverse pew breaks, framed paving and occasional reading, votive or memorial bays. |
| Choir loft stairs are narrow and exposed | Two-wide treads and walkway, supported side guards and clear arrival. |

Side uses follow the bay rhythm and leave staggered quiet bays. See
[the reference review](INTERIOR_REFERENCES.md) for the inspected builders' examples and limits.

Regression tests inspect finished volumes after decoration. `CathedralPlayabilityTest` traces
supported two-block-high routes from the entrance to every sampled aisle bay, transept end and
gallery run in six large presets, with multiple seeds and all quarter turns distributed across
the cases. Separate join, stair, choir, tower and light-propagation checks target the faults above.
The light test uses conservative block-volume propagation; it complements, rather than replaces,
Minecraft lighting review. The historical oracle test remains disabled because this generator
deliberately diverged from that port before this review.

## Validation in this workspace

The final `gradle -PcoreOnly :core:build` succeeded: 27 tests passed, one historical oracle test
was skipped, and no tests failed. This includes the existing generation/rotation/parameter-fuzz
and export tests as well as the new physical checks. Final offline views are in
`/workspace/review-final`: cathedral, minster, flamboyant (`dark`) and colossal at seed 1/turn 0,
plus minster and colossal at seed 7/turn 1. Matched baseline cameras and images are in
`/workspace/review-refined-before`.

Live Minecraft and the full Fabric build remain unverified: the current network policy denies
Fabric Maven access. No new runnable mod jar is claimed from this core-only build. Before
releasing, walk the routes in Minecraft and inspect textures, lighting and collision there.

## Revised geometry review, seed 1

Identical fixed cameras were used for `/workspace/review-refined-before` and
`/workspace/review-refined-after`. These local artifacts include cathedral, minster, dark
(Flamboyant) and colossal. The framed terminal aisle wall now closes the former plain-apse
opening; a continuous supported and guarded bridge replaces the former gallery drop. Lower
fixtures and occasional aisle-side niches introduce readable destinations without filling the
main walking routes. The choir screen is best inspected from its choir side through the axial
opening; separate views show its two-wide loft stair and guarded loft walkway.

The initial automatic cameras hid the choir screen behind the raised floor and placed the
terminal-wall camera against its masonry. Those cameras were moved back or inside the choir;
the added stair-turn, final-arrival and attic-portal views keep those micro-joints reviewable.
Always use the saved camera CSV for before/after comparison because automatic camera relocation
can otherwise obscure new furniture or an access obstruction. A camera that cannot find a
nearby supported standing cell is explicitly marked; its view alone does not prove access.

The revisions look more usable in offline geometry review, but final route tests and the actual
Minecraft walkthrough remain separate validation steps. In particular, generic full-block
clearance maps cannot certify stair collisions or the physical guard of a half-block step.
