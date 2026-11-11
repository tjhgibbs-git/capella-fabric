package io.github.tjhgibbs.capella.core.params;

import io.github.tjhgibbs.capella.core.params.Params.Belfry;
import io.github.tjhgibbs.capella.core.params.Params.BuildingType;
import io.github.tjhgibbs.capella.core.params.Params.Cap;
import io.github.tjhgibbs.capella.core.params.Params.Chapels;
import io.github.tjhgibbs.capella.core.params.Params.Chevet;
import io.github.tjhgibbs.capella.core.params.Params.Covering;
import io.github.tjhgibbs.capella.core.params.Params.PierForm;
import io.github.tjhgibbs.capella.core.params.Params.Stone;
import io.github.tjhgibbs.capella.core.params.Params.TowerAt;
import io.github.tjhgibbs.capella.core.params.Params.Tracery;
import io.github.tjhgibbs.capella.core.params.Params.Transepts;
import io.github.tjhgibbs.capella.core.params.Params.VaultType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Named starting points, one good example per kind of church: Capella's demo list (its
 * {@code DEMOS}, cut to the single church), from small to large. Each only sets what differs from
 * the defaults.
 */
public final class Presets {
    private static final Map<String, Preset> BY_ID = new LinkedHashMap<>();

    public record Preset(String id, String name, String description, Consumer<BuildingParams> apply) {
        public BuildingParams params(long seed) {
            BuildingParams p = new BuildingParams().seed(seed);
            apply.accept(p);
            return p;
        }
    }

    // Length, width, height of the original Capella fixtures' bodies, so the cathedral presets keep their shape.
    private static final int[] ABBEY = {91, 44, 57}, CATHEDRAL = {104, 52, 63}, MINSTER = {130, 50, 63}, DARK = {99, 66, 78},
            BASILICA = {101, 54, 68}, GREAT = {133, 76, 68}, COLOSSAL = {223, 100, 78};

