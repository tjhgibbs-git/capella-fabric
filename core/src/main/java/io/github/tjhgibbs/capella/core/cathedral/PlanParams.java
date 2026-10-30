package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.kit.TowerCap;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The parametric cathedral (Capella {@code PlanParams} in {@code scene.ts}), with the crypt's
 * parameters removed. Mutable so the GUI can edit it; {@link #copy} before handing it to a
 * generation that must not see later edits. {@link #parse} validates exactly as Capella's
 * {@code parsePlan} does, refusing out-of-range values rather than clamping.
 */
public final class PlanParams {
    public enum Chevet {
        FLAT, APSE, APSE_WITH_AMBULATORY;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public enum Chapels {
        NONE, AISLE, RADIATING;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum TowerAt {
        PRINCIPAL, EACH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum NaveCovering {
        VAULT, TIMBER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum VaultType {
        QUADRIPARTITE, SEXPARTITE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum PierForm {
        CYLINDRICAL, COMPOUND, CLUSTERED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum TraceryStyle {
        LANCET, PLATE, BAR, FLAMBOYANT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final class TranseptParams {
        /** 1-based, matching {@code Bay.index}. */
        public int atBay;
        /** Arm length each side of the body, in bays. */
        public int armBays;

        public TranseptParams(int atBay, int armBays) {
            this.atBay = atBay;
            this.armBays = armBays;
        }
    }

    public static final class CrossingTower {
        public boolean present;
        public int height;
        public TowerCap cap;
        public TowerAt at;

        public CrossingTower(boolean present, int height, TowerCap cap, TowerAt at) {
            this.present = present;
            this.height = height;
            this.cap = cap;
            this.at = at;
        }
    }

    public int naveBays = 7;
    public int bayWidth = 10;
    public int naveWidth = 22;
    /** Per side: 0, 1 or 2. */
    public int aisles = 1;
    public int aisleWidth = 10;
    /** West to east; empty is none. */
    public List<TranseptParams> transepts = new ArrayList<>(List.of(new TranseptParams(5, 2)));
    public Chevet chevet = Chevet.APSE;
    public Chapels chapels = Chapels.AISLE;
    public CrossingTower crossingTower = new CrossingTower(true, 28, TowerCap.SPIRE, TowerAt.PRINCIPAL);
    /** 0, 1 or 2. */
    public int westTowers = 2;
    public int westTowerHeight = 66;
    /** The west front's cap, both towers. */
    public TowerCap towerCap = TowerCap.SPIRE;
    public NaveCovering naveCovering = NaveCovering.VAULT;
    /** 2 or 3: how the nave wall is divided. */
    public int elevationStoreys = 3;
    public VaultType vaultType = VaultType.QUADRIPARTITE;
    /** 0 = round (romanesque), 1 = steeply pointed. */
    public double archProfile = 0.7;
    public PierForm pierForm = PierForm.COMPOUND;
    public TraceryStyle traceryStyle = TraceryStyle.BAR;
    /** Eaves above the floor, in blocks; 0 lets the proportion rule decide. Not in Capella. */
    public int naveHeight = 0;
    /** The stair turret beside the west front (and a second when there is no mural passage). Not in Capella's default. */
    public boolean stairTurret = false;

    /** Capella's {@code DEFAULT_PLAN}, less the crypt. */
    public PlanParams() {
    }

    public PlanParams copy() {
        PlanParams p = new PlanParams();
        p.naveBays = naveBays;
        p.bayWidth = bayWidth;
        p.naveWidth = naveWidth;
        p.aisles = aisles;
        p.aisleWidth = aisleWidth;
        p.transepts = new ArrayList<>();
        for (TranseptParams t : transepts) p.transepts.add(new TranseptParams(t.atBay, t.armBays));
        p.chevet = chevet;
        p.chapels = chapels;
        p.crossingTower = new CrossingTower(crossingTower.present, crossingTower.height, crossingTower.cap, crossingTower.at);
        p.westTowers = westTowers;
        p.westTowerHeight = westTowerHeight;
        p.towerCap = towerCap;
        p.naveCovering = naveCovering;
        p.elevationStoreys = elevationStoreys;
        p.vaultType = vaultType;
        p.archProfile = archProfile;
        p.pierForm = pierForm;
        p.traceryStyle = traceryStyle;
        p.naveHeight = naveHeight;
        p.stairTurret = stairTurret;
        return p;
    }

    // ---- Validation (Capella PLAN_RANGES, PLAN_CHOICES, parsePlan) ------------------------------

    public record Range(double lo, double hi, boolean integer) {
    }

    /** The numeric ranges {@link #parse} enforces, in Capella's order. Sliders should use these. */
    public static final Map<String, Range> RANGES = new LinkedHashMap<>();

    static {
        RANGES.put("naveBays", new Range(4, 12, true));
        RANGES.put("bayWidth", new Range(8, 14, true));
        RANGES.put("naveWidth", new Range(16, 28, true));
        RANGES.put("aisleWidth", new Range(8, 14, true));
        RANGES.put("westTowerHeight", new Range(0, 200, true));
        RANGES.put("archProfile", new Range(0, 1, false));
        RANGES.put("elevationStoreys", new Range(2, 3, true));
    }

    public static final Range TRANSEPT_ARM_BAYS = new Range(1, 4, true);
    /** Bays from one crossing to the next, at the least: one ordinary bay between them. */
    public static final int MIN_CROSSING_GAP = 2;

    /** How many transepts a nave of this many bays can hold. */
    public static int maxTransepts(int naveBays) {
        return Math.max(0, (int) Math.ceil(naveBays / (double) MIN_CROSSING_GAP));
    }

    /**
     * Moves the transepts back onto bays that exist, in place, after an edit; returns how many
     * had to be dropped. Push east, pull west from the east end, drop what fell off the front.
     */
    public int constrainTransepts() {
        int floor = 1;
        for (TranseptParams t : transepts) {
            t.atBay = Math.max(t.atBay, floor);
            floor = t.atBay + MIN_CROSSING_GAP;
        }
        int ceiling = naveBays;
        for (int i = transepts.size() - 1; i >= 0; i--) {
            TranseptParams t = transepts.get(i);
            t.atBay = Math.min(t.atBay, ceiling);
            ceiling = t.atBay - MIN_CROSSING_GAP;
        }
        List<TranseptParams> kept = new ArrayList<>();
        for (TranseptParams t : transepts) if (t.atBay >= 1) kept.add(t);
        int dropped = transepts.size() - kept.size();
        transepts = kept;
        return dropped;
    }

    /** Validates a plan object from a scene file; throws listing every problem. Unknown keys (crypt*) are ignored. */
    public static PlanParams parse(Map<String, Object> raw) {
        List<String> errors = new ArrayList<>();
        PlanParams p = parseInto(raw, errors, "plan");
        if (!errors.isEmpty()) throw new IllegalArgumentException("invalid scene:\n  " + String.join("\n  ", errors));
        return p;
    }

    static PlanParams parseInto(Map<String, Object> raw, List<String> errors, String path) {
        Map<String, Object> input = raw == null ? Map.of() : raw;
        PlanParams out = new PlanParams();
        for (Map.Entry<String, Range> e : RANGES.entrySet()) {
            Object v = input.get(e.getKey());
            if (v == null) continue;
            if (!(v instanceof Double d) || !Double.isFinite(d)) {
                errors.add(path + "." + e.getKey() + ": expected a number, got " + v);
                continue;
            }
            Range r = e.getValue();
            if (r.integer() && !Js.isInteger(d)) errors.add(path + "." + e.getKey() + ": expected an integer");
            if (d < r.lo() || d > r.hi()) {
                errors.add(path + "." + e.getKey() + ": " + Js.str(d) + " outside [" + Js.str(r.lo()) + ", " + Js.str(r.hi()) + "]");
            }
            switch (e.getKey()) {
                case "naveBays" -> out.naveBays = (int) (double) d;
                case "bayWidth" -> out.bayWidth = (int) (double) d;
                case "naveWidth" -> out.naveWidth = (int) (double) d;
                case "aisleWidth" -> out.aisleWidth = (int) (double) d;
                case "westTowerHeight" -> out.westTowerHeight = (int) (double) d;
                case "archProfile" -> out.archProfile = d;
                case "elevationStoreys" -> out.elevationStoreys = (int) (double) d;
                default -> throw new IllegalStateException();
            }
        }
        Object a = input.get("aisles");
        if (a != null) {
            if (!(a instanceof Double d) || (d != 0 && d != 1 && d != 2)) errors.add(path + ".aisles: expected 0, 1 or 2");
            else out.aisles = (int) (double) d;
        }
        Object wt = input.get("westTowers");
        if (wt != null) {
            if (!(wt instanceof Double d) || (d != 0 && d != 1 && d != 2)) errors.add(path + ".westTowers: expected 0, 1 or 2");
            else out.westTowers = (int) (double) d;
        }

        parseTransepts(input, out, errors, path);

        if (input.get("towerCap") != null) out.towerCap = choice(path + ".towerCap", input.get("towerCap"), TowerCap.class, errors, out.towerCap);
        out.crossingTower.cap = out.towerCap;

        if (input.get("crossingTower") instanceof Map<?, ?> c) {
            if (!(c.get("present") instanceof Boolean present)) errors.add(path + ".crossingTower.present: expected a boolean");
            else out.crossingTower.present = present;
            double height = out.crossingTower.height;
            if (c.get("height") instanceof Double h) height = h;
            if (!Js.isInteger(height) || height < 0) errors.add(path + ".crossingTower.height: expected a non-negative integer");
            out.crossingTower.height = (int) height;
            if (c.get("cap") != null) out.crossingTower.cap = choice(path + ".crossingTower.cap", c.get("cap"), TowerCap.class, errors, out.crossingTower.cap);
            if (c.get("at") != null) out.crossingTower.at = choice(path + ".crossingTower.at", c.get("at"), TowerAt.class, errors, out.crossingTower.at);
        } else if (input.containsKey("crossingTower")) {
            errors.add(path + ".crossingTower.present: expected a boolean");
        }

        if (input.get("chevet") != null) out.chevet = choice(path + ".chevet", input.get("chevet"), Chevet.class, errors, out.chevet);
        if (input.get("chapels") != null) out.chapels = choice(path + ".chapels", input.get("chapels"), Chapels.class, errors, out.chapels);
        if (input.get("naveCovering") != null) out.naveCovering = choice(path + ".naveCovering", input.get("naveCovering"), NaveCovering.class, errors, out.naveCovering);
        if (input.get("vaultType") != null) out.vaultType = choice(path + ".vaultType", input.get("vaultType"), VaultType.class, errors, out.vaultType);
        if (input.get("pierForm") != null) out.pierForm = choice(path + ".pierForm", input.get("pierForm"), PierForm.class, errors, out.pierForm);
        if (input.get("traceryStyle") != null) out.traceryStyle = choice(path + ".traceryStyle", input.get("traceryStyle"), TraceryStyle.class, errors, out.traceryStyle);

        for (int i = 0; i < out.transepts.size(); i++) {
            TranseptParams t = out.transepts.get(i);
            if (t.atBay < 1 || t.atBay > out.naveBays) {
                errors.add(path + ".transepts[" + i + "].atBay: " + t.atBay + " outside bays 1.." + out.naveBays);
            }
            if (i == 0) continue;
            TranseptParams previous = out.transepts.get(i - 1);
            if (t.atBay <= previous.atBay) {
                errors.add(path + ".transepts: bays " + previous.atBay + " then " + t.atBay + " — expected them west to east");
            } else if (t.atBay - previous.atBay < MIN_CROSSING_GAP) {
                errors.add(path + ".transepts: crossings at bays " + previous.atBay + " and " + t.atBay + " are neighbours;"
                        + " leave at least " + (MIN_CROSSING_GAP - 1) + " ordinary bay between them");
            }
        }
        if (out.transepts.size() > maxTransepts(out.naveBays)) {
            errors.add(path + ".transepts: " + out.transepts.size() + " crossings in a " + out.naveBays + "-bay nave;"
                    + " at most " + maxTransepts(out.naveBays) + " fit");
        }
        return out;
    }

    private static <E extends Enum<E>> E choice(String name, Object value, Class<E> type, List<String> errors, E fallback) {
        if (value instanceof String s) {
            for (E e : type.getEnumConstants()) {
                if (e.name().toLowerCase(Locale.ROOT).replace('_', '-').equals(s)) return e;
            }
        }
        errors.add(name + ": unexpected value " + value);
        return fallback;
    }

    private static void parseTransepts(Map<String, Object> input, PlanParams out, List<String> errors, String path) {
        if (input.get("transepts") != null && input.get("transept") != null) {
            errors.add(path + ": give transepts or transept, not both");
            return;
        }
        if (input.get("transepts") != null) {
            if (!(input.get("transepts") instanceof List<?> list)) {
                errors.add(path + ".transepts: expected an array");
                return;
            }
            List<TranseptParams> ts = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Map<?, ?> t = list.get(i) instanceof Map<?, ?> m ? m : Map.of();
                Object atBay = t.get("atBay");
                Object armBays = t.get("armBays") == null ? 2.0 : t.get("armBays");
                if (!(atBay instanceof Double ab) || !Js.isInteger(ab) || !(armBays instanceof Double arb) || !Js.isInteger(arb)) {
                    errors.add(path + ".transepts[" + i + "]: atBay and armBays must be integers");
                }
                double arms = armBays instanceof Double d ? d : Double.NaN;
                if (!(arms >= TRANSEPT_ARM_BAYS.lo() && arms <= TRANSEPT_ARM_BAYS.hi())) {
                    errors.add(path + ".transepts[" + i + "].armBays: " + Js.str(arms) + " outside [1, 4]");
                }
                ts.add(new TranseptParams(atBay instanceof Double d ? (int) (double) d : 0, (int) arms));
            }
            out.transepts = ts;
            return;
        }
        if (input.get("transept") == null) return;
        Map<?, ?> t = input.get("transept") instanceof Map<?, ?> m ? m : Map.of();
        Object present = t.get("present");
        if (!(present instanceof Boolean)) errors.add(path + ".transept.present: expected a boolean");
        TranseptParams one = out.transepts.isEmpty() ? new TranseptParams(5, 2)
                : new TranseptParams(out.transepts.get(0).atBay, out.transepts.get(0).armBays);
        double atBay = one.atBay, armBays = one.armBays;
        if (t.get("atBay") instanceof Double d) atBay = d;
        if (t.get("armBays") instanceof Double d) armBays = d;
        if (!Js.isInteger(atBay) || !Js.isInteger(armBays)) errors.add(path + ".transept: atBay and armBays must be integers");
        if (armBays < TRANSEPT_ARM_BAYS.lo() || armBays > TRANSEPT_ARM_BAYS.hi()) {
            errors.add(path + ".transept.armBays: " + Js.str(armBays) + " outside [1, 4]");
        }
        one.atBay = (int) atBay;
        one.armBays = (int) armBays;
        out.transepts = Boolean.FALSE.equals(present) ? new ArrayList<>() : new ArrayList<>(List.of(one));
    }
}
