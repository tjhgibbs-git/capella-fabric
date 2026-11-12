package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.raster.Volume;

/**
 * Shared frame for the garrison ranges (barracks, stables): a rectangular hall in the Mason frame
 * (+x along the length from the west door at the origin, z across, y up, floor at y = -1).
 * Storeys are five blocks floor to floor, so raising the wall height adds whole storeys.
 */
abstract class Range {
    static final int STOREY = 5;
    final BuildingParams p;
    final Mason m;
    final Style s;
    final double seed;
    final int w, h, l, z0, z1, n;
    /** Bay divisions along the length, ends included: buttresses, posts, joists and trusses line up on them. */
    final int[] posts;
    final boolean furnish;
    final String liningStairs, bed;

    Range(BuildingParams clamped) {
        p = clamped;
        m = new Mason(p.seed());
        s = Style.of(p.get(Params.STONE));
        seed = p.seed();
        w = p.integer(Params.WIDTH);
        h = p.integer(Params.HEIGHT);
        l = p.integer(Params.LENGTH);
        z0 = -w / 2;
        z1 = z0 + w - 1;
        n = storeys(h);
        int nb = Math.max(1, (int) Math.round((l - 1) / 4.0));
        posts = new int[nb + 1];
        for (int i = 0; i <= nb; i++) posts[i] = (int) Math.round(i * (l - 1) / (double) nb);
        furnish = p.bool(Params.FURNISHINGS);
        liningStairs = s.lining.replace("_planks", "_stairs");
        bed = s.carpet.replace("_carpet", "_bed");
    }

    /** Whole storeys that fit under a wall of height {@code h}; each needs four clear blocks. */
    static int storeys(int h) {
        return Math.max(1, (h + 1) / STOREY);
    }

    /** Standing level of storey {@code i}. */
    static int level(int i) {
        return STOREY * i;
    }

    abstract void build();

    final Volume volume(int turns) {
        build();
        return m.toVolume(turns);
    }

    boolean isPost(int x) {
        for (int q : posts) if (q == x) return true;
        return false;
    }

    void stone(int x, int y, int z) {
        m.set(x, y, z, s.wall.block(seed, x, y, z, 0));
    }

    void trim(int x, int y, int z) {
        m.set(x, y, z, s.trim.block(seed, x, y, z, 0));
    }

    void trimStairs(int x, int y, int z, String facing, boolean top) {
        m.stairs(x, y, z, s.trim.stairs(seed, x, y, z, 0), facing, top);
    }

    void log(int x, int y, int z, String axis) {
        m.set(x, y, z, s.timberLog, "axis", axis);
    }

    void lantern(int x, int y, int z, boolean hanging) {
        m.set(x, y, z, "minecraft:lantern", "hanging", hanging ? "true" : "false");
    }

    /** Intermediate floors: planks, with a log joist on every bay line showing from below. */
    void floors(int za, int zb) {
        for (int i = 1; i < n; i++) {
            int y = level(i) - 1;
            for (int x = 1; x < l - 1; x++)
                for (int z = za; z <= zb; z++) {
                    if (isPost(x)) log(x, y, z, "z");
                    else m.set(x, y, z, s.timber);
                }
        }
    }

    interface Profile {
        int rise(int d);
    }

    interface Fill {
        void at(int x, int y, int z);
    }

    abstract void roofStairs(int x, int y, int z, String facing);

    abstract void roofBlock(int x, int y, int z);

    abstract void ridge(int x, int y, int z);

    abstract void verge(int x, int y, int z, String facing);

    abstract void plate(int x, int y, int z);

    /** Distance in from the eave overhang, for the roof span z0-1 .. z1+1. */
    int depth(int z) {
        return Math.min(z - (z0 - 1), (z1 + 1) - z);
    }

    int dMax() {
        return (w + 1) / 2;
    }

    /** Lowest roof block in a column (the ceiling's underside sits just below it). */
    int roofLow(Profile r, int base, int z) {
        int d = depth(z);
        return d == 0 ? base + r.rise(0) : base + r.rise(d - 1) + 1;
    }

