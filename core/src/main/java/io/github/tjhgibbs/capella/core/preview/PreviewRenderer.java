package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.math.Rng;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import java.util.Arrays;

/**
 * Small software rasteriser: draws the exposed faces of a {@link BlockVolume} with a z-buffer,
 * directional light, per-face ambient occlusion and depth cueing. No AWT and no OpenGL, so the
 * same code renders the in-game preview (uploaded as a texture) and PNGs in tests/tools.
 */
public final class PreviewRenderer {

    /**
     * @param yaw   rotation around the vertical axis, radians
     * @param pitch camera elevation, radians (positive looks down on the tree)
     * @param zoom  1 fits the whole tree
     * @param panX  horizontal pan in pixels; panY likewise
     */
    public record View(int width, int height, double yaw, double pitch, double zoom, double panX, double panY, boolean ground) {
        public static View fit(int width, int height, double yaw) {
            return new View(width, height, yaw, Math.toRadians(22), 1.0, 0, 0, true);
        }
    }

    public interface Colors {
        /** Opaque RGB for a block state. */
        int rgb(String state);
    }

    private static final int[][] NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final int w, h;
    private final int[] pixels;
    private final float[] depth;
    private final double cosY, sinY, cosP, sinP;
    private double scale, offX, offY, cx, cy, cz;
    private double minDepth, maxDepth;

    private PreviewRenderer(View view) {
        this.w = view.width();
        this.h = view.height();
        this.pixels = new int[w * h];
        this.depth = new float[w * h];
        Arrays.fill(depth, Float.POSITIVE_INFINITY);
        cosY = Math.cos(view.yaw());
        sinY = Math.sin(view.yaw());
        cosP = Math.cos(view.pitch());
        sinP = Math.sin(view.pitch());
    }

    /** Renders to ARGB pixels (row-major, transparent background). */
    public static int[] render(BlockVolume v, View view, Colors colors) {
        PreviewRenderer r = new PreviewRenderer(view);
        r.draw(v, view, colors);
        return r.pixels;
    }

