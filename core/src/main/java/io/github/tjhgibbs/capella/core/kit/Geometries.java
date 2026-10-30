package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Vec2;
import io.github.tjhgibbs.capella.core.geom.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The functions of Capella's {@code kit/geometry.ts}. */
public final class Geometries {
    /** How much a fully pointed arch out-rises a semicircle: rise = halfSpan * (1 + gain * profile). */
    public static final double ARCH_POINT_GAIN = 2.5;

    private Geometries() {
    }

    public static Geometry solid(Box b) {
        return new Geometry.Solid(b);
    }

    private static Box pointBox(Vec3 p, double pad) {
        return Box.of(p.x() - pad, p.y() - pad, p.z() - pad, p.x() + pad + 1, p.y() + pad + 1, p.z() + pad + 1);
    }

    /** Conservative integer bounds of what the geometry can rasterize. */
    public static Box bounds(Geometry g) {
        return switch (g) {
            case Geometry.Solid s -> integerise(s.box());
            case Geometry.Shell s -> integerise(s.box());
            case Geometry.HeightField h -> {
                boolean shell = h.mode() == Geometry.HeightMode.SHELL;
                double top = shell ? Double.NEGATIVE_INFINITY : h.baseY();
                double bottom = shell ? Double.POSITIVE_INFINITY : h.baseY();
                for (double v : h.heights()) {
                    if (Double.isNaN(v)) continue;
                    if (v > top) top = v;
                    if (v < bottom) bottom = v;
                }
                if (!shell && h.bases() != null) {
                    for (double b : h.bases()) {
                        if (Double.isNaN(b)) continue;
                        if (b > top) top = b;
                        if (b < bottom) bottom = b;
                    }
                }
                if (Double.isInfinite(top)) {
                    yield Box.of(h.originX(), h.baseY(), h.originZ(), h.originX() + h.sizeX(), h.baseY(), h.originZ() + h.sizeZ());
                }
                if (shell) top += Math.max(1, Math.round(h.thickness() == null ? 1 : h.thickness())) - 1;
                yield Box.of(h.originX(), Math.floor(bottom), h.originZ(), h.originX() + h.sizeX(), Math.ceil(top) + 1, h.originZ() + h.sizeZ());
            }
            case Geometry.SweptArc a -> {
                double pad = Math.ceil(Math.max(a.width() / 2, a.depth())) + 1;
                Box ends = pointBox(a.from(), pad).union(pointBox(a.to(), pad));
                yield integerise(ends.union(Box.of(ends.min().x(), ends.min().y(), ends.min().z(), ends.max().x(), Math.ceil(a.crownY()) + pad + 1, ends.max().z())));
            }
            case Geometry.Extrude e -> {
                double reach = 1;
                for (Vec2 p : e.profile()) reach = Math.max(reach, Math.max(Math.abs(p.x()), Math.abs(p.z())));
                double pad = Math.ceil(reach) + 1;
                List<Box> boxes = new ArrayList<>();
                for (Vec3 p : e.path()) boxes.add(pointBox(p, pad));
                Box b = Box.unionAll(boxes);
                if (b == null) throw new IllegalStateException("extrude geometry has an empty path");
                yield integerise(b);
            }
        };
    }

    private static Box integerise(Box b) {
        return Box.of(Math.floor(b.min().x()), Math.floor(b.min().y()), Math.floor(b.min().z()),
                Math.ceil(b.max().x()), Math.ceil(b.max().y()), Math.ceil(b.max().z()));
    }

    public record ArchSolution(double radius, double centreOffset, double centreY, boolean pointed) {
    }

