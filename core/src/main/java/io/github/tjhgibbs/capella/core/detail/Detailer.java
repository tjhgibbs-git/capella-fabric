package io.github.tjhgibbs.capella.core.detail;

import io.github.tjhgibbs.capella.core.blockstate.BlockState;
import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import io.github.tjhgibbs.capella.core.geom.Box;
import io.github.tjhgibbs.capella.core.hash.Hash;
import io.github.tjhgibbs.capella.core.raster.RasterPass;
import io.github.tjhgibbs.capella.core.raster.Volume;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Masonry detail over a finished volume: the surface layer that turns big plain walls into
 * built-up ones. It reads only blocks, so it works on any building in any of the four turns.
 *
 * <p>Builders' rules it follows: three depths on every face (frame +1, wall 0, recess -1); no
 * more than a storey of wall without a band; partial blocks (stairs, walls) at every edge. In
 * order, so earlier work claims its blocks first:
 * <ol>
 *   <li>a two-course plinth outside, a bench course inside, and bases round free piers;</li>
 *   <li>string courses at the given heights (capitals where the course is a springing line);</li>
 *   <li>a cornice on a corbel table under the top of every long wall;</li>
 *   <li>hood moulds over every window and door head;</li>
 *   <li>attached shafts in re-entrant corners (buttress against wall, pier against respond);</li>
 *   <li>blind arcades: pointed panels carved one deep into flat wall, shafted between;</li>
 *   <li>quoins on outside corners, and weatherings on narrow set-offs.</li>
 * </ol>
 * A wall is never carved through: carving one face protects the block behind it.
 */
public final class Detailer {
    /** A band at height {@code y}; a capital course projects from the top half, a sill course from the bottom. */
    public record Course(int y, boolean capital) {
    }

    /**
     * What the detail is made of. {@code body} is the wall stone the detailer may cut and dress;
     * {@code trim} is existing dressed work it keeps clear of.
     */
    public record Kit(Set<String> body, Set<String> trim, String courseBlock, String courseStairs, String plinthBlock,
                      String plinthStairs, String shaftWall, String quoin, List<String> shadow) {
    }

    /** Which passes run; small churches already dress their own plinths and courses. */
    public record Options(boolean plinth, boolean courses, boolean cornice, boolean hoods, boolean shafts, boolean panels,
                          boolean quoins, boolean weatherings) {
        public static final Options ALL = new Options(true, true, true, true, true, true, true, true);
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Openings up to 2R+1 wide are sealed when finding the outside, so the doors do not let it in. */
    private static final int SEAL = 4;
    private static final int RAY = 14;
    private static final byte STRUCTURE = RasterPass.STRUCTURE.code();

    private final Volume v;
    private final Kit kit;
    private final double seed;
    private final int floorY;
    private final boolean[] touched;
    private final boolean[] protectedCell;
    private boolean[] reached;
    private boolean[] bodyId = new boolean[0];
    private boolean[] trimId = new boolean[0];
    private boolean[] partialId = new boolean[0];
    private final Map<String, Integer> ids = new HashMap<>();

    private Detailer(Volume v, Kit kit, double seed, int floorY) {
        this.v = v;
        this.kit = kit;
        this.seed = seed;
        this.floorY = floorY;
        this.touched = new boolean[v.length()];
        this.protectedCell = new boolean[v.length()];
    }

    /**
     * Returns a copy of {@code in}, grown by two blocks each way, with the detail added. Run it
     * before {@code Normalize}, which sets the stairs' corner shapes and the walls' connections.
     */
    public static Volume detail(Volume in, Kit kit, double seed, int floorY, List<Course> courses, Options options) {
        return detail(in, 0, kit, seed, floorY, courses, options);
    }

    /**
     * As above for a building turned {@code turns} quarter turns clockwise (seen from above): it is
     * dressed in its own frame and turned back, so every turn gets the same detail.
     */
    public static Volume detail(Volume in, int turns, Kit kit, double seed, int floorY, List<Course> courses, Options options) {
        int t = Math.floorMod(turns, 4);
        if (t != 0) return rotate(detail(rotate(in, 4 - t), 0, kit, seed, floorY, courses, options), t);
        Volume v = grow(in, SEAL + 4);
        Detailer d = new Detailer(v, kit, seed, floorY);
        d.classify();
        d.findOutside();
        if (options.plinth()) d.plinth();
        if (options.courses()) for (Course c : courses) d.course(c);
        if (options.cornice()) d.cornice();
        if (options.hoods()) d.hoods();
        if (options.shafts()) d.cornerShafts();
        if (options.panels()) {
            d.panels(6, 4);
            // Then the leftovers: gables, spandrels, the strips between windows.
            d.panels(5, 3);
        }
        if (options.quoins()) d.quoins();
        if (options.weatherings()) d.weatherings();
        return crop(v);
    }

    /** The volume turned {@code t} quarter turns clockwise about the origin, as {@code Mason.toVolume} turns. */
    static Volume rotate(Volume in, int t) {
        Box b = in.bounds();
        int[] xs = {(int) b.min().x(), (int) b.max().x() - 1}, zs = {(int) b.min().z(), (int) b.max().z() - 1};
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x : xs) {
            for (int z : zs) {
                int[] r = turn(x, z, t);
                minX = Math.min(minX, r[0]);
                maxX = Math.max(maxX, r[0]);
                minZ = Math.min(minZ, r[1]);
                maxZ = Math.max(maxZ, r[1]);
            }
        }
        Volume out = new Volume(Box.of(minX, b.min().y(), minZ, maxX + 1, b.max().y(), maxZ + 1));
        int[] remap = new int[in.paletteSize()];
        for (int i = 0; i < remap.length; i++) remap[i] = out.intern(Blockstates.rotateState(in.stateOfId(i), t, false));
        in.forEachSolid((x, y, z, id) -> {
            int[] r = turn(x, z, t);
            out.setId(r[0], y, r[1], remap[id], in.passAt(x, y, z));
        });
        return out;
    }

