package io.github.tjhgibbs.capella.core.preview;

import java.util.ArrayList;
import java.util.List;

/**
 * Approximate collision-style boxes for the non-cube blocks a tree uses, so previews show
 * fences as posts and rails rather than as solid cubes. Boxes are {x0,y0,z0,x1,y1,z1} in block
 * units; null means a full cube.
 */
final class BlockShapes {
    private BlockShapes() {
    }

    static double[][] boxes(String state) {
        String id = state.indexOf('[') >= 0 ? state.substring(0, state.indexOf('[')) : state;
        List<double[]> out = new ArrayList<>();
        if (id.endsWith("_fence") || id.endsWith("_bars") || id.endsWith("_pane")) {
            double p0 = id.endsWith("_fence") ? 6 / 16.0 : 7 / 16.0, p1 = 1 - p0;
            out.add(new double[]{p0, 0, p0, p1, 1, p1});
            arms(state, out, "true", 7 / 16.0, 9 / 16.0, 6 / 16.0, 15 / 16.0);
        } else if (id.endsWith("_wall")) {
            if (!state.contains("up=false")) {
                out.add(new double[]{4 / 16.0, 0, 4 / 16.0, 12 / 16.0, 1, 12 / 16.0});
            }
            arms(state, out, "low", 5 / 16.0, 11 / 16.0, 0, 14 / 16.0);
            arms(state, out, "tall", 5 / 16.0, 11 / 16.0, 0, 1);
        } else if (id.endsWith(":vine")) {
            double t = 1 / 16.0;
            if (state.contains("north=true")) out.add(new double[]{0, 0, 0, 1, 1, t});
            if (state.contains("south=true")) out.add(new double[]{0, 0, 1 - t, 1, 1, 1});
            if (state.contains("east=true")) out.add(new double[]{1 - t, 0, 0, 1, 1, 1});
            if (state.contains("west=true")) out.add(new double[]{0, 0, 0, t, 1, 1});
            if (state.contains("up=true")) out.add(new double[]{0, 1 - t, 0, 1, 1, 1});
        } else if (id.contains("weeping_vines") || id.contains("hanging_moss") || id.contains("cave_vines")
                || id.endsWith(":chain") || id.endsWith("end_rod")) {
            out.add(new double[]{5 / 16.0, 0, 5 / 16.0, 11 / 16.0, 1, 11 / 16.0});
        } else {
            return null;
        }
        return out.toArray(new double[0][]);
    }

    /** Adds a horizontal arm from the centre to each side whose property equals {@code value}. */
    private static void arms(String state, List<double[]> out, String value, double w0, double w1, double y0, double y1) {
        if (state.contains("north=" + value)) out.add(new double[]{w0, y0, 0, w1, y1, 0.5});
        if (state.contains("south=" + value)) out.add(new double[]{w0, y0, 0.5, w1, y1, 1});
        if (state.contains("west=" + value)) out.add(new double[]{0, y0, w0, 0.5, y1, w1});
        if (state.contains("east=" + value)) out.add(new double[]{0.5, y0, w0, 1, y1, w1});
    }
}
