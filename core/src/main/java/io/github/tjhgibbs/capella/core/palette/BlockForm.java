package io.github.tjhgibbs.capella.core.palette;

import java.util.Locale;

/** The shape a role is asked for; the palette picks the material. */
public enum BlockForm {
    BLOCK, SLAB, STAIRS, WALL, FENCE, PANE, TRAPDOOR, CARPET, LADDER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BlockForm of(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
