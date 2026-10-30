package io.github.tjhgibbs.capella.core.raster;

import io.github.tjhgibbs.capella.core.geom.Vec2;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.Geometries;
import io.github.tjhgibbs.capella.core.kit.Geometry;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Geometry to voxels (Capella {@code raster/primitives.ts}). Each primitive calls the sink once
 * per voxel it covers; the same voxel may be emitted twice.
 */
public final class Primitives {
    private Primitives() {
    }

    public interface VoxelSink {
        void emit(int x, int y, int z);
    }

    public static void rasterizeGeometry(Geometry g, VoxelSink emit) {
        switch (g) {
            case Geometry.Solid s -> rasterizeBox(s, emit);
            case Geometry.Shell s -> rasterizeShell(s, emit);
            case Geometry.HeightField h -> rasterizeHeightField(h, emit);
            case Geometry.SweptArc a -> rasterizeSweptArc(a, emit);
            case Geometry.Extrude e -> rasterizeExtrude(e, emit);
        }
    }

    public static void rasterizeBox(Geometry.Solid g, VoxelSink emit) {
        var min = g.box().min();
        var max = g.box().max();
        for (int y = (int) Math.floor(min.y()); y < Math.ceil(max.y()); y++) {
            for (int z = (int) Math.floor(min.z()); z < Math.ceil(max.z()); z++) {
                for (int x = (int) Math.floor(min.x()); x < Math.ceil(max.x()); x++) emit.emit(x, y, z);
            }
        }
    }

    public static void rasterizeShell(Geometry.Shell g, VoxelSink emit) {
        int t = (int) Math.max(1, Js.round(g.thickness()));
        int x0 = (int) Math.floor(g.box().min().x()), y0 = (int) Math.floor(g.box().min().y()), z0 = (int) Math.floor(g.box().min().z());
        int x1 = (int) Math.ceil(g.box().max().x()), y1 = (int) Math.ceil(g.box().max().y()), z1 = (int) Math.ceil(g.box().max().z());
        var f = g.faces();
        for (int y = y0; y < y1; y++) {
            boolean inBottom = y < y0 + t, inTop = y >= y1 - t;
            for (int z = z0; z < z1; z++) {
                boolean inNorth = z < z0 + t, inSouth = z >= z1 - t;
                for (int x = x0; x < x1; x++) {
                    boolean inWest = x < x0 + t, inEast = x >= x1 - t;
                    if ((f.bottom() && inBottom) || (f.top() && inTop) || (f.north() && inNorth) || (f.south() && inSouth)
                            || (f.west() && inWest) || (f.east() && inEast)) {
                        emit.emit(x, y, z);
                    }
                }
            }
        }
    }

    private static final int[][] ORTHOGONAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static void rasterizeHeightField(Geometry.HeightField g, VoxelSink emit) {
        int base = (int) Js.round(g.baseY());
        int thickness = (int) Math.max(1, Js.round(g.thickness() == null ? 1 : g.thickness()));
        double[] heights = g.heights();
        for (int iz = 0; iz < g.sizeZ(); iz++) {
            for (int ix = 0; ix < g.sizeX(); ix++) {
                int at = iz * g.sizeX() + ix;
                if (at >= heights.length) continue;
                double h = heights[at];
                if (Double.isNaN(h)) continue;
                int top = (int) Math.floor(h);
                int x = (int) (g.originX() + ix);
                int z = (int) (g.originZ() + iz);
                switch (g.mode()) {
                    case SURFACE -> emit.emit(x, top, z);
                    case SHELL -> {
                        int lo = top;
                        for (int[] d : ORTHOGONAL) {
                            int nx = ix + d[0], nz = iz + d[1];
                            if (nx < 0 || nz < 0 || nx >= g.sizeX() || nz >= g.sizeZ()) continue;
                            int ni = nz * g.sizeX() + nx;
                            if (ni >= heights.length || Double.isNaN(heights[ni])) continue;
                            lo = Math.min(lo, (int) Math.floor(heights[ni]));
                        }
                        for (int y = lo; y <= top + thickness - 1; y++) emit.emit(x, y, z);
                    }
                    case SOLID -> {
                        double per = g.bases() != null && at < g.bases().length ? g.bases()[at] : Double.NaN;
                        int floorOf = Double.isNaN(per) ? base : (int) Js.round(per);
                        int lo = Math.min(floorOf, top), hi = Math.max(floorOf, top);
                        for (int y = lo; y <= hi; y++) emit.emit(x, y, z);
                    }
                }
            }
        }
    }

    /**
     * {@code Math.hypot} as V8 computes it (max-scaled, Kahan-summed), not Java's correctly
     * rounded one: an arch ring's radial step is rounded to a block, and a last-bit difference
     * at a half block moves a voxel.
     */
    static double hypot(double... values) {
        double max = 0;
        for (double v : values) {
            double a = Math.abs(v);
            if (Double.isNaN(a)) return Double.NaN;
            if (a > max) max = a;
        }
        if (max == 0) return 0;
        if (Double.isInfinite(max)) return Double.POSITIVE_INFINITY;
        double sum = 0, compensation = 0;
        for (double v : values) {
            double n = Math.abs(v) / max;
            double summand = n * n - compensation;
            double preliminary = sum + summand;
            compensation = (preliminary - sum) - summand;
            sum = preliminary;
        }
        return Math.sqrt(sum) * max;
    }

