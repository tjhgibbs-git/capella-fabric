package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.cathedral.CathedralPlan;
import io.github.tjhgibbs.capella.core.cathedral.CathedralShell;
import io.github.tjhgibbs.capella.core.cathedral.PlanLayout;
import io.github.tjhgibbs.capella.core.cathedral.PlanParams;
import io.github.tjhgibbs.capella.core.cathedral.Scene;
import io.github.tjhgibbs.capella.core.element.ElementTree;
import io.github.tjhgibbs.capella.core.field.Field;
import io.github.tjhgibbs.capella.core.kit.TowerCap;
import io.github.tjhgibbs.capella.core.layout.Portal;
import io.github.tjhgibbs.capella.core.layout.StructureLayout;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.palette.Palettes;
import io.github.tjhgibbs.capella.core.raster.Normalize;
import io.github.tjhgibbs.capella.core.raster.Rasterizer;
import io.github.tjhgibbs.capella.core.raster.ToBlockVolume;
import io.github.tjhgibbs.capella.core.raster.Volume;
import io.github.tjhgibbs.capella.core.small.SmallChurch;

import java.util.Map;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Entry point: settings in, finished block volume out. Pure and deterministic for a given
 * params + seed + turns, and safe to call from any thread.
 *
 * <pre>
 *   BuildingParams --planOf--> PlanParams --resolveLayout--> PlanLayout
 *                  --generateShell--> ElementTree --rasterize, normalize--> BlockVolume
 * </pre>
 *
 * This is Capella's {@code generate} cut to one pristine cathedral on no ground: no footing,
 * no tail (ruin, greenery, clutter), no water.
 */
public final class BuildingGenerator {
    /** The scene is this big and the church stands in the middle, so nothing is clipped. */
    private static final double SCENE = 1024;
    /** The flat datum the plan solves its floor from; only differences of height reach the output. */
    private static final double GROUND = 64;

    private BuildingGenerator() {
    }

    public static GeneratedBuilding generate(BuildingParams params) {
        return generate(params, 0);
    }

