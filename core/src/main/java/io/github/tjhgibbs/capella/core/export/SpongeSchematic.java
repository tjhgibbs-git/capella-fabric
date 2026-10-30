package io.github.tjhgibbs.capella.core.export;

import io.github.tjhgibbs.capella.core.nbt.Nbt;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * Sponge Schematic v3 ({@code .schem}): read by WorldEdit, FAWE, Axiom (blueprint import),
 * Litematica's converter and most other editors. {@code Offset} is the box's minimum corner
 * relative to the tree base, so pasting puts the base of the trunk at the paste position.
 */
public final class SpongeSchematic implements SchematicFormat {
    @Override
    public String id() {
        return "sponge";
    }

    @Override
    public String extension() {
        return ".schem";
    }

    @Override
    public void write(BlockVolume v, ExportMeta meta, OutputStream out) throws IOException {
        if (v.width > 0xFFFF || v.height > 0xFFFF || v.length > 0xFFFF) {
            throw new IOException("volume too large for a schematic");
        }
        Map<String, Object> palette = Nbt.compound();
        List<String> states = v.palette();
        for (int i = 0; i < states.size(); i++) {
            palette.put(states.get(i), i);
        }
        Map<String, Object> blocks = Nbt.compound();
        blocks.put("Palette", palette);
        blocks.put("Data", varints(v.data()));
        blocks.put("BlockEntities", Nbt.ListTag.of(Nbt.COMPOUND, List.of()));

        Map<String, Object> metadata = Nbt.compound();
        metadata.put("Name", meta.name());
        metadata.put("Author", meta.author());
        metadata.put("Date", meta.createdMillis());
        Map<String, Object> capella = Nbt.compound();
        capella.put("Params", meta.params());
        metadata.put("Capella", capella);

        Map<String, Object> schem = Nbt.compound();
        schem.put("Version", 3);
        schem.put("DataVersion", meta.dataVersion());
        schem.put("Metadata", metadata);
        schem.put("Width", (short) v.width);
        schem.put("Height", (short) v.height);
        schem.put("Length", (short) v.length);
        schem.put("Offset", new int[]{-v.originX, -v.originY, -v.originZ});
        schem.put("Blocks", blocks);

        Map<String, Object> root = Nbt.compound();
        root.put("Schematic", schem);
        Nbt.writeGzip(out, "", root);
    }

    /** Palette indices as unsigned LEB128 varints, the Sponge "Data" encoding. */
    static byte[] varints(int[] data) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(data.length);
        for (int value : data) {
            int v = value;
            while ((v & ~0x7F) != 0) {
                b.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            b.write(v);
        }
        return b.toByteArray();
    }

    /** Inverse of {@link #varints}; used by tests and by importers. */
    public static int[] decodeVarints(byte[] bytes, int count) {
        int[] out = new int[count];
        int i = 0, pos = 0;
        while (i < count) {
            int value = 0, shift = 0, b;
            do {
                b = bytes[pos++] & 0xFF;
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            out[i++] = value;
        }
        return out;
    }
}
