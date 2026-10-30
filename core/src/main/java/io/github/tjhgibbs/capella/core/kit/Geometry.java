package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Vec2;
import io.github.tjhgibbs.capella.core.geom.Vec3;

import java.util.List;

/**
 * An element's exact metric shape (Capella {@code kit/geometry.ts}). The rasterizer turns each
 * kind into voxels; see {@code raster.Primitives}.
 */
public sealed interface Geometry permits Geometry.Solid, Geometry.Shell, Geometry.HeightField, Geometry.SweptArc, Geometry.Extrude {

    String kind();

    /** {@code kind: 'box'}: every voxel whose centre is inside. */
    record Solid(Box box) implements Geometry {
        public String kind() {
            return "box";
        }
    }

    /** {@code kind: 'shell'}: the faces of a box, {@code thickness} deep. */
    record Shell(Box box, double thickness, ShellFaces faces) implements Geometry {
        public String kind() {
            return "shell";
        }
    }

    enum HeightMode {
        SOLID, SURFACE, SHELL;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * {@code kind: 'heightfield'}: a grid of column tops, row-major {@code iz * sizeX + ix};
     * NaN skips a column. {@code bases} (optional, may be null) gives per-column bottoms;
     * {@code thickness} (optional, may be null) applies to {@code SHELL} mode.
     */
    record HeightField(double originX, double originZ, int sizeX, int sizeZ, double baseY, HeightMode mode,
                       Double thickness, double[] heights, double[] bases) implements Geometry {
        public String kind() {
            return "heightfield";
        }

        public static HeightField solid(double originX, double originZ, int sizeX, int sizeZ, double baseY, double[] heights) {
            return new HeightField(originX, originZ, sizeX, sizeZ, baseY, HeightMode.SOLID, null, heights, null);
        }

        public HeightField withHeights(double[] h, double[] b) {
            return new HeightField(originX, originZ, sizeX, sizeZ, baseY, mode, thickness, h, b);
        }
    }

    /** {@code kind: 'swept-arc'}: an arch ring from {@code from} to {@code to} rising to {@code crownY}. */
    record SweptArc(Vec3 from, Vec3 to, double crownY, double width, double depth) implements Geometry {
        public String kind() {
            return "swept-arc";
        }
    }

    /** {@code kind: 'extrude'}: a 2D profile swept along a polyline. */
    record Extrude(List<Vec3> path, List<Vec2> profile) implements Geometry {
        public String kind() {
            return "extrude";
        }
    }
}
