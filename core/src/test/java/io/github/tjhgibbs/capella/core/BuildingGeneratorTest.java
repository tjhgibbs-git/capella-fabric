package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.Presets;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingGeneratorTest {
    @Test
    void everyPresetBuildsAndRebuildsIdentically() {
        for (Presets.Preset preset : Presets.all()) {
            if (preset.id().equals("colossal")) continue;
            BlockVolume a = BuildingGenerator.generate(preset.params(3)).volume();
            BlockVolume b = BuildingGenerator.generate(preset.params(3)).volume();
            assertTrue(a.blockCount() > 1_000, preset.id());
            assertEquals(a.palette(), b.palette(), preset.id());
            assertArrayEquals(a.data(), b.data(), preset.id());
        }
    }

    /** Every block id in every preset exists in Minecraft 26.3: the colour table lists the game's blocks. */
    @Test
    void everyBlockIdExistsInTheGame() throws Exception {
        Set<String> known = new HashSet<>();
        try (var in = BuildingGeneratorTest.class.getResourceAsStream("/capella/block_colors.txt")) {
            for (String line : new String(in.readAllBytes()).split("\n")) {
                if (!line.startsWith("#") && !line.isBlank()) known.add("minecraft:" + line.split(" ")[0]);
            }
        }
        for (Presets.Preset preset : Presets.all()) {
            if (preset.id().equals("colossal")) continue;
            for (String state : BuildingGenerator.generate(preset.params(3)).volume().palette()) {
                String id = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
                if (id.equals("minecraft:air")) continue;
                assertTrue(known.contains(id), preset.id() + ": " + id);
            }
        }
    }

    @Test
    void smallChurchesOpenOntoTheirDoor() {
        for (String id : List.of("wayside", "chapel", "village", "parish", "tower")) {
            BlockVolume v = BuildingGenerator.generate(Presets.get(id).params(1), 0).volume();
            String at = v.get(v.originX + 1, v.originY, v.originZ);
            assertTrue(at.contains("_door"), id + ": " + at);
        }
    }

    @Test
    void aQuarterTurnSwapsTheFootprintAndKeepsTheBlockCount() {
        var params = Presets.get("parish").params(1);
        BlockVolume east = BuildingGenerator.generate(params, 0).volume();
        for (int turns = 1; turns < 4; turns++) {
            BlockVolume v = BuildingGenerator.generate(params, turns).volume();
            assertEquals(east.blockCount(), v.blockCount(), "turns " + turns);
            assertEquals(east.height, v.height, "turns " + turns);
            int[] dims = turns % 2 == 0 ? new int[] {east.width, east.length} : new int[] {east.length, east.width};
            assertEquals(Arrays.toString(dims), Arrays.toString(new int[] {v.width, v.length}), "turns " + turns);
        }
    }

    @Test
    void theOriginIsTheWestDoorThreshold() {
        BlockVolume v = BuildingGenerator.generate(Presets.get("parish").params(1), 0).volume();
        // The door is on the west face, so it lies on the volume's west edge (or inside it,
        // behind the west towers' buttresses) and at the floor, not underneath the building.
        assertTrue(v.originX >= 0 && v.originX < v.width / 2, "originX " + v.originX);
        assertTrue(v.originY >= 0 && v.originY < 4, "originY " + v.originY);
        assertTrue(v.originZ > 0 && v.originZ < v.length, "originZ " + v.originZ);
    }
}
