package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Dir;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.kit.Geometries;
import io.github.tjhgibbs.capella.core.kit.PlacementFrame;
import io.github.tjhgibbs.capella.core.kit.Rect;
import io.github.tjhgibbs.capella.core.layout.FaceAnchors;
import io.github.tjhgibbs.capella.core.layout.LevelBand;
import io.github.tjhgibbs.capella.core.layout.Portal;
import io.github.tjhgibbs.capella.core.layout.StructureLayout;
import io.github.tjhgibbs.capella.core.terrain.FootprintRect;
import io.github.tjhgibbs.capella.core.terrain.GroundSampler;
import io.github.tjhgibbs.capella.core.terrain.Terrain;
import io.github.tjhgibbs.capella.core.util.Js;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The plan resolver (Capella {@code cathedral/plan.ts}): {@link PlanParams} in, exact metric
 * geometry out. Bay positions, the Z bands of the section, every springing and crown, and the
 * portals. Pure arithmetic; it reads one field, {@code terrainHeight}, to solve the datum.
 */
public final class CathedralPlan {
    /** The floor level a scene on the default terrain (64) comes out at. */
    public static final double FLOOR_Y = 68;
    /** How far an arcade pier's shaft stands proud of the arcade into the nave. */
    public static final double PIER_SHAFT_PROJECTION = 1;

    /** How high the high vault rises over its springing, as a fraction of the nave's width. */
    private static final double VAULT_RISE_FRACTION = 0.45;
    /** Where the attic's pocket fill sits between springing and crown. */
    private static final double ATTIC_FILL_FRACTION = 0.65;
    /** Blocks of rise per block of run on the main roofs: about 55 degrees. */
    private static final double ROOF_PITCH = 1.4;
    /** Least clear height a storey can have and still be one: two of head plus the one stood on. */
    private static final double MIN_STOREY_CLEAR = 3;

    private CathedralPlan() {
    }

    /** Top block of the precinct paving. */
    public static double terraceTopOf(Levels levels) {
        return levels.floorY - Terrain.PLINTH_RISE - 1;
    }

    /** Nave clear height floor from the proportion rule: twice the clear width. */
    private static double targetNaveHeight(double naveWidth) {
        return Js.round(naveWidth * 2);
    }

    private static double pierWidthFor(PlanParams plan, double referenceSpan) {
        double base = plan.pierForm == PlanParams.PierForm.CYLINDRICAL ? 3 : plan.pierForm == PlanParams.PierForm.COMPOUND ? 4 : 5;
        // Never let a pier eat its own bay: an arcade needs a hole in it.
        return Math.max(3, Math.min(base, referenceSpan - 4));
    }

    /** Plan size of a (square) west tower: over the aisle with aisles, flanking the nave without. */
    private static double towerSizeFor(PlanParams plan, double wallThickness, double arcadeDepth) {
        if (plan.westTowers == 0) return 0;
        return plan.aisles > 0
                ? Math.min(arcadeDepth + plan.aisleWidth + wallThickness, plan.naveWidth)
                : Math.max(8, Js.round(plan.naveWidth * 0.5));
    }

    /** How far the chevet reaches east of the last bay, wall included. The apse is a semicircle. */
    public static double chevetDepthFor(PlanParams plan, double wallThickness, double arcadeDepth) {
        if (plan.chevet == PlanParams.Chevet.FLAT) return 0;
        double apse = Math.ceil(plan.naveWidth / 2.0) + arcadeDepth;
        return plan.chevet == PlanParams.Chevet.APSE_WITH_AMBULATORY ? apse + plan.aisleWidth + wallThickness : apse;
    }

    public static double chevetDepthFor(PlanParams plan) {
        return chevetDepthFor(plan, 2, 3);
    }

    /**
     * Bay spans. The crossing bay is square on the nave's width and the west bay is the tower's
     * size; both add length, so {@code bayWidth} still means what it says for an ordinary bay.
     */
    private static List<Bay> layoutBays(PlanParams plan, double westX, double towerSize) {
        Set<Integer> crossingBays = new HashSet<>();
        for (PlanParams.TranseptParams t : plan.transepts) crossingBays.add(t.atBay);
        int principal = plan.transepts.isEmpty() ? -1 : plan.transepts.get(0).atBay;
        List<Bay> bays = new ArrayList<>();
        double x = westX;
        for (int i = 1; i <= plan.naveBays; i++) {
            boolean isCrossing = crossingBays.contains(i);
            boolean isTowerBay = i == 1 && towerSize > 0;
            double span = isCrossing ? plan.naveWidth : isTowerBay ? towerSize : plan.bayWidth;
            boolean east = principal > 0 && i > principal;
            Bay.Kind kind = isCrossing ? Bay.Kind.CROSSING : isTowerBay ? Bay.Kind.TOWER : east ? Bay.Kind.CHOIR : Bay.Kind.NAVE;
            bays.add(new Bay(i, x, x + span, span, kind, isCrossing, east));
            x += span;
        }
        return bays;
    }

