package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.ElementTree;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Axis;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Dir;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.AddOptions;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.kit.Geometries;
import io.github.tjhgibbs.capella.core.kit.Geometry;
import io.github.tjhgibbs.capella.core.kit.Parts;
import io.github.tjhgibbs.capella.core.kit.Rect;
import io.github.tjhgibbs.capella.core.kit.Roof;
import io.github.tjhgibbs.capella.core.kit.ShellBuilder;
import io.github.tjhgibbs.capella.core.kit.Tower;
import io.github.tjhgibbs.capella.core.kit.Turret;
import io.github.tjhgibbs.capella.core.kit.Vault;
import io.github.tjhgibbs.capella.core.layout.Portal;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;
import io.github.tjhgibbs.capella.core.raster.Primitives;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The cathedral's fabric: every element of the building, set out in the plan's own frame along
 * +X and turned by {@code layout.frame} as it is added (Capella {@code cathedral/shell.ts}).
 *
 * <p>Not ported, by decision: the undercroft crypt, the sunken-floor platforms and the aisle
 * mezzanines, and the footing (the building floats). Floors are flat slabs.
 */
public final class CathedralShell {
    private static final List<String> SIDES = List.of("north", "south");
    private static final double WEST_SPIRE_PITCH = 2.6;
    private static final double CROSSING_SPIRE_PITCH = 1.8;
    /** Narrowest light a tracery run leaves between mullions. */
    private static final int MIN_LIGHT = 3;

    private CathedralShell() {
    }

    public static ElementTree generateShell(Scene scene, PlanLayout layout, String prefix) {
        ShellBuilder s = new ShellBuilder(prefix, layout.frame);
        buildGroundFloors(s, layout);
        for (String side : SIDES) buildArcades(s, layout, side);
        buildCrossing(s, layout);
        for (String side : SIDES) buildOuterWalls(s, layout, side);
        for (String side : SIDES) buildTriforium(s, layout, side);
        buildWestFront(s, layout);
        buildEastEnd(s, layout);
        buildTransept(s, layout);
        buildVaults(s, layout);
        buildRoofs(s, layout);
        Buttress.buildButtressing(s, layout);
        for (String side : SIDES) Mural.buildMuralPassage(s, layout, side);
        for (String side : SIDES) Mural.buildGalleryStairs(s, layout, side);
        // Before the towers, so a tower's corner buttress can be told where the vice is.
        buildTowers(s, layout, buildStairTurrets(s, layout));
        return s.builder.build();
    }

    private static SideBands bandsOf(PlanLayout layout, String side) {
        return side.equals("north") ? layout.north : layout.south;
    }

    // ---- Floors -----------------------------------------------------------------------------

    /** East of the crossing the sanctuary is raised: a nave is never one uninterrupted plate. */
    private static double floorYOfBay(PlanLayout layout, Bay bay) {
        return bay.raised ? layout.levels.choirFloorY : layout.levels.floorY;
    }

    private static void buildGroundFloors(ShellBuilder s, PlanLayout layout) {
        Levels L = layout.levels;
        for (Bay bay : layout.bays) {
            double y = floorYOfBay(layout, bay);
            s.add("floor/bay-" + bay.index, ElementKind.FLOOR, bay.raised ? MaterialRole.FLOOR : MaterialRole.FLOOR_WORN,
                    LevelName.GROUND, Parts.floorSlab(bay.x0, bay.x1, layout.north.outerZ, layout.south.outerZ, L.floorY, y));
        }
        // The step run up into the choir: blocks, one rise per tread, kind floor so the floors
        // pass does not flatten them.
        Bay choir = null;
        for (Bay b : layout.bays) {
            if (b.kind == Bay.Kind.CHOIR) {
                choir = b;
                break;
            }
        }
        if (choir != null && L.choirFloorY > L.floorY) {
            double rise = L.choirFloorY - L.floorY;
            for (int step = 0; step < rise; step++) {
                double x = choir.x0 - rise + step;
                s.add("floor/choir-step-" + (step + 1), ElementKind.FLOOR, MaterialRole.FLOOR, LevelName.GROUND,
                        Parts.solid(Box.of(x, L.floorY - 1, layout.naveZ0, x + 1, L.floorY + step + 1, layout.naveZ1)));
            }
        }
    }

    // ---- Arcades ----------------------------------------------------------------------------

    private record Opening(double from, double to, double crownY, double ringDepth) {
    }

    private static Geometry.HeightField spandrelField(double x0, double x1, Band band, double springY, double topY,
                                                      List<Opening> openings) {
        int sizeX = (int) (x1 - x0);
        int sizeZ = (int) (band.z1() - band.z0());
        double[] heights = nans(sizeX * sizeZ);
        for (int i = 0; i < sizeX; i++) {
            double x = x0 + i + 0.5;
            double floorAt = springY;
            for (Opening o : openings) {
                if (x < o.from() || x > o.to()) continue;
                double span = o.to() - o.from();
                double h = Geometries.archHeight(x - (o.from() + o.to()) / 2, span, o.crownY() - springY);
                if (Double.isNaN(h)) continue;
                floorAt = Math.max(floorAt, Js.round(springY + h) + o.ringDepth());
            }
            if (floorAt >= topY) continue;
            for (int j = 0; j < sizeZ; j++) heights[j * sizeX + i] = floorAt;
        }
        return Geometry.HeightField.solid(x0, band.z0(), sizeX, sizeZ, topY - 1, heights);
    }

    private static double bandCentre(Band band) {
        return Parts.bandSweep(band).centre();
    }

    private static double[] pierSpanAt(PlanLayout layout, double x) {
        double x0 = x - Math.floor(layout.pierWidth / 2);
        return new double[]{x0, x0 + layout.pierWidth};
    }

    private static void buildArcades(ShellBuilder s, PlanLayout layout, String side) {
        SideBands bands = bandsOf(layout, side);
        Levels L = layout.levels;
        for (int k = 0; k < bands.arcades.size(); k++) {
            Band band = bands.arcades.get(k);
            boolean inner = k == 0;
            double topY = inner ? L.spandrelTopY : L.aisleCrownY + 2;
            double crownY = inner ? L.arcadeCrownY : L.aisleCrownY;
            buildPiers(s, layout, side, band, k);
            Parts.Sweep sweep = Parts.bandSweep(band);
            for (Bay bay : layout.bays) {
                String prefix = "arcade/" + side + "-" + k + "/bay-" + bay.index;
                String pierA = "arcade/" + side + "-" + k + "/pier-" + bay.index;
                String pierB = "arcade/" + side + "-" + k + "/pier-" + (bay.index + 1);
                double from = pierSpanAt(layout, bay.x0)[1];
                double to = pierSpanAt(layout, bay.x1)[0];
                List<Opening> openings = new ArrayList<>();
                if (bay.isCrossing && inner) {
                    openings.add(new Opening(bay.x0, bay.x1, L.crossingCrownY, L.arcadeRingDepth));
                } else if (to - from >= 3) {
                    String ring = s.add(new AddOptions(prefix + "/arch", ElementKind.ARCH, MaterialRole.MASONRY_TRIM, LevelName.GROUND,
                            new Geometry.SweptArc(new Vec3(from, L.arcadeSpringY, sweep.centre()), new Vec3(to, L.arcadeSpringY, sweep.centre()),
                                    crownY, sweep.width(), L.arcadeRingDepth))
                            .supports(pierA, pierB).param("symmetry", "x"));
                    if (ring != null) openings.add(new Opening(from, to, crownY, L.arcadeRingDepth));
                }
                s.add(new AddOptions(prefix + "/spandrel", ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                        spandrelField(bay.x0, bay.x1, band, L.arcadeSpringY, topY, openings))
                        .supports(prefix + "/arch", pierA, pierB));
            }
            if (inner) buildClerestoryWall(s, layout, side, band);
        }
    }

