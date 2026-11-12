package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.hash.Hash;
import io.github.tjhgibbs.capella.core.params.BuildingParams;

/**
 * A timber stable range on a low rubble sill: boarded walls framed by posts and girts, a gambrel
 * roof of wooden shingles with a louvred cupola, and barn doors at both gables. Each loose box has
 * its own stable door to the yard; a tack room and feed store close the east end. Taller ranges
 * add a hayloft with a hoist door over the west entrance, then grooms' lofts, reached by a ladder.
 */
final class Stables extends Range {
    /** Aisle centre: the middle when boxes line both walls, otherwise along the north wall. */
    private final int zc;
    private final boolean twoSided;
    private final Profile gambrel;

    Stables(BuildingParams clamped) {
        super(clamped);
        twoSided = w >= 13;
        zc = twoSided ? 0 : z0 + 2;
        int a = Math.max(1, (int) Math.round(dMax() * 0.55));
        gambrel = d -> d <= a ? 2 * d : 2 * a + (d - a);
    }

    private int front(int side) {
        return twoSided ? side * 2 : zc + 2;
    }

    private int[] sides() {
        return twoSided ? new int[]{-1, 1} : new int[]{1};
    }

    @Override
    void build() {
        shell();
        floors(z0 + 1, z1 - 1);
        int peak = roof(h, gambrel, true, this::gableFill);
        trusses(gambrel, h);
        cupola(peak);
        doors();
        if (n > 1) loft();
        if (furnish) furnish();
        lights();
    }

    private void shell() {
        for (int x = -1; x <= l; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean inside = x > 0 && x < l - 1 && z > z0 && z < z1;
                String f;
                if (!inside) f = s.plinth.block(seed, x, -1, z, 0);
                else if (Math.abs(z - zc) <= 1) f = s.plinth.block(seed, x, -1, z, 0);
                else f = Hash.hash(seed, "straw", x, 0, z) < 0.3 ? "minecraft:rooted_dirt" : "minecraft:coarse_dirt";
                m.set(x, -1, z, f);
            }
        for (int x = 0; x < l; x++)
            for (int z = z0; z <= z1; z++) {
                boolean longWall = z == z0 || z == z1, gableWall = x == 0 || x == l - 1;
                if (!longWall && !gableWall) continue;
                boolean corner = longWall && gableWall;
                boolean post = corner || longWall && isPost(x) || gableWall && (Math.abs(z - zc) == 2);
                for (int y = 0; y < h; y++) {
                    if (y < 1) stone(x, y, z);
                    else if (post) log(x, y, z, "y");
                    else if (y % STOREY == STOREY - 1) log(x, y, z, longWall ? "x" : "z");
                    else m.set(x, y, z, s.lining);
                }
            }
        // Small square lights upstairs, one a bay, and on the aisle wall below.
        for (int i = 0; i < n; i++) {
            int L = level(i);
            for (int b = 0; b + 1 < posts.length; b++) {
                int x = (posts[b] + posts[b + 1]) / 2;
                for (int z : new int[]{z0, z1}) {
                    if (i == 0 && boxWall(z)) continue;
                    int y = i == 0 ? 2 : L + 1;
                    if (y + 1 >= h) continue;
                    m.set(x, y, z, "minecraft:glass_pane");
                    m.set(x, y + 1, z, "minecraft:glass_pane");
                    m.slab(x, y + 2, z == z0 ? z0 - 1 : z1 + 1, s.timberSlab, false);
                }
            }
        }
    }

    /** Whether the long wall at {@code z} backs the loose boxes (and so carries their stable doors). */
    private boolean boxWall(int z) {
        return z == z1 || twoSided && z == z0;
    }

    private void gableFill(int x, int y, int z) {
        int centre = Math.floorDiv(z0 + z1, 2);
        if (z == centre) log(x, y, z, "y");
        else m.set(x, y, z, s.lining);
    }

