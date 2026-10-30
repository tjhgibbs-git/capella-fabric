package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Axis;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Roofs and the timber that carries them (Capella {@code kit/roof.ts}).
 *
 * <p>A pitched roof is a {@code shell} height field, not an extrusion: the column height is the
 * soffit, the covering sits on it, and the skirt the shell mode drops closes the step between
 * adjacent columns. That is what makes a 1.4 pitch watertight in whole blocks.
 *
 * <p>The trusses use the same trick sideways: a rafter pair is one height field a single column
 * wide, following the roof line a couple of blocks under it.
 */
public final class Roof {
    private Roof() {
    }

    /** {@code { low?, high? }} flags on the two ends of a run. Null means "not said". */
    public static final class Ends {
        public final Boolean low, high;

        public Ends(Boolean low, Boolean high) {
            this.low = low;
            this.high = high;
        }

        boolean is(boolean high) {
            return Boolean.TRUE.equals(high ? this.high : this.low);
        }
    }

    /** {@code gableSupports}: what each gable stands on, when not the run's own supports. */
    public static final class EndSupports {
        public final List<String> low, high;

        public EndSupports(List<String> low, List<String> high) {
            this.low = low;
            this.high = high;
        }
    }

    /**
     * One pitched roof run. The required fields are final; the optional ones (nullable, as in the
     * TS) are set fluently.
     */
    public static final class RoofRun {
        /** Element id prefix: {@code nave}, {@code transept-north}. */
        public final String id;
        public final double x0, x1, z0, z1;
        /** The axis the ridge runs along (X or Z). */
        public final Axis along;
        public final double eavesY;
        public final double pitch;
        /** Blocks of covering at and above the soffit. */
        public final double thickness;
        /** Ends to close with a gable; a run that dies into another roof leaves its end open. */
        public Ends gables;
        public List<String> supports;
        /** What each gable actually stands on: the one wall that closes that end. */
        public EndSupports gableSupports;
        /** Close the course at {@code eavesY} along both eaves. Null means true. */
        public Boolean eavesCourse;
        /** The covering's material; null means ROOF. */
        public MaterialRole role;
        /** The gable walls' material; null means MASONRY_MAIN. */
        public MaterialRole gableRole;
        /** Hip the ends instead of gabling them. */
        public Ends hips;
        /** How far tie beam and collar reach either side of the ridge; null means the half-span. */
        public Double tieSpan;
        /** Stretches of the run that are not there, as half-open [from, to) along the ridge. */
        public List<double[]> holes;

        public RoofRun(String id, double x0, double x1, double z0, double z1, Axis along, double eavesY, double pitch,
                       double thickness) {
            this.id = id;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.along = along;
            this.eavesY = eavesY;
            this.pitch = pitch;
            this.thickness = thickness;
        }

        /** {@code { ...run, x0, x1, z0, z1 }}. */
        public RoofRun withSpan(double nx0, double nx1, double nz0, double nz1) {
            RoofRun r = new RoofRun(id, nx0, nx1, nz0, nz1, along, eavesY, pitch, thickness);
            r.gables = gables;
            r.supports = supports;
            r.gableSupports = gableSupports;
            r.eavesCourse = eavesCourse;
            r.role = role;
            r.gableRole = gableRole;
            r.hips = hips;
            r.tieSpan = tieSpan;
            r.holes = holes;
            return r;
        }

        public RoofRun gables(Boolean low, Boolean high) {
            this.gables = new Ends(low, high);
            return this;
        }

        public RoofRun hips(Boolean low, Boolean high) {
            this.hips = new Ends(low, high);
            return this;
        }

        public RoofRun supports(List<String> s) {
            this.supports = s;
            return this;
        }

        public RoofRun gableSupports(List<String> low, List<String> high) {
            this.gableSupports = new EndSupports(low, high);
            return this;
        }

        public RoofRun eavesCourse(boolean v) {
            this.eavesCourse = v;
            return this;
        }

        public RoofRun role(MaterialRole r) {
            this.role = r;
            return this;
        }

        public RoofRun gableRole(MaterialRole r) {
            this.gableRole = r;
            return this;
        }

        public RoofRun tieSpan(double t) {
            this.tieSpan = t;
            return this;
        }

        public RoofRun holes(List<double[]> h) {
            this.holes = h;
            return this;
        }
    }

    /** Where the ridge sits, and how high. */
    public static final class Ridge {
        public final double at, y, halfSpan;

        Ridge(double at, double y, double halfSpan) {
            this.at = at;
            this.y = y;
            this.halfSpan = halfSpan;
        }
    }

    private static boolean holed(RoofRun run, double at) {
        if (run.holes == null) return false;
        for (double[] h : run.holes) if (at >= h[0] && at < h[1]) return true;
        return false;
    }