    private static void buildPiers(ShellBuilder s, PlanLayout layout, String side, Band band, int k) {
        Levels L = layout.levels;
        PlanParams plan = layout.plan;
        List<Double> boundaries = new ArrayList<>();
        boundaries.add(layout.bays.get(0).x0);
        for (Bay b : layout.bays) boundaries.add(b.x1);
        for (int i = 0; i < boundaries.size(); i++) {
            double x = boundaries.get(i);
            double[] span = pierSpanAt(layout, x);
            boolean crossingHere = false;
            for (Crossing c : layout.crossings) {
                if (x == c.bay.x0 || x == c.bay.x1) crossingHere = true;
            }
            double grow = crossingHere ? 1 : 0;
            String id = "arcade/" + side + "-" + k + "/pier-" + (i + 1);
            s.add(new AddOptions(id, ElementKind.PIER, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                    Parts.solid(Box.of(span[0] - grow, L.floorY, band.z0() - grow, span[1] + grow, L.arcadeSpringY, band.z1() + grow)))
                    .param("form", "block").param("pierForm", plan.pierForm.id()));
            if (plan.pierForm == PlanParams.PierForm.CYLINDRICAL) continue;
            double mid = Js.round((span[0] + span[1]) / 2);
            List<Box> shafts = new ArrayList<>(List.of(
                    Box.of(mid - 1, L.floorY, band.z0() - 1, mid + 1, L.arcadeSpringY, band.z0()),
                    Box.of(mid - 1, L.floorY, band.z1(), mid + 1, L.arcadeSpringY, band.z1() + 1)));
            if (plan.pierForm == PlanParams.PierForm.CLUSTERED) {
                double zMid = Js.round(bandCentre(band));
                shafts.add(Box.of(span[0] - 1, L.floorY, zMid - 1, span[0], L.arcadeSpringY, zMid + 1));
                shafts.add(Box.of(span[1], L.floorY, zMid - 1, span[1] + 1, L.arcadeSpringY, zMid + 1));
            }
            for (int n = 0; n < shafts.size(); n++) {
                s.add(new AddOptions(id + "/shaft-" + (n + 1), ElementKind.PIER, MaterialRole.MASONRY_TRIM, LevelName.GROUND,
                        Parts.solid(shafts.get(n))).parent(id).supports(id));
            }
        }
    }

    private static void buildClerestoryWall(ShellBuilder s, PlanLayout layout, String side, Band band) {
        Levels L = layout.levels;
        for (Bay bay : layout.bays) {
            String id = "wall/clerestory-" + side + "/bay-" + bay.index;
            s.add(new AddOptions(id, ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.TRIFORIUM,
                    Parts.solid(Box.of(bay.x0, L.spandrelTopY, band.z0(), bay.x1, L.naveWallTopY, band.z1())))
                    .supports("arcade/" + side + "-0/bay-" + bay.index + "/spandrel"));

            double inset = 2;
            double usable = bay.span - inset * 2;
            if (L.hasGallery && usable >= 6) {
                double each = Math.floor((usable - 2) / 2);
                for (int n = 0; n < 2; n++) {
                    double from = bay.x0 + inset + n * (each + 2);
                    s.add(new AddOptions("aperture/triforium-" + side + "/bay-" + bay.index + "-" + (n + 1), ElementKind.APERTURE,
                            MaterialRole.MASONRY_MAIN, LevelName.TRIFORIUM,
                            Parts.archedOpening(Axis.X, from, from + each, band.z0(), band.z1(), L.triforiumDeckY,
                                    L.triforiumDeckY + Math.max(1, Math.floor((L.triforiumHeadY - L.triforiumDeckY) / 2)), L.triforiumHeadY))
                            .parent(id).param("symmetry", "x"));
                }
            }

            double windowWidth = Math.max(3, Math.min(bay.span - 4, layout.referenceSpan - 2));
            double centre = Js.round((bay.x0 + bay.x1) / 2);
            double from = centre - Math.floor(windowWidth / 2);
            Window w = new Window(Axis.X, from, from + windowWidth, band.z0(), band.z1(), L.clerestorySillY, L.clerestorySpringY, L.clerestoryCrownY);
            String ap = "aperture/clerestory-" + side + "/bay-" + bay.index;
            s.add(new AddOptions(ap, ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.TRIFORIUM, w.geometry())
                    .parent(id).param("symmetry", "x"));
            addTracery(s, layout, w, "tracery/clerestory-" + side + "/bay-" + bay.index, LevelName.TRIFORIUM, ap, id);
        }
    }

    // ---- Crossing ---------------------------------------------------------------------------

    private static void buildCrossing(ShellBuilder s, PlanLayout layout) {
        for (Crossing c : layout.crossings) buildOneCrossing(s, layout, c);
    }

    private static void buildOneCrossing(ShellBuilder s, PlanLayout layout, Crossing c) {
        Bay crossing = c.bay;
        Levels L = layout.levels;
        for (String side : SIDES) {
            List<Band> arcades = bandsOf(layout, side).arcades;
            Parts.Sweep sweep = arcades.isEmpty() ? null : Parts.bandSweep(arcades.get(0));
            double z = sweep != null ? sweep.centre() : side.equals("north") ? layout.naveZ0 : layout.naveZ1;
            double width = sweep != null ? sweep.width() : layout.wallThickness - 1;
            s.add(new AddOptions("arch/crossing" + c.tag + "-" + side, ElementKind.ARCH, MaterialRole.MASONRY_TRIM, LevelName.GROUND,
                    new Geometry.SweptArc(new Vec3(crossing.x0, L.arcadeSpringY, z), new Vec3(crossing.x1, L.arcadeSpringY, z),
                            L.crossingCrownY, width, L.arcadeRingDepth))
                    .supports("arcade/" + side + "-0/pier-" + crossing.index).param("symmetry", "x"));
        }
        for (int k = 0; k < 2; k++) {
            String name = k == 0 ? "west" : "east";
            double x = k == 0 ? crossing.x0 : crossing.x1;
            s.add(new AddOptions("arch/crossing" + c.tag + "-" + name, ElementKind.ARCH, MaterialRole.MASONRY_TRIM, LevelName.GROUND,
                    new Geometry.SweptArc(new Vec3(x, L.arcadeSpringY, layout.naveZ0), new Vec3(x, L.arcadeSpringY, layout.naveZ1),
                            L.crossingCrownY, layout.pierWidth, L.arcadeRingDepth))
                    .supports("arcade/north-0/pier-" + crossing.index, "arcade/south-0/pier-" + crossing.index)
                    .param("symmetry", "z"));
        }
    }

