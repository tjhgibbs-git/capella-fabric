package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.element.ElementKind;
import io.github.tjhgibbs.capella.core.element.LevelName;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.kit.AddOptions;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.kit.Geometries;
import io.github.tjhgibbs.capella.core.kit.Geometry;
import io.github.tjhgibbs.capella.core.kit.Parts;
import io.github.tjhgibbs.capella.core.kit.Pinnacle;
import io.github.tjhgibbs.capella.core.kit.Rect;
import io.github.tjhgibbs.capella.core.kit.ShellBuilder;
import io.github.tjhgibbs.capella.core.palette.MaterialRole;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Flank buttresses, flying buttresses and the transept arms' buttresses (Capella
 * {@code cathedral/buttress.ts}). Each buttress is sized by {@link Thrust} from the vault it
 * resists; a deep one becomes a stepped pier with a pinnacle, and over an aisle it throws a
 * flyer to the clerestory.
 */
public final class Buttress {
    private static final double COPING_THICKNESS = 2;
    private static final double ARCH_THICKNESS = 2;
    /** The flyer's arch bulges this fraction of its run below the coping. */
    private static final double FLYER_ARCH_RISE = 0.3;
    /** How far the pinnacle sits in from the buttress face. */
    private static final double PINNACLE_DEPTH = 3;

    private Buttress() {
    }

    public static final class FlyerPlan {
        public final double footZ, wallZ, footY, headY;
        /** Whether the flyer lands low enough to need a door cut through to the attic. */
        public final boolean door;

        FlyerPlan(double footZ, double wallZ, double footY, double headY, boolean door) {
            this.footZ = footZ;
            this.wallZ = wallZ;
            this.footY = footY;
            this.headY = headY;
            this.door = door;
        }
    }

    public static final class ButtressPlan {
        public final Bay bay;
        public final boolean atEast;
        public final double x;
        public final String form;
        public final double depth, width, headY, pinnacleHeight, pinnacleDepth;
        public final FlyerPlan flyer;
        public final double thrust;

        ButtressPlan(Bay bay, boolean atEast, double x, String form, double depth, double width, double headY,
                     double pinnacleHeight, double pinnacleDepth, FlyerPlan flyer, double thrust) {
            this.bay = bay;
            this.atEast = atEast;
            this.x = x;
            this.form = form;
            this.depth = depth;
            this.width = width;
            this.headY = headY;
            this.pinnacleHeight = pinnacleHeight;
            this.pinnacleDepth = pinnacleDepth;
            this.flyer = flyer;
            this.thrust = thrust;
        }
    }

    private record Boundary(Bay bay, boolean atEast, double x) {
    }