    private static int[] turn(int x, int z, int t) {
        for (int i = 0; i < t; i++) {
            int nx = -z;
            z = x;
            x = nx;
        }
        return new int[]{x, z};
    }

    /** The volume cut back to its blocks. */
    private static Volume crop(Volume v) {
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        v.forEachSolid((x, y, z, id) -> {
            lo[0] = Math.min(lo[0], x);
            lo[1] = Math.min(lo[1], y);
            lo[2] = Math.min(lo[2], z);
            hi[0] = Math.max(hi[0], x);
            hi[1] = Math.max(hi[1], y);
            hi[2] = Math.max(hi[2], z);
        });
        if (lo[0] == Integer.MAX_VALUE) return v;
        return v.extract(Box.of(lo[0], lo[1], lo[2], hi[0] + 1, hi[1] + 1, hi[2] + 1));
    }

    private static Volume grow(Volume in, int m) {
        Box b = in.bounds();
        Volume out = new Volume(Box.of(b.min().x() - m, b.min().y() - m, b.min().z() - m,
                b.max().x() + m, b.max().y() + m, b.max().z() + m));
        int[] remap = new int[in.paletteSize()];
        for (int i = 0; i < remap.length; i++) remap[i] = out.intern(in.stateOfId(i));
        in.forEachSolid((x, y, z, id) -> out.setId(x, y, z, remap[id], in.passAt(x, y, z)));
        return out;
    }

    // ---- block tests --------------------------------------------------------------------------

    private void classify() {
        int n = v.paletteSize();
        bodyId = new boolean[n];
        trimId = new boolean[n];
        partialId = new boolean[n];
        for (int i = 1; i < n; i++) {
            BlockState s = v.stateOfId(i);
            bodyId[i] = kit.body().contains(s.name);
            trimId[i] = kit.trim().contains(s.name);
            partialId[i] = Blockstates.isStairs(s) || Blockstates.isSlab(s) || Blockstates.isWall(s);
        }
    }

    private boolean in(int x, int y, int z) {
        return v.contains(x, y, z);
    }

    private boolean air(int x, int y, int z) {
        return !in(x, y, z) || v.idAt(x, y, z) == 0;
    }

    private boolean body(int x, int y, int z) {
        if (!in(x, y, z)) return false;
        int i = v.index(x, y, z);
        int id = v.states[i];
        // Floors are laid in a pass of their own and may share a stone with the walls.
        return id < bodyId.length && bodyId[id] && !touched[i] && v.passes[i] == STRUCTURE;
    }

    /** Dressed work already there: the generator's own trim, or any stair, slab or wall. */
    private boolean dressed(int x, int y, int z) {
        if (!in(x, y, z)) return false;
        int id = v.idAt(x, y, z);
        return id < trimId.length && (trimId[id] || partialId[id]);
    }

    private boolean isTouched(int x, int y, int z) {
        return in(x, y, z) && touched[v.index(x, y, z)];
    }

    /** Air cells in front of a face, up to {@code max}. */
    private int free(int x, int y, int z, int dx, int dz, int max) {
        int n = 0;
        for (int k = 1; k <= max; k++) {
            if (!air(x + dx * k, y, z + dz * k)) break;
            n++;
        }
        return n;
    }

    /** Whether the face at (x, y, z) looking (dx, dz) looks out of the building. */
    private boolean outside(int x, int y, int z, int dx, int dz) {
        for (int k = 1; k <= RAY; k++) {
            int ax = x + dx * k, az = z + dz * k;
            if (!in(ax, y, az)) return true;
            if (!air(ax, y, az)) return false;
            if (reached[v.index(ax, y, az)]) return true;
        }
        return false;
    }

