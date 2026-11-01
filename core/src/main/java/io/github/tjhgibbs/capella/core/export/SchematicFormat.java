package io.github.tjhgibbs.capella.core.export;

import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import java.io.IOException;
import java.io.OutputStream;

/** One output file format. */
public interface SchematicFormat {
    /** Short stable id used in commands and config ("sponge", "structure", "14eredit"). */
    String id();

    /** File extension including the dot, or "" for none. */
    String extension();

    void write(BlockVolume volume, ExportMeta meta, OutputStream out) throws IOException;

    static SchematicFormat byId(String id) {
        for (SchematicFormat f : all()) {
            if (f.id().equalsIgnoreCase(id)) {
                return f;
            }
        }
        return null;
    }

    static SchematicFormat[] all() {
        return new SchematicFormat[]{new AxiomBlueprint(), new SpongeSchematic(), new StructureNbt(), new FourteenerSchemLite()};
    }
}
