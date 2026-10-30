package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The object literal passed to Capella's {@code ShellBuilder.add}. Required fields in the
 * constructor; {@code parentId}, {@code supports} and {@code params} are fluent.
 */
public final class AddOptions {
    public final String id;
    public final ElementKind kind;
    public final MaterialRole role;
    public final LevelName level;
    public final Geometry geometry;
    public String parentId;
    public List<String> supports = new ArrayList<>();
    public Map<String, Object> params;

    public AddOptions(String id, ElementKind kind, MaterialRole role, LevelName level, Geometry geometry) {
        this.id = id;
        this.kind = kind;
        this.role = role;
        this.level = level;
        this.geometry = geometry;
    }

    public AddOptions parent(String parentId) {
        this.parentId = parentId;
        return this;
    }

    public AddOptions supports(String... ids) {
        this.supports = new ArrayList<>(Arrays.asList(ids));
        return this;
    }

    public AddOptions supports(List<String> ids) {
        this.supports = new ArrayList<>(ids);
        return this;
    }

    /** Adds one param; keeps insertion order (which is irrelevant to the rasterizer). */
    public AddOptions param(String key, Object value) {
        if (params == null) params = new LinkedHashMap<>();
        params.put(key, value);
        return this;
    }

    public AddOptions params(Map<String, Object> p) {
        if (p != null) {
            if (params == null) params = new LinkedHashMap<>();
            params.putAll(p);
        }
        return this;
    }
}
