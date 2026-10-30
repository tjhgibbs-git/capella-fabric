package io.github.tjhgibbs.capella.core.kit;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The stair turret and the helix inside it (Capella {@code kit/turret.ts}): the building's
 * vertical circulation. The hard part is landing it: a door has to arrive on a chosen face at an
 * exact height, so {@code planHelix} searches for a run that lands every door, spreading flat
 * treads out rather than bunching them (bunched flats bring one turn down onto the head of the
 * turn below).
 */
public final class Turret {
    private Turret() {
    }

    public static final class TurretDoor {
        /** Element id suffix. */
        public final String name;
        /** World Y of the walking surface on both sides of the door. */
        public final double surfaceY;
        /** Inner-grid z indices the door may be cut at, on the east face. */
        public final List<Integer> cells;
        /** Cut through to this world X, past the wall the turret is built against. */
        public final double throughX;
        /** Optional south face: inner-grid x indices, and the world Z to cut through to. */
        public List<Integer> southCells;
        public Double throughZ;

        public TurretDoor(String name, double surfaceY, List<Integer> cells, double throughX) {
            this.name = name;
            this.surfaceY = surfaceY;
            this.cells = cells;
            this.throughX = throughX;
        }

        public TurretDoor south(List<Integer> southCells, double throughZ) {
            this.southCells = southCells;
            this.throughZ = throughZ;
            return this;
        }
    }

    public static final class TurretSpec {
        public final String id;
        /** Outer footprint, half-open. */
        public final double x0, x1, z0, z1;
        public final double thickness;
        public final double floorY;
        public final double topY;
        /** Doors above the ground floor, ascending. */
        public final List<TurretDoor> doors;

        public TurretSpec(String id, double x0, double x1, double z0, double z1, double thickness, double floorY,
                          double topY, List<TurretDoor> doors) {
            this.id = id;
            this.x0 = x0;
            this.x1 = x1;
            this.z0 = z0;
            this.z1 = z1;
            this.thickness = thickness;
            this.floorY = floorY;
            this.topY = topY;
            this.doors = doors;
        }
    }

    /** A cell of the inner grid. */
    public record Cell(int ix, int iz) {
    }

    private record Step(int ix, int iz, double y) {
    }

    private record Landing(boolean east, int at) {
    }

    private record Helix(List<Step> steps, Map<String, Landing> doorCells) {
    }

    /** The perimeter of the inner grid, clockwise, as a cycle. */
    public static List<Cell> ringOf(int sizeX, int sizeZ) {
        List<Cell> ring = new ArrayList<>();
        for (int iz = 0; iz < sizeZ; iz++) ring.add(new Cell(0, iz));
        for (int ix = 1; ix < sizeX; ix++) ring.add(new Cell(ix, sizeZ - 1));
        for (int iz = sizeZ - 2; iz >= 0; iz--) ring.add(new Cell(sizeX - 1, iz));
        for (int ix = sizeX - 2; ix >= 1; ix--) ring.add(new Cell(ix, 0));
        return ring;
    }

    private static Landing landingAt(Cell cell, TurretDoor door, int eastIx, int southIz) {
        if (cell.ix() == eastIx && door.cells.contains(cell.iz())) return new Landing(true, cell.iz());
        if (door.southCells != null && cell.iz() == southIz && door.southCells.contains(cell.ix())) {
            return new Landing(false, cell.ix());
        }
        return null;
    }

    private static Helix planHelix(List<Cell> ring, double baseY, List<TurretDoor> doors, double topY, int eastIx, int southIz) {
        int n = ring.size();
        List<Step> steps = new ArrayList<>();
        Map<String, Landing> doorCells = new LinkedHashMap<>();
        double y = baseY;
        int i = 0;
        for (TurretDoor door : doors) {
            // The tread a player stands on to walk out is one below the surface.
            double rise = door.surfaceY - 1 - y;
            if (rise < 1) return null;
            int count = (int) rise;
            Landing landing;
            for (int guard = 0; ; guard++) {
                if (guard > n * 4) return null;
                Cell cell = ring.get((i + count) % n);
                landing = landingAt(cell, door, eastIx, southIz);
                // Half the treads flat is the point at which two would have to touch.
                if (landing != null && count - rise <= count / 2.0) break;
                count++;
            }
            double flats = count - rise;
            double spent = 0;
            for (int k = 0; k < count; k++) {
                Cell c = ring.get((i + k) % n);
                steps.add(new Step(c.ix(), c.iz(), y));
                double want = Math.floor(((k + 1) * flats) / count);
                if (want > spent) spent = want;
                else y++;
            }
            Cell c = ring.get((i + count) % n);
            steps.add(new Step(c.ix(), c.iz(), y));
            doorCells.put(door.name, landing);
            i += count + 1;
            y++;
        }
        // ...and on up to the head of the turret.
        while (y < topY - 2) {
            Cell c = ring.get(i % n);
            steps.add(new Step(c.ix(), c.iz(), y));
            y++;
            i++;
        }
        return new Helix(steps, doorCells);
    }

    /** Whether {@link #buildTurret} would land this helix, without building anything. */
    public static boolean turretLands(TurretSpec spec) {
        double t = spec.thickness;
        int sizeX = (int) (spec.x1 - t - (spec.x0 + t));
        int sizeZ = (int) (spec.z1 - t - (spec.z0 + t));
        if (sizeX < 3 || sizeZ < 3 || spec.topY <= spec.floorY + 6) return false;
        return planHelix(ringOf(sizeX, sizeZ), spec.floorY, spec.doors, spec.topY, sizeX - 1, sizeZ - 1) != null;
    }

