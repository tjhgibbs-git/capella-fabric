package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.cathedral.*;
import io.github.tjhgibbs.capella.core.element.*;
import io.github.tjhgibbs.capella.core.field.Field;
import io.github.tjhgibbs.capella.core.geom.*;
import io.github.tjhgibbs.capella.core.kit.*;
import io.github.tjhgibbs.capella.core.layout.Portal;
import io.github.tjhgibbs.capella.core.layout.StructureLayout;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import io.github.tjhgibbs.capella.core.palette.Palettes;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.raster.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CathedralTowerTransitionTest {
    private static Tower.TowerSpec tower() {
        return new Tower.TowerSpec("test", 20, 40, 20, 40, 8, 72, 3,
                List.of(), TowerCap.SPIRE, 1.8, 3, false);
    }

    private static Volume volume(Tower.TowerSpec spec) {
        ShellBuilder s = new ShellBuilder();
        Tower.buildTower(s, spec);
        return Rasterizer.rasterize(80, 80, 1, s.builder.build(),
                new Rasterizer.Options(Palettes.get("limestone"))).volume();
    }

    @Test
    void openWestTowerHasNoMasonryBarsAcrossItsChurchFacingOpening() {
        Volume v = volume(tower().open(Tower.Open.EAST).openTopY(48.0));
        for (int y = 9; y < 48; y++) {
            for (int z = 28; z <= 32; z++) {
                assertTrue(v.isAirAt(39, y, z), "east opening at y=" + y);
            }
        }
        assertFalse(v.isAirAt(39, 50, 30), "weather enclosure above abutting roof");
        assertFalse(v.isAirAt(30, 24, 20), "retain north string course");
    }

    @Test
    void TowerStoreysMeetTheWallsAndKeepOnlyTheClimbWellOpen() {
        Volume v = volume(tower().interiorFloors(true));
        // Height 64 gives four stages, with decks at the midpoint and top joint.
        for (int y : new int[]{40, 56}) {
            for (int x = 23; x < 37; x++) {
                for (int z = 23; z < 37; z++) {
                    if (x == 26 && z == 25) {
                        assertTrue(v.keyAt(x, y, z).contains("ladder"), "well contains a continuous climb");
                    } else {
                        assertFalse(v.isAirAt(x, y, z), "deck has no perimeter fall trench: " + x + "," + y + "," + z);
                    }
                }
            }
        }
        for (int y = 9; y < 58; y++) assertTrue(v.keyAt(26, y, 25).contains("ladder"));
    }

    @Test
    void CrossingTowerAtticDoorsHaveSupportedThresholdsAndThreeBlockHeadroom() {
        for (String preset : List.of("cathedral", "minster", "great", "colossal")) {
            for (int turn = 0; turn < 4; turn++) {
                BuildingParams p = Presets.get(preset).params(turn + 1);
                Scene scene = new Scene(p.seed(), "limestone", 1024, 1024,
                        Field.constant(Field.TERRAIN_HEIGHT, 1024, 1024, 64), BuildingGenerator.planOf(p),
                        "cathedral", null, null, turn * 90, Scene.NEUTRAL_RUINATION);
                PlanLayout l = CathedralPlan.resolveLayout(scene);
                ElementTree tree = CathedralShell.generateShell(scene, l, "");
                Volume v = Rasterizer.rasterize(1024, 1024, p.seed(), tree,
                        new Rasterizer.Options(Palettes.get("limestone"))).volume();
                BlockVolume finished = BuildingGenerator.generate(p, turn).volume();
                Portal westDoor = StructureLayout.placeLayout(CathedralPlan.structureLayoutOf(l), l.frame).portals.stream()
                        .filter(q -> q.kind == Portal.Kind.WEST_GREAT).findFirst().orElseThrow();
                int portals = 0;
                for (Element e : tree.elements()) {
                    if (!e.id().startsWith("aperture/crossing") || !e.id().contains("-attic-")) continue;
                    portals++;
                    Box b = e.bounds();
                    for (int x = (int)b.min().x(); x < b.max().x(); x++) {
                        for (int z = (int)b.min().z(); z < b.max().z(); z++) {
                            int y = (int)b.min().y();
                            assertFalse(v.isAirAt(x, y - 1, z), preset + " supported doorway");
                            for (int dy = 0; dy < 3; dy++) assertTrue(v.isAirAt(x, y + dy, z), preset + " clear doorway");
                            assertFalse(isAir(finishedState(finished, westDoor, x, y - 1, z)), preset + " finished threshold");
                            for (int dy = 0; dy < 3; dy++) {
                                assertTrue(isAir(finishedState(finished, westDoor, x, y + dy, z)),
                                        preset + " decorated doorway remains clear at " + x + "," + (y + dy) + "," + z);
                            }
                        }
                    }
                }
                assertTrue(portals >= 2, preset + " has access on both sides of the shaft");
                for (Crossing crossing : l.crossings) {
                    // Walk the axis through the approach, doorway, shaft and opposite exit.
                    // Roof trusses/hoods may remain overhead, but must not clip a player's body.
                    for (int x = (int)crossing.bay.x0 - 7; x <= crossing.bay.x1 + 6; x++) {
                        int feet = (int)l.levels.atticWalkY;
                        Vec3 world = l.frame.point(new Vec3(x, feet, l.axisZ));
                        int wx = (int)Math.round(world.x()), wz = (int)Math.round(world.z());
                        assertFalse(isAir(finishedState(finished, westDoor, wx, feet - 1, wz)), preset + " axis floor " + x);
                        for (int dy = 0; dy < 2; dy++) assertTrue(isAir(finishedState(finished, westDoor, wx, feet + dy, wz)),
                                preset + " finished attic axis route " + x + "," + (feet + dy));
                    }
                }
            }
        }
    }

    private static String finishedState(BlockVolume v, Portal door, int x, int y, int z) {
        return v.get(x - (int)Math.floor(door.position.x()) + v.originX,
                y - (int)Math.floor(door.threshold) + v.originY,
                z - (int)Math.floor(door.position.z()) + v.originZ);
    }

    private static boolean isAir(String state) {
        return state.endsWith(":air") || state.endsWith(":cave_air");
    }
}
