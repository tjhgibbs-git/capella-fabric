package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.hash.Hash;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.params.Params.BuildingType;
import io.github.tjhgibbs.capella.core.raster.Volume;

/** Independent block builders for pristine castles, using the existing stone mixes and placement frame.
 * Tower origin: west doorway at floor level. Wall origin: centre of the west end at ground level.
 * Heights measure to the fighting deck; parapets, turrets and roofs rise above it.
 */
public final class Fortification {
    private final BuildingParams p;
    private final Mason m;
    private final Style style;
    private final BuildingType type;
    private final int w, h, length;
    private java.util.List<Integer> floors;

    private Fortification(BuildingParams source) {
        p = source.copy();
        Params.clampSizes(p);
        type = p.get(Params.TYPE);
        w = p.integer(Params.WIDTH);
        h = p.integer(Params.HEIGHT);
        length = p.integer(Params.LENGTH);
        m = new Mason(p.seed());
        style = Style.of(p.get(Params.STONE));
    }

    public static Volume build(BuildingParams p, int turns) {
        if (!p.get(Params.TYPE).fortification()) throw new IllegalArgumentException("Not a fortification");
        Fortification f = new Fortification(p);
        if (f.type.outbuilding() || f.type.compactTower()) return Garrison.build(p, turns);
        if (f.type.wall()) f.wall(); else f.tower();
        return f.m.toVolume(turns);
    }

    private void stone(int x, int y, int z, boolean trim) {
        // Keep large surfaces coherent; reserve the noisier mix for broad weathered patches.
        String block = trim ? style.trim.block(p.seed(), x, y, z, 0)
                : Hash.noise3(p.seed(), "castle-masonry", x, y, z, 5) < 0.36
                ? style.wall.block(p.seed(), x, y, z, 0) : style.wall.blocks().get(1);
        m.set(x, y, z, block);
    }

    private boolean inside(int x, int z, int inset) {
        if (x < inset || x >= w - inset || z < -w / 2 + inset || z >= -w / 2 + w - inset) return false;
        if (type != BuildingType.ROUND_WATCHTOWER) return true;
        double radius = w / 2.0 - inset;
        double dx = x - (w - 1) / 2.0, dz = z - (-w / 2 + (w - 1) / 2.0);
        return dx * dx + dz * dz <= radius * radius;
    }

    private void tower() {
        floors = TowerInterior.floorLevels(p);
        int z0 = -w / 2, z1 = z0 + w - 1;
        for (int y = -1; y < h; y++) {
            for (int x = 0; x < w; x++) for (int z = z0; z <= z1; z++) {
                if (!inside(x, z, 0)) continue;
                boolean shell = !inside(x, z, 3);
                boolean floor = y == -1 || y == h - 1;
                if (shell || floor) {
                    if (shell || y == -1 || y == h - 1) stone(x, y, z, false);
                    else m.set(x, y, z, style.timber);
                }
            }
        }
        towerMasonry();
        facadeDetails();
        if (type == BuildingType.SQUARE_KEEP) keepTurrets();
        if (type == BuildingType.ROUND_WATCHTOWER) roundCrown();
        if (type == BuildingType.GOTHIC_TURRET) gothicRoof();
        towerWindows();
        portal();
        TowerInterior.build(m, style, p);
        if (p.bool(Params.ACCESS)) {
            // A continuous ladder shaft, including holes through each floor and the roof deck.
            // Use z=2 to keep its backing clear of both the portal and the arrow loops.
            for (int y = 0; y < h; y++) {
                stone(2, y, 2, false);
                m.set(3, y, 2, "minecraft:ladder", "facing", "east");
            }
            m.clear(3, h, 2);
            m.clear(3, h + 1, 2);
        }
    }

