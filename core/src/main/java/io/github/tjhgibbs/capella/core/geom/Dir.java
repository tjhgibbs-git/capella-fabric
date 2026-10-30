package io.github.tjhgibbs.capella.core.geom;

import java.util.List;
import java.util.Locale;

/** Capella's {@code Dir6}. The first four are {@code Dir}, the horizontals. */
public enum Dir {
    NORTH(0, 0, -1), EAST(1, 0, 0), SOUTH(0, 0, 1), WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0);

    public static final List<Dir> HORIZONTALS = List.of(NORTH, EAST, SOUTH, WEST);

    public final int dx, dy, dz;

    Dir(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Dir of(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }

    public boolean horizontal() {
        return ordinal() < 4;
    }

    /** Quarter turns clockwise seen from above; UP and DOWN are fixed. */
    public Dir rotate(int quarterTurns) {
        if (!horizontal()) return this;
        int t = ((quarterTurns % 4) + 4) % 4;
        return values()[(ordinal() + t) % 4];
    }

    public Dir clockWise() {
        return rotate(1);
    }

    public Dir counterClockWise() {
        return rotate(3);
    }

    public Dir opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            default -> rotate(2);
        };
    }

    /** Mirror across the plane normal to {@code axis} (x flips east/west, z flips north/south). */
    public Dir mirror(Axis axis) {
        if (axis == Axis.X) return this == EAST ? WEST : this == WEST ? EAST : this;
        if (axis == Axis.Z) return this == NORTH ? SOUTH : this == SOUTH ? NORTH : this;
        return this;
    }
}