    public static void rasterizeSweptArc(Geometry.SweptArc g, VoxelSink emit) {
        double dx = g.to().x() - g.from().x();
        double dz = g.to().z() - g.from().z();
        double span = hypot(dx, dz);
        if (span < 1e-6) return;
        double ux = dx / span, uz = dz / span;
        double nx = -uz, nz = ux;
        double springY = (g.from().y() + g.to().y()) / 2;
        double rise = g.crownY() - springY;
        if (rise <= 0) return;
        double midX = (g.from().x() + g.to().x()) / 2;
        double midZ = (g.from().z() + g.to().z()) / 2;
        double halfWidth = g.width() / 2;
        int depth = (int) Math.max(1, Js.round(g.depth()));
        Geometries.ArchSolution solution = Geometries.solveArch(span, rise);
        int steps = 2 * (int) Math.max(4, Math.ceil(span * 2));

        boolean alongX = Math.abs(dz) < 1e-9;
        boolean alongZ = Math.abs(dx) < 1e-9;
        boolean mirrored = alongX != alongZ && Js.isInteger(alongX ? 2 * midX : 2 * midZ);
        int last = mirrored ? steps / 2 : steps;
        VoxelSink sink;
        if (!mirrored) {
            sink = emit;
        } else if (alongX) {
            int mx2 = (int) (2 * midX);
            sink = (x, y, z) -> {
                emit.emit(x, y, z);
                emit.emit(mx2 - x, y, z);
            };
        } else {
            int mz2 = (int) (2 * midZ);
            sink = (x, y, z) -> {
                emit.emit(x, y, z);
                emit.emit(x, y, mz2 - z);
            };
        }

        Integer[] previousY = new Integer[depth];
        for (int i = 0; i <= last; i++) {
            double u = -span / 2 + (span * i) / steps;
            double h = Geometries.archHeight(u, span, rise);
            if (Double.isNaN(h)) continue;
            double centreU = solution.pointed() ? (u >= 0 ? -solution.centreOffset() : solution.centreOffset()) : 0;
            double radialU = u - centreU;
            double radialV = h - solution.centreY();
            double radialLength = hypot(radialU, radialV);
            if (radialLength == 0) radialLength = 1;
            double stepU = radialU / radialLength;
            double stepV = radialV / radialLength;
            for (int d = 0; d < depth; d++) {
                double ou = u + stepU * d;
                double ov = h + stepV * d;
                double cx = midX + ux * ou;
                double cz = midZ + uz * ou;
                int y = (int) Js.round(springY + ov);
                Integer previous = previousY[d];
                int from = previous == null ? y : Math.min(previous, y);
                int to = previous == null ? y : Math.max(previous, y);
                previousY[d] = y;
                for (double w = -halfWidth; w <= halfWidth; w += 0.5) {
                    int px = (int) Js.round(cx + nx * w);
                    int pz = (int) Js.round(cz + nz * w);
                    for (int py = from; py <= to; py++) sink.emit(px, py, pz);
                }
            }
        }
    }

    public static void rasterizeExtrude(Geometry.Extrude g, VoxelSink emit) {
        List<Vec3> path = g.path();
        if (path.size() < 2) return;
        List<Vec2> offsets = fillProfile(g.profile());
        if (offsets.isEmpty()) return;
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3 a = path.get(i), b = path.get(i + 1);
            double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
            double len = hypot(dx, dy, dz);
            if (len < 1e-6) continue;
            Vec3 tangent = new Vec3(dx / len, dy / len, dz / len);
            Vec3 worldUp = Math.abs(tangent.y()) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = normalise(cross(tangent, worldUp));
            Vec3 up = normalise(cross(right, tangent));
            int steps = (int) Math.max(1, Math.ceil(len * 4));
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                double px = a.x() + dx * t, py = a.y() + dy * t, pz = a.z() + dz * t;
                for (Vec2 o : offsets) {
                    emit.emit((int) Js.round(px + right.x() * o.x() + up.x() * o.z()),
                            (int) Js.round(py + right.y() * o.x() + up.y() * o.z()),
                            (int) Js.round(pz + right.z() * o.x() + up.z() * o.z()));
                }
            }
        }
    }

    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }

    private static Vec3 normalise(Vec3 v) {
        double l = hypot(v.x(), v.y(), v.z());
        return l < 1e-9 ? new Vec3(0, 0, 0) : new Vec3(v.x() / l, v.y() / l, v.z() / l);
    }

    /** The integer offsets inside a closed profile polygon (fewer than 3 points: the points). */
    public static List<Vec2> fillProfile(List<Vec2> profile) {
        List<Vec2> out = new ArrayList<>();
        if (profile.size() < 3) {
            for (Vec2 p : profile) out.add(new Vec2(p.x(), p.z()));
            return out;
        }
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vec2 p : profile) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
        }
        Set<String> seen = new HashSet<>();
        for (double z = Math.floor(minZ); z <= Math.ceil(maxZ); z += 0.5) {
            for (double x = Math.floor(minX); x <= Math.ceil(maxX); x += 0.5) {
                if (!pointInPolygon(x, z, profile)) continue;
                double rx = Js.round(x), rz = Js.round(z);
                if (!seen.add(rx + "," + rz)) continue;
                out.add(new Vec2(rx, rz));
            }
        }
        return out;
    }

    private static boolean pointInPolygon(double x, double z, List<Vec2> poly) {
        boolean inside = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            Vec2 a = poly.get(i), b = poly.get(j);
            if (a.z() > z != b.z() > z) {
                double cross = ((b.x() - a.x()) * (z - a.z())) / (b.z() - a.z()) + a.x();
                if (x < cross) inside = !inside;
            }
        }
        return inside;
    }
}
