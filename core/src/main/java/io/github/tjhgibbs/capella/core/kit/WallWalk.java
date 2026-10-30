package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;

import java.util.List;
import java.util.function.Function;

/**
 * A passage cut along the inside of a thick wall, and the flight that reaches it (Capella
 * {@code kit/wall-walk.ts}). The cathedral's mural passage is one; a castle's wall walk was another.
 */
public final class WallWalk {
    /** Blocks of wall left outboard of the passage. */
    public static final double OUTER_SKIN = 1;
    /** Narrower than this and the passage is not cut. */
    public static final double MIN_PASSAGE_WIDTH = 2;

    private WallWalk() {
    }

    public static final class WalkBay {
        public final int index;
        public final double x0, x1;

        public WalkBay(int index, double x0, double x1) {
            this.index = index;
            this.x0 = x0;
            this.x1 = x1;
        }
    }

    public static final class WallWalkRequest {
        public final String id;
        public final List<WalkBay> bays;
        public final double x0, x1;
        /** The wall face the passage opens onto. */
        public final double faceZ;
        /** -1 or +1: which way into the wall from {@code faceZ}. */
        public final int inward;
        public final double thickness;
        public final double floorY, headY;
        public final LevelName level;
        public final Function<WalkBay, String> wallOf;
        public final List<String> carriedBy;

        public WallWalkRequest(String id, List<WalkBay> bays, double x0, double x1, double faceZ, int inward,
                               double thickness, double floorY, double headY, LevelName level,
                               Function<WalkBay, String> wallOf, List<String> carriedBy) {
            this.id = id;
            this.bays = bays;
            this.x0 = x0;
            this.x1 = x1;
            this.faceZ = faceZ;
            this.inward = inward;
            this.thickness = thickness;
            this.floorY = floorY;
            this.headY = headY;
            this.level = level;
            this.wallOf = wallOf;
            this.carriedBy = carriedBy;
        }
    }

    /** Cuts the passage bay by bay, with a corbel course and colonnettes on its open face. */
    public static boolean cutWallWalk(ShellBuilder s, WallWalkRequest o) {
        double depth = o.thickness - OUTER_SKIN;
        if (depth < MIN_PASSAGE_WIDTH) return false;
        double near = o.faceZ;
        double far = o.faceZ + o.inward * depth;
        double cz0 = Math.min(near, far), cz1 = Math.max(near, far);

        for (WalkBay bay : o.bays) {
            s.add(new AddOptions("aperture/" + o.id + "/bay-" + bay.index, ElementKind.APERTURE, MaterialRole.MASONRY_MAIN,
                    o.level, Parts.solid(Box.of(bay.x0, o.floorY, cz0, bay.x1, o.headY, cz1)))
                    .parent(o.wallOf.apply(bay)).param("symmetry", "x"));
        }

        double proud = o.faceZ + (o.inward < 0 ? 0 : -1);
        s.add(new AddOptions("wall/" + o.id + "/corbel", ElementKind.WALL, MaterialRole.MASONRY_TRIM, o.level,
                Parts.solid(Box.of(o.x0, o.floorY - 1, proud, o.x1, o.floorY + 1, proud + 1))).supports(o.carriedBy));

        for (WalkBay bay : o.bays) {
            s.add(new AddOptions("wall/" + o.id + "/colonnette-" + bay.index, ElementKind.WALL, MaterialRole.MASONRY_FINE,
                    o.level, Parts.solid(Box.of(bay.x0, o.floorY + 1, proud, bay.x0 + 1, o.headY + 1, proud + 1)))
                    .supports("wall/" + o.id + "/corbel"));
        }
        return true;
    }

    /** A straight flight of one-block risers running +X from {@code x0}, {@code depth} wide in Z. */
    public static void wallWalkFlight(ShellBuilder s, String id, double x0, double rise, double baseY, double z0,
                                      int depth, LevelName level, List<String> supports) {
        if (rise < 1) return;
        int sizeX = (int) rise;
        double[] heights = new double[sizeX * depth];
        for (int iz = 0; iz < depth; iz++) {
            for (int ix = 0; ix < sizeX; ix++) heights[iz * sizeX + ix] = baseY + ix;
        }
        s.add(new AddOptions(id, ElementKind.STAIR, MaterialRole.MASONRY_TRIM, level,
                Geometry.HeightField.solid(x0, z0, sizeX, depth, baseY - 1, heights)).supports(supports));
    }
}
