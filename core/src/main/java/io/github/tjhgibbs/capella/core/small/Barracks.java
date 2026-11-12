package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.params.BuildingParams;

/**
 * A stone garrison block: buttressed walls with string courses at every floor, barred ground-floor
 * lights, crow-stepped gables, a slate roof on open trusses and an external chimney on the east
 * gable. Inside, an armoury by the arched entrance, a mess hall round the fireplace, and bunk
 * dormitories upstairs reached by a switchback stair against the north wall.
 */
final class Barracks extends Range {
    /** Switchback stair bay: rows A and B against the north wall, x = S .. S+6, railed along row C. */
    private static final int S = 1;
    private final int rowA, rowB, rowC;
    private final Profile pitch = d -> d;

    Barracks(BuildingParams clamped) {
        super(clamped);
        rowA = z0 + 1;
        rowB = z0 + 2;
        rowC = z0 + 3;
    }

    @Override
    void build() {
        shell();
        floors(z0 + 1, z1 - 1);
        int peak = roof(h, pitch, false, this::stone);
        peak = Math.max(peak, crowSteps());
        trusses(pitch, h);
        dormers();
        windows();
        entrance();
        fireplace(peak);
        if (n > 1) stair();
        if (furnish) furnish();
        lights();
    }

    // ---- Shell --------------------------------------------------------------------------------

    private void shell() {
        for (int x = -1; x <= l; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean inside = x > 0 && x < l - 1 && z > z0 && z < z1;
                boolean ring = x == -1 || x == l || z == z0 - 1 || z == z1 + 1;
                if (inside) {
                    boolean border = x == 1 || x == l - 2 || z == z0 + 1 || z == z1 - 1;
                    m.set(x, -1, z, (border ? s.floorBorder : s.floorField).block(seed, x, -1, z, 0));
                } else m.set(x, -1, z, s.plinth.block(seed, x, -1, z, 0));
                if (ring && !(x == -1 && Math.abs(z) <= 1) && !(x == l && Math.abs(z) <= 1)) {
                    // Chamfered plinth skirt, sloping away from the wall.
                    String f = x == -1 ? "east" : x == l ? "west" : z < 0 ? "south" : "north";
                    m.stairs(x, 0, z, s.plinth.stairs(seed, x, 0, z, 0), f, false);
                }
            }
        for (int x = 0; x < l; x++)
            for (int z = z0; z <= z1; z++) {
                if (x != 0 && x != l - 1 && z != z0 && z != z1) continue;
                boolean corner = (x == 0 || x == l - 1) && (z == z0 || z == z1);
                for (int y = 0; y < h; y++) {
                    // Long-and-short quoins at the corners; a dressed base course all round.
                    if (y == 0 || corner && y % 2 == 0) trim(x, y, z);
                    else stone(x, y, z);
                }
            }
        // String courses mark each floor; the eaves get a corbelled cornice on the long sides.
        for (int i = 1; i < n; i++) band(level(i) - 1, true);
        for (int x = -1; x <= l; x++) {
            trimStairs(x, h - 1, z0 - 1, "south", true);
            trimStairs(x, h - 1, z1 + 1, "north", true);
        }
        // Buttresses on the bay lines of the long walls, weathered back under the cornice.
        for (int i = 1; i + 1 < posts.length; i++) {
            int x = posts[i];
            if (i % 2 == 1 && posts.length > 3) continue;
            for (int[] side : new int[][]{{z0 - 1, 1}, {z1 + 1, -1}}) {
                for (int y = 0; y <= h - 3; y++) stone(x, y, side[0]);
                trim(x, 0, side[0]);
                trimStairs(x, h - 2, side[0], side[1] > 0 ? "south" : "north", false);
            }
        }
    }

    private void band(int y, boolean gables) {
        for (int x = -1; x <= l; x++) {
            trimStairs(x, y, z0 - 1, "south", true);
            trimStairs(x, y, z1 + 1, "north", true);
        }
        if (gables) for (int z = z0; z <= z1; z++) {
            trimStairs(-1, y, z, "east", true);
            trimStairs(l, y, z, "west", true);
        }
    }

