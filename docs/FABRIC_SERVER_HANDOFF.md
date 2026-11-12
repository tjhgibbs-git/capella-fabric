# TEMPORARY HANDOFF — Fabric dedicated-server verification (Capella)

> **DO NOT MERGE THIS FILE.** This document is a temporary instruction sheet for a fresh local Codex session on the user's PC. It is deliberately committed to `feature/fabric-dedicated-server-support` to enable handoff. **Delete `docs/FABRIC_SERVER_HANDOFF.md` from the feature branch and commit that deletion BEFORE the PR is marked ready or merged into `26.3`.** Do not delete older `docs/HANDOFF.md` or other project handoffs. Keep the actual code, production README documentation, tests and bug fixes.
>
> Created 2026-11-12. The cloud session reviewed source and committed implementation changes, but **did not compile the Fabric module, launch a dedicated server, connect an in-game client, or validate real remote packet negotiation**. Do not interpret this PR as tested.

## Session mandate

You are a fresh Codex session working locally in `tjhgibbs-git/capella-fabric`. Work only on `feature/fabric-dedicated-server-support`, PR #6 (base `26.3`). Verify and finish **Fabric dedicated-server + client-only multiplayer compatibility** for Minecraft **26.3**. Do NOT add Paper/Bukkit/Spigot, vanilla-server block editing, unrelated generator changes, or a new server-only JAR unless an actual dedicated-server defect requires it. Prefer the existing single Fabric JAR installed on both sides of a Fabric server; core is included in the JAR. Keep client-only preview/exports working on vanilla servers.

Before modifying anything, `git status`, `git branch --show-current`, `git log -5 --oneline`, read this file, the PR diff, README Fabric multiplayer section, `fabric/build.gradle`, `fabric/src/main/resources/fabric.mod.json`, and relevant existing handoffs/tests. Check for branch updates/other work and avoid overwriting unrelated changes.

## What was changed in this PR

- Client `fabric/src/client/java/io/github/tjhgibbs/capella/client/GeneratorScreen.java`: `canPlaceOnServer(Minecraft)` requires a creative player, a non-null play connection, and `ClientPlayNetworking.canSend(PlaceBuildingPayload.TYPE)`. The `Build here` button and Y-offset controls follow this availability. Packet-send entrypoints are guarded.
- A localized tooltip explains that direct placement needs the mod on the Fabric server; user can still generate and save schematics locally.
- `README.md` now describes Fabric multiplayer usage, separate local GUI saves vs server-side command exports, and a smoke-test checklist.
- Existing `CapellaMod` common/server initializer registers the serverbound placement payload receiver, server tick placement, lifecycle cleanup and commands. Existing client entrypoint uses the `H` key; dedicated servers must not load client-only classes.

**Design intent:** Unmodded vanilla server / Fabric server without this mod: previews, presets, settings, variations, local Save all work; `Build here` and placement-only Y controls are greyed out. Fabric server **with** this mod: creative users can place and undo. Survival players cannot place. Do not disable local exports merely because server placement is missing.

## Local prerequisites and build

- Working desktop with JDK **25**, Gradle wrapper, Minecraft 26.3 client, Fabric Loader >= 0.20.1 and Fabric API matching `gradle.properties` (currently `0.165.0+26.3`). Axiom is **optional** for export-paste checks, not required for direct placement. Internet/Maven/Fabric/Mojang access may be needed on first build.
- Build from repo root: `./gradlew clean build` (Windows PowerShell: `.\gradlew.bat clean build`). This must compile `core`, `fabric`, and exercise existing tests. Run any configured GameTest task after inspecting `./gradlew tasks --all`; do not guess a nonexistent task. Capture full failure logs, fix genuine code/test issues on the feature branch, rerun.
- Inspect `fabric/build/libs/` for the **remapped distributable** `capella-1.4.0.jar` (actual filename/version may vary); **do not install `*-sources.jar`, `*-dev.jar`, or an unmapped dev JAR**. Confirm the produced JAR contains `fabric.mod.json`, expected common classes, client entrypoint metadata and the bundled `core` dependency. Use the exact same build on client and server. Avoid treating successful unit tests as proof of network compatibility.

## Run a real multiplayer test matrix

Use separate **fresh test worlds** (back them up), two dedicated servers listening on different ports, and a standalone client (not only the integrated singleplayer server). Server A: Fabric 26.3 + Fabric API + Capella JAR. Server B: Fabric 26.3 + Fabric API, **without** Capella. Also test a vanilla 26.3 server with the same modded client (or record a precise blocker if unavailable). Client: Fabric 26.3 + Fabric API + Capella JAR. Confirm actual jar versions and loader logs. For multiplayer place-tests, set player to creative, use a safe writable test area and check any spawn-protection/claims restrictions. Test survival separately.

