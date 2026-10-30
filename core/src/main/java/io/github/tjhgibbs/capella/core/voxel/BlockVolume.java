package io.github.tjhgibbs.capella.core.voxel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The finished tree: a dense box of palette indices. Index 0 is always {@code minecraft:air}.
 * Layout is Sponge-schematic order, {@code (y * length + z) * width + x}. {@code originX/Y/Z}
 * is where the tree's base block (the "place here" point) sits inside the box.
 */
public final class BlockVolume {
    public static final String AIR = "minecraft:air";

    public final int width, height, length;
    public final int originX, originY, originZ;
    private final int[] data;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private int solidCount;

    public BlockVolume(int width, int height, int length, int originX, int originY, int originZ) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.data = new int[Math.multiplyExact(Math.multiplyExact(width, height), length)];
        intern(AIR);
    }

    public int intern(String state) {
        return paletteIndex.computeIfAbsent(state, s -> {
            palette.add(s);
            return palette.size() - 1;
        });
    }

    public int index(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    public void set(int x, int y, int z, String state) {
        int i = index(x, y, z);
        int id = intern(state);
        if (data[i] == 0 && id != 0) {
            solidCount++;
        } else if (data[i] != 0 && id == 0) {
            solidCount--;
        }
        data[i] = id;
    }

    public int idAt(int x, int y, int z) {
        return data[index(x, y, z)];
    }

    public String get(int x, int y, int z) {
        return palette.get(data[index(x, y, z)]);
    }

    public List<String> palette() {
        return Collections.unmodifiableList(palette);
    }

    /** Raw palette-index array in Sponge order; do not modify. */
    public int[] data() {
        return data;
    }

    /** Number of non-air blocks. */
    public int blockCount() {
        return solidCount;
    }

    public interface Visitor {
        void visit(int x, int y, int z, String state);
    }

    /** Visits non-air blocks bottom-up (y, then z, then x), in volume coordinates. */
    public void forEachBlock(Visitor v) {
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int id = data[index(x, y, z)];
                    if (id != 0) {
                        v.visit(x, y, z, palette.get(id));
                    }
                }
            }
        }
    }
}
