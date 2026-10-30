package io.github.tjhgibbs.capella.core.kit;

import java.util.List;

/** Capella {@code kit/pier.ts}: a pier's footprint and the dressing (base and capital courses) it earns. */
public final class Pier {
    /** A pier narrower than this is a plain shaft: a base course on a 2x2 reads as a lump. */
    private static final int DRESSED_MIN_SIZE = 3;
    /** The least plain shaft left between base and capital. */
    private static final int SHAFT_MIN = 2;
    private static final int BASE_SECOND_COURSE = 12;
    private static final int CAP_SECOND_COURSE = 10;

    private Pier() {
    }

    public static final class PierFootprint {
        public final double x0, x1, z0, z1;

        public PierFootprint(double x0, double x1, double z0, double z1) {
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
        }
    }

    public static final class PierDressing {
        public final double baseHeight;
        public final double capHeight;

        public PierDressing(double baseHeight, double capHeight) {
            this.baseHeight = baseHeight;
            this.capHeight = capHeight;
        }
    }

    /** Null when the pier is too small or too short to carry a base and a capital. */
    public static PierDressing pierDressing(double clearHeight, double size) {
        if (size < DRESSED_MIN_SIZE) return null;
        double baseHeight = clearHeight >= BASE_SECOND_COURSE ? 2 : 1;
        double capHeight = clearHeight >= CAP_SECOND_COURSE ? 2 : 1;
        if (clearHeight - baseHeight - capHeight < SHAFT_MIN) return null;
        return new PierDressing(baseHeight, capHeight);
    }

    /** The footprint grown by one block each way: where the base and capital courses project. */
    public static PierFootprint pierRing(PierFootprint p) {
        return new PierFootprint(p.x0 - 1, p.x1 + 1, p.z0 - 1, p.z1 + 1);
    }

    public static boolean inFootprint(PierFootprint p, double x, double z) {
        return x >= p.x0 && x < p.x1 && z >= p.z0 && z < p.z1;
    }

    /** The ring's four corner columns, as {x, z}. */
    public static List<double[]> ringCorners(PierFootprint ring) {
        return List.of(
                new double[]{ring.x0, ring.z0},
                new double[]{ring.x1 - 1, ring.z0},
                new double[]{ring.x0, ring.z1 - 1},
                new double[]{ring.x1 - 1, ring.z1 - 1});
    }
}
