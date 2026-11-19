package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.raster.Volume;

/** Three domestic plans. The west threshold is the origin, with the floor immediately below. */
public final class Houses {
    private static final String POST = "stripped_dark_oak_log";
    private static final String FLOOR = "spruce_planks";
    private static final String PLASTER = "white_terracotta";
    private final Params.HouseRoof mainRoof;
    private final Params.HouseLayout layout;
    private final Mason m;
    private final boolean furnished;

    private Houses(BuildingParams params) {
        mainRoof = params.get(Params.HOUSE_ROOF);
        layout = params.get(Params.HOUSE_LAYOUT);
        m = new Mason(params.seed());
        furnished = params.bool(Params.FURNISHINGS);
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

    private void box(int x0, int x1, int y0, int y1, int z0, int z1, String block) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) m.set(x, y, z, block);
    }

    /** Perimeter only, with posts at the corners and a continuous plate at the eaves. */
    private void room(int x0, int x1, int z0, int z1, int floor, int plate, String wall) {
        box(x0, x1, floor, floor, z0, z1, FLOOR);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean end = x == x0 || x == x1;
            boolean side = z == z0 || z == z1;
            if (!end && !side) continue;
            for (int y = floor + 1; y < plate; y++)
                m.set(x, y, z, end && side ? POST : wall);
            m.set(x, plate, z, POST, "axis", end ? "z" : "x");
        }
    }

    /** A stair pitched roof with closed gables and one block of verge and eave. */
    private void roof(int start, int end, int centre, int radius, int plate,
                      boolean ridgeAlongX, String wood, String gable) {
        roof(start, end, centre, radius, plate, ridgeAlongX, wood, gable, Params.HouseRoof.GABLE);
    }

    private void roof(int start, int end, int centre, int radius, int plate,
                      boolean ridgeAlongX, String wood, String gable, Params.HouseRoof profile) {
        String backing = switch (wood) {
            case "deepslate_tile" -> "deepslate_tiles";
            case "bamboo_mosaic" -> "bamboo_mosaic";
            default -> wood + "_planks";
        };
        for (int run = start - 1; run <= end + 1; run++) {
            for (int cross = -radius - 1; cross <= radius + 1; cross++) {
                int x = ridgeAlongX ? run : centre + cross;
                int z = ridgeAlongX ? centre + cross : run;
                int inset = radius + 1 - Math.abs(cross);
                int endInset = Math.min(run - start + 1, end + 1 - run);
                boolean endSlope = profile == Params.HouseRoof.HIP && endInset < inset;
                int rise = switch (profile) {
                    case HIP -> Math.min(inset, endInset);
                    case STEEP -> inset + inset / 2;
                    case GAMBREL -> 2 * Math.min(inset, 2) + Math.max(0, inset - 2) / 2;
                    case GABLE -> inset;
                };
                int y = plate + rise;
                if (run == start || run == end) {
                    for (int fill = plate + 1; fill < y; fill++)
                        m.set(x, fill, z, cross == 0 ? POST : gable);
                }
                // A full course seals the vertical riser where adjacent stairs are two levels apart.
                if (inset > 0 && (profile == Params.HouseRoof.STEEP || profile == Params.HouseRoof.GAMBREL)) {
                    m.set(x, y - 1, z, backing);
                }
                if (profile == Params.HouseRoof.GAMBREL && inset >= 2) {
                    if (inset % 2 == 0) m.slab(x, y, z, wood + "_slab", false);
                    else m.set(x, y, z, backing);
                } else if (cross == 0 && !endSlope) m.slab(x, y, z, wood + "_slab", false);
                else {
                    String facing = ridgeAlongX ? (cross < 0 ? "south" : "north") : (cross < 0 ? "east" : "west");
                    boolean lowEnd = run - start < end - run;
                    if (endSlope) facing = ridgeAlongX ? (lowEnd ? "east" : "west") : (lowEnd ? "south" : "north");
                    if (profile == Params.HouseRoof.HIP && endInset == inset) {
                        boolean left = ridgeAlongX ? lowEnd == (cross < 0) : lowEnd != (cross < 0);
                        m.set(x, y, z, wood + "_stairs", "facing", facing, "shape", left ? "outer_left" : "outer_right");
                    } else m.stairs(x, y, z, wood + "_stairs", facing, false);
                }
            }
        }
    }

    private void door(int x, int z, String facing) {
        m.set(x, 0, z, "spruce_door", "facing", facing, "half", "lower");
        m.set(x, 1, z, "spruce_door", "facing", facing, "half", "upper");
    }

    private void casement(int x, int y, int z, boolean west, int width) {
        for (int i = 0; i < width; i++) {
            int wx = west ? x : x + i;
            int wz = west ? z + i : z;
            for (int h = 0; h < 2; h++) m.set(wx, y + h, wz, "glass_pane");
            m.slab(west ? wx - 1 : wx, y - 1, west ? wz : wz + 1, "spruce_slab", true);
        }
    }

    private void stack(int x, int z, int top, String facing) {
        box(x, x, -1, top, z, z, "bricks");
        m.set(x, 0, z, "smoker", "facing", facing);
        m.set(x, top + 1, z, "brick_wall");
    }

    private void bed(int x, int y, int z, String dye) {
        m.set(x, y, z, dye + "_bed", "part", "foot", "facing", "east");
        m.set(x + 1, y, z, dye + "_bed", "part", "head", "facing", "east");
    }

    private void table(int x, int y, int z) {
        m.set(x, y, z, "spruce_fence");
        m.set(x, y + 1, z, "spruce_pressure_plate");
        m.stairs(x, y, z + 1, "spruce_stairs", "south", false);
    }

    /** Straight flight with three blocks of headroom, an open landing and an inner balustrade. */
    private void flight(int x, int z, int floor) {
        for (int i = 0; i <= floor; i++) {
            for (int y = 0; y < i; y++) m.set(x + i, y, z, FLOOR);
            m.stairs(x + i, i, z, "spruce_stairs", "east", false);
            if (i > 0 && i < floor) m.clear(x + i, floor, z);
            if (i > 0) m.set(x + i, floor + 1, z + 1, "spruce_fence");
        }
    }

    /** An offset garden wing or a broad reception room with twin projecting glazed bays. */
    private void extension(int wallZ, int centre, String wall, String roofWood) {
        if (layout == Params.HouseLayout.ORIGINAL) return;
        boolean manor = layout == Params.HouseLayout.MANOR;
        int radius = manor ? 4 : 3;
        int end = wallZ + (manor ? 6 : 9);
        int left = centre - radius, right = centre + radius;
        // Remove the former exterior sills and planting along the party wall.
        for (int x = left; x <= right; x++)
            for (int y = -1; y <= 2; y++) m.clear(x, y, wallZ + 1);
        room(left, right, wallZ, end, -1, 3, wall);
        roof(wallZ + 1, end, centre, radius, 3, false, roofWood, wall,
                manor ? Params.HouseRoof.HIP : Params.HouseRoof.GABLE);
        for (int x = centre - 1; x <= centre + 1; x++)
            for (int y = 0; y <= 2; y++) m.clear(x, y, wallZ);
        casement(left, 1, wallZ + 2, true, 2);
        box(right, right, 1, 2, wallZ + 2, wallZ + 3, "glass_pane");
        // Frame each garden elevation in shorter bays, with a projecting sill below the glass.
        for (int side : new int[]{left, right}) {
            box(side, side, 0, 2, wallZ + 4, wallZ + 4, POST);
            if (!manor) box(side, side, 1, 2, end - 3, end - 2, "glass_pane");
        }
        if (manor) {
            for (int bay : new int[]{centre - 2, centre + 2}) {
                box(bay - 1, bay + 1, -1, -1, end, end + 2, "stone_bricks");
                for (int x = bay - 1; x <= bay + 1; x++) {
                    for (int y = 0; y <= 2; y++) m.clear(x, y, end);
                    boolean corner = x != bay;
                    if (corner) {
                        m.set(x, 0, end + 1, FLOOR);
                        box(x, x, 1, 2, end + 1, end + 1, "glass_pane");
                    }
                    m.set(x, 0, end + 2, "stone_bricks");
                    box(x, x, 1, 2, end + 2, end + 2, "glass_pane");
                    m.slab(x, 3, end + 2, roofWood + "_slab", false);
                    m.set(x, 3, end + 1, roofWood + "_stairs", "facing", "north");
                }
            }
        } else {
            casement(centre - 1, 1, end, false, 3);
            m.set(centre, 4, end, "glass_pane");
        }
        if (furnished) {
            table(centre, 0, end - 3);
            m.set(right - 1, 0, end - 1, "bookshelf");
        }
    }

    private void townhouse() {
        room(0, 13, -2, 6, -1, 3, "stone_bricks");
        room(-1, 13, -2, 6, 4, 9, PLASTER);
        roof(-1, 13, 2, 4, 9, true, "deepslate_tile", PLASTER, mainRoof);
        for (int z : new int[]{-2, 1, 3, 6}) {
            m.stairs(-1, 3, z, "dark_oak_stairs", "east", true);
            box(-1, -1, 5, 8, z, z, POST);
        }
        for (int x : new int[]{3, 8, 12}) {
            for (int z : new int[]{-2, 6}) box(x, x, 5, 8, z, z, POST);
            casement(x - 2, 6, 6, false, 2);
        }
        casement(-1, 6, -1, true, 2);
        casement(-1, 6, 4, true, 2);
        if (mainRoof != Params.HouseRoof.HIP) casement(-1, 10, 2, true, 1);
        casement(0, 1, 2, true, 4);
        for (int x : new int[]{3, 8}) casement(x, 1, 6, false, 2);
        box(13, 13, 6, 7, 0, 1, "glass_pane");
        door(0, 0, "west");
        flight(3, -1, 4);
        box(9, 9, 5, 8, 2, 5, FLOOR);
        stack(12, 4, 15, "west");
        m.set(1, 3, 0, "lantern", "hanging", "true");
        if (furnished) {
            for (int z = 3; z <= 5; z++) m.set(3, 0, z, "barrel", "facing", "up");
            m.set(5, 0, 5, "crafting_table");
            table(3, 5, 3);
            box(4, 6, 5, 5, 1, 2, "red_carpet");
            m.set(12, 0, 3, "barrel", "facing", "up");
            bed(10, 5, 2, "red");
            m.set(12, 5, 1, "bookshelf");
        }
        extension(6, 7, PLASTER, "deepslate_tile");
    }

    private void cottage() {
        room(0, 10, -4, 4, -1, 3, "calcite");
        roof(0, 10, 0, 4, 3, true, "bamboo_mosaic", "calcite", mainRoof);
        // A small cross gable shelters the entrance on two freestanding posts.
        box(-3, -1, -1, -1, -2, 2, "cobblestone");
        for (int z : new int[]{-2, 2}) box(-3, -3, 0, 2, z, z, "oak_fence");
        roof(-3, -1, 0, 2, 2, true, "spruce", FLOOR);
        door(0, 0, "west");
        casement(0, 1, -3, true, 1);
        casement(2, 1, 4, false, 2);
        casement(7, 1, 4, false, 2);
        box(10, 10, 1, 2, 0, 1, "glass_pane");
        box(7, 8, 1, 2, -4, -4, "glass_pane");
        for (int x : new int[]{2, 7}) {
            m.set(x, -1, 5, "coarse_dirt");
            m.set(x, 0, 5, "flowering_azalea");
        }
        box(6, 6, 0, 3, 0, 3, "stripped_oak_log");
        stack(4, -3, 8, "south");
        m.set(4, 0, -2, "polished_andesite_slab");
        if (furnished) {
            table(3, 0, 1);
            box(4, 5, 0, 0, -1, 0, "green_carpet");
            bed(8, 0, 2, "green");
            m.set(9, 0, -2, "barrel", "facing", "up");
            m.set(3, 0, -3, "crafting_table");
            m.set(3, 1, -3, "lantern");
        }
        extension(4, 6, "calcite", "spruce");
    }

    private void farmhouse() {
        room(0, 10, -8, 8, -1, 3, "birch_planks");
        room(0, 10, -8, 8, 4, 8, "birch_planks");
        roof(-8, 8, 5, 5, 8, false, "dark_oak", PLASTER, mainRoof);
        // Cross-gabled front dormer tucked into the main slope.
        for (int z : new int[]{-2, 2}) box(0, 2, 9, 10, z, z, PLASTER);
        box(0, 0, 9, 10, -1, 1, PLASTER);
        roof(0, 3, 0, 2, 10, true, "dark_oak", PLASTER);
        casement(0, 10, 0, true, 1);
        room(10, 17, 0, 6, -1, 3, "stone_bricks");
        roof(10, 17, 3, 3, 3, true, "spruce", PLASTER);
        // Kitchen doorway and a second exit to the fields.
        door(10, 2, "east");
        door(17, 2, "east");
        stack(16, 5, 8, "north");
        box(-3, -1, -1, -1, -7, 7, FLOOR);
        for (int z = -7; z <= 7; z++) {
            for (int x = -3; x < 0; x++)
                m.slab(x, x == -1 ? 4 : 3, z, "spruce_slab", x == -2);
            if ((z + 7) % 5 == 0 || z == 7) box(-3, -3, 0, 2, z, z, "oak_fence");
        }
        for (int z : new int[]{-6, -3, 2, 5}) {
            casement(0, 1, z, true, 2);
            casement(0, 5, z, true, 2);
        }
        casement(3, 1, 8, false, 2);
        box(5, 6, 1, 2, -8, -8, "glass_pane");
        box(10, 10, 5, 6, -5, -4, "glass_pane");
        box(10, 10, 1, 2, -5, -4, "glass_pane");
        casement(4, 5, 8, false, 2);
        casement(13, 1, 6, false, 2);
        door(0, 0, "west");
        flight(2, -7, 4);
        box(1, 6, 5, 7, 0, 0, PLASTER);
        if (furnished) {
            box(2, 7, 0, 0, -1, 0, "cyan_carpet");
            table(4, 0, 4);
            m.stairs(3, 0, -4, "oak_stairs", "north", false);
            m.set(4, 0, -4, "bookshelf");
            bed(7, 5, 5, "blue");
            bed(7, 5, -4, "yellow");
            table(2, 5, 4);
            m.set(15, 0, 5, "barrel", "facing", "up");
            m.set(14, 0, 5, "crafting_table");
            m.set(14, 1, 5, "lantern");
        }
        extension(8, 6, PLASTER, "dark_oak");
    }
}