    // ---- Outer walls ------------------------------------------------------------------------

    private static void buildOuterWalls(ShellBuilder s, PlanLayout layout, String side) {
        SideBands bands = bandsOf(layout, side);
        Levels L = layout.levels;
        Band band = bands.outerWall;
        boolean hasAisles = !bands.aisles.isEmpty();
        double topY = hasAisles ? L.aisleWallTopY : L.naveWallTopY;
        for (Bay bay : layout.bays) {
            if (bay.isCrossing) continue;
            String id = "wall/aisle-" + side + "/bay-" + bay.index;
            s.add(id, ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                    Parts.solid(Box.of(bay.x0, L.floorY, band.z0(), bay.x1, topY, band.z1())));

            double windowWidth = Math.max(3, Math.min(bay.span - 4, layout.referenceSpan - 2));
            double centre = Js.round((bay.x0 + bay.x1) / 2);
            double from = centre - Math.floor(windowWidth / 2);
            Window w = new Window(Axis.X, from, from + windowWidth, band.z0(), band.z1(), L.aisleSillY, L.aisleSpringY, L.aisleCrownY);
            String ap = "aperture/aisle-" + side + "/bay-" + bay.index;
            s.add(new AddOptions(ap, ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.GROUND, w.geometry())
                    .parent(id).param("symmetry", "x"));
            addTracery(s, layout, w, "tracery/aisle-" + side + "/bay-" + bay.index, LevelName.GROUND, ap, id);

            if (hasAisles) continue;
            Window upper = new Window(Axis.X, from, from + windowWidth, band.z0(), band.z1(), L.clerestorySillY, L.clerestorySpringY, L.clerestoryCrownY);
            String up = "aperture/clerestory-" + side + "/bay-" + bay.index;
            s.add(new AddOptions(up, ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.TRIFORIUM, upper.geometry())
                    .parent(id).param("symmetry", "x"));
            addTracery(s, layout, upper, "tracery/clerestory-" + side + "/bay-" + bay.index, LevelName.TRIFORIUM, up, id);
        }
    }

    private static void buildTriforium(ShellBuilder s, PlanLayout layout, String side) {
        SideBands bands = bandsOf(layout, side);
        if (bands.aisles.isEmpty()) return;
        Levels L = layout.levels;
        Band inner = bands.arcades.get(0);
        Band outer = bands.outerWall;
        double z0 = side.equals("south") ? inner.z1() : outer.z1();
        double z1 = side.equals("south") ? outer.z0() : inner.z0();
        // The gallery over the aisle vaults, and where the storey over it is tall enough to stand
        // in, a flat leaded roof at the clerestory sill closing it, so the triforium looks into a
        // covered gallery and not up at the sky. Neither crosses a transept arm.
        boolean roofed = L.aisleWallTopY - L.triforiumDeckY >= CathedralPlan.MIN_STOREY_CLEAR + 1;
        List<double[]> runs = new ArrayList<>();
        for (Bay b : layout.bays) {
            if (b.isCrossing) continue;
            double from = b.index == 1 ? layout.westX - layout.wallThickness : b.x0;
            if (!runs.isEmpty() && runs.get(runs.size() - 1)[1] == b.x0) runs.get(runs.size() - 1)[1] = b.x1;
            else runs.add(new double[]{from, b.x1});
        }
        for (int n = 0; n < runs.size(); n++) {
            double[] run = runs.get(n);
            List<String> supports = new ArrayList<>();
            for (Bay b : layout.bays) {
                if (!b.isCrossing && b.x0 >= run[0] - layout.wallThickness && b.x1 <= run[1]) supports.add("wall/aisle-" + side + "/bay-" + b.index);
            }
            String tag = n == 0 ? "" : "-" + (n + 1);
            s.add(new AddOptions("floor/triforium-" + side + tag, ElementKind.FLOOR, MaterialRole.FLOOR, LevelName.TRIFORIUM,
                    Parts.floorSlab(run[0], run[1], Math.min(z0, z1), Math.max(z0, z1), L.triforiumDeckY, L.triforiumDeckY))
                    .supports(supports));
            if (!roofed) continue;
            s.add(new AddOptions("roof/aisle-" + side + tag, ElementKind.ROOF, MaterialRole.ROOF, LevelName.ROOF,
                    Parts.floorSlab(run[0], run[1], Math.min(z0, z1), Math.max(z0, z1), L.aisleWallTopY, L.aisleWallTopY))
                    .supports(supports));
        }
    }

    // ---- West front and east end --------------------------------------------------------------

    private static Portal portalOf(PlanLayout layout, String kind) {
        for (Portal p : layout.portals) {
            if (p.kind.id().equals(kind)) return p;
        }
        return null;
    }

    private static void buildWestFront(ShellBuilder s, PlanLayout layout) {
        Levels L = layout.levels;
        double t = layout.wallThickness;
        double x0 = layout.westX - t, x1 = layout.westX;
        s.add("wall/west-front", ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                Parts.solid(Box.of(x0, L.floorY, layout.naveZ0 - layout.arcadeDepth, x1, L.naveWallTopY, layout.naveZ1 + layout.arcadeDepth)));
        for (String side : SIDES) {
            SideBands bands = bandsOf(layout, side);
            if (bands.aisles.isEmpty()) continue;
            double near = side.equals("north") ? layout.naveZ0 - layout.arcadeDepth : layout.naveZ1 + layout.arcadeDepth;
            s.add("wall/west-front-" + side, ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                    Parts.solid(Box.of(x0, L.floorY, Math.min(near, bands.outerZ), x1, L.aisleWallTopY, Math.max(near, bands.outerZ))));
        }

        Portal portal = portalOf(layout, "west-great");
        if (portal != null) {
            double from = portal.position.z() - Math.floor(portal.width / 2);
            s.add(new AddOptions("aperture/west-portal", ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                    Parts.archedOpening(Axis.Z, from, from + portal.width, x0, x1, L.floorY,
                            L.floorY + Js.round(portal.height * 0.6), L.floorY + portal.height))
                    .parent("wall/west-front").param("symmetry", "z"));
        }

        double windowWidth = Math.max(5, layout.plan.naveWidth - 8);
        double from = layout.axisZ - Math.floor(windowWidth / 2);
        double sillY = L.floorY + (portal != null ? portal.height : 12) + 4;
        Window w = new Window(Axis.Z, from, from + windowWidth, x0, x1, sillY, Math.max(sillY + 4, L.triforiumDeckY), L.clerestoryCrownY);
        s.add(new AddOptions("aperture/west-window", ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.GROUND, w.geometry())
                .parent("wall/west-front").param("symmetry", "z"));
        addTracery(s, layout, w, "tracery/west-window", LevelName.GROUND, "aperture/west-window", "wall/west-front");
    }

