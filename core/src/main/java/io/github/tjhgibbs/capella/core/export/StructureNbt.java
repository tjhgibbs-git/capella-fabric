package io.github.tjhgibbs.capella.core.export;

import io.github.tjhgibbs.capella.core.nbt.Nbt;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vanilla structure template ({@code .nbt}) as saved by structure blocks; load with
 * {@code /place template} (no size limit) or a structure block (48^3 limit). Air is left out,
 * so placing a tree never clears the blocks around it.
 */
public final class StructureNbt implements SchematicFormat {
    @Override
    public String id() {
        return "structure";
    }

    @Override
    public String extension() {
        return ".nbt";
    }

    @Override
    public void write(BlockVolume v, ExportMeta meta, OutputStream out) throws IOException {
        List<Object> palette = new ArrayList<>();
        for (String state : v.palette()) {
            BlockStateString s = BlockStateString.parse(state);
            Map<String, Object> entry = Nbt.compound();
            entry.put("Name", s.name());
            if (!s.properties().isEmpty()) {
                Map<String, Object> props = Nbt.compound();
                props.putAll(s.properties());
                entry.put("Properties", props);
            }
            palette.add(entry);
        }
        List<Object> blocks = new ArrayList<>(v.blockCount());
        v.forEachBlock((x, y, z, state) -> {
            Map<String, Object> b = Nbt.compound();
            b.put("pos", Nbt.ListTag.of(Nbt.INT, List.of(x, y, z)));
            b.put("state", v.idAt(x, y, z));
            blocks.add(b);
        });
        Map<String, Object> root = Nbt.compound();
        root.put("DataVersion", meta.dataVersion());
        root.put("size", Nbt.ListTag.of(Nbt.INT, List.of(v.width, v.height, v.length)));
        root.put("palette", Nbt.ListTag.of(Nbt.COMPOUND, palette));
        root.put("blocks", Nbt.ListTag.of(Nbt.COMPOUND, blocks));
        root.put("entities", Nbt.ListTag.of(Nbt.COMPOUND, List.of()));
        Nbt.writeGzip(out, "", root);
    }
}