    public static List<ButtressPlan> resolveButtresses(PlanLayout layout, String side) {
        Levels L = layout.levels;
        SideBands bands = side.equals("north") ? layout.north : layout.south;
        boolean hasAisles = !bands.aisles.isEmpty();
        int dir = side.equals("north") ? -1 : 1;
        double width = Math.max(5, layout.pierWidth);
        double headY = hasAisles ? L.aisleWallTopY : L.naveWallTopY;
        double deliveryY = hasAisles ? headY : L.vaultSpringY;
        double inboardRun = layout.wallThickness + bands.aisles.size() * layout.plan.aisleWidth
                + Math.max(0, bands.arcades.size() - 1) * layout.arcadeDepth;

        List<ButtressPlan> out = new ArrayList<>();
        for (Boundary b : boundariesOf(layout)) {
            Bay bay = b.bay();
            Thrust.Force high = L.highVault
                    ? Thrust.vaultThrust(layout.plan.naveWidth, bay.span, L.vaultCrownY - L.vaultSpringY, L.vaultThickness)
                    : new Thrust.Force(0, 0);
            Thrust.Force aisle = hasAisles
                    ? Thrust.vaultThrust(layout.plan.aisleWidth, bay.span, Math.max(1, L.aisleVaultCrownY - L.aisleVaultSpringY), L.vaultThickness)
                    : new Thrust.Force(0, 0);
            double demand = Thrust.overturningDemand(high.h(), deliveryY - L.floorY)
                    + Thrust.overturningDemand(aisle.h(), L.aisleVaultSpringY - L.floorY);
            double height = headY - L.floorY;
            double byThrust = Thrust.requiredDepth(demand, width, layout.wallThickness, height);
            double climb = L.atticFloorY - headY - layout.arcadeDepth;
            double byRake = hasAisles && L.highVault ? climb - inboardRun + PINNACLE_DEPTH : 0;
            double depth = Math.max(Thrust.MIN_BUTTRESS_DEPTH, Math.min(Thrust.MAX_BUTTRESS_DEPTH, Math.max(byThrust, byRake)));
            String form = Thrust.formOfDepth(depth);
            double shortfall = demand - Thrust.overturningCapacity(depth, width, layout.wallThickness, height);
            double pinnacleHeight = form.equals("pier")
                    ? Math.max(width * 2, Thrust.pinnacleHeightFor(shortfall, width * depth, depth)) : 0;
            double pinnacleDepth = Math.min(PINNACLE_DEPTH, Math.max(1, depth - 1));
            FlyerPlan flyer = null;
            if (hasAisles && form.equals("pier")) {
                double face = bands.outerZ;
                double footZ = face + dir * (depth - pinnacleDepth);
                Band inner = bands.arcades.get(0);
                double wallZ = side.equals("north") ? inner.z0() : inner.z1() - 1;
                double run = Math.abs(wallZ - footZ);
                double flyerHeadY = Math.min(L.atticFloorY, headY + run);
                boolean door = L.atticFloorY - flyerHeadY <= inner.z1() - inner.z0();
                flyer = new FlyerPlan(footZ, wallZ, headY, flyerHeadY, door);
            }
            out.add(new ButtressPlan(bay, b.atEast(), b.x(), form, depth, width, headY, pinnacleHeight, pinnacleDepth,
                    flyer, high.h() + aisle.h()));
        }
        return out;
    }

    private static List<Boundary> boundariesOf(PlanLayout layout) {
        double towerEastX = layout.towerSize > 0 ? layout.westX - layout.wallThickness + layout.towerSize : layout.westX;
        List<Boundary> all = new ArrayList<>();
        for (Bay bay : layout.bays) all.add(new Boundary(bay, false, bay.x0));
        Bay last = layout.bays.isEmpty() ? null : layout.bays.get(layout.bays.size() - 1);
        if (last != null && layout.chevetDepth == 0) all.add(new Boundary(last, true, last.x1));
        List<Boundary> out = new ArrayList<>();
        for (Boundary b : all) {
            if (b.x() <= towerEastX) continue;
            boolean inCrossing = false;
            for (Crossing c : layout.crossings) {
                if (b.x() >= c.bay.x0 && b.x() <= c.bay.x1) inCrossing = true;
            }
            if (!inCrossing) out.add(b);
        }
        return out;
    }

    public static void buildButtressing(ShellBuilder s, PlanLayout layout) {
        for (String side : List.of("north", "south")) {
            for (ButtressPlan plan : resolveButtresses(layout, side)) buildFlankButtress(s, layout, side, plan);
        }
        buildTranseptButtresses(s, layout);
    }

    private record Stage(String name, double projection, double y0, double y1) {
    }

