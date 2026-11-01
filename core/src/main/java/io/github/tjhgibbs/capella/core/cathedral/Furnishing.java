package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.blockstate.BlockState;
import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import io.github.tjhgibbs.capella.core.detail.Detailer;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.raster.RasterPass;
import io.github.tjhgibbs.capella.core.raster.Volume;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a cathedral is furnished with, written block by block over the finished, dressed volume in
 * the plan's own frame (+x east along the nave, +z south) and turned with it: a patterned floor
 * and a runner up the nave, pews, chandeliers hung from the vault, a pulpit and a font, a screen
 * with a loft across the entrance to the choir, stepped stalls, and the altar on its dais; spiral
 * stairs up to the galleries over the aisles; carved bosses where the vault ribs cross.
 *
 * <p>Only air is furnished, so nothing here cuts the fabric.
 */
public final class Furnishing {
    private static final String TIMBER = "minecraft:spruce_planks";
    private static final String TIMBER_STAIRS = "minecraft:spruce_stairs";
    private static final String TIMBER_SLAB = "minecraft:spruce_slab";
    private static final String TIMBER_FENCE = "minecraft:spruce_fence";
    private static final String CHAIN = "minecraft:iron_chain";
    private static final String LANTERN = "minecraft:lantern";

    private final Volume v;
    private final PlanLayout layout;
    private final Detailer.Kit kit;
    private final int turns;
    private final Map<String, BlockState> cache = new HashMap<>();
    private final int floorY, choirY, axisZ, naveZ0, naveZ1;

    private Furnishing(Volume v, PlanLayout layout, Detailer.Kit kit) {
        this.v = v;
        this.layout = layout;
        this.kit = kit;
        this.turns = layout.frame.turns();
        Levels L = layout.levels;
        this.floorY = (int) L.floorY;
        this.choirY = (int) L.choirFloorY;
        this.axisZ = (int) Math.floor(layout.axisZ);
        this.naveZ0 = (int) layout.naveZ0;
        this.naveZ1 = (int) layout.naveZ1;
    }

    /** Which parts to put in: each is an Interior setting. */
    public record Options(boolean furnishings, boolean galleryStairs, boolean vaultBosses) {
        public static final Options ALL = new Options(true, true, true);
    }

    public static void furnish(Volume v, PlanLayout layout, Detailer.Kit kit, Options o) {
        Furnishing f = new Furnishing(v, layout, kit);
        // Bosses first, so a chandelier's chain hangs from one.
        if (o.vaultBosses()) f.bosses();
        if (o.galleryStairs()) f.galleryStairs();
        if (!o.furnishings()) return;
        f.floor();
        f.pulpit();
        f.font();
        f.pews();
        f.choir();
        f.altar();
        f.lights();
    }

    // ---- blocks, in the plan's frame ---------------------------------------------------------

    private int[] at(int x, int y, int z) {
        Vec3 p = layout.frame.point(new Vec3(x, y, z));
        return new int[]{(int) Math.round(p.x()), y, (int) Math.round(p.z())};
    }

    private boolean air(int x, int y, int z) {
        int[] p = at(x, y, z);
        return v.contains(p[0], p[1], p[2]) && v.isAirAt(p[0], p[1], p[2]);
    }

    private boolean solid(int x, int y, int z) {
        int[] p = at(x, y, z);
        return v.contains(p[0], p[1], p[2]) && !v.isAirAt(p[0], p[1], p[2]);
    }

