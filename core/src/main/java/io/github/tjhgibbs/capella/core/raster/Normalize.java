package io.github.tjhgibbs.capella.core.raster;

import io.github.tjhgibbs.capella.core.blockstate.BlockState;
import io.github.tjhgibbs.capella.core.blockstate.Blockstates;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * The last pass over a finished volume (Capella {@code raster/normalize.ts}): stair shapes,
 * wall/fence/pane connections, and multiface faces, each worked out from the neighbours as they
 * stand. Stairs and connections read a snapshot, so the order the volume is swept in cannot
 * change the answer. Waterlogging is not ported (no water in this port).
 */
public final class Normalize {
    private Normalize() {
    }

    public record Stats(int stairsAdjusted, int connectionsAdjusted, int multifaceAdjusted, int multifaceDropped) {
    }

    public static Stats normalizeVolume(Volume volume) {
        int stairs = normalizePass(volume, Blockstates::isStairs, Blockstates::normaliseStair);
        int connections = normalizePass(volume, Blockstates::hasConnections, Blockstates::connectionState);
        int[] multiface = normalizeMultiface(volume);
        return new Stats(stairs, connections, multiface[0], multiface[1]);
    }

    /** Top down, so a vine hanging from a vine above it sees that one's final faces. Returns {adjusted, dropped}. */
    public static int[] normalizeMultiface(Volume volume) {
        Set<Integer> relevant = new HashSet<>();
        for (int id = 1; id < volume.paletteSize(); id++) {
            if (Blockstates.isMultiface(volume.stateOfId(id))) relevant.add(id);
        }
        if (relevant.isEmpty()) return new int[]{0, 0};
        int adjusted = 0, dropped = 0;
        int[] c = new int[3];
        Blockstates.StateLookup lookup = (dx, dy, dz) -> volume.at(c[0] + dx, c[1] + dy, c[2] + dz);
        for (int y = volume.minY + volume.sizeY - 1; y >= volume.minY; y--) {
            for (int z = volume.minZ; z < volume.minZ + volume.sizeZ; z++) {
                for (int x = volume.minX; x < volume.minX + volume.sizeX; x++) {
                    int id = volume.idAt(x, y, z);
                    if (!relevant.contains(id)) continue;
                    c[0] = x;
                    c[1] = y;
                    c[2] = z;
                    BlockState next = Blockstates.multifaceState(volume.stateOfId(id), lookup);
                    if (!Blockstates.multifaceAttached(next)) {
                        volume.setId(x, y, z, 0, RasterPass.NONE);
                        dropped++;
                        continue;
                    }
                    int nextId = volume.intern(next);
                    relevant.add(nextId);
                    if (nextId == id) continue;
                    volume.setId(x, y, z, nextId, volume.passAt(x, y, z));
                    adjusted++;
                }
            }
        }
        return new int[]{adjusted, dropped};
    }

    private static int normalizePass(Volume volume, Predicate<BlockState> applies,
                                     BiFunction<BlockState, Blockstates.StateLookup, BlockState> compute) {
        int paletteSize = volume.paletteSize();
        boolean[] relevant = new boolean[paletteSize];
        boolean any = false;
        for (int id = 0; id < paletteSize; id++) {
            if (applies.test(volume.stateOfId(id))) {
                relevant[id] = true;
                any = true;
            }
        }
        if (!any) return 0;
        int[] snapshot = volume.snapshot();
        int[] c = new int[3];
        Blockstates.StateLookup lookup = (dx, dy, dz) -> {
            int x = c[0] + dx, y = c[1] + dy, z = c[2] + dz;
            return volume.contains(x, y, z) ? volume.stateOfId(snapshot[volume.index(x, y, z)]) : volume.stateOfId(0);
        };
        int adjusted = 0;
        int i = 0;
        for (int y = 0; y < volume.sizeY; y++) {
            for (int z = 0; z < volume.sizeZ; z++) {
                for (int x = 0; x < volume.sizeX; x++, i++) {
                    int id = snapshot[i];
                    if (id >= paletteSize || !relevant[id]) continue;
                    c[0] = x + volume.minX;
                    c[1] = y + volume.minY;
                    c[2] = z + volume.minZ;
                    BlockState current = volume.stateOfId(id);
                    BlockState next = compute.apply(current, lookup);
                    if (next == current) continue;
                    int nextId = volume.intern(next);
                    if (nextId == id) continue;
                    volume.states[i] = nextId;
                    adjusted++;
                }
            }
        }
        return adjusted;
    }
}
