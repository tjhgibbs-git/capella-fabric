package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.hash.Hash;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Seeded combat floors and a continuous stair promenade. All coordinates use the tower frame.
 * Floor levels are feet elevations, including the ground and fighting deck. Stair nodes specify
 * the supporting block; three cells above every node are reserved before decoration is placed.
 */
public final class TowerInterior {
    public record Step(int x, int y, int z, String facing) {
        public boolean stair() { return facing != null; }
    }
    public record Plan(List<Integer> floors, List<Step> route, int x0, int x1, int z0, int z1, int stairWidth) {}
    public record Cell(int x, int z) {}
    private TowerInterior() {}

    public static List<Integer> floorLevels(BuildingParams source) {
        BuildingParams p = source.copy();
        Params.clampSizes(p);
        int h = p.integer(Params.HEIGHT), spacing = p.integer(Params.FLOOR_HEIGHT);
        int count = Math.max(1, Math.min(h / 5, (int) Math.round(h / (double) spacing)));
        int[] gaps = new int[count];
        for (int i = 0; i < count; i++) gaps[i] = h / count + (i < h % count ? 1 : 0);
        if (p.bool(Params.VARY_FLOORS)) {
            for (int i = 0; i < count; i++) {
                int next = (i + 1) % count;
                int amount = 1 + (int) (Hash.hash(p.seed(), "floor-height", p.get(Params.TYPE).name(), i) * 2);
                if (gaps[i] - amount >= 5) { gaps[i] -= amount; gaps[next] += amount; }
            }
        }
        List<Integer> levels = new ArrayList<>();
        levels.add(0);
        int y = 0;
        for (int gap : gaps) { y += gap; levels.add(y); }
        return List.copyOf(levels);
    }

    public static Plan plan(BuildingParams source) {
        BuildingParams p = source.copy();
        Params.clampSizes(p);
        int w = p.integer(Params.WIDTH);
        int x0 = 4, x1 = w - 5, z0 = -w / 2 + 4, z1 = -w / 2 + w - 5;
        if (p.get(Params.TYPE) == Params.BuildingType.ROUND_WATCHTOWER) {
            double half = Math.floor((w / 2.0 - 3.5) / Math.sqrt(2));
            double cx = (w - 1) / 2.0, cz = -w / 2 + (w - 1) / 2.0;
            x0 = (int) Math.ceil(cx - half); x1 = (int) Math.floor(cx + half);
            z0 = (int) Math.ceil(cz - half); z1 = (int) Math.floor(cz + half);
        }
        int stairWidth = Math.min(x1 - x0, z1 - z0) >= 5 ? 2 : 1;
        List<int[]> ring = new ArrayList<>();
        for (int x = x0; x < x1; x++) ring.add(new int[]{x, z0});
        for (int z = z0; z < z1; z++) ring.add(new int[]{x1, z});
        for (int x = x1; x > x0; x--) ring.add(new int[]{x, z1});
        for (int z = z1; z > z0; z--) ring.add(new int[]{x0, z});
        List<Integer> floors = floorLevels(p);
        List<Step> route = new ArrayList<>();
        int cursor = (int) (Hash.hash(p.seed(), "stair-start", p.get(Params.TYPE).name()) * ring.size());
        int[] start = ring.get(cursor);
        route.add(new Step(start[0], -1, start[1], null));
        for (int level = 1; level < floors.size(); level++) {
            int feet = floors.get(level - 1), top = floors.get(level);
            while (feet < top) {
                int[] previous = ring.get(cursor % ring.size());
                int[] at = ring.get(++cursor % ring.size());
                boolean corner = (at[0] <= x0 + stairWidth - 1 || at[0] >= x1 - stairWidth + 1)
                        && (at[1] <= z0 + stairWidth - 1 || at[1] >= z1 - stairWidth + 1);
                if (corner) route.add(new Step(at[0], feet - 1, at[1], null));
                else {
                    route.add(new Step(at[0], feet, at[1], Mason.dir(at[0] - previous[0], at[1] - previous[1])));
                    feet++;
                }
            }
            // Two flat landing cells join each floor before the next flight turns away.
            for (int i = 0; i < 2; i++) {
                int[] at = ring.get(++cursor % ring.size());
                route.add(new Step(at[0], top - 1, at[1], null));
            }
        }
        return new Plan(floors, List.copyOf(route), x0, x1, z0, z1, stairWidth);
    }

