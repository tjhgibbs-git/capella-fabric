package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.geom.Axis;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Dir;
import io.github.tjhgibbs.capella.core.geom.Vec2;
import io.github.tjhgibbs.capella.core.geom.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A quarter-turn about a world point (Capella {@code kit/frame.ts}). Builders set a structure
 * out along +X in its own frame; the frame turns each geometry as it is added.
 */
public abstract sealed class PlacementFrame permits PlacementFrame.Identity, PlacementFrame.Turn {
    public static final PlacementFrame IDENTITY = new Identity();

    public static PlacementFrame of(double originX, double originZ, double yaw) {
        int turns = (int) ((((Math.round(yaw / 90) % 4) + 4) % 4));
        if (turns == 0) return IDENTITY;
        return new Turn(turns, Math.round(originX + originZ), Math.round(originZ - originX));
    }

    public abstract int turns();

    public abstract boolean identity();

    public abstract Vec3 point(Vec3 p);

    /** A grid-line point (a block edge, not a block centre). */
    public abstract Vec2 edge(double x, double z);

    public abstract Box box(Box b);

    public abstract Rect rect(Rect r);

    public abstract Dir dir(Dir d);

    public abstract Axis axis(Axis a);

    public abstract Geometry geometry(Geometry g);

    public abstract PlacementFrame inverse();

    /** Wraps a world-space column reader so it can be called with frame coordinates. */
    public interface Sampler {
        double at(double x, double z);
    }

    public abstract Sampler sampler(Sampler read);

    static final class Identity extends PlacementFrame {
        public int turns() {
            return 0;
        }

        public boolean identity() {
            return true;
        }

        public Vec3 point(Vec3 p) {
            return p;
        }

        public Vec2 edge(double x, double z) {
            return new Vec2(x, z);
        }

        public Box box(Box b) {
            return b;
        }

        public Rect rect(Rect r) {
            return r;
        }

        public Dir dir(Dir d) {
            return d;
        }

        public Axis axis(Axis a) {
            return a;
        }

        public Geometry geometry(Geometry g) {
            return g;
        }

        public PlacementFrame inverse() {
            return this;
        }

        public Sampler sampler(Sampler read) {
            return read;
        }
    }

    static final class Turn extends PlacementFrame {
        final int turns;
        final double s, d;

        Turn(int turns, double s, double d) {
            this.turns = turns;
            this.s = s;
            this.d = d;
        }

        public int turns() {
            return turns;
        }

        public boolean identity() {
            return false;
        }

        private Vec2 continuous(double x, double z) {
            return switch (turns) {
                case 1 -> new Vec2(s - z, d + x);
                case 2 -> new Vec2(s - d - x, s + d - z);
                default -> new Vec2(z - d, s - x);
            };
        }

        public Vec3 point(Vec3 p) {
            Vec2 c = continuous(p.x() + 0.5, p.z() + 0.5);
            return new Vec3(c.x() - 0.5, p.y(), c.z() - 0.5);
        }

        public Vec2 edge(double x, double z) {
            return continuous(x, z);
        }

        public Box box(Box b) {
            Vec2 a = continuous(b.min().x(), b.min().z());
            Vec2 c = continuous(b.max().x(), b.max().z());
            return Box.of(a.x(), b.min().y(), a.z(), c.x(), b.max().y(), c.z());
        }

        public Rect rect(Rect r) {
            Vec2 a = continuous(r.x0(), r.z0());
            Vec2 b = continuous(r.x1(), r.z1());
            return new Rect(r.name(), Math.min(a.x(), b.x()), Math.max(a.x(), b.x()), Math.min(a.z(), b.z()), Math.max(a.z(), b.z()));
        }

        public Dir dir(Dir dd) {
            return dd.rotate(turns);
        }

        public Axis axis(Axis a) {
            if (a == Axis.Y || turns % 2 == 0) return a;
            return a == Axis.X ? Axis.Z : Axis.X;
        }

        public Sampler sampler(Sampler read) {
            return (x, z) -> {
                Vec2 p = continuous(x + 0.5, z + 0.5);
                return read.at(p.x() - 0.5, p.z() - 0.5);
            };
        }

        public PlacementFrame inverse() {
            return new Turn(4 - turns, s, d);
        }

