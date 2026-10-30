package io.github.tjhgibbs.capella.core.kit;

import java.util.Locale;

/** What finishes a tower: spire, saddleback roof, dome, or a flat parapet. */
public enum TowerCap {
    SPIRE, SADDLEBACK, DOME, FLAT;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TowerCap of(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
