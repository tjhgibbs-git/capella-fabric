package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Axis;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Dir;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntToDoubleFunction;

/**
 * Towers: stages, corner buttresses, belfry openings and caps (Capella {@code kit/tower.ts}).
 *
 * <p>A tower is judged on silhouette. What turns a box into a tower is, in order: diminution
 * (corner buttresses stepping back each stage), a cap ({@link Cap}), and horizontal division
 * (string courses and a belfry). The stage count comes from the height.
 */
public final class Tower {
    /** Blocks of tower per stage, near enough: a 60-block tower gets four stages. */
    private static final double STAGE_HEIGHT = 16;
    private static final double MIN_STAGES = 2;
    private static final double MAX_STAGES = 5;

    /** Below this a tower is not built at all. */
    public static final double MIN_TOWER_HEIGHT = 12;

    private Tower() {
    }

    /** {@code open}: the face left open where the tower stands over a bay of the building. */
    public enum Open {
        EAST, NONE
    }

    public static final class TowerSpec {
        public final String id;
        public final double x0, x1, z0, z1;
        public final double baseY, topY;
        public final double thickness;
        /** Faces to pierce with belfry openings and lancets (horizontals only). */
        public final List<Dir> pierced;
        /** How the tower is finished; {@link Cap} builds it. */
        public final TowerCap cap;
        /** Rise per block of run on the spire (saddleback derives from it). Zero for no cap on those two. */
        public final double spirePitch;
        /** Corner buttresses project this far. Zero for none. */
        public final double buttressProjection;
        /** True when the tower stands on the ground and claims ground under it. */
        public final boolean grounded;
        /** Face left open; nullable (= closed). */
        public Open open;
        /** Where the fabric abutting the open face stops, so the shaft closes itself above it; nullable. */
        public Double openTopY;
        /** Where a vice inside the shaft has its bottom tread, when not the shaft's foot; nullable. */
        public Double viceFootY;
        /** Interior floors and a ladder under a non-flat cap. */
        public boolean interiorFloors;
        /** Attic doors on both ends of a crossing shaft, aligned with the nave ridge. */
        public boolean atticAccess;
        public List<String> supports;
        /** Footprints a corner buttress must not land in (the stair turret's). */
        public List<Rect> keepOut;

        public TowerSpec(String id, double x0, double x1, double z0, double z1, double baseY, double topY,
                         double thickness, List<Dir> pierced, TowerCap cap, double spirePitch,
                         double buttressProjection, boolean grounded) {
            this.id = id;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.baseY = baseY;
            this.topY = topY;
            this.thickness = thickness;
            this.pierced = pierced;
            this.cap = cap;
            this.spirePitch = spirePitch;
            this.buttressProjection = buttressProjection;
            this.grounded = grounded;
        }

        public TowerSpec open(Open o) {
            open = o;
            return this;
        }

        public TowerSpec openTopY(Double y) {
            openTopY = y;
            return this;
        }

        public TowerSpec viceFootY(Double y) {
            viceFootY = y;
            return this;
        }

        public TowerSpec interiorFloors(boolean v) {
            interiorFloors = v;
            return this;
        }

        public TowerSpec atticAccess(boolean v) {
            atticAccess = v;
            return this;
        }

        public TowerSpec supports(List<String> s) {
            supports = s;
            return this;
        }

        public TowerSpec keepOut(List<Rect> k) {
            keepOut = k;
            return this;
        }
    }

    public static double stageCount(double height) {
        return Math.max(MIN_STAGES, Math.min(MAX_STAGES, Js.round(height / STAGE_HEIGHT)));
    }