    /** The stretches of [lo, hi) that survive the holes. */
    private static List<double[]> segments(RoofRun run, double lo, double hi) {
        List<double[]> cuts = new ArrayList<>(run.holes == null ? List.of() : run.holes);
        cuts.sort(Comparator.comparingDouble(a -> a[0]));
        List<double[]> out = new ArrayList<>();
        double at = lo;
        for (double[] c : cuts) {
            if (c[0] > at) out.add(new double[]{at, Math.min(c[0], hi)});
            at = Math.max(at, c[1]);
        }
        if (at < hi) out.add(new double[]{at, hi});
        out.removeIf(p -> !(p[1] > p[0]));
        return out;
    }

    public static Ridge ridgeOf(RoofRun run) {
        double a = run.along == Axis.X ? run.z0 : run.x0;
        double b = run.along == Axis.X ? run.z1 : run.x1;
        double halfSpan = (b - a) / 2;
        return new Ridge((a + b) / 2, run.eavesY + Js.round(halfSpan * run.pitch), halfSpan);
    }

    private static double[] runSpan(RoofRun run) {
        return run.along == Axis.X ? new double[]{run.x0, run.x1} : new double[]{run.z0, run.z1};
    }

    /**
     * The soffit at a column: the pitch across the run, and where an end is hipped the same pitch
     * running inward from that end, whichever is lower. This is the <em>top</em> of the covering.
     */
    public static double soffitAt(RoofRun run, double x, double z) {
        Ridge ridge = ridgeOf(run);
        double across = run.along == Axis.X ? z + 0.5 : x + 0.5;
        double h = ridge.y - Js.round(Math.abs(across - ridge.at) * run.pitch);
        if (run.hips == null) return h;
        double[] ab = runSpan(run);
        double along = run.along == Axis.X ? x + 0.5 : z + 0.5;
        double out = h;
        if (run.hips.is(false)) out = Math.min(out, run.eavesY + Js.round((along - ab[0]) * run.pitch));
        if (run.hips.is(true)) out = Math.min(out, run.eavesY + Js.round((ab[1] - along) * run.pitch));
        return out;
    }

    private interface ColumnHeight {
        double at(double x, double z);
    }

    private static Geometry.HeightField heightField(RoofRun run, ColumnHeight at, Geometry.HeightMode mode,
                                                    double thickness, double baseY) {
        int sizeX = (int) (run.x1 - run.x0);
        int sizeZ = (int) (run.z1 - run.z0);
        double[] heights = new double[Math.max(0, sizeX * sizeZ)];
        Arrays.fill(heights, Double.NaN);
        for (int iz = 0; iz < sizeZ; iz++) {
            for (int ix = 0; ix < sizeX; ix++) {
                heights[iz * sizeX + ix] = at.at(run.x0 + ix, run.z0 + iz);
            }
        }
        return new Geometry.HeightField(run.x0, run.z0, sizeX, sizeZ, baseY, mode, thickness, heights, null);
    }

    private static MaterialRole roleOf(RoofRun run) {
        return run.role != null ? run.role : MaterialRole.ROOF;
    }

