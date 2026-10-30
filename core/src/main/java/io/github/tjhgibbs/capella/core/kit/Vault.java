package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Vaults: ribs, webs, the extrados and the attic that sits on it (Capella {@code kit/vault.ts}).
 *
 * <p>A vault cell is a rectangle bounded by four arches struck to one springing and one crown. The
 * web is the <b>union of two barrel vaults</b>, so the intrados is
 * {@code springY + max(A(dz), B(dx))}: at a bay boundary the web <em>is</em> the transverse arch,
 * along a wall it <em>is</em> the wall arch, so ribs and web cannot disagree. The diagonal is the
 * crease where the barrels meet, generated from the web rather than struck.
 */
public final class Vault {
    /** Half-width of a rib: two blocks reads as a rib from the floor of a tall nave. */
    private static final double RIB_HALF_WIDTH = 1;

    private Vault() {
    }

    public static final class VaultCell {
        /** Element id prefix: {@code nave/bay-3}, {@code aisle-north-1/bay-2}. */
        public final String id;
        public final double x0, x1, z0, z1;
        public final double springY, crownY, thickness;
        public final LevelName level;
        /** Ids this cell rests on; nullable. */
        public List<String> supports;
        /** A sexpartite cell takes an extra transverse rib across its middle. */
        public boolean sexpartite;

        public VaultCell(String id, double x0, double x1, double z0, double z1, double springY, double crownY,
                         double thickness, LevelName level) {
            this.id = id;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.springY = springY;
            this.crownY = crownY;
            this.thickness = thickness;
            this.level = level;
        }

        public VaultCell supports(List<String> s) {
            this.supports = s;
            return this;
        }

        public VaultCell sexpartite(boolean v) {
            this.sexpartite = v;
            return this;
        }
    }

    public static final class VaultOptions {
        public MaterialRole webRole;
        public MaterialRole ribRole;
        /** Emit the transverse rib at {@code x1} too (the last cell of a run). */
        public boolean closeEast;
        public boolean closeSouth;
        /** Skip the transverse rib at {@code x0} / the wall rib at {@code z0}. */
        public boolean openWest;
        public boolean openNorth;

        public VaultOptions webRole(MaterialRole r) {
            webRole = r;
            return this;
        }

        public VaultOptions ribRole(MaterialRole r) {
            ribRole = r;
            return this;
        }

        public VaultOptions closeEast(boolean v) {
            closeEast = v;
            return this;
        }

        public VaultOptions closeSouth(boolean v) {
            closeSouth = v;
            return this;
        }

        public VaultOptions openWest(boolean v) {
            openWest = v;
            return this;
        }

        public VaultOptions openNorth(boolean v) {
            openNorth = v;
            return this;
        }
    }

    /** Intrados of a cell at a block centre, or NaN outside it. */
    private static double intradosAt(VaultCell cell, double x, double z) {
        double lenX = cell.x1 - cell.x0;
        double lenZ = cell.z1 - cell.z0;
        double rise = cell.crownY - cell.springY;
        if (lenX <= 0 || lenZ <= 0 || rise <= 0) return Double.NaN;
        double a = Geometries.archHeight(z + 0.5 - (cell.z0 + cell.z1) / 2, lenZ, rise);
        double b = Geometries.archHeight(x + 0.5 - (cell.x0 + cell.x1) / 2, lenX, rise);
        if (Double.isNaN(a) || Double.isNaN(b)) return Double.NaN;
        return Js.round(cell.springY + Math.max(a, b));
    }

    private interface ColumnHeight {
        double at(double x, double z);
    }

    private static Geometry.HeightField field(VaultCell cell, ColumnHeight at, Geometry.HeightMode mode, double thickness,
                                              double baseY) {
        int sizeX = (int) (cell.x1 - cell.x0);
        int sizeZ = (int) (cell.z1 - cell.z0);
        double[] heights = new double[Math.max(0, sizeX * sizeZ)];
        Arrays.fill(heights, Double.NaN);
        for (int iz = 0; iz < sizeZ; iz++) {
            for (int ix = 0; ix < sizeX; ix++) {
                heights[iz * sizeX + ix] = at.at(cell.x0 + ix, cell.z0 + iz);
            }
        }
        return new Geometry.HeightField(cell.x0, cell.z0, sizeX, sizeZ, baseY, mode, thickness, heights, null);
    }

