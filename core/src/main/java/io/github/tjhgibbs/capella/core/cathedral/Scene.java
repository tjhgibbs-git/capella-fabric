package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.field.Field;
import io.github.tjhgibbs.capella.core.util.Json;

import java.util.List;
import java.util.Map;

/**
 * One cathedral on its ground: Capella's {@code Scene} reduced to a single cathedral structure.
 * {@code originX/originZ} is where the plan is set out around (the footprint's centre in X, the
 * nave axis in Z); null means the centre of the bounds, as Capella's default placement does.
 * {@code yaw} is 0, 90, 180 or 270, clockwise from above.
 */
public final class Scene {
    /** Capella's {@code NEUTRAL_RUINATION}: the document as painted. Kept for the ruin follow-up. */
    public static final double NEUTRAL_RUINATION = 0.5;

    public final double seed;
    public final String palette;
    public final double boundsX;
    public final double boundsZ;
    public final Field terrainHeight;
    public final PlanParams plan;
    /** The structure's id; "cathedral" for the first and only structure. */
    public final String id;
    public final Double originX;
    public final Double originZ;
    public final int yaw;
    public final double ruination;

    public Scene(double seed, String palette, double boundsX, double boundsZ, Field terrainHeight, PlanParams plan,
                 String id, Double originX, Double originZ, int yaw, double ruination) {
        this.seed = seed;
        this.palette = palette;
        this.boundsX = boundsX;
        this.boundsZ = boundsZ;
        this.terrainHeight = terrainHeight;
        this.plan = plan;
        this.id = id;
        this.originX = originX;
        this.originZ = originZ;
        this.yaw = yaw;
        this.ruination = ruination;
    }

    /** A cathedral centred in {@code bounds} on flat ground whose top solid block is at y = {@code floor(terrain)}. */
    public static Scene flat(PlanParams plan, double seed, String palette, double boundsX, double boundsZ, double terrain) {
        return new Scene(seed, palette, boundsX, boundsZ, Field.constant(Field.TERRAIN_HEIGHT, boundsX, boundsZ, terrain),
                plan, "cathedral", null, null, 0, NEUTRAL_RUINATION);
    }

    /** {@code placementOrigin}: the origin with the default filled in. Unrounded on purpose. */
    public double originXOrDefault() {
        return originX != null ? originX : boundsX / 2;
    }

    public double originZOrDefault() {
        return originZ != null ? originZ : boundsZ / 2;
    }

    /**
     * Reads a Capella scene file holding one cathedral: either v1's top-level {@code plan} or a
     * one-entry {@code structures} list. Fields other than {@code terrainHeight} are ignored,
     * and {@code terrainHeight} must be a {@code const} (run-length grids are not read).
     */
    public static Scene fromJson(String text) {
        Map<String, Object> input = Json.object(Json.parse(text));
        if (input == null) throw new IllegalArgumentException("scene: expected an object");
        if (!(input.get("version") instanceof Double v) || v != 1) throw new IllegalArgumentException("scene.version: expected 1");
        if (!(input.get("seed") instanceof Double seed) || !Double.isFinite(seed)) throw new IllegalArgumentException("scene.seed: expected a finite number");
        Map<String, Object> bounds = Json.object(input.get("bounds"));
        double bx = 256, bz = 256;
        if (bounds != null) {
            if (!(bounds.get("x") instanceof Double x) || !(bounds.get("z") instanceof Double z) || x <= 0 || z <= 0) {
                throw new IllegalArgumentException("scene.bounds: expected positive x and z");
            }
            bx = x;
            bz = z;
        }

        Map<String, Object> rawPlan;
        String id = "cathedral";
        Double ox = null, oz = null;
        int yaw = 0;
        List<Object> structures = Json.array(input.get("structures"));
        if (structures != null) {
            if (input.containsKey("plan")) throw new IllegalArgumentException("scene: give structures or plan, not both");
            if (structures.size() != 1) throw new IllegalArgumentException("scene.structures: this port reads exactly one cathedral");
            Map<String, Object> s = Json.object(structures.get(0));
            if (s == null) s = Map.of();
            Object type = s.get("type");
            if (type != null && !"cathedral".equals(type)) throw new IllegalArgumentException("scene.structures[0].type: only 'cathedral' is supported");
            if (s.get("id") instanceof String sid) id = sid;
            Map<String, Object> placement = Json.object(s.get("placement"));
            if (placement != null) {
                Map<String, Object> origin = Json.object(placement.get("origin"));
                if (origin != null) {
                    if (!(origin.get("x") instanceof Double x) || !(origin.get("z") instanceof Double z)) {
                        throw new IllegalArgumentException("scene.structures[0].placement.origin: expected finite x and z");
                    }
                    ox = x;
                    oz = z;
                }
                if (placement.get("yaw") != null) {
                    if (!(placement.get("yaw") instanceof Double y) || (y != 0 && y != 90 && y != 180 && y != 270)) {
                        throw new IllegalArgumentException("scene.structures[0].placement.yaw: expected one of 0|90|180|270");
                    }
                    yaw = (int) (double) y;
                }
            }
            rawPlan = Json.object(s.get("params"));
        } else {
            rawPlan = Json.object(input.get("plan"));
        }
        PlanParams plan = PlanParams.parse(rawPlan);

        Field terrain = Field.defaults(Field.TERRAIN_HEIGHT, bx, bz);
        Map<String, Object> fields = Json.object(input.get("fields"));
        if (fields != null && fields.get("terrainHeight") != null) {
            Map<String, Object> th = Json.object(fields.get("terrainHeight"));
            if (th == null || !th.containsKey("const")) throw new IllegalArgumentException("fields.terrainHeight: only { const } is supported");
            Object c = th.get("const");
            terrain = Field.constant(Field.TERRAIN_HEIGHT, bx, bz, c == null ? Double.NaN : (Double) c);
        }
        double ruination = NEUTRAL_RUINATION;
        if (input.get("ruination") instanceof Double r && Double.isFinite(r)) ruination = Math.min(1, Math.max(0, r));
        String palette = input.get("palette") instanceof String p ? p : "limestone";
        return new Scene(seed, palette, bx, bz, terrain, plan, id, ox, oz, yaw, ruination);
    }
}
