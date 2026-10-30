package io.github.tjhgibbs.capella.core.layout;

/** A walkable storey as a band of feet heights, [floorY, headY]. */
public final class LevelBand {
    public final String name;
    public final double floorY;
    public final double headY;

    public LevelBand(String name, double floorY, double headY) {
        this.name = name;
        this.floorY = floorY;
        this.headY = headY;
    }
}
