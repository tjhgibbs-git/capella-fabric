package io.github.tjhgibbs.capella.core.palette;

import java.util.List;

/** A role's materials, and optionally those substituted in as weathering rises (may be null). */
public final class RoleEntry {
    public final List<Material> materials;
    public final List<Material> weathered;

    public RoleEntry(List<Material> materials, List<Material> weathered) {
        this.materials = List.copyOf(materials);
        this.weathered = weathered == null ? null : List.copyOf(weathered);
    }

    public RoleEntry(List<Material> materials) {
        this(materials, null);
    }
}