    private static void buildEastEnd(ShellBuilder s, PlanLayout layout) {
        Levels L = layout.levels;
        double t = layout.wallThickness;
        if (layout.chevetDepth != 0) {
            buildApse(s, layout);
            return;
        }
        double bz0 = layout.north.outerZ, bz1 = layout.south.outerZ;
        double hz0 = layout.naveZ0 - layout.arcadeDepth, hz1 = layout.naveZ1 + layout.arcadeDepth;
        Object[][] bands = {
                {"north", bz0, Math.min(hz0, bz1), L.aisleWallTopY},
                {"high", Math.max(hz0, bz0), Math.min(hz1, bz1), L.naveWallTopY},
                {"south", Math.max(hz1, bz0), bz1, L.aisleWallTopY},
        };
        for (Object[] b : bands) {
            String name = (String) b[0];
            double z0 = (double) b[1], z1 = (double) b[2], topY = (double) b[3];
            if (z1 <= z0 || topY <= L.floorY) continue;
            s.add(name.equals("high") ? "wall/east-end" : "wall/east-end-" + name, ElementKind.WALL, MaterialRole.MASONRY_MAIN,
                    LevelName.GROUND, Parts.solid(Box.of(layout.eastX, L.floorY, z0, layout.eastX + t, topY, z1)));
        }
        double windowWidth = Math.max(5, layout.plan.naveWidth - 8);
        double from = layout.axisZ - Math.floor(windowWidth / 2);
        Window w = new Window(Axis.Z, from, from + windowWidth, layout.eastX, layout.eastX + t, L.choirFloorY + 4,
                L.arcadeSpringY, L.clerestoryCrownY);
        s.add(new AddOptions("aperture/east-window", ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.GROUND, w.geometry())
                .parent("wall/east-end").param("symmetry", "z"));
        addTracery(s, layout, w, "tracery/east-window", LevelName.GROUND, "aperture/east-window", "wall/east-end");
    }

    /**
     * The chevet, in rings about the end of the choir. Without an ambulatory the apse is one
     * windowed wall. With one, the apse wall stands on an arcade and there is a ring of walk round
     * it for every aisle, divided by arcades as the aisles are and closed by the outer wall where
     * the aisles' outer walls end, so the aisles run on round the sanctuary. The walk is vaulted
     * and leaded at the aisles' heights; the apse wall has a clerestory over the leads.
     */
    private static void buildApse(ShellBuilder s, PlanLayout layout) {
        Levels L = layout.levels;
        double t = layout.wallThickness;
        double radius = layout.plan.naveWidth / 2.0;
        double ring = layout.arcadeDepth;
        double aisle = layout.plan.aisleWidth;
        int walks = ambulatoryWalks(layout.plan);
        double step = ring + aisle;
        double outerR = radius + walks * step;
        Geometries.Span zs = Geometries.centredSpan(layout.axisZ, outerR + t + 1);
        Annulus field = new Annulus(layout.eastX, layout.axisZ, layout.eastX, zs.from(),
                (int) Math.ceil(outerR + t + 1), (int) (zs.to() - zs.from()));
        int bays = 5;

        if (walks == 0) {
            field.wall(s, "wall/apse", radius, ring, L.floorY, L.naveWallTopY + 1);
            field.openings(s, "apse-windows", "wall/apse", radius, ring, bays, 0.25, L.choirFloorY + 4,
                    Math.min(L.naveWallTopY, L.spandrelTopY) - 2, MaterialRole.GLASS_B);
        } else {
            // The apse wall: an arcade on the sanctuary, a clerestory over the walk's leads.
            field.wall(s, "wall/apse", radius, ring, L.floorY, L.naveWallTopY + 1);
            field.openings(s, "apse-arcade", "wall/apse", radius, ring, bays, 0.34, L.choirFloorY,
                    L.arcadeCrownY, null);
            field.openings(s, "apse-clerestory", "wall/apse", radius, ring, bays, 0.25, L.clerestorySillY,
                    L.clerestoryCrownY, MaterialRole.GLASS_B);
            for (int k = 1; k < walks; k++) {
                String id = "wall/ambulatory-arcade-" + k;
                double inner = radius + k * step;
                field.wall(s, id, inner, ring, L.floorY, L.aisleCrownY + 2);
                // Twice the bays: the outer ring is twice as long, and its piers stand behind the apse's.
                field.openings(s, "ambulatory-arcade-" + k, id, inner, ring, bays * 2, 0.34, L.choirFloorY,
                        L.aisleCrownY, null);
            }
            field.wall(s, "wall/ambulatory", outerR, t, L.floorY, L.aisleWallTopY);
            field.openings(s, "ambulatory-windows", "wall/ambulatory", outerR, t, bays * 2, 0.25,
                    Math.max(L.aisleSillY, L.choirFloorY + 3), L.aisleCrownY, MaterialRole.GLASS_A);
            field.walkVault(s, "vault/ambulatory", radius + ring, aisle, step, walks, L.aisleVaultSpringY,
                    L.triforiumDeckY - 1, L.aisleCrownY + 2);
            if (L.aisleWallTopY - L.triforiumDeckY >= CathedralPlan.MIN_STOREY_CLEAR + 1) {
                double[] leads = field.blank();
                field.each((i, dx, dz, r) -> {
                    if (r >= radius + ring && r < outerR) leads[i] = L.aisleWallTopY - 1;
                });
                s.add(new AddOptions("roof/ambulatory", ElementKind.ROOF, MaterialRole.ROOF, LevelName.ROOF,
                        field.solid(L.aisleWallTopY - 1, leads)).supports("wall/ambulatory"));
            }
        }

        double[] floor = field.blank();
        field.each((i, dx, dz, r) -> {
            if (r < outerR + t) floor[i] = L.choirFloorY - 1;
        });
        s.add("floor/chevet", ElementKind.FLOOR, MaterialRole.FLOOR, LevelName.GROUND, field.solid(L.floorY - 1, floor));
    }

    /** Rings of walk round the apse: one per aisle, none without an ambulatory. */
    static int ambulatoryWalks(PlanParams plan) {
        return plan.chevet == PlanParams.Chevet.APSE_WITH_AMBULATORY ? Math.max(1, plan.aisles) : 0;
    }

    /** A grid over the eastern half-disc of the chevet, centred on the end of the choir. */
    private record Annulus(double cx, double cz, double x0, double z0, int sizeX, int sizeZ) {
        interface Cell {
            void at(int i, double dx, double dz, double r);
        }

        double[] blank() {
            return nans(sizeX * sizeZ);
        }

        void each(Cell c) {
            for (int iz = 0; iz < sizeZ; iz++) {
                for (int ix = 0; ix < sizeX; ix++) {
                    double dx = x0 + ix + 0.5 - cx;
                    double dz = z0 + iz + 0.5 - cz;
                    if (dx < 0) continue;
                    c.at(iz * sizeX + ix, dx, dz, Primitives.hypot(dx, dz));
                }
            }
        }

