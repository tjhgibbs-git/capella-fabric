package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.params.Params.BridgeArch;
import io.github.tjhgibbs.capella.core.params.Params.BridgeParapet;
import io.github.tjhgibbs.capella.core.params.Params.BridgeWalkways;
import io.github.tjhgibbs.capella.core.params.Params.Cutwaters;
import io.github.tjhgibbs.capella.core.raster.Volume;

import java.util.ArrayList;
import java.util.List;

/**
 * A Gothic arcade bridge in the bridge frame: +x along the span, the walking datum y=0 at the
 * start landing, supports down to y=-height.
 *
 * <p>Each tier is an arcade of struck arches whose crowns sit level under the tier top. Arches are
 * true concentric curves, so the face order (one block larger), the soffit and the projecting
 * ribs (one block smaller) are exact offsets of one another. Upper tiers stand on the lower piers
 * and may split each lower bay into a smaller arcade. A tier below the deck can carry a gallery
 * walk that tunnels through the piers above it; stair turrets at both ends join every walk.
 */
final class GothicBridge {
    private final BuildingParams p;
    private final Mason m;
    private final Style s;
    private final double seed;
    private final int l, w, h, z0, z1, slope;
    private final BridgeArch form;
    private final BridgeWalkways walk;
    private final BridgeParapet parapet;
    private final Cutwaters cut;
    private final int abut, pierW, span;
    final List<Tier> tiers = new ArrayList<>();

    record Arch(int x0, int x1, int spring) {
        double xc() { return (x0 + x1) / 2.0; }
        double a() { return (x1 - x0 + 1) / 2.0; }
        int width() { return x1 - x0 + 1; }
    }

    static final class Tier {
        int base, top, crown;
        boolean gallery;
        List<Arch> arches = new ArrayList<>();
        /** Pier ranges between arches, abutments excluded; main piers stand over the lowest tier's piers. */
        List<int[]> piers = new ArrayList<>();
    }

    GothicBridge(BuildingParams source) {
        p = source;
        l = p.integer(Params.LENGTH); w = p.integer(Params.WIDTH); h = p.integer(Params.HEIGHT);
        z0 = -w / 2; z1 = z0 + w - 1;
        slope = p.integer(Params.BRIDGE_SLOPE);
        seed = p.seed();
        m = new Mason(seed);
        s = Style.of(p.get(Params.STONE));
        form = p.get(Params.BRIDGE_ARCH);
        walk = p.get(Params.BRIDGE_WALKWAYS);
        parapet = p.get(Params.BRIDGE_PARAPET);
        cut = p.get(Params.CUTWATERS);
        span = p.integer(Params.BRIDGE_SPAN);
        pierW = span >= 8 ? 5 : 3;
        layoutTiers();
        abut = tiers.size() > 1 && walk.galleries() ? 5 : 3;
        layoutArches();
    }

    static Volume build(BuildingParams p, int heading) {
        GothicBridge b = new GothicBridge(p);
        b.build();
        Mason world = b.m.offsetColumns(b::deckBase).rotatedHeading(heading);
        b.turrets(world, heading);
        return world.toVolume(0);
    }

    /** Gallery walking surfaces (bridge frame, before the grade), lowest first; for tests. */
    static int[] galleryLevels(BuildingParams p) {
        GothicBridge b = new GothicBridge(p);
        return b.tiers.stream().filter(t -> t.gallery).mapToInt(t -> t.top).toArray();
    }

    // ---- Grade (shared with Bridge) ------------------------------------------------------------

    int halfRise(int x) {
        return (int) Math.round(2.0 * Math.clamp(x, 0, l - 1) * slope / 100.0);
    }

    int deckBase(int x) { return Math.floorDiv(halfRise(x), 2); }

    // ---- Layout --------------------------------------------------------------------------------

