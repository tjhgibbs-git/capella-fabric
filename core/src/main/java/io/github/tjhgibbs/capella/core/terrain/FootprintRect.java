package io.github.tjhgibbs.capella.core.terrain;

import io.github.tjhgibbs.capella.core.kit.Rect;

/** An unnamed ground rectangle [x0, x1) x [z0, z1). */
public record FootprintRect(double x0, double x1, double z0, double z1) {
    public static FootprintRect of(Rect r) {
        return new FootprintRect(r.x0(), r.x1(), r.z0(), r.z1());
    }
}
