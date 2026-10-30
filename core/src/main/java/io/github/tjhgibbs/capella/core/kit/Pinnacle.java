package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Capella {@code kit/pinnacle.ts}: a square shaft finished with a pyramidal spirelet. */
public final class Pinnacle {
    private Pinnacle() {
    }

    /** The options object of {@code buildPinnacle}. {@code supports} and {@code parentId} are optional. */
    public static final class Options {
        public final String id;
        public final double x0, x1, z0, z1, baseY, height;
        public final LevelName level;
        public List<String> supports;
        public String parentId;

        public Options(String id, double x0, double x1, double z0, double z1, double baseY, double height, LevelName level) {
            this.id = id;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.baseY = baseY;
            this.height = height;
            this.level = level;
        }

        public Options supports(List<String> s) {
            this.supports = s;
            return this;
        }

        public Options supports(String... s) {
            this.supports = new ArrayList<>(Arrays.asList(s));
            return this;
        }

        public Options parent(String p) {
            this.parentId = p;
            return this;
        }
    }

    public static void buildPinnacle(ShellBuilder s, Options o) {
        double sizeXd = o.x1 - o.x0;
        double sizeZd = o.z1 - o.z0;
        if (sizeXd < 2 || sizeZd < 2 || o.height < 4) return;
        int sizeX = (int) sizeXd;
        int sizeZ = (int) sizeZd;
        double shaftTop = o.baseY + Math.max(2, Js.round(o.height * 0.45));

        s.add(new AddOptions(o.id + "/shaft", ElementKind.PINNACLE, MaterialRole.MASONRY_TRIM, o.level,
                Parts.solid(Box.of(o.x0, o.baseY, o.z0, o.x1, shaftTop, o.z1)))
                .supports(o.supports == null ? List.of() : o.supports)
                .parent(o.parentId));

        double cx = (o.x0 + o.x1) / 2;
        double cz = (o.z0 + o.z1) / 2;
        double half = Math.min(sizeXd, sizeZd) / 2 - 0.5;
        double pitch = (o.baseY + o.height - shaftTop) / Math.max(0.5, half);
        double[] heights = new double[sizeX * sizeZ];
        Arrays.fill(heights, Double.NaN);
        for (int iz = 0; iz < sizeZ; iz++) {
            for (int ix = 0; ix < sizeX; ix++) {
                double inset = Math.min(
                        sizeXd / 2 - Math.abs(o.x0 + ix + 0.5 - cx),
                        sizeZd / 2 - Math.abs(o.z0 + iz + 0.5 - cz));
                heights[iz * sizeX + ix] = shaftTop - 1 + Js.round(Math.max(0, inset - 0.5) * pitch);
            }
        }
        s.add(new AddOptions(o.id + "/spirelet", ElementKind.PINNACLE, MaterialRole.MASONRY_FINE, o.level,
                new Geometry.HeightField(o.x0, o.z0, sizeX, sizeZ, shaftTop, Geometry.HeightMode.SHELL, 1.0, heights, null))
                .parent(o.id + "/shaft")
                .supports(o.id + "/shaft"));
    }
}
