package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Axis;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tower caps (Capella {@code kit/cap.ts}): what the top of a tower does against the sky, and
 * whether you can stand on it. {@code flat} is a parapet walk with a vice up to it; {@code dome}
 * seals the shaft under a chamfered platform; {@code spire} and {@code saddleback} are neither.
 */
public final class Cap {
    /** A saddleback's pitch as a fraction of the same tower's spire pitch: lower, or it reads as a sat-on spire. */
    private static final double SADDLEBACK_PITCH = 0.6;
    /** Two blocks of parapet: one is a kerb, three hides a player behind it. */
    private static final double PARAPET_HEIGHT = 2;
    /** Two thick, so every relief cut at a parapet corner has backing. */
    private static final double PARAPET_THICKNESS = 2;

    private Cap() {
    }

    public static void buildCap(ShellBuilder s, Tower.TowerSpec spec, String shellId) {
        switch (spec.cap) {
            case SPIRE -> {
                if (spec.spirePitch <= 0) return;
                buildSpire(s, spec, shellId);
                buildCapPinnacles(s, spec, shellId);
            }
            case SADDLEBACK -> {
                if (spec.spirePitch <= 0) return;
                buildSaddleback(s, spec, shellId);
                buildCapPinnacles(s, spec, shellId);
            }
            case DOME -> buildDome(s, spec, shellId);
            case FLAT -> buildFlatRoof(s, spec, shellId);
        }
    }

    /** A square-based stepped cone, as a shell height field. */
    private static void buildSpire(ShellBuilder s, Tower.TowerSpec spec, String shellId) {
        int sizeX = (int) (spec.x1 - spec.x0);
        int sizeZ = (int) (spec.z1 - spec.z0);
        double cx = (spec.x0 + spec.x1) / 2;
        double cz = (spec.z0 + spec.z1) / 2;
        double[] heights = new double[sizeX * sizeZ];
        Arrays.fill(heights, Double.NaN);
        for (int iz = 0; iz < sizeZ; iz++) {
            for (int ix = 0; ix < sizeX; ix++) {
                double inset = Math.min(sizeX / 2.0 - Math.abs(spec.x0 + ix + 0.5 - cx), sizeZ / 2.0 - Math.abs(spec.z0 + iz + 0.5 - cz));
                heights[iz * sizeX + ix] = spec.topY - 1 + Js.round(inset * spec.spirePitch);
            }
        }
        s.add(new AddOptions("spire/" + spec.id, ElementKind.SPIRE, MaterialRole.ROOF, LevelName.ROOF,
                new Geometry.HeightField(spec.x0, spec.z0, sizeX, sizeZ, spec.topY, Geometry.HeightMode.SHELL, 1.0, heights, null))
                .parent(shellId).supports(shellId));
    }

    /**
     * A pitched roof ridged across the tower's shorter axis, so the gables close its broader faces.
     * {@code buildRoof} and nothing else: no trusses, no eaves course.
     */
    private static void buildSaddleback(ShellBuilder s, Tower.TowerSpec spec, String shellId) {
        double sizeX = spec.x1 - spec.x0;
        double sizeZ = spec.z1 - spec.z0;
        // One below the wall head, so the eaves land on the head.
        Roof.RoofRun run = new Roof.RoofRun("cap-" + spec.id, spec.x0, spec.x1, spec.z0, spec.z1,
                sizeX <= sizeZ ? Axis.X : Axis.Z, spec.topY - 1, Math.max(1, spec.spirePitch * SADDLEBACK_PITCH), 1)
                .eavesCourse(false)
                .supports(List.of(shellId));
        Roof.buildRoof(s, run, List.of());
    }

    private interface DiscHeight {
        double at(double d);
    }

