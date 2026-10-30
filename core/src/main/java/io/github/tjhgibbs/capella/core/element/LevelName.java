package io.github.tjhgibbs.capella.core.element;

import java.util.Locale;

/** Which walkable storey an element belongs to. */
public enum LevelName {
    CRYPT, GROUND, UPPER, TRIFORIUM, MURAL, ATTIC, TOWER, ROOF;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