    /** Louvred ventilator astride the ridge, with a pyramid cap and a vane rod. */
    private void cupola(int peak) {
        int xc = posts[posts.length / 2], zr = Math.floorDiv(z0 + z1, 2), t = peak - 1;
        if (xc <= 1 || xc >= l - 2) xc = l / 2;
        for (int x = xc - 1; x <= xc + 1; x++)
            for (int z = zr - 1; z <= zr + 1; z++) {
                boolean edge = x != xc || z != zr, corner = x != xc && z != zr;
                for (int y = t - 1; y <= t + 1; y++) m.set(x, y, z, s.lining);
                if (corner) log(x, t + 2, z, "y");
                else if (edge) m.set(x, t + 2, z, s.timberFence);
                else m.clear(x, t + 2, z);
                if (edge) {
                    String f = x < xc && z == zr ? "east" : x > xc && z == zr ? "west" : z < zr ? "south" : "north";
                    m.stairs(x, t + 3, z, s.timberStairs, f, false);
                } else m.set(x, t + 3, z, s.timber);
            }
        m.set(xc, t + 4, zr, "minecraft:lightning_rod", "facing", "up");
    }

    /** Barn doors at both gables, their leaves swung back flat against the wall. */
    private void doors() {
        int top = Math.min(3, h - 2);
        for (int x : new int[]{0, l - 1}) {
            int out = x == 0 ? -1 : l;
            String f = x == 0 ? "west" : "east";
            for (int z = zc - 1; z <= zc + 1; z++) {
                for (int y = 0; y <= top; y++) m.clear(x, y, z);
                m.set(x, -1, z, s.plinth.block(seed, x, -1, z, 0));
            }
            for (int y = 0; y <= top + 1; y++) {
                log(x, y, zc - 2, "y");
                log(x, y, zc + 2, "y");
            }
            for (int z = zc - 2; z <= zc + 2; z++) log(x, top + 1, z, "z");
            for (int z : new int[]{zc - 3, zc - 2, zc + 2, zc + 3}) {
                if (z < z0 || z > z1) continue;
                for (int y = 0; y <= top; y++) m.set(out, y, z, s.timberTrapdoor, "facing", f, "open", "true", "half", "bottom");
            }
            // Pent hood over the doorway.
            for (int z = zc - 2; z <= zc + 2; z++) m.stairs(out, top + 1, z, s.timberStairs, x == 0 ? "east" : "west", false);
        }
        // Each loose box opens to the yard by its own stable door.
        for (int z : new int[]{z0, z1}) {
            if (!boxWall(z)) continue;
            int outZ = z == z0 ? z0 - 1 : z1 + 1;
            String face = z == z0 ? "north" : "south";
            for (int b = 0; b + 2 < posts.length; b++) {
                int x = (posts[b] + posts[b + 1]) / 2;
                m.set(x, 0, z, s.door, "facing", face, "half", "lower", "hinge", "left", "open", "false");
                m.set(x, 1, z, s.door, "facing", face, "half", "upper", "hinge", "left", "open", "false");
                for (int dx = -1; dx <= 1; dx++) log(x + dx, 2, z, "x");
            }
            // A pentice along the yard side shelters the stable doors, on brackets at each post.
            int far = z == z0 ? z0 - 2 : z1 + 2;
            String in = z == z0 ? "south" : "north";
            for (int x = 0; x < l; x++) {
                m.stairs(x, 4, outZ, s.timberStairs, in, false);
                m.stairs(x, 3, far, s.timberStairs, in, false);
            }
            for (int x : posts) {
                m.stairs(x, 3, outZ, s.timberStairs, in, true);
                for (int y = 0; y <= 2; y++) m.set(x, y, far, s.timberFence);
            }
        }
    }

    private int ladderX() {
        return l - 2;
    }

    private int ladderZ() {
        return front(1) + 1;
    }