    /** A dome on a drum: chamfer, drum, cornice, hemisphere, lantern. */
    private static void buildDome(ShellBuilder s, Tower.TowerSpec spec, String shellId) {
        int sizeX = (int) (spec.x1 - spec.x0);
        int sizeZ = (int) (spec.z1 - spec.z0);
        double cx = (spec.x0 + spec.x1) / 2;
        double cz = (spec.z0 + spec.z1) / 2;
        double radius = Math.min(sizeX, sizeZ) / 2.0;
        if (radius < 3) return;

        double headY = spec.topY - 1;
        double chamfer = Math.max(2, Js.round(radius * 0.4));
        double drumFootY = headY + chamfer;
        double drumTopY = drumFootY + Math.max(4, Js.round(radius * 1.1));
        double wallT = Math.max(1, Math.min(3, Js.round(radius * 0.3)));

        java.util.function.Function<DiscHeight, double[]> disc = at -> {
            double[] heights = new double[sizeX * sizeZ];
            for (int iz = 0; iz < sizeZ; iz++) {
                for (int ix = 0; ix < sizeX; ix++) {
                    double d = Math.hypot(spec.x0 + ix + 0.5 - cx, spec.z0 + iz + 0.5 - cz);
                    heights[iz * sizeX + ix] = at.at(d);
                }
            }
            return heights;
        };

        s.add(new AddOptions("cap/" + spec.id + "/chamfer", ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.TOWER,
                field(spec, sizeX, sizeZ, disc.apply(d -> d < radius ? drumFootY : Math.max(headY, drumFootY - Js.round(d - radius + 1))),
                        headY, Geometry.HeightMode.SOLID))
                .parent(shellId).supports(shellId));

        s.add(new AddOptions("cap/" + spec.id + "/drum", ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.TOWER,
                field(spec, sizeX, sizeZ, disc.apply(d -> d < radius && d >= radius - wallT ? drumTopY : Double.NaN),
                        drumFootY + 1, Geometry.HeightMode.SOLID))
                .parent(shellId).supports(shellId));

        s.add(new AddOptions("cap/" + spec.id + "/cornice", ElementKind.WALL, MaterialRole.MASONRY_TRIM, LevelName.TOWER,
                field(spec, sizeX, sizeZ, disc.apply(d -> d < radius + 1 && d >= radius - wallT - 1 ? drumTopY + 1 : Double.NaN),
                        drumTopY + 1, Geometry.HeightMode.SOLID))
                .parent(shellId).supports(shellId));

        // The outer ring is flat, at the springing: a true hemisphere's outermost column would hang.
        double squash = 0.85;
        double springY = drumTopY + 2;
        double inner = radius - 0.5;
        double apexY = springY + Js.round(inner * squash);
        s.add(new AddOptions("cap/" + spec.id + "/dome", ElementKind.ROOF, MaterialRole.ROOF, LevelName.ROOF,
                field(spec, sizeX, sizeZ, disc.apply(d -> d >= radius ? Double.NaN
                        : d >= inner ? springY : springY + Js.round(Math.sqrt(Math.max(0, inner * inner - d * d)) * squash)),
                        springY, Geometry.HeightMode.SHELL))
                .parent(shellId).supports(shellId));

        double lanternR = Math.max(1.5, radius * 0.25);
        double lanternTopY = apexY + Math.max(3, Js.round(radius * 0.6));
        s.add(new AddOptions("cap/" + spec.id + "/lantern", ElementKind.PINNACLE, MaterialRole.MASONRY_TRIM, LevelName.ROOF,
                field(spec, sizeX, sizeZ, disc.apply(d -> d < lanternR ? lanternTopY : Double.NaN), apexY, Geometry.HeightMode.SOLID))
                .parent(shellId).supports(shellId));
        s.add(new AddOptions("spire/" + spec.id, ElementKind.SPIRE, MaterialRole.ROOF, LevelName.ROOF,
                field(spec, sizeX, sizeZ, disc.apply(d -> d < lanternR + 1 ? lanternTopY + Js.round(lanternR + 1 - d) : Double.NaN),
                        lanternTopY, Geometry.HeightMode.SHELL))
                .parent(shellId).supports(shellId));
    }

    private static Geometry.HeightField field(Tower.TowerSpec spec, int sizeX, int sizeZ, double[] heights, double baseY,
                                              Geometry.HeightMode mode) {
        return new Geometry.HeightField(spec.x0, spec.z0, sizeX, sizeZ, baseY, mode, 1.0, heights, null);
    }

    /**
     * A flat leaded roof: deck, parapet, merlons and the vice that reaches it. The deck is laid
     * with a hole over the head of the stair rather than cut afterwards.
     */
    private static void buildFlatRoof(ShellBuilder s, Tower.TowerSpec spec, String shellId) {
        int sizeX = (int) (spec.x1 - spec.x0);
        int sizeZ = (int) (spec.z1 - spec.z0);
        double deckY = spec.topY;
        List<Integer> well = buildVice(s, spec, shellId, deckY);

        double[] heights = new double[sizeX * sizeZ];
        Arrays.fill(heights, deckY);
        for (int cell : well) heights[cell] = Double.NaN;
        s.add(new AddOptions("cap/" + spec.id + "/deck", ElementKind.ROOF, MaterialRole.ROOF, LevelName.ROOF,
                Geometry.HeightField.solid(spec.x0, spec.z0, sizeX, sizeZ, deckY, heights))
                .parent(shellId).supports(shellId));

        s.add(new AddOptions("cap/" + spec.id + "/parapet", ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.ROOF,
                new Geometry.Shell(Box.of(spec.x0, deckY + 1, spec.z0, spec.x1, deckY + 1 + PARAPET_HEIGHT, spec.z1),
                        PARAPET_THICKNESS, ShellFaces.ALL.withTop(false).withBottom(false)))
                .parent(shellId).supports(shellId));

        // Merlons on every other block, counted from the tower's own corner, plain at the corners.
        double merlonY = deckY + 1 + PARAPET_HEIGHT;
        double[] merlons = new double[sizeX * sizeZ];
        Arrays.fill(merlons, Double.NaN);
        for (int iz = 0; iz < sizeZ; iz++) {
            for (int ix = 0; ix < sizeX; ix++) {
                boolean inX = ix < PARAPET_THICKNESS || ix >= sizeX - PARAPET_THICKNESS;
                boolean inZ = iz < PARAPET_THICKNESS || iz >= sizeZ - PARAPET_THICKNESS;
                if (!inX && !inZ) continue;
                if (inX && inZ) continue;
                int along = inX ? iz : ix;
                if (along % 2 == 0) merlons[iz * sizeX + ix] = merlonY;
            }
        }
        s.add(new AddOptions("cap/" + spec.id + "/merlons", ElementKind.WALL, MaterialRole.MASONRY_TRIM, LevelName.ROOF,
                new Geometry.HeightField(spec.x0, spec.z0, sizeX, sizeZ, merlonY, Geometry.HeightMode.SURFACE, null, merlons, null))
                .parent(shellId).supports(shellId));
    }

