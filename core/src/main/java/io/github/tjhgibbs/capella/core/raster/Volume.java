package io.github.tjhgibbs.capella.core.raster;

import io.github.tjhgibbs.capella.core.blockstate.BlockState;
import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.hash.Hasher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A dense box of interned block states plus, per voxel, the pass that wrote it (Capella
 * {@code volume.ts}). Index 0 is always air. Layout is {@code (y * sizeZ + z) * sizeX + x}.
 * Coordinates are world block coordinates; reads outside the box answer air.
 */
public final class Volume {
    public final int minX, minY, minZ;
    public final int sizeX, sizeY, sizeZ;
    public final int[] states;
    public final byte[] passes;

    private final List<String> paletteKeys = new ArrayList<>();
    private final List<BlockState> paletteStates = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();

    public Volume(Box bounds) {
        Vec3 size = bounds.size();
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) throw new IllegalArgumentException("Volume: empty bounds " + bounds);
        minX = (int) bounds.min().x();
        minY = (int) bounds.min().y();
        minZ = (int) bounds.min().z();
        sizeX = (int) size.x();
        sizeY = (int) size.y();
        sizeZ = (int) size.z();
        states = new int[Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ)];
        passes = new byte[states.length];
        intern(Blockstates.AIR);
    }

    public Box bounds() {
        return Box.of(minX, minY, minZ, minX + sizeX, minY + sizeY, minZ + sizeZ);
    }

    public int length() {
        return states.length;
    }

    public int paletteSize() {
        return paletteKeys.size();
    }

    public int intern(BlockState state) {
        String key = state.key();
        Integer existing = paletteIndex.get(key);
        if (existing != null) return existing;
        int id = paletteKeys.size();
        paletteKeys.add(key);
        paletteStates.add(state);
        paletteIndex.put(key, id);
        return id;
    }

    public int internKey(String key) {
        Integer existing = paletteIndex.get(key);
        if (existing != null) return existing;
        return intern(Blockstates.parseState(key));
    }

    public BlockState stateOfId(int id) {
        return paletteStates.get(id);
    }

    public String keyOfId(int id) {
        return paletteKeys.get(id);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && y >= minY && z >= minZ && x < minX + sizeX && y < minY + sizeY && z < minZ + sizeZ;
    }

    public int index(int x, int y, int z) {
        return ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
    }

    public int idAt(int x, int y, int z) {
        if (!contains(x, y, z)) return 0;
        return states[index(x, y, z)];
    }

    public BlockState at(int x, int y, int z) {
        return stateOfId(idAt(x, y, z));
    }

    public String keyAt(int x, int y, int z) {
        return keyOfId(idAt(x, y, z));
    }

    public boolean isAirAt(int x, int y, int z) {
        return idAt(x, y, z) == 0;
    }

    public boolean isVoidAt(int x, int y, int z) {
        if (!contains(x, y, z)) return false;
        int i = index(x, y, z);
        return states[i] == 0 && passes[i] == RasterPass.VOID.code();
    }

    /** Marks an untouched air cell as structure void; false if anything already wrote it. */
    public boolean markVoid(int x, int y, int z) {
        if (!contains(x, y, z)) return false;
        int i = index(x, y, z);
        if (states[i] != 0 || passes[i] != RasterPass.NONE.code()) return false;
        passes[i] = RasterPass.VOID.code();
        return true;
    }

    /** The key a file writer should emit: structure void where marked, the state otherwise. */
    public String exportKeyAt(int x, int y, int z) {
        return isVoidAt(x, y, z) ? Blockstates.STRUCTURE_VOID_KEY : keyAt(x, y, z);
    }

    public boolean setId(int x, int y, int z, int id, RasterPass pass) {
        if (!contains(x, y, z)) return false;
        int i = index(x, y, z);
        states[i] = id;
        passes[i] = pass.code();
        return true;
    }

    public boolean set(int x, int y, int z, BlockState state, RasterPass pass) {
        return setId(x, y, z, intern(state), pass);
    }

    public RasterPass passAt(int x, int y, int z) {
        if (!contains(x, y, z)) return RasterPass.NONE;
        return RasterPass.ofCode(passes[index(x, y, z)]);
    }

    public interface SolidVisitor {
        void visit(int x, int y, int z, int id);
    }

    /** Visits every non-air voxel, y then z then x, in world coordinates. */
    public void forEachSolid(SolidVisitor visit) {
        int i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, i++) {
                    int id = states[i];
                    if (id != 0) visit.visit(x + minX, y + minY, z + minZ, id);
                }
            }
        }
    }

    public int solidCount() {
        int n = 0;
        for (int s : states) if (s != 0) n++;
        return n;
    }

    public byte[] occupancyMask() {
        byte[] mask = new byte[states.length];
        for (int i = 0; i < states.length; i++) mask[i] = (byte) (states[i] == 0 ? 0 : 1);
        return mask;
    }

    /** Block key to count, most common first; structure-void cells counted under its key. */
    public Map<String, Integer> blockCounts() {
        Map<Integer, Integer> counts = new TreeMap<>();
        int voids = 0;
        for (int i = 0; i < states.length; i++) {
            int id = states[i];
            if (id == 0) {
                if (passes[i] == RasterPass.VOID.code()) voids++;
                continue;
            }
            counts.merge(id, 1, Integer::sum);
        }
        Map<String, Integer> merged = new HashMap<>();
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) merged.merge(keyOfId(e.getKey()), e.getValue(), Integer::sum);
        if (voids > 0) merged.merge(Blockstates.STRUCTURE_VOID_KEY, voids, Integer::sum);
        List<Map.Entry<String, Integer>> out = new ArrayList<>(merged.entrySet());
        out.sort((a, b) -> b.getValue() - a.getValue() != 0 ? b.getValue() - a.getValue() : a.getKey().compareTo(b.getKey()));
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : out) result.put(e.getKey(), e.getValue());
        return result;
    }

    /** Sorted palette (air first) and data remapped onto it; the basis of {@link #contentHash}. */
    public record Canonical(List<String> palette, int[] data) {
    }

    public Canonical canonical() {
        boolean[] seen = new boolean[paletteKeys.size()];
        int voids = 0;
        for (int i = 0; i < states.length; i++) {
            int id = states[i];
            if (id == 0 && passes[i] == RasterPass.VOID.code()) {
                voids++;
                continue;
            }
            seen[id] = true;
        }
        List<String> keys = new ArrayList<>();
        for (int id = 0; id < seen.length; id++) if (seen[id]) keys.add(keyOfId(id));
        if (voids > 0 && !keys.contains(Blockstates.STRUCTURE_VOID_KEY)) keys.add(Blockstates.STRUCTURE_VOID_KEY);
        keys.sort(null);
        keys.remove("minecraft:air");
        keys.add(0, "minecraft:air");
        Map<String, Integer> keyToNew = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) keyToNew.put(keys.get(i), i);
        int[] remap = new int[paletteKeys.size()];
        for (int id = 0; id < paletteKeys.size(); id++) remap[id] = keyToNew.getOrDefault(paletteKeys.get(id), 0);
        int[] data = new int[states.length];
        int voidIndex = voids > 0 ? keyToNew.get(Blockstates.STRUCTURE_VOID_KEY) : -1;
        for (int i = 0; i < states.length; i++) {
            int id = states[i];
            data[i] = voids > 0 && id == 0 && passes[i] == RasterPass.VOID.code() ? voidIndex : remap[id];
        }
        return new Canonical(keys, data);
    }

    /** Same digest as Capella's {@code contentHash} for the same blocks. */
    public String contentHash() {
        Canonical c = canonical();
        Hasher h = new Hasher();
        h.str("volume/v1");
        h.num(minX).num(minY).num(minZ);
        h.u32(sizeX).u32(sizeY).u32(sizeZ);
        h.u32(c.palette().size());
        for (String k : c.palette()) h.str(k);
        int[] data = c.data();
        int runValue = data.length > 0 ? data[0] : 0;
        int runLength = 0;
        for (int v : data) {
            if (v == runValue) {
                runLength++;
            } else {
                h.u32(runLength).u32(runValue);
                runValue = v;
                runLength = 1;
            }
        }
        h.u32(runLength).u32(runValue);
        return h.hex();
    }

    public String occupancyHash() {
        Hasher h = new Hasher();
        h.str("occupancy/v1");
        h.num(minX).num(minY).num(minZ);
        h.u32(sizeX).u32(sizeY).u32(sizeZ);
        h.bytes(occupancyMask());
        return h.hex();
    }

    /** A new volume holding this one's solid voxels inside {@code region}. */
    public Volume extract(Box region) {
        Box clipped = region.intersection(bounds());
        Volume out = new Volume(clipped);
        for (int y = (int) clipped.min().y(); y < clipped.max().y(); y++) {
            for (int z = (int) clipped.min().z(); z < clipped.max().z(); z++) {
                for (int x = (int) clipped.min().x(); x < clipped.max().x(); x++) {
                    int id = idAt(x, y, z);
                    if (id == 0) continue;
                    out.setId(x, y, z, out.intern(stateOfId(id)), passAt(x, y, z));
                }
            }
        }
        return out;
    }

    public Volume copy() {
        Volume out = new Volume(bounds());
        System.arraycopy(states, 0, out.states, 0, states.length);
        System.arraycopy(passes, 0, out.passes, 0, passes.length);
        out.paletteKeys.clear();
        out.paletteStates.clear();
        out.paletteIndex.clear();
        out.paletteKeys.addAll(paletteKeys);
        out.paletteStates.addAll(paletteStates);
        for (int id = 0; id < paletteKeys.size(); id++) out.paletteIndex.put(paletteKeys.get(id), id);
        return out;
    }

    /** Raw state ids, for passes that snapshot before writing. */
    public int[] snapshot() {
        return Arrays.copyOf(states, states.length);
    }
}
