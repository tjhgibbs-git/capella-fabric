package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/** Every side-porch door opens onto floor that can be walked to from the west door. */
class PorchAccessTest {
    @Test
    void porchDoorsOpenIntoReachableFloor() {
        List<String> fails = new ArrayList<>();
        int doors = 0;
        for (String preset : List.of("wayside", "chapel", "village", "parish")) {
            for (int seed = 1; seed <= 60; seed++) {
                BuildingParams p = Presets.get(preset).params(seed);
                BlockVolume v = BuildingGenerator.generate(p).volume();
                int y = v.originY;
                for (int x = 0; x < v.width; x++) for (int z = 0; z < v.length; z++) {
                    String s = v.get(x, y, z);
                    if (!s.contains("_door[") || !s.contains("half=lower")) continue;
                    int dz = z - v.originZ;
                    if (Math.abs(dz) < 2) continue; // west door
                    doors++;
                    int in = Integer.signum(dz);
                    int iz = Math.abs(dz) - 1;
                    var reached = ArchitectureTest.walk(v, new ArchitectureTest.Cell(v.originX + 2, y, v.originZ),
                            0, v.width - 1, v.originZ - iz, v.originZ + iz);
                    int cx = x, cz = z - in;
                    boolean ok = reached.contains(new ArchitectureTest.Cell(cx, y, cz));
                    if (!ok) fails.add(preset + " seed " + seed + " door x=" + (x - v.originX) + " z=" + dz
                            + " inside=" + v.get(cx, y, cz) + " / " + v.get(cx, y + 1, cz) + " below=" + v.get(cx, y - 1, cz));
                }
            }
        }
        assertTrue(doors > 100, "porches were generated: " + doors);
        assertTrue(fails.isEmpty(), fails.size() + " blocked porch doors:\n" + String.join("\n", fails));
    }
}