    /** Ladder to every loft in the tack room's corner, and the hoist door in the west gable. */
    private void loft() {
        int lx = ladderX(), lz = ladderZ(), topL = level(n - 1);
        for (int y = 0; y < topL; y++) m.set(lx, y, lz, "minecraft:ladder", "facing", "west");
        int L = level(1);
        if (L + 2 < h) {
            for (int z = zc - 1; z <= zc + 1; z++)
                for (int y = L; y <= L + 1; y++) m.clear(0, y, z);
            for (int z = zc - 1; z <= zc + 1; z++) m.set(0, L - 1, z, s.timber);
            for (int z : new int[]{zc - 1, zc + 1})
                for (int y = L; y <= L + 1; y++) m.set(-1, y, z, s.timberTrapdoor, "facing", "west", "open", "true", "half", "bottom");
            m.set(0, L, zc, s.timberFence);
            log(-1, L + 3, zc, "x");
            log(-2, L + 3, zc, "x");
            m.stairs(-1, L + 2, zc, s.timberStairs, "east", true);
            lantern(-2, L + 2, zc, true);
        }
    }

    // ---- Interior -----------------------------------------------------------------------------

    private void furnish() {
        for (int side : sides()) {
            int fr = front(side), wallRow = side > 0 ? z1 - 1 : z0 + 1;
            for (int b = 0; b + 1 < posts.length; b++) {
                int p0 = posts[b], p1 = posts[b + 1];
                boolean last = b + 2 == posts.length;
                if (last && side > 0) tackRoom(p0, p1, fr, wallRow, side);
                else if (last) feedStore(p0, p1, fr, wallRow, side);
                else box(p0, p1, fr, wallRow, side, b == 0);
            }
        }
        for (int i = 1; i < n; i++) {
            int L = level(i);
            if (i == 1) hay(L);
            else {
                beds(L, 1, 1, ladderX() - 2, false);
                beds(L, -1, 1, l - 2, false);
            }
        }
    }

    private void partition(int x, int fr, int wallRow, int side, boolean full) {
        if (x <= 0 || x >= l - 1) return;
        for (int z = fr + side; side > 0 ? z <= wallRow : z >= wallRow; z += side) {
            m.set(x, 0, z, s.timber);
            m.set(x, 1, z, s.timber);
            m.set(x, 2, z, full ? s.timber : "minecraft:iron_bars");
        }
        for (int y = 0; y <= Math.min(3, h - 2); y++) log(x, y, fr, "y");
    }

    private void box(int p0, int p1, int fr, int wallRow, int side, boolean first) {
        partition(p0, fr, wallRow, side, false);
        partition(p1, fr, wallRow, side, false);
        int mid = (p0 + p1) / 2;
        for (int x = p0 + 1; x < p1; x++) {
            // Boarded kick-wall with bars above; the gate is the box's way in.
            if (x == mid) m.set(x, 0, fr, s.timber.replace("_planks", "_fence_gate"), "facing", side > 0 ? "south" : "north", "open", "false", "in_wall", "false");
            else {
                m.set(x, 0, fr, s.timber);
                m.set(x, 1, fr, s.timberFence);
            }
        }
        m.set(p0 + 1, 0, wallRow, "minecraft:hay_block", "axis", "y");
        m.set(p1 - 1, 0, wallRow, "minecraft:water_cauldron", "level", "3");
        if (first) m.set(p0 + 1, 1, wallRow, "minecraft:hay_block", "axis", "x");
    }

