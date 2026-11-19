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

    private String at(BlockVolume v, int turn, int x, int y, int z) {
        for (int i = 0; i < turn; i++) {
            int previousX = x;
            x = -z;
            z = previousX;
        }
        return v.get(v.originX + x, v.originY + y, v.originZ + z);
    }
}