    /** Projecting plinths, set-off buttresses, string courses, and a machicolated cornice. */
    private void towerMasonry() {
        int z0 = -w / 2, z1 = z0 + w - 1;
        for (int y = -1; y <= h + 1; y++) {
            boolean band = y == 2 || y == h - 3 || y > 4 && floors.contains(y + 1);
            int spread = y < 0 ? 2 : y < 2 || band ? 1 : 0;
            if (spread > 0) {
                for (int x = -spread; x < w + spread; x++) for (int z = z0 - spread; z <= z1 + spread; z++) {
                    if (!inside(x, z, -spread) || inside(x, z, 0)) continue;
                    if (band) m.slab(x, y, z, style.trim.slab(p.seed(), x, y, z, 0), true);
                    else stone(x, y, z, y == -1);
                }
            }
            if (type == BuildingType.GOTHIC_TURRET && y < h) {
                int depth = y < h / 3 ? 3 : y < 2 * h / 3 ? 2 : 1;
                for (int face = 0; face < 4; face++) for (int u : new int[]{-w / 2 + 2, w / 2 - 2})
                    for (int side = -1; side <= 1; side++) for (int d = -depth; d <= 0; d++)
                        faceStone(face, u + side, y, d, side != 0 || y % 7 == 5);
            }
        }
        // A supported, overhanging fighting platform with individual corbels under it.
        for (int x = -1; x <= w; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            if (!inside(x, z, -1) || inside(x, z, 1)) continue;
            stone(x, h - 1, z, true);
            if (!inside(x, z, 0) && Math.floorMod(x + z, 3) == 0) {
                String face = x < 0 ? "east" : x >= w ? "west" : z < z0 ? "south" : "north";
                m.stairs(x, h - 2, z, style.trim.stairs(p.seed(), x, h - 2, z, 0), face, true);
            }
            if (!inside(x, z, 0)) {
                stone(x, h, z, false);
                if (Math.floorMod(x + z, 4) < 2) {
                    stone(x, h + 1, z, false);
                    m.slab(x, h + 2, z, style.trim.slab(p.seed(), x, h + 2, z, 0), false);
                }
            }
        }
    }

    /** Restrained quoin chains leave clear wall fields around the keep windows. */
    private void facadeDetails() {
        if (type == BuildingType.ROUND_WATCHTOWER) return;
        for (int face = 0; face < 4; face++) {
            // Staggered dressed stones at the corners tie the elevation to its buttresses.
            for (int y = 3; y < h - 3; y += 3)
                for (int side : new int[]{-1, 1})
                    for (int u = 0; u < 2; u++) faceStone(face, side * (w / 2 - 1 - u), y, 0, true);
        }
        if (type == BuildingType.SQUARE_KEEP && floors.get(1) >= 7) {
            // A steep, shallow entrance gable frames the portal rather than competing with turrets.
            for (int z = -4; z <= 4; z++) {
                int y = Math.min(9, floors.get(1)) - Math.abs(z);
                for (int fill = 0; fill <= y; fill++) stone(-1, fill, z, Math.abs(z) == 4);
                stone(-2, y, z, true);
                m.stairs(-2, y + 1, z, style.trim.stairs(p.seed(), -2, y, z, 0), z < 0 ? "south" : "north", false);
            }
        }
    }

    private int[] facePoint(int face, int u, int y, int depth) {
        return switch (face) {
            case 0 -> new int[]{depth, y, u};
            case 1 -> new int[]{w - 1 - depth, y, u};
            case 2 -> new int[]{w / 2 + u, y, -w / 2 + depth};
            default -> new int[]{w / 2 + u, y, -w / 2 + w - 1 - depth};
        };
    }

    private void faceStone(int face, int u, int y, int depth, boolean trim) {
        int[] q = facePoint(face, u, y, depth);
        stone(q[0], q[1], q[2], trim);
    }

    private void faceClear(int face, int u, int y, int depth) {
        int[] q = facePoint(face, u, y, depth);
        m.clear(q[0], q[1], q[2]);
    }

    private void faceSlab(int face, int u, int y, int depth) {
        int[] q = facePoint(face, u, y, depth);
        m.slab(q[0], y, q[2], style.trim.slab(p.seed(), q[0], y, q[2], 0), true);
    }

    private void towerWindows() {
        if (type == BuildingType.SQUARE_KEEP) { keepWindows(); return; }
        boolean gothic = type == BuildingType.GOTHIC_TURRET;
        var levels = floors;
        for (int face = 0; face < 4; face++) for (int level = 0; level < levels.size() - 1; level++) {
            int y = levels.get(level) + 1;
            int clear = levels.get(level + 1) - levels.get(level);
            int[] offsets = type == BuildingType.SQUARE_KEEP ? new int[]{-w / 4, w / 4} : new int[]{0};
            for (int offset : offsets) {
                int rise = Math.min(gothic ? 4 : 3, clear - 2);
                int radius = gothic ? 1 : 0;
                for (int u = -radius; u <= radius; u++) for (int dy = 0; dy < rise - Math.abs(u); dy++) {
                    for (int d = -1; d < 3; d++) faceClear(face, offset + u, y + dy, d);
                    int[] glass = facePoint(face, offset + u, y + dy, 1);
                    if (gothic) m.set(glass[0], glass[1], glass[2], "minecraft:cyan_stained_glass");
                }
                for (int dy = 0; dy < rise - 1; dy++) for (int side : new int[]{-radius - 1, radius + 1})
                    faceStone(face, offset + side, y + dy, -1, true);
                for (int u = -radius - 1; u <= radius + 1; u++) {
                    faceSlab(face, offset + u, y - 1, -1);
                    int hood = y + rise - Math.min(radius + 1, Math.abs(u));
                    faceStone(face, offset + u, hood, -1, true);
                    faceSlab(face, offset + u, hood + 1, -1);
                }
                if (gothic) for (int dy = 0; dy < 2; dy++) faceStone(face, offset, y + dy, 0, true);
            }
        }
    }