    /** Saddle racks on the partition, a chest and barrels; the ladder rises in the corner. */
    private void tackRoom(int p0, int p1, int fr, int wallRow, int side) {
        partition(p0, fr, wallRow, side, true);
        int mid = (p0 + p1) / 2;
        for (int x = p0 + 1; x < p1; x++)
            for (int y = 0; y <= 2; y++) m.set(x, y, fr, s.timber);
        m.set(mid, 0, fr, s.door, "facing", side > 0 ? "south" : "north", "half", "lower", "hinge", "left", "open", "true");
        m.set(mid, 1, fr, s.door, "facing", side > 0 ? "south" : "north", "half", "upper", "hinge", "left", "open", "true");
        for (int x = p0 + 1; x < p1; x++)
            for (int z = fr + side; side > 0 ? z <= wallRow : z >= wallRow; z += side) m.set(x, -1, z, s.timber);
        for (int z = fr + 2 * side; side > 0 ? z <= wallRow : z >= wallRow; z += side)
            m.stairs(p0 + 1, 1, z, s.timberStairs, "west", true);
        m.set(p0 + 1, 0, wallRow, "minecraft:chest", "facing", "east");
        if (p1 - 2 > p0 + 1) m.set(p1 - 2, 0, wallRow, "minecraft:barrel", "facing", "up");
        if (n == 1) m.set(p1 - 1, 0, wallRow, "minecraft:barrel", "facing", "up");
    }

    private void feedStore(int p0, int p1, int fr, int wallRow, int side) {
        partition(p0, fr, wallRow, side, true);
        int mid = (p0 + p1) / 2;
        for (int x = p0 + 1; x < p1; x++) if (x != mid) m.set(x, 0, fr, s.timberFence);
        for (int x = p0 + 1; x < p1; x++)
            for (int z = wallRow; z != fr + side; z -= side) {
                if (z == fr + side && x == mid) continue;
                int stack = 1 + (int) (Hash.hash(seed, "feed", x, 0, z) * 2.2);
                if (Math.abs(z - wallRow) == 1) stack = Math.min(stack, 1);
                for (int y = 0; y < stack; y++) m.set(x, y, z, "minecraft:hay_block", "axis", y % 2 == 0 ? "x" : "z");
            }
        m.set(p1 - 1, 0, fr + side, "minecraft:composter", "level", "4");
    }

    /** Hay stacked along both walls of the loft, stepping down to a clear walk in the middle. */
    private void hay(int L) {
        int top = n == 2 ? h + 1 : L + 3;
        for (int x = 1; x < l - 1; x++)
            for (int z = z0 + 1; z < z1; z++) {
                int fromWall = Math.min(z - z0, z1 - z);
                if (fromWall > 2 || x >= ladderX() - 1 && Math.abs(z - ladderZ()) <= 2) continue;
                if (x <= 1 && Math.abs(z - zc) <= 2) continue;
                double r = Hash.hash(seed, "loft", x, L, z);
                int stack = (fromWall == 1 ? 2 : 1) + (r < 0.35 ? 1 : 0) - (r > 0.85 ? 1 : 0);
                stack = Math.min(stack, top - L);
                for (int y = 0; y < stack; y++) m.set(x, L + y, z, "minecraft:hay_block", "axis", (x + y) % 2 == 0 ? "x" : "z");
            }
    }

    private void lights() {
        for (int i = 0; i < n; i++) {
            int y = i < n - 1 ? level(i) + 3 : h - 1;
            for (int b = 0; b + 1 < posts.length; b++) {
                int x = i < n - 1 ? (posts[b] + posts[b + 1]) / 2 : posts[b];
                if (x <= 0 || x >= l - 1) continue;
                if (i == 1 && b % 2 == 1) continue;
                lantern(x, y, zc, true);
            }
        }
    }

    // ---- Materials ----------------------------------------------------------------------------

    @Override
    void roofStairs(int x, int y, int z, String facing) {
        m.stairs(x, y, z, s.timberStairs, facing, false);
    }

    @Override
    void roofBlock(int x, int y, int z) {
        m.set(x, y, z, s.timber);
    }

    @Override
    void ridge(int x, int y, int z) {
        m.slab(x, y, z, s.timberSlab, false);
    }

    @Override
    void verge(int x, int y, int z, String facing) {
        m.stairs(x, y, z, s.timberStairs, facing, false);
    }

    @Override
    void plate(int x, int y, int z) {
        log(x, y, z, "x");
    }
}
