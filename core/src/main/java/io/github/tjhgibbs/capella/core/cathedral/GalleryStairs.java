package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import io.github.tjhgibbs.capella.core.detail.Detailer;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.raster.RasterPass;
import io.github.tjhgibbs.capella.core.raster.Volume;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Protected switchback stairs joining the ground floor to the aisle galleries. */
public final class GalleryStairs {
    private final Volume v;
    private final PlanLayout layout;
    private final Detailer.Kit kit;
    private final int floorY;

    private GalleryStairs(Volume v, PlanLayout layout, Detailer.Kit kit) {
        this.v = v;
        this.layout = layout;
        this.kit = kit;
        this.floorY = (int) layout.levels.floorY;
    }

    public static void build(Volume v, PlanLayout layout, Detailer.Kit kit) {
        new GalleryStairs(v, layout, kit).galleryStairs();
    }

    private int[] at(int x, int y, int z) {
        Vec3 p = layout.frame.point(new Vec3(x, y, z));
        return new int[]{(int) Math.round(p.x()), y, (int) Math.round(p.z())};
    }

    private boolean air(int x, int y, int z) {
        int[] p = at(x, y, z);
        return v.contains(p[0], p[1], p[2]) && v.isAirAt(p[0], p[1], p[2]);
    }

    private void put(int x, int y, int z, String name, String... props) {
        int[] p = at(x, y, z);
        if (!v.contains(p[0], p[1], p[2])) return;
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < props.length; i += 2) m.put(props[i], props[i + 1]);
        v.set(p[0], p[1], p[2], Blockstates.rotateState(Blockstates.makeState(name, m), layout.frame.turns(), false), RasterPass.DECORATION);
    }

    private String stone() { return kit.courseBlock(); }
    private String stoneStairs() { return kit.courseStairs(); }
    private boolean naveBay(Bay b) { return b.kind == Bay.Kind.NAVE && !b.raised; }

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
        // Confine the stair to the aisle; never clear its outer wall or neighboring bay.
        for (int x = xs; x <= xe; x++) {
            for (int k = 0; k <= 5; k++) {
                for (int y = floorY; y < deckY; y++) clear(x, y, zw + k * in);
            }
        }
        for (int x = xs + 1; x < xe; x++) {
            for (int y = floorY; y < deckY; y++) put(x, y, spine, kit.plinthBlock());
        }
        // An aisle-facing enclosure supports the exposed inner flight and protects its edge.
        // The two-wide ground-floor doorway is below the returning flight, not behind a wall.
        int inner = zw + 5 * in;
        for (int x = xs; x <= xe; x++) {
            for (int y = floorY; y < deckY; y++) {
                if (x <= xs + 1 && y < floorY + 3) continue;
                boolean grille = x > xs + 1 && x < xe && (x - xs) % 3 == 0
                        && Math.floorMod(y - floorY, run) >= 3 && Math.floorMod(y - floorY, run) <= 5;
                put(x, y, inner, grille ? "minecraft:iron_bars" : stone());
            }
        }

        int t = floorY;
        boolean east = true;
        int flight = 0;
        java.util.Set<Long> open = new java.util.HashSet<>();
        java.util.Set<Long> arrival = new java.util.HashSet<>();
        while (t < deckY) {
            int[] lane = flight % 2 == 0 ? laneA : laneB;
            for (int i = 0; i < run && t < deckY; i++, t++) {
                int x = east ? xs + 1 + i : xe - 1 - i;
                for (int z : lane) {
                    put(x, t, z, stoneStairs(), "facing", east ? "east" : "west", "half", "bottom");
                    if (t > floorY) put(x, t - 1, z, stone());
                    // Keep the real hole only where a player needs it, not above the final tread.
                    if (t >= deckY - 4 && t < deckY - 1) open.add(column(x, z));
                    if (t == deckY - 1) arrival.add(column(x, z));
                }
            }
            if (t >= deckY) break;
            int lx = east ? xe : xs;
            int top = t - 1;
            for (int k = 0; k <= 4; k++) {
                int z = zw + k * in;
                put(lx, top - 1, z, stone());
                put(lx, top, z, kit.quoin());
                if (top >= deckY - 4) open.add(column(lx, z));
            }
            // Every turn is lit from a bracket anchored to the solid central spine.
            if (top + 3 < deckY) {
                put(lx, top + 3, spine, "minecraft:lantern", "hanging", "true");
                put(lx, top + 4, spine, stone());
            }
            east = !east;
            flight++;
        }
        int exitX = east ? xs + 2 + ((deckY - floorY - 1) % run)
                : xe - 2 - ((deckY - floorY - 1) % run);
        int[] finalLane = flight % 2 == 0 ? laneA : laneB;
        // A partial final flight needs a bridge all the way out of its shaft. Merely placing
        // one arrival block can strand it behind the remaining staircase or a roof rib.
        int endX = east ? xe + 1 : xs - 1;
        for (int x = exitX; east ? x <= endX : x >= endX; x += east ? 1 : -1) {
            for (int z : finalLane) {
                arrival.add(column(x, z));
                put(x, deckY - 1, z, stone());
                for (int y = deckY; y <= deckY + 2; y++) clear(x, y, z);
            }
        }
        // Rebuild a complete gallery floor around the aperture. The old implementation restored
        // only previously occupied cells, leaving raw vault holes alongside its landing.
        for (int x = xs; x <= xe; x++) {
            for (int k = 0; k <= 5; k++) {
                int z = zw + k * in;
                if (!open.contains(column(x, z))) put(x, deckY - 1, z, stone());
                else for (int y = deckY; y <= deckY + 2; y++) clear(x, y, z);
            }
        }
        for (long c : arrival) {
            int x = (int) (c >> 32), z = (int) c;
            for (int y = deckY; y <= deckY + 2; y++) clear(x, y, z);
        }
        // Guard the aperture on actual supported deck cells, keeping the arrival route open.
        for (long c : open) {
            int x = (int) (c >> 32), z = (int) c;
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int nx = x + d[0], nz = z + d[1];
                long nc = column(nx, nz);
                if (open.contains(nc) || arrival.contains(nc) || air(nx, deckY - 1, nz)) continue;
                if (air(nx, deckY, nz)) put(nx, deckY, nz, kit.shaftWall());
            }
        }
        for (int x = xs + 1; x < xe; x++) {
            if (air(x, deckY, inner)) put(x, deckY, inner, kit.shaftWall());
        }
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private void clear(int x, int y, int z) {
        int[] p = at(x, y, z);
        v.setId(p[0], p[1], p[2], 0, RasterPass.DECORATION);
    }

}
