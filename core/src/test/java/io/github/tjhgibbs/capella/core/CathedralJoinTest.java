package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.cathedral.*;
import io.github.tjhgibbs.capella.core.field.Field;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.layout.Portal;
import io.github.tjhgibbs.capella.core.layout.StructureLayout;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks actual finished voxels at junctions, including the detail and furnishing passes. */
class CathedralJoinTest {
    private record Building(PlanLayout layout, BlockVolume volume, Vec3 door, int threshold) {
        String at(int x, int y, int z) {
            Vec3 p = layout.frame.point(new Vec3(x, y, z));
            return volume.get((int) Math.floor(p.x()) - (int) Math.floor(door.x()) + volume.originX,
                    y - threshold + volume.originY,
                    (int) Math.floor(p.z()) - (int) Math.floor(door.z()) + volume.originZ);
        }
    }

    private Building generate(String preset, long seed, int turns, Params.Chevet chevet) {
        BuildingParams p = Presets.get(preset).params(seed);
        if (chevet != null) p.set(Params.CHEVET, chevet);
        PlanLayout l = CathedralPlan.resolveLayout(new Scene(seed, "limestone", 1024, 1024,
                Field.constant(Field.TERRAIN_HEIGHT, 1024, 1024, 64), BuildingGenerator.planOf(p),
                "cathedral", null, null, turns * 90, Scene.NEUTRAL_RUINATION));
        Portal door = StructureLayout.placeLayout(CathedralPlan.structureLayoutOf(l), l.frame).portals.stream()
                .filter(a -> a.kind == Portal.Kind.WEST_GREAT).findFirst().orElseThrow();
        return new Building(l, BuildingGenerator.generate(p, turns).volume(), door.position, (int) door.threshold);
    }

    @Test
    void galleriesCrossEveryTranseptOnSupportedGuardedDecks() {
        for (String preset : List.of("cathedral", "minster", "great", "colossal")) {
            for (int turns : new int[]{0, 1}) {
                Building b = generate(preset, turns == 0 ? 1 : 7, turns, null);
                PlanLayout l = b.layout;
                for (Crossing c : l.crossings) for (SideBands bands : List.of(l.north, l.south)) {
                    Band aisle = bands.aisles.getLast();
                    if (aisle.z1() - aisle.z0() < 5) continue;
                    int z0 = (int) Math.floor((aisle.z0() + aisle.z1() - 5) / 2);
                    int deck = (int) l.levels.triforiumDeckY;
                    for (int x = (int) c.bay.x0; x < c.bay.x1; x++) {
                        for (int z = z0 + 1; z < z0 + 4; z++) {
                            String where = preset + " turn=" + turns + " at " + x + "," + z;
                            assertFalse(air(b.at(x, deck - 1, z)), "missing bridge deck " + where);
                            assertTrue(air(b.at(x, deck, z)), "blocked feet " + where + " " + b.at(x, deck, z));
                            assertTrue(air(b.at(x, deck + 1, z)), "blocked head " + where + " " + b.at(x, deck + 1, z));
                        }
                        assertFalse(air(b.at(x, deck, z0)), "missing north balustrade " + preset);
                        assertFalse(air(b.at(x, deck, z0 + 4)), "missing south balustrade " + preset);
                    }
                    Band arcade = bands.arcades.getFirst();
                    int firstZ = (int) (bands == l.north ? bands.outerWall.z1() : arcade.z1());
                    int lastZ = (int) (bands == l.north ? arcade.z0() : bands.outerWall.z0());
                    for (int x : new int[]{(int) c.bay.x0, (int) c.bay.x1 - 1}) {
                        for (int z = firstZ; z < lastZ; z++) {
                            if (z > z0 && z < z0 + 4) continue;
                            assertFalse(air(b.at(x, deck, z)), "unguarded gallery end beside bridge " + preset);
                            assertFalse(air(b.at(x, deck - 1, z)), "unsupported arrival guard beside bridge " + preset);
                        }
                    }
                }
                if (l.levels.hasGallery) for (Bay bay : l.bays) {
                    if (bay.isCrossing || bay.span - 4 < 6) continue;
                    int each = (int) Math.floor((bay.span - 6) / 2);
                    int x = (int) bay.x0 + 2 + each / 2;
                    for (SideBands bands : List.of(l.north, l.south)) {
                        Band arcade = bands.arcades.getFirst();
                        int lip = bands == l.north ? (int) arcade.z1() - 1 : (int) arcade.z0();
                        assertFalse(air(b.at(x, (int) l.levels.triforiumDeckY, lip)),
                                "unprotected nave-facing gallery opening " + preset + " bay " + bay.index);
                    }
                }
            }
        }
    }

    @Test
    void plainApsesEncloseAisleEndsWithoutSealingAmbulatories() {
        for (int turns : new int[]{0, 3}) for (String preset : List.of("cathedral", "great")) {
            Building b = generate(preset, 3 + turns, turns, Params.Chevet.APSE);
            PlanLayout l = b.layout;
            for (SideBands bands : List.of(l.north, l.south)) for (Band aisle : bands.aisles) {
                int z = (int) Math.floor((aisle.z0() + aisle.z1()) / 2);
                assertFalse(air(b.at((int) l.eastX, (int) l.levels.choirFloorY + 1, z)), "open aisle end " + preset);
                assertFalse(air(b.at((int) l.eastX, (int) l.levels.triforiumDeckY + 1, z)), "open gallery end " + preset);
            }
        }
        Building b = generate("great", 7, 1, Params.Chevet.AMBULATORY);
        PlanLayout l = b.layout;
        for (SideBands bands : List.of(l.north, l.south)) for (Band aisle : bands.aisles) {
            int z = (int) Math.floor((aisle.z0() + aisle.z1()) / 2);
            assertTrue(air(b.at((int) l.eastX, (int) l.levels.choirFloorY + 1, z)), "sealed ambulatory entrance");
        }
    }

    @Test
    void everyAisleHasAnApproachToTheRaisedChoir() {
        for (String preset : List.of("cathedral", "great", "minster")) {
            Building b = generate(preset, 4, 2, null);
            PlanLayout l = b.layout;
            Bay choir = l.bays.stream().filter(a -> a.kind == Bay.Kind.CHOIR).findFirst().orElseThrow();
            int rise = (int) (l.levels.choirFloorY - l.levels.floorY);
            for (SideBands bands : List.of(l.north, l.south)) for (Band aisle : bands.aisles) {
                int z = (int) Math.floor((aisle.z0() + aisle.z1()) / 2);
                for (int step = 0; step < rise; step++) {
                    int x = (int) choir.x0 - rise + step;
                    int y = (int) l.levels.floorY + step;
                    assertFalse(air(b.at(x, y, z)), "missing aisle choir step " + preset + " step " + step);
                    assertTrue(air(b.at(x, y + 1, z)), "blocked aisle choir step " + preset + " step " + step);
                    assertTrue(air(b.at(x, y + 2, z)), "low aisle choir step " + preset + " step " + step);
                }
            }
        }
    }

    private static boolean air(String s) {
        return s.equals("minecraft:air") || s.equals("minecraft:cave_air");
    }
}