    /**
     * The vice inside the shaft: one two-block tread per cell of the inner perimeter (clear of the
     * corner buttresses), rising a block a cell to the deck. Returns the deck cells left open.
     */
    private static List<Integer> buildVice(ShellBuilder s, Tower.TowerSpec spec, String shellId, double deckY) {
        double inset = Math.max(spec.thickness, Tower.cornerButtressReach(spec));
        double innerX0 = spec.x0 + inset;
        double innerZ0 = spec.z0 + inset;
        double innerX1 = spec.x1 - inset;
        double innerZ1 = spec.z1 - inset;
        int sizeX = (int) (innerX1 - innerX0);
        int sizeZ = (int) (innerZ1 - innerZ0);
        double footY = spec.viceFootY != null ? spec.viceFootY : spec.baseY;
        double climb = deckY - footY;
        List<Integer> well = new ArrayList<>();
        if (sizeX < 3 || sizeZ < 3 || climb < 4) return well;

        List<Turret.Cell> ring = Turret.ringOf(sizeX, sizeZ);
        double width = spec.x1 - spec.x0;
        for (int turn = 0; turn * ring.size() <= climb; turn++) {
            double[] heights = new double[sizeX * sizeZ];
            double[] bases = new double[sizeX * sizeZ];
            Arrays.fill(heights, Double.NaN);
            Arrays.fill(bases, Double.NaN);
            boolean any = false;
            for (int k = 0; k < ring.size(); k++) {
                int step = turn * ring.size() + k;
                if (step > climb) break;
                Turret.Cell cell = ring.get(k);
                double y = footY + step;
                heights[cell.iz() * sizeX + cell.ix()] = y;
                bases[cell.iz() * sizeX + cell.ix()] = y - 1;
                any = true;
                // Head clearance under the deck.
                if (deckY - y <= 2) {
                    double dx = innerX0 + cell.ix() - spec.x0;
                    double dz = innerZ0 + cell.iz() - spec.z0;
                    well.add((int) (dz * width + dx));
                }
            }
            if (!any) break;
            s.add(new AddOptions("cap/" + spec.id + "/vice-" + Js.str(turn + 1), ElementKind.STAIR, MaterialRole.MASONRY_TRIM,
                    LevelName.TOWER,
                    new Geometry.HeightField(innerX0, innerZ0, sizeX, sizeZ, footY, Geometry.HeightMode.SOLID, null, heights, bases))
                    .parent(shellId).supports(shellId));
        }
        return well;
    }

    /** The four pinnacles round the foot of a spire or a saddleback. */
    private static void buildCapPinnacles(ShellBuilder s, Tower.TowerSpec spec, String shellId) {
        double sizeX = spec.x1 - spec.x0;
        double sizeZ = spec.z1 - spec.z0;
        double pin = Math.max(3, Math.min(5, Js.round(sizeX * 0.22)));
        double height = Js.round(Math.min(sizeX, sizeZ) * 0.9);
        String[] xNames = {"w", "e"};
        double[] xs = {spec.x0 - 1, spec.x1 + 1 - pin};
        String[] zNames = {"n", "s"};
        double[] zs = {spec.z0 - 1, spec.z1 + 1 - pin};
        for (int xi = 0; xi < 2; xi++) {
            for (int zi = 0; zi < 2; zi++) {
                if (spec.open == Tower.Open.EAST && xNames[xi].equals("e")) continue;
                double x0 = xs[xi], z0 = zs[zi];
                Pinnacle.buildPinnacle(s, new Pinnacle.Options("pinnacle/" + spec.id + "-" + xNames[xi] + zNames[zi],
                        x0, x0 + pin, z0, z0 + pin, spec.topY, height, LevelName.ROOF)
                        .parent(shellId)
                        .supports(shellId));
            }
        }
    }
}