    /** Crow-stepped parapets on both gables, two-block steps capped with slabs. Returns their top. */
    private int crowSteps() {
        int top = 0, dm = dMax();
        for (int x : new int[]{0, l - 1})
            for (int z = z0; z <= z1; z++) {
                int d = depth(z);
                int step = Math.min(dm, 2 * ((d + 1) / 2));
                int gt = h + pitch.rise(step) + 1 + (step == dm ? 1 : 0);
                for (int y = roofLow(pitch, h, z); y <= gt; y++) stone(x, y, z);
                for (int y = gt - 1; y <= gt; y++) if (y >= h) trim(x, y, z);
                m.slab(x, gt + 1, z, s.trim.slab(seed, x, gt + 1, z, 0), false);
                top = Math.max(top, gt + 1);
            }
        return top;
    }

    /**
     * Gabled stone dormers on alternate bays of both slopes, over the top storey's windows: a
     * glazed front rising from the wall face, cheeks down to the main roof, a ridged cap.
     */
    private void dormers() {
        if (dMax() < 4 || posts.length < 4) return;
        for (int b = 1; b + 2 < posts.length; b += 2) {
            int x = (posts[b] + posts[b + 1]) / 2;
            for (int side : new int[]{-1, 1}) {
                int wall = side < 0 ? z0 : z1;
                // k counts inward from the eave overhang (k = 0) to where the cap meets the roof.
                for (int k = 0; k <= 5; k++) {
                    int z = wall + side * (1 - k);
                    for (int dx = -2; dx <= 2; dx++) {
                        int cap = h + 5 - Math.abs(dx);
                        if (Math.abs(dx) == 2) {
                            if (k <= 3) m.stairs(x + dx, cap, z, s.roof.stairs(seed, x + dx, cap, z, 0), dx < 0 ? "east" : "west", false);
                            continue;
                        }
                        if (dx == 0) {
                            m.set(x, cap, z, s.roof.block(seed, x, cap, z, 0));
                            if (k < 5) m.slab(x, cap + 1, z, s.ridge.slab(seed, x, cap + 1, z, 0), false);
                        } else m.stairs(x + dx, cap, z, s.roof.stairs(seed, x + dx, cap, z, 0), dx < 0 ? "east" : "west", false);
                        if (k == 0) continue;
                        for (int y = h; y < cap; y++) {
                            if (k == 1) {
                                // Front face: a two-high light under a dressed head.
                                if (dx == 0 && (y == h + 1 || y == h + 2)) m.set(x, y, z, "minecraft:glass_pane");
                                else if (dx == 0 && y == h + 3 || dx == 0 && y == h) trim(x, y, z);
                                else stone(x + dx, y, z);
                            } else if (dx == 0) m.clear(x, y, z);
                            else stone(x + dx, y, z);
                        }
                    }
                }
            }
        }
    }

    private void windows() {
        for (int i = 0; i < n; i++) {
            int L = level(i);
            int lintel = L + 3;
            if (lintel > h - 1) continue;
            for (int b = 0; b + 1 < posts.length; b++) {
                int x = (posts[b] + posts[b + 1]) / 2;
                for (int z : new int[]{z0, z1}) {
                    for (int y = L + 1; y <= L + 2; y++) {
                        if (i == 0) m.set(x, y, z, "minecraft:iron_bars");
                        else m.set(x, y, z, "minecraft:glass_pane");
                    }
                    trim(x, L, z);
                    trim(x, lintel, z);
                    int out = z == z0 ? z0 - 1 : z1 + 1;
                    String face = z == z0 ? "north" : "south", in = z == z0 ? "south" : "north";
                    // Projecting sill and a pair of shutters folded back against the wall.
                    if (i > 0) trimStairs(x, L, out, in, true);
                    for (int dx : new int[]{-1, 1})
                        if (m.air(x + dx, L + 1, out))
                            for (int y = L + 1; y <= L + 2; y++)
                                m.set(x + dx, y, out, s.timberTrapdoor, "facing", face, "open", "true", "half", "bottom");
                }
            }
            // Gable lights: either side of the door upstairs, clear of the chimney to the east.
            if (i > 0) for (int z : new int[]{-2, 2}) gableLight(0, L, z);
            for (int z : new int[]{-3, 3}) if (i > 0 || z1 - z0 >= 12) gableLight(l - 1, L, z);
        }
        // A small light high in the west gable for the open roof.
        if (dMax() >= 4) for (int y = h + 1; y <= h + 2; y++) m.set(0, y, 0, "minecraft:glass_pane");
    }

