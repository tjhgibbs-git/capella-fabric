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
        add("village", "Village church", "Nave and lower chancel, a battlemented west tower and a south porch.", p -> size(p, BuildingType.PARISH, 40, 13, 10)
                .set(Params.BELFRY, Belfry.TOWER).set(Params.TOWER_CAP, Cap.FLAT).set(Params.TRACERY, Tracery.BAR));
        add("parish", "Parish church", "A broach spire on the west tower, porch, chancel and a great east window.", p -> size(p, BuildingType.PARISH, 48, 15, 12)
                .set(Params.BELFRY, Belfry.TOWER).set(Params.TOWER_CAP, Cap.SPIRE).set(Params.TRACERY, Tracery.BAR));
        add("tower", "Bell tower", "A free-standing tower with a louvred belfry and a spire.", p -> size(p, BuildingType.TOWER, 9, 9, 28)
                .set(Params.TOWER_CAP, Cap.SPIRE));
        // Cathedral-scale, block by block (see GreatTower).
        add("westtower", "Great west tower", "A tall English tower: angle buttresses, a deep portal, a louvred belfry and pinnacled battlements.",
                p -> size(p, BuildingType.GREAT_TOWER, 21, 21, 84).set(Params.TOWER_CAP, Cap.FLAT));
        add("spiretower", "Spire tower", "A great tower with a needle spire set inside its parapet, lucarnes at the spire's foot.",
                p -> size(p, BuildingType.GREAT_TOWER, 23, 23, 76).set(Params.TOWER_CAP, Cap.SPIRE));
        add("belfry", "Belfry", "A broad saddleback belfry tower in dark stone.",
                p -> size(p, BuildingType.GREAT_TOWER, 19, 19, 64).set(Params.TOWER_CAP, Cap.SADDLEBACK).set(Params.STONE, Stone.DEEPSLATE));
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