    /**
     * @param turns quarter turns clockwise (seen from above) from the nave running east;
     *              see {@link #turnsFacing}
     */
    public static GeneratedBuilding generate(BuildingParams params, int turns) {
        if (params.get(Params.TYPE) != Params.BuildingType.CATHEDRAL) return generateSmall(params, turns);
        long t0 = System.nanoTime();
        PlanParams plan = planOf(params);
        String palette = params.get(Params.STONE).paletteId;
        Scene scene = new Scene(params.seed(), palette, SCENE, SCENE, Field.constant(Field.TERRAIN_HEIGHT, SCENE, SCENE, GROUND),
                plan, "cathedral", null, null, Math.floorMod(turns, 4) * 90, Scene.NEUTRAL_RUINATION);
        PlanLayout layout = CathedralPlan.resolveLayout(scene);
        ElementTree elements = CathedralShell.generateShell(scene, layout, "");
        long t1 = System.nanoTime();

        Volume volume = Rasterizer.rasterize(SCENE, SCENE, scene.seed, elements, new Rasterizer.Options(Palettes.get(palette))).volume();
        Normalize.normalizeVolume(volume);
        Portal door = westDoor(StructureLayout.placeLayout(CathedralPlan.structureLayoutOf(layout), layout.frame));
        var out = ToBlockVolume.convert(volume, (int) Math.floor(door.position.x()), (int) door.threshold,
                (int) Math.floor(door.position.z()));
        long t2 = System.nanoTime();
        return new GeneratedBuilding(params.copy(), Math.floorMod(turns, 4), out, elements.countsByKind(),
                new GeneratedBuilding.Timings((t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000));
    }

    /** Chapels, parish churches and towers: {@link SmallChurch}, block by block. */
    private static GeneratedBuilding generateSmall(BuildingParams params, int turns) {
        long t0 = System.nanoTime();
        SmallChurch.Kind kind = switch (params.get(Params.TYPE)) {
            case CHAPEL -> SmallChurch.Kind.CHAPEL;
            case PARISH -> SmallChurch.Kind.PARISH;
            default -> SmallChurch.Kind.TOWER;
        };
        SmallChurch.Belfry belfry = SmallChurch.Belfry.valueOf(params.get(Params.BELFRY).name());
        SmallChurch.Spec spec = new SmallChurch.Spec(kind, params.integer(Params.LENGTH), params.integer(Params.WIDTH),
                params.integer(Params.HEIGHT), belfry, cap(params.get(Params.TOWER_CAP)),
                params.get(Params.TRACERY) != Params.Tracery.LANCET, params.bool(Params.PORCH), params.get(Params.STONE), params.seed());
        Volume volume = SmallChurch.build(spec, turns);
        long t1 = System.nanoTime();
        var out = ToBlockVolume.convert(volume, 0, 0, 0);
        long t2 = System.nanoTime();
        return new GeneratedBuilding(params.copy(), Math.floorMod(turns, 4), out, Map.of(),
                new GeneratedBuilding.Timings((t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000));
    }

    /** Turns that make the nave run the way a player faces ({@code Direction.get2DDataValue()}: 0 S, 1 W, 2 N, 3 E). */
    public static int turnsFacing(int horizontalDirection) {
        return Math.floorMod(horizontalDirection + 1, 4);
    }

    private static Portal westDoor(StructureLayout placed) {
        for (Portal p : placed.portals) {
            if (p.kind == Portal.Kind.WEST_GREAT) return p;
        }
        return placed.portals.getFirst();
    }

    /** The GUI's settings as Capella's plan. */
    public static PlanParams planOf(BuildingParams p) {
        PlanParams plan = new PlanParams();
        plan.bayWidth = p.integer(Params.BAY_WIDTH);
        plan.aisles = p.integer(Params.AISLES);
        plan.naveHeight = p.integer(Params.HEIGHT);

        plan.chevet = switch (p.get(Params.CHEVET)) {
            case FLAT -> PlanParams.Chevet.FLAT;
            case APSE -> PlanParams.Chevet.APSE;
            case AMBULATORY -> PlanParams.Chevet.APSE_WITH_AMBULATORY;
        };
        plan.chapels = PlanParams.Chapels.valueOf(p.get(Params.CHAPELS).name());
        plan.crossingTower = new PlanParams.CrossingTower(p.bool(Params.CROSSING_TOWER) && !plan.transepts.isEmpty(),
                p.integer(Params.CROSSING_HEIGHT), cap(p.get(Params.CROSSING_CAP)), PlanParams.TowerAt.valueOf(p.get(Params.CROSSING_AT).name()));
        plan.westTowers = p.integer(Params.WEST_TOWERS);
        plan.westTowerHeight = p.integer(Params.WEST_TOWER_HEIGHT);
        plan.towerCap = cap(p.get(Params.TOWER_CAP));
        plan.naveCovering = PlanParams.NaveCovering.valueOf(p.get(Params.COVERING).name());
        plan.elevationStoreys = p.integer(Params.STOREYS);
        plan.vaultType = PlanParams.VaultType.valueOf(p.get(Params.VAULT).name());
        plan.archProfile = p.num(Params.ARCH_PROFILE);
        plan.pierForm = PlanParams.PierForm.valueOf(p.get(Params.PIER_FORM).name());
        plan.traceryStyle = PlanParams.TraceryStyle.valueOf(p.get(Params.TRACERY).name());
        fitSize(plan, p);
        plan.crossingTower.present = p.bool(Params.CROSSING_TOWER) && !plan.transepts.isEmpty();
        return plan;
    }

    /**
     * Chooses the nave's width (and the aisles', in proportion) and its number of bays so the body
     * comes out as close as the plan allows to the Width and Length asked for. Each guess is a
     * plan solve, which costs well under a millisecond.
     */
    private static void fitSize(PlanParams plan, BuildingParams p) {
        int wantW = p.integer(Params.WIDTH), wantL = p.integer(Params.LENGTH);
        double best = Double.MAX_VALUE;
        int bestNave = plan.naveWidth, bestAisle = plan.aisleWidth;
        for (int nave = 16; nave <= 28; nave++) {
            int aisle = Math.max(8, Math.min(14, (int) Math.round(nave * 0.45)));
            plan.naveWidth = nave;
            plan.aisleWidth = aisle;
            double err = Math.abs(bodySize(plan)[1] - wantW);
            if (err < best) {
                best = err;
                bestNave = nave;
                bestAisle = aisle;
            }
        }
        plan.naveWidth = bestNave;
        plan.aisleWidth = bestAisle;
        double atShare = p.num(Params.TRANSEPT_AT);
        int arms = p.integer(Params.TRANSEPT_ARMS);
        Params.Transepts count = p.get(Params.TRANSEPTS);
        best = Double.MAX_VALUE;
        int bestBays = plan.naveBays;
        for (int bays = 4; bays <= 12; bays++) {
            plan.naveBays = bays;
            transepts(plan, count, atShare, arms);
            double err = Math.abs(bodySize(plan)[0] - wantL);
            if (err < best) {
                best = err;
                bestBays = bays;
            }
        }
        plan.naveBays = bestBays;
        transepts(plan, count, atShare, arms);
    }

    private static void transepts(PlanParams plan, Params.Transepts count, double atShare, int arms) {
        plan.transepts = new ArrayList<>();
        if (count == Params.Transepts.NONE) return;
        int first = 1 + (int) Math.round(atShare * plan.naveBays);
        plan.transepts.add(new PlanParams.TranseptParams(first, arms));
        if (count == Params.Transepts.TWO) {
            plan.transepts.add(new PlanParams.TranseptParams((first + plan.naveBays + 1) / 2, Math.max(1, arms - 1)));
        }
        plan.constrainTransepts();
    }

    /** The body's outer length (west face to the far side of the east end) and width (aisle wall to aisle wall). */
    public static int[] bodySize(PlanParams plan) {
        Scene scene = new Scene(1, "limestone", SCENE, SCENE, Field.constant(Field.TERRAIN_HEIGHT, SCENE, SCENE, GROUND),
                plan, "cathedral", null, null, 0, Scene.NEUTRAL_RUINATION);
        PlanLayout l = CathedralPlan.resolveLayout(scene);
        double west = l.westX - l.wallThickness;
        double east = l.eastX + Math.max(l.chevetDepth + 1, l.wallThickness);
        return new int[]{(int) Math.round(east - west), (int) Math.round(l.south.outerZ - l.north.outerZ),
                (int) Math.round(l.levels.naveWallTopY - l.levels.floorY)};
    }

    private static TowerCap cap(Params.Cap c) {
        return TowerCap.valueOf(c.name().toUpperCase(Locale.ROOT));
    }
}