| Environment | Expected client behaviour | Verification |
| --- | --- | --- |
| Singleplayer integrated server | GUI, preview, Save, direct placement, Y offset and undo work | Compare against previous behaviour |
| Dedicated Fabric + matching mod | GUI and Save work; **Build here enabled in creative**; packet reaches server; placement finishes; `/capella undo` restores prior blocks | Check both client and server logs; test second client/player if possible |
| Dedicated Fabric, mod absent | GUI, seeds, previews, variations, Save work; **Build here + Y controls greyed** with clear tooltip; no malformed/unknown-payload disconnect | Try clicking/keyboard activation; verify zero unintended packet sends |
| Vanilla 26.3 server | Same graceful client-only behaviour as above | Confirm no disconnect or crash |
| Dedicated Fabric + mod, player in survival | GUI/Save continue, placement and Y controls greyed out | Confirm server still rejects any forged/unsanctioned placement request |

**Mandatory server-runtime check:** Actually start `runServer` (if available) and/or the **standalone dedicated Fabric server** with the built JAR. Catch `NoClassDefFoundError` / `ClassNotFoundException` due to client classes, runtime mixin failures, missing embedded core, Fabric API dependency errors, and unexpected resource loading on a headless server.

**Mandatory networking check:** `ClientPlayNetworking.canSend(PlaceBuildingPayload.TYPE)` must return **true on the remote modded server** and **false on both server types missing the mod**. Validate this through behaviour and, if ambiguous, minimal temporary logging / a targeted test. Pay attention to timing when opening the GUI immediately after join and to reconnecting/switching servers without restarting the game. If Fabric's 26.3 payload negotiation does not advertise this serverbound channel, investigate Fabric API networking registration/advertisement and add correct handshake support rather than assuming canSend is working. Confirm that the guard does not accidentally disable normal singleplayer.

**GUI detail:** Confirm initial `Build here` and Y-stepper disabled states, the tooltip, and tick-driven updates. Check that disabling placement does NOT disable Save, parameter controls, or variations. When reconnecting to a server with hooks enabled, the button must enable without a restart. Re-check immediately before sending; no wrong-server payload should be sent.

**Placement check:** Exercise `/capella presets`, one `/capella place chapel` operation, undo, GUI placement, Y-offset and repeated placing/undo. Check server log for generation errors, off-thread access, oversized packet/parameter errors, placement queue overlap and block changes. Check west-door placement origin, facing/orientation, Y offset (including sunken interiors), undo, a larger building, and the big-build confirmation/save-alternative dialog. Watch for chunk sections that appear incomplete until a relog (see existing docs/HANDOFF.md). Confirm placement is synchronized to other nearby clients and survives reconnect/chunk reload.

**Save/export check:** From GUI on the remote server, create .bp, .schem, 14erEdit, .nbt exports; verify they appear in the **client** game directory (e.g. Axiom blueprint folder), not on server. If Axiom matching this Minecraft version is available, check opening a generated `.bp` and whether server policy permits pasting; **Axiom multiplayer paste is separate from this mod's own placement**. Execute `/capella export chapel sponge` on the dedicated modded server, verify export appears in **server** filesystem and does not magically download to client. Compare file paths and report any permission/config surprises.

## Potential follow-up code fixes (only as evidence requires)

1. Fix compile/runtime API incompatibilities around `ClientPlayNetworking.canSend` and the PlaceBuildingPayload registration semantics; ensure the packet is genuinely advertised by a dedicated server. A client-side `fabric.mod.json` declaration alone is NOT an adequate server capability test.
2. Fix any dedicated-server classloading or bundled-core problems. Keep `client` source-set classes isolated from server runtime.
3. Check dynamic GUI state/tooltip on world change and any race where the click handler sees a stale connection. Do not weaken server-side creative/distance/params validation.
4. Check server work queue/undo memory with large inputs. Record any severe performance, permissions or protection plugin/claim implications; keep significant new permission frameworks or configurable quotas out of scope unless needed for safety.
5. Prefer concise, automated regression tests for capability-dependent behaviour if feasible. Add a reproducible local harness or documented scripted test for start/stop/connect where practical. Preserve existing tests.
6. Fix README if actual runtime behaviour or installation requirements differ (especially exact Gradle tasks, Java version, JAR path, and placement permissions).

## Deliverables and exit criteria

- Build and tests pass locally; real **headless dedicated Fabric server** launches with the published-style JAR.
- Live client to server A: direct placement + undo verified, no crash/disconnect, creative restriction holds.
- Live client to server B and vanilla: **placement/Y controls greyed out; Save + previews still work; no crash/disconnect**.
- Client local Save vs server command export storage verified.
- Important logs/test commands, versions, screenshots and outstanding issues summarised in PR comment or production-facing test documentation. Clearly distinguish PASS, FAIL, NOT RUN, and ENVIRONMENT BLOCKED. Commit functional fixes and regression tests in small readable commits.
- **Before declaring the PR ready for merge, run `git rm docs/FABRIC_SERVER_HANDOFF.md`, commit its deletion on this feature branch, and confirm `git diff origin/26.3...HEAD --name-only` does NOT contain the temporary handoff file.** This is not a request to delete the older existing handoff documents. Do not merge automatically without user instruction.

## Cross-repository coordination

The sibling repository `tjhgibbs-git/tree-generator-fabric` (PR #3) has an equivalent temporary handoff and patch. Prefer analogous fixes in both so a user installing both mods on the same client and Fabric server gets consistent controls. Test **both installed together** (G and H keys, each payload, each undo/command namespace, client-only fallback for either missing mod) if the local environment allows. Do not accidentally include Paper support.
