package io.github.tjhgibbs.capella.core.raster;

import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

/**
 * Copies a finished {@link Volume} into the exporters' {@link BlockVolume}: same box, Sponge
 * order, one palette string per cell from {@link Volume#exportKeyAt} (so structure-void cells
 * export as {@code minecraft:structure_void}).
 */
public final class ToBlockVolume {
    private ToBlockVolume() {
    }

    /**
     * @param originX world x of the volume's "place here" point; stored relative to the volume's
     *                minimum corner, as {@link BlockVolume} expects (likewise y and z)
     */
    public static BlockVolume convert(Volume v, int originX, int originY, int originZ) {
        BlockVolume out = new BlockVolume(v.sizeX, v.sizeY, v.sizeZ, originX - v.minX, originY - v.minY, originZ - v.minZ);
        String[] keys = new String[v.paletteSize()];
        for (int y = 0; y < v.sizeY; y++) {
            for (int z = 0; z < v.sizeZ; z++) {
                for (int x = 0; x < v.sizeX; x++) {
                    int wx = x + v.minX, wy = y + v.minY, wz = z + v.minZ;
                    int i = v.index(wx, wy, wz);
                    String key;
                    if (v.states[i] == 0) {
                        if (!v.isVoidAt(wx, wy, wz)) continue;
                        key = v.exportKeyAt(wx, wy, wz);
                    } else {
                        int id = v.states[i];
                        if (id >= keys.length) keys = java.util.Arrays.copyOf(keys, v.paletteSize());
                        key = keys[id] != null ? keys[id] : (keys[id] = v.keyOfId(id));
                    }
                    out.set(x, y, z, key);
                }
            }
        }
        return out;
    }
}
