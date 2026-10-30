package io.github.tjhgibbs.capella.gametest;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.GeneratedBuilding;
import io.github.tjhgibbs.capella.core.params.Presets;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import io.github.tjhgibbs.capella.mod.BuildingPlacer;
import io.github.tjhgibbs.capella.mod.McBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Server-side checks that need the real game: every block state the generator emits must parse
 * in the real registry, and a placed building must match its volume block for block, then undo
 * cleanly.
 */
public class PlacementGameTests {
    private static final String EMPTY_64 = "capella-gametest:empty64";
    private static final String STRUCTURE_VOID = "minecraft:structure_void";

    @GameTest(structure = EMPTY_64, maxTicks = 100)
    public void everyPresetEmitsValidBlockStates(GameTestHelper h) {
        for (Presets.Preset preset : Presets.all()) {
            if (preset.id().equals("colossal")) {
                continue; // same vocabulary as "great", four times the blocks
            }
            BlockVolume v = BuildingGenerator.generate(preset.params(1)).volume();
            for (String state : v.palette()) {
                if (!state.equals(STRUCTURE_VOID) && !McBlocks.isValid(state)) {
                    h.fail(preset.id() + " emits invalid state " + state);
                    return;
                }
            }
        }
        h.succeed();
    }

    @GameTest(structure = EMPTY_64, maxTicks = 400)
    public void placedChapelMatchesItsVolumeAndUndoes(GameTestHelper h) {
        GeneratedBuilding building = BuildingGenerator.generate(Presets.get("chapel").params(7));
        BlockVolume v = building.volume();
        BlockPos base = h.absolutePos(new BlockPos(4, 1, 32));
        UUID owner = UUID.randomUUID();
        BuildingPlacer.enqueue(h.getLevel(), v, base, owner, placed -> {
            for (int y = 0; y < v.height; y++) {
                for (int z = 0; z < v.length; z++) {
                    for (int x = 0; x < v.width; x++) {
                        String state = v.get(x, y, z);
                        if (v.idAt(x, y, z) == 0 || state.equals(STRUCTURE_VOID)) {
                            continue;
                        }
                        BlockPos p = base.offset(x - v.originX, y - v.originY, z - v.originZ);
                        BlockState expected = McBlocks.parse(state);
                        if (!h.getLevel().getBlockState(p).equals(expected)) {
                            h.fail("mismatch at " + p + ": " + h.getLevel().getBlockState(p) + " != " + expected);
                            return;
                        }
                    }
                }
            }
            BuildingPlacer.undo(owner, undone -> {
                BlockPos above = base.above(3);
                if (!h.getLevel().getBlockState(above).isAir()) {
                    h.fail("undo left " + h.getLevel().getBlockState(above) + " above the threshold");
                    return;
                }
                h.succeed();
            });
        });
    }
}