    /** The most common ordinary bay span ({@code bayWidth} whenever there is one), else the median. */
    private static double referenceSpanOf(List<Bay> bays, PlanParams plan) {
        boolean ordinary = bays.stream().anyMatch(b -> b.kind == Bay.Kind.NAVE || b.kind == Bay.Kind.CHOIR);
        if (ordinary) return plan.bayWidth;
        double[] spans = bays.stream().mapToDouble(b -> b.span).sorted().toArray();
        return spans.length > 0 ? spans[spans.length / 2] : plan.bayWidth;
    }

    private static SideBands bandsFor(PlanParams plan, double from, int direction, double arcadeDepth, double wallThickness) {
        List<Band> arcades = new ArrayList<>();
        List<Band> aisles = new ArrayList<>();
        double[] cursor = {from};
        java.util.function.DoubleFunction<Band> step = n -> {
            double a = cursor[0];
            double b = cursor[0] + direction * n;
            cursor[0] = b;
            return new Band(Math.min(a, b), Math.max(a, b));
        };
        for (int a = 1; a <= plan.aisles; a++) {
            arcades.add(step.apply(arcadeDepth));
            aisles.add(step.apply(plan.aisleWidth));
        }
        Band outerWall = step.apply(wallThickness);
        return new SideBands(List.copyOf(arcades), List.copyOf(aisles), outerWall, cursor[0]);
    }