    private static void buildFlankButtress(ShellBuilder s, PlanLayout layout, String side, ButtressPlan plan) {
        Levels L = layout.levels;
        SideBands bands = side.equals("north") ? layout.north : layout.south;
        int dir = side.equals("north") ? -1 : 1;
        double face = bands.outerZ;
        String suffix = plan.atEast ? "-east" : "";
        String id = "buttress/" + side + "/bay-" + plan.bay.index + suffix;
        double x0 = plan.x - Math.floor(plan.width / 2);
        double x1 = x0 + plan.width;
        DoubleUnaryOperator z0Of = p -> Math.min(face, face + dir * p);
        DoubleUnaryOperator z1Of = p -> Math.max(face, face + dir * p);

        String wallId = "wall/aisle-" + side + "/bay-" + plan.bay.index;
        List<String> supports = s.has(wallId) ? List.of(wallId) : List.of();

        List<Stage> stages = plan.form.equals("pilaster")
                ? List.of(new Stage("shaft", plan.depth, L.floorY, plan.headY))
                : List.of(new Stage("base", plan.depth + 2, L.floorY, L.aisleSpringY),
                new Stage("middle", plan.depth + 1, L.aisleSpringY, L.triforiumDeckY),
                new Stage("head", plan.depth, L.triforiumDeckY, plan.headY));

        double widest = stages.get(0).projection();
        s.ground(new Rect(side + "-buttress-" + plan.bay.index + (plan.atEast ? "e" : ""), x0, x1,
                z0Of.applyAsDouble(widest), z1Of.applyAsDouble(widest)));

        for (Stage stage : stages) {
            s.add(new AddOptions(id + "/" + stage.name(), ElementKind.BUTTRESS, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                    Parts.solid(Box.of(x0, stage.y0(), z0Of.applyAsDouble(stage.projection()), x1, stage.y1(), z1Of.applyAsDouble(stage.projection()))))
                    .supports(supports).param("thrust", Js.round(plan.thrust)).param("depth", plan.depth).param("form", plan.form));
        }

        if (plan.pinnacleHeight > 0) {
            double innerP = Math.max(0, plan.depth - plan.pinnacleDepth);
            double shaft = Math.max(3, plan.width - 2);
            double cx = Js.round((x0 + x1) / 2);
            double px0 = cx - Math.floor(shaft / 2);
            Pinnacle.buildPinnacle(s, new Pinnacle.Options("pinnacle/" + side + "/bay-" + plan.bay.index + suffix,
                    px0, px0 + shaft,
                    dir < 0 ? z0Of.applyAsDouble(plan.depth) : z1Of.applyAsDouble(innerP),
                    dir < 0 ? z0Of.applyAsDouble(innerP) : z1Of.applyAsDouble(plan.depth),
                    plan.headY, plan.pinnacleHeight, LevelName.ROOF).supports(List.of(id + "/head")));
        }

        if (plan.flyer == null) return;
        double width = Math.min(3, plan.width - 2);
        double fx0 = plan.x - Math.floor(width / 2);
        buildFlyer(s, "flyer/" + side + "/bay-" + plan.bay.index + suffix, fx0, fx0 + width, plan.flyer, List.of(id + "/head"));

        if (plan.flyer.door) {
            Band inner = bands.arcades.get(0);
            int depthZ = (int) (inner.z1() - inner.z0());
            int w = (int) width;
            double[] heights = new double[w * depthZ];
            for (int iz = 0; iz < depthZ; iz++) {
                int step = side.equals("north") ? iz : depthZ - 1 - iz;
                double y = Math.min(L.atticFloorY, plan.flyer.headY + step);
                for (int ix = 0; ix < w; ix++) heights[iz * w + ix] = y;
            }
            s.add(new AddOptions("aperture/flyer-" + side + "-bay-" + plan.bay.index + suffix, ElementKind.APERTURE,
                    MaterialRole.MASONRY_MAIN, LevelName.ATTIC,
                    Geometry.HeightField.solid(fx0, inner.z0(), w, depthZ, L.atticFloorY + 3, heights))
                    .parent("wall/clerestory-" + side + "/bay-" + plan.bay.index)
                    .param("asymmetric", "the threshold steps up toward the attic, so the head climbs across the wall"));
        }
    }

    private static void buildFlyer(ShellBuilder s, String id, double x0, double x1, FlyerPlan r, List<String> supports) {
        int sizeX = (int) (x1 - x0);
        int n = (int) (Math.abs(r.wallZ - r.footZ) + 1);
        if (sizeX <= 0 || n < 4) return;
        double originZ = Math.min(r.footZ, r.wallZ);
        double rise = r.headY - r.footY;
        double bulgeMax = Math.max(2, Js.round(n * FLYER_ARCH_RISE));
        DoubleUnaryOperator copingTop = iz -> {
            double u = Math.abs(originZ + iz - r.footZ) / (n - 1);
            return r.footY + Js.round(rise * u);
        };

        s.add(new AddOptions(id + "/coping", ElementKind.FLYER, MaterialRole.MASONRY_TRIM, LevelName.ROOF,
                field(x0, originZ, sizeX, n, r.footY, COPING_THICKNESS, iz -> copingTop.applyAsDouble(iz) - COPING_THICKNESS))
                .supports(supports).param("rise", rise).param("run", n));
        s.add(new AddOptions(id + "/arch", ElementKind.FLYER, MaterialRole.MASONRY_MAIN, LevelName.ROOF,
                field(x0, originZ, sizeX, n, r.footY, ARCH_THICKNESS, iz -> {
                    double bulge = Geometries.archHeight(iz + 0.5 - n / 2.0, n, bulgeMax);
                    double sag = Double.isNaN(bulge) ? 0 : bulge;
                    return Js.round(copingTop.applyAsDouble(iz) - COPING_THICKNESS - (bulgeMax - sag)) - ARCH_THICKNESS;
                }))
                .supports(supports));
    }

