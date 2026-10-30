package io.github.tjhgibbs.capella.core.layout;

import io.github.tjhgibbs.capella.core.geom.Dir;
import io.github.tjhgibbs.capella.core.geom.Vec2;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.PlacementFrame;
import io.github.tjhgibbs.capella.core.kit.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a type-agnostic pass may ask of a building (Capella {@code structure-layout.ts}):
 * where it stands, its doors, its storeys and its datum. Nothing gothic.
 */
public final class StructureLayout {
    public final String id;
    /** "cathedral": the only type this port builds. */
    public final String type;
    /** What it stands on. */
    public final List<Rect> rects;
    /** What it occupies. */
    public final List<Rect> extent;
    public final List<Portal> portals;
    public final List<LevelBand> levels;
    public final double datumY;
    public final FaceAnchors anchors;
    public final String groundNote;
    /** "natural", "floating" or "shell". */
    public final String ground;
    /** Precinct width, or null for the earthwork default. */
    public final Integer precinct;
    /** Plinth rise, or null for the terrain default. */
    public final Integer plinthRise;

    public StructureLayout(String id, String type, List<Rect> rects, List<Rect> extent, List<Portal> portals,
                           List<LevelBand> levels, double datumY, FaceAnchors anchors, String groundNote, String ground,
                           Integer precinct, Integer plinthRise) {
        this.id = id;
        this.type = type;
        this.rects = rects;
        this.extent = extent;
        this.portals = portals;
        this.levels = levels;
        this.datumY = datumY;
        this.anchors = anchors;
        this.groundNote = groundNote;
        this.ground = ground;
        this.precinct = precinct;
        this.plinthRise = plinthRise;
    }

    /** The layout turned into world space by its frame. */
    public static StructureLayout placeLayout(StructureLayout layout, PlacementFrame frame) {
        if (frame.identity()) return layout;
        List<Rect> rects = layout.rects.stream().map(frame::rect).toList();
        List<Rect> extent = layout.extent.stream().map(frame::rect).toList();
        List<Portal> portals = layout.portals.stream().map(p -> turnPortal(p, frame)).toList();
        return new StructureLayout(layout.id, layout.type, rects, extent, portals, layout.levels, layout.datumY,
                turnAnchors(layout.anchors, frame), layout.groundNote, layout.ground, layout.precinct, layout.plinthRise);
    }

    private static boolean eastWest(Dir d) {
        return d == Dir.EAST || d == Dir.WEST;
    }

    private static Portal turnPortal(Portal p, PlacementFrame frame) {
        Dir facing = frame.dir(p.facing);
        boolean acrossX = !eastWest(p.facing);
        boolean turnedX = !eastWest(facing);
        double low = (acrossX ? p.position.x() : p.position.z()) - Math.floor(p.width / 2);
        java.util.function.DoubleUnaryOperator edge = v -> {
            Vec2 e = frame.edge(acrossX ? v : 0, acrossX ? 0 : v);
            return turnedX ? e.x() : e.z();
        };
        Vec3 point = frame.point(p.position);
        double value = Math.min(edge.applyAsDouble(low), edge.applyAsDouble(low + p.width)) + Math.floor(p.width / 2);
        Vec3 position = turnedX ? new Vec3(value, point.y(), point.z()) : new Vec3(point.x(), point.y(), value);
        return p.with(position, facing);
    }

    private static FaceAnchors turnAnchors(FaceAnchors anchors, PlacementFrame frame) {
        boolean odd = frame.turns() % 2 == 1;
        List<Double> stripX = new ArrayList<>();
        List<Double> stripZ = new ArrayList<>();
        if (odd) {
            for (double v : anchors.stripZ) stripX.add(frame.point(new Vec3(0, 0, v)).x());
            for (double v : anchors.stripX) stripZ.add(frame.point(new Vec3(v, 0, 0)).z());
        } else {
            for (double v : anchors.stripX) stripX.add(frame.point(new Vec3(v, 0, 0)).x());
            for (double v : anchors.stripZ) stripZ.add(frame.point(new Vec3(0, 0, v)).z());
        }
        stripX.sort(Double::compare);
        stripZ.sort(Double::compare);
        return new FaceAnchors(anchors.courseY, stripX, stripZ);
    }

    public static boolean inFootprint(StructureLayout layout, double x, double z) {
        for (Rect r : layout.extent) {
            if (x >= r.x0() && x < r.x1() && z >= r.z0() && z < r.z1()) return true;
        }
        return false;
    }
}
