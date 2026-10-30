package io.github.tjhgibbs.capella.core.element;

import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.kit.Geometry;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;

import java.util.List;
import java.util.Map;

/**
 * A typed architectural part with exact metric geometry. {@code level} and {@code parentId} may
 * be null; {@code params} may be null and otherwise holds Strings, Numbers and Booleans
 * ({@code form}, {@code facing}, {@code axis}, {@code top}, {@code hanging} are read by the rasterizer).
 */
public record Element(String id, ElementKind kind, MaterialRole role, LevelName level, Box bounds, Geometry geometry,
                      String parentId, List<String> supports, Map<String, Object> params) {

    public Object param(String key) {
        return params == null ? null : params.get(key);
    }
}