    private void gableLight(int x, int L, int z) {
        if (z <= z0 || z >= z1) return;
        for (int y = L + 1; y <= L + 2; y++) m.set(x, y, z, "minecraft:glass_pane");
        trim(x, L + 3, z);
    }

    /**
     * A projecting gabled porch with a round-headed outer arch, sheltering a plank door in a
     * dressed surround; lantern inside, the garrison's banner on the porch gable.
     */
    private void entrance() {
        int top = Math.min(3, h - 2);
        // Door in the gable wall.
        for (int y = 0; y <= 1; y++) m.clear(0, y, 0);
        m.set(0, 0, 0, s.door, "facing", "east", "half", "lower", "hinge", "left", "open", "false");
        m.set(0, 1, 0, s.door, "facing", "east", "half", "upper", "hinge", "left", "open", "false");
        m.set(0, -1, 0, s.floorField.block(seed, 0, -1, 0, 0));
        for (int y = 0; y <= 2; y++) for (int z : new int[]{-1, 1}) trim(0, y, z);
        for (int z = -1; z <= 1; z++) trim(0, 2, z);
        // Porch: side walls, floor, outer arch.
        for (int x = -3; x <= -1; x++)
            for (int z = -2; z <= 2; z++) {
                m.set(x, -1, z, (Math.abs(z) == 2 || x == -3 ? s.trim : s.floorField).block(seed, x, -1, z, 0));
                m.clear(x, 0, z);
                for (int y = 0; y <= top; y++) {
                    if (Math.abs(z) == 2 || x == -3) {
                        if (y == 0 || (x == -3 && Math.abs(z) == 2)) trim(x, y, z);
                        else stone(x, y, z);
                    } else m.clear(x, y, z);
                }
            }
        for (int z = -1; z <= 1; z++) {
            for (int y = 0; y < top; y++) m.clear(-3, y, z);
            m.set(-3, -1, z, s.floorField.block(seed, -3, -1, z, 0));
        }
        if (top >= 3) {
            trimStairs(-3, top - 1, -1, "north", true);
            trimStairs(-3, top - 1, 1, "south", true);
        }
        for (int z = -2; z <= 2; z++) trim(-3, top, z);
        m.set(-2, 1, -2, "minecraft:iron_bars");
        m.set(-2, 1, 2, "minecraft:iron_bars");
        // Gabled roof over the porch, its front gable closed in stone.
        int eave = top + 1;
        for (int x = -4; x <= -1; x++)
            for (int z = -3; z <= 3; z++) {
                int y = eave + 3 - Math.abs(z);
                if (z == 0) {
                    m.set(x, y, z, s.roof.block(seed, x, y, z, 0));
                    m.slab(x, y + 1, z, s.ridge.slab(seed, x, y + 1, z, 0), false);
                } else m.stairs(x, y, z, s.roof.stairs(seed, x, y, z, 0), z < 0 ? "south" : "north", false);
                if (x >= -3) for (int gy = eave; gy < y; gy++) {
                    if (gy == eave && x > -3 && Math.abs(z) <= 1) m.set(x, gy, z, s.timber);
                    else stone(x, gy, z);
                }
            }
        lantern(-2, top, 0, true);
        m.set(-4, eave + 1, 0, "minecraft:red_wall_banner", "facing", "west");
    }