        Geometry.HeightField solid(double baseY, double[] heights) {
            return Geometry.HeightField.solid(x0, z0, sizeX, sizeZ, baseY, heights);
        }

        /** A ring wall, {@code topY} exclusive like a box's. */
        void wall(ShellBuilder s, String id, double inner, double depth, double baseY, double topY) {
            double[] h = blank();
            each((i, dx, dz, r) -> {
                if (r >= inner && r < inner + depth) h[i] = topY - 1;
            });
            s.add(id, ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.GROUND, solid(baseY, h));
        }

        /**
         * Arched openings through a ring wall, {@code bays} of them round the half-circle with a pier
         * on the axis of the choir arcade at each end; {@code half} is half an opening's width as a
         * share of its bay. Glazed in the middle of the wall when {@code glass} is given.
         */
        void openings(ShellBuilder s, String name, String parent, double inner, double depth, int bays, double half,
                      double sillY, double crownY, MaterialRole glass) {
            double mid = inner + depth / 2;
            double bayLength = Math.PI * mid / bays;
            double span = 2 * half * bayLength;
            double rise = Math.min(span / 2 + 1, Math.max(1, crownY - sillY - 1));
            double springY = crownY - rise;
            double[] top = blank(), bottom = blank(), pane = blank(), paneBottom = blank();
            each((i, dx, dz, r) -> {
                if (r < inner || r >= inner + depth) return;
                double phase = (StrictMath.atan2(dz, dx) + Math.PI / 2) / Math.PI * bays;
                double u = (phase - Math.floor(phase) - 0.5) * bayLength;
                double h = Geometries.archHeight(u, span, rise);
                if (Double.isNaN(h)) return;
                top[i] = Math.floor(springY + h) - 1;
                bottom[i] = sillY;
                if (glass != null && Math.abs(r - mid) < 0.75) {
                    pane[i] = top[i];
                    paneBottom[i] = sillY;
                }
            });
            String ap = "aperture/" + name;
            s.add(new AddOptions(ap, ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                    solid(sillY, top).withHeights(top, bottom)).param("symmetry", "z").parent(parent));
            if (glass == null) return;
            s.add(new AddOptions("glass/" + name, ElementKind.GLAZING, glass, LevelName.GROUND,
                    solid(sillY, pane).withHeights(pane, paneBottom))
                    .parent(ap).supports(parent).param("form", "pane").param("symmetry", "z"));
        }

        /**
         * The walk's vault and leads: a barrel turned round each ring of walk, springing off the
         * ring walls, and over the arcades between the rings solid up to the leads.
         */
        void walkVault(ShellBuilder s, String id, double from, double aisle, double step, int walks,
                       double springY, double leadsY, double wallTopY) {
            double crown = Math.min(leadsY - 1, springY + aisle / 2);
            double[] top = blank(), bottom = blank();
            each((i, dx, dz, r) -> {
                if (r < from || r >= from + (walks - 1) * step + aisle) return;
                double into = (r - from) % step;
                top[i] = leadsY;
                if (into >= aisle) {
                    bottom[i] = wallTopY;
                    return;
                }
                double h = Geometries.archHeight(into + 0.5 - aisle / 2, aisle, crown - springY);
                bottom[i] = Double.isNaN(h) ? springY : Math.floor(springY + h);
            });
            s.add(new AddOptions(id, ElementKind.VAULT_WEB, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                    solid(leadsY, top).withHeights(top, bottom)).supports("wall/apse"));
        }
    }

    // ---- Transept ---------------------------------------------------------------------------

    private static void buildTransept(ShellBuilder s, PlanLayout layout) {
        for (Crossing c : layout.crossings) buildOneTransept(s, layout, c);
    }

    private static void buildOneTransept(ShellBuilder s, PlanLayout layout, Crossing c) {
        Bay crossing = c.bay;
        Levels L = layout.levels;
        double t = layout.wallThickness;
        double floorY = floorYOfBay(layout, crossing);
        for (String side : SIDES) {
            String arm = Crossing.armName(c, side);
            int dir = side.equals("north") ? -1 : 1;
            double near = bandsOf(layout, side).outerZ;
            double far = layout.axisZ + dir * c.reach;
            double inner = far - dir * t;
            double armZ0 = Math.min(near, inner), armZ1 = Math.max(near, inner);
            double endZ0 = Math.min(inner, far), endZ1 = Math.max(inner, far);

            for (int k = 0; k < 2; k++) {
                String name = k == 0 ? "west" : "east";
                double x = k == 0 ? crossing.x0 - t : crossing.x1;
                s.add("wall/" + arm + "-" + name, ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                        Parts.solid(Box.of(x, L.floorY, armZ0, x + t, L.naveWallTopY, armZ1)));
            }
            String endId = "wall/" + arm + "-end";
            s.add(endId, ElementKind.WALL, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                    Parts.solid(Box.of(crossing.x0 - t, L.floorY, endZ0, crossing.x1 + t, L.naveWallTopY, endZ1)));
            s.add("floor/" + arm, ElementKind.FLOOR, MaterialRole.FLOOR_WORN, LevelName.GROUND,
                    Parts.floorSlab(crossing.x0 - t, crossing.x1 + t, armZ0, armZ1, L.floorY, floorY));

            Portal portal = null;
            for (Portal p : layout.portals) {
                if (p.kind.id().equals("transept") && p.facing.id().equals(side) && p.at != null && p.at == c.at) {
                    portal = p;
                    break;
                }
            }
            if (portal != null) {
                double from = portal.position.x() - Math.floor(portal.width / 2);
                s.add(new AddOptions("aperture/" + arm + "-portal", ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                        Parts.archedOpening(Axis.X, from, from + portal.width, endZ0, endZ1, floorY,
                                floorY + Js.round(portal.height * 0.6), floorY + portal.height))
                        .parent(endId).param("symmetry", "x"));
            }

            double windowWidth = Math.max(5, crossing.span - 8);
            double from = Js.round((crossing.x0 + crossing.x1) / 2) - Math.floor(windowWidth / 2);
            Window w = new Window(Axis.X, from, from + windowWidth, endZ0, endZ1, L.arcadeSpringY - 4, L.spandrelTopY, L.clerestoryCrownY);
            s.add(new AddOptions("aperture/" + arm + "-window", ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.GROUND, w.geometry())
                    .parent(endId).param("symmetry", "x"));
            addTracery(s, layout, w, "tracery/" + arm + "-window", LevelName.GROUND, "aperture/" + arm + "-window", endId);
        }
    }

    // ---- Vaults -----------------------------------------------------------------------------

    /** What a nave vault cell springs from: the clerestory where there is one, else the outer wall. */
    private static List<String> naveWallIds(ShellBuilder s, int bayIndex) {
        List<String> out = new ArrayList<>();
        for (String side : SIDES) {
            String clerestory = "wall/clerestory-" + side + "/bay-" + bayIndex;
            out.add(s.has(clerestory) ? clerestory : "wall/aisle-" + side + "/bay-" + bayIndex);
        }
        return out;
    }