    /** Somewhere the detail may stand: outside, or inside with room to walk past it. */
    private boolean roomy(int x, int y, int z, int dx, int dz) {
        return outside(x, y, z, dx, dz) || free(x, y, z, dx, dz, 4) >= 4;
    }

    // ---- writing ------------------------------------------------------------------------------

    private int id(String name, String... props) {
        String key = props.length == 0 ? name : name + "|" + String.join(",", props);
        Integer id = ids.get(key);
        if (id == null) {
            Map<String, String> p = new LinkedHashMap<>();
            for (int i = 0; i + 1 < props.length; i += 2) p.put(props[i], props[i + 1]);
            id = v.intern(Blockstates.makeState(name, p));
            ids.put(key, id);
        }
        return id;
    }

    private void put(int x, int y, int z, int id) {
        if (!in(x, y, z)) return;
        v.setId(x, y, z, id, RasterPass.DECORATION);
        touched[v.index(x, y, z)] = true;
    }

    private void putIfAir(int x, int y, int z, int id) {
        if (in(x, y, z) && air(x, y, z)) put(x, y, z, id);
    }

    private int stairs(String name, int fx, int fz, boolean top) {
        return id(name, "facing", dir(fx, fz), "half", top ? "top" : "bottom");
    }

    private static String dir(int dx, int dz) {
        if (dx > 0) return "east";
        if (dx < 0) return "west";
        return dz > 0 ? "south" : "north";
    }

    private int shadow(int x, int y, int z) {
        List<String> s = kit.shadow();
        double n = Hash.noise3(seed, "shadow", x, y, z, 2.5) * 0.7 + Hash.hash(seed, "shadow", x, y, z) * 0.3;
        return id(s.get(Math.min(s.size() - 1, (int) (n * s.size()))));
    }

    // ---- the outside --------------------------------------------------------------------------