    /** {@code props} alternate name, value; a facing is in the plan's frame. */
    private void put(int x, int y, int z, String name, String... props) {
        int[] p = at(x, y, z);
        if (!v.contains(p[0], p[1], p[2])) return;
        String key = name + String.join(",", props);
        BlockState s = cache.computeIfAbsent(key, k -> {
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i + 1 < props.length; i += 2) m.put(props[i], props[i + 1]);
            return Blockstates.rotateState(Blockstates.makeState(name, m), turns, false);
        });
        v.set(p[0], p[1], p[2], s, RasterPass.DECORATION);
    }

    private void putIfAir(int x, int y, int z, String name, String... props) {
        if (air(x, y, z)) put(x, y, z, name, props);
    }

    private void stairs(int x, int y, int z, String name, String facing, boolean top) {
        putIfAir(x, y, z, name, "facing", facing, "half", top ? "top" : "bottom");
    }

    private String stone() {
        return kit.courseBlock();
    }

    private String stoneStairs() {
        return kit.courseStairs();
    }

    private String slabOf(String stairs) {
        return stairs.replace("_stairs", "_slab");
    }

    // ---- the nave -----------------------------------------------------------------------------

    private boolean naveBay(Bay b) {
        return b.kind == Bay.Kind.NAVE && !b.raised;
    }

    /** A runner up the middle of the nave and a border along each arcade, over the worn floor. */
    private void floor() {
        int y = floorY - 1;
        for (Bay b : layout.bays) {
            if (b.raised) continue;
            for (int x = (int) b.x0; x < (int) b.x1; x++) {
                for (int z = naveZ0; z < naveZ1; z++) {
                    if (!solid(x, y, z) || !air(x, y + 1, z)) continue;
                    int off = Math.abs(z - axisZ);
                    if (off <= 1) put(x, y, z, off == 0 && b.kind != Bay.Kind.CROSSING ? kit.quoin() : stone());
                    else if (z == naveZ0 || z == naveZ1 - 1) put(x, y, z, kit.plinthBlock());
                    else if (b.isCrossing && Math.floorMod(x + z, 2) == 0) put(x, y, z, kit.quoin());
                }
            }
        }
    }

    /** Pews in rows either side of the runner, facing east, clear of the piers and the pulpit. */
    private void pews() {
        List<Bay> bays = layout.bays.stream().filter(this::naveBay).toList();
        for (int i = 0; i < bays.size(); i++) {
            Bay b = bays.get(i);
            if (i == 0 && bays.size() > 2) continue;
            for (int x = (int) b.x0 + 2; x <= (int) b.x1 - 2; x += 2) {
                for (int z = naveZ0 + 2; z < naveZ1 - 2; z++) {
                    if (Math.abs(z - axisZ) <= 2) continue;
                    if (!air(x, floorY, z) || !air(x, floorY + 1, z)) continue;
                    boolean end = z == naveZ0 + 2 || z == naveZ1 - 3 || Math.abs(z - axisZ) == 3;
                    if (end) put(x, floorY, z, TIMBER_SLAB, "type", "top");
                    else stairs(x, floorY, z, TIMBER_STAIRS, "west", false);
                }
            }
        }
    }

    /** A stone pulpit on the north side against a pier half way down the nave, up three steps. */
    private void pulpit() {
        List<Bay> bays = layout.bays.stream().filter(this::naveBay).toList();
        if (bays.size() < 2) return;
        int x = (int) bays.get(bays.size() / 2).x0;
        int z = naveZ0;
        int top = floorY + 3;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                for (int y = floorY; y < top; y++) putIfAir(x + dx, y, z + dz, y == top - 1 ? stone() : kit.shaftWall());
                putIfAir(x + dx, top - 1, z + dz, stone());
            }
        }
        putIfAir(x + 1, top, z + 1, kit.shaftWall());
        putIfAir(x, top, z + 1, kit.shaftWall());
        putIfAir(x + 1, top, z, kit.shaftWall());
        stairs(x + 1, top - 2, z + 2, stoneStairs(), "south", true);
        for (int k = 0; k < 3; k++) {
            int sx = x - 3 + k;
            for (int y = floorY; y < floorY + k; y++) putIfAir(sx, y, z, stone());
            stairs(sx, floorY + k, z, stoneStairs(), "east", false);
        }
    }

    /** The font by the west door, on the south side. */
    private void font() {
        Bay first = layout.bays.stream().filter(this::naveBay).findFirst().orElse(null);
        if (first == null) return;
        int x = (int) first.x0 + 3, z = naveZ1 - 4;
        putIfAir(x, floorY, z, kit.shaftWall());
        if (air(x, floorY + 1, z)) put(x, floorY + 1, z, "minecraft:water_cauldron", "level", "3");
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}}) {
            String facing = d[0] > 0 ? "west" : d[0] < 0 ? "east" : d[1] > 0 ? "north" : "south";
            stairs(x + d[0], floorY, z + d[1], stoneStairs(), facing, false);
        }
    }

    // ---- the choir ----------------------------------------------------------------------------

    private Bay firstChoir() {
        return layout.bays.stream().filter(b -> b.kind == Bay.Kind.CHOIR).findFirst().orElse(null);
    }

    /**
     * The screen across the entrance to the choir, with a loft on it reached by a stair from the
     * choir side, and two stepped rows of stalls along each side behind it under a canopy.
     */
    private void choir() {
        Bay first = firstChoir();
        if (first == null) return;
        int x0 = (int) first.x0;
        int h = 5;
        int door = 1;
        // The screen: three deep, a doorway on the axis, a parapet along both edges of the loft.
        for (int x = x0; x < x0 + 3; x++) {
            for (int z = naveZ0; z < naveZ1; z++) {
                boolean opening = Math.abs(z - axisZ) <= door;
                for (int y = choirY; y < choirY + h; y++) {
                    if (opening && y < choirY + 4) continue;
                    putIfAir(x, y, z, y == choirY + h - 1 ? stone() : kit.plinthBlock());
                }
                if (x != x0 + 1) putIfAir(x, choirY + h, z, kit.shaftWall());
            }
        }
        for (int z = axisZ - door; z <= axisZ + door; z++) stairs(x0, choirY + 3, z, stoneStairs(), "west", true);
        // The loft stair: up the south side of the choir, leaving the screen at its east face.
        int sz = naveZ1 - 1;
        for (int k = 0; k < h; k++) {
            int sx = x0 + 3 + h - 1 - k;
            for (int y = choirY; y < choirY + k; y++) putIfAir(sx, y, sz, stone());
            stairs(sx, choirY + k, sz, stoneStairs(), "west", false);
        }
        // Gap in the loft's east parapet where the stair arrives.
        int[] p = at(x0 + 2, choirY + h, sz);
        v.setId(p[0], p[1], p[2], 0, RasterPass.DECORATION);

        // Stalls: a raised back row against the arcade, a front row before it, a canopy over.
        int from = x0 + 3 + h + 1;
        int to = (int) Math.min(layout.eastX, first.x1 + (layout.bays.stream().filter(b -> b.kind == Bay.Kind.CHOIR).count() > 1 ? first.span : 0)) - 2;
        for (int x = from; x <= to; x++) {
            boolean post = (x - from) % 4 == 0;
            for (int side : new int[]{-1, 1}) {
                int back = side < 0 ? naveZ0 : naveZ1 - 1;
                int front = back - side;
                String face = side < 0 ? "north" : "south";
                putIfAir(x, choirY, back, TIMBER);
                stairs(x, choirY + 1, back, TIMBER_STAIRS, face, false);
                stairs(x, choirY, front, TIMBER_STAIRS, face, false);
                for (int y = choirY + 2; y < choirY + 5; y++) {
                    if (post) putIfAir(x, y, back, TIMBER_FENCE);
                }
                putIfAir(x, choirY + 5, back, TIMBER_SLAB, "type", "bottom");
                stairs(x, choirY + 5, front, TIMBER_STAIRS, face, true);
            }
        }
    }

    /** The high altar on a dais, at the east end of the choir or in the apse. */
    private void altar() {
        int x = layout.chevetDepth > 0 ? (int) layout.eastX + 1 : (int) layout.eastX - 3;
        int half = Math.max(1, Math.min(2, (naveZ1 - naveZ0) / 6));
        // Dais one step up, a step all round.
        for (int dx = -3; dx <= 2; dx++) {
            for (int dz = -half - 2; dz <= half + 2; dz++) {
                boolean edge = dx == -3 || Math.abs(dz) == half + 2;
                if (edge) {
                    String facing = dx == -3 ? "east" : dz < 0 ? "south" : "north";
                    if (dx == -3) stairs(x + dx, choirY, axisZ + dz, stoneStairs(), facing, false);
                    else putIfAir(x + dx, choirY, axisZ + dz, slabOf(stoneStairs()), "type", "bottom");
                } else {
                    putIfAir(x + dx, choirY, axisZ + dz, kit.quoin());
                }
            }
        }
        for (int dz = -half; dz <= half; dz++) putIfAir(x, choirY + 1, axisZ + dz, stone());
        putIfAir(x, choirY + 2, axisZ - half, "minecraft:candle", "candles", "3", "lit", "true");
        putIfAir(x, choirY + 2, axisZ + half, "minecraft:candle", "candles", "3", "lit", "true");
        putIfAir(x, choirY + 2, axisZ, "minecraft:white_candle", "candles", "1", "lit", "true");
        // Candlesticks either side.
        for (int s : new int[]{-1, 1}) {
            int z = axisZ + s * (half + 1);
            putIfAir(x - 1, choirY + 1, z, kit.shaftWall());
            putIfAir(x - 1, choirY + 2, z, "minecraft:candle", "candles", "1", "lit", "true");
        }
    }

    // ---- stairs and bosses --------------------------------------------------------------------

    /**
     * A switchback stair against the outer wall of each outer aisle at the west end, from the
     * floor up through the aisle vault to the gallery deck: two-wide flights either side of a
     * solid spine wall, turning on solid landings, every tread
     * on a stringer. The deck is left open only over the top of the last flight.
     */
    private void galleryStairs() {
        Levels L = layout.levels;
        int deckY = (int) L.triforiumDeckY;
        if (deckY - floorY < 4) return;
        Bay bay = layout.bays.stream().filter(this::naveBay).findFirst().orElse(null);
        if (bay == null) return;
        int run = Math.min(8, (int) bay.span - 3);
        if (run < 4) return;
        int xs = (int) bay.x0 + 1;
        for (String side : List.of("north", "south")) {
            SideBands bands = side.equals("north") ? layout.north : layout.south;
            if (bands.aisles.isEmpty()) continue;
            Band aisle = bands.aisles.get(bands.aisles.size() - 1);
            if (aisle.z1() - aisle.z0() < 7) continue;
            boolean north = side.equals("north");
            switchback(xs, run, north ? (int) aisle.z0() : (int) aisle.z1() - 1, north ? 1 : -1, deckY);
        }
    }

    /** {@code zw} is the cell against the wall, {@code in} the way into the aisle. */
    private void switchback(int xs, int run, int zw, int in, int deckY) {
        int xe = xs + run + 1;
        int[] laneA = {zw, zw + in}, laneB = {zw + 3 * in, zw + 4 * in};
        int spine = zw + 2 * in;
        // Clear the box, keeping what the deck was made of.
        Map<Long, String> deck = new HashMap<>();
        for (int x = xs; x <= xe; x++) {
            for (int k = 0; k <= 5; k++) {
                int z = zw + k * in;
                int[] p = at(x, deckY - 1, z);
                if (v.contains(p[0], p[1], p[2])) deck.put(((long) x << 32) | (z & 0xffffffffL), v.keyAt(p[0], p[1], p[2]));
                for (int y = floorY; y < deckY; y++) {
                    int[] q = at(x, y, z);
                    v.setId(q[0], q[1], q[2], 0, RasterPass.DECORATION);
                }
            }
        }
        for (int x = xs + 1; x < xe; x++) {
            for (int y = floorY; y < deckY; y++) put(x, y, spine, kit.plinthBlock());
        }
        int t = floorY;
        boolean east = true;
        int flight = 0;
        java.util.Set<Long> open = new java.util.HashSet<>();
        while (t < deckY) {
            int[] lane = flight % 2 == 0 ? laneA : laneB;
            for (int i = 0; i < run && t < deckY; i++, t++) {
                int x = east ? xs + 1 + i : xe - 1 - i;
                for (int z : lane) {
                    put(x, t, z, stoneStairs(), "facing", east ? "east" : "west", "half", "bottom");
                    if (t > floorY) put(x, t - 1, z, stone());
                    if (t >= deckY - 4) open.add(((long) x << 32) | (z & 0xffffffffL));
                }
                // No diagonal wall-block rails: they float and obstruct the turn.
            }
            if (t >= deckY) break;
            // The landing at the end just reached, level with the last tread, across both lanes.
            int lx = east ? xe : xs;
            int top = t - 1;
            for (int k = 0; k <= 4; k++) {
                int z = zw + k * in;
                int from = top - 1; // A supported slab, leaving the ground-floor entrance open.
                for (int y = from; y <= top; y++) put(lx, y, z, y == top ? kit.quoin() : stone());
            }

            east = !east;
            flight++;
        }
        // The final tread rises onto the gallery: provide an exit one block beyond it.
        int exitX = east ? xs + 1 + ((deckY - floorY - 1) % run) + 1
                : xe - 1 - ((deckY - floorY - 1) % run) - 1;
        int[] finalLane = flight % 2 == 0 ? laneA : laneB;
        for (int z : finalLane) {
            put(exitX, deckY - 1, z, stone());
            for (int y = deckY; y <= deckY + 2; y++) clear(exitX, y, z);
        }
        // Restore the deck, but keep three blocks of headroom over the last treads.
        for (Map.Entry<Long, String> e : deck.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey().intValue();
            if (open.contains(e.getKey())) {
                for (int y = deckY; y <= deckY + 2; y++) clear(x, y, z);
                continue;
            }
            if (!e.getValue().equals("minecraft:air") && air(x, deckY - 1, z)) {
                int[] p = at(x, deckY - 1, z);
                v.setId(p[0], p[1], p[2], v.internKey(e.getValue()), RasterPass.DECORATION);
            }
        }
    }

    private void clear(int x, int y, int z) {
        int[] p = at(x, y, z);
        v.setId(p[0], p[1], p[2], 0, RasterPass.DECORATION);
    }

    private static String dirOf(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? "east" : "west";
        return dz > 0 ? "south" : "north";
    }

    /**
     * Bosses on the vaults: a carved one where the diagonal ribs cross in the middle of every high
     * bay, flared with stairs, and a plain one on each transverse rib; a small one in the middle of
     * every aisle bay.
     */
    private void bosses() {
        for (Bay b : layout.bays) {
            int floor = b.raised ? choirY : floorY;
            int mid = (int) Math.floor((b.x0 + b.x1) / 2);
            boss(mid, axisZ, floor, true);
            if (b.index > 1) boss((int) b.x0, axisZ, floor, false);
            if (b.isCrossing) continue;
            for (SideBands bands : List.of(layout.north, layout.south)) {
                for (Band aisle : bands.aisles) boss(mid, (int) Math.floor((aisle.z0() + aisle.z1()) / 2), floor, false);
            }
        }
    }

    private void boss(int x, int z, int floor, boolean carved) {
        int ceiling = ceiling(x, floor, z);
        if (ceiling < 0 || ceiling - floor < 8) return;
        int y = ceiling - 1;
        putIfAir(x, y, z, kit.quoin());
        if (!carved) return;
        putIfAir(x, y - 1, z, kit.shaftWall());
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            stairs(x + d[0], y, z + d[1], stoneStairs(), dirOf(-d[0], -d[1]), true);
        }
    }

    // ---- light --------------------------------------------------------------------------------

    /**
     * A chandelier on a long chain from the crown of every high bay, hung at the arcade's crown;
     * a lantern on a short chain in every aisle bay.
     */
    private void lights() {
        Levels L = layout.levels;
        for (Bay b : layout.bays) {
            int x = (int) Math.floor((b.x0 + b.x1) / 2);
            int floor = b.raised ? choirY : floorY;
            if (!b.isCrossing) chandelier(x, axisZ, floor, (int) L.arcadeCrownY + (b.raised ? 3 : 0));
            for (String side : List.of("north", "south")) {
                if (b.isCrossing) continue;
                SideBands bands = side.equals("north") ? layout.north : layout.south;
                for (Band aisle : bands.aisles) {
                    int z = (int) Math.floor((aisle.z0() + aisle.z1()) / 2);
                    int ceiling = ceiling(x, floor, z);
                    if (ceiling < 0) continue;
                    int lamp = Math.max(floor + 4, ceiling - 3);
                    for (int y = lamp + 1; y < ceiling; y++) putIfAir(x, y, z, CHAIN, "axis", "y");
                    if (lamp < ceiling) putIfAir(x, lamp, z, LANTERN, "hanging", "true");
                }
            }
        }
    }

    /** The first solid block over a column, or -1 if it is open to the sky. */
    private int ceiling(int x, int floor, int z) {
        for (int y = floor + 3; y < floor + 400; y++) {
            int[] p = at(x, y, z);
            if (!v.contains(p[0], p[1], p[2])) return -1;
            if (!v.isAirAt(p[0], p[1], p[2])) return y;
        }
        return -1;
    }

    private void chandelier(int x, int z, int floor, int hang) {
        int ceiling = ceiling(x, floor, z);
        if (ceiling < 0) return;
        hang = Math.min(hang, ceiling - 4);
        if (hang < floor + 5) return;
        for (int y = hang + 1; y < ceiling; y++) putIfAir(x, y, z, CHAIN, "axis", "y");
        // A ring: four arms of chain out to a lantern at each end, and one in the middle.
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            String axis = d[0] != 0 ? "x" : "z";
            putIfAir(x + d[0], hang + 1, z + d[1], CHAIN, "axis", axis);
            putIfAir(x + 2 * d[0], hang + 1, z + 2 * d[1], CHAIN, "axis", axis);
            putIfAir(x + 2 * d[0], hang, z + 2 * d[1], LANTERN, "hanging", "true");
        }
        putIfAir(x, hang, z, LANTERN, "hanging", "true");
    }
}
