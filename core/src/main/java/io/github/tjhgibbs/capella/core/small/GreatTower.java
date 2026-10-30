package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.detail.Detailer;
import io.github.tjhgibbs.capella.core.detail.Kits;
import io.github.tjhgibbs.capella.core.kit.TowerCap;
import io.github.tjhgibbs.capella.core.params.Params.Stone;
import io.github.tjhgibbs.capella.core.raster.Normalize;
import io.github.tjhgibbs.capella.core.raster.Volume;

import java.util.ArrayList;
import java.util.List;

/**
 * A great free-standing tower, cathedral-scale: the English west tower and the French belfry
 * tower set up as one generator. Walls three thick (two on the smallest), so every face has the
 * three depths a big face needs: buttress, wall, and windows and recesses set back in it.
 *
 * <ul>
 *   <li>Angle buttresses at every corner, stepping back a block at each stage and running up
 *       through the parapet as pinnacles.</li>
 *   <li>Three to six stages: a portal stage with a deep, stepped doorway; lancet stages; a tall
 *       belfry of two or three louvred lights per face with shafted mullions; the bells hung in
 *       a timber frame.</li>
 *   <li>A battlemented parapet with corner and mid-face pinnacles, a needle spire inside it, or a
 *       saddleback.</li>
 * </ul>
 * The {@link Detailer} then dresses it: plinth, courses at every stage line, blind arcading on
 * the blank faces, shafts in the buttress angles, a corbel table under the parapet.
 *
 * <p>Frame: x 0..W-1 west to east, z -h..h, y up; the floor at y = -1 and the west door's
 * threshold at the origin.
 */
public final class GreatTower {
    public record Spec(int width, int height, TowerCap cap, Stone stone, long seed) {
    }

    /** {min, max} side and height. */
    public static final int[][] RANGE = {{13, 31}, {40, 160}};

    private final Spec spec;
    private final Style st;
    private final Mason m;
    private final double seed;
    private final int w, h, t, x0, x1;
    /** Stage lines, bottom to top; the last is the wall top under the parapet. */
    private final List<Integer> lines = new ArrayList<>();

    private GreatTower(Spec spec) {
        this.spec = spec;
        this.st = Style.of(spec.stone());
        this.seed = spec.seed();
        this.m = new Mason(spec.seed());
        int side = Math.max(RANGE[0][0], Math.min(RANGE[0][1], spec.width()));
        this.w = side % 2 == 0 ? side - 1 : side;
        this.h = (w - 1) / 2;
        this.t = w >= 19 ? 3 : 2;
        this.x0 = 0;
        this.x1 = w - 1;
    }

    /** Built, dressed, normalised and turned; the volume's (0, 0, 0) is the door threshold. */
    public static Volume build(Spec spec, int turns) {
        GreatTower g = new GreatTower(spec);
        g.build();
        Volume v = g.m.toVolume(turns);
        List<Detailer.Course> courses = new ArrayList<>();
        for (int i = 0; i < g.lines.size() - 1; i++) courses.add(new Detailer.Course(g.lines.get(i), false));
        v = Detailer.detail(v, turns, Kits.small(spec.stone()), spec.seed(), 0, courses, Detailer.Options.ALL);
        Normalize.normalizeVolume(v);
        return v;
    }

    // ---- materials ----------------------------------------------------------------------------

    private static double grad(int y) {
        return y <= 1 ? 0.2 : y <= 4 ? 0.1 : y <= 8 ? 0.04 : 0;
    }

    private void wall(int x, int y, int z) {
        m.set(x, y, z, st.wall.block(seed, x, y, z, grad(y)));
    }

    private void trim(int x, int y, int z) {
        m.set(x, y, z, st.trim.block(seed, x, y, z, 0));
    }

    private void trimStairs(int x, int y, int z, String facing, boolean top) {
        m.stairs(x, y, z, st.trim.stairs(seed, x, y, z, 0), facing, top);
    }

    private void trimWall(int x, int y, int z) {
        m.set(x, y, z, st.trim.wall(seed, x, y, z, 0));
    }

    // ---- faces --------------------------------------------------------------------------------

    /**
     * One face of the tower: {@code (cx, cz)} the middle of its outer layer, {@code (ax, az)} along
     * it (to the right seen from outside), {@code (nx, nz)} outward.
     */
    private record Face(int cx, int cz, int ax, int az, int nx, int nz) {
        int x(int a, int depth) {
            return cx + a * ax - depth * nx;
        }