    public static void buildTower(ShellBuilder s, TowerSpec spec) {
        double sizeX = spec.x1 - spec.x0;
        double sizeZ = spec.z1 - spec.z0;
        double height = spec.topY - spec.baseY;
        if (sizeX < 5 || sizeZ < 5 || height < MIN_TOWER_HEIGHT) return;

        String shellId = "tower/" + spec.id;
        // Open to the east: a west tower stands over the west bay, not in front of it.
        s.add(new AddOptions(shellId, ElementKind.TOWER, MaterialRole.MASONRY_MAIN, LevelName.TOWER,
                new Geometry.Shell(Box.of(spec.x0, spec.baseY, spec.z0, spec.x1, spec.topY, spec.z1), spec.thickness,
                        ShellFaces.ALL.withBottom(false).withTop(false).withEast(spec.open != Open.EAST)))
                .supports(new ArrayList<>(spec.supports == null ? List.of() : spec.supports)));
        if (spec.grounded) s.ground(new Rect(spec.id, spec.x0, spec.x1, spec.z0, spec.z1));
        buildOpenFaceClosure(s, spec, shellId);

        int stages = (int) stageCount(height);
        IntToDoubleFunction stageY = n -> spec.baseY + Js.round((height * n) / stages);

        // String courses at the stage joints: what the eye counts the storeys with.
        for (int n = 1; n < stages; n++) {
            double y = stageY.applyAsDouble(n);
            s.add(new AddOptions(shellId + "/course-" + Js.str(n), ElementKind.WALL, MaterialRole.MASONRY_TRIM, LevelName.TOWER,
                    new Geometry.Shell(Box.of(spec.x0 - 1, y, spec.z0 - 1, spec.x1 + 1, y + 1, spec.z1 + 1), spec.thickness + 1,
                            ShellFaces.ALL.withTop(false).withBottom(false).withEast(
                                    spec.open != Open.EAST || spec.openTopY != null && y >= spec.openTopY)))
                    .parent(shellId).supports(shellId));
        }

        buildCornerButtresses(s, spec, shellId, stages, stageY);
        buildOpenings(s, spec, shellId, stages, stageY);
        Cap.buildCap(s, spec, shellId);
        buildInteriorFloors(s, spec, shellId, stages, stageY);
        buildAtticAccess(s, spec, shellId);
    }

    /** The band that closes an open face above the fabric it opens into. */
    private static void buildOpenFaceClosure(ShellBuilder s, TowerSpec spec, String shellId) {
        if (spec.open != Open.EAST || spec.openTopY == null) return;
        double from = Math.max(spec.baseY, spec.openTopY);
        if (from >= spec.topY) return;
        s.add(new AddOptions(shellId + "/east-closure", ElementKind.TOWER, MaterialRole.MASONRY_MAIN, LevelName.TOWER,
                Parts.solid(Box.of(spec.x1 - spec.thickness, from, spec.z0, spec.x1, spec.topY, spec.z1)))
                .parent(shellId).supports(shellId));
    }

    /**
     * How far a corner buttress reaches along the wall it clasps, from the corner, and so how far
     * it reaches into the shaft: anything inside a tower has to clear it.
     */
    public static double cornerButtressReach(TowerSpec spec) {
        if (spec.buttressProjection <= 0) return 0;
        return Math.max(3, Math.min(6, Js.round((spec.x1 - spec.x0) * 0.25)));
    }