    /** Screen-space bounds (at unit scale) of every block's centre, padded by half a block diagonal; null if empty. */
    private double[] blockBounds(BlockVolume v) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int y = 0; y < v.height; y++) {
            for (int z = 0; z < v.length; z++) {
                for (int x = 0; x < v.width; x++) {
                    if (v.idAt(x, y, z) != 0) {
                        double[] q = project(x + 0.5, y + 0.5, z + 0.5);
                        minX = Math.min(minX, q[0]);
                        maxX = Math.max(maxX, q[0]);
                        minY = Math.min(minY, q[1]);
                        maxY = Math.max(maxY, q[1]);
                    }
                }
            }
        }
        if (minX > maxX) {
            return null;
        }
        double pad = 0.87;
        return new double[]{minX - pad, maxX + pad, minY - pad, maxY + pad};
    }

    // Camera space: x right, y up, z away from the viewer.
    private double[] project(double x, double y, double z) {
        double rx = (x - cx) * cosY - (z - cz) * sinY;
        double rz0 = (x - cx) * sinY + (z - cz) * cosY;
        double ry = (y - cy) * cosP + rz0 * sinP;
        double rz = -(y - cy) * sinP + rz0 * cosP;
        return new double[]{offX + rx * scale, offY - ry * scale, rz};
    }

    private void draw(BlockVolume v, View view, Colors colors) {
        cx = v.width / 2.0;
        cy = v.height / 2.0;
        cz = v.length / 2.0;
        // Fit: project the box corners at unit scale and size the image to them.
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        minDepth = Double.MAX_VALUE;
        maxDepth = -Double.MAX_VALUE;
        scale = 1;
        offX = 0;
        offY = 0;
        for (int i = 0; i < 8; i++) {
            double[] q = project((i & 1) * v.width, ((i >> 1) & 1) * v.height, ((i >> 2) & 1) * v.length);
            minX = Math.min(minX, q[0]);
            maxX = Math.max(maxX, q[0]);
            minY = Math.min(minY, q[1]);
            maxY = Math.max(maxY, q[1]);
            minDepth = Math.min(minDepth, q[2]);
            maxDepth = Math.max(maxDepth, q[2]);
        }
        if (!view.ground()) {
            // Floating tree: frame the blocks themselves, not the (much larger) rotated box.
            double[] b = blockBounds(v);
            if (b != null) {
                minX = b[0];
                maxX = b[1];
                minY = b[2];
                maxY = b[3];
            }
        }
        scale = Math.min(w * 0.94 / Math.max(1, maxX - minX), h * 0.94 / Math.max(1, maxY - minY)) * view.zoom();
        offX = w / 2.0 - (minX + maxX) / 2 * scale + view.panX();
        offY = h / 2.0 - (minY + maxY) / 2 * scale + view.panY();

        if (view.ground()) {
            drawGround(v);
        }
        int[] palette = new int[v.palette().size()];
        boolean[] foliage = new boolean[palette.length];
        double[][][] shapes = new double[palette.length][][];
        boolean[] full = new boolean[palette.length];
        for (int i = 1; i < palette.length; i++) {
            String state = v.palette().get(i);
            palette[i] = colors.rgb(state);
            foliage[i] = BlockColors.isFoliage(state);
            shapes[i] = BlockShapes.boxes(state);
            full[i] = shapes[i] == null;
        }
        double[] light = normalize(-0.45, 0.8, -0.35);
        for (int y = 0; y < v.height; y++) {
            for (int z = 0; z < v.length; z++) {
                for (int x = 0; x < v.width; x++) {
                    int id = v.idAt(x, y, z);
                    if (id == 0) {
                        continue;
                    }
                    double jitter = foliage[id] ? 0.9 + 0.2 * hash01(x, y, z) : 0.96 + 0.08 * hash01(x, y, z);
                    for (int f = 0; f < 6; f++) {
                        int[] n = NORMALS[f];
                        int nx = x + n[0], ny = y + n[1], nz = z + n[2];
                        // Back-face cull in camera space.
                        double ncz = (n[0] * sinY + n[2] * cosY) * cosP - n[1] * sinP;
                        if (ncz > 0) {
                            continue;
                        }
                        double lambert = Math.max(0, n[0] * light[0] + n[1] * light[1] + n[2] * light[2]);
                        if (full[id]) {
                            if (inside(v, nx, ny, nz) && full[v.idAt(nx, ny, nz)]) {
                                continue;
                            }
                            double ao = 1 - 0.075 * occluders(v, nx, ny, nz, f);
                            face(x, y, z, x + 1, y + 1, z + 1, f, palette[id], (0.5 + 0.55 * lambert) * ao * jitter);
                        } else {
                            for (double[] b : shapes[id]) {
                                face(x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5], f, palette[id], (0.5 + 0.55 * lambert) * 0.95 * jitter);
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean inside(BlockVolume v, int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < v.width && y < v.height && z < v.length;
    }

    /** Solid voxels around the cell in front of a face: cheap ambient occlusion. */
    private static int occluders(BlockVolume v, int x, int y, int z, int f) {
        int count = 0;
        int axis = f / 2;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                int qx = x, qy = y, qz = z;
                switch (axis) {
                    case 0 -> { qy += a; qz += b; }
                    case 1 -> { qx += a; qz += b; }
                    default -> { qx += a; qy += b; }
                }
                if (inside(v, qx, qy, qz) && v.idAt(qx, qy, qz) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static double hash01(int x, int y, int z) {
        long hsh = Rng.mix(x * 73856093L ^ y * 19349663L ^ z * 83492791L);
        return (hsh >>> 11) * 0x1.0p-53;
    }

    private static double[] normalize(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    private void face(double x0, double y0, double z0, double x1, double y1, double z1, int f, int rgb, double shade) {
        double[][] c = new double[4][];
        switch (f) {
            case 0 -> { c[0] = project(x1, y0, z0); c[1] = project(x1, y1, z0); c[2] = project(x1, y1, z1); c[3] = project(x1, y0, z1); }
            case 1 -> { c[0] = project(x0, y0, z0); c[1] = project(x0, y1, z0); c[2] = project(x0, y1, z1); c[3] = project(x0, y0, z1); }
            case 2 -> { c[0] = project(x0, y1, z0); c[1] = project(x1, y1, z0); c[2] = project(x1, y1, z1); c[3] = project(x0, y1, z1); }
            case 3 -> { c[0] = project(x0, y0, z0); c[1] = project(x1, y0, z0); c[2] = project(x1, y0, z1); c[3] = project(x0, y0, z1); }
            case 4 -> { c[0] = project(x0, y0, z1); c[1] = project(x1, y0, z1); c[2] = project(x1, y1, z1); c[3] = project(x0, y1, z1); }
            default -> { c[0] = project(x0, y0, z0); c[1] = project(x1, y0, z0); c[2] = project(x1, y1, z0); c[3] = project(x0, y1, z0); }
        }
        double mid = (c[0][2] + c[2][2]) / 2;
        double fog = 1 - 0.22 * (mid - minDepth) / Math.max(1e-6, maxDepth - minDepth);
        int color = shadeColor(rgb, shade * fog);
        tri(c[0], c[1], c[2], color);
        tri(c[0], c[2], c[3], color);
    }

    private void drawGround(BlockVolume v) {
        int pad = 3;
        int gy = v.originY;
        for (int z = -pad; z < v.length + pad; z++) {
            for (int x = -pad; x < v.width + pad; x++) {
                // Fade the ground out towards its edge as a disc.
                double dx = (x + 0.5 - v.originX), dz = (z + 0.5 - v.originZ);
                double rad = Math.max(v.width, v.length) / 2.0 + pad;
                if (dx * dx + dz * dz > rad * rad) {
                    continue;
                }
                int base = ((x + z) & 1) == 0 ? 0x6A9A3A : 0x64933A;
                int color = shadeColor(base, 0.92);
                double[] a = project(x, gy, z), b = project(x + 1, gy, z), c = project(x + 1, gy, z + 1), d = project(x, gy, z + 1);
                // Nudge the ground slightly back so blocks resting on it win ties.
                a[2] += 0.01; b[2] += 0.01; c[2] += 0.01; d[2] += 0.01;
                tri(a, b, c, color);
                tri(a, c, d, color);
            }
        }
    }

    private static int shadeColor(int rgb, double s) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * s);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * s);
        int b = (int) Math.min(255, (rgb & 255) * s);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void tri(double[] a, double[] b, double[] c, int color) {
        int x0 = (int) Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
        int x1 = (int) Math.min(w - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
        int y0 = (int) Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
        int y1 = (int) Math.min(h - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
        double area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        if (Math.abs(area) < 1e-9) {
            return;
        }
        for (int py = y0; py <= y1; py++) {
            double sy = py + 0.5;
            for (int px = x0; px <= x1; px++) {
                double sx = px + 0.5;
                double w0 = ((b[0] - sx) * (c[1] - sy) - (b[1] - sy) * (c[0] - sx)) / area;
                double w1 = ((c[0] - sx) * (a[1] - sy) - (c[1] - sy) * (a[0] - sx)) / area;
                double w2 = 1 - w0 - w1;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) {
                    continue;
                }
                float d = (float) (w0 * a[2] + w1 * b[2] + w2 * c[2]);
                int i = py * w + px;
                if (d < depth[i]) {
                    depth[i] = d;
                    pixels[i] = color;
                }
            }
        }
    }
}