    private static Geometry.HeightField field(double originX, double originZ, int sizeX, int n, double baseY, double thickness,
                                              DoubleUnaryOperator at) {
        double[] heights = new double[sizeX * n];
        Arrays.fill(heights, Double.NaN);
        for (int iz = 0; iz < n; iz++) {
            double h = at.applyAsDouble(iz);
            for (int ix = 0; ix < sizeX; ix++) heights[iz * sizeX + ix] = h;
        }
        return new Geometry.HeightField(originX, originZ, sizeX, n, baseY, Geometry.HeightMode.SHELL, thickness, heights, null);
    }

    private static void buildTranseptButtresses(ShellBuilder s, PlanLayout layout) {
        if (layout.crossings.isEmpty()) return;
        Levels L = layout.levels;
        double t = layout.wallThickness;
        double width = Math.max(5, layout.pierWidth);
        Thrust.Force thrust = Thrust.vaultThrust(layout.plan.naveWidth, layout.plan.bayWidth, L.vaultCrownY - L.vaultSpringY, L.vaultThickness);
        double demand = Thrust.overturningDemand(thrust.h(), L.vaultSpringY - L.floorY);
        double height = L.naveWallTopY - L.floorY;
        double depth = Math.max(Thrust.MIN_BUTTRESS_DEPTH, Math.min(Thrust.MAX_BUTTRESS_DEPTH, Thrust.requiredDepth(demand, width, t, height)));
        double shortfall = demand - Thrust.overturningCapacity(depth, width, t, height);
        double pinnacleHeight = Math.max(8, Thrust.pinnacleHeightFor(shortfall, width * depth, depth));

        for (Crossing c : layout.crossings) {
            for (String side : List.of("north", "south")) {
                String arm = Crossing.armName(c, side);
                int dir = side.equals("north") ? -1 : 1;
                double near = (side.equals("north") ? layout.north : layout.south).outerZ;
                double far = layout.axisZ + dir * c.reach;
                double armLength = Math.abs(far - near);
                double count = Math.max(1, Js.round(armLength / Math.max(4, layout.plan.bayWidth)));
                for (int n = 1; n <= count; n++) {
                    double z = Js.round(near + dir * (armLength * n) / count);
                    boolean atEnd = n == count;
                    for (int k = 0; k < 2; k++) {
                        String name = k == 0 ? "west" : "east";
                        double faceX = k == 0 ? c.bay.x0 - t : c.bay.x1;
                        int xDir = k == 0 ? -1 : 1;
                        double zc = atEnd ? z - dir * Math.ceil(width / 2) : z;
                        double z0 = zc - Math.floor(width / 2);
                        double rx0 = Math.min(faceX, faceX + xDir * depth), rx1 = Math.max(faceX, faceX + xDir * depth);
                        double rz0 = z0, rz1 = z0 + width;
                        String id = "buttress/" + arm + "-" + name + "/station-" + n;
                        s.ground(new Rect(arm + "-" + name + "-" + n, rx0, rx1, rz0, rz1));
                        String wall = "wall/" + arm + "-" + name;
                        s.add(new AddOptions(id, ElementKind.BUTTRESS, MaterialRole.MASONRY_MAIN, LevelName.GROUND,
                                Parts.solid(Box.of(rx0, L.floorY, rz0, rx1, L.naveWallTopY, rz1)))
                                .supports(s.has(wall) ? List.of(wall) : List.of())
                                .param("thrust", Js.round(thrust.h())).param("depth", depth).param("form", "pier"));
                        Pinnacle.buildPinnacle(s, new Pinnacle.Options(id.replace("buttress/", "pinnacle/"),
                                rx0 + (xDir > 0 ? 1 : 0), rx1 - (xDir > 0 ? 0 : 1), rz0 + 1, rz1 - 1,
                                L.naveWallTopY, pinnacleHeight, LevelName.ROOF).supports(List.of(id)));
                    }
                }
            }
        }
    }
}