    static {
        // Small churches, built block by block (see SmallChurch).
        add("wayside", "Wayside chapel", "A tiny single-cell chapel with a bellcote and plain lancets.", p -> size(p, BuildingType.CHAPEL, 19, 9, 7)
                .set(Params.BELFRY, Belfry.BELLCOTE).set(Params.TRACERY, Tracery.LANCET));
        add("chapel", "Chapel", "A buttressed chapel with paired lights, a rose over the door and a bellcote.", p -> size(p, BuildingType.CHAPEL, 27, 13, 11)
                .set(Params.BELFRY, Belfry.BELLCOTE).set(Params.TRACERY, Tracery.BAR));
        add("village", "Village church", "Nave and lower chancel, a battlemented west tower and a side porch.", p -> size(p, BuildingType.PARISH, 40, 13, 10)
                .set(Params.BELFRY, Belfry.TOWER).set(Params.TOWER_CAP, Cap.FLAT).set(Params.TRACERY, Tracery.BAR));
        add("parish", "Parish church", "A broach spire on the west tower, porch, chancel and a great east window.", p -> size(p, BuildingType.PARISH, 48, 15, 12)
                .set(Params.BELFRY, Belfry.TOWER).set(Params.TOWER_CAP, Cap.SPIRE).set(Params.TRACERY, Tracery.BAR));
        // Abbey churches: the cathedral plan on a compact section (PlanParams.compact). Galleries,
        // wall passages and flyers at a size between the parish church and the cathedral.
        add("priory", "Norman priory", "Round arches on drum piers, a gallery and a wall passage over the aisles, flyers and a battlemented crossing tower.",
                p -> size(p, BuildingType.ABBEY, 68, 34, 38)
                        .set(Params.BAY_WIDTH, 9).set(Params.AISLES, 1).set(Params.STOREYS, 3)
                        .set(Params.TRANSEPT_AT, 0.5).set(Params.TRANSEPT_ARMS, 1).set(Params.CHEVET, Chevet.FLAT).set(Params.CHAPELS, Chapels.NONE)
                        .set(Params.CROSSING_HEIGHT, 22).set(Params.CROSSING_CAP, Cap.FLAT).set(Params.WEST_TOWERS, 0)
                        .set(Params.ARCH_PROFILE, 0.3).set(Params.PIER_FORM, PierForm.CYLINDRICAL).set(Params.TRACERY, Tracery.PLATE));
        add("collegiate", "Collegiate church", "Three storeys under a ribbed vault, twin pinnacled west towers, an apse with chapels and a spire over the crossing.",
                p -> size(p, BuildingType.ABBEY, 90, 40, 44)
                        .set(Params.BAY_WIDTH, 9).set(Params.AISLES, 1).set(Params.STOREYS, 3)
                        .set(Params.TRANSEPT_AT, 0.5).set(Params.TRANSEPT_ARMS, 1).set(Params.CHEVET, Chevet.APSE).set(Params.CHAPELS, Chapels.AISLE)
                        .set(Params.CROSSING_HEIGHT, 30).set(Params.CROSSING_CAP, Cap.SPIRE)
                        .set(Params.WEST_TOWERS, 2).set(Params.WEST_TOWER_HEIGHT, 58).set(Params.TOWER_CAP, Cap.FLAT)
                        .set(Params.ARCH_PROFILE, 0.72).set(Params.TRACERY, Tracery.BAR));
        add("abbeychurch", "Abbey church", "Galleries all round, an ambulatory with radiating chapels, clustered piers, a spired west tower and a lantern over the crossing.",
                p -> size(p, BuildingType.ABBEY, 96, 42, 46)
                        .set(Params.BAY_WIDTH, 10).set(Params.AISLES, 1).set(Params.STOREYS, 3)
                        .set(Params.TRANSEPT_AT, 0.6).set(Params.TRANSEPT_ARMS, 1).set(Params.CHEVET, Chevet.AMBULATORY).set(Params.CHAPELS, Chapels.RADIATING)
                        .set(Params.CROSSING_HEIGHT, 26).set(Params.CROSSING_CAP, Cap.FLAT)
                        .set(Params.WEST_TOWERS, 1).set(Params.WEST_TOWER_HEIGHT, 66).set(Params.TOWER_CAP, Cap.SPIRE)
                        .set(Params.ARCH_PROFILE, 0.82).set(Params.PIER_FORM, PierForm.CLUSTERED).set(Params.TRACERY, Tracery.BAR));
        add("abbey", "Romanesque", "Round arches, heavy cylindrical piers and a squat crossing tower.", p -> size(p, BuildingType.CATHEDRAL, ABBEY)
                .set(Params.BAY_WIDTH, 9)
                .set(Params.TRANSEPT_ARMS, 1).set(Params.CHEVET, Chevet.APSE).set(Params.CHAPELS, Chapels.NONE)
                .set(Params.CROSSING_HEIGHT, 18).set(Params.CROSSING_CAP, Cap.SADDLEBACK).set(Params.WEST_TOWERS, 2)
                .set(Params.WEST_TOWER_HEIGHT, 40).set(Params.TOWER_CAP, Cap.SADDLEBACK).set(Params.STOREYS, 3)
                .set(Params.ARCH_PROFILE, 0.0).set(Params.PIER_FORM, PierForm.CYLINDRICAL).set(Params.TRACERY, Tracery.PLATE));
        // fixtures/scenes/default.json
        add("cathedral", "Cathedral", "The classic: aisled nave, transept, apse, twin west spires and a crossing spire.", p -> size(p, BuildingType.CATHEDRAL, CATHEDRAL));
        // fixtures/scenes/double-transept.json
        add("minster", "Minster", "Two transepts, each crossing with its own tower.", p -> size(p, BuildingType.CATHEDRAL, MINSTER)
                .set(Params.BAY_WIDTH, 11).set(Params.TRANSEPTS, Transepts.TWO)
                .set(Params.TRANSEPT_AT, 0.25).set(Params.CROSSING_HEIGHT, 30).set(Params.CROSSING_AT, TowerAt.EACH)
                .set(Params.WEST_TOWER_HEIGHT, 70).set(Params.ARCH_PROFILE, 0.72));
        // fixtures/scenes/irregular-bays.json
        add("dark", "Flamboyant", "Wide, tight-bayed and flamboyant, with saddleback towers and sexpartite vaults.", p -> size(p, BuildingType.CATHEDRAL, DARK)
                .set(Params.BAY_WIDTH, 8)
                .set(Params.TRANSEPT_AT, 0.5).set(Params.TRANSEPT_ARMS, 1).set(Params.CHEVET, Chevet.FLAT).set(Params.CHAPELS, Chapels.NONE)
                .set(Params.CROSSING_TOWER, false).set(Params.WEST_TOWER_HEIGHT, 72).set(Params.TOWER_CAP, Cap.SADDLEBACK)
                .set(Params.VAULT, VaultType.SEXPARTITE).set(Params.ARCH_PROFILE, 0.55).set(Params.TRACERY, Tracery.FLAMBOYANT)
                .set(Params.STONE, Stone.DEEPSLATE));
        add("basilica", "Desert basilica", "Sandstone, domed towers, round-headed arches.", p -> size(p, BuildingType.CATHEDRAL, BASILICA)
                .set(Params.BAY_WIDTH, 11)
                .set(Params.TRANSEPT_ARMS, 2).set(Params.CHAPELS, Chapels.NONE).set(Params.CROSSING_HEIGHT, 26)
                .set(Params.CROSSING_CAP, Cap.DOME).set(Params.WEST_TOWERS, 2).set(Params.WEST_TOWER_HEIGHT, 48)
                .set(Params.TOWER_CAP, Cap.DOME).set(Params.ARCH_PROFILE, 0.15).set(Params.PIER_FORM, PierForm.CYLINDRICAL)
                .set(Params.TRACERY, Tracery.PLATE).set(Params.STONE, Stone.SANDSTONE));
        // fixtures/scenes/pristine.json
        add("great", "Great church", "Double aisles, an ambulatory with radiating chapels, and clustered piers.", p -> size(p, BuildingType.CATHEDRAL, GREAT)
                .set(Params.BAY_WIDTH, 11).set(Params.AISLES, 2)
                .set(Params.TRANSEPT_AT, 0.625).set(Params.CHEVET, Chevet.AMBULATORY)
                .set(Params.CHAPELS, Chapels.RADIATING).set(Params.CROSSING_HEIGHT, 30).set(Params.WEST_TOWER_HEIGHT, 76)
                .set(Params.ARCH_PROFILE, 0.75).set(Params.PIER_FORM, PierForm.CLUSTERED));
        // fixtures/scenes/max-plan.json
        add("colossal", "Colossal", "Every parameter at its maximum. Big: expect a few seconds to generate.", p -> size(p, BuildingType.CATHEDRAL, COLOSSAL)
                .set(Params.BAY_WIDTH, 14).set(Params.AISLES, 2)
                .set(Params.TRANSEPT_AT, 0.583).set(Params.TRANSEPT_ARMS, 4)
                .set(Params.CHEVET, Chevet.AMBULATORY).set(Params.CHAPELS, Chapels.RADIATING).set(Params.CROSSING_HEIGHT, 48)
                .set(Params.CROSSING_CAP, Cap.DOME).set(Params.WEST_TOWER_HEIGHT, 96).set(Params.TOWER_CAP, Cap.DOME)
                .set(Params.VAULT, VaultType.SEXPARTITE).set(Params.ARCH_PROFILE, 1.0).set(Params.PIER_FORM, PierForm.CLUSTERED)
                .set(Params.TRACERY, Tracery.FLAMBOYANT).set(Params.STONE, Stone.BLACKSTONE));
        // Towers on their own: the parish bell tower (SmallChurch), the great towers (GreatTower), then the
        // castle towers with fighting floors (Fortification).
        add("tower", "Bell tower", "A free-standing tower with a louvred belfry and a spire.", p -> size(p, BuildingType.TOWER, 9, 9, 28)
                .set(Params.TOWER_CAP, Cap.SPIRE));
        add("westtower", "Great west tower", "A tall English tower: angle buttresses, a deep portal, a louvred belfry and pinnacled battlements.",
                p -> size(p, BuildingType.GREAT_TOWER, 21, 21, 84).set(Params.TOWER_CAP, Cap.FLAT));
        add("spiretower", "Spire tower", "A great tower with a needle spire set inside its parapet, lucarnes at the spire's foot.",
                p -> size(p, BuildingType.GREAT_TOWER, 23, 23, 76).set(Params.TOWER_CAP, Cap.SPIRE));
        add("belfry", "Belfry", "A broad saddleback belfry tower in dark stone.",
                p -> size(p, BuildingType.GREAT_TOWER, 19, 19, 64).set(Params.TOWER_CAP, Cap.SADDLEBACK).set(Params.STONE, Stone.DEEPSLATE));
        add("squarekeep", "Square keep", "A broad medieval keep with four octagonal corner turrets, framed arrow loops and timber floors.",
                p -> size(p, BuildingType.SQUARE_KEEP, 27, 27, 28).set(Params.FLOOR_HEIGHT, 6));
        add("roundwatchtower", "Round watchtower", "A round watchtower with a battered base, projecting stage strings and a corbelled fighting crown.",
                p -> size(p, BuildingType.ROUND_WATCHTOWER, 21, 21, 34).set(Params.FLOOR_HEIGHT, 8));
        add("gothicturret", "Gothic turret", "A Gothic turret with stepped buttresses, leaded lancets, pinnacles, dormers and a steep copper roof.",
                p -> size(p, BuildingType.GOTHIC_TURRET, 19, 19, 38).set(Params.FLOOR_HEIGHT, 9).set(Params.STONE, Stone.DEEPSLATE));
        add("sentrytower", "Sentry tower", "A compact stone tower with chamfered corners, arrow loops, a guarded fighting deck and ladder access.",
                p -> size(p, BuildingType.SENTRY_TOWER, 9, 9, 16));
        add("timberwatchtower", "Timber watchtower", "A compact timber-framed lookout on a stone base, with an open roofed watch gallery and ladder access.",
                p -> size(p, BuildingType.TIMBER_WATCHTOWER, 9, 9, 12));
        add("barracks", "Barracks", "A stone and timber garrison hall with paired bunks, lockers, roof trusses and a clear central aisle.",
                p -> size(p, BuildingType.BARRACKS, 29, 13, 7));
        add("stables", "Stables", "A gabled stable range with open stall fronts, hay, water troughs and a broad central passage.",
                p -> size(p, BuildingType.STABLES, 25, 13, 6));
        // Walls: modular castle walls with open ends, for joining to towers (Fortification).
        add("curtainwall", "Curtain wall", "A modular battlemented castle wall with buttresses, an optional guarded wall walk and open ends for joining towers.",
                p -> size(p, BuildingType.CURTAIN_WALL, 40, 5, 12));
        add("highwall", "High curtain wall", "A tall, thick defensive curtain with a broad fighting platform.",
                p -> size(p, BuildingType.CURTAIN_WALL, 64, 9, 24));
        add("arcadedwall", "Arcaded wall", "A Gothic castle wall carried on pointed arches with a guarded wall walk above.",
                p -> size(p, BuildingType.ARCADED_WALL, 40, 5, 12));
        add("gatehouse", "Square gatehouse", "A curtain wall with square flanking towers, a pointed gateway and a continuous wall walk.",
                p -> size(p, BuildingType.CURTAIN_WALL, 40, 5, 12).set(Params.GATEHOUSE, true));
        add("roundgatehouse", "Round gatehouse", "Round flanking towers, a round arch and a raised portcullis integrated into a defensive wall.",
                p -> size(p, BuildingType.CURTAIN_WALL, 48, 7, 16).set(Params.GATEHOUSE, true)
                        .set(Params.GATE_TOWERS, Params.GateTower.ROUND).set(Params.GATE_ARCH, Params.GateArch.ROUND)
                        .set(Params.GATE_STYLE, Params.GateStyle.RAISED_PORTCULLIS));
        add("roofedgatehouse", "Roofed gatehouse", "A compact enclosure entrance with roofed towers, a flat lintel and a closed timber gate.",
                p -> size(p, BuildingType.PLAIN_WALL, 32, 5, 8).set(Params.GATEHOUSE, true)
                        .set(Params.GATE_TOWERS, Params.GateTower.ROOFED).set(Params.GATE_ARCH, Params.GateArch.FLAT)
                        .set(Params.GATE_STYLE, Params.GateStyle.TIMBER));
        add("plainwall", "Low enclosure wall", "A low, solid castle boundary with stone coping; enable Wall walk to add a guarded deck.",
                p -> size(p, BuildingType.PLAIN_WALL, 24, 5, 6).set(Params.WALKABLE, false));
    }

    private static BuildingParams size(BuildingParams p, BuildingType type, int[] lwh) {
        return size(p, type, lwh[0], lwh[1], lwh[2]);

    }

    private static BuildingParams size(BuildingParams p, BuildingType type, int length, int width, int height) {
        return p.set(Params.TYPE, type).set(Params.LENGTH, length).set(Params.WIDTH, width).set(Params.HEIGHT, height);
    }

    private Presets() {
    }

    private static void add(String id, String name, String description, Consumer<BuildingParams> apply) {
        BY_ID.put(id, new Preset(id, name, description, apply));
    }

    public static Preset get(String id) {
        Preset p = BY_ID.get(id);
        return p != null ? p : BY_ID.get("parish");
    }

    public static boolean exists(String id) {
        return BY_ID.containsKey(id);
    }

    public static List<Preset> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }
}