    /** Narrow defensive loops below, paired domestic lights above in the larger keeps.
     * Jambs are bonded into the face. Only the sill and hood project; the opening widens
     * through the wall towards the room, rather than remaining a dark square-cut tunnel.
     */
    private void keepWindows() {
        int parity = w % 2 == 0 ? 1 : 0;
        boolean paired = w >= 25;
        int offset = Math.max(3, w / 2 - (w >= 21 ? 3 : 2) - 5);
        int[] centres = w >= 19 ? new int[]{-offset - parity, offset} : new int[]{-parity};
        for (int face = 0; face < 4; face++) for (int level = 0; level < floors.size() - 1; level++) {
            if (face == 0 && level == 0) continue; // The entrance owns the ground-floor west bay.
            int base = floors.get(level), gap = floors.get(level + 1) - base;
            int y = base + 2, rise = Math.min(3, gap - 3);
            int radius = paired && level > 0 ? 1 : 0;
            for (int centre : centres) {
                // Cut projecting stage/crown bands too when a short upper storey meets them.
                for (int u = -radius; u <= radius; u++) for (int dy = 0; dy < rise; dy++)
                    faceClear(face, centre + u, y + dy, -1);
                for (int d = 0; d < 3; d++) {
                    int reveal = radius + (d > 0 ? 1 : 0);
                    for (int u = -reveal; u <= reveal; u++) {
                        // Continuous dressed sill and lintel support every layer of the opening.
                        faceStone(face, centre + u, y - 1, d, true);
                        faceStone(face, centre + u, y + rise, d, true);
                        for (int dy = 0; dy < rise; dy++) faceClear(face, centre + u, y + dy, d);
                    }
                    for (int side : new int[]{-reveal - 1, reveal + 1})
                        for (int dy = 0; dy < rise; dy++) faceStone(face, centre + side, y + dy, d, true);
                }
                // Slender stone mullion sits in the outer plane; the inner embrasure stays open.
                if (radius > 0) for (int dy = 0; dy < rise; dy++) {
                    int[] q = facePoint(face, centre, y + dy, 0);
                    m.set(q[0], q[1], q[2], style.trim.wall(p.seed(), q[0], q[1], q[2], 0));
                }
                for (int u = -radius - 1; u <= radius + 1; u++) {
                    // Low sill, solid lintel and a single shallow hood: no stacked stone eyebrows.
                    faceSlab(face, centre + u, y - 1, -1);
                    faceSlab(face, centre + u, y + rise, -1);
                }
            }
        }
    }

    private void portal() {
        for (int z = -3; z <= 3; z++) {
            int top = 6 - Math.abs(z);
            for (int y = 0; y <= top; y++) {
                if (Math.abs(z) == 3 || y == top) {
                    for (int x = -2; x <= 0; x++) stone(x, y, z, true);
                } else for (int x = -2; x < 3; x++) m.clear(x, y, z);
            }
        }
        for (int z : new int[]{-4, 4}) {
            stone(-1, 2, z, true);
            m.set(-1, 3, z, "minecraft:lantern", "hanging", "false");
        }
    }

