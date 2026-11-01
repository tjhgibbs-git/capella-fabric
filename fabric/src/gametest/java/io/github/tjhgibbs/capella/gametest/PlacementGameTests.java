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

import io.github.tjhgibbs.capella.core.export.AxiomBlueprint;
import io.github.tjhgibbs.capella.core.export.ExportMeta;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import java.io.*;

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
    @GameTest(structure = EMPTY_64, maxTicks = 400)
    public void sunkenRoomExcavatesAndUndoRestoresGround(GameTestHelper h) {
        BlockVolume v = BuildingGenerator.generate(Presets.get("parish").params(9)).volume();
        BlockPos base = h.absolutePos(new BlockPos(4, 6, 32));
        BlockPos[] excavation = {null};
        v.forEachBlock((x,y,z,state) -> {
            if (state.equals("minecraft:cave_air") && y < v.originY) {
                excavation[0] = base.offset(x-v.originX,y-v.originY,z-v.originZ);
            }
        });
        if (excavation[0] == null) { h.fail("seed must include a sunken room"); return; }
        h.getLevel().setBlockAndUpdate(excavation[0], Blocks.STONE.defaultBlockState());
        UUID owner = UUID.randomUUID();
        BuildingPlacer.enqueue(h.getLevel(),v,base,owner,placed -> {
            if (!h.getLevel().getBlockState(excavation[0]).isAir()) { h.fail("room was not excavated"); return; }
            BuildingPlacer.undo(owner,undone -> {
                if (!h.getLevel().getBlockState(excavation[0]).is(Blocks.STONE)) { h.fail("undo did not restore ground"); return; }
                h.succeed();
            });
        });
    }

    /**
     * A .bp must decode with the codec Axiom itself uses on 26.3 (AxiomPaperPlugin's
     * VersionHelper.createPalettedContainerCodec): every block back in place, empty cells void_air.
     */
    @GameTest(structure = EMPTY_64, maxTicks = 100)
    public void axiomBlueprintDecodesWithVanillaCodec(GameTestHelper h) {
        BlockVolume v = BuildingGenerator.generate(Presets.get("parish").params(9)).volume();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new AxiomBlueprint().write(v, new ExportMeta("t", "a", McBlocks.dataVersion(), "", 0), out);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
            in.readInt();
            in.readInt();
            CompoundTag header = NbtIo.read(in);
            in.skipNBytes(in.readInt()); // thumbnail
            in.readInt();
            CompoundTag data = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            BlockState empty = Blocks.VOID_AIR.defaultBlockState();
            Codec<PalettedContainer<BlockState>> codec = PalettedContainer.codecRW(BlockState.CODEC,
                    Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), empty);
            int found = 0;
            for (Tag t : data.getListOrEmpty("BlockRegion")) {
                CompoundTag region = (CompoundTag) t;
                int sx = region.getIntOr("X", 0), sy = region.getIntOr("Y", 0), sz = region.getIntOr("Z", 0);
                PalettedContainer<BlockState> c = codec.parse(NbtOps.INSTANCE, region.getCompoundOrEmpty("BlockStates")).getOrThrow();
                for (int i = 0; i < 4096; i++) {
                    int lx = i & 15, ly = i >> 8, lz = (i >> 4) & 15;
                    int x = sx * 16 + lx + v.originX, y = sy * 16 + ly + v.originY, z = sz * 16 + lz + v.originZ;
                    boolean inside = x >= 0 && y >= 0 && z >= 0 && x < v.width && y < v.height && z < v.length;
                    BlockState got = c.get(lx, ly, lz);
                    if (!inside || v.idAt(x, y, z) == 0) {
                        if (got != empty) {
                            h.fail("expected void_air at " + x + "," + y + "," + z + ", got " + got);
                            return;
                        }
                        continue;
                    }
                    if (!got.equals(McBlocks.parse(v.get(x, y, z)))) {
                        h.fail("mismatch at " + x + "," + y + "," + z + ": " + got + " != " + v.get(x, y, z));
                        return;
                    }
                    found++;
                }
            }
            if (found != v.blockCount() || header.getIntOr("BlockCount", -1) != v.blockCount() || header.getIntOr("Version", 0) != 2) {
                h.fail("found " + found + " of " + v.blockCount() + " blocks; header " + header);
                return;
            }
        } catch (IOException e) {
            h.fail(e.toString());
            return;
        }
        h.succeed();
    }
}