        int z(int a, int depth) {
            return cz + a * az - depth * nz;
        }

        String out() {
            return Mason.dir(nx, nz);
        }

        String in() {
            return Mason.dir(-nx, -nz);
        }

        String along(int s) {
            return Mason.dir(ax * s, az * s);
        }
    }

    private List<Face> faces() {
        return List.of(
                new Face(x0, 0, 0, -1, -1, 0),     // west, the door
                new Face(x0 + h, -h, 1, 0, 0, -1), // north
                new Face(x1, 0, 0, 1, 1, 0),       // east
                new Face(x0 + h, h, -1, 0, 0, 1)); // south
    }

    // ---- build --------------------------------------------------------------------------------

    private void build() {
        int ht = Math.max(RANGE[1][0], Math.min(RANGE[1][1], spec.height()));
        stages(ht);
        shell(ht);
        buttresses(ht);
        List<Face> faces = faces();
        int belfryLo = lines.get(lines.size() - 2);
        for (int i = 0; i < faces.size(); i++) {
            Face f = faces.get(i);
            int lo = 0;
            for (int s = 0; s < lines.size(); s++) {
                int hi = lines.get(s);
                if (s == 0) {
                    if (i == 0) portal(f, hi);
                } else if (s == lines.size() - 1) {
                    belfry(f, lo, hi);
                } else if (s % 2 == 1) {
                    lancets(f, lo, hi, w >= 19 ? 2 : 1);
                } else if (hi - lo >= 9) {
                    lancets(f, lo, hi, 1);
                }
                lo = hi;
            }
        }
        bells(belfryLo, ht);
        switch (spec.cap() == null ? TowerCap.FLAT : spec.cap()) {
            case SPIRE -> {
                parapet(ht);
                spire(x0 + h, h - t + 1, ht, (int) Math.round(Math.max(ht * 0.8, w * 3.0)));
            }
            case SADDLEBACK -> saddleback(ht);
            default -> parapet(ht);
        }
    }