    private static List<Double> armVaultBoundaries(PlanLayout layout, Crossing c, String side) {
        int dir = side.equals("north") ? -1 : 1;
        double near = side.equals("north") ? layout.naveZ0 : layout.naveZ1;
        double far = layout.axisZ + dir * (c.reach - layout.wallThickness);
        double armLength = Math.abs(far - near);
        double count = Math.max(1, Js.round(armLength / Math.max(4, layout.plan.bayWidth)));
        double step = armLength / count;
        List<Double> out = new ArrayList<>();
        for (int n = 0; n <= count; n++) out.add(Js.round(near + dir * step * n));
        return out;
    }

    private static void buildVaults(ShellBuilder s, PlanLayout layout) {
        Levels L = layout.levels;
        boolean sexpartite = layout.plan.vaultType == PlanParams.VaultType.SEXPARTITE;
        if (!L.highVault) {
            buildAisleVaults(s, layout);
            return;
        }
        List<Vault.VaultCell> highCells = new ArrayList<>();
        for (Bay bay : layout.bays) {
            highCells.add(new Vault.VaultCell("nave/bay-" + bay.index, bay.x0, bay.x1, layout.naveZ0, layout.naveZ1,
                    L.vaultSpringY, L.vaultCrownY, L.vaultThickness, LevelName.ATTIC)
                    .sexpartite(sexpartite && bay.span >= 8).supports(naveWallIds(s, bay.index)));
        }
        int lastHigh = highCells.size() - 1;
        List<TowerSite> towerSites = crossingTowerSites(layout);
        for (int n = 0; n < highCells.size(); n++) {
            Vault.VaultCell cell = highCells.get(n);
            Vault.buildVaultCell(s, cell, new Vault.VaultOptions().closeEast(n == lastHigh).closeSouth(true));
            Vault.buildAtticFill(s, cell, L.atticFloorY);
            Bay bay = layout.bays.get(n);
            boolean towered = false;
            for (TowerSite site : towerSites) {
                if (site.crossing().bay == bay) towered = true;
            }
            if (bay.isCrossing && towered) Vault.buildAtticFill(s, cell, L.atticWalkY, cell.id + "-tower-floor");
        }

        for (Crossing c : layout.crossings) {
            for (String side : SIDES) {
                String arm = Crossing.armName(c, side);
                List<Double> bounds = armVaultBoundaries(layout, c, side);
                for (int n = 0; n < bounds.size() - 1; n++) {
                    double a = bounds.get(n), b = bounds.get(n + 1);
                    Vault.VaultCell cell = new Vault.VaultCell(arm + "/bay-" + (n + 1), c.bay.x0, c.bay.x1, Math.min(a, b), Math.max(a, b),
                            L.vaultSpringY, L.vaultCrownY, L.vaultThickness, LevelName.ATTIC)
                            .supports(List.of("wall/" + arm + "-west", "wall/" + arm + "-east"));
                    Vault.buildVaultCell(s, cell, new Vault.VaultOptions().closeEast(true).closeSouth(n == bounds.size() - 2).openNorth(n > 0));
                    Vault.buildAtticFill(s, cell, L.atticFloorY);
                }
            }
        }

        buildAisleVaults(s, layout);

        if (layout.chevetDepth > 0) {
            Vault.buildSemiDome(s, new Vault.SemiDomeOptions("apse", layout.eastX, layout.axisZ, layout.plan.naveWidth / 2.0,
                    L.vaultSpringY, L.vaultCrownY, L.vaultThickness).supports(List.of("wall/apse")));
        }
    }

    private static void buildAisleVaults(ShellBuilder s, PlanLayout layout) {
        Levels L = layout.levels;
        for (String side : SIDES) {
            List<Band> aisles = bandsOf(layout, side).aisles;
            for (int k = 0; k < aisles.size(); k++) {
                Band aisle = aisles.get(k);
                for (Bay bay : layout.bays) {
                    if (bay.isCrossing) continue;
                    Vault.buildVaultCell(s, new Vault.VaultCell("aisle-" + side + "-" + k + "/bay-" + bay.index, bay.x0, bay.x1,
                                    aisle.z0(), aisle.z1(), L.aisleVaultSpringY, L.aisleVaultCrownY, L.vaultThickness, LevelName.GROUND)
                                    .supports(List.of("wall/aisle-" + side + "/bay-" + bay.index)),
                            new Vault.VaultOptions().closeEast(bay.index == layout.bays.size()).closeSouth(true));
                }
            }
        }
    }

    // ---- Roofs ------------------------------------------------------------------------------

    private static void buildRoofs(ShellBuilder s, PlanLayout layout) {
        Levels L = layout.levels;
        double t = layout.wallThickness;
        List<TowerSite> towers = crossingTowerSites(layout);

        List<double[]> holes = new ArrayList<>();
        for (TowerSite site : towers) holes.add(new double[]{site.x0(), site.x1()});
        Roof.RoofRun nave = new Roof.RoofRun("nave", layout.westX - t, layout.eastX + (layout.chevetDepth > 0 ? 0 : t),
                layout.naveZ0 - layout.arcadeDepth, layout.naveZ1 + layout.arcadeDepth, Axis.X, L.naveWallTopY, L.roofPitch, 2)
                .supports(naveWallIds(s, 1))
                .gableSupports(List.of("wall/west-front"), layout.chevetDepth == 0 ? List.of("wall/east-end") : null)
                .holes(holes);
        List<Double> stations = new ArrayList<>();
        for (Bay b : layout.bays) stations.add(b.x0);
        stations.add(layout.eastX - 1);
        Roof.buildRoof(s, nave, stations);

        for (Crossing c : layout.crossings) {
            Bay crossing = c.bay;
            TowerSite tower = null;
            for (TowerSite site : towers) {
                if (site.crossing() == c) tower = site;
            }
            for (String side : SIDES) {
                String arm = Crossing.armName(c, side);
                int dir = side.equals("north") ? -1 : 1;
                double far = layout.axisZ + dir * c.reach;
                double inner = layout.axisZ;
                Roof.RoofRun run = new Roof.RoofRun(arm, crossing.x0 - t, crossing.x1 + t, Math.min(far, inner), Math.max(far, inner),
                        Axis.Z, L.naveWallTopY, L.roofPitch, 2)
                        .gables(side.equals("north") ? null : false, side.equals("north") ? false : null)
                        .supports(List.of("wall/" + arm + "-west"));
                if (tower != null) run.holes(List.<double[]>of(new double[]{tower.z0(), tower.z1()}));
                Roof.buildRoof(s, run, armVaultBoundaries(layout, c, side));
            }
        }

        if (layout.chevetDepth > 0) {
            // Over the apse alone: an ambulatory is leaded like the aisles, under the clerestory.
            double reach = Math.ceil(layout.plan.naveWidth / 2.0) + layout.arcadeDepth;
            Roof.buildConicalRoof(s, new Roof.ConeOptions("apse", layout.eastX, layout.axisZ, reach, L.naveWallTopY,
                    Math.max(0.5, (L.roofRidgeY - L.naveWallTopY) / reach), 2)
                    .supports(List.of("wall/apse")));
        }
    }

