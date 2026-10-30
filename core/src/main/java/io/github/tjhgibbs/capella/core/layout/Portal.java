package io.github.tjhgibbs.capella.core.layout;

import io.github.tjhgibbs.capella.core.geom.Dir;
import io.github.tjhgibbs.capella.core.geom.Vec3;

import java.util.Locale;

/**
 * A typed connection point: a door, with the floor level outside it ({@code threshold}).
 * {@code position} is the centre of the opening on the wall's outer face; {@code at} is the
 * crossing index for a transept door and null otherwise.
 */
public final class Portal {
    public enum Kind {
        WEST_GREAT, TRANSEPT, CLOISTER, CRYPT, TOWER, SERVICE, GATE, POSTERN, DOOR;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public final Vec3 position;
    public final Dir facing;
    public final double width;
    public final double height;
    public final double threshold;
    public final Kind kind;
    public final Integer at;

    public Portal(Vec3 position, Dir facing, double width, double height, double threshold, Kind kind, Integer at) {
        this.position = position;
        this.facing = facing;
        this.width = width;
        this.height = height;
        this.threshold = threshold;
        this.kind = kind;
        this.at = at;
    }

    public Portal with(Vec3 position, Dir facing) {
        return new Portal(position, facing, width, height, threshold, kind, at);
    }
}