    /** External chimney stack on the east gable with a hearth opening into the ground floor. */
    private void fireplace(int peak) {
        int top = peak + 1;
        for (int z = -2; z <= 2; z++) {
            // The breast is two deep and five wide to the eaves, enclosing the hearth, then
            // weathers back to a one-deep stack three wide.
            for (int y = -1; y < h; y++)
                for (int x = l; x <= l + 1; x++) {
                    if (y == -1 || Math.abs(z) == 2 && y % 2 == 0) trim(x, y, z);
                    else stone(x, y, z);
                }
            trimStairs(l + 1, h, z, "west", false);
            if (Math.abs(z) == 2) {
                trimStairs(l, h, z, z < 0 ? "south" : "north", false);
                continue;
            }
            for (int y = h; y < top; y++) stone(l, y, z);
            trim(l, top, z);
            trimStairs(l + 1, top, z, "west", true);
        }
        m.set(l, top + 1, -1, s.trim.wall(seed, l, top + 1, -1, 0));
        m.set(l, top + 1, 1, s.trim.wall(seed, l, top + 1, 1, 0));
        m.set(l, top + 1, 0, "minecraft:campfire", "lit", "true", "signal_fire", "false", "facing", "west");
        // Hearth.
        for (int z = -1; z <= 1; z++)
            for (int y = 0; y <= 1; y++) {
                m.clear(l - 1, y, z);
                m.clear(l, y, z);
            }
        m.set(l, 0, 0, "minecraft:campfire", "lit", "true", "signal_fire", "false", "facing", "west");
        for (int z = -1; z <= 1; z++) trim(l - 1, 2, z);
        for (int y = 0; y <= 1; y++) {
            trim(l - 2, y, -2);
            trim(l - 2, y, 2);
        }
        for (int z = -2; z <= 2; z++) trimStairs(l - 2, 2, z, "east", true);
    }

    // ---- Stair --------------------------------------------------------------------------------

    /**
     * Even flights climb east in row A, odd flights west in row B, landings at x = S and S+6.
     * The floor above each flight is cut back for headroom and railed off along row C.
     */
    private void stair() {
        for (int i = 0; i + 1 < n; i++) {
            int L = level(i);
            boolean east = i % 2 == 0;
            int row = east ? rowA : rowB;
            for (int k = 0; k <= 4; k++) {
                int x = east ? S + 1 + k : S + 5 - k;
                m.stairs(x, L + k, row, s.timberStairs, east ? "east" : "west", false);
                for (int y = L; y < L + k; y++) m.set(x, y, row, s.timber);
            }
            for (int x = S + 2; x <= S + 4; x++) m.clear(x, L + 4, row);
            // Guard the cut where the next storey's floor is safe to stand over it.
            m.set(east ? S + 2 : S + 4, L + 5, row, s.timberFence);
        }
        for (int i = 1; i < n; i++)
            for (int x = S + 1; x <= S + 5; x++) m.set(x, level(i), rowC, s.timberFence);
    }

    // ---- Interior -----------------------------------------------------------------------------

    private void furnish() {
        armoury();
        int east = l - 4;
        if (n == 1) {
            beds(0, 1, S + 8, east, h >= 6);
            beds(0, -1, S + 8, east, h >= 6);
            return;
        }
        mess(S + 8, east);
        for (int i = 1; i < n; i++) {
            int L = level(i);
            if (n >= 3 && i == n - 1) {
                officers(L);
                continue;
            }
            beds(L, 1, 1, l - 2, true);
            beds(L, -1, S + 8, l - 2, true);
            for (int x = S + 7; x < l - 1; x++) m.set(x, L, 0, s.carpet);
        }
    }

    /**
     * The top floor of a tall block is the officers': single beds on the south side, and opposite
     * them a writing desk, map table, book presses and a strongbox under the company colours.
     */
    private void officers(int L) {
        beds(L, 1, 1, l - 2, false);
        int zw = z0 + 1;
        for (int x = S + 8; x < l - 2; x++) {
            int r = (x - S - 8) % 6;
            switch (r) {
                case 0, 5 -> {
                    m.set(x, L, zw, "minecraft:bookshelf");
                    m.set(x, L + 1, zw, "minecraft:bookshelf");
                }
                case 1 -> m.set(x, L, zw, "minecraft:lectern", "facing", "south");
                case 2 -> {
                    m.set(x, L, zw, "minecraft:cartography_table");
                    m.stairs(x, L, zw + 1, s.timberStairs, "south", false);
                    m.set(x, L + 2, zw, "minecraft:red_wall_banner", "facing", "south");
                }
                case 3 -> m.set(x, L, zw, "minecraft:barrel", "facing", "up");
                case 4 -> m.set(x, L, zw, "minecraft:chest", "facing", "south");
                default -> {
                }
            }
        }
        for (int x = S + 7; x < l - 1; x++) m.set(x, L, 0, s.carpet);
    }

