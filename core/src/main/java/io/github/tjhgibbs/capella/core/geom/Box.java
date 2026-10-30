package io.github.tjhgibbs.capella.core.geom;

import java.util.Collection;

/** Half-open box [min, max). Port of Capella's {@code types.ts} Box and its helpers. */
public record Box(Vec3 min, Vec3 max) {

    /** {@code box(x0..z1)}: corners in either order. */
    public static Box of(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new Box(new Vec3(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1)),
                new Vec3(Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1)));
    }

    public static Box at(Vec3 min, Vec3 size) {
        return new Box(min, new Vec3(min.x() + size.x(), min.y() + size.y(), min.z() + size.z()));
    }

    public Vec3 size() {
        return new Vec3(max.x() - min.x(), max.y() - min.y(), max.z() - min.z());
    }

    public double volume() {
        Vec3 s = size();
        return Math.max(0, s.x()) * Math.max(0, s.y()) * Math.max(0, s.z());
    }

    public boolean isEmpty() {
        return max.x() <= min.x() || max.y() <= min.y() || max.z() <= min.z();
    }

    public boolean contains(double x, double y, double z) {
        return x >= min.x() && x < max.x() && y >= min.y() && y < max.y() && z >= min.z() && z < max.z();
    }

    public boolean intersects(Box b) {
        return min.x() < b.max.x() && b.min.x() < max.x() && min.y() < b.max.y() && b.min.y() < max.y()
                && min.z() < b.max.z() && b.min.z() < max.z();
    }

    public Box intersection(Box b) {
        return new Box(new Vec3(Math.max(min.x(), b.min.x()), Math.max(min.y(), b.min.y()), Math.max(min.z(), b.min.z())),
                new Vec3(Math.min(max.x(), b.max.x()), Math.min(max.y(), b.max.y()), Math.min(max.z(), b.max.z())));
    }

    public Box union(Box b) {
        return new Box(new Vec3(Math.min(min.x(), b.min.x()), Math.min(min.y(), b.min.y()), Math.min(min.z(), b.min.z())),
                new Vec3(Math.max(max.x(), b.max.x()), Math.max(max.y(), b.max.y()), Math.max(max.z(), b.max.z())));
    }

    public Box expand(double n) {
        return new Box(new Vec3(min.x() - n, min.y() - n, min.z() - n), new Vec3(max.x() + n, max.y() + n, max.z() + n));
    }

    /** Union of the non-empty boxes, or null when there are none. */
    public static Box unionAll(Collection<Box> boxes) {
        Box acc = null;
        for (Box b : boxes) {
            if (b.isEmpty()) continue;
            acc = acc == null ? b : acc.union(b);
        }
        return acc;
    }
}