    /** Full landing/flight footprint, including the inside lane around a turn. */
    public static List<Cell> footprint(Plan p, Step s) {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell(s.x, s.z));
        if (p.stairWidth == 1) return cells;
        int dx = s.x == p.x0 ? 1 : s.x == p.x1 ? -1 : 0;
        int dz = s.z == p.z0 ? 1 : s.z == p.z1 ? -1 : 0;
        if (dx != 0) cells.add(new Cell(s.x + dx, s.z));
        if (dz != 0) cells.add(new Cell(s.x, s.z + dz));
        if (dx != 0 && dz != 0) cells.add(new Cell(s.x + dx, s.z + dz));
        return cells;
    }

    static boolean room(BuildingParams p, int x, int z) {
        int w = p.integer(Params.WIDTH), z0 = -w / 2;
        if (x < 3 || x >= w - 3 || z < z0 + 3 || z >= z0 + w - 3) return false;
        if (p.get(Params.TYPE) != Params.BuildingType.ROUND_WATCHTOWER) return true;
        double dx = x - (w - 1) / 2.0, dz = z - (z0 + (w - 1) / 2.0);
        return dx * dx + dz * dz <= Math.pow(w / 2.0 - 3, 2);
    }

    static void build(Mason m, Style style, BuildingParams p) {
        Plan plan = plan(p);
        int w = p.integer(Params.WIDTH), h = p.integer(Params.HEIGHT), z0 = -w / 2;
        int cx = w / 2, cz = 0;
        // Replace the old regular floor stack, keeping every exterior detail and the roof intact.
        for (int x = 3; x < w - 3; x++) for (int z = z0 + 3; z < z0 + w - 3; z++) {
            if (!room(p, x, z)) continue;
            for (int y = 0; y < h; y++) m.clear(x, y, z);
        }
        int span = Math.min(plan.x1 - plan.x0, plan.z1 - plan.z0);
        int well = w >= 19 ? Math.min(Math.max(2, span / 4), Math.max(0, span / 2 - plan.stairWidth - 2)) : 0;
        if (well < 2) well = 0; // Keep a complete circulation lane beside wider flights in compact rooms.
        for (int floor = 0; floor < plan.floors.size(); floor++) {
            int feet = plan.floors.get(floor);
            boolean gallery = well > 0 && floor > 0 && floor < plan.floors.size() - 1 && floor % 2 == 1;
            for (int x = 3; x < w - 3; x++) for (int z = z0 + 3; z < z0 + w - 3; z++) {
                if (!room(p, x, z)) continue;
                boolean opening = gallery && Math.abs(x - cx) <= well && Math.abs(z - cz) <= well && Math.abs(z - cz) > 1;
                if (opening) continue;
                boolean border = !room(p, x + 1, z) || !room(p, x - 1, z) || !room(p, x, z - 1) || !room(p, x, z + 1);
                String material = floor == 0 || floor == plan.floors.size() - 1 || border
                        ? style.floorBorder.block(p.seed(), x, feet, z, 0) : style.timber;
                m.set(x, feet - 1, z, material);
                // Stone bridge, timber galleries: the colour change reads the crossing from below.
                if (gallery && Math.abs(z) <= 1 && Math.abs(x - cx) <= well + 1)
                    m.set(x, feet - 1, z, style.floorAccent.block(p.seed(), x, feet, z, 0));
                if (gallery && !reserved(plan, x, z) &&
                        (Math.abs(x - cx) == well + 1 && Math.abs(z) >= 2 && Math.abs(z) <= well
                        || Math.abs(z) == well + 1 && Math.abs(x - cx) <= well
                        || Math.abs(z) == 1 && Math.abs(x - cx) <= well))
                    m.set(x, feet, z, style.timberFence);
            }
            if (p.bool(Params.FURNISHINGS) && floor < plan.floors.size() - 1)
                furnish(m, style, p, plan, floor);
        }
        if (p.get(Params.TYPE) == Params.BuildingType.SQUARE_KEEP) keepRooms(m, style, p, plan);
        if (p.bool(Params.ACCESS)) stairs(m, style, plan);
        // A clear three-wide ground approach connects the west portal to the first stair ring.
        for (int x = 3; x <= cx; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y < 3; y++)
            if (!isStep(plan, x, y, z)) m.clear(x, y, z);
    }

    /** The corner drums are usable firing rooms, with level doorways back into the keep. */
    private static void keepRooms(Mason m, Style style, BuildingParams p, Plan plan) {
        int w = p.integer(Params.WIDTH), h = p.integer(Params.HEIGHT), radius = w >= 21 ? 3 : 2;
        int z0 = -w / 2;
        for (int cx : new int[]{1, w - 2}) for (int cz : new int[]{z0 + 1, z0 + w - 2}) {
            for (int x = cx - radius + 1; x < cx + radius; x++) for (int z = cz - radius + 1; z < cz + radius; z++) {
                for (int y = 0; y < h + 2; y++) m.clear(x, y, z);
                for (int feet : plan.floors) m.set(x, feet - 1, z, style.timber);
            }
            int exitX = cx < w / 2 ? 4 : w - 5, exitZ = cz < 0 ? z0 + 4 : z0 + w - 5;
            List<int[]> door = new ArrayList<>();
            for (int x = Math.min(cx, exitX); x <= Math.max(cx, exitX); x++) door.add(new int[]{x, cz});
            for (int z = Math.min(cz, exitZ); z <= Math.max(cz, exitZ); z++) door.add(new int[]{exitX, z});
            for (int feet : plan.floors) for (int[] cell : door) {
                m.set(cell[0], feet - 1, cell[1], style.timber);
                for (int dy = 0; dy < 3; dy++) m.clear(cell[0], feet + dy, cell[1]);
            }
            if (p.bool(Params.ACCESS)) {
                for (int y = h; y <= h + Fortification.KEEP_TURRET_RISE; y++)
                    m.set(cx, y, cz - radius + 1, "minecraft:ladder", "facing", "south");
            }
            if (p.bool(Params.FURNISHINGS)) for (int i = 1; i < plan.floors.size(); i++) {
                int ceiling = plan.floors.get(i) - 1;
                m.set(cx, ceiling - 1, cz, "minecraft:lantern", "hanging", "true");
            }
        }
    }

    /** Cells retained by the shell-free review, including the keep's corner firing rooms. */
    public static boolean interiorCell(BuildingParams p, int x, int z) {
        if (room(p, x, z) || x == 3 && z == 2) return true;
        if (p.get(Params.TYPE) != Params.BuildingType.SQUARE_KEEP) return false;
        int w = p.integer(Params.WIDTH), r = w >= 21 ? 3 : 2, z0 = -w / 2;
        for (int cx : new int[]{1, w - 2}) for (int cz : new int[]{z0 + 1, z0 + w - 2})
            if (Math.abs(x - cx) < r && Math.abs(z - cz) < r) return true;
        return false;
    }

    private static boolean isStep(Plan p, int x, int y, int z) {
        return p.route.stream().anyMatch(s -> s.y == y && footprint(p, s).contains(new Cell(x, z)));
    }

    private static boolean reserved(Plan p, int x, int z) {
        boolean onRing = x >= p.x0 - 1 && x <= p.x1 + 1 && z >= p.z0 - 1 && z <= p.z1 + 1
                && (Math.abs(x - p.x0) <= p.stairWidth || Math.abs(x - p.x1) <= p.stairWidth
                || Math.abs(z - p.z0) <= p.stairWidth || Math.abs(z - p.z1) <= p.stairWidth);
        return onRing || x <= 4 && Math.abs(z - 2) <= 1;
    }

    private static void furnish(Mason m, Style style, BuildingParams p, Plan plan, int floor) {
        int feet = plan.floors.get(floor), upper = plan.floors.get(floor + 1);
        int w = p.integer(Params.WIDTH), cx = w / 2;
        // Opposed, staggered cover leaves the central axes and the stair promenade open.
        int shift = Hash.hash(p.seed(), "cover-side", floor, p.get(Params.TYPE).name()) < .5 ? -1 : 1;
        for (int[] anchor : new int[][]{{cx - 2, 2 * shift}, {cx + 2, -2 * shift}, {cx, 4 * shift}}) {
            int x = anchor[0], z = anchor[1];
            if (!room(p, x, z) || reserved(plan, x, z) || m.air(x, feet - 1, z)) continue;
            if (floor == 0 && Math.abs(z) <= 1) continue;
            m.set(x, feet, z, "minecraft:barrel", "facing", "up", "open", "false");
            boolean tall = Hash.hash(p.seed(), "high-cover", floor, x) < .6;
            if (tall) m.set(x, feet + 1, z, floor % 2 == 0 ? style.timber : "minecraft:bookshelf");
            else m.set(x, feet + 1, z, "minecraft:lantern", "hanging", "false");
            // A short stone barricade makes a flanking lane beside the supply stack.
            int bx = x + (x < cx ? -1 : 1);
            if (room(p, bx, z) && !reserved(plan, bx, z) && !m.air(bx, feet - 1, z))
                m.set(bx, feet, z, style.trim.block(p.seed(), bx, feet, z, 0));
            // An L-shaped barricade can be flanked from either end; the tall end breaks sightlines.
            int bz = z + (z < 0 ? -1 : 1);
            if (room(p, x, bz) && !reserved(plan, x, bz) && !m.air(x, feet - 1, bz)) {
                m.set(x, feet, bz, style.timber);
                m.slab(x, feet + 1, bz, style.timberSlab, false);
            }
        }
        // Supported ceiling beams and warm lighting outside all ascent paths.
        for (int x = 3; x < w - 3; x++) {
            if (room(p, x, 0) && !reserved(plan, x, 0) && !m.air(x, upper - 1, 0))
                m.set(x, upper - 2, 0, style.timberLog, "axis", "x");
        }
        if (!reserved(plan, cx, 0) && !m.air(cx, upper - 2, 0))
            m.set(cx, upper - 3, 0, "minecraft:lantern", "hanging", "true");
    }

    private static void stairs(Mason m, Style style, Plan p) {
        // Carve both lanes and every landing through the completed pillars and furnishings.
        // Supporting posts for higher flights must also respect this reserved clearance.
        record Voxel(int x, int y, int z) {}
        Set<Voxel> clearance = new HashSet<>();
        for (Step s : p.route) for (Cell c : footprint(p, s)) for (int y = s.y + 1; y <= s.y + 3; y++) {
            clearance.add(new Voxel(c.x, y, c.z));
            m.clear(c.x, y, c.z);
        }
        for (Step s : p.route) for (Cell c : footprint(p, s)) {
            if (s.stair()) {
                m.stairs(c.x, s.y, c.z, style.timberStairs, s.facing, false);
                if (!clearance.contains(new Voxel(c.x, s.y - 1, c.z)))
                    m.setIfAir(c.x, s.y - 1, c.z, style.timber);
            } else {
                m.set(c.x, s.y, c.z, style.timber);
                int bottom = 0;
                for (int y : p.floors) if (y <= s.y) bottom = y;
                for (int y = bottom; y < s.y; y++) if (!clearance.contains(new Voxel(c.x, y, c.z)))
                    m.setIfAir(c.x, y, c.z, style.timberLog, "axis", "y");
            }
        }
        for (Step s : p.route) {
            if (!s.stair() || p.floors.stream().anyMatch(y -> Math.abs(y - s.y - 1) <= 1)) continue;
            int rx = s.x, rz = s.z;
            if (s.x == p.x0) rx--; else if (s.x == p.x1) rx++;
            else if (s.z == p.z0) rz--; else if (s.z == p.z1) rz++;
            if (rx == s.x && rz == s.z) continue;
            m.setIfAir(rx, s.y, rz, style.timber);
            m.setIfAir(rx, s.y + 1, rz, style.timberFence);
        }
    }
}
