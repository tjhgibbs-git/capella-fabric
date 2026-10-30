package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.blockstate.BlockState;
import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.raster.RasterPass;
import io.github.tjhgibbs.capella.core.raster.Volume;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sparse block store for the small-church builders, in the church's own frame: +x east along the
 * nave, +z south, y up, the west door's threshold at the origin. {@link #toVolume} turns it to
 * face the way the player wants.
 */
final class Mason {
    private static final int OFF = 1 << 20;
    private final Map<Long, BlockState> blocks = new HashMap<>();
    private final Map<String, BlockState> cache = new HashMap<>();
    final double seed;

    Mason(double seed) {
        this.seed = seed;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + OFF) << 42) | ((long) (y + OFF) << 21) | (z + OFF);
    }

    BlockState get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    boolean air(int x, int y, int z) {
        return get(x, y, z) == null;
    }

    boolean is(int x, int y, int z, String name) {
        BlockState s = get(x, y, z);
        return s != null && s.name.equals(name);
    }

    void clear(int x, int y, int z) {
        blocks.remove(key(x, y, z));
    }

    /** {@code props} alternate name, value. */
    void set(int x, int y, int z, String name, String... props) {
        String k = props.length == 0 ? name : name + String.join(",", props);
        BlockState s = cache.get(k);
        if (s == null) {
            Map<String, String> p = new LinkedHashMap<>();
            for (int i = 0; i + 1 < props.length; i += 2) p.put(props[i], props[i + 1]);
            s = Blockstates.makeState(name, p);
            cache.put(k, s);
        }
        blocks.put(key(x, y, z), s);
    }

    void setIfAir(int x, int y, int z, String name, String... props) {
        if (air(x, y, z)) set(x, y, z, name, props);
    }

    void stairs(int x, int y, int z, String name, String facing, boolean top) {
        set(x, y, z, name, "facing", facing, "half", top ? "top" : "bottom");
    }

    void slab(int x, int y, int z, String name, boolean top) {
        set(x, y, z, name, "type", top ? "top" : "bottom");
    }

    int size() {
        return blocks.size();
    }

    /** The store turned {@code turns} quarter turns clockwise (seen from above) about the origin. */
    Volume toVolume(int turns) {
        int t = Math.floorMod(turns, 4);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Map<Long, int[]> at = new HashMap<>();
        for (long k : blocks.keySet()) {
            int x = (int) ((k >>> 42) & 0x1FFFFF) - OFF, y = (int) ((k >>> 21) & 0x1FFFFF) - OFF, z = (int) (k & 0x1FFFFF) - OFF;
            for (int i = 0; i < t; i++) {
                int nx = -z;
                z = x;
                x = nx;
            }
            at.put(k, new int[]{x, y, z});
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        // Keep the origin inside so the door threshold is always addressable.
        minX = Math.min(minX, 0);
        minY = Math.min(minY, 0);
        minZ = Math.min(minZ, 0);
        Volume v = new Volume(Box.of(minX, minY, minZ, maxX + 1, Math.max(maxY, 0) + 1, maxZ + 1));
        Map<BlockState, Integer> ids = new HashMap<>();
        for (Map.Entry<Long, BlockState> e : blocks.entrySet()) {
            int[] p = at.get(e.getKey());
            int id = ids.computeIfAbsent(e.getValue(), s -> v.intern(Blockstates.rotateState(s, t, false)));
            v.setId(p[0], p[1], p[2], id, RasterPass.STRUCTURE);
        }
        return v;
    }

    /** Horizontal directions by (dx, dz). */
    static String dir(int dx, int dz) {
        if (dx > 0) return "east";
        if (dx < 0) return "west";
        return dz > 0 ? "south" : "north";
    }
}