    /**
     * The covering, the gable walls that close its ends, and a truss at every station given.
     * {@code stations} are positions along the ridge, normally the bay boundaries.
     */
    public static void buildRoof(ShellBuilder s, RoofRun run, List<? extends Number> stations) {
        Ridge ridge = ridgeOf(run);
        if (ridge.halfSpan < 2 || run.x1 <= run.x0 || run.z1 <= run.z0) return;
        List<String> supports = new ArrayList<>(run.supports == null ? List.of() : run.supports);
        boolean alongX = run.along == Axis.X;

        s.add(new AddOptions("roof/" + run.id + "/covering", ElementKind.ROOF, roleOf(run), LevelName.ROOF,
                heightField(run, (x, z) -> holed(run, alongX ? x : z) ? Double.NaN : soffitAt(run, x, z),
                        Geometry.HeightMode.SHELL, run.thickness, run.eavesY))
                .supports(supports)
                .param("pitch", run.pitch)
                .param("ridgeY", ridge.y));

        if (!Boolean.FALSE.equals(run.eavesCourse)) buildEavesCourse(s, run);

        // Gables: solid from the wall head up to the underside of the covering. A hipped end has none.
        boolean wantLow = (run.gables == null || run.gables.low == null || run.gables.low) && !(run.hips != null && run.hips.is(false));
        boolean wantHigh = (run.gables == null || run.gables.high == null || run.gables.high) && !(run.hips != null && run.hips.is(true));
        Object[][] ends = alongX
                ? new Object[][]{{"west", run.x0, run.x0 + 1, false}, {"east", run.x1 - 1, run.x1, true}}
                : new Object[][]{{"north", run.z0, run.z0 + 1, false}, {"south", run.z1 - 1, run.z1, true}};
        for (Object[] end : ends) {
            String name = (String) end[0];
            double a = (Double) end[1];
            double b = (Double) end[2];
            boolean high = (Boolean) end[3];
            if (!(high ? wantHigh : wantLow) || holed(run, a)) continue;
            RoofRun slice = alongX ? run.withSpan(a, b, run.z0, run.z1) : run.withSpan(run.x0, run.x1, a, b);
            List<String> gs = run.gableSupports == null ? null : (high ? run.gableSupports.high : run.gableSupports.low);
            s.add(new AddOptions("roof/" + run.id + "/gable-" + name, ElementKind.WALL,
                    run.gableRole != null ? run.gableRole : MaterialRole.MASONRY_MAIN, LevelName.ATTIC,
                    heightField(slice, (x, z) -> soffitAt(run, x, z) - 1, Geometry.HeightMode.SOLID, 1, run.eavesY))
                    .supports(new ArrayList<>(gs != null ? gs : supports)));
        }

        for (int n = 0; n < stations.size(); n++) {
            double station = stations.get(n).doubleValue();
            RoofRun slice = alongX ? run.withSpan(station, station + 1, run.z0, run.z1)
                    : run.withSpan(run.x0, run.x1, station, station + 1);
            if (slice.x1 <= run.x0 || slice.x0 >= run.x1 || slice.z1 <= run.z0 || slice.z0 >= run.z1) continue;
            // A truss where the run is not keeps the others' numbers.
            if (holed(run, station)) continue;
            String num = Js.str(n + 1);

            s.add(new AddOptions("timber/" + run.id + "/rafters-" + num, ElementKind.TIMBER, MaterialRole.TIMBER,
                    LevelName.ATTIC,
                    heightField(slice, (x, z) -> soffitAt(run, x, z) - run.thickness, Geometry.HeightMode.SHELL, 1, run.eavesY))
                    .supports(supports));

            // Tie beam at the wall head and a collar a little over half way up.
            double collarY = run.eavesY + Js.round((ridge.y - run.eavesY) * 0.55);
            for (int k = 0; k < 2; k++) {
                String name = k == 0 ? "tie" : "collar";
                double y = k == 0 ? run.eavesY : collarY;
                double reach = run.tieSpan != null ? run.tieSpan : ridge.halfSpan;
                double half = Math.min(reach, k == 0 ? ridge.halfSpan : (ridge.y - collarY) / run.pitch);
                double lo = Js.round(ridge.at - half);
                double hi = Js.round(ridge.at + half);
                Box box = alongX
                        ? new Box(new Vec3(slice.x0, y, lo), new Vec3(slice.x1, y + 1, hi))
                        : new Box(new Vec3(lo, y, slice.z0), new Vec3(hi, y + 1, slice.z1));
                s.add(new AddOptions("timber/" + run.id + "/" + name + "-" + num, ElementKind.TIMBER, MaterialRole.TIMBER,
                        LevelName.ATTIC, new Geometry.Solid(box)).supports(supports));
            }
        }

        // Ridge purlin, under the apex for as far as there is an apex.
        double[] span = runSpan(run);
        double hipBack = Js.round(ridge.halfSpan);
        double purlinLo = span[0] + (run.hips != null && run.hips.is(false) ? hipBack : 0);
        double purlinHi = span[1] - (run.hips != null && run.hips.is(true) ? hipBack : 0);
        double at = Js.round(ridge.at);
        List<double[]> pieces = segments(run, purlinLo, purlinHi);
        for (int n = 0; n < pieces.size(); n++) {
            double lo = pieces.get(n)[0], hi = pieces.get(n)[1];
            double y0 = ridge.y - run.thickness - 1, y1 = ridge.y - run.thickness;
            Box box = alongX
                    ? new Box(new Vec3(lo, y0, at), new Vec3(hi, y1, at + 1))
                    : new Box(new Vec3(at, y0, lo), new Vec3(at + 1, y1, hi));
            s.add(new AddOptions("timber/" + run.id + "/ridge" + (pieces.size() == 1 ? "" : "-" + Js.str(n + 1)),
                    ElementKind.TIMBER, MaterialRole.TIMBER, LevelName.ATTIC, new Geometry.Solid(box)).supports(supports));
        }
    }