    /** Four full-height octagonal drums give the keep a castle silhouette at any viewing angle. */
    private void keepTurrets() {
        int radius = w >= 21 ? 3 : 2;
        for (int cx : new int[]{1, w - 2}) for (int cz : new int[]{-w / 2 + 1, -w / 2 + w - 2}) {
            for (int y = -1; y < h + 6; y++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                if (Math.abs(dx) + Math.abs(dz) > radius * 2 - 1) continue;
                boolean edge = distance == radius || Math.abs(dx) + Math.abs(dz) == radius * 2 - 1;
                if (y < h + 3 && (edge || y < 2 || y == h + 2))
                    stone(cx + dx, y, cz + dz, y < 2 || floors.contains(y + 1) || y == h + 2);
                if (y == h + 3 && edge) stone(cx + dx, y, cz + dz, true);
                if (y == h + 4 && edge && (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz))) {
                    stone(cx + dx, y, cz + dz, false);
                    m.slab(cx + dx, y + 1, cz + dz, style.trim.slab(p.seed(), cx + dx, y, cz + dz, 0), false);
                }
            }
            // Slits on the two outward drum faces, with deep reveals.
            for (int y = 4; y < h - 3; y += 7) for (int dy = 0; dy < 2; dy++) {
                m.clear(cx < w / 2 ? cx - radius : cx + radius, y + dy, cz);
                m.clear(cx, y + dy, cz < 0 ? cz - radius : cz + radius);
            }
        }
    }

    private void roundCrown() {
        int z0 = -w / 2, z1 = z0 + w - 1;
        for (int x = -2; x <= w + 1; x++) for (int z = z0 - 2; z <= z1 + 2; z++) {
            if (!inside(x, z, -2)) continue;
            boolean edge = !inside(x, z, -1);
            stone(x, h - 1, z, true);
            if (!inside(x, z, 0)) {
                stone(x, h - 2, z, true);
                if (Math.floorMod(x + z, 3) == 0) stone(x, h - 3, z, true);
            }
            m.clear(x, h, z); m.clear(x, h + 1, z); m.clear(x, h + 2, z);
            if (edge) {
                stone(x, h, z, false);
                // Angular spacing yields regular paired merlons around the circle.
                double angle = Math.atan2(z - (z0 + (w - 1) / 2.0), x - (w - 1) / 2.0);
                if (Math.floorMod((int) Math.floor(angle * (w + 4) / 3.0), 4) < 2) {
                    stone(x, h + 1, z, false);
                    m.slab(x, h + 2, z, style.trim.slab(p.seed(), x, h, z, 0), false);
                }
            }
        }
    }

    private void gothicRoof() {
        int z0 = -w / 2, z1 = z0 + w - 1;
        // A tall concave pavilion: flared eaves, steep lower slopes and a needle finial.
        for (int y = h + 3; y <= h + 4 + w; y++) {
            int inset = (y - h - 4) / 2;
            int lo = y == h + 3 ? -2 : Math.max(0, inset), hi = w - 1 - lo;
            if (lo > hi) break;
            for (int x = lo; x <= hi; x++) for (int z = z0 + lo; z <= z1 - lo; z++) {
                String block = style.roof.block(p.seed(), x, y, z, 0);
                boolean edge = x <= lo + 1 || x >= hi - 1 || z <= z0 + lo + 1 || z >= z1 - lo - 1;
                if (!edge) continue;
                m.set(x, y, z, block);
                if (y % 2 == 0 && (x == lo || x == hi || z == z0 + lo || z == z1 - lo)) {
                    String facing = x == lo ? "east" : x == hi ? "west" : z == z0 + lo ? "south" : "north";
                    m.stairs(x, y, z, style.roof.stairs(p.seed(), x, y, z, 0), facing, false);
                }
            }
        }
        int tip = h + w + 4;
        for (int y = tip; y < tip + 4; y++) m.set(w / 2, y, 0, style.trim.wall(p.seed(), w / 2, y, 0, 0));
        // Turret pinnacles continue the buttress axes beside the eaves.
        for (int cx : new int[]{0, w - 1}) for (int cz : new int[]{z0, z1}) {
            for (int y = h - 2; y < h + 6; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                stone(cx + dx, y, cz + dz, y % 3 == 0);
            for (int y = h + 6; y <= h + 10; y++) {
                stone(cx, y, cz, true);
                if (y == h + 6) for (int d : new int[]{-1, 1}) { stone(cx + d, y, cz, true); stone(cx, y, cz + d, true); }
            }
            m.set(cx, h + 11, cz, style.trim.wall(p.seed(), cx, h, cz, 0));
        }
        gothicDormers();
        for (int x : new int[]{2, w - 3}) for (int z : new int[]{z0 + 2, z1 - 2})
            for (int y = h; y < h + 3; y++) m.set(x, y, z, style.timberLog, "axis", "y");
    }

    /** The glazing is recessed into a real opening through both roof layers. Cheeks and
     * gabled caps return to the sloping roof, leaving an open reveal into the hollow attic. */
    private void gothicDormers() {
        int centre = w % 2 == 0 ? -1 : 0;
        for (int face = 0; face < 4; face++) {
            // Dressed sill beam bonds into the flared eaves; no freestanding glass screen.
            for (int u = -2; u <= 2; u++) for (int d = 0; d <= 2; d++) {
                faceStone(face, centre + u, h + 3, d, true);
                faceStone(face, centre + u, h + 4, d, true);
            }
            for (int u = -2; u <= 2; u++) {
                int gableTop = h + 11 - Math.abs(u);
                for (int y = h + 5; y <= gableTop; y++) {
                    int back = Math.max(0, (y - h - 4) / 2) + 1;
                    for (int d = 0; d <= back; d++) {
                        // Front tympanum, side cheeks and head close against the main roof.
                        if (d == 0 || Math.abs(u) == 2 || y == gableTop)
                            faceStone(face, centre + u, y, d, true);
                    }
                }
            }
            for (int u = -1; u <= 1; u++) {
                int rise = 4 - Math.abs(u);
                for (int dy = 0; dy < rise; dy++) {
                    int y = h + 5 + dy, back = Math.max(0, (y - h - 4) / 2) + 1;
                    for (int d = 0; d <= back; d++) faceClear(face, centre + u, y, d);
                    int[] q = facePoint(face, centre + u, y, 1);
                    if (u == 0 && dy < 3)
                        m.set(q[0], y, q[2], style.trim.wall(p.seed(), q[0], y, q[2], 0));
                    else m.set(q[0], y, q[2], "minecraft:cyan_stained_glass_pane");
                }
                int head = h + 5 + rise;
                for (int d = 0; d <= Math.max(0, (head - h - 4) / 2) + 1; d++)
                    faceStone(face, centre + u, head, d, true);
            }
            for (int u = -3; u <= 3; u++) {
                int y = h + 12 - Math.abs(u), back = Math.max(0, (y - h - 4) / 2);
                for (int d = 0; d <= back; d++) {
                    int[] q = facePoint(face, centre + u, y, d);
                    var mix = d == 0 ? style.trim : style.roof;
                    if (u == 0) {
                        m.set(q[0], y, q[2], mix.block(p.seed(), q[0], y, q[2], 0));
                        m.slab(q[0], y + 1, q[2], mix.slab(p.seed(), q[0], y, q[2], 0), false);
                    } else {
                        String facing = face < 2 ? (u < 0 ? "south" : "north") : (u < 0 ? "east" : "west");
                        m.stairs(q[0], y, q[2], mix.stairs(p.seed(), q[0], y, q[2], 0), facing, false);
                    }
                }
            }
            // A small drip sill projects only half a block below the recessed lights.
            for (int u = -2; u <= 2; u++) faceSlab(face, centre + u, h + 4, -1);
        }
    }

    private int wallButtressReach(int y) {
        if (type == BuildingType.PLAIN_WALL) return 1;
        // Leave a real upper stem even on the shortest wall before its capital.
        int lower = Math.max(0, (h - 3) / 3), upper = Math.max(lower + 1, 2 * (h - 3) / 3);
        return y < lower ? 3 : y < upper ? 2 : 1;
    }

    private void wall() {
        int z0 = -w / 2, z1 = z0 + w - 1;
        boolean walk = p.bool(Params.WALKABLE);
        for (int x = 0; x < length; x++) for (int y = -1; y < h; y++) {
            for (int z = z0; z <= z1; z++) {
                // Pointed openings retain full-height piers and a minimum three-course spandrel.
                int bay = Math.floorMod(x, 8), distance = Math.abs(bay - 4);
                int archTop = h - 4 - distance;
                boolean arch = type == BuildingType.ARCADED_WALL && x >= 2 && x < length - 2
                        && distance <= 2 && y >= 0 && y < archTop;
                if (!arch) stone(x, y, z, y == h - 2 || y == 1);
            }
            if (type != BuildingType.PLAIN_WALL && x % 8 == 0 && y < h - 2) {
                stone(x, y, z0 - 1, y == 1);
                stone(x, y, z1 + 1, y == 1);
            }
        }
        // Shallow recessed panels make the curtain read as masonry bays between its piers.
        // Backing is retained even at minimum thickness; joining end planes remain untouched.
        if (type == BuildingType.CURTAIN_WALL && h >= 9) {
            for (int x = 2; x < length - 2; x++) {
                int bay = x % 8;
                if (bay < 2 || bay > 6) continue;
                for (int side : new int[]{-1, 1}) {
                    int z = side < 0 ? z0 : z1;
                    for (int y = 3; y < h - 4; y++) m.clear(x, y, z);
                    m.slab(x, 2, z, style.trim.slab(p.seed(), x, 2, z, 0), true);
                    m.stairs(x, h - 4, z, style.trim.stairs(p.seed(), x, h, z, 0),
                            side < 0 ? "south" : "north", true);
                }
            }
        }
        // Build the body first, then weather only exposed setback shoulders. Caps that were
        // written inside the body loop could be overwritten by the next masonry course.
        for (int x = 0; x < length; x += 8) for (int side : new int[]{-1, 1}) {
            int edge = side < 0 ? z0 : z1;
            for (int y = -1; y <= h - 3; y++) {
                int reach = wallButtressReach(y);
                for (int dx = -1; dx <= 1; dx++) for (int d = 1; d <= reach; d++) {
                    if (x + dx >= 0 && x + dx < length)
                        stone(x + dx, y, edge + side * d, dx != 0 || y == 1);
                }
            }
            for (int y = 0; y <= h - 3; y++) {
                int previous = wallButtressReach(y - 1), next = wallButtressReach(y);
                for (int d = next + 1; d <= previous; d++) for (int dx = -1; dx <= 1; dx++) {
                    if (x + dx >= 0 && x + dx < length)
                        m.stairs(x + dx, y, edge + side * d,
                                style.trim.stairs(p.seed(), x, y, edge, 0), side < 0 ? "south" : "north", false);
                }
            }
            // A full-width capital bonds all three buttress lanes into the cornice course.
            for (int dx = -1; dx <= 1; dx++) if (x + dx >= 0 && x + dx < length)
                stone(x + dx, h - 2, edge + side, true);
        }
        for (int x = 0; x < length; x++) for (int side : new int[]{-1, 1}) {
            int z = side < 0 ? z0 : z1;
            // A stone arch ring projects from the face while the opening stays fully clear.
            if (type == BuildingType.ARCADED_WALL) {
                int distance = Math.abs(x % 8 - 4), crown = h - 4 - distance;
                if (distance <= 2 && x >= 2 && x < length - 2) {
                    stone(x, crown, z + side, true);
                    m.slab(x, crown + 1, z + side, style.trim.slab(p.seed(), x, crown, z, 0), false);
                }
            }
            if (type != BuildingType.PLAIN_WALL) {
                // Corbels have their high half against the wall and their full top against the
                // continuous cornice. Buttress capitals take precedence over the corbel rhythm.
                int bay = x % 8;
                boolean buttress = bay <= 1 || bay == 7 && x + 1 < length;
                if (x % 3 == 0 && !buttress)
                    m.stairs(x, h - 2, z + side, style.trim.stairs(p.seed(), x, h, z, 0),
                            side < 0 ? "south" : "north", true);
                stone(x, h - 1, z + side, true);
            } else {
                // Bottom slabs sit directly on the capital; top slabs left a half-block gap.
                m.slab(x, h - 1, z + side, style.trim.slab(p.seed(), x, h, z, 0), false);
            }
            if (walk) {
                stone(x, h, z, false);
                if (type != BuildingType.PLAIN_WALL && x % 4 < 2) {
                    stone(x, h + 1, z, false);
                    m.slab(x, h + 2, z, style.trim.slab(p.seed(), x, h, z, 0), false);
                    // Arrow slit between merlon pairs is visible as a deep vertical notch.
                    if (x % 4 == 0) m.stairs(x, h + 1, z + side,
                            style.trim.stairs(p.seed(), x, h, z, 0), side < 0 ? "south" : "north", true);
                } else m.slab(x, h + 1, z, style.trim.slab(p.seed(), x, h, z, 0), false);
            }
        }
        if (!walk) {
            for (int x = 0; x < length; x++) for (int z = z0; z <= z1; z++) {
                boolean north = z - z0 < w / 2;
                m.stairs(x, h, z, style.trim.stairs(p.seed(), x, h, z, 0), north ? "south" : "north", false);
                if (Math.abs(z) <= 1) m.slab(x, h + 1, z, style.trim.slab(p.seed(), x, h, z, 0), false);
            }
            for (int x = 0; x < length; x += 8) {
                for (int z = z0 - 1; z <= z1 + 1; z++) stone(x, h + 1, z, true);
                m.slab(x, h + 2, 0, style.trim.slab(p.seed(), x, h, 0, 0), false);
            }
        }
        if (p.bool(Params.GATEHOUSE)) CastleGatehouse.build(m, style, p);
    }
}
