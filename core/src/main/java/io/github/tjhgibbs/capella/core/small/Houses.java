package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.raster.Volume;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Three domestic plans. The west threshold is the origin, with the floor immediately below.
 *
 * <p>Every roof (main range, wing, porch, dormer, bay) is registered first and raised together
 * from one combined height field, so where two roofs meet the lower one stops at the higher
 * one's surface. Valleys, dormer cheeks and wing junctions close without hand-placed patches.
 */
public final class Houses {
    private static final String FLOOR = "spruce_planks";
    private static final String POST = "stripped_dark_oak_log";
    private static final int[][] CARDINAL = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    private final Params.HouseRoof mainRoof;
    private final Params.HouseLayout layout;
    private final Mason m;
    private final boolean furnished;
    private final Params.HouseCladding cladding;
    private final int storeys;
    private final List<Roof> roofs = new ArrayList<>();
    private final List<int[]> rooms = new ArrayList<>();
    /** Roof cells placed so far, with the index of the roof that owns each. */
    private final Map<Long, Integer> roofCells = new HashMap<>();
    private String trapdoor = "spruce_trapdoor";
    private boolean shutters;

    private Houses(BuildingParams params) {
        mainRoof = params.get(Params.HOUSE_ROOF);
        layout = params.get(Params.HOUSE_LAYOUT);
        m = new Mason(params.seed());
        furnished = params.bool(Params.FURNISHINGS);
        cladding = params.get(Params.HOUSE_CLADDING);
        storeys = params.get(Params.HOUSE_STOREYS);
    }

    public static Volume build(BuildingParams params, int turns) {
        Houses house = new Houses(params);
        switch (params.get(Params.TYPE)) {
            case TOWNHOUSE -> house.townhouse();
            case COTTAGE -> house.cottage();
            case FARMHOUSE -> house.farmhouse();
            default -> throw new IllegalArgumentException("Expected a house");
        }
        return house.m.toVolume(turns);
    }

    // ------------------------------------------------------------------ roofs

    /**
     * A roof over the wall rectangle x0..x1, z0..z1 (odd across the ridge), with one block of
     * eave and verge beyond it. Open roofs (porches) have no walls under them: they hide nothing
     * and are never closed below.
     */
    private record Roof(int x0, int x1, int z0, int z1, int plate, boolean alongX,
                        Params.HouseRoof profile, String wood, String gable, boolean open, boolean kingPost) {
        int start() { return alongX ? x0 : z0; }
        int end() { return alongX ? x1 : z1; }
        int centre() { return alongX ? (z0 + z1) / 2 : (x0 + x1) / 2; }
        int radius() { return alongX ? (z1 - z0) / 2 : (x1 - x0) / 2; }
        boolean covers(int x, int z) { return x >= x0 - 1 && x <= x1 + 1 && z >= z0 - 1 && z <= z1 + 1; }
        boolean walls(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
        int cross(int x, int z) { return (alongX ? z : x) - centre(); }
        int run(int x, int z) { return alongX ? x : z; }

        /** Height of the profile line, before the ridge is capped. */
        int h(int x, int z) {
            if (!covers(x, z)) return Integer.MIN_VALUE;
            int d = radius() + 1 - Math.abs(cross(x, z));
            int r = run(x, z);
            int dEnd = Math.min(r - start() + 1, end() + 1 - r);
            return plate + switch (profile) {
                case GABLE -> d;
                case HIP -> Math.min(d, dEnd);
                case STEEP -> 2 * d;
                case GAMBREL -> d <= 2 ? 2 * d : 4 + (d - 2);
            };
        }

        /** No neighbour, diagonals included, stands higher. */
        boolean peak(int x, int z) {
            int h = h(x, z);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) if (h(x + dx, z + dz) > h) return false;
            return true;
        }

        /** The y of this roof's surface block in the column: a slab half a block over the slopes at a peak. */
        int top(int x, int z) {
            if (!covers(x, z)) return Integer.MIN_VALUE;
            int h = h(x, z);
            if (!peak(x, z)) return h;
            int lower = Integer.MIN_VALUE;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    int n = h(x + dx, z + dz);
                    if (n < h) lower = Math.max(lower, n);
                }
            return lower == Integer.MIN_VALUE ? h : lower + 1;
        }