    /**
     * Stage lines: the belfry about a quarter of the height, the lower stages each roughly as
     * tall as the tower is wide, the first a little taller than the rest.
     */
    private void stages(int ht) {
        int belfry = Math.max(12, Math.min(34, (int) Math.round(ht * 0.24)));
        int rest = ht - belfry;
        int n = Math.max(2, Math.min(5, (int) Math.round(rest / (w * 1.1))));
        double[] weight = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            weight[i] = i == 0 ? 1.35 : 1.0 - 0.06 * i;
            sum += weight[i];
        }
        double y = 0;
        for (int i = 0; i < n; i++) {
            y += rest * weight[i] / sum;
            lines.add((int) Math.round(y));
        }
        lines.add(ht);
    }

    private void shell(int ht) {
        for (int x = x0; x <= x1; x++) {
            for (int z = -h; z <= h; z++) {
                m.set(x, -1, z, st.plinth.block(seed, x, -1, z, 0));
                boolean shell = x < x0 + t || x > x1 - t || Math.abs(z) > h - t;
                for (int y = 0; y < ht; y++) {
                    if (shell) wall(x, y, z);
                }
                if (!shell) {
                    // Timber floors at the stage lines, the belfry's on a stone vault course.
                    for (int i = 0; i < lines.size() - 1; i++) m.set(x, lines.get(i), z, st.timber);
                    m.set(x, ht - 1, z, st.roof.block(seed, x, ht - 1, z, 0));
                    m.set(x, -1, z, st.floorField.block(seed, x, -1, z, 0));
                }
            }
        }
        // A ladder up the north inner wall, through the floors.
        int lx = x0 + t + 1, lz = -(h - t);
        for (int y = 0; y < ht - 1; y++) m.set(lx, y, lz, "minecraft:ladder", "facing", "south");
    }

    // ---- buttresses ---------------------------------------------------------------------------

    /**
     * Two buttresses at every corner, one on each face, set back a block at each stage line
     * (the Detailer weathers the set-offs) and carried up through the parapet as pinnacles.
     */
    private void buttresses(int ht) {
        int bw = w >= 21 ? 3 : 2;
        int base = w >= 25 ? 5 : w >= 17 ? 4 : 3;
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                int cx = sx < 0 ? x0 : x1, cz = sz * h;
                // Along x (out of the east or west face) and along z (out of the north or south).
                buttress(cx, cz, sx, 0, 0, -sz, bw, base, ht);
                buttress(cx, cz, 0, sz, -sx, 0, bw, base, ht);
            }
        }
    }

    /** From corner (cx, cz) outward (ox, oz), its width running inward along (ix, iz). */
    private void buttress(int cx, int cz, int ox, int oz, int ix, int iz, int bw, int base, int ht) {
        int depth = base;
        int lo = 0;
        for (int s = 0; s < lines.size(); s++) {
            int hi = lines.get(s);
            for (int y = lo; y < hi; y++) {
                for (int k = 1; k <= depth; k++) {
                    for (int j = 0; j < bw; j++) wall(cx + ox * k + ix * j, y, cz + oz * k + iz * j);
                }
            }
            for (int k = 1; k <= depth; k++) {
                for (int j = 0; j < bw; j++) m.set(cx + ox * k + ix * j, -1, cz + oz * k + iz * j, st.plinth.block(seed, cx, -1, cz, 0));
            }
            lo = hi;
            depth = Math.max(1, depth - 1);
        }
        // The pinnacle: the last stage's buttress runs on, gableted, with a needle.
        int top = ht + 4;
        for (int y = ht; y < top; y++) {
            for (int k = 1; k <= depth; k++) for (int j = 0; j < bw; j++) wall(cx + ox * k + ix * j, y, cz + oz * k + iz * j);
        }
        int px = cx + ox, pz = cz + oz;
        for (int j = 0; j < bw; j++) {
            int x = px + ix * j, z = pz + iz * j;
            trimStairs(x + ox, top - 1, z + oz, Mason.dir(-ox, -oz), false);
            trim(x, top, z);
        }
        int mx = px + ix * (bw / 2), mz = pz + iz * (bw / 2);
        for (int y = top + 1; y <= top + 3 + bw; y++) trimWall(mx, y, mz);
    }

    // ---- openings -----------------------------------------------------------------------------

    /**
     * The great door: a pointed opening through the wall in stepped orders, each layer out from
     * the door a block wider and a block taller, so the doorway reads as a deep, moulded portal.
     */
    private void portal(Face f, int stageTop) {
        int half = w >= 21 ? 2 : 1;
        int dh = Math.min(stageTop - 4, half * 2 + 4);
        for (int d = 0; d < t; d++) {
            int extra = t - 1 - d; // outer layers wider
            int hw = half + extra, top = dh + extra;
            if (d == t - 1) {
                // The inner layer is the door and a tympanum over it.
                for (int a = -half; a <= half; a++) for (int y = 0; y <= 1; y++) m.clear(f.x(a, d), y, f.z(a, d));
                continue;
            }
            for (int a = -hw; a <= hw; a++) {
                int x = f.x(a, d), z = f.z(a, d);
                int clearTop = Math.abs(a) == hw ? top - 2 : top - 1;
                for (int y = 0; y <= clearTop; y++) m.clear(x, y, z);
                if (Math.abs(a) == hw) trimStairs(x, top - 1, z, f.along(a < 0 ? -1 : 1), true);
                if (hw >= 2 && Math.abs(a) == 1) trimStairs(x, top, z, f.along(a < 0 ? -1 : 1), true);
                if (hw >= 2 && a == 0) m.clear(x, top, z);
            }
            // A shaft in each reveal.
            for (int s : new int[]{-1, 1}) {
                for (int y = 0; y < top - 2; y++) trimWall(f.x(s * hw, d), y, f.z(s * hw, d));
            }
        }
        // The doors themselves, at the inner face.
        for (int a = -half; a <= half; a++) {
            int x = f.x(a, t - 1), z = f.z(a, t - 1);
            m.set(x, 0, z, st.door, "facing", f.out(), "half", "lower", "hinge", a < 0 ? "left" : "right");
            m.set(x, 1, z, st.door, "facing", f.out(), "half", "upper", "hinge", a < 0 ? "left" : "right");
        }
    }

    /**
     * {@code lights} lancets centred on the face, glazed at the inner layer so the outer layers are
     * a reveal; two lights share a wall-shaft mullion and a hood.
     */
    private void lancets(Face f, int lo, int hi, int lights) {
        int lw = w >= 23 ? 2 : 1;
        int ys = lo + 3, yh = hi - 3;
        if (yh - ys < 4) return;
        int total = lights * lw + (lights - 1);
        int a0 = -total / 2;
        for (int l = 0; l < lights; l++) {
            int from = a0 + l * (lw + 1);
            for (int a = from; a < from + lw; a++) {
                for (int d = 0; d < t; d++) {
                    int x = f.x(a, d), z = f.z(a, d);
                    for (int y = ys; y <= yh; y++) {
                        if (d == t - 1) m.set(x, y, z, st.glassPlain.block(seed, x, y, z, 0));
                        else m.clear(x, y, z);
                    }
                    // A pointed head: the top course stepped in from each side.
                    if (lw == 2 && d < t - 1) trimStairs(x, yh, z, f.along(a == from ? -1 : 1), true);
                }
            }
            if (l + 1 < lights) {
                int a = from + lw;
                for (int d = 0; d < t - 1; d++) {
                    int x = f.x(a, d), z = f.z(a, d);
                    for (int y = ys; y <= yh; y++) trimWall(x, y, z);
                }
            }
        }
    }

    /**
     * The belfry: two or three tall lights per face, louvred, divided by shafted mullions, the
     * whole group set in a recessed frame one block deep under a pointed head.
     */
    private void belfry(Face f, int lo, int hi) {
        int lights = w >= 21 ? 3 : 2;
        int lw = w >= 17 ? 2 : 1;
        int total = lights * lw + (lights - 1);
        int a0 = -total / 2;
        int ys = lo + 3, yh = hi - 4;
        if (yh - ys < 5) return;
        // The recessed frame round the group.
        for (int a = a0 - 1; a <= a0 + total; a++) {
            for (int y = ys - 1; y <= yh + 1; y++) m.clear(f.x(a, 0), y, f.z(a, 0));
        }
        trimStairs(f.x(a0 - 1, 0), yh + 1, f.z(a0 - 1, 0), f.along(-1), true);
        trimStairs(f.x(a0 + total, 0), yh + 1, f.z(a0 + total, 0), f.along(1), true);
        for (int a = a0; a < a0 + total; a++) trimStairs(f.x(a, 0), ys - 1, f.z(a, 0), f.in(), false);
        for (int l = 0; l < lights; l++) {
            int from = a0 + l * (lw + 1);
            for (int a = from; a < from + lw; a++) {
                for (int d = 1; d < t; d++) {
                    int x = f.x(a, d), z = f.z(a, d);
                    for (int y = ys; y <= yh; y++) {
                        if (d == t - 1) m.set(x, y, z, st.timberTrapdoor, "facing", f.in(), "half", "top", "open", "false");
                        else m.clear(x, y, z);
                    }
                }
                if (lw == 2) {
                    for (int d = 1; d < t; d++) trimStairs(f.x(a, d), yh, f.z(a, d), f.along(a == from ? -1 : 1), true);
                }
            }
            if (l + 1 < lights) {
                int a = from + lw;
                for (int y = ys; y <= yh; y++) trimWall(f.x(a, 1), y, f.z(a, 1));
            }
        }
    }

    // ---- bells --------------------------------------------------------------------------------

    private void bells(int lo, int ht) {
        int count = w >= 21 ? 3 : w >= 15 ? 2 : 1;
        int beamY = lo + Math.max(5, (ht - lo) / 2);
        int inner0 = x0 + t, inner1 = x1 - t;
        for (int x = inner0; x <= inner1; x++) m.set(x, beamY, 0, st.timberLog, "axis", "x");
        for (int i = 0; i < count; i++) {
            int bx = inner0 + (inner1 - inner0) * (i + 1) / (count + 1);
            m.set(bx, beamY - 1, 0, "minecraft:bell", "attachment", "ceiling", "facing", "north");
        }
        // The frame's posts.
        for (int x : new int[]{inner0, inner1}) for (int y = lo + 1; y < beamY; y++) m.set(x, y, 0, st.timberLog, "axis", "y");
    }

    // ---- tops ---------------------------------------------------------------------------------

    /** Battlements: merlons two wide, crenels one, on a course of trim, with a pinnacle mid-face on the bigger towers. */
    private void parapet(int ht) {
        for (int x = x0; x <= x1; x++) {
            for (int z = -h; z <= h; z++) {
                boolean edge = x == x0 || x == x1 || Math.abs(z) == h;
                if (!edge) continue;
                trim(x, ht, z);
                int a = x == x0 || x == x1 ? z + h : x - x0;
                if (Math.floorMod(a, 3) != 2) {
                    wall(x, ht + 1, z);
                    m.slab(x, ht + 2, z, st.trim.slab(seed, x, ht + 2, z, 0), false);
                }
            }
        }
        if (w >= 19) {
            for (Face f : faces()) {
                int x = f.x(0, 0), z = f.z(0, 0);
                for (int y = ht + 1; y <= ht + 3; y++) wall(x, y, z);
                trim(x, ht + 4, z);
                for (int y = ht + 5; y <= ht + 7; y++) trimWall(x, y, z);
            }
        }
    }

    private void saddleback(int ht) {
        for (int k = 0; k <= h; k++) {
            int y = ht + k, zs = h - k;
            for (int x = x0; x <= x1; x++) {
                boolean gable = x == x0 || x == x1;
                if (zs == 0) {
                    if (gable) trim(x, y, 0);
                    else m.slab(x, y, 0, st.ridge.slab(seed, x, y, 0, 0), false);
                    continue;
                }
                for (int z : new int[]{-zs, zs}) {
                    if (gable) trimStairs(x, y, z, z > 0 ? "north" : "south", false);
                    else m.stairs(x, y, z, st.roof.stairs(seed, x, y, z, 0), z > 0 ? "north" : "south", false);
                }
                if (gable) for (int z = -zs + 1; z <= zs - 1; z++) wall(x, y, z);
            }
        }
        for (int x : new int[]{x0, x1}) {
            trimWall(x, ht + h + 1, 0);
            trimWall(x, ht + h + 2, 0);
        }
    }

    /**
     * An octagonal needle spire inside the parapet, a band of trim every eighth course and
     * gableted lucarnes on the four cardinal faces near its foot.
     */
    private void spire(int cx, int r0, int base, int height) {
        for (int k = 0; k < height; k++) {
            int y = base + k;
            for (int dx = -r0; dx <= r0; dx++) {
                for (int dz = -r0; dz <= r0; dz++) {
                    if (!inSpire(dx, dz, k, r0, height)) continue;
                    boolean shell = !inSpire(dx + 1, dz, k, r0, height) || !inSpire(dx - 1, dz, k, r0, height)
                            || !inSpire(dx, dz + 1, k, r0, height) || !inSpire(dx, dz - 1, k, r0, height);
                    if (!shell) continue;
                    int x = cx + dx, z = dz;
                    boolean band = k > 4 && k % 8 == 0;
                    if (!inSpire(dx, dz, k + 1, r0, height)) {
                        String facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                        if (dx == 0 && dz == 0) m.set(x, y, z, st.roof.block(seed, x, y, z, 0));
                        else if (band) trimStairs(x, y, z, facing, false);
                        else m.stairs(x, y, z, st.roof.stairs(seed, x, y, z, 0), facing, false);
                    } else if (band) {
                        trim(x, y, z);
                    } else {
                        m.set(x, y, z, st.roof.block(seed, x, y, z, 0));
                    }
                }
            }
        }
        // Lucarnes: little gabled openings on the cardinal faces.
        if (r0 >= 4) {
            int ly = base + 3;
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                int rr = (int) Math.floor((r0 + 0.49) * (1 - 3.0 / height));
                int ex = d[1], ez = d[0]; // along the spire face
                for (int s = -1; s <= 1; s++) {
                    int x = cx + d[0] * (rr + 1) + ex * s, z = d[1] * (rr + 1) + ez * s;
                    for (int y = ly; y <= ly + 2; y++) {
                        if (s == 0 && y < ly + 2) m.clear(x, y, z);
                        else wall(x, y, z);
                    }
                    m.clear(x - d[0], ly, z - d[1]);
                    m.clear(x - d[0], ly + 1, z - d[1]);
                    if (s == 0) trim(x, ly + 3, z);
                    else trimStairs(x, ly + 3, z, Mason.dir(-ex * s, -ez * s), false);
                }
                int x = cx + d[0] * (rr + 1), z = d[1] * (rr + 1);
                trimWall(x, ly + 4, z);
            }
        }
        int top = base + height;
        trimWall(cx, top, 0);
        trimWall(cx, top + 1, 0);
        m.set(cx, top + 2, 0, "minecraft:lightning_rod");
    }

    private static boolean inSpire(int dx, int dz, int k, int r0, int height) {
        if (k >= height) return false;
        double r = (r0 + 0.49) * (1 - k / (double) height);
        int a = Math.abs(dx), b = Math.abs(dz);
        if (a > r || b > r) return false;
        return a + b <= r * 1.42;
    }
}