    private static double distanceToDiagonal(VaultCell cell, double x, double z, int sign) {
        double lenX = cell.x1 - cell.x0;
        double lenZ = cell.z1 - cell.z0;
        double dx = x + 0.5 - (cell.x0 + cell.x1) / 2;
        double dz = z + 0.5 - (cell.z0 + cell.z1) / 2;
        double length = Math.hypot(lenX, lenZ);
        return Math.abs(dz * lenX - sign * dx * lenZ) / (length == 0 ? 1 : length);
    }

    public static void buildVaultCell(ShellBuilder s, VaultCell cell) {
        buildVaultCell(s, cell, new VaultOptions());
    }

    /**
     * One vault cell: web, boundary ribs, two diagonals and a boss. Ribs are emitted before the web
     * and carry it ({@code vault rib -> vault web}).
     */
    public static void buildVaultCell(ShellBuilder s, VaultCell cell, VaultOptions options) {
        double rise = cell.crownY - cell.springY;
        if (rise < 3 || cell.x1 - cell.x0 < 3 || cell.z1 - cell.z0 < 3) return;
        MaterialRole webRole = options.webRole != null ? options.webRole : MaterialRole.MASONRY_MAIN;
        MaterialRole ribRole = options.ribRole != null ? options.ribRole : MaterialRole.RIB;
        List<String> supports = new ArrayList<>(cell.supports == null ? List.of() : cell.supports);
        List<String> ribs = new ArrayList<>();

        double drop = RIB_HALF_WIDTH + 1;
        List<Object[]> transverse = new ArrayList<>();
        if (!options.openWest) transverse.add(new Object[]{"west", cell.x0});
        if (options.closeEast) transverse.add(new Object[]{"east", cell.x1});
        for (Object[] t : transverse) {
            double x = (Double) t[1];
            rib(ribs, s.add(new AddOptions("rib/" + cell.id + "/transverse-" + t[0], ElementKind.RIB, ribRole, cell.level,
                    new Geometry.SweptArc(new Vec3(x - 0.5, cell.springY - drop, cell.z0), new Vec3(x - 0.5, cell.springY - drop, cell.z1),
                            cell.crownY - drop, RIB_HALF_WIDTH, drop))
                    .supports(supports)));
        }
        if (cell.sexpartite) {
            double mid = Js.round((cell.x0 + cell.x1) / 2);
            // An intermediate rib rises to the same crown as the boundary ones.
            rib(ribs, s.add(new AddOptions("rib/" + cell.id + "/transverse-mid", ElementKind.RIB, ribRole, cell.level,
                    new Geometry.SweptArc(new Vec3(mid - 0.5, cell.springY - drop, cell.z0), new Vec3(mid - 0.5, cell.springY - drop, cell.z1),
                            cell.crownY - drop, RIB_HALF_WIDTH, drop))
                    .supports(supports)));
        }

        List<Object[]> wall = new ArrayList<>();
        if (!options.openNorth) wall.add(new Object[]{"north", cell.z0});
        if (options.closeSouth) wall.add(new Object[]{"south", cell.z1});
        for (Object[] w : wall) {
            double z = (Double) w[1];
            rib(ribs, s.add(new AddOptions("rib/" + cell.id + "/wall-" + w[0], ElementKind.RIB, ribRole, cell.level,
                    new Geometry.SweptArc(new Vec3(cell.x0, cell.springY - drop, z - 0.5), new Vec3(cell.x1, cell.springY - drop, z - 0.5),
                            cell.crownY - drop, RIB_HALF_WIDTH, drop))
                    .supports(supports)));
        }

        // Diagonals, from the web: for an oblong bay the groin is not a struck arc.
        for (int k = 0; k < 2; k++) {
            String name = k == 0 ? "nw-se" : "ne-sw";
            int sign = k == 0 ? 1 : -1;
            rib(ribs, s.add(new AddOptions("rib/" + cell.id + "/diagonal-" + name, ElementKind.RIB, ribRole, cell.level,
                    field(cell, (x, z) -> distanceToDiagonal(cell, x, z, sign) <= RIB_HALF_WIDTH
                            ? intradosAt(cell, x, z) - drop : Double.NaN, Geometry.HeightMode.SHELL, drop, cell.springY))
                    .supports(supports)));
        }

        s.add(new AddOptions("vault/" + cell.id + "/web", ElementKind.VAULT_WEB, webRole, cell.level,
                field(cell, (x, z) -> intradosAt(cell, x, z), Geometry.HeightMode.SHELL, cell.thickness, cell.springY))
                .supports(!ribs.isEmpty() ? ribs : supports)
                .param("span", cell.z1 - cell.z0)
                .param("rise", rise));

        double bx = Js.round((cell.x0 + cell.x1) / 2);
        double bz = Js.round((cell.z0 + cell.z1) / 2);
        s.add(new AddOptions("vault/" + cell.id + "/boss", ElementKind.RIB, MaterialRole.MASONRY_FINE, cell.level,
                new Geometry.Solid(new Box(new Vec3(bx - 1, cell.crownY - drop, bz - 1), new Vec3(bx + 1, cell.crownY, bz + 1))))
                .supports("rib/" + cell.id + "/diagonal-nw-se"));
    }