    /**
     * Every height in the section. The storeys are proportions of the nave's clear height and
     * {@code archProfile} decides where inside a storey each arch springs. See Capella's
     * {@code resolveLevels} for the reasoning behind each constant.
     */
    private static Levels resolveLevels(PlanParams plan, double referenceSpan, double pierWidth, double arcadeDepth, double datumY) {
        double p = Math.min(1, Math.max(0, plan.archProfile));
        double floorY = datumY;
        double ringDepth = 2;
        double spandrel = 2;
        double deckThickness = 1;
        boolean threeStorey = plan.elevationStoreys == 3;
        double triforiumClear = threeStorey ? 5 : 0;
        double muralFloor = threeStorey ? 2 : 0;
        double muralClear = threeStorey ? 3 : 0;
        // With neither storey the aisle wall stops at the clerestory sill, which is then the
        // parapet over the leads: three so its top course is two over the walking surface.
        double sillUpstand = threeStorey ? 0 : 3;
        double parapet = 2;
        double stack = ringDepth + spandrel + deckThickness + triforiumClear + muralFloor + muralClear + sillUpstand;

        double arcadeOpening = Math.max(3, referenceSpan - pierWidth);
        double clerestoryOpening = Math.max(3, referenceSpan - 4);
        double aisleOpening = Math.max(3, referenceSpan - 4);

        final double MIN_CLERESTORY = 6;
        final double ARCADE_FRACTION = 0.48;

        boolean highVault = plan.naveCovering == PlanParams.NaveCovering.VAULT;
        double roofHalfSpan = Math.ceil(plan.naveWidth / 2.0) + arcadeDepth;
        double vaultRise = highVault ? Math.max(4, Js.round(plan.naveWidth * VAULT_RISE_FRACTION)) : 0;
        double coveringRise = highVault ? vaultRise : Js.round(roofHalfSpan * ROOF_PITCH) - 2;

        // The proportion rule (clear height at least twice clear width): three storeys apply it to
        // the wall alone; two ask it of the clear height and let the covering pay its share.
        double proportionFloor = threeStorey
                ? targetNaveHeight(plan.naveWidth) + muralFloor + muralClear
                : Math.max(0, targetNaveHeight(plan.naveWidth) - coveringRise);
        double elevationHeight = Math.max(proportionFloor, Math.ceil((parapet + MIN_CLERESTORY + stack) / (1 - ARCADE_FRACTION)));

        double elevationTopY = floorY + elevationHeight;
        double arcadeCrownY = floorY + Js.round(elevationHeight * ARCADE_FRACTION);
        double arcadeSpringY = springAt(p, arcadeCrownY, arcadeOpening, floorY + Js.round(elevationHeight * 0.22));
        double spandrelTopY = arcadeCrownY + ringDepth + spandrel;
        double triforiumDeckY = spandrelTopY + deckThickness;
        double triforiumHeadY = triforiumDeckY + triforiumClear;
        double muralFloorY = triforiumHeadY + muralFloor;
        double muralHeadY = muralFloorY + muralClear;
        double clerestorySillY = muralHeadY + sillUpstand;
        double clerestoryCrownY = elevationTopY - parapet;
        double clerestorySpringY = springAt(p, clerestoryCrownY, clerestoryOpening, clerestorySillY + 2);

        double vaultThickness = 2;
        double vaultSpringY = elevationTopY;
        double vaultCrownY = vaultSpringY + vaultRise;
        // Two blocks of wall head over the extrados crown so the tie beams clear the attic walk.
        double naveWallTopY = highVault ? vaultCrownY + vaultThickness + 2 : elevationTopY;
        double atticFloorY = highVault ? vaultSpringY + Js.round(vaultRise * ATTIC_FILL_FRACTION) : naveWallTopY + 1;
        double roofRidgeY = naveWallTopY + Js.round(roofHalfSpan * ROOF_PITCH);

        double aisleCrownY = spandrelTopY - 2;
        double aisleSpringY = springAt(p, aisleCrownY, aisleOpening, floorY + Js.round(elevationHeight * 0.2));
        double aisleSillY = Math.max(floorY + 3, aisleSpringY - aisleOpening * 2);

        return new Levels(
                floorY,
                floorY + 3,
                arcadeSpringY,
                arcadeCrownY,
                ringDepth,
                spandrelTopY,
                triforiumDeckY,
                triforiumHeadY,
                triforiumHeadY - triforiumDeckY >= MIN_STOREY_CLEAR,
                muralHeadY - muralFloorY >= MIN_STOREY_CLEAR,
                muralFloorY,
                muralHeadY,
                clerestorySillY,
                clerestorySpringY,
                clerestoryCrownY,
                vaultSpringY,
                vaultCrownY,
                vaultThickness,
                highVault,
                atticFloorY,
                highVault ? vaultCrownY + vaultThickness : atticFloorY,
                Math.min(aisleSpringY, triforiumDeckY - 5),
                triforiumDeckY - vaultThickness,
                naveWallTopY,
                roofRidgeY,
                ROOF_PITCH,
                aisleSillY,
                aisleSpringY,
                aisleCrownY,
                clerestorySillY,
                Math.min(clerestoryCrownY, arcadeSpringY + Js.round(Geometries.archRise(plan.naveWidth, p))),
                (highVault ? vaultCrownY : roofRidgeY - 2) - floorY);
    }

    /** Springing for an arch family: {@code crown - halfSpan} is the semicircle, {@code low} the steep extreme. */
    private static double springAt(double p, double crownY, double opening, double low) {
        double round = crownY - opening / 2;
        return Js.round(Math.min(crownY - 2, Math.max(low, round + (low - round) * p)));
    }

