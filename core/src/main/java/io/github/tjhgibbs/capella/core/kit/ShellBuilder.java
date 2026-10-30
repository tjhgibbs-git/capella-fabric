package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.element.Element;
import io.github.tjhgibbs.capella.core.element.ElementBuilder;
import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Axis;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Dir;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where every builder adds its elements (Capella {@code kit/parts.ts}). Parts are set out in the
 * structure's own frame; {@link #frame} turns each one as it is added, including its
 * {@code facing} and {@code axis} params. A support naming an element not yet added is dropped.
 */
public final class ShellBuilder {
    public final String prefix;
    public final PlacementFrame frame;
    public final ElementBuilder builder;
    /** Ground rectangles claimed by the building, in world space. */
    public final List<Rect> footprints;
    private ShellBuilder worldView;

    public ShellBuilder() {
        this("", PlacementFrame.IDENTITY, null);
    }

    public ShellBuilder(String prefix, PlacementFrame frame) {
        this(prefix, frame, null);
    }

    public ShellBuilder(String prefix, PlacementFrame frame, ShellBuilder base) {
        this.prefix = prefix;
        this.frame = frame;
        this.builder = base != null ? base.builder : new ElementBuilder();
        this.footprints = base != null ? base.footprints : new ArrayList<>();
    }

    /** The same builder in world space (identity frame), for passes that work on placed geometry. */
    public ShellBuilder world() {
        if (frame.identity()) return this;
        if (worldView == null) worldView = new ShellBuilder(prefix, PlacementFrame.IDENTITY, this);
        return worldView;
    }

    public String qualify(String id) {
        return prefix + id;
    }

    /** Adds the element; returns its unqualified id, or null if its geometry rasterizes to nothing. */
    public String add(AddOptions o) {
        Geometry geometry = frame.geometry(o.geometry);
        Box bounds = Geometries.bounds(geometry);
        if (bounds.isEmpty()) return null;
        if (geometry instanceof Geometry.HeightField h) {
            boolean any = false;
            for (double v : h.heights()) {
                if (!Double.isNaN(v)) {
                    any = true;
                    break;
                }
            }
            if (!any) return null;
        }
        List<String> supports = new ArrayList<>();
        for (String s : o.supports) {
            String q = qualify(s);
            if (builder.has(q)) supports.add(q);
        }
        builder.add(new Element(qualify(o.id), o.kind, o.role, o.level, bounds, geometry,
                o.parentId == null ? null : qualify(o.parentId), List.copyOf(supports),
                o.params == null ? null : orient(o.params)));
        return o.id;
    }

    /** Shorthand for an element with no parent, supports or params. */
    public String add(String id, ElementKind kind, MaterialRole role, LevelName level, Geometry geometry) {
        return add(new AddOptions(id, kind, role, level, geometry));
    }

    private Map<String, Object> orient(Map<String, Object> params) {
        Map<String, Object> out = new LinkedHashMap<>(params);
        if (frame.identity()) return out;
        if (out.get("facing") instanceof String f) out.put("facing", frame.dir(Dir.of(f)).id());
        if (out.get("axis") instanceof String a) out.put("axis", frame.axis(Axis.of(a)).id());
        return out;
    }

    public boolean has(String id) {
        return builder.has(qualify(id));
    }

    public void ground(Rect rect) {
        footprints.add(frame.rect(rect));
    }
}