    /** Arms store along the south wall by the door: racks, a forge corner and banners above. */
    private void armoury() {
        int zw = z1 - 1;
        String[][] kit = {
                {"minecraft:barrel", "facing", "up"},
                {"minecraft:smithing_table"},
                {"minecraft:grindstone", "face", "floor", "facing", "east"},
                {"minecraft:anvil", "facing", "east"},
                {"minecraft:chest", "facing", "north"},
                {"minecraft:fletching_table"},
                {"minecraft:barrel", "facing", "up"}};
        for (int i = 0; i < kit.length && 1 + i < l - 4; i++) {
            String[] k = kit[i];
            String[] props = java.util.Arrays.copyOfRange(k, 1, k.length);
            m.set(1 + i, 0, zw, k[0], props);
        }
        for (int x : new int[]{2, 6}) m.set(x, 2, zw, "minecraft:red_wall_banner", "facing", "north");
        // Racks of spears stood against the wall between the banners.
        for (int x = 3; x <= 5; x++) m.set(x, 1, zw, "minecraft:lightning_rod", "facing", "up");
        if (n == 1) {
            int zn = z0 + 1;
            for (int x = 1; x <= 6; x++) m.set(x, 0, zn, x % 3 == 0 ? "minecraft:chest" : "minecraft:barrel",
                    x % 3 == 0 ? new String[]{"facing", "south"} : new String[]{"facing", "up"});
        }
    }

    /** Trestle tables with benches either side, in runs of five with gaps to pass through. */
    private void mess(int xa, int xb) {
        for (int side : new int[]{-1, 1}) {
            int wall = side > 0 ? z1 - 1 : z0 + 1;
            for (int start = 2; ; start += 4) {
                int bench1 = side * start, table = side * (start + 1), bench2 = side * (start + 2);
                if (Math.abs(table) > Math.abs(wall)) break;
                boolean second = Math.abs(bench2) < Math.abs(wall);
                for (int x = xa; x <= xb; x++) {
                    int r = (x - xa) % 6;
                    if (r == 5) continue;
                    m.slab(x, 0, table, s.timberSlab, true);
                    m.stairs(x, 0, bench1, s.timberStairs, side > 0 ? "north" : "south", false);
                    if (second) m.stairs(x, 0, bench2, s.timberStairs, side > 0 ? "south" : "north", false);
                    if (r == 2) m.set(x, 1, table, "minecraft:candle", "candles", "3", "lit", "true");
                }
            }
        }
        // Company colours on the piers between the windows.
        for (int x : posts)
            if (x > xa && x < xb) {
                m.set(x, 2, z0 + 1, "minecraft:red_wall_banner", "facing", "south");
                m.set(x, 2, z1 - 1, "minecraft:red_wall_banner", "facing", "north");
            }
        // Cooking corner beside the hearth.
        m.set(l - 3, 0, z1 - 1, "minecraft:smoker", "facing", "north");
        m.set(l - 2, 0, z1 - 1, "minecraft:barrel", "facing", "up");
        m.set(l - 3, 0, z0 + 1, "minecraft:crafting_table");
        m.set(l - 2, 0, z0 + 1, "minecraft:barrel", "facing", "up");
    }

    private void lights() {
        for (int i = 0; i < n; i++) {
            int L = level(i);
            if (i < n - 1) {
                for (int b = 0; b + 1 < posts.length; b++) {
                    int x = (posts[b] + posts[b + 1]) / 2;
                    lantern(x, L + 3, 0, true);
                }
            } else {
                for (int x : posts) if (x > 0 && x < l - 1) lantern(x, h - 1, 0, true);
            }
        }
    }

    // ---- Materials ----------------------------------------------------------------------------

    @Override
    void roofStairs(int x, int y, int z, String facing) {
        m.stairs(x, y, z, s.roof.stairs(seed, x, y, z, 0), facing, false);
    }

    @Override
    void roofBlock(int x, int y, int z) {
        m.set(x, y, z, s.roof.block(seed, x, y, z, 0));
    }

    @Override
    void ridge(int x, int y, int z) {
        m.slab(x, y, z, s.ridge.slab(seed, x, y, z, 0), false);
    }

    @Override
    void verge(int x, int y, int z, String facing) {
        roofStairs(x, y, z, facing);
    }

    @Override
    void plate(int x, int y, int z) {
        stone(x, y, z);
    }
}
