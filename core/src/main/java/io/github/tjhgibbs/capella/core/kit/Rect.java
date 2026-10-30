package io.github.tjhgibbs.capella.core.kit;

/** A named ground rectangle [x0, x1) x [z0, z1). */
public record Rect(String name, double x0, double x1, double z0, double z1) {
}
