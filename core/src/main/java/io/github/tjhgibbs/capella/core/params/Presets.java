package io.github.tjhgibbs.capella.core.params;

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

    static {
        // fixtures/scenes/minimal.json
        add("chapel", "Chapel", "A single vaulted cell: four bays, no aisles, no towers.", p -> p
                .set(Params.NAVE_BAYS, 4).set(Params.NAVE_WIDTH, 20).set(Params.AISLES, 0).set(Params.TRANSEPTS, Transepts.NONE)
                .set(Params.CHEVET, Chevet.FLAT).set(Params.CHAPELS, Chapels.NONE).set(Params.CROSSING_TOWER, false)
                .set(Params.WEST_TOWERS, 0).set(Params.PIER_FORM, PierForm.CYLINDRICAL).set(Params.TRACERY, Tracery.LANCET));
        // fixtures/scenes/parish.json
        add("parish", "Parish church", "Timber-roofed nave and aisles, one stout west tower, a flat east end.", p -> p
                .set(Params.NAVE_BAYS, 6).set(Params.BAY_WIDTH, 11).set(Params.NAVE_WIDTH, 18).set(Params.AISLE_WIDTH, 8)
                .set(Params.TRANSEPTS, Transepts.NONE).set(Params.CHEVET, Chevet.FLAT).set(Params.CHAPELS, Chapels.NONE)
                .set(Params.CROSSING_TOWER, false).set(Params.WEST_TOWERS, 1).set(Params.WEST_TOWER_HEIGHT, 44)
                .set(Params.TOWER_CAP, Cap.FLAT).set(Params.COVERING, Covering.TIMBER).set(Params.STOREYS, 2)
                .set(Params.ARCH_PROFILE, 0.45).set(Params.PIER_FORM, PierForm.CYLINDRICAL).set(Params.TRACERY, Tracery.PLATE));
        add("abbey", "Romanesque abbey", "Round arches, heavy cylindrical piers and a squat crossing tower.", p -> p
                .set(Params.NAVE_BAYS, 7).set(Params.BAY_WIDTH, 9).set(Params.NAVE_WIDTH, 18).set(Params.AISLE_WIDTH, 8)
                .set(Params.TRANSEPT_ARMS, 1).set(Params.CHEVET, Chevet.APSE).set(Params.CHAPELS, Chapels.NONE)
                .set(Params.CROSSING_HEIGHT, 18).set(Params.CROSSING_CAP, Cap.SADDLEBACK).set(Params.WEST_TOWERS, 2)
                .set(Params.WEST_TOWER_HEIGHT, 40).set(Params.TOWER_CAP, Cap.SADDLEBACK).set(Params.STOREYS, 3)
                .set(Params.ARCH_PROFILE, 0.0).set(Params.PIER_FORM, PierForm.CYLINDRICAL).set(Params.TRACERY, Tracery.PLATE));
        // fixtures/scenes/default.json
        add("cathedral", "Cathedral", "The classic: aisled nave, transept, apse, twin west spires and a crossing spire.", p -> {
        });
        // fixtures/scenes/double-transept.json
        add("minster", "Minster", "Two transepts, each crossing with its own tower.", p -> p
                .set(Params.NAVE_BAYS, 8).set(Params.BAY_WIDTH, 11).set(Params.AISLE_WIDTH, 9).set(Params.TRANSEPTS, Transepts.TWO)
                .set(Params.TRANSEPT_AT, 0.25).set(Params.CROSSING_HEIGHT, 30).set(Params.CROSSING_AT, TowerAt.EACH)
                .set(Params.WEST_TOWER_HEIGHT, 70).set(Params.ARCH_PROFILE, 0.72));
        // fixtures/scenes/irregular-bays.json
        add("dark", "Deepslate cathedral", "Wide, tight-bayed and flamboyant, with saddleback towers and sexpartite vaults.", p -> p
                .set(Params.NAVE_BAYS, 8).set(Params.BAY_WIDTH, 8).set(Params.NAVE_WIDTH, 28).set(Params.AISLE_WIDTH, 14)
                .set(Params.TRANSEPT_AT, 0.5).set(Params.TRANSEPT_ARMS, 1).set(Params.CHEVET, Chevet.FLAT).set(Params.CHAPELS, Chapels.NONE)
                .set(Params.CROSSING_TOWER, false).set(Params.WEST_TOWER_HEIGHT, 72).set(Params.TOWER_CAP, Cap.SADDLEBACK)
                .set(Params.VAULT, VaultType.SEXPARTITE).set(Params.ARCH_PROFILE, 0.55).set(Params.TRACERY, Tracery.FLAMBOYANT)
                .set(Params.STONE, Stone.DEEPSLATE));
        add("basilica", "Desert basilica", "Sandstone, domed towers, round-headed arches.", p -> p
                .set(Params.NAVE_BAYS, 6).set(Params.BAY_WIDTH, 11).set(Params.NAVE_WIDTH, 24).set(Params.AISLE_WIDTH, 10)
                .set(Params.TRANSEPT_ARMS, 2).set(Params.CHAPELS, Chapels.NONE).set(Params.CROSSING_HEIGHT, 26)
                .set(Params.CROSSING_CAP, Cap.DOME).set(Params.WEST_TOWERS, 2).set(Params.WEST_TOWER_HEIGHT, 48)
                .set(Params.TOWER_CAP, Cap.DOME).set(Params.ARCH_PROFILE, 0.15).set(Params.PIER_FORM, PierForm.CYLINDRICAL)
                .set(Params.TRACERY, Tracery.PLATE).set(Params.STONE, Stone.SANDSTONE));
        // fixtures/scenes/pristine.json
        add("great", "Great cathedral", "Double aisles, an ambulatory with radiating chapels, and clustered piers.", p -> p
                .set(Params.NAVE_BAYS, 8).set(Params.BAY_WIDTH, 11).set(Params.NAVE_WIDTH, 24).set(Params.AISLES, 2)
                .set(Params.AISLE_WIDTH, 9).set(Params.TRANSEPT_AT, 0.625).set(Params.CHEVET, Chevet.AMBULATORY)
                .set(Params.CHAPELS, Chapels.RADIATING).set(Params.CROSSING_HEIGHT, 30).set(Params.WEST_TOWER_HEIGHT, 76)
                .set(Params.ARCH_PROFILE, 0.75).set(Params.PIER_FORM, PierForm.CLUSTERED));
        // fixtures/scenes/max-plan.json
        add("colossal", "Colossal", "Every parameter at its maximum. Big: expect a few seconds to generate.", p -> p
                .set(Params.NAVE_BAYS, 12).set(Params.BAY_WIDTH, 14).set(Params.NAVE_WIDTH, 28).set(Params.AISLES, 2)
                .set(Params.AISLE_WIDTH, 14).set(Params.TRANSEPT_AT, 0.583).set(Params.TRANSEPT_ARMS, 4)
                .set(Params.CHEVET, Chevet.AMBULATORY).set(Params.CHAPELS, Chapels.RADIATING).set(Params.CROSSING_HEIGHT, 48)
                .set(Params.CROSSING_CAP, Cap.DOME).set(Params.WEST_TOWER_HEIGHT, 96).set(Params.TOWER_CAP, Cap.DOME)
                .set(Params.VAULT, VaultType.SEXPARTITE).set(Params.ARCH_PROFILE, 1.0).set(Params.PIER_FORM, PierForm.CLUSTERED)
                .set(Params.TRACERY, Tracery.FLAMBOYANT).set(Params.STONE, Stone.BLACKSTONE));
    }

    private Presets() {
    }

    private static void add(String id, String name, String description, Consumer<BuildingParams> apply) {
        BY_ID.put(id, new Preset(id, name, description, apply));
    }

    public static Preset get(String id) {
        Preset p = BY_ID.get(id);
        return p != null ? p : BY_ID.get("cathedral");
    }

    public static boolean exists(String id) {
        return BY_ID.containsKey(id);
    }

    public static List<Preset> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }
}
