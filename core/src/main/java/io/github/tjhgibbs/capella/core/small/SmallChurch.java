package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import io.github.tjhgibbs.capella.core.raster.RasterPass;
import io.github.tjhgibbs.capella.core.detail.Detailer;
import io.github.tjhgibbs.capella.core.detail.Kits;
import io.github.tjhgibbs.capella.core.kit.TowerCap;
import io.github.tjhgibbs.capella.core.params.Params.Stone;
import io.github.tjhgibbs.capella.core.raster.Normalize;
import io.github.tjhgibbs.capella.core.raster.Volume;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Chapels, parish churches and free-standing towers, built block by block. Not part of the
 * Capella port: the cathedral solver works in 3-block walls and 8-block bays, which is too coarse
 * for a building 11 blocks wide. Here every wall is two thick so it has three depth planes
 * (buttress +1, face 0, glass -1), and detail comes from half-block parts (stairs, slabs, walls,
 * panes, trapdoors) rather than from size.
 *
 * <p>Frame: +x east along the nave, +z south, y up; floor blocks at y = -1 and the west door's
 * threshold at the origin.
 */
public final class SmallChurch {
    public enum Kind { CHAPEL, PARISH, TOWER }

    public enum Belfry { NONE, BELLCOTE, TOWER }

    /**
     * @param length overall, west face to east face (ignored for a tower)
     * @param width  the nave's outer width (a tower's side)
     * @param height eaves above the floor (a tower's height to its parapet)
     */
    public record Spec(Kind kind, int length, int width, int height, Belfry belfry, TowerCap cap, boolean tracery,
                       boolean porch, Stone stone, long seed) {
    }

    /** The legal size range for each kind: {min, max} of length, width, height. */
    public static int[][] ranges(Kind kind) {
        return switch (kind) {
            case CHAPEL -> new int[][]{{13, 45}, {9, 19}, {5, 14}};
            case PARISH -> new int[][]{{28, 80}, {11, 25}, {6, 16}};
            case TOWER -> new int[][]{{5, 13}, {5, 13}, {12, 48}};
        };
    }

    /** Built, normalised and turned; the volume's (0, 0, 0) is the door threshold. */
    public static Volume build(Spec spec, int turns) {
        SmallChurch b = new SmallChurch(spec);
        b.build();
        Volume v = b.m.toVolume(turns);
        // The church dresses its own plinth, courses and cornices; the detailer adds the rest.
        v = Detailer.detail(v, turns, Kits.small(spec.stone()), spec.seed(), 0, List.of(),
                new Detailer.Options(false, false, false, true, true, true, false, true));
        Normalize.normalizeVolume(v);
        // Explicit excavation cells survive sparse exports, which normally skip air.
        // Apply after masonry detail so negative-space cells cannot be mistaken for stone.
        for (int[] p : b.excavation) {
            int x = p[0], z = p[2];
            for (int i = 0; i < Math.floorMod(turns, 4); i++) { int nx = -z; z = x; x = nx; }
            if (v.isAirAt(x, p[1], z)) v.set(x, p[1], z, Blockstates.makeState("minecraft:cave_air"),
                    RasterPass.DECORATION);
        }
        return v;
    }

    // ---- frames -------------------------------------------------------------------------------

    /** A local frame: u along a ridge, v across it; world = origin + u*U + v*V. */
    record F(int ox, int oz, int ux, int uz, int vx, int vz) {
        static final F CANON = new F(0, 0, 1, 0, 0, 1);

        int x(int u, int v) {
            return ox + u * ux + v * vx;
        }

        int z(int u, int v) {
            return oz + u * uz + v * vz;
        }

        String du(int s) {
            return Mason.dir(ux * s, uz * s);
        }

        String dv(int s) {
            return Mason.dir(vx * s, vz * s);
        }

        String axisU() {
            return ux != 0 ? "x" : "z";
        }

        String axisV() {
            return vx != 0 ? "x" : "z";
        }
    }

    /** A gabled, two-walled rectangle: nave, chancel, porch. */
    static final class Hall {
        final F f;
        final int u0, u1, hv, h;
        final double pitch;
        /** Coped gable walls at the low/high u end; false means another mass closes that end. */
        boolean gableLo = true, gableHi = true;
        /** Whether to build the end walls at all. */
        boolean wallLo = true, wallHi = true;
        final Set<Integer> trusses = new HashSet<>();
        List<Integer> bays = List.of();

        Hall(F f, int u0, int u1, int hv, int h, double pitch) {
            this.f = f;
            this.u0 = u0;
            this.u1 = u1;
            this.hv = hv;
            this.h = h;
            this.pitch = pitch;
        }

        /** Top of the roof skin in column i, counted in from the eaves overhang (i = 0). */
        int top(int i) {
            return h + (int) Math.floor(i * pitch + 1e-9);
        }

        /** Where column i's skin starts. */
        int low(int i) {
            return i == 0 ? h : top(i - 1);
        }

        int ridge() {
            return top(hv + 1);
        }

        int col(int v) {
            return hv + 1 - Math.abs(v);
        }
    }

    // ---- state --------------------------------------------------------------------------------

    private final Spec spec;
    private final Style st;
    private final Mason m;
    private final double seed;
    private final int interiorVariant, sanctuaryRise, porchSide, porchDepth, porchWidth;
    private final long layoutSeed;
    private final List<int[]> excavation = new ArrayList<>();

    private SmallChurch(Spec spec) {
        this.spec = spec;
        this.st = Style.of(spec.stone());
        this.seed = spec.seed();
        this.m = new Mason(spec.seed());
        Random layout = new Random(spec.seed() ^ 0x434150454c4c41L);
        interiorVariant = layout.nextInt(6);
        sanctuaryRise = 1 + layout.nextInt(3);
        porchSide = layout.nextBoolean() ? 1 : -1;
        porchDepth = 4 + layout.nextInt(3);
        porchWidth = 2 + layout.nextInt(2);
        layoutSeed = layout.nextLong();
    }