    private static void buildCornerButtresses(ShellBuilder s, TowerSpec spec, String shellId, int stages, IntToDoubleFunction stageY) {
        if (spec.buttressProjection <= 0) return;
        double size = cornerButtressReach(spec);
        String[] xNames = {"w", "e"};
        double[] xs = {spec.x0, spec.x1};
        String[] zNames = {"n", "s"};
        double[] zs = {spec.z0, spec.z1};
        for (int xi = 0; xi < 2; xi++) {
            for (int zi = 0; zi < 2; zi++) {
                String xName = xNames[xi], zName = zNames[zi];
                double cx = xs[xi], cz = zs[zi];
                // The east corners of a tower open to the east are inside the building.
                if (spec.open == Open.EAST && xName.equals("e")) continue;
                for (int n = 0; n < stages; n++) {
                    double projection = Math.max(1, spec.buttressProjection - n);
                    double x0 = xName.equals("w") ? cx - projection : cx - size;
                    double x1 = xName.equals("w") ? cx + size : cx + projection;
                    double z0 = zName.equals("n") ? cz - projection : cz - size;
                    double z1 = zName.equals("n") ? cz + size : cz + projection;
                    boolean blocked = false;
                    if (spec.keepOut != null) {
                        for (Rect r : spec.keepOut) {
                            if (r.x0() < x1 && x0 < r.x1() && r.z0() < z1 && z0 < r.z1()) {
                                blocked = true;
                                break;
                            }
                        }
                    }
                    if (blocked) break;
                    s.add(new AddOptions(shellId + "/buttress-" + xName + zName + "-" + Js.str(n + 1), ElementKind.BUTTRESS,
                            MaterialRole.MASONRY_MAIN, LevelName.TOWER,
                            Parts.solid(Box.of(x0, spec.baseY, z0, x1, stageY.applyAsDouble(n + 1), z1)))
                            .parent(shellId).supports(shellId));
                    if (n == 0 && spec.grounded) s.ground(new Rect(spec.id + "-" + xName + zName, x0, x1, z0, z1));
                }
            }
        }
    }

    /** Lancets in the upper middle stage and a tall belfry opening in the top one. */
    private static void buildOpenings(ShellBuilder s, TowerSpec spec, String shellId, int stages, IntToDoubleFunction stageY) {
        for (Dir face : spec.pierced) {
            Axis along = face == Dir.NORTH || face == Dir.SOUTH ? Axis.X : Axis.Z;
            double lo = along == Axis.X ? spec.x0 : spec.z0;
            double hi = along == Axis.X ? spec.x1 : spec.z1;
            double bandFrom, bandTo;
            switch (face) {
                case NORTH -> {
                    bandFrom = spec.z0;
                    bandTo = spec.z0 + spec.thickness;
                }
                case SOUTH -> {
                    bandFrom = spec.z1 - spec.thickness;
                    bandTo = spec.z1;
                }
                case WEST -> {
                    bandFrom = spec.x0;
                    bandTo = spec.x0 + spec.thickness;
                }
                default -> {
                    bandFrom = spec.x1 - spec.thickness;
                    bandTo = spec.x1;
                }
            }
            for (int n = 1; n < stages; n++) {
                double y0 = stageY.applyAsDouble(n - 1);
                double y1 = stageY.applyAsDouble(n);
                boolean belfry = n == stages - 1;
                if (!belfry && n < stages - 2) continue;
                double clear = y1 - y0;
                if (clear < 8) continue;
                double width = belfry ? Math.max(3, Js.round((hi - lo) * 0.45)) : Math.max(2, Js.round((hi - lo) * 0.2));
                double centre = Js.round((lo + hi) / 2);
                double from = centre - Math.floor(width / 2);
                double sillY = y0 + Js.round(clear * (belfry ? 0.2 : 0.35));
                double crownY = y1 - 2;
                s.add(new AddOptions("aperture/" + spec.id + "-" + face.id() + "-" + (belfry ? "belfry" : "stage-" + Js.str(n)),
                        ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.TOWER,
                        Parts.archedOpening(along, from, from + width, bandFrom, bandTo, sillY,
                                Js.round(sillY + (crownY - sillY) * 0.55), crownY))
                        .parent(shellId)
                        .param("symmetry", along.id()));
            }
        }
    }

