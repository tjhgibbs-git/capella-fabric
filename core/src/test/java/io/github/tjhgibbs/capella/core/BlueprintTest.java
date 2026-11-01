package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.export.*;
import io.github.tjhgibbs.capella.core.nbt.Nbt;
import io.github.tjhgibbs.capella.core.params.Presets;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintTest {
    static byte[] bytes(BlockVolume v) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new AxiomBlueprint().write(v, new ExportMeta("Test church", "tester", 4321, "", 0), out);
        return out.toByteArray();
    }
    /** Reads a .bp the way Axiom's BlueprintIo does and checks every block lands where the volume has it. */
    @Test
    void axiomBlueprintRoundTrips() throws Exception {
        BlockVolume v = BuildingGenerator.generate(Presets.get("parish").params(9)).volume();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes(v)));
        assertEquals(0x0AE5BB36, in.readInt());

        byte[] headerBytes = in.readNBytes(in.readInt());
        Map<String, Object> header = Nbt.read(new ByteArrayInputStream(headerBytes)).value();
        assertEquals(2, header.get("Version"));
        assertEquals("Test church", header.get("Name"));
        assertEquals("tester", header.get("Author"));
        assertEquals(v.blockCount(), header.get("BlockCount"));
        assertEquals((byte) 1, header.get("ContainsAir"));

        byte[] png = in.readNBytes(in.readInt());
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, Arrays.copyOf(png, 4));

        byte[] blockBytes = in.readNBytes(in.readInt());
        assertEquals(-1, in.read(), "nothing after the block data");
        Map<String, Object> data = Nbt.readGzip(new ByteArrayInputStream(blockBytes)).value();
        assertEquals(4321, data.get("DataVersion"));

        int found = 0;
        for (Object o : ((Nbt.ListTag) data.get("BlockRegion")).values()) {
            Map<String, Object> region = (Map<String, Object>) o;
            int sx = (int) region.get("X"), sy = (int) region.get("Y"), sz = (int) region.get("Z");
            Map<String, Object> states = (Map<String, Object>) region.get("BlockStates");
            List<Object> palette = ((Nbt.ListTag) states.get("palette")).values();
            assertEquals("minecraft:void_air", ((Map<String, Object>) palette.get(0)).get("id"));
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long[] packed = (long[]) states.get("data");
            int perLong = 64 / bits;
            assertEquals((4096 + perLong - 1) / perLong, packed.length, "vanilla rejects a wrong-length storage");
            for (int i = 0; i < 4096; i++) {
                int id = (int) (packed[i / perLong] >>> (i % perLong * bits) & ((1L << bits) - 1));
                int x = sx * 16 + (i & 15) + v.originX, y = sy * 16 + (i >> 8) + v.originY, z = sz * 16 + ((i >> 4) & 15) + v.originZ;
                Map<String, Object> entry = (Map<String, Object>) palette.get(id);
                boolean inside = x >= 0 && y >= 0 && z >= 0 && x < v.width && y < v.height && z < v.length;
                String expected = inside ? v.get(x, y, z) : BlockVolume.AIR;
                if (expected.equals(BlockVolume.AIR)) {
                    assertEquals(0, id, "empty cells are void_air");
                    continue;
                }
                BlockStateString s = BlockStateString.parse(expected);
                assertEquals(s.name(), entry.get("id"));
                assertEquals(s.properties(), entry.getOrDefault("properties", Map.of()));
                found++;
            }
        }
        assertEquals(v.blockCount(), found);
    }

    @Test
    void largeBuildingsAlsoFitTheBlueprintSectionPalettes() throws Exception {
        assertEquals(".bp", SchematicFormat.byId("axiom").extension());
        for (String id : List.of("cathedral", "great", "westtower")) {
            var p = Presets.get(id).params(7);
            if (id.equals("westtower")) p.set(Params.HEIGHT,160);
            assertTrue(bytes(BuildingGenerator.generate(p).volume()).length > 1000, id);
        }
    }
}