    private void layoutTiers() {
        int n = Math.clamp(p.integer(Params.BRIDGE_TIERS), 1, 3);
        // Upper tiers take the chosen height each; the lowest keeps at least eight blocks.
        while (n > 1 && h - 7 * (n - 1) < 8) n--;
        int upper = n == 1 ? 0 : Math.clamp(p.integer(Params.BRIDGE_TIER_HEIGHT), 7, (h - 8) / (n - 1));
        int base = -h;
        for (int i = 0; i < n; i++) {
            Tier t = new Tier();
            t.base = base;
            t.top = i == n - 1 ? 0 : -(n - 1 - i) * upper;
            int c = t.top - t.base >= 10 ? 3 : 2;
            t.crown = t.top - 1 - c;
            t.gallery = i < n - 1 && walk.galleries();
            tiers.add(t);
            base = t.top;
        }
    }

    private void layoutArches() {
        Tier bottom = tiers.get(0);
        int target = Math.max(1, (int) Math.round((l - 2 * abut + pierW) / (double) (span + pierW)));
        List<Arch> best = null;
        for (int n = target; ; n++) {
            List<Arch> arches = split(abut, l - abut - 1, n, pierW, bottom);
            if (arches == null) break;
            best = arches;
            if (fits(bottom, arches, 1)) break;
        }
        if (best == null) best = split(abut, l - abut - 1, 1, pierW, bottom);
        bottom.arches = best;
        bottom.piers = gaps(best);
        int[] prevK = new int[best.size()];
        java.util.Arrays.fill(prevK, 1);
        for (int i = 1; i < tiers.size(); i++) {
            Tier t = tiers.get(i);
            int minPier = tiers.get(i - 1).gallery ? 3 : 1;
            boolean small = i == tiers.size() - 1;
            List<Arch> done = new ArrayList<>();
            int[] used = new int[bottom.arches.size()];
            for (int b = 0; b < used.length; b++) {
                Arch bay = bottom.arches.get(b);
                // The top arcade is always finer than the tier it stands on.
                int k = small && bay.width() >= 9 ? Math.max(2, prevK[b] + 1) : 1;
                if (split(bay.x0, bay.x1, k, 1, t) == null) k = Math.max(1, prevK[b]);
                List<Arch> sub = split(bay.x0, bay.x1, k, 1, t);
                while (!fits(t, sub, minPier)) {
                    List<Arch> finer = split(bay.x0, bay.x1, ++k, 1, t);
                    if (finer == null) { k--; break; }
                    sub = finer;
                }
                done.addAll(sub);
                used[b] = k;
            }
            prevK = used;
            t.arches = done;
            t.piers = gaps(done);
        }
    }

