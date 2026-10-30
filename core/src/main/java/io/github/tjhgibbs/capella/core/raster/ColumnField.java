package io.github.tjhgibbs.capella.core.raster;

/** A 2D field sampled at a point, e.g. weathering (0..255) at a block centre. */
@FunctionalInterface
public interface ColumnField {
    ColumnField ZERO = (x, z) -> 0;

    double sample(double x, double z);
}
