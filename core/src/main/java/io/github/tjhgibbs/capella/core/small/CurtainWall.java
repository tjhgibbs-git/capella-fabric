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
 * A stretch of town or castle wall, block by block: a battlemented curtain with a wall-walk,
 * flanking towers, and an optional gatehouse in the middle.
 *
 * <ul>
 *   <li>The curtain: solid masonry four or five thick, a crenellated parapet on the field side, a
 *       low wall on the town side, and the walk between them.</li>
 *   <li>Towers, square or round, projecting into the field. Each has a door from the town at the
 *       foot, a ladder through timber floors, doorways where the walk passes through it, arrow
 *       loops on the faces that look out, and a corbelled parapet or a pointed roof on top.</li>
 *   <li>A gatehouse: two taller towers flanking an arched passage with a raised portcullis, the
 *       parapet carried out over the gate on corbels.</li>
 *   <li>Stairs up from the town to the walk wherever a stretch of curtain is long enough.</li>
 * </ul>
 *
 * <p>Frame: x from the towers' field faces (x = 0) inward to the town, z along the wall, centred on
 * the gate; the floor at y = -1. The volume's origin is the middle of the gate's threshold (or of
 * the wall's foot), so the player stands in the field facing the wall.
 */
public final class CurtainWall {
    public record Spec(int length, int height, int towerWidth, int towers, boolean round, boolean gatehouse,
                       TowerCap cap, Stone stone, long seed) {
    }

    /** {min, max} of length, tower width, walk height and tower count. */
    public static final int[][] RANGE = {{16, 200}, {7, 15}, {6, 24}, {0, 9}};

    private final Spec spec;
    private final Style st;
    private final Mason m;
    private final double seed;
    /** Walk level (people stand at y = H), tower width (odd) and half, wall thickness, tower shell. */
    private final int H, S, h, T, tt;
    private final int z0, z1, xb0, xb1;
    /** Gate passage half-width; 0 when there is no gatehouse. */
    private final int p;
    private final boolean towered;
    private final List<Tower> towers = new ArrayList<>();

    private record Tower(int cz, int top) {
    }

    private CurtainWall(Spec spec) {
        this.spec = spec;
        this.st = Style.of(spec.stone());
        this.seed = spec.seed();
        this.m = new Mason(spec.seed());
        int len = clamp(spec.length(), RANGE[0]);
        int side = clamp(spec.towerWidth(), RANGE[1]);
        this.S = side % 2 == 0 ? side + 1 : side;
        this.h = (S - 1) / 2;
        this.H = clamp(spec.height(), RANGE[2]);
        this.T = H >= 14 ? 5 : 4;
        this.tt = S >= 9 ? 2 : 1;
        this.z0 = -(len / 2);
        this.z1 = z0 + len - 1;
        this.p = !spec.gatehouse() ? 0 : S >= 11 ? 2 : 1;
        int count = clamp(spec.towers(), RANGE[3]);
        this.towered = count > 0 || spec.gatehouse();
        this.xb0 = towered ? S - T : 0;
        this.xb1 = xb0 + T - 1;
        placeTowers(count);
    }

    private static int clamp(int v, int[] r) {
        return Math.max(r[0], Math.min(r[1], v));
    }

    /** Built, dressed, normalised and turned; the volume's (0, 0, 0) is the middle of the gate's threshold. */
    public static Volume build(Spec spec, int turns) {
        CurtainWall w = new CurtainWall(spec);
        w.build();
        Volume v = w.m.toVolume(turns);
        List<Detailer.Course> courses = List.of(new Detailer.Course(Math.max(3, w.H / 2), false));
        // A defensive wall keeps its faces plain: no blind arcading, and no shafts or quoins, which flute a round tower.
        Detailer.Options options = new Detailer.Options(true, true, true, true, false, false, false, true);
        v = Detailer.detail(v, turns, Kits.small(spec.stone()), spec.seed(), 0, courses, options);
        Normalize.normalizeVolume(v);
        return v;
    }

    // ---- layout -------------------------------------------------------------------------------

    private int towerTop() {
        return H + Math.max(5, h + 2);
    }

    /**
     * Gate towers either side of the passage; the others spread evenly from end to end (one tower
     * stands in the middle), leaving out any that would crowd the gatehouse.
     */
    private void placeTowers(int count) {
        List<Integer> gate = new ArrayList<>();
        if (spec.gatehouse()) {
            gate.add(-(p + 1 + h));
            gate.add(p + 1 + h);
            for (int cz : gate) towers.add(new Tower(cz, towerTop() + 3));
        }
        int lo = z0 + h, hi = z1 - h;
        for (int i = 0; i < count && hi >= lo; i++) {
            int cz = count == 1 ? 0 : lo + (int) Math.round((hi - lo) * i / (double) (count - 1));
            boolean crowded = false;
            for (Tower t : towers) crowded |= Math.abs(t.cz - cz) < S + 3;
            if (spec.gatehouse() && Math.abs(cz) <= p + S + 2) crowded = true;
            if (!crowded) towers.add(new Tower(cz, towerTop()));
        }
        towers.sort((a, b) -> Integer.compare(a.cz, b.cz));
    }

    /** Inside tower {@code t}'s footprint, grown outward by {@code grow} (negative shrinks it). */
    private boolean inTower(Tower t, int x, int z, int grow) {
        int dz = z - t.cz;
        if (spec.round()) {
            double r = h + 0.5 + grow;
            double dx = x - h;
            return dx * dx + dz * dz <= r * r;
        }
        return x >= -grow && x <= S - 1 + grow && Math.abs(dz) <= h + grow;
    }

    private boolean inAnyTower(int x, int z) {
        for (Tower t : towers) if (inTower(t, x, z, 0)) return true;
        return false;
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

    /** A merlon (or the gap beside it) every third block along a parapet, merlons two wide. */
    private void merlon(int x, int y, int z, int along) {
        if (Math.floorMod(along, 3) == 2) return;
        wall(x, y, z);
        m.slab(x, y + 1, z, st.trim.slab(seed, x, y + 1, z, 0), false);
    }

    // ---- build --------------------------------------------------------------------------------

    private void build() {
        curtain();
        if (spec.gatehouse()) gate();
        for (Tower t : towers) tower(t);
        stairs();
    }

    /** The curtain between and behind the towers: masonry, the walk, both parapets. */
    private void curtain() {
        for (int z = z0; z <= z1; z++) {
            for (int x = xb0; x <= xb1; x++) {
                if (inAnyTower(x, z)) continue;
                m.set(x, -1, z, st.plinth.block(seed, x, -1, z, 0));
                for (int y = 0; y < H; y++) wall(x, y, z);
            }
            if (inAnyTower(xb0, z)) continue;
            // The field-side parapet: a course of trim, then merlons.
            trim(xb0, H, z);
            merlon(xb0, H + 1, z, z);
            if (!inAnyTower(xb1, z)) trimWall(xb1, H, z);
        }
        // Clean ends where the curtain stops without a tower: the parapet turns the corner.
        for (int z : new int[]{z0, z1}) {
            if (inAnyTower(xb0, z) || inAnyTower(xb1, z)) continue;
            for (int x = xb0 + 1; x < xb1; x++) trimWall(x, H, z);
        }
    }

    /**
     * The gate: the curtain brought forward between the gate towers, an arched passage through
     * it, a raised portcullis behind the arch, and the parapet out on corbels over the gate.
     */
    private void gate() {
        int front = 1;
        for (int z = -p; z <= p; z++) {
            for (int x = front; x < xb0; x++) {
                m.set(x, -1, z, st.plinth.block(seed, x, -1, z, 0));
                for (int y = 0; y < H; y++) wall(x, y, z);
            }
            // Clear the curtain's own parapet where the gate now carries it.
            m.clear(xb0, H, z);
            m.clear(xb0, H + 1, z);
            m.clear(xb0, H + 2, z);
            // The machicolation: corbels out at x = 0, the parapet on them.
            trimStairs(front - 1, H - 1, z, "east", true);
            trim(front - 1, H, z);
            merlon(front - 1, H + 1, z, z + p);
        }
        // As tall as the wall allows, up to a mounted rider and a bit: half the wall, four to eight.
        int ph = Math.max(3, Math.min(H - 3, Math.max(4, Math.min(8, H / 2))));
        for (int z = -p; z <= p; z++) {
            int top = Math.abs(z) == p ? ph - 2 : ph - 1;
            for (int x = front; x <= xb1; x++) {
                for (int y = 0; y <= top; y++) m.clear(x, y, z);
                if (Math.abs(z) == p) trimStairs(x, ph - 1, z, z > 0 ? "south" : "north", true);
                m.set(x, -1, z, st.floorField.block(seed, x, -1, z, 0));
            }
            // The portcullis, wound up into its slot: only its foot shows under the arch.
            int px = front + 1;
            for (int y = Math.max(0, top - 1); y <= top; y++) m.set(px, y, z, "minecraft:iron_bars");
        }
        // The walk over the gate is as wide as the gate block; the town-side wall stays.
        for (int z = -p; z <= p; z++) trimWall(xb1, H, z);
    }

    private void tower(Tower t) {
        int top = t.top;
        int mid = H >= 10 ? (H - 1) / 2 : -1;
        int ladderX = S - 1 - tt, ladderZ = t.cz + 1;
        for (int x = -1; x <= S; x++) {
            for (int z = t.cz - h - 1; z <= t.cz + h + 1; z++) {
                if (!inTower(t, x, z, 0)) continue;
                boolean shell = !inTower(t, x, z, -tt);
                m.set(x, -1, z, shell ? st.plinth.block(seed, x, -1, z, 0) : st.floorField.block(seed, x, -1, z, 0));
                for (int y = 0; y < top; y++) {
                    if (shell) wall(x, y, z);
                    else m.clear(x, y, z);
                }
                if (!shell) {
                    if (mid > 0) m.set(x, mid, z, st.timber);
                    m.set(x, H - 1, z, st.timber);
                    m.set(x, top - 1, z, st.roof.block(seed, x, top - 1, z, 0));
                }
            }
        }
        // A ladder up the town-side wall, through every floor and onto the roof.
        for (int y = 0; y < top; y++) m.set(ladderX, y, ladderZ, "minecraft:ladder", "facing", "west");
        // The door from the town, in the back wall beside the ladder.
        for (int d = 0; d < tt; d++) {
            int x = S - 1 - d;
            m.clear(x, 0, t.cz);
            m.clear(x, 1, t.cz);
        }
        int dx = S - tt;
        m.set(dx, 0, t.cz, st.door, "facing", "west", "half", "lower", "hinge", "left");
        m.set(dx, 1, t.cz, st.door, "facing", "west", "half", "upper", "hinge", "left");
        // Doorways where the walk runs through: tunnel in from each side until the room.
        for (int x = xb0 + 1; x < xb1; x++) {
            if (!inTower(t, x, t.cz, -tt)) continue; // this file of the walk would meet the back wall, not the room
            for (int s : new int[]{-1, 1}) {
                for (int k = h; k >= 0; k--) {
                    int z = t.cz + s * k;
                    if (!inTower(t, x, z, 0)) continue;
                    if (inTower(t, x, z, -tt)) break;
                    m.clear(x, H, z);
                    m.clear(x, H + 1, z);
                }
            }
        }
        loops(t, top, mid);
        if (spec.cap() == TowerCap.SPIRE) {
            corbels(t, top, false);
            roof(t, top);
        } else {
            corbels(t, top, true);
        }
    }

    /** Arrow loops, one per storey, on the faces that look out over the field. */
    private void loops(Tower t, int top, int mid) {
        List<int[]> storeys = new ArrayList<>();
        int lo = 0;
        for (int line : new int[]{mid, H - 1, top - 1}) {
            if (line <= lo) continue;
            storeys.add(new int[]{lo, line});
            lo = line + 1;
        }
        for (int[] s : storeys) {
            int room = s[1] - s[0];
            if (room < 4) continue;
            int y0 = s[0] + Math.max(1, (room - 3) / 2), y1 = Math.min(s[1] - 1, y0 + 2);
            // Front, then the two flanks where they stand clear of the curtain.
            for (int x = 0; x < S && !inTower(t, x, t.cz, -tt); x++) for (int y = y0; y <= y1; y++) m.clear(x, y, t.cz);
            int fx = Math.min(h, xb0 - 1);
            if (fx < 1) continue;
            for (int s2 : new int[]{-1, 1}) {
                for (int k = h; k >= 0; k--) {
                    int z = t.cz + s2 * k;
                    if (!inTower(t, fx, z, 0)) continue;
                    if (inTower(t, fx, z, -tt)) break;
                    for (int y = y0; y <= y1; y++) m.clear(fx, y, z);
                }
            }
        }
    }

    /**
     * A ring of corbels one block out from the tower's top, carrying a battlemented parapet
     * ({@code parapet}) or the eaves of the roof.
     */
    private void corbels(Tower t, int top, boolean parapet) {
        for (int x = -2; x <= S + 1; x++) {
            for (int z = t.cz - h - 2; z <= t.cz + h + 2; z++) {
                if (!inTower(t, x, z, 1) || inTower(t, x, z, 0)) continue;
                String facing = inTower(t, x + 1, z, 0) ? "east" : inTower(t, x - 1, z, 0) ? "west"
                        : inTower(t, x, z + 1, 0) ? "south" : inTower(t, x, z - 1, 0) ? "north" : null;
                // A corner of the ring touches the tower only diagonally: no corbel, but the parapet runs on.
                if (facing != null) trimStairs(x, top - 2, z, facing, true);
                trim(x, top - 1, z);
                if (parapet) {
                    wall(x, top, z);
                    merlon(x, top + 1, z, spec.round() ? (int) Math.round(Math.atan2(z - t.cz, x - h) * (h + 1.5)) : x + z);
                }
            }
        }
    }

    /** A cone on a round tower, a pyramid on a square one, its eaves over the corbels; a finial on top. */
    private void roof(Tower t, int top) {
        int r0 = h + 1;
        int height = spec.round() ? r0 * 2 + 3 : r0 + r0 / 2 + 2;
        for (int k = 0; k < height; k++) {
            int y = top + k;
            for (int dx = -r0 - 1; dx <= r0 + 1; dx++) {
                for (int dz = -r0 - 1; dz <= r0 + 1; dz++) {
                    if (!inRoof(dx, dz, k, r0, height)) continue;
                    boolean shell = !inRoof(dx + 1, dz, k, r0, height) || !inRoof(dx - 1, dz, k, r0, height)
                            || !inRoof(dx, dz + 1, k, r0, height) || !inRoof(dx, dz - 1, k, r0, height);
                    int x = h + dx, z = t.cz + dz;
                    if (!shell) {
                        if (k == 0) m.set(x, y, z, st.roof.block(seed, x, y, z, 0));
                        continue;
                    }
                    if (!inRoof(dx, dz, k + 1, r0, height) && !(dx == 0 && dz == 0)) {
                        String facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                        m.stairs(x, y, z, st.roof.stairs(seed, x, y, z, 0), facing, false);
                    } else {
                        m.set(x, y, z, st.roof.block(seed, x, y, z, 0));
                    }
                }
            }
        }
        int fy = top + height;
        trimWall(h, fy, t.cz);
        m.set(h, fy + 1, t.cz, "minecraft:lightning_rod");
    }

    private boolean inRoof(int dx, int dz, int k, int r0, int height) {
        if (k >= height) return false;
        double r = (r0 + 0.49) * (1 - k / (double) height);
        if (spec.round()) return dx * dx + dz * dz <= r * r;
        return Math.abs(dx) <= r && Math.abs(dz) <= r;
    }

    /**
     * Stairs from the town up to the walk: one flight against the town face of each stretch of
     * curtain long enough to take it, the ones beside the gate first, rising away from the gate.
     */
    private void stairs() {
        List<int[]> runs = new ArrayList<>();
        int from = z0;
        for (Tower t : towers) {
            runs.add(new int[]{from, t.cz - h - 2});
            from = t.cz + h + 2;
        }
        runs.add(new int[]{from, z1});
        int need = H + 1;
        int[] west = null, east = null;
        for (int[] r : runs) {
            if (r[1] - r[0] + 1 < need) continue;
            if (!spec.gatehouse()) {
                // No gate: one flight, at the west end, rising towards the middle.
                flight(r[0], 1);
                return;
            }
            // Beside the gate: the nearest stretch on each side, rising away from it.
            if (r[1] < 0 && (west == null || r[1] > west[1])) west = r;
            if (r[0] > 0 && (east == null || r[0] < east[0])) east = r;
        }
        if (west != null) flight(west[1], -1);
        if (east != null) flight(east[0], 1);
    }

    private void flight(int zStart, int dir) {
        int ax = xb1 + 1;
        for (int k = 0; k <= H - 1; k++) {
            int z = zStart + dir * k;
            for (int x = ax; x <= ax + 1; x++) {
                m.set(x, -1, z, st.plinth.block(seed, x, -1, z, 0));
                for (int y = 0; y < k; y++) wall(x, y, z);
                if (k < H - 1) trimStairs(x, k, z, dir > 0 ? "south" : "north", false);
                else wall(x, k, z); // the landing, level with the walk
            }
        }
        // A gap in the town-side wall where the flight arrives.
        int zl = zStart + dir * (H - 1);
        m.clear(xb1, H, zl);
        m.clear(xb1, H, zl - dir);
    }
}