    // ---- Stair turrets ----------------------------------------------------------------------

    private static List<Rect> buildStairTurrets(ShellBuilder s, PlanLayout layout) {
        List<Rect> rects = new ArrayList<>();
        // Off by default: it stands proud of one side of the west front and spoils its symmetry.
        if (!layout.plan.stairTurret) return rects;
        Rect north = buildStairTurret(s, layout, "north");
        if (north != null) {
            rects.add(north);
            buildAtticRamp(s, layout);
        }
        if (!layout.levels.hasMuralPassage && !layout.south.aisles.isEmpty()) {
            Rect south = buildStairTurret(s, layout, "south");
            if (south != null) rects.add(south);
        }
        return rects;
    }

    private static Rect buildStairTurret(ShellBuilder s, PlanLayout layout, String side) {
        Levels L = layout.levels;
        double t = layout.wallThickness;
        double thickness = 2;
        SideBands bands = bandsOf(layout, side);
        int inner = 7;
        boolean north = side.equals("north");
        double innerZ0 = north ? layout.naveZ0 - 5 : layout.naveZ1 + 5 - inner;
        String id = north ? "turret-west" : "turret-west-south";
        List<Turret.TurretDoor> doors = new ArrayList<>();
        if (!bands.aisles.isEmpty()) {
            doors.add(new Turret.TurretDoor("gallery", L.triforiumDeckY, List.of(outboard(north, inner, 0), outboard(north, inner, 1)), layout.westX));
        }
        if (!bands.arcades.isEmpty() && L.hasMuralPassage) {
            doors.add(new Turret.TurretDoor("mural", L.muralFloorY, List.of(outboard(north, inner, 3), outboard(north, inner, 4)), layout.westX));
        }
        doors.add(new Turret.TurretDoor("attic", L.atticFloorY, List.of(outboard(north, inner, 5), outboard(north, inner, 6)), layout.westX));

        Rect rect = new Rect(id, layout.westX - t - inner, layout.westX - t, innerZ0 - thickness, innerZ0 + inner + thickness);
        boolean built = Turret.buildTurret(s, new Turret.TurretSpec(id, rect.x0(), rect.x1(), rect.z0(), rect.z1(), thickness,
                L.floorY, L.naveWallTopY + 4, doors));
        if (!built) return null;

        double outerZ = innerZ0 + (north ? 2 : inner - 3);
        s.add(new AddOptions("aperture/" + id + "-outer", ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                Parts.solid(Box.of(rect.x0(), L.floorY, outerZ, rect.x0() + thickness, L.floorY + 3, outerZ + 1)))
                .parent("tower/" + id).param("symmetry", "z"));
        double naveZ = north ? layout.naveZ0 + 1 : layout.naveZ1 - 2;
        s.add(new AddOptions("aperture/" + id + "-nave", ElementKind.APERTURE, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                Parts.solid(Box.of(layout.westX - t - thickness, L.floorY, naveZ, layout.westX, L.floorY + 3, naveZ + 1)))
                .parent("tower/" + id).param("symmetry", "z"));
        return rect;
    }

    private static int outboard(boolean north, int inner, int c) {
        return north ? c : inner - 1 - c;
    }

    private static void buildAtticRamp(ShellBuilder s, PlanLayout layout) {
        Levels L = layout.levels;
        double z0 = layout.naveZ0, z1 = layout.axisZ + 1;
        double x0 = layout.westX, x1 = layout.westX + 4;
        int sizeX = (int) (x1 - x0), sizeZ = (int) (z1 - z0);
        if (sizeZ < 2 || L.atticWalkY <= L.atticFloorY) return;
        double[] heights = new double[sizeX * sizeZ];
        for (int iz = 0; iz < sizeZ; iz++) {
            double top = Math.min(L.atticWalkY - 1, L.atticFloorY - 1 + iz);
            for (int ix = 0; ix < sizeX; ix++) heights[iz * sizeX + ix] = top;
        }
        s.add(new AddOptions("stair/attic-ramp", ElementKind.STAIR, MaterialRole.TIMBER, LevelName.ATTIC,
                Geometry.HeightField.solid(x0, z0, sizeX, sizeZ, L.atticFloorY - 1, heights))
                .supports(naveWallIds(s, 1)));
    }

    // ---- Towers -----------------------------------------------------------------------------

    private static void buildTowers(ShellBuilder s, PlanLayout layout, List<Rect> keepOut) {
        PlanParams plan = layout.plan;
        Levels L = layout.levels;
        double t = layout.wallThickness;
        if (plan.westTowers > 0 && plan.westTowerHeight > 0) {
            double size = layout.towerSize;
            Bay bay = layout.bays.get(0);
            List<String> names = new ArrayList<>();
            List<Double> zs = new ArrayList<>();
            if (plan.westTowers == 1) {
                names.add("centre");
                zs.add(layout.axisZ);
            } else {
                for (String side : SIDES) {
                    double outer = bandsOf(layout, side).outerZ;
                    double nave = side.equals("north") ? layout.naveZ0 : layout.naveZ1;
                    names.add(side);
                    zs.add(Js.round((outer + nave) / 2));
                }
            }
            double topY = Math.max(L.floorY + plan.westTowerHeight, L.roofRidgeY + Js.round(size * 0.4));
            for (int i = 0; i < names.size(); i++) {
                double z = zs.get(i);
                Tower.buildTower(s, new Tower.TowerSpec("west-" + names.get(i), bay.x0 - t, Math.min(bay.x1, bay.x0 - t + size),
                        Js.round(z - size / 2), Js.round(z + size / 2), L.floorY, topY, t,
                        List.of(Dir.WEST, Dir.NORTH, Dir.SOUTH), plan.towerCap, WEST_SPIRE_PITCH, 3, true)
                        .open(Tower.Open.EAST)
                        .openTopY(plan.westTowers == 1 ? L.naveWallTopY : L.aisleWallTopY)
                        .keepOut(keepOut));
            }
        }
        for (TowerSite site : crossingTowerSites(layout)) {
            String tag = site.crossing().tag;
            Tower.buildTower(s, new Tower.TowerSpec("crossing" + tag, site.x0(), site.x1(), site.z0(), site.z1(),
                    L.naveWallTopY, site.topY(), t, List.of(Dir.NORTH, Dir.SOUTH, Dir.EAST, Dir.WEST),
                    plan.crossingTower.cap, CROSSING_SPIRE_PITCH, 2, false)
                    .open(Tower.Open.NONE).viceFootY(L.atticWalkY).interiorFloors(true)
                    .supports(List.of("arch/crossing" + tag + "-north", "arch/crossing" + tag + "-south")));
        }
    }

    private record TowerSite(Crossing crossing, double x0, double x1, double z0, double z1, double topY) {
    }