    private List<int[]> gaps(List<Arch> arches) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < arches.size(); i++) out.add(new int[]{arches.get(i).x1 + 1, arches.get(i + 1).x0 - 1});
        return out;
    }

    /** n arches over [from, to] with piers between, extra blocks given symmetrically about the centre. */
    private List<Arch> split(int from, int to, int n, int pier, Tier t) {
        int clear = to - from + 1 - (n - 1) * pier;
        if (n < 1 || clear < 3 * n) return null;
        int[] spans = new int[n];
        java.util.Arrays.fill(spans, clear / n);
        int r = clear % n, centrePier = 0;
        if (n % 2 == 1 && r % 2 == 1) { spans[n / 2]++; r--; }
        for (int j = 0; r >= 2; j++) {
            int a = n % 2 == 1 ? n / 2 - 1 - j : n / 2 - 1 - j, b = n - 1 - a;
            spans[a]++; spans[b]++; r -= 2;
        }
        if (r == 1) centrePier = 1;
        List<Arch> out = new ArrayList<>();
        int x = from;
        for (int i = 0; i < n; i++) {
            int x1 = x + spans[i] - 1;
            out.add(new Arch(x, x1, springFor(t, spans[i])));
            x = x1 + 1 + pier + (n % 2 == 0 && i == n / 2 - 1 ? centrePier : 0);
        }
        return out;
    }

    private int springFor(Tier t, int width) {
        double a = width / 2.0, dx = width % 2 == 1 ? 0 : 0.5;
        return t.crown - (int) Math.floor(rise(dx, a, 0) - 0.5);
    }

    private boolean fits(Tier t, List<Arch> arches, int minPier) {
        if (arches == null) return false;
        for (Arch a : arches) if (a.spring < t.base + minPier) return false;
        return true;
    }

    // ---- Arch geometry -------------------------------------------------------------------------

    /**
     * Height of the intrados above the springing line at {@code dx} from the arch centre, for a
     * half-span {@code a} and a concentric offset {@code o} (positive: a larger, outer order).
     * Negative where the column is outside the arch.
     */
    double rise(double dx, double a, double o) {
        if (form == BridgeArch.FOUR_CENTRED) {
            double r1 = 0.45 * a, r2 = 2.0 * a, th = Math.toRadians(50), cs = Math.cos(th), sn = Math.sin(th);
            double c1 = a - r1;
            if (dx >= c1 + (r1 + o) * cs) {
                double q = (r1 + o) * (r1 + o) - (dx - c1) * (dx - c1);
                return q < 0 ? -1 : Math.sqrt(q);
            }
            double c2x = c1 - (r2 - r1) * cs, c2y = -(r2 - r1) * sn;
            double q = (r2 + o) * (r2 + o) - (dx - c2x) * (dx - c2x);
            return q < 0 ? -1 : c2y + Math.sqrt(q);
        }
        // Two-centred: each side is struck from a centre (k-1) half-spans beyond the far springer.
        double k = switch (form) {
            case LANCET -> 3.0;
            case DROP -> 1.4;
            default -> 2.0;
        };
        double e = (k - 1) * a, rad = k * a + o, q = rad * rad - (dx + e) * (dx + e);
        return q < 0 ? -1 : Math.sqrt(q);
    }

    /** Whether cell (x, y) is inside arch {@code ar}'s opening, for a slice with order offset {@code o}. */
    private boolean open(Arch ar, Tier t, int x, int y, int o) {
        if (y < t.base) return false;
        double dx = Math.abs(x - ar.xc());
        // Ribs spring from the impost: below it every slice keeps the plain jamb.
        int jamb = y < ar.spring ? Math.max(o, 0) : o;
        if (dx >= ar.a() + jamb) return false;
        if (y < ar.spring) return true;
        if (y > t.crown + Math.max(o, 0)) return false;
        return y - ar.spring + 0.5 <= rise(dx, ar.a(), o);
    }

    /** Order offset for slice z: faces one larger where there is room, ribs one smaller. */
    private int order(Tier t, Arch ar, int z) {
        if (z == z0 || z == z1) return t.top - t.crown >= 4 ? 1 : 0;
        int d = Math.min(z - z0, z1 - z);
        return ar.width() >= 7 && d >= 2 && (d - 2) % 3 == 0 ? -1 : 0;
    }

    // ---- Building ------------------------------------------------------------------------------

    private void stone(int x, int y, int z, boolean trim) {
        m.set(x, y, z, (trim ? s.trim : s.wall).block(seed, x, y, z, 0));
    }

    private void build() {
        for (Tier t : tiers) arcade(t);
        cutwaters();
        Tier top = tiers.get(tiers.size() - 1);
        deck();
        for (Tier t : tiers) if (t.gallery) gallery(t);
        deckFeatures(top);
    }

    private void arcade(Tier t) {
        boolean bottom = t == tiers.get(0);
        for (int x = 0; x < l; x++) for (int z = z0; z <= z1; z++) for (int y = t.base; y < t.top; y++) {
            boolean plinth = bottom && y == t.base;
            m.set(x, y, z, (plinth ? s.plinth : s.wall).block(seed, x, y, z, 0));
        }
        // The ledge under a gallery or the deck reads as a dressed course.
        for (int x = 0; x < l; x++) for (int z = z0; z <= z1; z++) stone(x, t.top - 1, z, true);
        for (Arch ar : t.arches) for (int z = z0; z <= z1; z++) {
            int o = order(t, ar, z);
            boolean dressed = o != 0 || z == z0 || z == z1;
            int xa = ar.x0 - 2, xb = ar.x1 + 2;
            for (int x = xa; x <= xb; x++) for (int y = t.base; y <= t.crown + 1; y++)
                if (open(ar, t, x, y, o)) m.clear(x, y, z);
            // Voussoirs: the solid band one block round the opening, in dressed stone on the
            // faces and ribs; smoothed with upturned stairs wherever the intrados steps.
            for (int x = xa; x <= xb; x++) for (int y = ar.spring; y <= t.crown + 2 && y < t.top - 1; y++) {
                if (x < 0 || x >= l || open(ar, t, x, y, o)) continue;
                boolean ring = false;
                for (int dx = -1; dx <= 1 && !ring; dx++) for (int dy = -1; dy <= 1; dy++)
                    if (open(ar, t, x + dx, y + dy, o)) { ring = true; break; }
                if (!ring) continue;
                int side = x > ar.xc() ? 1 : -1;
                boolean step = open(ar, t, x, y - 1, o) && open(ar, t, x - side, y, o);
                Style.Mix mix = dressed ? s.trim : s.wall;
                if (step) m.stairs(x, y, z, s.trim.stairs(seed, x, y, z, 0), side > 0 ? "east" : "west", true);
                else m.set(x, y, z, mix.block(seed, x, y, z, 0));
            }
        }
        // String course at each ledge.
        if (t.top < 0) for (int x = 0; x < l; x++) {
            m.stairs(x, t.top - 1, z0 - 1, s.trim.stairs(seed, x, t.top - 1, z0 - 1, 0), "south", true);
            m.stairs(x, t.top - 1, z1 + 1, s.trim.stairs(seed, x, t.top - 1, z1 + 1, 0), "north", true);
        }
    }

    /** Rows of a cutwater's triangular plan, k = 1..depth out from the face. */
    private int depth(int[] pier) { return (pier[1] - pier[0] + 2) / 2; }

    /** Where the deck feature sits on a main pier: refuges need room for a recess. */
    private Cutwaters feature(int[] pier) {
        if (cut == Cutwaters.REFUGES && pier[1] - pier[0] + 1 < 5) return Cutwaters.PINNACLES;
        return cut;
    }

    private void cutwaters() {
        Tier bottom = tiers.get(0);
        boolean single = tiers.size() == 1;
        for (int[] pier : bottom.piers) {
            int dep = depth(pier);
            Cutwaters f = feature(pier);
            int springLow = Integer.MAX_VALUE;
            for (Arch a : bottom.arches) if (a.x1 + 1 == pier[0] || a.x0 - 1 == pier[1]) springLow = Math.min(springLow, a.spring);
            // A cutwater runs to the deck under a refuge or pinnacle; otherwise it is weathered off.
            int full = single && f != Cutwaters.CAPPED ? -2 : Math.min(springLow + 1, bottom.top - 2 - dep);
            for (int side : new int[]{-1, 1}) {
                for (int k = 1; k <= dep; k++) {
                    int zz = side < 0 ? z0 - k : z1 + k;
                    for (int x = pier[0] + k - 1; x <= pier[1] - k + 1; x++)
                        for (int y = bottom.base; y <= full; y++)
                            m.set(x, y, zz, (y == bottom.base ? s.plinth : s.wall).block(seed, x, y, zz, 0));
                }
                if (full == -2) continue;
                // Stepped weathering: each course drops the outermost row to a stair.
                for (int j = 1; j <= dep; j++) {
                    int y = full + j;
                    for (int k = 1; k <= dep - j + 1; k++) {
                        int zz = side < 0 ? z0 - k : z1 + k;
                        for (int x = pier[0] + k - 1; x <= pier[1] - k + 1; x++) {
                            if (k == dep - j + 1) m.stairs(x, y, zz, s.trim.stairs(seed, x, y, zz, 0), side < 0 ? "south" : "north", false);
                            else stone(x, y, zz, true);
                        }
                    }
                }
            }
        }
    }

    private boolean footway(int z) {
        if (!walk.footways() || w < 9) return false;
        int fw = w >= 13 ? 2 : 1;
        return z > z0 && z < z1 && (z <= z0 + fw || z >= z1 - fw);
    }

    private void deck() {
        for (int x = -1; x <= l; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = z == z0 || z == z1 || footway(z) || x < 0 || x >= l;
            m.set(x, -1, z, (edge ? s.trim : s.plinth).block(seed, x, -1, z, 0));
        }
        // Half-block terraces: slabs on the carriageway, a half-block kerb on footways.
        for (int x = -1; x <= l; x++) {
            boolean odd = Math.floorMod(halfRise(x), 2) == 1;
            for (int z = z0 + 1; z < z1; z++) {
                if (footway(z)) {
                    if (odd) stone(x, 0, z, true);
                    else m.slab(x, 0, z, s.trim.slab(seed, x, 0, z, 0), false);
                } else if (odd) m.slab(x, 0, z, s.plinth.slab(seed, x, 0, z, 0), false);
            }
        }
        for (int x = -1; x <= l; x++) for (int z : new int[]{z0, z1}) parapet(x, z);
        // Cornice under the parapets.
        for (int x = 0; x < l; x++) {
            m.stairs(x, -1, z0 - 1, s.trim.stairs(seed, x, -1, z0 - 1, 0), "south", true);
            m.stairs(x, -1, z1 + 1, s.trim.stairs(seed, x, -1, z1 + 1, 0), "north", true);
        }
        // End posts.
        for (int x : new int[]{-1, l}) for (int z : new int[]{z0, z1}) {
            stone(x, 0, z, true); stone(x, 1, z, true);
            m.set(x, 2, z, s.trim.wall(seed, x, 2, z, 0));
            m.set(x, 3, z, "minecraft:lantern", "hanging", "false");
        }
        // Abutment ends are dressed to ground.
        for (int x : new int[]{0, l - 1}) for (int z = z0; z <= z1; z++)
            for (int y = -h; y < -1; y++) if (!m.air(x, y, z)) stone(x, y, z, y == -h || z == z0 || z == z1);
    }

    /** Distance in from the nearer landing post, so patterns mirror about mid-span. */
    private int fromEnd(int x) { return Math.min(x + 1, l - x); }

    private void parapet(int x, int z) {
        int e = fromEnd(x);
        boolean firstHalf = x + 1 < l - x;
        switch (parapet) {
            case SOLID -> {
                stone(x, 0, z, false);
                m.slab(x, 1, z, s.trim.slab(seed, x, 1, z, 0), false);
            }
            case PIERCED -> {
                // An arcaded course over a solid kerb: pier, upturned stair, open light, upturned
                // stair. The stairs' backs bear on the piers, so each light is a stepped arch two
                // wide at the sill and one wide at the head, too low to climb through.
                stone(x, 0, z, false);
                int r = e % 4;
                if (r == 0) stone(x, 1, z, true);
                else if (r != 2) {
                    boolean towardEnd = r == 1;
                    String face = towardEnd == firstHalf ? "west" : "east";
                    m.stairs(x, 1, z, s.trim.stairs(seed, x, 1, z, 0), face, true);
                }
                m.slab(x, 2, z, s.trim.slab(seed, x, 2, z, 0), false);
            }
            case BATTLEMENTED -> {
                stone(x, 0, z, false);
                if (e % 3 != 2) {
                    stone(x, 1, z, false);
                    m.slab(x, 2, z, s.trim.slab(seed, x, 2, z, 0), false);
                } else m.slab(x, 1, z, s.trim.slab(seed, x, 1, z, 0), false);
            }
        }
    }

    /** Refuges or pinnacles at the deck over each main pier. */
    private void deckFeatures(Tier top) {
        boolean single = tiers.size() == 1;
        Tier bottom = tiers.get(0);
        for (int[] pier : bottom.piers) {
            Cutwaters f = feature(pier);
            if (f == Cutwaters.CAPPED) continue;
            int dep = depth(pier), mid = (pier[0] + pier[1]) / 2;
            for (int side : new int[]{-1, 1}) {
                int zFace = side < 0 ? z0 : z1;
                String out = side < 0 ? "north" : "south", in = side < 0 ? "south" : "north";
                if (!single) {
                    // Above the lowest tier a buttress strip climbs each face to the deck, and
                    // the projection is corbelled out from it.
                    for (int y = bottom.top; y <= -2; y++)
                        for (int x = pier[0] + 1; x <= pier[1] - 1; x++) stone(x, y, zFace + side, false);
                    for (int k = 2; k <= dep; k++) {
                        int zz = zFace + side * k;
                        for (int x = pier[0] + k - 1; x <= pier[1] - k + 1; x++)
                            m.stairs(x, -2, zz, s.trim.stairs(seed, x, -2, zz, 0), in, true);
                    }
                }
                for (int k = 1; k <= dep; k++) {
                    int zz = zFace + side * k;
                    for (int x = pier[0] + k - 1; x <= pier[1] - k + 1; x++) stone(x, -1, zz, true);
                }
                if (f == Cutwaters.REFUGES) {
                    // A pointed refuge: open the parapet and wall round the projection's edge.
                    for (int x = pier[0] + 1; x <= pier[1] - 1; x++) {
                        m.clear(x, 0, zFace); m.clear(x, 1, zFace); m.clear(x, 2, zFace);
                        if (Math.floorMod(halfRise(x), 2) == 1) m.slab(x, 0, zFace, s.plinth.slab(seed, x, 0, zFace, 0), false);
                    }
                    for (int k = 1; k <= dep; k++) {
                        int zz = zFace + side * k;
                        for (int x = pier[0] + k - 1; x <= pier[1] - k + 1; x++) {
                            boolean inside = k == 1 && x > pier[0] && x < pier[1];
                            if (inside) {
                                if (Math.floorMod(halfRise(x), 2) == 1) m.slab(x, 0, zz, s.plinth.slab(seed, x, 0, zz, 0), false);
                                continue;
                            }
                            // Refuge walls rise to the parapet's own coping height.
                            int cap = parapet == BridgeParapet.SOLID ? 1 : 2;
                            for (int y = 0; y < cap; y++) stone(x, y, zz, false);
                            m.slab(x, cap, zz, s.trim.slab(seed, x, cap, zz, 0), false);
                        }
                    }
                    int tip = zFace + side * dep;
                    m.set(mid, parapet == BridgeParapet.SOLID ? 2 : 3, tip, "minecraft:lantern", "hanging", "false");
                } else {
                    // A pinnacle: a stepped pedestal and a slender shaft over the cutwater point.
                    for (int k = 1; k <= dep; k++) {
                        int zz = zFace + side * k;
                        for (int x = pier[0] + k - 1; x <= pier[1] - k + 1; x++) {
                            boolean centre = x == mid && k <= 2;
                            if (centre) stone(x, 0, zz, true);
                            else m.stairs(x, 0, zz, s.trim.stairs(seed, x, 0, zz, 0), in, false);
                        }
                    }
                    int zz = zFace + side;
                    stone(mid, 1, zz, true);
                    stone(mid, 2, zz, true);
                    m.set(mid, 3, zz, s.trim.wall(seed, mid, 3, zz, 0));
                    m.set(mid, 4, zz, "minecraft:lantern", "hanging", "false");
                }
            }
        }
    }

    /** A walk along the top of tier {@code t}, through the piers of the tier above. */
    private void gallery(Tier t) {
        int g = t.top;
        Tier above = tiers.get(tiers.indexOf(t) + 1);
        List<int[]> tunnels = new ArrayList<>(above.piers);
        tunnels.add(new int[]{1, abut - 1});
        tunnels.add(new int[]{l - abut, l - 2});
        for (int[] r : tunnels) for (int x = r[0]; x <= r[1]; x++) for (int z = z0 + 1; z < z1; z++) {
            // Four courses: a half-block terrace still leaves two clear blocks under the corner stairs.
            for (int y = g; y <= g + 3; y++) m.clear(x, y, z);
            if (w > 5 && (z == z0 + 1 || z == z1 - 1))
                m.stairs(x, g + 3, z, s.trim.stairs(seed, x, g + 3, z, 0), z == z0 + 1 ? "north" : "south", true);
        }
        for (int x = 1; x <= l - 2; x++) {
            boolean odd = Math.floorMod(halfRise(x), 2) == 1;
            for (int z = z0 + 1; z < z1; z++) if (odd && m.air(x, g, z)) m.slab(x, g, z, s.trim.slab(seed, x, g, z, 0), false);
            for (int z : new int[]{z0, z1}) if (m.air(x, g, z)) {
                stone(x, g, z, false);
                m.slab(x, g + 1, z, s.trim.slab(seed, x, g + 1, z, 0), false);
            }
        }
    }

    // ---- Stair turrets -------------------------------------------------------------------------

    /** Ring cells round the newel, clockwise, starting at the bridge-side corner (canonical frame, bridge at -v). */
    private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

    /**
     * Spiral stairs at both ends on the downstream face. They are built after the bridge is turned,
     * square to the world grid, so the one-wide ring survives every heading; a short passage runs
     * from the ring through the turret wall and the bridge face to each walk.
     */
    private void turrets(Mason world, int heading) {
        if (tiers.stream().noneMatch(t -> t.gallery)) return;
        double a = Math.floorMod(heading, 16) * Math.PI / 8;
        for (int xc : new int[]{2, l - 3}) turret(world, Math.cos(a), Math.sin(a), xc);
    }

    private record Passage(List<int[]> cells, int lx) {}

    private Passage passage(int[] from, int[] d, double c, double sn, int xc) {
        List<int[]> cells = new ArrayList<>();
        int x = from[0], z = from[1];
        for (int step = 0; step < 8; step++) {
            x += d[0]; z += d[1];
            int lx = (int) Math.floor(c * x + sn * z + 0.5), lz = (int) Math.floor(-sn * x + c * z + 0.5);
            if (lz <= z1 - 1) return Math.abs(lx - xc) <= 3 ? new Passage(cells, lx) : null;
            cells.add(new int[]{x, z, lz});
        }
        return null;
    }

    private void turret(Mason world, double c, double sn, int xc) {
        int zl = z1 + 3;
        int cx = (int) Math.round(c * xc - sn * zl), cz = (int) Math.round(sn * xc + c * zl);
        // The bridge lies toward local -z: snap that to a grid axis and turn the ring to face it.
        double nx = sn, nz = -c;
        int[] d = Math.abs(nx) > Math.abs(nz) + 1e-9 ? new int[]{(int) Math.signum(nx), 0} : new int[]{0, (int) Math.signum(nz)};
        int q = d[1] == -1 ? 0 : d[0] == 1 ? 1 : d[1] == 1 ? 2 : 3;
        int[][] ring = new int[8][];
        for (int i = 0; i < 8; i++) {
            int u = RING[i][0], v = RING[i][1];
            for (int k = 0; k < q; k++) { int nu = -v; v = u; u = nu; }
            ring[i] = new int[]{cx + u, cz + v};
        }
        List<Integer> levels = new ArrayList<>();
        for (Tier t : tiers) if (t.gallery) levels.add(t.top);
        levels.add(0);
        Passage[] ways = new Passage[3];
        for (int i = 0; i < 3; i++) ways[i] = passage(ring[i], d, c, sn, xc);
        int[][] target = new int[levels.size()][3];
        for (int li = 0; li < levels.size(); li++) for (int i = 0; i < 3; i++)
            target[li][i] = ways[i] == null ? Integer.MIN_VALUE : 2 * levels.get(li) + halfRise(ways[i].lx);
        int start = ways[1] != null ? 1 : ways[0] != null ? 0 : 2;
        if (ways[start] == null) return;
        int base = Integer.MAX_VALUE;
        for (int x = xc - 3; x <= xc + 3; x++) base = Math.min(base, -h + deckBase(x));
        // Walk the ring: one half-block per cell, with evenly spread flat cells so each door
        // lands on a bridge-side cell. At least 6 half-blocks per turn keeps full headroom.
        List<int[]> path = new ArrayList<>();
        List<int[]> doors = new ArrayList<>();
        int idx = start, hh = target[0][start];
        path.add(new int[]{idx, hh});
        doors.add(new int[]{idx, hh});
        for (int li = 1; li < levels.size(); li++) {
            int[] chosen = null;
            for (int mm = 1; mm < 400 && chosen == null; mm++) {
                int ni = (idx + mm) % 8;
                if (ni > 2 || ways[ni] == null) continue;
                int tg = target[li][ni];
                for (int r : new int[]{tg - hh, tg - hh - 1, tg - hh + 1})
                    if (r >= 0 && r <= mm && 4 * (mm - r) <= mm) { chosen = new int[]{mm, r, tg}; break; }
            }
            if (chosen == null) throw new IllegalStateException("no stair fit");
            int mm = chosen[0], r = chosen[1];
            for (int j = 1; j <= mm; j++) path.add(new int[]{(idx + j) % 8, hh + (int) Math.floor(j * (double) r / mm)});
            idx = (idx + mm) % 8;
            hh += r;
            doors.add(new int[]{idx, chosen[2]});
        }
        int roof = Math.floorDiv(Math.max(hh, doors.getLast()[1]), 2) + 3;
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++)
            for (int y = base; y <= roof + 4; y++) world.clear(x, y, z);
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = cz - 2; z <= cz + 2; z++) {
            boolean wall = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2, newel = x == cx && z == cz;
            if (!wall && !newel) continue;
            boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
            for (int y = base; y <= roof; y++)
                world.set(x, y, z, (y == base ? s.plinth : corner || newel ? s.trim : s.wall).block(seed, x, y, z, 0));
        }
        boolean[] seen = new boolean[8];
        for (int[] step : path) {
            int[] at = ring[step[0]];
            int hs = step[1], y = Math.floorDiv(hs, 2);
            boolean odd = Math.floorMod(hs, 2) == 1;
            if (!seen[step[0]]) {
                seen[step[0]] = true;
                for (int yy = base; yy <= (odd ? y - 1 : y - 2); yy++) world.set(at[0], yy, at[1], s.wall.block(seed, at[0], yy, at[1], 0));
            }
            if (odd) world.slab(at[0], y, at[1], s.trim.slab(seed, at[0], y, at[1], 0), false);
            else world.slab(at[0], y - 1, at[1], s.trim.slab(seed, at[0], y - 1, at[1], 0), true);
        }
        // Ceiling, then a stepped pyramid cap in the roof stone with a finial: alike on every face.
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) world.set(x, roof, z, s.trim.block(seed, x, roof, z, 0));
        for (int ringN = 2; ringN >= 1; ringN--) {
            int y = roof + 3 - ringN;
            for (int x = cx - ringN; x <= cx + ringN; x++) for (int z = cz - ringN; z <= cz + ringN; z++) {
                int dx = x - cx, dz = z - cz;
                if (Math.max(Math.abs(dx), Math.abs(dz)) == ringN) {
                    String facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                    world.stairs(x, y, z, s.roof.stairs(seed, x, y, z, 0), facing, false);
                } else world.set(x, y, z, s.roof.block(seed, x, y, z, 0));
            }
        }
        world.set(cx, roof + 3, cz, s.trim.wall(seed, cx, roof + 3, cz, 0));
        // Slits on the three faces away from the bridge, one per turn of the stair.
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (o[0] == d[0] && o[1] == d[1]) continue;
            for (int y = base + 3; y < roof - 1; y += 5) {
                world.clear(cx + 2 * o[0], y, cz + 2 * o[1]);
                world.clear(cx + 2 * o[0], y + 1, cz + 2 * o[1]);
            }
        }
        // Doors: through the turret wall and the bridge face, floored at the walk they serve.
        for (int[] door : doors) {
            Passage pw = ways[door[0]];
            int hs = door[1], y = Math.floorDiv(hs, 2);
            boolean odd = Math.floorMod(hs, 2) == 1;
            for (int[] cell : pw.cells) {
                int x = cell[0], z = cell[1];
                if (odd) world.slab(x, y, z, s.trim.slab(seed, x, y, z, 0), false);
                else world.set(x, y - 1, z, s.trim.block(seed, x, y - 1, z, 0));
                for (int yy = odd ? y + 1 : y; yy <= y + 3; yy++) world.clear(x, yy, z);
                boolean outside = Math.abs(x - cx) > 2 || Math.abs(z - cz) > 2;
                if (outside && cell[2] > z1) {
                    // Bridging a gap between the turret and a turned face: wall it in.
                    for (int side : new int[]{-1, 1}) for (int yy = y; yy <= y + 3; yy++) {
                        int sx = x + side * d[1], sz = z + side * d[0];
                        if (world.air(sx, yy, sz)) world.set(sx, yy, sz, s.wall.block(seed, sx, yy, sz, 0));
                    }
                    if (world.air(x, y + 4, z)) world.set(x, y + 4, z, s.trim.block(seed, x, y + 4, z, 0));
                }
            }
        }
    }
}