    /**
     * Floods the air from the volume's edges with every opening up to 2·SEAL+1 wide stopped, so a
     * nave entered by a great door still counts as inside. Faces then look out if a short ray
     * from them reaches the flood.
     */
    private void findOutside() {
        int sx = v.sizeX, sy = v.sizeY, sz = v.sizeZ;
        boolean[] sealed = new boolean[v.length()];
        int[] dist = new int[Math.max(sx, sz)];
        boolean[] rowX = new boolean[sx * sz];
        for (int y = 0; y < sy; y++) {
            // Along x, then along z of that: a square dilation of the solid by SEAL.
            for (int z = 0; z < sz; z++) {
                int last = -1000;
                for (int x = 0; x < sx; x++) {
                    if (v.states[(y * sz + z) * sx + x] != 0) last = x;
                    dist[x] = x - last;
                }
                last = 1000000;
                for (int x = sx - 1; x >= 0; x--) {
                    if (v.states[(y * sz + z) * sx + x] != 0) last = x;
                    rowX[z * sx + x] = Math.min(dist[x], last - x) <= SEAL;
                }
            }
            for (int x = 0; x < sx; x++) {
                int last = -1000;
                for (int z = 0; z < sz; z++) {
                    if (rowX[z * sx + x]) last = z;
                    dist[z] = z - last;
                }
                last = 1000000;
                for (int z = sz - 1; z >= 0; z--) {
                    if (rowX[z * sx + x]) last = z;
                    sealed[(y * sz + z) * sx + x] = Math.min(dist[z], last - z) <= SEAL;
                }
            }
        }
        reached = new boolean[v.length()];
        int[] queue = new int[v.length()];
        int head = 0, tail = 0;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    boolean edge = x == 0 || z == 0 || x == sx - 1 || z == sz - 1 || y == sy - 1;
                    int i = (y * sz + z) * sx + x;
                    if (edge && !sealed[i]) {
                        reached[i] = true;
                        queue[tail++] = i;
                    }
                }
            }
        }
        int plane = sx * sz;
        while (head < tail) {
            int i = queue[head++];
            int x = i % sx, z = (i / sx) % sz, y = i / plane;
            int[] ns = {x > 0 ? i - 1 : -1, x < sx - 1 ? i + 1 : -1, z > 0 ? i - sx : -1, z < sz - 1 ? i + sx : -1,
                    y > 0 ? i - plane : -1, y < sy - 1 ? i + plane : -1};
            for (int n : ns) {
                if (n < 0 || reached[n] || sealed[n]) continue;
                reached[n] = true;
                queue[tail++] = n;
            }
        }
    }

    // ---- 1. plinth ----------------------------------------------------------------------------

    private record Placed(int x, int y, int z, int dx, int dz) {
    }

    /**
     * Outside: a plinth block below the floor and at it, weathered with a stair on top. Inside:
     * a bench course at the foot of walls and a base round piers.
     */
    private void plinth() {
        int y = floorY;
        List<Placed> out = new ArrayList<>(), inside = new ArrayList<>();
        forFaces(y, (x, z, dx, dz) -> {
            if (!body(x, y + 1, z) || !body(x, y + 2, z)) return;
            if (!air(x + dx, y + 1, z + dz)) return;
            if (outside(x, y, z, dx, dz)) {
                if (body(x, y + 3, z)) out.add(new Placed(x + dx, y, z + dz, dx, dz));
            } else if (free(x, y, z, dx, dz, 4) >= 4) {
                inside.add(new Placed(x + dx, y, z + dz, dx, dz));
            }
        });
        int block = id(kit.plinthBlock());
        Map<Long, int[]> stairs = new HashMap<>();
        for (Placed p : out) {
            putIfAir(p.x, p.y - 1, p.z, block);
            put(p.x, p.y, p.z, block);
            int s = stairs(kit.plinthStairs(), -p.dx, -p.dz, false);
            put(p.x, p.y + 1, p.z, s);
            stairs.put(key(p.x, p.y + 1, p.z), new int[]{p.dx, p.dz});
        }
        for (Placed p : inside) {
            put(p.x, p.y, p.z, stairs(kit.plinthStairs(), -p.dx, -p.dz, false));
            stairs.put(key(p.x, p.y, p.z), new int[]{p.dx, p.dz});
        }
        fillCorners(stairs, (x, y2, z, dx, dz) -> {
            if (y2 == y + 1) {
                putIfAir(x, y - 1, z, block);
                putIfAir(x, y, z, block);
            }
            put(x, y2, z, stairs(kit.plinthStairs(), -dx, -dz, false));
        });
    }

    private interface FaceVisitor {
        void visit(int x, int z, int dx, int dz);
    }

    /** Every wall-stone face at height y, with its outward direction. */
    private void forFaces(int y, FaceVisitor f) {
        if (y < v.minY || y >= v.minY + v.sizeY) return;
        for (int z = v.minZ; z < v.minZ + v.sizeZ; z++) {
            for (int x = v.minX; x < v.minX + v.sizeX; x++) {
                if (!body(x, y, z)) continue;
                for (int[] d : DIRS) {
                    if (air(x + d[0], y, z + d[1])) f.visit(x, z, d[0], d[1]);
                }
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + (1 << 20)) << 42) | ((long) (y + (1 << 20)) << 21) | (z + (1 << 20));
    }

    private interface CornerVisitor {
        void visit(int x, int y, int z, int dx, int dz);
    }

    /**
     * Closes a projecting course round an outside corner: where two runs meet diagonally, a
     * stair in the empty corner cell (Normalize then turns it to an outer corner).
     */
    private void fillCorners(Map<Long, int[]> placed, CornerVisitor f) {
        List<int[]> corners = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : placed.entrySet()) {
            long k = e.getKey();
            int x = (int) ((k >>> 42) & 0x1FFFFF) - (1 << 20), y = (int) ((k >>> 21) & 0x1FFFFF) - (1 << 20), z = (int) (k & 0x1FFFFF) - (1 << 20);
            int dx = e.getValue()[0], dz = e.getValue()[1];
            for (int s : new int[]{-1, 1}) {
                int ex = dz * s, ez = dx * s;
                int ax = x + ex, az = z + ez;
                if (!air(ax, y, az)) continue;
                int[] other = placed.get(key(ax - dx, y, az - dz));
                if (other != null && other[0] == ex && other[1] == ez) corners.add(new int[]{ax, y, az, dx, dz});
            }
        }
        for (int[] c : corners) if (air(c[0], c[1], c[2])) f.visit(c[0], c[1], c[2], c[3], c[4]);
    }

    // ---- 2. string courses --------------------------------------------------------------------

    /** Whether dressed work already runs within two courses of this face. */
    private boolean nearDressed(int x, int y, int z, int dx, int dz) {
        for (int k = -2; k <= 2; k++) {
            if (dressed(x, y + k, z) || dressed(x + dx, y + k, z + dz)) return true;
        }
        return false;
    }

    private void course(Course c) {
        int y = c.y();
        List<Placed> faces = new ArrayList<>();
        forFaces(y, (x, z, dx, dz) -> {
            if (!roomy(x, y, z, dx, dz) || nearDressed(x, y, z, dx, dz)) return;
            faces.add(new Placed(x, y, z, dx, dz));
        });
        int band = id(kit.courseBlock());
        Map<Long, int[]> placed = new HashMap<>();
        for (Placed p : faces) {
            put(p.x, y, p.z, band);
            int fx = p.x + p.dx, fz = p.z + p.dz;
            if (!air(fx, y, fz)) continue;
            put(fx, y, fz, stairs(kit.courseStairs(), -p.dx, -p.dz, c.capital()));
            placed.put(key(fx, y, fz), new int[]{p.dx, p.dz});
        }
        fillCorners(placed, (x, y2, z, dx, dz) -> put(x, y2, z, stairs(kit.courseStairs(), -dx, -dz, c.capital())));
    }

    // ---- 3. cornice ---------------------------------------------------------------------------

    /**
     * Under the top of every wall that stands at least six courses: a stair cornice, and below it
     * a corbel every other block. Only along level runs of three or more, so gables are left to
     * their copings.
     */
    private void cornice() {
        List<Placed> tops = new ArrayList<>();
        Set<Long> top = new java.util.HashSet<>();
        for (int y = v.minY + 6; y < v.minY + v.sizeY - 1; y++) {
            final int yy = y;
            forFaces(y, (x, z, dx, dz) -> {
                if (body(x, yy + 1, z) || !air(x + dx, yy + 1, z + dz) && !dressed(x + dx, yy + 1, z + dz)) return;
                for (int k = 1; k <= 5; k++) if (!body(x, yy - k, z)) return;
                if (!outside(x, yy, z, dx, dz) || nearDressed(x, yy - 1, z, dx, dz)) return;
                tops.add(new Placed(x, yy, z, dx, dz));
                top.add(key(x, yy, z) * 4 + dirIndex(dx, dz));
            });
        }
        Map<Long, int[]> placed = new HashMap<>();
        for (Placed p : tops) {
            int ex = p.dz, ez = p.dx; // along the face
            int run = 1;
            for (int s : new int[]{-1, 1}) {
                for (int k = 1; k <= 2; k++) {
                    if (top.contains(key(p.x + ex * s * k, p.y, p.z + ez * s * k) * 4 + dirIndex(p.dx, p.dz))) run++;
                    else break;
                }
            }
            if (run < 3) continue;
            int fx = p.x + p.dx, fz = p.z + p.dz;
            if (!air(fx, p.y, fz)) continue;
            put(fx, p.y, fz, stairs(kit.courseStairs(), -p.dx, -p.dz, true));
            placed.put(key(fx, p.y, fz), new int[]{p.dx, p.dz});
            // Every other block, counted from the run's start so every turn agrees.
            int back = 0;
            while (top.contains(key(p.x - ex * (back + 1), p.y, p.z - ez * (back + 1)) * 4 + dirIndex(p.dx, p.dz))) back++;
            if (back % 2 == 0 && air(fx, p.y - 1, fz)) {
                put(fx, p.y - 1, fz, stairs(kit.courseStairs(), -p.dx, -p.dz, true));
            }
        }
        fillCorners(placed, (x, y, z, dx, dz) -> put(x, y, z, stairs(kit.courseStairs(), -dx, -dz, true)));
    }

    private static int dirIndex(int dx, int dz) {
        return dx > 0 ? 0 : dx < 0 ? 1 : dz > 0 ? 2 : 3;
    }

    // ---- 4. hood moulds -----------------------------------------------------------------------

    /** An opening below: air, glass or tracery the generator put there, not anything of ours. */
    private boolean opening(int x, int y, int z) {
        if (!in(x, y, z) || isTouched(x, y, z)) return false;
        int id = v.idAt(x, y, z);
        if (id == 0) return true;
        BlockState s = v.stateOfId(id);
        return (id < trimId.length && trimId[id]) || s.name.contains("glass") || s.name.contains("trapdoor")
                || s.name.contains("iron_bars");
    }

    /**
     * A drip stair over every stone that heads an opening, following the arch's steps, carried
     * one block past the springing each side.
     */
    private void hoods() {
        List<Placed> heads = new ArrayList<>();
        for (int y = v.minY + 1; y < v.minY + v.sizeY - 1; y++) {
            final int yy = y;
            forFaces(y, (x, z, dx, dz) -> {
                if (!opening(x, yy - 1, z) || !air(x + dx, yy - 1, z + dz)) return;
                // The opening must be a hole in this face, not a ledge: stone behind it or beside it.
                if (!roomy(x, yy, z, dx, dz) || nearDressedFront(x, yy, z, dx, dz)) return;
                heads.add(new Placed(x, yy, z, dx, dz));
            });
        }
        Set<Long> at = new java.util.HashSet<>();
        for (Placed p : heads) at.add(key(p.x, p.y, p.z));
        List<Placed> all = new ArrayList<>(heads);
        for (Placed p : heads) {
            int ex = p.dz, ez = p.dx;
            for (int s : new int[]{-1, 1}) {
                int x = p.x + ex * s, z = p.z + ez * s;
                if (at.contains(key(x, p.y, z)) || at.contains(key(x, p.y + 1, z))) continue;
                if (body(x, p.y, z) && body(x, p.y - 1, z) && air(x + p.dx, p.y, z + p.dz)) {
                    all.add(new Placed(x, p.y, z, p.dx, p.dz));
                    // A label stop: the drip turns down one course.
                    if (body(x, p.y - 2, z) && air(x + p.dx, p.y - 1, z + p.dz)) all.add(new Placed(x, p.y - 1, z, p.dx, p.dz));
                }
            }
        }
        for (Placed p : all) {
            putIfAir(p.x + p.dx, p.y, p.z + p.dz, stairs(kit.courseStairs(), -p.dx, -p.dz, true));
        }
    }

    private boolean nearDressedFront(int x, int y, int z, int dx, int dz) {
        for (int k = 0; k <= 1; k++) if (dressed(x + dx, y + k, z + dz)) return true;
        return false;
    }

    // ---- 5. corner shafts ---------------------------------------------------------------------

    /**
     * Where a buttress or respond meets a wall, a shaft of wall blocks stands in the angle, on the
     * plinth, with a stair capital. Six courses at least.
     */
    private void cornerShafts() {
        int shaft = id(kit.shaftWall());
        for (int z = v.minZ + 1; z < v.minZ + v.sizeZ - 1; z++) {
            for (int x = v.minX + 1; x < v.minX + v.sizeX - 1; x++) {
                for (int[] e1 : DIRS) {
                    int[] e2 = {e1[1], -e1[0]};
                    int run = 0;
                    for (int y = v.minY + 1; y < v.minY + v.sizeY; y++) {
                        boolean ok = y < v.minY + v.sizeY - 1 && angle(x, y, z, e1, e2);
                        if (ok) {
                            run++;
                            continue;
                        }
                        if (run >= 6) {
                            int y0 = y - run, y1 = y - 1;
                            if (roomy(x, y0 + run / 2, z, -e1[0], -e1[1]) && roomy(x, y0 + run / 2, z, -e2[0], -e2[1])) {
                                for (int yy = y0; yy < y1; yy++) put(x, yy, z, shaft);
                                put(x, y1, z, stairs(kit.courseStairs(), e1[0], e1[1], true));
                            }
                        }
                        run = 0;
                    }
                }
            }
        }
    }

    private boolean angle(int x, int y, int z, int[] e1, int[] e2) {
        return air(x, y, z) && !isTouched(x, y, z) && body(x + e1[0], y, z + e1[1]) && body(x + e2[0], y, z + e2[1])
                && air(x - e1[0], y, z - e1[1]) && air(x - e2[0], y, z - e2[1]) && air(x - e1[0] - e2[0], y, z - e1[1] - e2[1]);
    }

    // ---- 6. blind arcades ---------------------------------------------------------------------

    /**
     * Every plane of wall is searched for the largest flat rectangles left; each becomes a row of
     * pointed panels carved one block deep, framed by a block all round, with shafts in front of
     * the piers between them and a weathered sill. Tall rectangles are cut into tiers.
     */
    private void panels(int minH, int minTier) {
        for (int[] d : DIRS) {
            boolean alongX = d[0] != 0;
            int planes = alongX ? v.sizeX : v.sizeZ;
            int us = alongX ? v.sizeZ : v.sizeX;
            int ys = v.sizeY;
            for (int p = 0; p < planes; p++) {
                boolean[] ok = new boolean[us * ys];
                int count = 0;
                for (int y = 0; y < ys; y++) {
                    for (int u = 0; u < us; u++) {
                        int x = alongX ? v.minX + p : v.minX + u, z = alongX ? v.minZ + u : v.minZ + p;
                        int wy = v.minY + y;
                        if (carvable(x, wy, z, d[0], d[1])) {
                            ok[y * us + u] = true;
                            count++;
                        }
                    }
                }
                if (count < 30) continue;
                for (int guard = 0; guard < 200; guard++) {
                    int[] r = bestRect(ok, us, ys, minH);
                    if (r == null) break;
                    for (int y = Math.max(0, r[1] - 1); y <= Math.min(ys - 1, r[3] + 1); y++) {
                        for (int u = Math.max(0, r[0] - 1); u <= Math.min(us - 1, r[2] + 1); u++) ok[y * us + u] = false;
                    }
                    arcade(d, alongX, p, r, minTier);
                }
            }
        }
    }

    private boolean carvable(int x, int y, int z, int dx, int dz) {
        if (!body(x, y, z) || !air(x + dx, y, z + dz)) return false;
        if (protectedCell[v.index(x, y, z)]) return false;
        if (!body(x - dx, y, z - dz) && !isTouched(x - dx, y, z - dz)) return false;
        if (air(x - dx, y, z - dz)) return false;
        return roomy(x, y, z, dx, dz);
    }

    /** The largest rectangle of {@code ok} at least 3 wide and minH tall (5 wide or minH+2 tall): {u0, y0, u1, y1}. */
    private static int[] bestRect(boolean[] ok, int us, int ys, int minH) {
        int[] h = new int[us];
        int[] best = null;
        int bestArea = 0;
        for (int y = 0; y < ys; y++) {
            for (int u = 0; u < us; u++) h[u] = ok[y * us + u] ? h[u] + 1 : 0;
            // Every maximal rectangle with its bottom on this row, from each bar's run of taller bars.
            for (int u = 0; u < us; u++) {
                if (h[u] == 0 || (u > 0 && h[u - 1] == h[u])) continue;
                int l = u, r = u;
                while (l > 0 && h[l - 1] >= h[u]) l--;
                while (r < us - 1 && h[r + 1] >= h[u]) r++;
                int w = r - l + 1, ht = h[u];
                if (w > 31) {
                    // Very long walls: take a bay's worth at a time so the panels stay in step with it.
                    w = 31;
                    r = l + 30;
                }
                if (w < 3 || ht < minH || (w < 5 && ht < minH + 2)) continue;
                int area = w * Math.min(ht, 24);
                if (area > bestArea) {
                    bestArea = area;
                    best = new int[]{l, y - ht + 1, r, y};
                }
            }
        }
        return best;
    }

    private static int[] fitPanels(int inner) {
        for (int w = inner; w >= 1; w -= 2) {
            if (w == 1) return new int[]{1, 1, inner};
            for (int pw : new int[]{3, 2, 4, 5}) {
                if ((w + 1) % (pw + 1) == 0) return new int[]{pw, (w + 1) / (pw + 1), w};
            }
        }
        return null;
    }

    private void arcade(int[] d, boolean alongX, int p, int[] r, int minTier) {
        int dx = d[0], dz = d[1];
        int w = r[2] - r[0] + 1, h = r[3] - r[1] + 1;
        int[] fit = fitPanels(w - 2);
        if (fit == null) return;
        int pw = fit[0], n = fit[1], used = fit[2];
        int uStart = r[0] + 1 + (w - 2 - used) / 2;
        int inner = h - 2;
        int tiers = Math.max(1, (int) Math.ceil((inner + 1) / 13.0));
        int tierH = (inner - (tiers - 1)) / tiers;
        if (tierH < minTier) return;
        int yBase = r[1] + 1 + (inner - (tierH * tiers + tiers - 1)) / 2;
        for (int t = 0; t < tiers; t++) {
            int y0 = yBase + t * (tierH + 1), y1 = y0 + tierH - 1;
            for (int i = 0; i < n; i++) {
                int u0 = uStart + i * (pw + 1);
                carvePanel(alongX, p, u0, pw, v.minY + y0, v.minY + y1, dx, dz);
                if (i + 1 < n) shaftFront(alongX, p, u0 + pw, v.minY + y0, v.minY + y1, dx, dz);
            }
        }
    }

    private int wx(boolean alongX, int p, int u) {
        return alongX ? v.minX + p : v.minX + u;
    }

    private int wz(boolean alongX, int p, int u) {
        return alongX ? v.minZ + u : v.minZ + p;
    }

    private void carvePanel(boolean alongX, int p, int u0, int pw, int y0, int y1, int dx, int dz) {
        // All or nothing: one blocked cell leaves the whole panel flat.
        for (int u = u0; u < u0 + pw; u++) {
            for (int y = y0; y <= y1; y++) {
                int x = wx(alongX, p, u), z = wz(alongX, p, u);
                if (!body(x, y, z) || protectedCell[v.index(x, y, z)] || air(x - dx, y, z - dz)) return;
            }
        }
        int ex = alongX ? 0 : 1, ez = alongX ? 1 : 0; // +u in the world
        int rise = Math.max(1, pw / 2);
        int sill = stairs(kit.courseStairs(), -dx, -dz, false);
        for (int u = u0; u < u0 + pw; u++) {
            int k = u - u0;
            int fromEdge = Math.min(k, pw - 1 - k);
            int x = wx(alongX, p, u), z = wz(alongX, p, u);
            for (int y = y0; y <= y1; y++) {
                int row = y1 - y; // 0 at the top
                int bx = x - dx, bz = z - dz;
                protectedCell[v.index(bx, y, bz)] = true;
                if (body(bx, y, bz)) put(bx, y, bz, shadow(bx, y, bz));
                if (row < rise) {
                    int indent = rise - 1 - row;
                    if (fromEdge < indent) continue; // still wall
                    if (fromEdge == indent && pw > 1) {
                        int s = k < pw / 2.0 ? -1 : 1;
                        if (pw % 2 == 1 && k == pw / 2) {
                            put(x, y, z, 0);
                        } else {
                            put(x, y, z, stairs(kit.courseStairs(), ex * s, ez * s, true));
                        }
                        continue;
                    }
                    if (pw == 1) {
                        put(x, y, z, stairs(kit.courseStairs(), -dx, -dz, true));
                        continue;
                    }
                }
                put(x, y, z, y == y0 ? sill : 0);
            }
        }
    }

    private void shaftFront(boolean alongX, int p, int u, int y0, int y1, int dx, int dz) {
        int x = wx(alongX, p, u) + dx, z = wz(alongX, p, u) + dz;
        for (int y = y0 - 1; y <= y1; y++) if (!air(x, y, z) || !air(x + dx, y, z + dz)) return;
        if (!outside(x - dx, (y0 + y1) / 2, z - dz, dx, dz) && free(x - dx, y0, z - dz, dx, dz, 5) < 5) return;
        put(x, y0 - 1, z, stairs(kit.courseStairs(), -dx, -dz, true));
        int shaft = id(kit.shaftWall());
        for (int y = y0; y < y1; y++) put(x, y, z, shaft);
        put(x, y1, z, stairs(kit.courseStairs(), -dx, -dz, true));
    }

    // ---- 7. quoins and weatherings ------------------------------------------------------------

    /** Dressed corner stones, long and short, on outside corners of eight courses and more. */
    private void quoins() {
        int q = id(kit.quoin());
        for (int z = v.minZ + 1; z < v.minZ + v.sizeZ - 1; z++) {
            for (int x = v.minX + 1; x < v.minX + v.sizeX - 1; x++) {
                for (int[] e1 : DIRS) {
                    int[] e2 = {e1[1], -e1[0]};
                    int run = 0;
                    for (int y = v.minY; y < v.minY + v.sizeY; y++) {
                        boolean ok = y < v.minY + v.sizeY - 1 && body(x, y, z) && air(x + e1[0], y, z + e1[1])
                                && air(x + e2[0], y, z + e2[1]) && air(x + e1[0] + e2[0], y, z + e1[1] + e2[1])
                                && wide(x, y, z, e1, e2) && wide(x, y, z, e2, e1);
                        if (ok) {
                            run++;
                            continue;
                        }
                        if (run >= 8 && outside(x, y - run / 2 - 1, z, e1[0], e1[1])) {
                            for (int yy = y - run; yy < y; yy++) {
                                boolean longOnE1 = Math.floorMod(yy, 4) < 2;
                                put(x, yy, z, q);
                                int ax = x - (longOnE1 ? e2[0] : e1[0]), az = z - (longOnE1 ? e2[1] : e1[1]);
                                if (body(ax, yy, az)) put(ax, yy, az, q);
                            }
                        }
                        run = 0;
                    }
                }
            }
        }
    }

    /** A face at least five wide running back from the corner along -along (looking out along {@code out}). */
    private boolean wide(int x, int y, int z, int[] out, int[] along) {
        for (int k = 1; k <= 4; k++) {
            int ax = x - along[0] * k, az = z - along[1] * k;
            if (in(ax, y, az) && v.idAt(ax, y, az) == 0) return false;
            if (!air(ax + out[0], y, az + out[1])) return false;
        }
        return true;
    }

    /**
     * A stair weathering on every narrow set-off open to the sky (a buttress stepping back, a
     * stage of a tower): the high side against the wall that rises behind.
     */
    private void weatherings() {
        int sx = v.sizeX, sz = v.sizeZ;
        int[] topOf = new int[sx * sz];
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                int t = Integer.MIN_VALUE;
                for (int y = v.sizeY - 1; y >= 0; y--) {
                    if (v.idAt(v.minX + x, v.minY + y, v.minZ + z) != 0) {
                        t = v.minY + y;
                        break;
                    }
                }
                topOf[z * sx + x] = t;
            }
        }
        List<int[]> put = new ArrayList<>();
        for (int z = v.minZ + 1; z < v.minZ + sz - 1; z++) {
            for (int x = v.minX + 1; x < v.minX + sx - 1; x++) {
                int y = topOf[(z - v.minZ) * sx + (x - v.minX)];
                if (y == Integer.MIN_VALUE || !body(x, y, z) && !isTouched(x, y, z)) continue;
                if (!body(x, y, z) && !Blockstates.isFullCube(v.at(x, y, z))) continue;
                for (int[] d : DIRS) {
                    // Wall rising behind (opposite d), open in front, ledge one deep.
                    int wxx = x - d[0], wzz = z - d[1];
                    if (!body(wxx, y + 1, wzz) || !body(wxx, y + 2, wzz)) continue;
                    if (!air(x + d[0], y, z + d[1]) || !air(x + d[0], y + 1, z + d[1])) continue;
                    put.add(new int[]{x, y + 1, z, d[0], d[1]});
                    break;
                }
            }
        }
        for (int[] p : put) if (air(p[0], p[1], p[2])) put(p[0], p[1], p[2], stairs(kit.courseStairs(), -p[3], -p[4], false));
    }
}
