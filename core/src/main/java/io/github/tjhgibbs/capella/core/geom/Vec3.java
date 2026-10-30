package io.github.tjhgibbs.capella.core.geom;

/** A point or offset in block space. Doubles, as in Capella: a centre line can sit on a half block. */
public record Vec3(double x, double y, double z) {
    public static Vec3 of(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    public double get(Axis a) {
        return switch (a) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    public Vec3 with(Axis a, double v) {
        return switch (a) {
            case X -> new Vec3(v, y, z);
            case Y -> new Vec3(x, v, z);
            case Z -> new Vec3(x, y, v);
        };
    }
}