        public Geometry geometry(Geometry g) {
            return switch (g) {
                case Geometry.Solid b -> new Geometry.Solid(box(b.box()));
                case Geometry.Shell sh -> new Geometry.Shell(box(sh.box()), sh.thickness(), faces(sh.faces()));
                case Geometry.HeightField h -> heightfield(h);
                case Geometry.SweptArc a -> arc(a);
                case Geometry.Extrude e -> {
                    List<Vec3> path = new ArrayList<>();
                    for (Vec3 p : e.path()) path.add(point(p));
                    yield new Geometry.Extrude(path, e.profile());
                }
            };
        }

        private Geometry.SweptArc arc(Geometry.SweptArc g) {
            Vec3 from = point(g.from());
            Vec3 to = point(g.to());
            Axis along = axisOf(g.from(), g.to());
            Axis turned = axisOf(from, to);
            if (along == null || turned == null) return new Geometry.SweptArc(from, to, g.crownY(), g.width(), g.depth());
            if (Math.signum(g.to().get(along) - g.from().get(along)) != Math.signum(to.get(turned) - from.get(turned))) {
                Vec3 t = from;
                from = to;
                to = t;
            }
            Axis across = turned == Axis.X ? Axis.Z : Axis.X;
            double edge = from.get(across) + g.width() / 2;
            if (edge == Math.rint(edge) || rises(across)) return new Geometry.SweptArc(from, to, g.crownY(), g.width(), g.depth());
            return new Geometry.SweptArc(from.with(across, from.get(across) - 1), to.with(across, to.get(across) - 1),
                    g.crownY(), g.width(), g.depth());
        }

        private boolean rises(Axis axis) {
            Vec2 a = continuous(0, 0);
            Vec2 b = continuous(1, 1);
            return axis == Axis.X ? b.x() > a.x() : b.z() > a.z();
        }

        private ShellFaces faces(ShellFaces f) {
            boolean[] out = new boolean[4]; // indexed by Dir ordinal
            out[dir(Dir.NORTH).ordinal()] = f.north();
            out[dir(Dir.EAST).ordinal()] = f.east();
            out[dir(Dir.SOUTH).ordinal()] = f.south();
            out[dir(Dir.WEST).ordinal()] = f.west();
            return new ShellFaces(f.top(), f.bottom(), out[0], out[2], out[1], out[3]);
        }

        private Geometry.HeightField heightfield(Geometry.HeightField g) {
            boolean odd = turns % 2 == 1;
            int sizeX = odd ? g.sizeZ() : g.sizeX();
            int sizeZ = odd ? g.sizeX() : g.sizeZ();
            Box corner = box(Box.of(g.originX(), 0, g.originZ(), g.originX() + g.sizeX(), 1, g.originZ() + g.sizeZ()));
            int n = g.sizeX() * g.sizeZ();
            double[] heights = new double[n];
            Arrays.fill(heights, Double.NaN);
            double[] bases = null;
            if (g.bases() != null) {
                bases = new double[n];
                Arrays.fill(bases, Double.NaN);
            }
            for (int iz = 0; iz < g.sizeZ(); iz++) {
                for (int ix = 0; ix < g.sizeX(); ix++) {
                    int ax, az;
                    switch (turns) {
                        case 1 -> {
                            ax = g.sizeZ() - 1 - iz;
                            az = ix;
                        }
                        case 2 -> {
                            ax = g.sizeX() - 1 - ix;
                            az = g.sizeZ() - 1 - iz;
                        }
                        default -> {
                            ax = iz;
                            az = g.sizeX() - 1 - ix;
                        }
                    }
                    int to = az * sizeX + ax;
                    heights[to] = g.heights()[iz * g.sizeX() + ix];
                    if (bases != null) bases[to] = g.bases()[iz * g.sizeX() + ix];
                }
            }
            return new Geometry.HeightField(corner.min().x(), corner.min().z(), sizeX, sizeZ, g.baseY(), g.mode(),
                    g.thickness(), heights, bases);
        }

        private static Axis axisOf(Vec3 a, Vec3 b) {
            boolean flatX = Math.abs(b.z() - a.z()) < 1e-9;
            boolean flatZ = Math.abs(b.x() - a.x()) < 1e-9;
            if (flatX == flatZ) return null;
            return flatX ? Axis.X : Axis.Z;
        }
    }
}
