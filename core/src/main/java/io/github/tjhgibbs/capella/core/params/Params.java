package io.github.tjhgibbs.capella.core.params;

import io.github.tjhgibbs.capella.core.params.Param.Category;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every building setting, in GUI order. Presets override the defaults. Each maps onto one field
 * of the ported {@code PlanParams} (see {@code CathedralGenerator.planOf}); the defaults are
 * Capella's {@code DEFAULT_PLAN}, less the crypt.
 */
public final class Params {
    private static final Map<String, Param<?>> BY_KEY = new LinkedHashMap<>();

    // ---- Plan ----------------------------------------------------------------------------------
    public static final Param.Int NAVE_BAYS = reg(new Param.Int("nave_bays", "Length (bays)", Category.PLAN, 4, 12, 7));
    public static final Param.Int BAY_WIDTH = reg(new Param.Int("bay_width", "Bay width", Category.PLAN, 8, 14, 10));
    public static final Param.Int NAVE_WIDTH = reg(new Param.Int("nave_width", "Nave width", Category.PLAN, 16, 28, 22));
    public static final Param.Int AISLES = reg(new Param.Int("aisles", "Aisles", Category.PLAN, 0, 2, 1));
    public static final Param.Int AISLE_WIDTH = reg(new Param.Int("aisle_width", "Aisle width", Category.PLAN, 8, 14, 10));
    public static final Param.Choice<Transepts> TRANSEPTS = reg(new Param.Choice<>("transepts", "Transepts", Category.PLAN, Transepts.class, Transepts.ONE));
    /** Where the (first) crossing stands: the share of the nave bays west of it. */
    public static final Param.Num TRANSEPT_AT = reg(new Param.Num("transept_at", "Crossing position", Category.PLAN, 0.0, 1.0, 0.01, 0.58));
    public static final Param.Int TRANSEPT_ARMS = reg(new Param.Int("transept_arms", "Transept arms (bays)", Category.PLAN, 1, 4, 2));
    public static final Param.Choice<Chevet> CHEVET = reg(new Param.Choice<>("chevet", "East end", Category.PLAN, Chevet.class, Chevet.APSE));
    public static final Param.Choice<Chapels> CHAPELS = reg(new Param.Choice<>("chapels", "Chapels", Category.PLAN, Chapels.class, Chapels.AISLE));

    // ---- Elevation -----------------------------------------------------------------------------
    public static final Param.Int STOREYS = reg(new Param.Int("storeys", "Nave storeys", Category.ELEVATION, 2, 3, 3));
    public static final Param.Choice<Covering> COVERING = reg(new Param.Choice<>("covering", "Nave ceiling", Category.ELEVATION, Covering.class, Covering.VAULT));
    public static final Param.Choice<VaultType> VAULT = reg(new Param.Choice<>("vault", "Vault", Category.ELEVATION, VaultType.class, VaultType.QUADRIPARTITE));

    // ---- Towers --------------------------------------------------------------------------------
    public static final Param.Int WEST_TOWERS = reg(new Param.Int("west_towers", "West towers", Category.TOWERS, 0, 2, 2));
    public static final Param.Int WEST_TOWER_HEIGHT = reg(new Param.Int("west_tower_height", "West tower height", Category.TOWERS, 12, 200, 66));
    public static final Param.Choice<Cap> TOWER_CAP = reg(new Param.Choice<>("tower_cap", "West tower top", Category.TOWERS, Cap.class, Cap.SPIRE));
    public static final Param.Bool CROSSING_TOWER = reg(new Param.Bool("crossing_tower", "Crossing tower", Category.TOWERS, true));
    public static final Param.Int CROSSING_HEIGHT = reg(new Param.Int("crossing_height", "Crossing tower height", Category.TOWERS, 12, 120, 28));
    public static final Param.Choice<Cap> CROSSING_CAP = reg(new Param.Choice<>("crossing_cap", "Crossing tower top", Category.TOWERS, Cap.class, Cap.SPIRE));
    public static final Param.Choice<TowerAt> CROSSING_AT = reg(new Param.Choice<>("crossing_at", "Crossing towers", Category.TOWERS, TowerAt.class, TowerAt.PRINCIPAL));

    // ---- Style ---------------------------------------------------------------------------------
    /** 0 = round (romanesque), 1 = steeply pointed. */
    public static final Param.Num ARCH_PROFILE = reg(new Param.Num("arch_profile", "Arches: round to pointed", Category.STYLE, 0.0, 1.0, 0.01, 0.7));
    public static final Param.Choice<PierForm> PIER_FORM = reg(new Param.Choice<>("pier_form", "Piers", Category.STYLE, PierForm.class, PierForm.COMPOUND));
    public static final Param.Choice<Tracery> TRACERY = reg(new Param.Choice<>("tracery", "Window tracery", Category.STYLE, Tracery.class, Tracery.BAR));

    // ---- Materials -----------------------------------------------------------------------------
    public static final Param.Choice<Stone> STONE = reg(new Param.Choice<>("stone", "Stone", Category.MATERIALS, Stone.class, Stone.LIMESTONE));

    private Params() {
    }

    private static <P extends Param<?>> P reg(P p) {
        if (BY_KEY.put(p.key(), p) != null) {
            throw new IllegalStateException("duplicate param " + p.key());
        }
        return p;
    }

    public static Param<?> byKey(String key) {
        return BY_KEY.get(key);
    }

    public static List<Param<?>> all() {
        return Collections.unmodifiableList(List.copyOf(BY_KEY.values()));
    }

    public static List<Param<?>> in(Category c) {
        return BY_KEY.values().stream().filter(p -> p.category() == c).toList();
    }

    // ---- Enumerations --------------------------------------------------------------------------

    public enum Transepts {
        NONE, ONE, TWO
    }

    public enum Chevet {
        /** A straight east wall. */
        FLAT,
        /** A semicircular apse. */
        APSE,
        /** An apse with a walkway (ambulatory) round it. */
        AMBULATORY
    }

    public enum Chapels {
        NONE, AISLE, RADIATING
    }

    public enum Covering {
        /** Stone vault over the nave. */
        VAULT,
        /** Open timber roof. */
        TIMBER
    }

    public enum VaultType {
        QUADRIPARTITE, SEXPARTITE
    }

    public enum Cap {
        SPIRE, SADDLEBACK, DOME, FLAT
    }

    public enum TowerAt {
        /** One tower, over the main crossing. */
        PRINCIPAL,
        /** A tower over every crossing. */
        EACH
    }

    public enum PierForm {
        CYLINDRICAL, COMPOUND, CLUSTERED
    }

    public enum Tracery {
        LANCET, PLATE, BAR, FLAMBOYANT
    }

    /** Capella's palettes, by id. */
    public enum Stone {
        LIMESTONE("limestone"), DEEPSLATE("deepslate"), SANDSTONE("sandstone"), BLACKSTONE("blackstone-basalt");

        public final String paletteId;

        Stone(String paletteId) {
            this.paletteId = paletteId;
        }
    }
}
