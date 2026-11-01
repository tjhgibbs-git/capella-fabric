# Interior reference review

Reviewed 2026-11-01. The goal is a coherent building at player height: readable routes,
believable supports, purposeful side spaces and warm light. More decoration is not a goal.

## Sources actually inspected

Two public builders' projects were accessible through this environment. These are examples of
specific techniques, not a claim that either is a definitive cathedral benchmark. No code or
assets were copied into the generator.

| Source | Evidence inspected | Useful technique |
| --- | --- | --- |
| [DanielSGH's procedural church](https://github.com/DanielSGH/Procedurally-Generated-Church-in-Minecraft/blob/e42ae0a3c593d0a66c33d8b542f7ed772b99c650/main.py#L652) | `build_pillars`, `add_decorations`, `build_seats`, `add_lecterns` source | Give the interior a repeated structural rhythm, attach fittings to that rhythm, keep a deliberate central aisle, and use a lectern as a functional focal point. |
| [gto76's church: entrance interior](https://github.com/gto76/church/blob/9b2e29454dacc0f3695cc88005f2e52d6665e039/site/images/screenshot803.png) | Actual player-height image | Recessed doorway and windows, substantial piers and a ceiling whose ribs visibly meet the walls. A small palette still communicates depth. |
| [gto76's church: lower room](https://github.com/gto76/church/blob/9b2e29454dacc0f3695cc88005f2e52d6665e039/site/images/screenshot556.png) | Actual player-height image | Low wall-mounted lights flank a meaningful doorway; a restrained floor border defines the route. Lighting does not need a long suspension chain. |
| [gto76's church: ceiling](https://github.com/gto76/church/blob/9b2e29454dacc0f3695cc88005f2e52d6665e039/site/images/screenshot785.png) | Actual upward interior image | Repeat a clear rib pattern and finish the wall-to-ceiling junction. Avoid adding tiny unrelated objects to compensate for an unfinished large surface. |

Minecraft.net and PlanetMinecraft requests returned proxy policy 403 responses. Their articles,
tutorials and showcase builds were not reviewed. Forsuin's `MinecraftCathedral` repository was
also found, but contains a world save without useful published interior views; it was not used
as visual evidence. The accessible examples provide modest reference points, not a broad
survey of contemporary cathedral builders.

## Applied design rules

- Keep a hierarchy: processional nave, open crossing, raised choir, quieter side-bay spaces.
  A continuous route matters more than furniture density.
- Place furnishings within the bay rhythm. Stagger reading, votive and memorial uses, and leave
  quiet bays between them. Repeated empty space makes the occupied bays legible.
- Use the floor to mark a route or a recess without inserting collision lips into the path.
- Mix a few supported light types: candles on stands or tables near people, low aisle lights,
  and occasional suspended fittings in the high central space. Do not hang every light.
- Keep a restrained material family; concentrate contrast on thresholds, floor borders and
  focal fittings. Avoid random block noise or decorating every wall.
- Resolve joins before dressing them. A rail needs a deck, a stair needs a usable landing,
  a wall needs an opening where a route meets it, and a fixture needs a support.

## Current implementation review

`CathedralInteriors` gives side bays specific uses instead of random clutter, includes low
four-candle stands, and leaves every third bay quiet with staggered sides. `Furnishing` retains
the central altar/choir focus and adds transverse seating breaks. Those choices fit the brief.
The physical route and light tests are still needed: a nice-looking candle is not proof that
the floor is lit, and a visible opening is not proof that a two-block-high player fits.

Offline review images use approximate block shapes/colours and diagnostic ambient lighting.
They can reveal holes, disconnected surfaces and poor proportions; they do not establish the
appearance of Minecraft textures or the game's propagated lighting. Final in-game checks
remain necessary when Fabric/Mojang access is available.