    private static void rib(List<String> ribs, String id) {
        if (id != null) ribs.add(id);
    }

    public static void buildAtticFill(ShellBuilder s, VaultCell cell, double atticFloorY) {
        buildAtticFill(s, cell, atticFloorY, cell.id);
    }

    /**
     * The masonry filling the corner pockets of a cell up to {@code atticFloorY}: packed haunches,
     * and what makes the attic a place rather than a trap.
     */
    public static void buildAtticFill(ShellBuilder s, VaultCell cell, double atticFloorY, String id) {
        double rise = cell.crownY - cell.springY;
        if (rise < 3) return;
        s.add(new AddOptions("vault/" + id + "/haunch-fill", ElementKind.VAULT_WEB, MaterialRole.RUBBLE_STONE, LevelName.ATTIC,
                field(cell, (x, z) -> {
                    double extradosTop = intradosAt(cell, x, z) + cell.thickness;
                    return extradosTop >= atticFloorY ? Double.NaN : extradosTop;
                }, Geometry.HeightMode.SOLID, 1, atticFloorY - 1))
                .supports("vault/" + cell.id + "/web"));
    }

    /** The options object of {@code buildSemiDome}. */
    public static final class SemiDomeOptions {
        public final String id;
        public final double centreX, centreZ, radius, springY, crownY, thickness;
        public List<String> supports;

        public SemiDomeOptions(String id, double centreX, double centreZ, double radius, double springY, double crownY,
                               double thickness) {
            this.id = id;
            this.centreX = centreX;
            this.centreZ = centreZ;
            this.radius = radius;
            this.springY = springY;
            this.crownY = crownY;
            this.thickness = thickness;
        }

        public SemiDomeOptions supports(List<String> s) {
            this.supports = s;
            return this;
        }
    }

    /** A semi-dome over an apse: the arch profile revolved about the apse centre. */
    public static void buildSemiDome(ShellBuilder s, SemiDomeOptions o) {
        double rise = o.crownY - o.springY;
        if (rise < 3 || o.radius < 3) return;
        double x0 = Js.round(o.centreX);
        double x1 = Js.round(o.centreX + o.radius) + 1;
        double z0 = Js.round(o.centreZ - o.radius);
        double z1 = Js.round(o.centreZ + o.radius) + 1;
        int sizeX = (int) (x1 - x0);
        int sizeZ = (int) (z1 - z0);
        double[] heights = new double[sizeX * sizeZ];
        Arrays.fill(heights, Double.NaN);
        for (int iz = 0; iz < sizeZ; iz++) {
            for (int ix = 0; ix < sizeX; ix++) {
                double r = Math.hypot(x0 + ix + 0.5 - o.centreX, z0 + iz + 0.5 - o.centreZ);
                if (r >= o.radius) continue;
                double h = Geometries.archHeight(r, o.radius * 2, rise);
                if (Double.isNaN(h)) continue;
                heights[iz * sizeX + ix] = Js.round(o.springY + h);
            }
        }
        s.add(new AddOptions("vault/" + o.id + "/semi-dome", ElementKind.VAULT_WEB, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                new Geometry.HeightField(x0, z0, sizeX, sizeZ, o.springY, Geometry.HeightMode.SHELL, o.thickness, heights, null))
                .supports(new ArrayList<>(o.supports == null ? List.of() : o.supports)));
    }
}
