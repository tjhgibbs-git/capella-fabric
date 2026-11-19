package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.params.Presets;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HousesTest {
    @Test
    void entranceAndStairClearanceSurviveRotationAndFurnishing() {
        for (String id : List.of("townhouse", "cottage", "farmhouse")) {
            for (int turn = 0; turn < 4; turn++) for (boolean furnished : List.of(false, true)) {
                var params = Presets.get(id).params(7).set(Params.FURNISHINGS, furnished);
                var v = BuildingGenerator.generate(params, turn).volume();
                assertTrue(at(v, turn, 0, 0, 0).contains("half=lower"), id);
                assertTrue(at(v, turn, 0, 1, 0).contains("half=upper"), id);
                assertEquals(BlockVolume.AIR, at(v, turn, 1, 0, 0), id);
                assertEquals(furnished, v.palette().stream().anyMatch(s -> s.contains("_bed[")), id);
                if (id.equals("cottage")) continue;
                int start = id.equals("townhouse") ? 3 : 2;
                int z = id.equals("townhouse") ? -1 : -7;
                for (int step = 0; step <= 4; step++) {
                    assertTrue(at(v, turn, start + step, step, z).contains("spruce_stairs"), id);
                    for (int head = 1; head <= 2; head++)
                        assertEquals(BlockVolume.AIR, at(v, turn, start + step, step + head, z), id);
                }
                assertEquals(BlockVolume.AIR, at(v, turn, start + 5, 5, z), id);
            }
        }
    }

    @Test
    void roofProfilesAndWalkThroughGardenRoomsSurviveRotation() {
        for (String id : List.of("townhouse", "cottage", "farmhouse")) {
            int wall = switch (id) { case "townhouse" -> 6; case "cottage" -> 4; default -> 8; };
            int centre = id.equals("townhouse") ? 7 : 6;
            for (var roof : Params.HouseRoof.values()) for (var plan : Params.HouseLayout.values()) {
                var params = Presets.get(id).params(7).set(Params.HOUSE_ROOF, roof).set(Params.HOUSE_LAYOUT, plan);
                for (int turn = 0; turn < 4; turn++) {
                    var v = BuildingGenerator.generate(params, turn).volume();
                    if (id.equals("townhouse")) {
                        int ridge = roof == Params.HouseRoof.STEEP ? 16 : 14;
                        String cap = roof == Params.HouseRoof.GAMBREL ? "deepslate_tiles" : "slab";
                        assertTrue(at(v, turn, 5, ridge, 2).contains(cap));
                        boolean hip = roof == Params.HouseRoof.HIP;
                        assertEquals(hip, at(v, turn, -2, 9, 2).contains("stairs"));
                    }
                    if (plan == Params.HouseLayout.ORIGINAL) continue;
                    for (int z = wall; z <= wall + 2; z++) {
                        assertTrue(at(v, turn, centre, -1, z).contains("planks"));
                        for (int y = 0; y < 3; y++)
                            assertEquals(BlockVolume.AIR, at(v, turn, centre, y, z));
                    }
                    if (plan == Params.HouseLayout.MANOR) {
                        for (int x : new int[]{centre - 2, centre + 2}) {
                            assertEquals(BlockVolume.AIR, at(v, turn, x, 1, wall + 6));
                            assertTrue(at(v, turn, x, 1, wall + 8).contains("glass_pane"));
                        }
                    }
                }
            }
        }
    }

    private String at(BlockVolume v, int turn, int x, int y, int z) {
        for (int i = 0; i < turn; i++) {
            int previousX = x;
            x = -z;
            z = previousX;
        }
        return v.get(v.originX + x, v.originY + y, v.originZ + z);
    }
}
