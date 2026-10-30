package io.github.tjhgibbs.capella.core.debug;

import io.github.tjhgibbs.capella.core.element.Element;
import io.github.tjhgibbs.capella.core.element.ElementTree;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Vec2;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.Geometry;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes an element tree as the same JSON Capella's {@code ElementTree.toJSON()} produces, so
 * {@code tools/oracle/compare.py} can diff the port against the TypeScript element by element.
 */
public final class ElementJson {
    private ElementJson() {
    }

    public static String write(ElementTree tree) {
        StringBuilder sb = new StringBuilder("{\"version\":1,\"elements\":[");
        boolean first = true;
        for (Element e : tree.elements()) {
            if (!first) sb.append(',');
            first = false;
            element(sb, e);
        }
        return sb.append("]}").toString();
    }

    private static void element(StringBuilder sb, Element e) {
        sb.append("{\"id\":").append(str(e.id()))
                .append(",\"kind\":").append(str(e.kind().id()))
                .append(",\"role\":").append(str(e.role().name()))
                .append(",\"level\":").append(e.level() == null ? "null" : str(e.level().id()))
                .append(",\"bounds\":");
        box(sb, e.bounds());
        sb.append(",\"geometry\":");
        geometry(sb, e.geometry());
        sb.append(",\"parentId\":").append(e.parentId() == null ? "null" : str(e.parentId()));
        sb.append(",\"supports\":[");
        for (int i = 0; i < e.supports().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(str(e.supports().get(i)));
        }
        sb.append(']');
        if (e.params() != null) {
            sb.append(",\"params\":{");
            boolean first = true;
            for (Map.Entry<String, Object> p : new TreeMap<>(e.params()).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(str(p.getKey())).append(':');
                Object v = p.getValue();
                if (v instanceof Number n) sb.append(num(n.doubleValue()));
                else if (v instanceof Boolean b) sb.append(b);
                else sb.append(str(String.valueOf(v)));
            }
            sb.append('}');
        }
        sb.append('}');
    }

    private static void geometry(StringBuilder sb, Geometry g) {
        sb.append("{\"kind\":").append(str(g.kind()));
        switch (g) {
            case Geometry.Solid s -> {
                sb.append(",\"box\":");
                box(sb, s.box());
            }
            case Geometry.Shell s -> {
                sb.append(",\"box\":");
                box(sb, s.box());
                sb.append(",\"thickness\":").append(num(s.thickness()));
                var f = s.faces();
                sb.append(",\"faces\":{\"top\":").append(f.top()).append(",\"bottom\":").append(f.bottom())
                        .append(",\"north\":").append(f.north()).append(",\"south\":").append(f.south())
                        .append(",\"east\":").append(f.east()).append(",\"west\":").append(f.west()).append('}');
            }
            case Geometry.HeightField h -> {
                sb.append(",\"originX\":").append(num(h.originX())).append(",\"originZ\":").append(num(h.originZ()))
                        .append(",\"sizeX\":").append(h.sizeX()).append(",\"sizeZ\":").append(h.sizeZ())
                        .append(",\"baseY\":").append(num(h.baseY())).append(",\"mode\":").append(str(h.mode().id()));
                if (h.thickness() != null) sb.append(",\"thickness\":").append(num(h.thickness()));
                sb.append(",\"heights\":");
                array(sb, h.heights());
                if (h.bases() != null) {
                    sb.append(",\"bases\":");
                    array(sb, h.bases());
                }
            }
            case Geometry.SweptArc a -> {
                sb.append(",\"from\":");
                vec(sb, a.from());
                sb.append(",\"to\":");
                vec(sb, a.to());
                sb.append(",\"crownY\":").append(num(a.crownY())).append(",\"width\":").append(num(a.width()))
                        .append(",\"depth\":").append(num(a.depth()));
            }
            case Geometry.Extrude x -> {
                sb.append(",\"path\":[");
                List<Vec3> path = x.path();
                for (int i = 0; i < path.size(); i++) {
                    if (i > 0) sb.append(',');
                    vec(sb, path.get(i));
                }
                sb.append("],\"profile\":[");
                List<Vec2> profile = x.profile();
                for (int i = 0; i < profile.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append("{\"x\":").append(num(profile.get(i).x())).append(",\"z\":").append(num(profile.get(i).z())).append('}');
                }
                sb.append(']');
            }
        }
        sb.append('}');
    }

    private static void array(StringBuilder sb, double[] a) {
        sb.append('[');
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(num(a[i]));
        }
        sb.append(']');
    }

    private static void box(StringBuilder sb, Box b) {
        sb.append("{\"min\":");
        vec(sb, b.min());
        sb.append(",\"max\":");
        vec(sb, b.max());
        sb.append('}');
    }

    private static void vec(StringBuilder sb, Vec3 v) {
        sb.append("{\"x\":").append(num(v.x())).append(",\"y\":").append(num(v.y())).append(",\"z\":").append(num(v.z())).append('}');
    }

    /** JSON.stringify: NaN and infinities become null. */
    private static String num(double v) {
        return Double.isFinite(v) ? Js.str(v) : "null";
    }

    private static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        return sb.append('"').toString();
    }
}