    /**
     * A ridged roof along x over the span z0-1 .. z1+1 (one block of eave overhang), its surface at
     * base + rise(d). Slopes steeper than one in one are filled solid; the rest are lined underneath
     * with upside-down stairs so the ceiling reads as a slope, not a staircase. Gable planes x = 0
     * and x = l-1 are closed by {@code gable}; {@code verge} adds barge boards at x = -1 and x = l.
     * Returns the top of the ridge.
     */
    int roof(int base, Profile r, boolean verge, Fill gable) {
        int a = z0 - 1, b = z1 + 1, top = base;
        for (int z = a; z <= b; z++) {
            int dn = z - a, ds = b - z, d = Math.min(dn, ds);
            boolean ridgeCol = dn == ds;
            String facing = dn < ds ? "south" : "north", out = dn < ds ? "north" : "south";
            int t = base + r.rise(d), lo = roofLow(r, base, z);
            boolean steep = d > 0 && r.rise(d) - r.rise(d - 1) > 1;
            top = Math.max(top, t + (ridgeCol ? 1 : 0));
            for (int x = -1; x <= l; x++) {
                boolean end = x == -1 || x == l, gableWall = x == 0 || x == l - 1;
                if (end && !verge) continue;
                if (ridgeCol) {
                    roofBlock(x, t, z);
                    ridge(x, t + 1, z);
                } else if (end) verge(x, t, z, facing);
                else roofStairs(x, t, z, facing);
                for (int y = lo; y < t; y++) roofBlock(x, y, z);
                if (end) continue;
                if (gableWall) {
                    if (d >= 1) for (int y = base; y < lo; y++) gable.at(x, y, z);
                } else if (d == 1) {
                    for (int y = base; y < lo; y++) plate(x, y, z);
                } else if (d >= 2 && !steep) {
                    if (ridgeCol) m.set(x, lo - 1, z, s.lining);
                    else m.stairs(x, lo - 1, z, liningStairs, out, true);
                }
            }
        }
        return top;
    }

    /** Tie beams on the inner bay lines at eaves level, each with a king post to the ridge. */
    void trusses(Profile r, int base) {
        int kz = Math.floorDiv(z0 + z1, 2);
        for (int x : posts) {
            if (x <= 0 || x >= l - 1) continue;
            for (int z = z0 + 1; z < z1; z++) log(x, base, z, "z");
            for (int y = base + 1; y < roofLow(r, base, kz) - 1; y++) log(x, y, kz, "y");
        }
    }

    /**
     * Beds along one long wall of a storey, two to a bay with a nightstand between and a footlocker
     * at the foot where the room is deep enough. {@code side} is -1 for the north wall, +1 south.
     */
    void beds(int level, int side, int xFrom, int xTo, boolean bunks) {
        int head = side > 0 ? z1 - 1 : z0 + 1;
        int foot = head - side, locker = foot - side;
        String facing = side > 0 ? "south" : "north", toAisle = side > 0 ? "north" : "south";
        for (int i = 0; i + 1 < posts.length; i++) {
            int a = posts[i] + 1, b = posts[i + 1] - 1;
            if (a < xFrom || b > xTo || b < a) continue;
            int[] cols = b > a ? new int[]{a, b} : new int[]{a};
            for (int x : cols) {
                for (int y : bunks ? new int[]{level, level + 2} : new int[]{level}) {
                    m.set(x, y, head, bed, "part", "head", "facing", facing);
                    m.set(x, y, foot, bed, "part", "foot", "facing", facing);
                }
                if (bunks) {
                    m.slab(x, level + 1, head, s.timberSlab, true);
                    m.slab(x, level + 1, foot, s.timberSlab, true);
                }
                if (Math.abs(locker) >= 2) m.set(x, level, locker, "minecraft:chest", "facing", toAisle);
            }
            for (int x = a + 1; x < b; x++) {
                m.set(x, level, head, "minecraft:barrel", "facing", "up");
                if (x == (a + b) / 2) m.set(x, level + 1, head, "minecraft:candle", "candles", "2", "lit", "true");
            }
        }
    }
}
