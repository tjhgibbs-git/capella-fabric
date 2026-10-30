package io.github.tjhgibbs.capella.core.geom;

import java.util.Locale;

public enum Axis {
    X, Y, Z;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Axis of(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
