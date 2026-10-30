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

    // ---- Size ----------------------------------------------------------------------------------
    public static final Param.Choice<BuildingType> TYPE = reg(new Param.Choice<>("type", "Building", Category.SIZE, BuildingType.class, BuildingType.CATHEDRAL));
    /** Blocks, west face to east face. The legal range depends on the type: see {@link #range}. */
    public static final Param.Int LENGTH = reg(new Param.Int("length", "Length", Category.SIZE, 5, 240, 104));
    /** Blocks: the nave's outer width (a tower's side). */
    public static final Param.Int WIDTH = reg(new Param.Int("width", "Width", Category.SIZE, 5, 100, 52));
    /** Blocks: the nave's eaves above the floor (a tower's height). */
    public static final Param.Int HEIGHT = reg(new Param.Int("height", "Height", Category.SIZE, 5, 80, 63));

    // ---- Plan ----------------------------------------------------------------------------------
    public static final Param.Choice<Belfry> BELFRY = reg(new Param.Choice<>("belfry", "Bell", Category.PLAN, Belfry.class, Belfry.BELLCOTE));
    public static final Param.Bool PORCH = reg(new Param.Bool("porch", "Porch", Category.PLAN, true));
    public static final Param.Int BAY_WIDTH = reg(new Param.Int("bay_width", "Bay width", Category.PLAN, 8, 14, 10));
    public static final Param.Int AISLES = reg(new Param.Int("aisles", "Aisles", Category.PLAN, 0, 2, 1));
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

    // ---- Which settings apply, and their ranges, by building type ------------------------------

    /** The size sliders' range for a type: {min, max}. */
    public static int[] range(Param.Int p, BuildingType type) {
        if (p != LENGTH && p != WIDTH && p != HEIGHT) return new int[]{p.min(), p.max()};
        int i = p == LENGTH ? 0 : p == WIDTH ? 1 : 2;
        return switch (type) {
            case CHAPEL -> new int[][]{{13, 45}, {9, 19}, {5, 14}}[i];
            case PARISH -> new int[][]{{28, 80}, {11, 25}, {6, 16}}[i];
            case TOWER -> new int[][]{{5, 13}, {5, 13}, {12, 48}}[i];
            case CATHEDRAL -> new int[][]{{70, 240}, {40, 100}, {24, 80}}[i];
        };
    }

    /** A sensible starting size for a type: length, width, height. */
    public static int[] defaultSize(BuildingType type) {
        return switch (type) {
            case CHAPEL -> new int[]{25, 11, 9};
            case PARISH -> new int[]{46, 15, 12};
            case TOWER -> new int[]{9, 9, 30};
            case CATHEDRAL -> new int[]{104, 52, 63};
        };
    }

    /** Whether a setting means anything for this type (the GUI hides the rest). */
    public static boolean applies(Param<?> p, BuildingType type) {
        boolean small = type != BuildingType.CATHEDRAL;
        if (p == TYPE || p == WIDTH || p == HEIGHT || p == STONE) return true;
        if (p == LENGTH) return type != BuildingType.TOWER;
        if (p == BELFRY) return type == BuildingType.CHAPEL || type == BuildingType.PARISH;
        if (p == PORCH) return type == BuildingType.PARISH;
        if (p == TOWER_CAP) return type != BuildingType.CHAPEL;
        if (p == TRACERY) return type != BuildingType.TOWER;
        return !small;
    }

    /** The handful of settings the GUI shows before "All settings", by type. */
    public static List<Param<?>> basic(BuildingType type) {
        return switch (type) {
            case CHAPEL -> List.of(TYPE, LENGTH, WIDTH, HEIGHT, BELFRY, TRACERY, STONE);
            case PARISH -> List.of(TYPE, LENGTH, WIDTH, HEIGHT, BELFRY, TOWER_CAP, STONE);
            case TOWER -> List.of(TYPE, WIDTH, HEIGHT, TOWER_CAP, STONE);
            case CATHEDRAL -> List.of(TYPE, LENGTH, WIDTH, HEIGHT, AISLES, TRANSEPTS, STONE);
        };
    }

    /** Switches type, starting the sizes from that type's defaults. */
    public static void changeType(BuildingParams bp, BuildingType type) {
        int[] d = defaultSize(type);
        bp.set(TYPE, type).set(LENGTH, d[0]).set(WIDTH, d[1]).set(HEIGHT, d[2]);
    }

    /** Puts the size settings back inside the type's range. */
    public static void clampSizes(BuildingParams bp) {
        BuildingType t = bp.get(TYPE);
        for (Param.Int p : List.of(LENGTH, WIDTH, HEIGHT)) {
            int[] r = range(p, t);
            bp.set(p, Math.max(r[0], Math.min(r[1], bp.integer(p))));
        }
    }

    // ---- Enumerations --------------------------------------------------------------------------

    public enum BuildingType {
        CHAPEL, PARISH, TOWER, CATHEDRAL
    }

    public enum Belfry {
        NONE, BELLCOTE, TOWER
    }

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
