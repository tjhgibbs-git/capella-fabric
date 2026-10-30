package io.github.tjhgibbs.capella.core.raster;

/**
 * Which pass last wrote a voxel. Later passes overwrite earlier ones; {@code VOID} marks a cell
 * that exports as {@code minecraft:structure_void} (leave the world's block alone).
 */
public enum RasterPass {
    NONE, STRUCTURE, FLOORS, APERTURES, DECORATION, DEBRIS, BACKFILL, VOID;

    public static RasterPass ofCode(int code) {
        return values()[code];
    }

    public byte code() {
        return (byte) ordinal();
    }
}
