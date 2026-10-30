package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import java.util.Map;

/**
 * Result of {@link BuildingGenerator#generate}. {@code volume}'s origin is the west door's
 * threshold: the block a player stands on to walk in.
 */
public record GeneratedBuilding(BuildingParams params, int turns, BlockVolume volume, Map<String, Integer> elementsByKind,
                                Timings timings) {

    public record Timings(long planMs, long rasterMs) {
        public long totalMs() {
            return planMs + rasterMs;
        }
    }

    public String summary() {
        int elements = elementsByKind.values().stream().mapToInt(Integer::intValue).sum();
        return String.format("%dx%dx%d, %d blocks, %d elements, %d ms", volume.width, volume.height, volume.length,
                volume.blockCount(), elements, timings.totalMs());
    }
}