    /**
     * Build the turret: shell, newel, helix, doors and spirelet. Returns false (and emits nothing)
     * when the helix could not be landed.
     */
    public static boolean buildTurret(ShellBuilder s, TurretSpec spec) {
        double t = spec.thickness;
        double innerX0 = spec.x0 + t;
        double innerZ0 = spec.z0 + t;
        int sizeX = (int) (spec.x1 - t - innerX0);
        int sizeZ = (int) (spec.z1 - t - innerZ0);
        if (sizeX < 3 || sizeZ < 3 || spec.topY <= spec.floorY + 6) return false;

        List<Cell> ring = ringOf(sizeX, sizeZ);
        Helix helix = planHelix(ring, spec.floorY, spec.doors, spec.topY, sizeX - 1, sizeZ - 1);
        if (helix == null) return false;

        s.ground(new Rect(spec.id, spec.x0, spec.x1, spec.z0, spec.z1));

        String shellId = "tower/" + spec.id;
        // No bottom face: it would fill the turret's ground floor solid and seal the door.
        s.add(shellId, ElementKind.TOWER, MaterialRole.MASONRY_MAIN, LevelName.TOWER,
                new Geometry.Shell(Box.of(spec.x0, spec.floorY - 1, spec.z0, spec.x1, spec.topY, spec.z1), t,
                        ShellFaces.ALL.withTop(false).withBottom(false)));

        // The newel: the core the stair winds round, and the only thing holding the treads up.
        double coreX0 = innerX0 + 1;
        double coreZ0 = innerZ0 + 1;
        if (sizeX > 2 && sizeZ > 2) {
            s.add(new AddOptions(shellId + "/newel", ElementKind.PIER, MaterialRole.MASONRY_TRIM, LevelName.TOWER,
                    Parts.solid(Box.of(coreX0, spec.floorY, coreZ0, innerX0 + sizeX - 1, spec.topY, innerZ0 + sizeZ - 1)))
                    .parent(shellId).supports(shellId));
        }

        // One element per turn, as a surface height field.
        int perTurn = ring.size();
        for (int turn = 0; turn * perTurn < helix.steps().size(); turn++) {
            List<Step> slice = helix.steps().subList(turn * perTurn, Math.min(helix.steps().size(), (turn + 1) * perTurn));
            double[] heights = new double[sizeX * sizeZ];
            Arrays.fill(heights, Double.NaN);
            for (Step step : slice) heights[step.iz() * sizeX + step.ix()] = step.y();
            s.add(new AddOptions(shellId + "/flight-" + Js.str(turn + 1), ElementKind.STAIR, MaterialRole.MASONRY_TRIM,
                    LevelName.TOWER,
                    new Geometry.HeightField(innerX0, innerZ0, sizeX, sizeZ, spec.floorY, Geometry.HeightMode.SURFACE, null, heights, null))
                    .parent(shellId + "/newel").supports(shellId + "/newel"));
        }

        s.add(new AddOptions(shellId + "/floor", ElementKind.FLOOR, MaterialRole.FLOOR_WORN, LevelName.GROUND,
                Parts.solid(Box.of(innerX0, spec.floorY - 1, innerZ0, innerX0 + sizeX, spec.floorY, innerZ0 + sizeZ)))
                .parent(shellId));

        for (TurretDoor door : spec.doors) {
            Landing landing = helix.doorCells().get(door.name);
            if (landing == null) continue;
            boolean east = landing.east();
            double z = innerZ0 + (east ? landing.at() : sizeZ - 1);
            double x = innerX0 + (east ? sizeX - 1 : landing.at());
            s.add(new AddOptions("aperture/" + spec.id + "-" + door.name, ElementKind.APERTURE, MaterialRole.MASONRY_MAIN,
                    LevelName.TOWER,
                    east ? Parts.solid(Box.of(spec.x1 - t, door.surfaceY, z, door.throughX, door.surfaceY + 3, z + 1))
                            : Parts.solid(Box.of(x, door.surfaceY, spec.z1 - t, x + 1, door.surfaceY + 3, door.throughZ)))
                    .parent(shellId)
                    .param("symmetry", east ? "z" : "x"));
        }

        // A spirelet, so the turret reads as one from a hundred blocks out rather than a chimney.
        double halfX = (spec.x1 - spec.x0) / 2;
        double halfZ = (spec.z1 - spec.z0) / 2;
        double cx = (spec.x0 + spec.x1) / 2;
        double cz = (spec.z0 + spec.z1) / 2;
        int capSizeX = (int) (spec.x1 - spec.x0);
        int capSizeZ = (int) (spec.z1 - spec.z0);
        double[] capHeights = new double[capSizeX * capSizeZ];
        Arrays.fill(capHeights, Double.NaN);
        for (int iz = 0; iz < capSizeZ; iz++) {
            for (int ix = 0; ix < capSizeX; ix++) {
                double inset = Math.min(halfX - Math.abs(spec.x0 + ix + 0.5 - cx), halfZ - Math.abs(spec.z0 + iz + 0.5 - cz));
                // The eaves course has to land on the wall head, not a block above it.
                capHeights[iz * capSizeX + ix] = spec.topY - 1 + Js.round(inset * 2);
            }
        }
        s.add(new AddOptions("spire/" + spec.id, ElementKind.SPIRE, MaterialRole.ROOF, LevelName.ROOF,
                new Geometry.HeightField(spec.x0, spec.z0, capSizeX, capSizeZ, spec.topY, Geometry.HeightMode.SHELL, 1.0, capHeights, null))
                .parent(shellId).supports(shellId));
        return true;
    }
}
