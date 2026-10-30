package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.kit.Band;

import java.util.List;

/** One side of the section, outward from the nave. */
public final class SideBands {
    /** {@code arcades[0]} divides nave from aisle 1; {@code arcades[n]} aisle n from aisle n+1. */
    public final List<Band> arcades;
    public final List<Band> aisles;
    public final Band outerWall;
    /** Outer face of the outer wall. */
    public final double outerZ;

    public SideBands(List<Band> arcades, List<Band> aisles, Band outerWall, double outerZ) {
        this.arcades = arcades;
        this.aisles = aisles;
        this.outerWall = outerWall;
        this.outerZ = outerZ;
    }
}