    /**
     * Sets the scene's cathedral out in its own frame (nave along +X) and says where that frame
     * stands. The terrain is read through the frame, so a turned building answers to the ground
     * it will stand on.
     */
    public static PlanLayout resolveLayout(Scene scene) {
        PlanParams plan = scene.plan;
        PlacementFrame frame = PlacementFrame.of(scene.originXOrDefault(), scene.originZOrDefault(), scene.yaw);
        GroundSampler world = Terrain.groundSampler(scene.terrainHeight);
        PlacementFrame.Sampler turned = frame.sampler(world::at);
        GroundSampler ground = turned::at;
        double wallThickness = 2;
        double arcadeDepth = 3;
        double towerSize = towerSizeFor(plan, wallThickness, arcadeDepth);
        double chevetDepth = chevetDepthFor(plan, wallThickness, arcadeDepth);

        List<Bay> provisional = layoutBays(plan, 0, towerSize);
        double naveLength = provisional.isEmpty() ? 0 : provisional.get(provisional.size() - 1).x1;
        double referenceSpan = referenceSpanOf(provisional, plan);
        double pierWidth = pierWidthFor(plan, referenceSpan);

        double aisleSpan = plan.aisles * (plan.aisleWidth + arcadeDepth);
        double halfWidth = Math.ceil(plan.naveWidth / 2.0) + aisleSpan + wallThickness;
        java.util.function.IntToDoubleFunction reachOf = armBays -> halfWidth + armBays * plan.bayWidth + wallThickness;
        double maxTranseptReach = halfWidth;
        for (PlanParams.TranseptParams t : plan.transepts) maxTranseptReach = Math.max(maxTranseptReach, reachOf.applyAsDouble(t.armBays));

        // The origin is the centre of the footprint in X and the nave axis in Z; rounded after
        // subtracting half the length so a default placement lands where Capella v1 put it.
        double totalLength = naveLength + chevetDepth + wallThickness * 2;
        double westX = Js.round(scene.originXOrDefault() - totalLength / 2) + wallThickness;
        double axisZ = Js.round(scene.originZOrDefault());

        List<Bay> bays = List.copyOf(layoutBays(plan, westX, towerSize));
        double eastX = westX + naveLength;

        double naveZ0 = axisZ - Math.floor(plan.naveWidth / 2.0);
        double naveZ1 = naveZ0 + plan.naveWidth;
        SideBands north = bandsFor(plan, naveZ0, -1, arcadeDepth, wallThickness);
        SideBands south = bandsFor(plan, naveZ1, 1, arcadeDepth, wallThickness);

        List<Crossing> crossings = new ArrayList<>();
        for (int at = 0; at < plan.transepts.size(); at++) {
            PlanParams.TranseptParams t = plan.transepts.get(at);
            Bay bay = null;
            for (Bay b : bays) {
                if (b.index == t.atBay) {
                    bay = b;
                    break;
                }
            }
            if (bay == null) continue;
            crossings.add(new Crossing(at, bay, at == 0 ? "" : Integer.toString(at + 1), t.armBays, reachOf.applyAsDouble(t.armBays)));
        }

        // The datum, and so every height, is solved from the ground under the plan's own footprint.
        Levels levels = resolveLevels(plan, referenceSpan, pierWidth, arcadeDepth,
                Terrain.resolveDatum(ground, new FootprintRect(westX - wallThickness, eastX + chevetDepth + 1,
                        Math.min(north.outerZ, axisZ - maxTranseptReach), Math.max(south.outerZ, axisZ + maxTranseptReach))));
        // Capella's crypt solver ran here; with no crypt and no lift it leaves the levels alone.

        Box footprint = Box.of(
                westX - wallThickness,
                levels.floorY - 24,
                Math.min(north.outerZ, axisZ - maxTranseptReach),
                eastX + chevetDepth + 1,
                Math.max(Math.max(levels.roofRidgeY, levels.floorY + plan.westTowerHeight),
                        levels.naveWallTopY + plan.crossingTower.height) + 8,
                Math.max(south.outerZ, axisZ + maxTranseptReach));

        List<Portal> portals = resolvePortals(plan, westX, axisZ, levels, wallThickness, crossings);

        // Capella resolves mezzanines and platforms (floor tiers) last; neither is ported.
        return new PlanLayout(scene.id, "cathedral", frame, plan, westX, eastX, axisZ, bays, List.copyOf(crossings),
                naveZ0, naveZ1, north, south, levels, wallThickness, arcadeDepth, pierWidth, referenceSpan, chevetDepth,
                maxTranseptReach, towerSize, portals, footprint, plan.naveWidth / 2.0, halfWidth,
                levels.naveClearHeight, levels.aisleWallTopY - levels.floorY, levels.floorY);
    }

    /**
     * The cathedral's answer to {@link StructureLayout}, in the plan's own frame; pass it
     * through {@link StructureLayout#placeLayout} with {@code layout.frame} for world space.
     */
    public static StructureLayout structureLayoutOf(PlanLayout layout) {
        return new StructureLayout(layout.id, layout.type, planRects(layout), planRects(layout), layout.portals,
                levelBands(layout.levels), layout.levels.floorY,
                new FaceAnchors(courseAnchors(layout), stripAnchorsX(layout), stripAnchorsZ(layout)),
                "no crypt: plan.crypt is false; the podium stays solid", "natural", null, null);
    }