    /**
     * Floors and a ladder inside a tower whose cap is not flat: a platform at the top stage joint
     * (and the midpoint on a tall tower) and a ladder on its own pier between them.
     */
    private static void buildInteriorFloors(ShellBuilder s, TowerSpec spec, String shellId, int stages, IntToDoubleFunction stageY) {
        if (!spec.interiorFloors || spec.cap == TowerCap.FLAT || stages < 2) return;
        double inset = Math.max(spec.thickness, cornerButtressReach(spec));
        // The ladder clears the buttress toes, but the deck must meet the wall. Insetting
        // both leaves a continuous fall trench around every tower storey.
        double x0 = spec.x0 + spec.thickness;
        double x1 = spec.x1 - spec.thickness;
        double z0 = spec.z0 + spec.thickness;
        double z1 = spec.z1 - spec.thickness;
        int sizeX = (int) (x1 - x0);
        int sizeZ = (int) (z1 - z0);
        if (sizeX < 3 || sizeZ < 3) return;

        double footY = spec.viceFootY != null ? spec.viceFootY : spec.baseY;
        double top = stageY.applyAsDouble(stages - 1);
        if (top - footY < 4) return;

        double pierX = spec.x0 + inset;
        double pierZ = spec.z0 + inset;
        if (pierX + 2 >= x1 || pierZ >= z1) return;
        // The ladder's topmost rung is at the landing's standing height; box y1 is exclusive.
        double climbTop = top + 2;
        s.add(new AddOptions(shellId + "/floors/pier", ElementKind.BUTTRESS, MaterialRole.MASONRY_TRIM, LevelName.TOWER,
                Parts.solid(Box.of(pierX, footY, pierZ, pierX + 1, climbTop, pierZ + 1)))
                .parent(shellId).supports(shellId));
        s.add(new AddOptions(shellId + "/floors/ladder", ElementKind.TIMBER, MaterialRole.TIMBER, LevelName.TOWER,
                Parts.solid(Box.of(pierX + 1, footY, pierZ, pierX + 2, climbTop, pierZ + 1)))
                .parent(shellId)
                .supports(shellId + "/floors/pier")
                .param("form", "ladder")
                .param("facing", "east"));

        Set<Integer> floorStages = new LinkedHashSet<>();
        floorStages.add((int) Js.round(stages / 2.0));
        floorStages.add(stages - 1);
        for (int n : floorStages) {
            if (n < 1) continue;
            double y = stageY.applyAsDouble(n);
            if (y <= footY + 2) continue;
            double[] heights = new double[sizeX * sizeZ];
            Arrays.fill(heights, y);
            for (int dx = 0; dx <= 1; dx++) {
                int ix = (int) (pierX + dx - x0);
                int iz = (int) (pierZ - z0);
                if (ix >= 0 && ix < sizeX && iz >= 0 && iz < sizeZ) heights[iz * sizeX + ix] = Double.NaN;
            }
            s.add(new AddOptions(shellId + "/floors/deck-" + Js.str(n), ElementKind.FLOOR, MaterialRole.FLOOR, LevelName.TOWER,
                    Geometry.HeightField.solid(x0, z0, sizeX, sizeZ, y - 1, heights))
                    .parent(shellId).supports(shellId));
        }
    }
    /** A shaft entered from the roof space needs a doorway, not a wall across its route. */
    private static void buildAtticAccess(ShellBuilder s, TowerSpec spec, String shellId) {
        if (!spec.atticAccess || spec.viceFootY == null) return;
        double y = spec.viceFootY;
        double z = Math.floor((spec.z0 + spec.z1) / 2);
        if (y + 3 >= spec.topY || spec.z1 - spec.z0 < 7) return;
        for (boolean east : new boolean[]{false, true}) {
            double x0 = east ? spec.x1 - spec.thickness - 1 : spec.x0 - 1;
            double x1 = east ? spec.x1 + 1 : spec.x0 + spec.thickness + 1;
            String id = shellId + "/attic-" + (east ? "east" : "west");
            s.add(new AddOptions(id + "/threshold", ElementKind.FLOOR, MaterialRole.FLOOR, LevelName.ATTIC,
                    Parts.solid(Box.of(x0, y - 1, z - 1, x1, y, z + 2)))
                    .parent(shellId).supports(shellId));
            s.add(new AddOptions("aperture/" + spec.id + "-attic-" + (east ? "east" : "west"),
                    ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.ATTIC,
                    Parts.solid(Box.of(x0, y, z - 1, x1, y + 3, z + 2)))
                    .parent(shellId).param("symmetry", "z"));
        }
    }

}