    /**
     * The eaves course: the lowest course of the covering, at the eaves themselves. Without it the
     * outermost column's soffit rounds to {@code eavesY + 1} and nothing closes the gap between the
     * wall head and the covering.
     */
    private static void buildEavesCourse(ShellBuilder s, RoofRun run) {
        if (run.hips != null && (run.hips.is(false) || run.hips.is(true))) {
            buildHippedEavesCourse(s, run);
            return;
        }
        String covering = "roof/" + run.id + "/covering";
        boolean alongX = run.along == Axis.X;
        Object[][] sides = alongX
                ? new Object[][]{{"north", run.z0}, {"south", run.z1 - 1}}
                : new Object[][]{{"west", run.x0}, {"east", run.x1 - 1}};
        double[] span = runSpan(run);
        for (Object[] side : sides) {
            String name = (String) side[0];
            double at = (Double) side[1];
            double x = alongX ? run.x0 : at;
            double z = alongX ? at : run.z0;
            double top = soffitAt(run, x, z);
            if (top <= run.eavesY) continue;
            List<double[]> pieces = segments(run, span[0], span[1]);
            for (int n = 0; n < pieces.size(); n++) {
                double lo = pieces.get(n)[0], hi = pieces.get(n)[1];
                Box box = alongX
                        ? new Box(new Vec3(lo, run.eavesY, at), new Vec3(hi, top, at + 1))
                        : new Box(new Vec3(at, run.eavesY, lo), new Vec3(at + 1, top, hi));
                s.add(new AddOptions(covering + "/eaves-" + name + (pieces.size() == 1 ? "" : "-" + Js.str(n + 1)),
                        ElementKind.ROOF, roleOf(run), LevelName.ROOF, new Geometry.Solid(box))
                        .parent(covering)
                        .supports(covering));
            }
        }
    }

    /** The same course round a hipped roof: all four sides, each a one-column height field. */
    private static void buildHippedEavesCourse(ShellBuilder s, RoofRun run) {
        String covering = "roof/" + run.id + "/covering";
        boolean alongX = run.along == Axis.X;
        Object[][] sides = {
                {"west", run.withSpan(run.x0, run.x0 + 1, run.z0, run.z1)},
                {"east", run.withSpan(run.x1 - 1, run.x1, run.z0, run.z1)},
                {"north", run.withSpan(run.x0, run.x1, run.z0, run.z0 + 1)},
                {"south", run.withSpan(run.x0, run.x1, run.z1 - 1, run.z1)},
        };
        for (Object[] side : sides) {
            String name = (String) side[0];
            boolean keep;
            if (alongX) {
                keep = name.equals("north") || name.equals("south") || run.hips.is(!name.equals("west"));
            } else {
                keep = name.equals("west") || name.equals("east") || run.hips.is(!name.equals("north"));
            }
            if (!keep) continue;
            RoofRun slice = (RoofRun) side[1];
            s.add(new AddOptions(covering + "/eaves-" + name, ElementKind.ROOF, roleOf(run), LevelName.ROOF,
                    heightField(slice, (x, z) -> soffitAt(run, x, z) - 1, Geometry.HeightMode.SOLID, 1, run.eavesY))
                    .parent(covering)
                    .supports(covering));
        }
    }

    /** The options object of {@code buildConicalRoof}. */
    public static final class ConeOptions {
        public final String id;
        public final double centreX, centreZ, radius, eavesY, pitch, thickness;
        public List<String> supports;

        public ConeOptions(String id, double centreX, double centreZ, double radius, double eavesY, double pitch,
                           double thickness) {
            this.id = id;
            this.centreX = centreX;
            this.centreZ = centreZ;
            this.radius = radius;
            this.eavesY = eavesY;
            this.pitch = pitch;
            this.thickness = thickness;
        }

        public ConeOptions supports(List<String> s) {
            this.supports = s;
            return this;
        }
    }

    /**
     * A half cone over an apse (the eastern half only: the flat side abuts the choir roof).
     */
    public static void buildConicalRoof(ShellBuilder s, ConeOptions o) {
        if (o.radius < 3) return;
        double x0 = Js.round(o.centreX - o.radius);
        double x1 = Js.round(o.centreX + o.radius) + 1;
        double z0 = Js.round(o.centreZ - o.radius);
        double z1 = Js.round(o.centreZ + o.radius) + 1;
        int sizeX = (int) (x1 - x0);
        int sizeZ = (int) (z1 - z0);
        double[] heights = new double[sizeX * sizeZ];
        Arrays.fill(heights, Double.NaN);
        for (int iz = 0; iz < sizeZ; iz++) {
            for (int ix = 0; ix < sizeX; ix++) {
                double dx = x0 + ix + 0.5 - o.centreX;
                double dz = z0 + iz + 0.5 - o.centreZ;
                if (dx < -1) continue;
                double r = Math.hypot(dx, dz);
                if (r >= o.radius) continue;
                heights[iz * sizeX + ix] = o.eavesY + Js.round((o.radius - r) * o.pitch);
            }
        }
        s.add(new AddOptions("roof/" + o.id + "/cone", ElementKind.ROOF, MaterialRole.ROOF, LevelName.ROOF,
                new Geometry.HeightField(x0, z0, sizeX, sizeZ, o.eavesY, Geometry.HeightMode.SHELL, o.thickness, heights, null))
                .supports(new ArrayList<>(o.supports == null ? List.of() : o.supports)));
    }
}