    private static List<TowerSite> crossingTowerSites(PlanLayout layout) {
        PlanParams plan = layout.plan;
        Levels L = layout.levels;
        double t = layout.wallThickness;
        if (!plan.crossingTower.present || plan.crossingTower.height == 0) return List.of();
        List<Crossing> carrying = plan.crossingTower.at == PlanParams.TowerAt.EACH
                ? layout.crossings : layout.crossings.subList(0, Math.min(1, layout.crossings.size()));
        List<TowerSite> sites = new ArrayList<>();
        for (Crossing crossing : carrying) {
            Bay c = crossing.bay;
            double height = crossing.at == 0 ? plan.crossingTower.height : subordinateHeight(plan.crossingTower.height);
            TowerSite site = new TowerSite(crossing, c.x0 - t, c.x1 + t, layout.naveZ0 - layout.arcadeDepth,
                    layout.naveZ1 + layout.arcadeDepth, L.naveWallTopY + height);
            if (site.x1() - site.x0() < 5 || site.z1() - site.z0() < 5 || height < Tower.MIN_TOWER_HEIGHT) continue;
            sites.add(site);
        }
        return sites;
    }

    private static double subordinateHeight(double principal) {
        return Math.min(principal, Math.max(Tower.MIN_TOWER_HEIGHT, Js.round(principal * 0.62)));
    }

    // ---- Tracery ----------------------------------------------------------------------------

    /** An arched window opening: the TS {@code archedOpening} options, kept for its tracery. */
    private record Window(Axis along, double from, double to, double bandFrom, double bandTo, double sillY, double springY,
                          double crownY) {
        Geometry.HeightField geometry() {
            return Parts.archedOpening(along, from, to, bandFrom, bandTo, sillY, springY, crownY);
        }
    }

    /**
     * Mullions laid out as a palindrome by {@code centredRun}, so the window is symmetric about
     * its own middle; bar and flamboyant tracery add arched heads, flamboyant groups them.
     */
    private static void addTracery(ShellBuilder s, PlanLayout layout, Window r, String id, LevelName level, String parentId,
                                   String supportId) {
        glaze(s, r, id + "/glass", level, parentId, supportId);
        double width = r.to() - r.from();
        PlanParams.TraceryStyle style = layout.plan.traceryStyle;
        Geometries.CentredRun run = null;
        for (int lights = traceryLights(style, width); lights >= 2; lights--) {
            Geometries.CentredRun candidate = Geometries.centredRun((int) r.from(), (int) r.to(), lights - 1);
            if (Geometries.narrowestGap(candidate) >= MIN_LIGHT) {
                run = candidate;
                break;
            }
        }
        if (run == null) return;
        String axis = r.along().id();
        for (int i = 0; i < run.members().size(); i++) {
            Geometries.Span m = run.members().get(i);
            Geometry g = r.along() == Axis.X
                    ? Parts.solid(Box.of(m.from(), r.sillY(), r.bandFrom(), m.to(), r.crownY(), r.bandTo()))
                    : Parts.solid(Box.of(r.bandFrom(), r.sillY(), m.from(), r.bandTo(), r.crownY(), m.to()));
            s.add(new AddOptions(id + "/mullion-" + (i + 1), ElementKind.TRACERY, MaterialRole.TRACERY, level, g)
                    .parent(parentId).supports(supportId).param("style", style.id()).param("symmetry", axis));
        }

        if (style != PlanParams.TraceryStyle.BAR && style != PlanParams.TraceryStyle.FLAMBOYANT) return;
        double headRise = Math.max(2, Js.round((r.crownY() - r.springY()) * 0.45));
        double centreAcross = (r.bandFrom() + r.bandTo() - 1) / 2;
        for (int i = 0; i < run.gaps().size(); i++) {
            Geometries.Span gap = run.gaps().get(i);
            double from = Math.max(r.from(), gap.from() - 1);
            double to = Math.min(r.to() - 1, gap.to());
            double rise = Math.max(2, Math.min(headRise, gap.to() - gap.from() + 1));
            head(s, r, id + "/head-" + (i + 1), from, to, rise, centreAcross, level, parentId, supportId, style);
        }

        if (style != PlanParams.TraceryStyle.FLAMBOYANT) return;
        List<Geometries.Span> lit = new ArrayList<>();
        for (Geometries.Span g : run.gaps()) {
            if (g.to() - g.from() >= MIN_LIGHT) lit.add(g);
        }
        for (int i = 0; i + 1 < lit.size(); i++) {
            Geometries.Span a = lit.get(i), b = lit.get(i + 1);
            double from = Math.max(r.from(), a.from() - 1);
            double to = Math.min(r.to() - 1, b.to());
            head(s, r, id + "/group-" + (i + 1), from, to, Math.max(3, Math.min(headRise, to - from)), centreAcross, level,
                    parentId, supportId, style);
        }
    }

    /**
     * One layer of panes in the middle of the wall, the opening's own outline. Glazing fills only
     * air, so the mullions and heads written over it stay stone. The great windows (west, east,
     * transept ends) take the richer glass.
     */
    private static void glaze(ShellBuilder s, Window r, String id, LevelName level, String parentId, String supportId) {
        double mid = Math.floor((r.bandFrom() + r.bandTo() - 1) / 2);
        MaterialRole glass = r.to() - r.from() >= 9 ? MaterialRole.GLASS_B : MaterialRole.GLASS_A;
        s.add(new AddOptions(id, ElementKind.GLAZING, glass, level,
                Parts.archedOpening(r.along(), r.from(), r.to(), mid, mid + 1, r.sillY(), r.springY(), r.crownY()))
                .parent(parentId).supports(supportId).param("form", "pane").param("symmetry", r.along().id()));
    }

    private static void head(ShellBuilder s, Window r,String id, double from, double to, double rise, double centreAcross,
                             LevelName level, String parentId, String supportId, PlanParams.TraceryStyle style) {
        if (to - from < 2) return;
        double y = r.springY();
        boolean x = r.along() == Axis.X;
        s.add(new AddOptions(id, ElementKind.TRACERY, MaterialRole.TRACERY, level,
                new Geometry.SweptArc(x ? new Vec3(from, y, centreAcross) : new Vec3(centreAcross, y, from),
                        x ? new Vec3(to, y, centreAcross) : new Vec3(centreAcross, y, to),
                        y + rise, Math.max(0, r.bandTo() - r.bandFrom() - 1), 1))
                .parent(parentId).supports(supportId).param("style", style.id()).param("symmetry", r.along().id()));
    }

    private static int traceryLights(PlanParams.TraceryStyle style, double width) {
        if (style == PlanParams.TraceryStyle.LANCET) return 1;
        if (width < 5) return 1;
        double density = style == PlanParams.TraceryStyle.PLATE ? 6 : MIN_LIGHT + 1;
        return (int) Math.max(1, Math.min(5, Math.floor((width + 1) / density)));
    }

    private static double[] nans(int n) {
        double[] a = new double[n];
        Arrays.fill(a, Double.NaN);
        return a;
    }
}