    private static int odd(int n) {
        return n % 2 == 0 ? n - 1 : n;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---- materials ----------------------------------------------------------------------------

    /** Darker, mossier courses at the foot of a wall. */
    private static double grad(int y) {
        return y <= 0 ? 0.22 : y <= 2 ? 0.12 : y <= 4 ? 0.05 : 0;
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

    private void glass(int x, int y, int z, Style.Mix mix) {
        m.set(x, y, z, mix.block(seed, x, y, z, 0));
    }

    // ---- build --------------------------------------------------------------------------------

    private void build() {
        int[][] r = ranges(spec.kind());
        if (spec.kind() == Kind.TOWER) {
            int t = odd(clamp(spec.width(), r[1][0], r[1][1]));
            int ht = clamp(spec.height(), r[2][0], r[2][1]);
            tower(0, t, ht, true, 0);
            plinth();
            return;
        }
        int length = clamp(spec.length(), r[0][0], r[0][1]);
        int width = odd(clamp(spec.width(), r[1][0], r[1][1]));
        int height = clamp(spec.height(), r[2][0], r[2][1]);
        int hv = (width - 1) / 2;
        boolean parish = spec.kind() == Kind.PARISH;
        double pitch = width <= 11 ? 2 : 1.5;

        boolean towered = spec.belfry() == Belfry.TOWER && width >= 11;
        int towerSide = towered ? clamp(odd((int) Math.round(width * 0.6)), 7, Math.min(11, width - 4)) : 0;
        int naveU0 = towered ? towerSide - 2 : 0;

        int chancelLen = parish ? clamp((int) Math.round(length * 0.3), 8, 22) : 0;
        int naveU1 = parish ? length - chancelLen + 1 : length - 1;
        if (naveU1 - naveU0 < 12) naveU1 = naveU0 + 12;

        Hall nave = new Hall(F.CANON, naveU0, naveU1, hv, height, pitch);
        nave.bays = bays(naveU0, naveU1);
        for (int i = 1; i < nave.bays.size() - 1; i++) nave.trusses.add(nave.bays.get(i));

        Hall chancel = null;
        if (parish) {
            int hvc = hv - 2;
            chancel = new Hall(F.CANON, naveU1 - 1, Math.max(naveU1 + 6, length - 1), hvc, Math.max(5, height - 2), pitch);
            chancel.gableLo = false;
            chancel.wallLo = false;
            chancel.bays = bays(chancel.u0 + 1, chancel.u1);
            for (int i = 1; i < chancel.bays.size() - 1; i++) chancel.trusses.add(chancel.bays.get(i));
            hall(chancel);
            dressSides(chancel, Set.of());
            eastEnd(chancel);
            chancelInterior(chancel);
        }

        hall(nave);
        int porchBay = -1;
        // Without a chancel the sanctuary dais fills the last bay; a side door there opens onto it.
        if (spec.porch() && nave.bays.size() >= 3) {
            porchBay = new Random(layoutSeed).nextInt(nave.bays.size() - (chancel == null ? 2 : 1));
        }
        dressSides(nave, porchBay >= 0 ? Set.of(porchSide * (porchBay + 1)) : Set.of());
        if (chancel != null) {
            chancelArch(nave, chancel);
            cross(nave.u1, nave.ridge() + 1);
        } else {
            eastEnd(nave);
        }
        if (!towered) westFront(nave);
        naveInterior(nave, chancel == null);

        if (porchBay >= 0) porch(nave, porchBay);
        if (towered) {
            int ht = Math.max((int) Math.round(height * 2.4), nave.ridge() + 8);
            tower(0, towerSide, ht, false, nave.ridge());
        } else if (spec.belfry() == Belfry.BELLCOTE) {
            bellcote(nave);
        }
        plinth();
    }

    /** Bay lines from u0 to u1, about six apart. */
    private static List<Integer> bays(int u0, int u1) {
        int n = Math.max(1, (int) Math.round((u1 - u0) / 6.0));
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) out.add(u0 + (int) Math.round(i * (u1 - u0) / (double) n));
        return out;
    }

    // ---- halls --------------------------------------------------------------------------------

    private void hall(Hall h) {
        F f = h.f;
        for (int u = h.u0; u <= h.u1; u++) {
            for (int v = -h.hv; v <= h.hv; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                m.set(x, -1, z, st.plinth.block(seed, x, -1, z, 0));
                boolean lo = h.wallLo && (u == h.u0 || u == h.u0 + 1);
                boolean hi = h.wallHi && (u == h.u1 || u == h.u1 - 1);
                boolean side = Math.abs(v) >= h.hv - 1;
                if (!(lo || hi || side)) continue;
                for (int y = 0; y < h.h; y++) wall(x, y, z);
            }
        }
        if (h.gableLo) gable(h, h.u0, 1);
        if (h.gableHi) gable(h, h.u1, -1);
        roof(h);
        cornice(h);
    }

    /** The gable wall above the eaves, its outer layer standing one course proud of the roof with stone coping. */
    private void gable(Hall h, int ue, int inward) {
        F f = h.f;
        for (int v = -h.hv; v <= h.hv; v++) {
            int i = h.col(v);
            int x = f.x(ue, v), z = f.z(ue, v);
            for (int y = h.h; y <= h.top(i); y++) wall(x, y, z);
            if (v == 0) {
                trim(x, h.ridge() + 1, z);
            } else {
                trimStairs(x, h.top(i) + 1, z, f.dv(v > 0 ? -1 : 1), false);
            }
            int xi = f.x(ue + inward, v), zi = f.z(ue + inward, v);
            for (int y = h.h; y < h.low(i); y++) wall(xi, y, zi);
        }
        // Kneelers: the coping's foot, a block out over the corner.
        for (int s : new int[]{-1, 1}) trim(f.x(ue, s * h.hv), h.h, f.z(ue, s * h.hv));
    }

    private void roof(Hall h) {
        F f = h.f;
        int from = h.gableLo ? h.u0 + 1 : h.u0, to = h.gableHi ? h.u1 - 1 : h.u1;
        for (int u = from; u <= to; u++) {
            boolean truss = h.trusses.contains(u);
            for (int v = -(h.hv + 1); v <= h.hv + 1; v++) {
                int i = h.col(v);
                int x = f.x(u, v), z = f.z(u, v);
                double eaves = Math.max(0, 0.18 - i * 0.05);
                int skinTop = v == 0 ? h.ridge() - 1 : h.top(i) - 1;
                for (int y = h.low(i); y <= skinTop; y++) {
                    boolean underside = y == h.low(i) && Math.abs(v) <= h.hv - 1 && i > 0;
                    if (underside) m.set(x, y, z, truss ? st.timber : st.lining);
                    else m.set(x, y, z, st.roof.block(seed, x, y, z, eaves));
                }
                if (v == 0) {
                    m.slab(x, h.ridge(), z, st.ridge.slab(seed, x, h.ridge(), z, 0), false);
                } else {
                    m.stairs(x, h.top(i), z, st.roof.stairs(seed, x, h.top(i), z, eaves), f.dv(v > 0 ? -1 : 1), false);
                }
            }
        }
    }

    /** A course of upside-down stairs under the eaves, and a corbel stop every other block below it. */
    private void cornice(Hall h) {
        F f = h.f;
        for (int s : new int[]{-1, 1}) {
            int v = s * (h.hv + 1);
            for (int u = h.u0; u <= h.u1; u++) {
                int x = f.x(u, v), z = f.z(u, v);
                trimStairs(x, h.h - 1, z, f.dv(-s), true);
                if (Math.floorMod(u - h.u0, 2) == 1 && h.h >= 7) {
                    m.setIfAir(x, h.h - 2, z, st.button, "face", "wall", "facing", f.dv(s));
                }
            }
        }
    }

    /** Buttresses, windows and the sill course along both long walls. {@code skip}: odd indices 2k+1 for bay k on the south. */
    private void dressSides(Hall h, Set<Integer> skip) {
        F f = h.f;
        List<Integer> b = h.bays;
        int ys = h.h >= 12 ? 3 : 2;
        int yh = Math.max(ys + 2, h.h - 4);
        for (int s : new int[]{-1, 1}) {
            int v = s * h.hv;
            for (int i = 1; i < b.size() - 1; i++) {
                buttress(f.x(b.get(i), v), f.z(b.get(i), v), f.vx() * s, f.vz() * s, h.h);
            }
            if (h.gableHi) {
                buttress(f.x(h.u1, v), f.z(h.u1, v), f.vx() * s, f.vz() * s, h.h);
                buttress(f.x(h.u1, v), f.z(h.u1, v), f.ux(), f.uz(), h.h);
            }
            if (h.gableLo && h.wallLo) {
                buttress(f.x(h.u0, v), f.z(h.u0, v), f.vx() * s, f.vz() * s, h.h);
                buttress(f.x(h.u0, v), f.z(h.u0, v), -f.ux(), -f.uz(), h.h);
            }
            for (int i = 0; i + 1 < b.size(); i++) {
                if (skip.contains(s * (i + 1))) continue;
                int lo = i == 0 ? h.u0 + 2 : b.get(i) + 1;
                int hi = i == b.size() - 2 ? h.u1 - 2 : b.get(i + 1) - 1;
                int span = hi - lo + 1;
                if (span < 1) continue;
                int w = span % 2 == 1 ? (span >= 5 && spec.tracery() ? 3 : 1) : 2;
                if (w > span) w = span;
                int c0 = lo + (span - w) / 2;
                Face face = new Face(f.x(0, v), f.z(0, v), f.ux(), f.uz(), f.vx() * s, f.vz() * s);
                window(face, c0, w, ys, yh, st.glassPlain, spec.tracery());
            }
            // Sill course: a weathered string course the windows sit on, stopping at the buttresses.
            for (int u = h.u0; u <= h.u1; u++) {
                int x = f.x(u, v + s), z = f.z(u, v + s);
                if (m.air(x, ys - 1, z)) trimStairs(x, ys - 1, z, f.dv(-s), false);
            }
        }
    }

    /** One buttress against the wall cell (x, z), projecting two blocks along (dx, dz), set back once. */
    private void buttress(int x, int z, int dx, int dz, int wallH) {
        int h1 = Math.max(2, (int) Math.round(wallH * 0.45));
        int h2 = wallH - 2;
        String in = Mason.dir(-dx, -dz);
        int x1 = x + dx, z1 = z + dz, x2 = x + 2 * dx, z2 = z + 2 * dz;
        for (int y = 0; y < h1; y++) {
            wall(x1, y, z1);
            wall(x2, y, z2);
        }
        trimStairs(x2, h1, z2, in, false);
        for (int y = h1; y < h2; y++) wall(x1, y, z1);
        trimStairs(x1, h2, z1, in, false);
    }

    /** A wall plane: outer layer at {@code base + t*along}, {@code out} pointing outside. */
    record Face(int x, int z, int ax, int az, int nx, int nz) {
        int ox(int t) {
            return x + t * ax;
        }

        int oz(int t) {
            return z + t * az;
        }

        String along(int s) {
            return Mason.dir(ax * s, az * s);
        }

        String inward() {
            return Mason.dir(-nx, -nz);
        }
    }

    /**
     * A recessed window: opening in the outer layer, panes in the inner, a lighter surround, a
     * pointed head of upside-down stairs, a mullion when three wide, and a hood mould.
     */
    private void window(Face fc, int c0, int w, int ys, int yh, Style.Mix glassMix, boolean tracery) {
        for (int t = c0; t < c0 + w; t++) {
            int x = fc.ox(t), z = fc.oz(t);
            for (int y = ys; y <= yh; y++) {
                m.clear(x, y, z);
                glass(x - fc.nx(), y, z - fc.nz(), glassMix);
            }
        }
        for (int y = ys; y <= yh + 1; y++) {
            surround(fc.ox(c0 - 1), y, fc.oz(c0 - 1));
            surround(fc.ox(c0 + w), y, fc.oz(c0 + w));
        }
        for (int t = c0; t < c0 + w; t++) surround(fc.ox(t), yh + 1, fc.oz(t));
        if (w >= 2) {
            trimStairs(fc.ox(c0), yh, fc.oz(c0), fc.along(-1), true);
            trimStairs(fc.ox(c0 + w - 1), yh, fc.oz(c0 + w - 1), fc.along(1), true);
        }
        if (w == 3 && tracery) {
            for (int y = ys; y < yh; y++) trimWall(fc.ox(c0 + 1), y, fc.oz(c0 + 1));
            trim(fc.ox(c0 + 1), yh, fc.oz(c0 + 1));
        }
        hood(fc, c0 - 1, c0 + w, yh + 1);
    }

    private void surround(int x, int y, int z) {
        if (!m.air(x, y, z)) trim(x, y, z);
    }

    /** Hood mould: upside-down stairs one block out, turned down at each end. */
    private void hood(Face fc, int t0, int t1, int y) {
        for (int t = t0; t <= t1; t++) {
            int x = fc.ox(t) + fc.nx(), z = fc.oz(t) + fc.nz();
            if (m.air(x, y, z)) trimStairs(x, y, z, fc.inward(), true);
        }
        for (int t : new int[]{t0, t1}) {
            int x = fc.ox(t) + fc.nx(), z = fc.oz(t) + fc.nz();
            if (m.air(x, y - 1, z)) trimStairs(x, y - 1, z, fc.inward(), true);
        }
    }

    // ---- ends ---------------------------------------------------------------------------------

    /** The east wall's great window, in the richer glass, and a cross on the gable. */
    private void eastEnd(Hall h) {
        F f = h.f;
        Face fc = new Face(f.x(h.u1, 0), f.z(h.u1, 0), f.vx(), f.vz(), f.ux(), f.uz());
        int w = h.hv >= 6 ? 5 : 3;
        int ys = h.h >= 9 ? 3 : 2;
        int yh = h.h + Math.max(1, (int) Math.round(h.pitch * 1.5));
        greatWindow(fc, w, ys, yh);
        cross(h.u1, h.ridge() + 1);
    }

    /** Three or five lights with stone mullions and Y tracery in the head. */
    private void greatWindow(Face fc, int w, int ys, int yh) {
        int c0 = -(w - 1) / 2;
        window(fc, c0, w, ys, yh, st.glassRich, true);
        if (w == 5) {
            for (int y = ys; y <= yh - 2; y++) {
                trimWall(fc.ox(-1), y, fc.oz(-1));
                trimWall(fc.ox(1), y, fc.oz(1));
                m.clear(fc.ox(0), y, fc.oz(0));
            }
            m.clear(fc.ox(0), yh - 1, fc.oz(0));
            m.clear(fc.ox(0), yh, fc.oz(0));
            trimStairs(fc.ox(-2), yh - 1, fc.oz(-2), fc.along(-1), true);
            trimStairs(fc.ox(2), yh - 1, fc.oz(2), fc.along(1), true);
            trim(fc.ox(-2), yh, fc.oz(-2));
            trim(fc.ox(2), yh, fc.oz(2));
            trimWall(fc.ox(-1), yh - 1, fc.oz(-1));
            trimWall(fc.ox(1), yh - 1, fc.oz(1));
            trimStairs(fc.ox(-1), yh, fc.oz(-1), fc.along(-1), true);
            trimStairs(fc.ox(1), yh, fc.oz(1), fc.along(1), true);
        }
    }

    /** A stone cross on a gable apex at (u, y) on the axis. */
    private void cross(int x, int y) {
        trimWall(x, y, 0);
        trimWall(x, y + 1, 0);
        trimWall(x, y + 2, 0);
        trimWall(x, y + 1, -1);
        trimWall(x, y + 1, 1);
    }

    /** The west front: a recessed portal with a gabled hood, a rose (or a great window) over it. */
    private void westFront(Hall h) {
        F f = h.f;
        Face fc = new Face(f.x(h.u0, 0), f.z(h.u0, 0), f.vx(), f.vz(), -f.ux(), -f.uz());
        int ph = h.h >= 9 ? 4 : 3;
        portal(fc, ph);
        // The eaves cornice carried across the front as a string course, so the gable reads as a zone of its own.
        for (int t = -h.hv + 1; t <= h.hv - 1; t++) {
            int x = fc.ox(t) + fc.nx(), z = fc.oz(t) + fc.nz();
            if (m.air(x, h.h - 1, z)) trimStairs(x, h.h - 1, z, fc.inward(), true);
        }
        // Lancets between the portal and the string course.
        int lo = ph + 4, hi = h.h - 3;
        if (hi - lo >= 2) {
            if (h.hv >= 5) {
                window(fc, -2, 1, lo, hi, st.glassPlain, false);
                window(fc, 2, 1, lo, hi, st.glassPlain, false);
            } else {
                window(fc, 0, 1, lo, hi, st.glassPlain, false);
            }
        }
        // A rose (or a lancet) in the gable.
        int rise = h.ridge() - h.h;
        if (h.hv >= 5 && rise >= 8) {
            int r = h.hv >= 8 && rise >= 11 ? 3 : 2;
            int yc = Math.min(h.ridge() - r - 4, h.h + r + 1);
            rose(fc, yc, r);
        } else if (rise >= 5) {
            window(fc, 0, 1, h.h + 1, h.h + rise - 3, st.glassRich, false);
        }
        if (spec.belfry() != Belfry.BELLCOTE) cross(h.u0, h.ridge() + 1);
    }

    /**
     * Two orders: a three-wide pointed opening in the face, the door in the inner layer under a
     * tympanum, colonnettes and a gabled hood standing a block proud.
     */
    private void portal(Face fc, int ph) {
        for (int t = -1; t <= 1; t++) {
            for (int y = 0; y <= ph; y++) m.clear(fc.ox(t), y, fc.oz(t));
        }
        trimStairs(fc.ox(-1), ph, fc.oz(-1), fc.along(-1), true);
        trimStairs(fc.ox(1), ph, fc.oz(1), fc.along(1), true);
        trim(fc.ox(0), ph + 1, fc.oz(0));
        for (int y = 0; y <= ph; y++) {
            trim(fc.ox(-2), y, fc.oz(-2));
            trim(fc.ox(2), y, fc.oz(2));
        }
        // Inner order: jambs, tympanum, door.
        int ix = -fc.nx(), iz = -fc.nz();
        for (int t = -1; t <= 1; t++) {
            for (int y = 0; y <= ph; y++) {
                int x = fc.ox(t) + ix, z = fc.oz(t) + iz;
                if (t == 0 && y <= 1) continue;
                trim(x, y, z);
            }
        }
        door(fc.ox(0) + ix, 0, fc.oz(0) + iz, fc.inward());
        // Projecting frame: wall shafts each side and a gabled hood.
        for (int y = 0; y < ph; y++) {
            trimWall(fc.ox(-2) + fc.nx(), y, fc.oz(-2) + fc.nz());
            trimWall(fc.ox(2) + fc.nx(), y, fc.oz(2) + fc.nz());
        }
        int hx = fc.nx(), hz = fc.nz();
        trimStairs(fc.ox(-2) + hx, ph, fc.oz(-2) + hz, fc.along(1), false);
        trimStairs(fc.ox(2) + hx, ph, fc.oz(2) + hz, fc.along(-1), false);
        trimStairs(fc.ox(-1) + hx, ph + 1, fc.oz(-1) + hz, fc.along(1), false);
        trimStairs(fc.ox(1) + hx, ph + 1, fc.oz(1) + hz, fc.along(-1), false);
        m.slab(fc.ox(0) + hx, ph + 2, fc.oz(0) + hz, st.trim.slab(seed, 0, 0, 0, 0), false);
        trim(fc.ox(0) + hx, ph + 1, fc.oz(0) + hz);
    }

    private void door(int x, int y, int z, String facing) {
        m.set(x, y, z, st.door, "facing", facing, "half", "lower", "hinge", "left", "open", "false");
        m.set(x, y + 1, z, st.door, "facing", facing, "half", "upper", "hinge", "left", "open", "false");
    }

    /** A wheel window of radius 2 or 3: spokes of stone walls, rich glass behind, a stone ring. */
    private void rose(Face fc, int yc, int r) {
        double in = (r + 0.5) * (r + 0.5), out = (r + 1.5) * (r + 1.5);
        for (int dt = -r - 1; dt <= r + 1; dt++) {
            for (int dy = -r - 1; dy <= r + 1; dy++) {
                double d = dt * dt + dy * dy;
                int x = fc.ox(dt), z = fc.oz(dt), y = yc + dy;
                if (d <= in) {
                    glass(x - fc.nx(), y, z - fc.nz(), st.glassRich);
                    if (dt == 0 && dy == 0) trim(x, y, z);
                    else if (dt == 0 || dy == 0 || (r >= 3 && Math.abs(dt) == Math.abs(dy) && Math.abs(dt) <= 1)) trimWall(x, y, z);
                    else m.clear(x, y, z);
                } else if (d <= out && !m.air(x, y, z)) {
                    trim(x, y, z);
                }
            }
        }
    }

    /** A little open gable on the west apex holding a bell, capped with stairs and a cross. */
    private void bellcote(Hall h) {
        int u = h.u0;
        int base = h.top(h.hv) + 1;
        int top = h.ridge() + 4;
        for (int x = u; x <= u + 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = base; y <= top; y++) wall(x, y, z);
            }
            for (int y = h.ridge() + 1; y <= h.ridge() + 2; y++) m.clear(x, y, 0);
        }
        trimStairs(u, h.ridge() + 3, 0, "east", true);
        m.set(u, h.ridge() + 2, 0, "minecraft:bell", "attachment", "ceiling", "facing", "north");
        for (int x = u - 1; x <= u + 2; x++) {
            trimStairs(x, top + 1, -1, "south", false);
            trimStairs(x, top + 1, 1, "north", false);
            trim(x, top + 1, 0);
        }
        for (int x = u; x <= u + 1; x++) {
            trimStairs(x, top, -2, "south", true);
            trimStairs(x, top, 2, "north", true);
        }
        cross(u, top + 2);
    }

    // ---- chancel ------------------------------------------------------------------------------

    /** The pointed arch from nave to chancel, its floor a step up. */
    private void chancelArch(Hall nave, Hall ch) {
        int half = ch.hv - 2;
        int ya = Math.max(4, ch.h - 2);
        for (int x = nave.u1 - 1; x <= nave.u1; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = 0; y <= ya; y++) m.clear(x, y, z);
                m.set(x, 0, z, st.sanctuary.block(seed, x, 0, z, 0));
            }
            if (half >= 1) {
                trimStairs(x, ya, -half, "north", true);
                trimStairs(x, ya, half, "south", true);
            }
            for (int y = 1; y <= ya; y++) {
                trim(x, y, -half - 1);
                trim(x, y, half + 1);
            }
        }
        // Rood beam across the arch.
        for (int z = -half; z <= half; z++) m.set(nave.u1 - 2, ya - 1, z, st.timberLog, "axis", "z");
    }

    private void chancelInterior(Hall ch) {
        int iz = ch.hv - 2;
        int rise = Math.min(sanctuaryRise, Math.max(1, ch.h - 4));
        int start = ch.u0 + 2;
        for (int x = start; x <= ch.u1 - 2; x++) {
            int top = Math.min(rise, x - start + 1) - 1;
            for (int z = -iz; z <= iz; z++) {
                for (int y = 0; y <= top; y++) m.set(x, y, z, st.sanctuary.block(seed, x, y, z, 0));
                if (x - start < rise) trimStairs(x, top, z, "east", false);
            }
        }
        int altarX = Math.min(ch.u1 - 2, Math.max(start + rise, ch.u1 - (interiorVariant % 2 == 0 ? 3 : 4)));
        altar(altarX, rise, Math.min(1, iz - 1));
        // Choir stalls face each other; keep the central route to the altar clear.
        if (iz >= 2 && ch.u1 - start > 7) {
            for (int x = start + rise + 1; x < altarX - 1; x++) {
                for (int side : new int[]{-1, 1}) m.stairs(x, rise, side * iz, st.timberStairs,
                        side > 0 ? "south" : "north", false);
            }
        }
        trusses(ch, rise);
    }

    private void altar(int x, int y, int half) {
        for (int z = -half; z <= half; z++) trim(x, y, z);
        for (int z = -half; z <= half; z += Math.max(1, half * 2)) {
            m.set(x, y + 1, z, "minecraft:candle", "candles", "3", "lit", "true");
        }
        if (half >= 1) m.set(x, y + 1, 0, "minecraft:white_candle", "candles", "1", "lit", "true");
    }

    // ---- nave interior -------------------------------------------------------------------------

    private void naveInterior(Hall h, boolean sanctuary) {
        int iz = h.hv - 2, ix0 = h.u0 + 2, ix1 = h.u1 - 2;
        int aisle = iz >= 5 ? 1 : 0;
        boolean sunken = (interiorVariant == 1 || interiorVariant == 4) && iz >= 3 && ix1 - ix0 >= 12;
        boolean gallery = (interiorVariant == 2 || interiorVariant == 4 || interiorVariant == 5)
                && h.h >= 8 && ix1 - ix0 >= 14 && iz >= 3;
        int depth = interiorVariant == 4 ? 3 : 2;
        int start = ix0 + (gallery ? 6 : 3), end = ix1 - (sanctuary ? 6 : 3);
        if (end - start < depth * 2 + 1) sunken = false;
        int pewEnd = sanctuary ? ix1 - 6 : ix1 - 2;
        int pewStart = ix0 + (gallery ? 6 : 2);
        int spacing = interiorVariant == 3 ? 3 : 2;
        for (int x = ix0; x <= ix1; x++) {
            int level = sunken ? -Math.max(0, Math.min(depth, Math.min(x - start + 1, end - x + 1))) : 0;
            for (int z = -iz; z <= iz; z++) {
                // A level walk along each side links every porch to the west door.
                int fy = Math.abs(z) == iz ? 0 : level;
                if (fy < 0) {
                    for (int y = fy; y <= 0; y++) {
                        m.clear(x, y, z);
                        excavation.add(new int[]{x, y, z});
                    }
                }
                String block = Math.abs(z) == iz || x == ix0 || x == ix1 ? st.floorBorder.block(seed, x, fy - 1, z, 0)
                        : Math.floorMod((interiorVariant == 3 ? x / 3 : x) + z, 2) == 0
                        ? st.floorField.block(seed, x, fy - 1, z, 0) : st.floorAccent.block(seed, x, fy - 1, z, 0);
                m.set(x, fy - 1, z, block);
                if (sunken && Math.abs(z) == iz) {
                    for (int y = -depth - 1; y < -1; y++) wall(x, y, z);
                }
                boolean step = fy < 0 && (x < start + depth || x > end - depth);
                if (step) trimStairs(x, fy, z, x < start + depth ? "west" : "east", false);
                boolean pew = x >= pewStart && x <= pewEnd && (x - pewStart) % spacing == 0
                        && Math.abs(z) > aisle && Math.abs(z) < iz && !step;
                if (pew) m.stairs(x, fy, z, st.timberStairs, interiorVariant == 3 ? (z > 0 ? "south" : "north") : "west", false);
                if (z == 0 && !step && x <= pewEnd + 1) m.set(x, fy, z, st.carpet);
            }
        }
        if (sanctuary) {
            int rise = Math.min(sanctuaryRise, Math.max(1, h.h - 4));
            int from = ix1 - 4;
            for (int x = from; x <= ix1; x++) {
                int top = Math.min(rise, x - from + 1) - 1;
                for (int z = -iz; z <= iz; z++) {
                    for (int y = 0; y <= top; y++) m.set(x, y, z, st.sanctuary.block(seed, x, y, z, 0));
                    if (x - from < rise) trimStairs(x, top, z, "east", false);
                }
            }
            altar(ix1 - 1, rise, Math.min(1, iz - 1));
        }
        if (gallery) balcony(ix0, iz);
        // A font or lectern changes the west-end arrangement as well as the seating.
        int fx = ix0 + 1, fz = -iz + 1;
        if (interiorVariant % 2 == 0) {
            m.set(fx, 0, fz, "minecraft:water_cauldron", "level", "3");
        } else m.set(fx, 0, fz, "minecraft:lectern", "facing", "east");
        trusses(h, 0);
    }

    /** A west loft with a stair beside the side walk, supported deck, and attached trapdoor rail. */
    private void balcony(int x0, int iz) {
        int deck = 4, end = x0 + 4;
        for (int x = x0; x <= end; x++) {
            for (int z = -iz; z <= iz; z++) m.set(x, deck - 1, z, st.timber);
        }
        for (int z : new int[]{-iz + 1, iz - 1}) {
            for (int y = 0; y < deck - 1; y++) m.set(end, y, z, st.timberLog, "axis", "y");
        }
        // The loft stair runs along the side opposite the porch so a west-bay side door stays clear.
        int side = -porchSide;
        int lane = side * (iz - 1);
        for (int k = 0; k < deck; k++) {
            int x = end + deck - k;
            for (int z : new int[]{lane, side * iz}) {
                for (int y = 0; y < k; y++) m.set(x, y, z, st.timber);
                m.stairs(x, k, z, st.timberStairs, "west", false);
                for (int y = k + 1; y <= k + 3; y++) m.clear(x, y, z);
            }
        }
        for (int z = -iz; z <= iz; z++) {
            if (z == lane || z == side * iz) continue;
            m.set(end, deck, z, st.timberTrapdoor, "facing", "west", "open", "true", "half", "bottom");
        }
        for (int z = -iz + 1; z <= iz - 1; z++) {
            m.stairs(x0 + 1, deck, z, st.timberStairs, "west", false);
        }
    }

    /** Tie beam, king post, wall-post brackets and a lantern on a chain at every truss. */
    private void trusses(Hall h, int floor) {
        int iz = h.hv - 2;
        for (int x : h.trusses) {
            for (int z = -iz; z <= iz; z++) m.set(x, h.h - 1, z, st.timberLog, "axis", "z");
            int ridgeLow = h.low(h.hv + 1);
            for (int y = h.h; y < ridgeLow; y++) m.set(x, y, 0, st.timberLog, "axis", "y");
            for (int s : new int[]{-1, 1}) {
                m.stairs(x, h.h - 2, s * iz, st.timberStairs, s < 0 ? "north" : "south", true);
                trimStairs(x, h.h - 3, s * iz, s < 0 ? "north" : "south", true);
            }
            int lamp = Math.max(floor + 3, h.h - 5);
            for (int y = lamp + 1; y <= h.h - 2; y++) m.set(x, y, 0, "minecraft:iron_chain", "axis", "y");
            m.set(x, lamp, 0, "minecraft:lantern", "hanging", "true");
        }
    }

    // ---- porch --------------------------------------------------------------------------------

    private void porch(Hall nave, int bay) {
        int c = (nave.bays.get(bay) + nave.bays.get(bay + 1)) / 2;
        int hvp = porchWidth;
        int hp = Math.max(4, Math.min(6, nave.h - 4));
        F f = new F(c, porchSide * nave.hv, 0, porchSide, 1, 0);
        for (int u = 1; u <= porchDepth; u++) {
            for (int v = -hvp; v <= hvp; v++) m.set(f.x(u, v), -1, f.z(u, v), st.plinth.block(seed, u, -1, v, 0));
        }
        // Build only into air so the porch tucks under the nave's eaves.
        Hall body = new Hall(f, 1, porchDepth, hvp, hp, 1);
        body.gableLo = false;
        body.wallLo = false;
        hall(body);
        dressSidesPorch(body);
        // Open archway in the gable end.
        Face front = new Face(f.x(porchDepth, 0), f.z(porchDepth, 0), f.vx(), f.vz(), f.ux(), f.uz());
        for (int t = -1; t <= 1; t++) {
            for (int y = 0; y <= hp - 2; y++) {
                m.clear(front.ox(t), y, front.oz(t));
                m.clear(front.ox(t) - front.nx(), y, front.oz(t) - front.nz());
            }
        }
        trimStairs(front.ox(-1), hp - 2, front.oz(-1), front.along(-1), true);
        trimStairs(front.ox(1), hp - 2, front.oz(1), front.along(1), true);
        int apex = body.ridge() + 1;
        trimWall(front.ox(0), apex, front.oz(0));
        trimWall(front.ox(0), apex + 1, front.oz(0));
        trimWall(front.ox(0), apex + 2, front.oz(0));
        trimWall(front.ox(-1), apex + 1, front.oz(-1));
        trimWall(front.ox(1), apex + 1, front.oz(1));
        // The side door into the nave.
        for (int y = 0; y <= 2; y++) m.clear(c, y, porchSide * nave.hv);
        trimStairs(c, 2, porchSide * nave.hv, f.du(1), true);
        door(c, 0, porchSide * (nave.hv - 1), f.du(-1));
        m.clear(c, 2, porchSide * (nave.hv - 1));
        // Stone benches along the porch walls.
        for (int u = 2; u < porchDepth; u++) {
            m.stairs(f.x(u, -hvp + 1), 0, f.z(u, -hvp + 1), st.trim.stairs(seed, u, 0, -2, 0), "west", false);
            m.stairs(f.x(u, hvp - 1), 0, f.z(u, hvp - 1), st.trim.stairs(seed, u, 0, 2, 0), "east", false);
        }
    }

    private void dressSidesPorch(Hall p) {
        F f = p.f;
        buttress(f.x(p.u1, -p.hv), f.z(p.u1, -p.hv), f.ux(), f.uz(), p.h);
        buttress(f.x(p.u1, p.hv), f.z(p.u1, p.hv), f.ux(), f.uz(), p.h);
    }

    // ---- tower --------------------------------------------------------------------------------

    /**
     * A west tower (or a free-standing one): two-thick walls in three stages divided by string
     * courses, angle buttresses, a louvred belfry with a bell, and a spire, pyramid or parapet.
     */
    private void tower(int x0, int t, int ht, boolean free, int naveRidge) {
        int th = (t - 1) / 2;
        int x1 = x0 + t - 1;
        int s1 = (int) Math.round(ht * 0.4);
        int belfry = Math.max(6, (int) Math.round(ht * 0.26));
        int s2 = Math.max(s1 + 4, ht - belfry);
        if (!free) s2 = Math.max(s2, naveRidge + 1);
        for (int x = x0; x <= x1; x++) {
            for (int z = -th; z <= th; z++) {
                m.set(x, -1, z, st.plinth.block(seed, x, -1, z, 0));
                boolean shell = x <= x0 + 1 || x >= x1 - 1 || Math.abs(z) >= th - 1;
                for (int y = 0; y < ht; y++) {
                    if (shell) wall(x, y, z);
                    else m.clear(x, y, z);
                }
                if (!shell) {
                    for (int y : new int[]{s1, s2}) m.set(x, y, z, st.timber);
                    // The tower's roof, laid in the roofing stone under the parapet or the spire.
                    m.set(x, ht - 1, z, st.roof.block(seed, x, ht - 1, z, 0));
                }
            }
        }
        // Ladder up the north inner wall, through hatches in the floors.
        int lx = x0 + 2, lz = -(th - 2);
        for (int y = 0; y < ht - 1; y++) {
            m.set(lx, y, lz, "minecraft:ladder", "facing", "south");
        }
        // Angle buttresses at the corners that stand free.
        for (int sz : new int[]{-1, 1}) {
            towerButtress(x0, sz * th, -1, 0, s1, s2);
            towerButtress(x0, sz * th, 0, sz, s1, s2);
            if (free) {
                towerButtress(x1, sz * th, 1, 0, s1, s2);
                towerButtress(x1, sz * th, 0, sz, s1, s2);
            }
        }
        // String courses at the stage lines and a cornice at the top.
        for (int y : new int[]{s1, s2}) ring(x0, x1, th, y, false);
        ring(x0, x1, th, ht - 1, true);

        List<Face> faces = new ArrayList<>();
        faces.add(new Face(x0, 0, 0, 1, -1, 0));
        faces.add(new Face(x0 + th, -th, 1, 0, 0, -1));
        faces.add(new Face(x0 + th, th, 1, 0, 0, 1));
        faces.add(new Face(x1, 0, 0, 1, 1, 0));
        int ph = 4;
        portal(faces.get(0), ph);
        if (!free) {
            // The tower arch into the nave.
            for (int x = x1 - 1; x <= x1; x++) {
                for (int z = -1; z <= 1; z++) for (int y = 0; y <= 3; y++) m.clear(x, y, z);
                trimStairs(x, 3, -1, "north", true);
                trimStairs(x, 3, 1, "south", true);
            }
        }
        for (int i = 0; i < 4; i++) {
            Face fc = faces.get(i);
            boolean east = i == 3;
            // Stage two: a single lancet on the faces the nave does not hide.
            int lo = i == 0 ? ph + 4 : s1 + 2;
            int hi = (i == 0 ? s1 : s2) - 2;
            if (i == 0 && hi - lo >= 2) window(fc, 0, 1, lo, hi, st.glassPlain, false);
            if (!east || free) {
                if (s2 - 2 - (s1 + 2) >= 2) window(fc, 0, 1, s1 + 2, s2 - 2, st.glassPlain, false);
            }
            belfryOpenings(fc, th, s2 + 2, ht - 3);
        }
        // The bell, hung from a beam.
        int bx = x0 + th;
        for (int x = x0 + 2; x <= x1 - 2; x++) m.set(x, s2 + 4, 0, st.timberLog, "axis", "x");
        m.set(bx, s2 + 3, 0, "minecraft:bell", "attachment", "ceiling", "facing", "north");

        switch (spec.cap() == null ? TowerCap.SPIRE : spec.cap()) {
            case FLAT -> parapet(x0, x1, th, ht);
            case SPIRE -> {
                // A needle spire set inside the parapet, the pinnacles standing round its foot.
                parapet(x0, x1, th, ht);
                spire(x0 + th, th - 1, ht, (int) Math.round(Math.max(ht * 0.85, th * 4.0)), true);
            }
            case SADDLEBACK -> saddleback(x0, x1, th, ht);
            default -> spire(x0 + th, th, ht, th * 2 + 2, false);
        }
    }

    /** A saddleback: a steep little gabled roof on the tower, its gables east and west with a cross on each. */
    private void saddleback(int x0, int x1, int th, int ht) {
        for (int k = 0; k <= th; k++) {
            int y = ht + k, zs = th - k;
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
        cross(x0, ht + th + 1);
        cross(x1, ht + th + 1);
    }

    private void towerButtress(int x, int z, int dx, int dz, int s1, int s2) {
        String in = Mason.dir(-dx, -dz);
        int x1 = x + dx, z1 = z + dz, x2 = x + 2 * dx, z2 = z + 2 * dz;
        for (int y = 0; y < s1; y++) {
            wall(x1, y, z1);
            wall(x2, y, z2);
        }
        trimStairs(x2, s1, z2, in, false);
        for (int y = s1; y < s2; y++) wall(x1, y, z1);
        trimStairs(x1, s2, z1, in, false);
    }

    /** A course of stairs round the tower at y: weathered (bottom) or a cornice (top). */
    private void ring(int x0, int x1, int th, int y, boolean cornice) {
        for (int x = x0; x <= x1; x++) {
            for (int s : new int[]{-1, 1}) {
                int z = s * (th + 1);
                if (m.air(x, y, z)) trimStairs(x, y, z, s < 0 ? "south" : "north", cornice);
            }
        }
        for (int z = -th; z <= th; z++) {
            if (m.air(x0 - 1, y, z)) trimStairs(x0 - 1, y, z, "east", cornice);
            if (m.air(x1 + 1, y, z)) trimStairs(x1 + 1, y, z, "west", cornice);
        }
    }

    /** Paired lights with a mullion, louvred with trapdoors, under a hood. */
    private void belfryOpenings(Face fc, int th, int lo, int hi) {
        if (hi - lo < 2) return;
        int[] ts = th >= 4 ? new int[]{-2, -1, 1, 2} : new int[]{-1, 1};
        for (int t : ts) {
            for (int y = lo; y <= hi; y++) {
                m.clear(fc.ox(t), y, fc.oz(t));
                m.set(fc.ox(t) - fc.nx(), y, fc.oz(t) - fc.nz(), st.timberTrapdoor, "facing", fc.inward(), "half", "top", "open", "false");
            }
        }
        if (th >= 4) {
            trimStairs(fc.ox(-2), hi, fc.oz(-2), fc.along(-1), true);
            trimStairs(fc.ox(-1), hi, fc.oz(-1), fc.along(1), true);
            trimStairs(fc.ox(1), hi, fc.oz(1), fc.along(-1), true);
            trimStairs(fc.ox(2), hi, fc.oz(2), fc.along(1), true);
        }
        for (int y = lo; y <= hi; y++) trimWall(fc.ox(0), y, fc.oz(0));
        int edge = th >= 4 ? 3 : 2;
        for (int y = lo; y <= hi + 1; y++) {
            surround(fc.ox(-edge), y, fc.oz(-edge));
            surround(fc.ox(edge), y, fc.oz(edge));
        }
        hood(fc, -edge, edge, hi + 1);
        for (int t = -edge; t <= edge; t++) {
            int x = fc.ox(t) + fc.nx(), z = fc.oz(t) + fc.nz();
            if (m.air(x, lo - 1, z)) trimStairs(x, lo - 1, z, fc.inward(), false);
        }
    }

    /** A walled parapet with a pinnacle at each corner. */
    private void parapet(int x0, int x1, int th, int ht) {
        for (int x = x0; x <= x1; x++) {
            for (int z = -th; z <= th; z++) {
                boolean edge = x == x0 || x == x1 || Math.abs(z) == th;
                if (edge) trimWall(x, ht, z);
            }
        }
        for (int x : new int[]{x0, x1}) {
            for (int z : new int[]{-th, th}) {
                trim(x, ht, z);
                trimWall(x, ht + 1, z);
                trimWall(x, ht + 2, z);
                trimWall(x, ht + 3, z);
            }
        }
    }

    /**
     * An octagonal broach spire (or a square pyramid): a hollow shell that narrows one block every
     * few courses, its steps finished in stairs, and a cross on the needle.
     */
    private void spire(int cx, int th, int base, int height, boolean octagon) {
        int broach = 0;
        for (int k = 0; k < height; k++) {
            int y = base + k;
            for (int dx = -th; dx <= th; dx++) {
                for (int dz = -th; dz <= th; dz++) {
                    if (!inSpire(dx, dz, k, th, height, broach, octagon)) continue;
                    boolean shell = !inSpire(dx + 1, dz, k, th, height, broach, octagon) || !inSpire(dx - 1, dz, k, th, height, broach, octagon)
                            || !inSpire(dx, dz + 1, k, th, height, broach, octagon) || !inSpire(dx, dz - 1, k, th, height, broach, octagon);
                    if (!shell) continue;
                    int x = cx + dx, z = dz;
                    if (!inSpire(dx, dz, k + 1, th, height, broach, octagon)) {
                        String facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                        if (dx == 0 && dz == 0) m.set(x, y, z, st.roof.block(seed, x, y, z, 0));
                        else m.stairs(x, y, z, st.roof.stairs(seed, x, y, z, 0), facing, false);
                    } else {
                        m.set(x, y, z, st.roof.block(seed, x, y, z, 0));
                    }
                }
            }
        }
        int top = base + height;
        trimWall(cx, top, 0);
        cross(cx, top + 1);
    }

    private static boolean inSpire(int dx, int dz, int k, int th, int height, int broach, boolean octagon) {
        if (k >= height) return dx == 0 && dz == 0 && k == height - 1;
        double r = (th + 0.49) * (1 - k / (double) height);
        int a = Math.abs(dx), b = Math.abs(dz);
        if (a > r || b > r) return false;
        if (!octagon || k < broach) return true;
        return a + b <= r * 1.42;
    }

    // ---- plinth -------------------------------------------------------------------------------

    /**
     * The base course round everything that stands on the ground: one block out at floor level and
     * a weathered stair on it. Found by flooding the outside at y = 0, so courtyards and the inside
     * are left alone.
     */
    private void plinth() {
        int minX = -40, maxX = 140, minZ = -60, maxZ = 60;
        int sx = maxX - minX + 1, sz = maxZ - minZ + 1;
        boolean[] solid = new boolean[sx * sz];
        boolean[] outside = new boolean[sx * sz];
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) solid[(x - minX) * sz + (z - minZ)] = !m.air(x, 0, z);
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{minX, minZ});
        outside[0] = true;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = p[0] + d[0], z = p[1] + d[1];
                if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                int i = (x - minX) * sz + (z - minZ);
                if (outside[i] || solid[i]) continue;
                outside[i] = true;
                q.add(new int[]{x, z});
            }
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                int i = (x - minX) * sz + (z - minZ);
                if (solid[i]) {
                    if (m.air(x, -1, z)) m.set(x, -1, z, st.plinth.block(seed, x, -1, z, 0));
                    continue;
                }
                // Paved ground (a porch floor) is not where a water table goes.
                if (!outside[i] || !m.air(x, -1, z)) continue;
                String facing = null;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (solid[(x + d[0] - minX) * sz + (z + d[1] - minZ)]) {
                        facing = Mason.dir(d[0], d[1]);
                        break;
                    }
                }
                if (facing == null) {
                    for (int[] d : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                        if (solid[(x + d[0] - minX) * sz + (z + d[1] - minZ)]) {
                            facing = Mason.dir(d[0], 0);
                            break;
                        }
                    }
                }
                if (facing == null) continue;
                m.set(x, -1, z, st.plinth.block(seed, x, -1, z, 0));
                m.stairs(x, 0, z, st.plinth.stairs(seed, x, 0, z, 0), facing, false);
            }
        }
    }
}