    /** Body, chevet and transept arms; the plinth spans all of it, walls included. */
    private static List<Rect> planRects(PlanLayout layout) {
        double t = layout.wallThickness;
        List<Rect> rects = new ArrayList<>();
        double bodyX1 = layout.chevetDepth > 0 ? layout.eastX : layout.eastX + t;
        rects.add(new Rect("body", layout.westX - t, bodyX1, layout.north.outerZ, layout.south.outerZ));
        if (layout.chevetDepth > 0) {
            Geometries.Span reach = Geometries.centredSpan((layout.naveZ0 + layout.naveZ1) / 2, layout.chevetDepth);
            rects.add(new Rect("chevet", layout.eastX, layout.eastX + layout.chevetDepth + 1, reach.from(), reach.to()));
        }
        for (Crossing c : layout.crossings) {
            for (String side : new String[]{"north", "south"}) {
                int dir = side.equals("north") ? -1 : 1;
                double near = (side.equals("north") ? layout.north : layout.south).outerZ;
                double far = layout.axisZ + dir * c.reach;
                rects.add(new Rect(Crossing.armName(c, side), c.bay.x0 - t, c.bay.x1 + t, Math.min(near, far), Math.max(near, far)));
            }
        }
        return rects;
    }

    /** The levels of a great church as bands of feet height. The mural band is absent, not empty, without a passage. */
    private static List<LevelBand> levelBands(Levels L) {
        List<LevelBand> out = new ArrayList<>();
        out.add(new LevelBand("ground", L.floorY - 1, L.choirFloorY + 1));
        out.add(new LevelBand("triforium", L.triforiumDeckY, L.triforiumDeckY + 2));
        if (L.hasMuralPassage) out.add(new LevelBand("mural", L.muralFloorY, L.muralFloorY + 2));
        out.add(new LevelBand("attic", L.atticFloorY, L.atticWalkY + 2));
        out.add(new LevelBand("roof", L.naveWallTopY, Double.POSITIVE_INFINITY));
        return out;
    }

    private static List<Double> courseAnchors(PlanLayout layout) {
        Levels L = layout.levels;
        List<Double> out = new ArrayList<>();
        for (double y : new double[]{L.floorY - 1, L.aisleSillY - 1, L.aisleWallTopY - 1, L.spandrelTopY,
                L.triforiumDeckY - 1, L.muralFloorY - 1, L.clerestorySillY - 1, L.naveWallTopY - 1}) {
            if (Double.isFinite(y)) out.add(y);
        }
        return out;
    }

    private static List<Double> stripAnchorsX(PlanLayout layout) {
        List<Double> out = new ArrayList<>();
        for (Bay bay : layout.bays) {
            out.add(bay.x0);
            out.add(Js.round((bay.x0 + bay.x1) / 2));
            out.add(bay.x1);
        }
        return out;
    }

    private static List<Double> stripAnchorsZ(PlanLayout layout) {
        List<Double> out = new ArrayList<>(List.of(layout.axisZ, layout.naveZ0, layout.naveZ1));
        for (SideBands bands : new SideBands[]{layout.north, layout.south}) {
            for (Band band : bands.arcades) {
                out.add(band.z0());
                out.add(band.z1());
            }
            for (Band aisle : bands.aisles) out.add(Js.round((aisle.z0() + aisle.z1()) / 2));
            out.add(bands.outerWall.z0());
            out.add(bands.outerWall.z1());
        }
        return out;
    }

    /** The great west door and a door at the end of every transept arm. */
    private static List<Portal> resolvePortals(PlanParams plan, double westX, double axisZ, Levels levels,
                                               double wallThickness, List<Crossing> crossings) {
        List<Portal> portals = new ArrayList<>();
        double greatWidth = Math.max(4, Math.min(8, plan.naveWidth - 8));
        portals.add(new Portal(new Vec3(westX - wallThickness, levels.floorY, axisZ), Dir.WEST, greatWidth,
                Math.min(14, levels.arcadeSpringY - levels.floorY + 4), levels.floorY, Portal.Kind.WEST_GREAT, null));
        for (Crossing crossing : crossings) {
            double x = Js.round((crossing.bay.x0 + crossing.bay.x1) / 2);
            // An arm that crosses the choir stands on the raised sanctuary, so its door is higher.
            double floorY = crossing.bay.raised ? levels.choirFloorY : levels.floorY;
            for (Object[] side : new Object[][]{{Dir.NORTH, axisZ - crossing.reach}, {Dir.SOUTH, axisZ + crossing.reach}}) {
                portals.add(new Portal(new Vec3(x, floorY, (Double) side[1]), (Dir) side[0], Math.max(4, greatWidth - 2),
                        Math.min(12, levels.arcadeSpringY - floorY + 2), floorY, Portal.Kind.TRANSEPT, crossing.at));
            }
        }
        // Capella's crypt door (opening off the precinct) is not ported.
        return List.copyOf(portals);
    }
}