        String stairs() { return wood + "_stairs"; }
        String slab() { return wood + "_slab"; }
        String backing() {
            return switch (wood) {
                case "deepslate_tile" -> "deepslate_tiles";
                case "bamboo_mosaic" -> "bamboo_mosaic";
                default -> wood + "_planks";
            };
        }
    }

    private Roof roof(int x0, int x1, int z0, int z1, int plate, boolean alongX, Params.HouseRoof profile,
                      String wood, String gable, boolean kingPost) {
        Roof r = new Roof(x0, x1, z0, z1, plate, alongX, profile, wood, gable, false, kingPost);
        roofs.add(r);
        return r;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 26) | ((long) (y + 512) << 14) | (z + 4096);
    }

    /** Another roof with walls under this column stands higher (ties go to the earlier roof). */
    private boolean shadowed(int i, int x, int z, int top) {
        for (int j = 0; j < roofs.size(); j++) {
            if (j == i) continue;
            Roof s = roofs.get(j);
            if (!s.walls(x, z)) continue;
            int t = s.top(x, z);
            if (t > top || (t == top && j < i)) return true;
        }
        return false;
    }

    /** An eave or verge of roof i overhanging into a building whose roof stands above i's plate there. */
    private boolean intrudes(int i, int x, int z) {
        Roof r = roofs.get(i);
        if (r.walls(x, z)) return false;
        for (int j = 0; j < roofs.size(); j++) {
            Roof s = roofs.get(j);
            if (j != i && !s.open() && s.walls(x, z) && s.top(x, z) >= r.plate()) return true;
        }
        return false;
    }

    /** Inside another walled roof's attic or rooms. */
    private boolean enclosed(int i, int x, int y, int z) {
        for (int j = 0; j < roofs.size(); j++) {
            Roof s = roofs.get(j);
            if (j != i && !s.open() && s.walls(x, z) && y <= s.top(x, z)) return true;
        }
        return false;
    }

    private void putRoof(int i, int x, int y, int z, String name, String... props) {
        long k = key(x, y, z);
        Integer owner = roofCells.get(k);
        if (owner == null) {
            if (!m.air(x, y, z)) return;
        } else if (owner != i) {
            // Two eaves meeting in the open: perpendicular slopes make a valley stair, opposed ones a gutter.
            var old = m.get(x, y, z);
            String oldFacing = old.prop("facing");
            String facing = null;
            for (int p = 0; p + 1 < props.length; p += 2) if (props[p].equals("facing")) facing = props[p + 1];
            if (oldFacing == null || facing == null) return;
            if (opposite(oldFacing).equals(facing)) {
                m.slab(x, y, z, roofs.get(i).slab(), false);
                roofCells.put(k, i);
                return;
            }
        }
        m.set(x, y, z, name, props);
        roofCells.put(k, i);
    }

    private static String opposite(String facing) {
        return switch (facing) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** Lays every registered roof, then closes gables and any gap between wall plate and roof. */
    private void raiseRoofs() {
        for (int i = 0; i < roofs.size(); i++) {
            Roof r = roofs.get(i);
            for (int x = r.x0() - 1; x <= r.x1() + 1; x++)
                for (int z = r.z0() - 1; z <= r.z1() + 1; z++) {
                    int top = r.top(x, z);
                    if (shadowed(i, x, z, top) || intrudes(i, x, z)) continue;
                    int below = Integer.MAX_VALUE;
                    for (int[] c : CARDINAL) {
                        int n = r.covers(x + c[0], z + c[1]) ? r.top(x + c[0], z + c[1]) : top - 1;
                        below = Math.min(below, n);
                    }
                    for (int y = below + 1; y < top; y++) putRoof(i, x, y, z, r.backing());
                    if (r.peak(x, z)) {
                        putRoof(i, x, top, z, r.slab(), "type", "bottom");
                        continue;
                    }
                    putRoof(i, x, top, z, r.stairs(), "facing", uphill(r, x, z), "half", "bottom");
                }
        }
        for (int i = 0; i < roofs.size(); i++) {
            Roof r = roofs.get(i);
            if (r.open()) continue;
            for (int x = r.x0(); x <= r.x1(); x++)
                for (int z = r.z0(); z <= r.z1(); z++) {
                    boolean edge = x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1();
                    if (!edge) continue;
                    int lid = Integer.MIN_VALUE;
                    for (int y = r.plate() + 1; y < r.plate() + 40; y++)
                        if (roofCells.containsKey(key(x, y, z))) { lid = y; break; }
                    if (lid == Integer.MIN_VALUE) continue;
                    boolean king = r.kingPost() && r.cross(x, z) == 0
                            && (r.run(x, z) == r.start() || r.run(x, z) == r.end());
                    for (int y = r.plate() + 1; y < lid; y++)
                        if (m.air(x, y, z) && !enclosed(i, x, y, z)) m.set(x, y, z, king ? POST : r.gable());
                }
        }
    }

    /** The cardinal direction toward higher roof (stairs face uphill); hip lines take the x component. */
    private static String uphill(Roof r, int x, int z) {
        int h = r.h(x, z), best = h;
        String facing = null;
        for (int[] c : CARDINAL) {
            int n = r.h(x + c[0], z + c[1]);
            if (n > best) { best = n; facing = Mason.dir(c[0], c[1]); }
        }
        if (facing != null) return facing;
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2)
                if (r.h(x + dx, z + dz) > h) return Mason.dir(dx, 0);
        return "north";
    }

    // ------------------------------------------------------------------ walls and openings

    private void box(int x0, int x1, int y0, int y1, int z0, int z1, String block, String... props) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) m.set(x, y, z, block, props);
    }

    /** Floor, perimeter walls with corner posts, and a continuous plate. Earlier walls are kept where they meet. */
    private void room(int x0, int x1, int z0, int z1, int floor, int plate, String wall) {
        rooms.add(new int[]{x0, x1, z0, z1, floor, plate});
        box(x0, x1, floor, floor, z0, z1, FLOOR);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean end = x == x0 || x == x1;
                boolean side = z == z0 || z == z1;
                if (!end && !side) continue;
                for (int y = floor + 1; y < plate; y++) m.setIfAir(x, y, z, end && side ? POST : wall);
                m.setIfAir(x, plate, z, POST, "axis", end ? "z" : "x");
            }
    }

    private boolean inRoom(int x, int y, int z) {
        for (int[] r : rooms)
            if (x >= r[0] && x <= r[1] && z >= r[2] && z <= r[3] && y >= r[4] && y <= r[5]) return true;
        return false;
    }

    private static int dx(String face) { return face.equals("east") ? 1 : face.equals("west") ? -1 : 0; }
    private static int dz(String face) { return face.equals("south") ? 1 : face.equals("north") ? -1 : 0; }

    /**
     * Panes in the wall facing {@code face}, running east or south from (x, z), with a thin
     * trapdoor sill and, on some houses, open shutters. Skipped if anything stands in front of it.
     */
    private void window(int x, int y, int z, String face, int width, int height) {
        window(x, y, z, face, width, height, true);
    }

    /** A small gable light: panes only, no sill or shutters. */
    private void light(int x, int y, int z, String face, int width, int height) {
        window(x, y, z, face, width, height, false);
    }

    private void window(int x, int y, int z, String face, int width, int height, boolean dressed) {
        int ox = dx(face), oz = dz(face);
        int ax = oz != 0 ? 1 : 0, az = ox != 0 ? 1 : 0;
        for (int i = 0; i < width; i++)
            for (int h = 0; h < height; h++) {
                int wx = x + ax * i + ox, wz = z + az * i + oz;
                if (!m.air(wx, y + h, wz) || inRoom(wx, y + h, wz)) return;
            }
        for (int i = 0; i < width; i++) {
            int wx = x + ax * i, wz = z + az * i;
            for (int h = 0; h < height; h++) m.set(wx, y + h, wz, "glass_pane");
            if (!dressed) continue;
            int sx = wx + ox, sz = wz + oz;
            if (m.air(sx, y - 1, sz))
                m.set(sx, y - 1, sz, trapdoor, "facing", face, "half", "top", "open", "false");
        }
        if (!shutters || !dressed) return;
        for (int side : new int[]{-1, width}) {
            int sx = x + ax * side + ox, sz = z + az * side + oz;
            boolean clear = true;
            for (int h = 0; h < height; h++) clear &= m.air(sx, y + h, sz);
            if (!clear) continue;
            for (int h = 0; h < height; h++)
                m.set(sx, y + h, sz, trapdoor, "facing", face, "half", h == 0 ? "bottom" : "top", "open", "true");
        }
    }

    private void door(int x, int z, String facing) {
        m.set(x, 0, z, "spruce_door", "facing", facing, "half", "lower");
        m.set(x, 1, z, "spruce_door", "facing", facing, "half", "upper");
    }

    /** A brick stack from the footing to two blocks over the roof it rises through, with a smoker at the hearth. */
    private void stack(int x, int z, String facing) {
        int top = 0;
        for (Roof r : roofs) if (r.covers(x, z)) top = Math.max(top, r.top(x, z));
        top += 2;
        box(x, x, -1, top, z, z, "bricks");
        m.set(x, 0, z, "smoker", "facing", facing);
        m.set(x, top + 1, z, "brick_wall");
    }

    private void bed(int x, int y, int z, String dye) {
        m.set(x, y, z, dye + "_bed", "part", "foot", "facing", "east");
        m.set(x + 1, y, z, dye + "_bed", "part", "head", "facing", "east");
    }

    /** A board table along x with a stair chair on each side. */
    private void table(int x, int y, int z, int length) {
        for (int i = 0; i < length; i++) {
            m.slab(x + i, y, z, "spruce_slab", true);
            m.stairs(x + i, y, z + 1, "spruce_stairs", "south", false);
            m.stairs(x + i, y, z - 1, "spruce_stairs", "north", false);
        }
    }

    private void hangingLantern(int x, int y, int z) {
        m.set(x, y, z, "lantern", "hanging", "true");
    }

    /** Straight flight east along z, with a trapdoor rail along the open side of the stairwell. */
    private void flight(int x, int z, int floor) {
        flight(x, z, 0, floor, 1);
    }

    /** From standing level {@code base}, {@code rise} steps up; the rail stands on the {@code side} (+1 south, -1 north). */
    private void flight(int x, int z, int base, int rise, int side) {
        for (int i = 0; i <= rise; i++) {
            for (int y = base; y < base + i; y++) m.set(x + i, y, z, FLOOR);
            m.stairs(x + i, base + i, z, "spruce_stairs", "east", false);
            if (i > 0 && i < rise) {
                m.clear(x + i, base + rise, z);
                m.set(x + i, base + rise + 1, z + side, trapdoor,
                        "facing", side > 0 ? "south" : "north", "half", "bottom", "open", "true");
            }
        }
    }

    // ------------------------------------------------------------------ garden wing

    private int wingLeft, wingRight, wingEnd;

    /** An offset garden wing, or a broad reception room with twin glazed bays, off the south wall. */
    private void wingFrame(int wallZ, int centre, int mainCentreZ, int plate, String wall, String roofWood, String gable) {
        if (layout == Params.HouseLayout.ORIGINAL) return;
        boolean manor = layout == Params.HouseLayout.MANOR;
        int radius = manor ? 4 : 3;
        wingLeft = centre - radius;
        wingRight = centre + radius;
        wingEnd = wallZ + (manor ? 6 : 10);
        room(wingLeft, wingRight, wallZ, wingEnd, -1, plate, wall);
        roof(wingLeft, wingRight, mainCentreZ, wingEnd, plate, false,
                manor ? Params.HouseRoof.HIP : Params.HouseRoof.GABLE, roofWood, gable, false);
        if (manor) for (int bay : new int[]{centre - 2, centre + 2}) {
            rooms.add(new int[]{bay - 1, bay + 1, wingEnd, wingEnd + 2, -1, plate});
            roof(bay - 1, bay + 1, wingEnd + 1, wingEnd + 2, plate, false, Params.HouseRoof.HIP, roofWood, gable, false);
        }
    }

    private void wingDetails(int wallZ, int centre, int plate, String wall) {
        if (layout == Params.HouseLayout.ORIGINAL) return;
        boolean manor = layout == Params.HouseLayout.MANOR;
        for (int x = centre - 1; x <= centre + 1; x++)
            for (int y = 0; y < plate; y++) m.clear(x, y, wallZ);
        if (manor) {
            window(wingLeft, 1, wallZ + 2, "west", 3, 2);
            window(wingRight, 1, wallZ + 2, "east", 3, 2);
            for (int bay : new int[]{centre - 2, centre + 2}) {
                for (int x = bay - 1; x <= bay + 1; x++) {
                    for (int y = 0; y < plate; y++) m.clear(x, y, wingEnd);
                    for (int z = wingEnd + 1; z <= wingEnd + 2; z++) {
                        m.set(x, -1, z, FLOOR);
                        boolean rim = x != bay || z == wingEnd + 2;
                        if (!rim) continue;
                        boolean corner = x != bay && z == wingEnd + 2;
                        m.set(x, 0, z, corner ? POST : wall);
                        box(x, x, 1, plate - 1, z, z, corner ? POST : "glass_pane");
                        m.set(x, plate, z, POST, "axis", z == wingEnd + 2 ? "x" : "z");
                    }
                }
            }
            door(centre, wingEnd, "south");
        } else {
            box(wingLeft, wingLeft, 0, plate - 1, wallZ + 5, wallZ + 5, POST);
            box(wingRight, wingRight, 0, plate - 1, wallZ + 5, wallZ + 5, POST);
            for (int z : new int[]{wallZ + 2, wallZ + 7}) {
                window(wingLeft, 1, z, "west", 2, 2);
                window(wingRight, 1, z, "east", 2, 2);
            }
            door(centre, wingEnd, "south");
            window(centre - 2, 1, wingEnd, "south", 1, 2);
            window(centre + 2, 1, wingEnd, "south", 1, 2);
            light(centre, plate + 2, wingEnd, "south", 1, 1);
        }
        if (furnished) {
            // Off the aisle that runs from the house to the garden door.
            int z = manor ? wallZ + 3 : wallZ + 6, x = manor ? centre - 3 : centre + 1;
            table(x, 0, z, 2);
            m.set(x, 1, z, "lantern");
            m.set(wingRight - 1, 0, wallZ + 1, "bookshelf");
            m.set(wingLeft + 1, 0, wallZ + 1, "bookshelf");
        }
    }

    // ------------------------------------------------------------------ houses

    /**
     * Stone shop storey; a timber-framed chamber floor jettied over the street on carved brackets;
     * a slate roof with the gable to the street. Posts stand on a four-block grid so every window
     * sits centred in its bay, above the shop window below it.
     */
    private void townhouse() {
        String plaster = "calcite";
        room(0, 14, -2, 6, -1, 3, "stone_bricks");
        room(-1, 14, -2, 6, 4, 9, plaster);
        roof(-1, 14, -2, 6, 9, true, mainRoof, "deepslate_tile", plaster, true);
        wingFrame(6, 7, 2, 3, "stone_bricks", "deepslate_tile", "stone_bricks");
        for (int x : new int[]{4, 9})
            for (int z : new int[]{-2, 6}) box(x, x, 5, 8, z, z, POST);
        for (int x : new int[]{-1, 14})
            for (int z : new int[]{1, 3}) box(x, x, 5, 8, z, z, POST);
        flight(3, -1, 4);
        // Rear chamber partition on the post line, open beside the stair head.
        box(9, 9, 5, 9, 1, 5, plaster);
        box(9, 9, 5, 9, 1, 1, POST);
        raiseRoofs();

        for (int z : new int[]{-2, 1, 3, 6}) m.stairs(-1, 3, z, "dark_oak_stairs", "east", true);
        for (int x : new int[]{1, 6, 11}) {
            window(x, 6, 6, "south", 2, 2);
            window(x, 6, -2, "north", 2, 2);
            window(x, 1, 6, "south", 2, 2);
        }
        window(1, 1, -2, "north", 2, 2);
        window(11, 1, -2, "north", 2, 2);
        for (int z : new int[]{-1, 4}) {
            window(-1, 6, z, "west", 2, 2);
            window(14, 6, z, "east", 2, 2);
            window(14, 1, z, "east", 2, 2);
        }
        if (mainRoof != Params.HouseRoof.HIP)
            for (int z : new int[]{1, 3}) {
                light(-1, 11, z, "west", 1, 2);
                light(14, 11, z, "east", 1, 2);
            }
        window(0, 1, 2, "west", 4, 2);
        door(0, 0, "west");
        hangingLantern(-1, 3, -1);
        hangingLantern(7, 3, 3);
        hangingLantern(11, 3, 3);
        stack(13, 2, "west");
        wingDetails(6, 7, 3, "stone_bricks");
        if (furnished) {
            for (int z = 3; z <= 5; z++) m.slab(3, 0, z, "spruce_slab", true);
            m.set(4, 0, 5, "barrel", "facing", "up");
            m.set(5, 0, 5, "barrel", "facing", "up");
            m.set(1, 0, 5, "crafting_table");
            table(10, 0, 4, 2);
            m.set(12, 0, 5, "barrel", "facing", "up");
            table(3, 5, 3, 3);
            m.set(4, 6, 3, "lantern");
            bed(11, 5, 5, "red");
            m.set(13, 5, 5, "barrel", "facing", "up");
            m.set(13, 6, 5, "lantern");
            m.set(13, 5, -1, "bookshelf");
            m.set(12, 5, -1, "bookshelf");
        }
    }

    /**
     * A single-storey limewashed cottage with shuttered windows, a central stack through the
     * ridge and a gabled porch over the west door.
     */
    private void cottage() {
        shutters = true;
        String wall = "calcite", thatch = "bamboo_mosaic";
        room(0, 10, -4, 4, -1, 3, wall);
        roof(0, 10, -4, 4, 3, true, mainRoof, thatch, wall, true);
        Roof porch = new Roof(-3, 0, -2, 2, 2, true, Params.HouseRoof.GABLE, thatch, FLOOR, true, false);
        roofs.add(porch);
        wingFrame(4, 5, 0, 3, wall, thatch, wall);
        // Sleeping alcove behind a plank screen on the stack's line.
        box(6, 6, 0, 3, 1, 3, FLOOR);
        raiseRoofs();

        // Porch: two posts carrying a tie beam and side plates, a boarded pediment, a flagged floor.
        box(-3, -1, -1, -1, -2, 2, "cobblestone");
        for (int z : new int[]{-2, 2}) {
            box(-3, -3, 0, 1, z, z, "stripped_spruce_log");
            box(-2, -1, 2, 2, z, z, "stripped_spruce_log", "axis", "x");
        }
        box(-3, -3, 2, 2, -2, 2, "stripped_spruce_log", "axis", "z");
        for (int z = -1; z <= 1; z++)
            for (int y = 3; y < porch.top(-3, z); y++) m.set(-3, y, z, FLOOR);
        hangingLantern(-2, 4, 0);
        door(0, 0, "west");
        for (int x : new int[]{2, 7}) {
            window(x, 1, 4, "south", 2, 2);
            window(x, 1, -4, "north", 2, 2);
        }
        window(0, 1, -2, "west", 1, 2);
        window(0, 1, 2, "west", 1, 2);
        window(10, 1, -1, "east", 3, 2);
        if (mainRoof != Params.HouseRoof.HIP) {
            light(10, 5, -1, "east", 1, 1);
            light(10, 5, 1, "east", 1, 1);
        }
        stack(6, 0, "west");
        wingDetails(4, 5, 3, wall);
        if (furnished) {
            table(2, 0, 2, 2);
            m.set(2, 1, 2, "lantern");
            box(3, 5, 0, 0, -2, -1, "green_carpet");
            m.set(1, 0, -3, "crafting_table");
            m.set(2, 0, -3, "barrel", "facing", "up");
            bed(8, 0, 2, "green");
            m.set(9, 0, 3, "barrel", "facing", "up");
            m.set(9, 1, 3, "lantern");
            m.set(9, 0, -3, "bookshelf");
        }
    }

    /**
     * A boarded farmhouse of two to four storeys: a long veranda on the street face, a gabled
     * dormer in the roof over the door, and a lower stone kitchen wing behind with its own stack
     * and yard door. Storeys are five blocks apart; flights alternate between the end walls.
     */
    private void farmhouse() {
        String wall = switch (cladding) {
            case PALE_OAK -> "pale_oak_planks";
            case BRICK -> "bricks";
            case SPRUCE -> "spruce_planks";
            case BIRCH -> "birch_planks";
        };
        trapdoor = "dark_oak_trapdoor";
        int plate = 5 * storeys - 2;
        for (int k = 0; k < storeys; k++) room(0, 10, -8, 8, 5 * k - 1, 5 * k + 3, wall);
        Roof main = roof(0, 10, -8, 8, plate, false, mainRoof, "dark_oak", wall, false);
        roof(0, 5, -2, 2, plate + 3, true, Params.HouseRoof.GABLE, "dark_oak", wall, false);
        room(10, 17, 0, 6, -1, 3, "cobblestone");
        roof(5, 17, 0, 6, 3, true, Params.HouseRoof.GABLE, "dark_oak", wall, false);
        wingFrame(8, 5, 0, 3, wall, "dark_oak", wall);
        for (int k = 0; k < storeys; k++) {
            int y = 5 * k;
            for (int z : new int[]{-4, 4})
                for (int x : new int[]{0, 10}) box(x, x, y, y + 2, z, z, POST);
            if (k < storeys - 1) flight(2, k % 2 == 0 ? -7 : 7, y, 4, k % 2 == 0 ? 1 : -1);
            if (k > 0) {
                // Bedrooms either side of a boarded partition, the door beside the stair heads.
                // Full height to the floor above (or the plate under the roof), with no slot under the ceiling.
                box(1, 7, y, y + 3, 0, 0, wall);
                box(7, 7, y, y + 3, 0, 0, POST);
            }
        }
        raiseRoofs();

        // Dormer: face and cheeks rise from wherever the main roof meets them.
        for (int x = 0; x <= 5; x++)
            for (int z = -2; z <= 2; z++) {
                if (x != 0 && Math.abs(z) != 2) continue;
                int base = x == 0 ? plate + 1 : main.top(x, z);
                if (base > plate + 3) continue;
                for (int y = Math.max(plate + 1, base); y <= plate + 2; y++) m.set(x, y, z, Math.abs(z) == 2 ? POST : wall);
                m.set(x, plate + 3, z, POST, "axis", x == 0 ? "z" : "x");
            }
        window(0, plate + 1, -1, "west", 3, 2);

        // Veranda along the street face.
        box(-3, -1, -1, -1, -7, 7, FLOOR);
        for (int z = -7; z <= 7; z++) {
            m.stairs(-1, 4, z, "dark_oak_stairs", "east", false);
            m.stairs(-2, 3, z, "dark_oak_stairs", "east", false);
            m.slab(-3, 3, z, "dark_oak_slab", false);
        }
        for (int z : new int[]{-7, -2, 2, 7}) box(-3, -3, 0, 2, z, z, POST);
        hangingLantern(-2, 2, -1);
        hangingLantern(-2, 2, 1);

        for (int k = 0; k < storeys; k++) {
            int y = 5 * k + 1;
            for (int z : new int[]{-6, -3, 2, 5}) {
                window(0, y, z, "west", 2, 2);
                if (k > 0) window(10, y, z, "east", 2, 2);
            }
            // End windows, except where this storey's flight climbs against that wall.
            boolean climbing = k < storeys - 1;
            if (k > 0 && !(climbing && k % 2 == 0)) window(4, y, -8, "north", 3, 2);
            if (k > 0 && !(climbing && k % 2 == 1)) window(4, y, 8, "south", 3, 2);
            if (k < storeys - 1) {
                hangingLantern(5, y + 2, -3);
                hangingLantern(5, y + 2, 4);
            }
        }
        window(10, 1, -6, "east", 2, 2);
        window(10, 1, -3, "east", 2, 2);
        window(8, 1, -8, "north", 2, 2);
        window(4, 1, 8, "south", 3, 2);
        window(12, 1, 6, "south", 2, 2);
        window(13, 1, 0, "north", 2, 2);
        door(0, 0, "west");
        door(10, 3, "east");
        door(17, 3, "east");
        stack(16, 5, "north");
        wingDetails(8, 5, 3, wall);
        if (furnished) {
            box(2, 7, 0, 0, -1, 0, "cyan_carpet");
            table(5, 0, 4, 2);
            m.stairs(3, 0, -4, "oak_stairs", "north", false);
            m.set(4, 0, -4, "bookshelf");
            m.set(4, 1, -4, "lantern");
            m.set(14, 0, 5, "crafting_table");
            m.set(14, 1, 5, "lantern");
            m.set(13, 0, 5, "barrel", "facing", "up");
            String[] dyes = {"yellow", "blue", "white", "orange", "light_blue", "lime"};
            for (int k = 1; k < storeys; k++) {
                int y = 5 * k;
                bed(8, y, -4, dyes[2 * k - 2]);
                bed(8, y, 4, dyes[2 * k - 1]);
                m.set(1, y, k % 2 == 0 ? -6 : 6, "barrel", "facing", "up");
                m.set(1, y + 1, k % 2 == 0 ? -6 : 6, "lantern");
                m.set(9, y, k % 2 == 0 ? 7 : -7, "barrel", "facing", "up");
                m.set(9, y + 1, k % 2 == 0 ? 7 : -7, "lantern");
            }
        }
    }
}
