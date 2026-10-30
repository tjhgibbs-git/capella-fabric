package io.github.tjhgibbs.capella.core.terrain;

import io.github.tjhgibbs.capella.core.field.Field;

import java.util.Arrays;

/**
 * Reading the terrain field as ground (Capella {@code terrain.ts}), only as far as the plan
 * needs it to solve the datum. The building floats: no footing or earthwork is ported.
 */
public final class Terrain {
    /** Blocks the floor stands above the precinct paving: the plinth's rise. */
    public static final int PLINTH_RISE = 3;
    /** Which quantile of the footprint's ground the datum is solved from. */
    public static final double DATUM_QUANTILE = 0.75;

    private Terrain() {
    }

    /** The top solid block of the ground in column (x, z). */
    public static GroundSampler groundSampler(Field terrain) {
        return (x, z) -> Math.floor(terrain.sample(x + 0.5, z + 0.5));
    }

    /** A field read at block-column centres, unfloored. */
    public static GroundSampler cellSampler(Field field) {
        return (x, z) -> field.sample(x + 0.5, z + 0.5);
    }

    /**
     * The floor level of a building over this footprint: the {@link #DATUM_QUANTILE} of its
     * ground, plus one, plus the plinth. High rather than median so most of the building stands
     * on made ground rather than in a cut.
     */
    public static double resolveDatum(GroundSampler ground, FootprintRect rect) {
        int n = (int) (Math.max(0, rect.z1() - rect.z0()) * Math.max(0, rect.x1() - rect.x0()));
        double[] heights = new double[n];
        int k = 0;
        for (double z = rect.z0(); z < rect.z1(); z++) {
            for (double x = rect.x0(); x < rect.x1(); x++) heights[k++] = ground.at(x, z);
        }
        if (k == 0) return 65 + PLINTH_RISE;
        heights = Arrays.copyOf(heights, k);
        Arrays.sort(heights);
        int index = Math.min(k - 1, (int) Math.floor((k - 1) * DATUM_QUANTILE));
        return heights[index] + 1 + PLINTH_RISE;
    }
}
