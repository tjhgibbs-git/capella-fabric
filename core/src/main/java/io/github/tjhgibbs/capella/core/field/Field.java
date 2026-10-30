package io.github.tjhgibbs.capella.core.field;

import io.github.tjhgibbs.capella.core.util.Js;

import java.util.Arrays;

/**
 * A painted 2D scalar layer, read side only (Capella {@code field.ts}). A grid of
 * {@code width x height} samples spaced {@code resolution} blocks apart; reads outside the grid
 * clamp to its edge. The port carries only {@code terrainHeight}; {@link #constant} is what a
 * scene's {@code { "const": 64 }} becomes, and {@link #grid} is how sampled world terrain can be
 * fed in.
 */
public final class Field {
    public enum Interpolation {
        BILINEAR, NEAREST
    }

    /** The parts of a {@code FieldSpec} the read side needs. */
    public record Spec(String name, boolean f32, int resolution, double defaultValue, Interpolation interpolation,
                       double min, double max) {
    }

    public static final Spec TERRAIN_HEIGHT = new Spec("terrainHeight", true, 1, 64, Interpolation.BILINEAR, -64, 320);

    public final Spec spec;
    public final int width;
    public final int height;
    private final double[] data;

    private Field(Spec spec, int width, int height, double[] data) {
        if (data.length != width * height) throw new IllegalArgumentException("field '" + spec.name() + "': bad data length");
        this.spec = spec;
        this.width = width;
        this.height = height;
        this.data = data;
    }

    private static int[] gridFor(Spec spec, double boundsX, double boundsZ) {
        return new int[]{(int) Math.ceil(boundsX / spec.resolution()) + 1, (int) Math.ceil(boundsZ / spec.resolution()) + 1};
    }

    /** A field over scene bounds holding one value everywhere (clamped and stored as the spec's type). */
    public static Field constant(Spec spec, double boundsX, double boundsZ, double value) {
        int[] wh = gridFor(spec, boundsX, boundsZ);
        Field f = new Field(spec, wh[0], wh[1], new double[wh[0] * wh[1]]);
        Arrays.fill(f.data, f.store(f.clamp(value)));
        return f;
    }

    /** A field over scene bounds at the spec's default value. */
    public static Field defaults(Spec spec, double boundsX, double boundsZ) {
        return constant(spec, boundsX, boundsZ, spec.defaultValue());
    }

    /** A field from explicit grid samples, row-major {@code gz * width + gx}. */
    public static Field grid(Spec spec, int width, int height, double[] samples) {
        Field f = new Field(spec, width, height, new double[width * height]);
        for (int i = 0; i < samples.length; i++) f.data[i] = f.store(f.clamp(samples[i]));
        return f;
    }

    private double store(double v) {
        return spec.f32() ? (double) (float) v : v;
    }

    public String name() {
        return spec.name();
    }

    public int resolution() {
        return spec.resolution();
    }

    public double clamp(double value) {
        if (Double.isNaN(value)) return value;
        return Math.min(spec.max(), Math.max(spec.min(), value));
    }

    /** Grid sample at (gx, gz), clamped to the grid's edge. */
    public double get(int gx, int gz) {
        int x = gx < 0 ? 0 : gx >= width ? width - 1 : gx;
        int z = gz < 0 ? 0 : gz >= height ? height - 1 : gz;
        return data[z * width + x];
    }

    /** The field at a world position, interpolated per the spec. */
    public double sample(double worldX, double worldZ) {
        int r = spec.resolution();
        double fx = worldX / r;
        double fz = worldZ / r;
        if (spec.interpolation() == Interpolation.NEAREST) {
            return get((int) Js.round(fx), (int) Js.round(fz));
        }
        double x0 = Math.floor(fx);
        double z0 = Math.floor(fz);
        double tx = fx - x0;
        double tz = fz - z0;
        int ix = (int) x0, iz = (int) z0;
        double v00 = get(ix, iz);
        double v10 = get(ix + 1, iz);
        double v01 = get(ix, iz + 1);
        double v11 = get(ix + 1, iz + 1);
        double a = v00 + (v10 - v00) * tx;
        double b = v01 + (v11 - v01) * tx;
        return a + (b - a) * tz;
    }
}
