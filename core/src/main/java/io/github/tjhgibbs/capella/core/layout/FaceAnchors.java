package io.github.tjhgibbs.capella.core.layout;

import java.util.List;

/** Course heights and strip positions a facade's rhythm is set out on. */
public final class FaceAnchors {
    public final List<Double> courseY;
    public final List<Double> stripX;
    public final List<Double> stripZ;

    public FaceAnchors(List<Double> courseY, List<Double> stripX, List<Double> stripZ) {
        this.courseY = courseY;
        this.stripX = stripX;
        this.stripZ = stripZ;
    }
}
