package io.github.tjhgibbs.capella.core.terrain;

/** The ground (or any column value) at a block column. */
@FunctionalInterface
public interface GroundSampler {
    double at(double x, double z);
}
