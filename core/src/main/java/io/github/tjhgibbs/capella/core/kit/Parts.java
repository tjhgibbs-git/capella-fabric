package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.geom.Axis;
import io.github.tjhgibbs.capella.core.geom.Box;

import java.util.Arrays;

/** The free functions of Capella's {@code kit/parts.ts}. */
public final class Parts {
    private Parts() {
    }

    public static Geometry solid(Box b) {
        return new Geometry.Solid(b);
    }

    /**
     * The air of an arched opening through a wall band: a heightfield whose column tops follow
     * the intrados. {@code along} is the axis the opening's span runs along (X or Z).
     */
    public static Geometry.HeightField archedOpening(Axis along, double from, double to, double bandFrom, double bandTo,
                                                     double sillY, double springY, double crownY) {
        double span = to - from;
        double rise = crownY - springY;
        double centre = (from + to) / 2;
        double across = bandTo - bandFrom;
        boolean x = along == Axis.X;
        int sizeX = (int) (x ? span : across);
        int sizeZ = (int) (x ? across : span);
        double originX = x ? from : bandFrom;
        double originZ = x ? bandFrom : from;
        double[] heights = new double[Math.max(0, sizeX * sizeZ)];
        Arrays.fill(heights, Double.NaN);
        if (span <= 0 || across <= 0 || rise <= 0) {
            return Geometry.HeightField.solid(originX, originZ, sizeX, sizeZ, sillY, heights);
        }
        for (int i = 0; i < span; i++) {
            double u = from + i + 0.5 - centre;
            double h = Geometries.archHeight(u, span, rise);
            if (Double.isNaN(h)) continue;
            double top = Math.round(springY + h) - 1;
            if (top < sillY) continue;
            for (int j = 0; j < across; j++) {
                int ix = x ? i : j;
                int iz = x ? j : i;
                heights[iz * sizeX + ix] = top;
            }
        }
        return Geometry.HeightField.solid(originX, originZ, sizeX, sizeZ, sillY, heights);
    }

    /** A floor slab whose walking surface is {@code surfaceY}, filled down to {@code baseY - 1}. */
    public static Geometry.HeightField floorSlab(double x0, double x1, double z0, double z1, double baseY, double surfaceY) {
        int sizeX = (int) (x1 - x0);
        int sizeZ = (int) (z1 - z0);
        double[] heights = new double[Math.max(0, sizeX * sizeZ)];
        Arrays.fill(heights, surfaceY - 1);
        return Geometry.HeightField.solid(x0, z0, sizeX, sizeZ, baseY - 1, heights);
    }

    public static Geometry.HeightField floorSlab(Rect r, double baseY, double surfaceY) {
        return floorSlab(r.x0(), r.x1(), r.z0(), r.z1(), baseY, surfaceY);
    }

    public record Sweep(double centre, double width) {
    }

    /** The centre line and width of a swept arch ring filling a band. */
    public static Sweep bandSweep(Band band) {
        return new Sweep((band.z0() + band.z1() - 1) / 2, Math.max(0, band.z1() - band.z0() - 1));
    }
}