    /** Two-centred (pointed) when rise >= half span, otherwise a segmental arc. */
    public static ArchSolution solveArch(double span, double rise) {
        if (span <= 0) throw new IllegalArgumentException("solveArch: span must be positive, got " + span);
        if (rise <= 0) throw new IllegalArgumentException("solveArch: rise must be positive, got " + rise);
        double halfSpan = span / 2;
        if (rise >= halfSpan) {
            double centreOffset = (rise * rise - halfSpan * halfSpan) / (2 * halfSpan);
            return new ArchSolution(halfSpan + centreOffset, centreOffset, 0, true);
        }
        double radius = (halfSpan * halfSpan + rise * rise) / (2 * rise);
        return new ArchSolution(radius, 0, rise - radius, false);
    }

    /** Intrados height above the springing at offset {@code u} from the centre; NaN outside the span. */
    public static double archHeight(double u, double span, double rise) {
        double halfSpan = span / 2;
        if (Math.abs(u) > halfSpan) return Double.NaN;
        ArchSolution s = solveArch(span, rise);
        double dx = s.pointed() ? u - (u >= 0 ? -s.centreOffset() : s.centreOffset()) : u;
        double inside = s.radius() * s.radius() - dx * dx;
        return s.centreY() + (inside <= 0 ? 0 : Math.sqrt(inside));
    }

    public static double archRise(double span, double profile) {
        double p = Math.min(1, Math.max(0, profile));
        return (span / 2) * (1 + ARCH_POINT_GAIN * p);
    }

    public static double mirrorBlock(double k, double from, double to) {
        return from + to - 1 - k;
    }

    public record Span(double from, double to) {
    }

    public static Span centredSpan(double centre, double reach) {
        return new Span(Math.floor(centre - reach), Math.ceil(centre + reach));
    }

    public static List<Span> spans(double from, double to, double[] divisions) {
        double[] cuts = Arrays.stream(divisions).filter(d -> d > from && d < to).sorted().toArray();
        List<Span> out = new ArrayList<>();
        double cursor = from;
        for (double cut : cuts) {
            out.add(new Span(cursor, cut));
            cursor = cut;
        }
        out.add(new Span(cursor, to));
        return out;
    }

    public record CentredRun(List<Span> members, List<Span> gaps) {
    }

    public static CentredRun centredRun(int from, int to, int count) {
        return centredRun(from, to, count, 1);
    }

    /** {@code count} members of {@code memberWidth}, symmetric about the centre of [from, to). */
    public static CentredRun centredRun(int from, int to, int count, int memberWidth) {
        int width = to - from;
        if (count <= 0 || width <= 0) return new CentredRun(List.of(), List.of(new Span(from, to)));
        int[] widths = new int[count];
        Arrays.fill(widths, memberWidth);
        int middle = (count - 1) / 2;
        if (count % 2 == 1 && (((width - memberWidth) % 2) + 2) % 2 == 1) widths[middle] = memberWidth + 1;
        int gapCount = count + 1;
        int spare = width - Arrays.stream(widths).sum();
        int base = Math.floorDiv(spare, gapCount);
        int[] gapWidths = new int[gapCount];
        Arrays.fill(gapWidths, base);
        spare -= base * gapCount;
        for (int i = 0, j = gapCount - 1; spare >= 2 && i < j; i++, j--) {
            gapWidths[i]++;
            gapWidths[j]++;
            spare -= 2;
        }
        if (spare == 1) {
            if (gapCount % 2 == 1) gapWidths[(gapCount - 1) / 2]++;
            else widths[middle]++;
        }
        List<Span> members = new ArrayList<>();
        List<Span> gaps = new ArrayList<>();
        int edge = from;
        for (int i = 0; i < count; i++) {
            gaps.add(new Span(edge, edge + gapWidths[i]));
            edge += gapWidths[i];
            members.add(new Span(edge, edge + widths[i]));
            edge += widths[i];
        }
        gaps.add(new Span(edge, to));
        return new CentredRun(members, gaps);
    }

    public static double narrowestGap(CentredRun run) {
        double min = Double.POSITIVE_INFINITY;
        for (Span g : run.gaps()) min = Math.min(min, g.to() - g.from());
        return min;
    }
}
