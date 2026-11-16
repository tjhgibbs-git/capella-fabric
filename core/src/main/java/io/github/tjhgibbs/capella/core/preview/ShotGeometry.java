package io.github.tjhgibbs.capella.core.preview;

import io.github.tjhgibbs.capella.core.BuildingGenerator;
import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.params.Presets;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dev tool for the in-game screenshot harness ({@code shots/review.py}): generates a building
 * exactly as {@code /capella place} would for a player facing a given way, and writes the
 * geometry a camera needs as JSON. Usage:
 * {@code ShotGeometry <outDir> <preset[,preset...] | encoded params> [seed] [turns]}.
 *
 * <p>Every coordinate in the JSON is relative to the volume's origin (the "place here" block), so
 * the world position of a cell is {@code base + rel}, which is BuildingPlacer's mapping. Boxes
 * are inclusive block ranges {@code [x0,y0,z0,x1,y1,z1]}. {@code turns} defaults to 1, the turn
 * a south-facing player gets (long axis along +Z).
 */
public final class ShotGeometry {
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "shot-geometry");
        String which = args.length > 1 ? args[1] : "village";
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1;
        int turns = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        Files.createDirectories(out);
        if (which.contains("=")) {
            write(out, "custom", BuildingParams.decode(which), turns);
            return;
        }
        for (String id : which.split(",")) {
            if (!Presets.exists(id.trim())) {
                System.err.println("unknown preset: " + id);
                continue;
            }
            write(out, id.trim(), Presets.get(id.trim()).params(seed), turns);
        }
    }

    private static void write(Path out, String name, BuildingParams p, int turns) throws IOException {
        BlockVolume v = BuildingGenerator.generate(p, turns).volume();
        Files.writeString(out.resolve(name + ".json"), json(name, p, turns, v));
        System.out.println(name + ": " + v.width + "x" + v.height + "x" + v.length + " -> " + out.resolve(name + ".json"));
    }

    /** Air for camera purposes: nothing there, or a placeholder that is never placed. */
    private static boolean[] airIds(BlockVolume v) {
        boolean[] air = new boolean[v.palette().size()];
        for (int i = 0; i < air.length; i++) {
            String s = v.palette().get(i);
            air[i] = s.equals("minecraft:air") || s.startsWith("minecraft:cave_air") || s.startsWith("minecraft:structure_void");
        }
        return air;
    }

    static String json(String name, BuildingParams p, int turns, BlockVolume v) {
        boolean[] air = airIds(v);
        int W = v.width, H = v.height, L = v.length, ox = v.originX, oy = v.originY, oz = v.originZ;
        boolean[] solid = new boolean[W * H * L];
        int[] b = {W, H, L, -1, -1, -1};
        // Per z and per x: [minY, maxY, min, max of the other horizontal axis], -1 where empty.
        int[][] pz = new int[L][], px = new int[W][];
        int[] perY = new int[H];
        // Per y: the footprint [minX, maxX, minZ, maxZ] of that course.
        int[][] py = new int[H][];
        // The outermost block each way, to check the placement landed where the cameras expect.
        int[][] probe = new int[6][];
        for (int y = 0; y < H; y++) for (int z = 0; z < L; z++) for (int x = 0; x < W; x++) {
            if (air[v.idAt(x, y, z)]) continue;
            solid[v.index(x, y, z)] = true;
            perY[y]++;
            b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], y); b[2] = Math.min(b[2], z);
            b[3] = Math.max(b[3], x); b[4] = Math.max(b[4], y); b[5] = Math.max(b[5], z);
            if (py[y] == null) py[y] = new int[]{x, x, z, z};
            py[y][0] = Math.min(py[y][0], x); py[y][1] = Math.max(py[y][1], x);
            py[y][2] = Math.min(py[y][2], z); py[y][3] = Math.max(py[y][3], z);
            int[] c = {x, y, z};
            for (int k = 0; k < 3; k++) {
                if (probe[k] == null || c[k] < probe[k][k]) probe[k] = c;
                if (probe[k + 3] == null || c[k] > probe[k + 3][k]) probe[k + 3] = c;
            }
            if (pz[z] == null) pz[z] = new int[]{y, y, x, x};
            pz[z][0] = Math.min(pz[z][0], y); pz[z][1] = Math.max(pz[z][1], y);
            pz[z][2] = Math.min(pz[z][2], x); pz[z][3] = Math.max(pz[z][3], x);
            if (px[x] == null) px[x] = new int[]{y, y, z, z};
            px[x][0] = Math.min(px[x][0], y); px[x][1] = Math.max(px[x][1], y);
            px[x][2] = Math.min(px[x][2], z); px[x][3] = Math.max(px[x][3], z);
        }
        // Crown: the top quarter of the occupied height, at least four courses.
        int crownFrom = Math.max(b[1], b[4] - Math.max(3, (b[4] - b[1] + 1) / 4 - 1));
        int[] crown = {W, crownFrom, L, -1, b[4], -1};
        for (int y = crownFrom; y <= b[4]; y++) for (int z = 0; z < L; z++) for (int x = 0; x < W; x++) {
            if (!solid[v.index(x, y, z)]) continue;
            crown[0] = Math.min(crown[0], x); crown[2] = Math.min(crown[2], z);
            crown[3] = Math.max(crown[3], x); crown[5] = Math.max(crown[5], z);
        }
        var type = p.get(Params.TYPE);
        StringBuilder s = new StringBuilder("{\n");
        s.append("  \"name\": \"").append(name).append("\",\n");
        s.append("  \"params\": \"").append(p.encode()).append("\",\n");
        s.append("  \"seed\": ").append(p.seed()).append(",\n");
        s.append("  \"type\": \"").append(type.name()).append("\",\n");
        s.append("  \"bridge\": ").append(type.bridge()).append(",\n");
        s.append("  \"wall\": ").append(type.wall()).append(",\n");
        s.append("  \"turns\": ").append(turns).append(",\n");
        s.append("  \"volume\": [").append(W).append(", ").append(H).append(", ").append(L).append("],\n");
        s.append("  \"origin\": [").append(ox).append(", ").append(oy).append(", ").append(oz).append("],\n");
        s.append("  \"blocks\": ").append(v.blockCount()).append(",\n");
        s.append("  \"bbox\": ").append(box(b, ox, oy, oz)).append(",\n");
        s.append("  \"crown\": ").append(box(crown, ox, oy, oz)).append(",\n");
        s.append("  \"profileZ\": {\"from\": ").append(-oz).append(", \"rows\": ").append(profile(pz, oy, ox)).append("},\n");
        s.append("  \"profileX\": {\"from\": ").append(-ox).append(", \"rows\": ").append(profile(px, oy, oz)).append("},\n");
        s.append("  \"profileY\": {\"from\": ").append(-oy).append(", \"rows\": ").append(profileY(py, ox, oz)).append("},\n");
        s.append("  \"probes\": ").append(probes(v, probe)).append(",\n");
        s.append("  \"blocksPerY\": {\"from\": ").append(-oy).append(", \"counts\": ").append(java.util.Arrays.toString(perY)).append("},\n");
        s.append("  \"hull\": ").append(hull(v, solid, b[1])).append(",\n");
        s.append("  \"crownHull\": ").append(hull(v, solid, crownFrom)).append(",\n");
        s.append("  \"doors\": ").append(doors(v)).append(",\n");
        s.append("  \"deck\": ").append(type.bridge() ? deck(v, solid, b) : "null").append(",\n");
        s.append("  \"pockets\": ").append(pockets(v, roomSolid(v))).append("\n}\n");
        return s.toString();
    }

    private static String box(int[] b, int ox, int oy, int oz) {
        return "[" + (b[0] - ox) + ", " + (b[1] - oy) + ", " + (b[2] - oz) + ", " + (b[3] - ox) + ", " + (b[4] - oy) + ", " + (b[5] - oz) + "]";
    }

    private static String profile(int[][] rows, int oy, int oOther) {
        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) s.append(", ");
            int[] r = rows[i];
            s.append(r == null ? "null" : "[" + (r[0] - oy) + "," + (r[1] - oy) + "," + (r[2] - oOther) + "," + (r[3] - oOther) + "]");
        }
        return s.append("]").toString();
    }

    /**
     * The block corners that stick out furthest in each of a few hundred directions: near enough
     * the convex hull, so a camera can be fitted to the real silhouette instead of the box.
     * Points are block-corner coordinates relative to the origin block's low corner.
     */
    private static String hull(BlockVolume v, boolean[] solid, int fromY) {
        int W = v.width, H = v.height, L = v.length, n = 500;
        double[][] dirs = new double[n + 6][];
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - 2 * (i + 0.5) / n, r = Math.sqrt(1 - y * y);
            dirs[i] = new double[]{Math.cos(golden * i) * r, y, Math.sin(golden * i) * r};
        }
        for (int k = 0; k < 6; k++) {
            dirs[n + k] = new double[3];
            dirs[n + k][k % 3] = k < 3 ? 1 : -1;
        }
        double[] best = new double[dirs.length];
        int[][] at = new int[dirs.length][];
        java.util.Arrays.fill(best, -1e18);
        for (int y = fromY; y < H; y++) for (int z = 0; z < L; z++) for (int x = 0; x < W; x++) {
            if (!solid[v.index(x, y, z)]) continue;
            boolean exposed = x == 0 || y == 0 || z == 0 || x == W - 1 || y == H - 1 || z == L - 1
                    || !solid[v.index(x - 1, y, z)] || !solid[v.index(x + 1, y, z)] || !solid[v.index(x, y - 1, z)]
                    || !solid[v.index(x, y + 1, z)] || !solid[v.index(x, y, z - 1)] || !solid[v.index(x, y, z + 1)];
            if (!exposed) continue;
            for (int d = 0; d < dirs.length; d++) {
                double[] dir = dirs[d];
                // The corner of this block furthest along dir.
                int cx = dir[0] > 0 ? x + 1 : x, cy = dir[1] > 0 ? y + 1 : y, cz = dir[2] > 0 ? z + 1 : z;
                double dot = cx * dir[0] + cy * dir[1] + cz * dir[2];
                if (dot > best[d]) {
                    best[d] = dot;
                    at[d] = new int[]{cx, cy, cz};
                }
            }
        }
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (int[] c : at) if (c != null) out.add("[" + (c[0] - v.originX) + "," + (c[1] - v.originY) + "," + (c[2] - v.originZ) + "]");
        return "[" + String.join(", ", out) + "]";
    }

    private static String profileY(int[][] rows, int ox, int oz) {
        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) s.append(", ");
            int[] r = rows[i];
            s.append(r == null ? "null" : "[" + (r[0] - ox) + "," + (r[1] - ox) + "," + (r[2] - oz) + "," + (r[3] - oz) + "]");
        }
        return s.append("]").toString();
    }

    private static String probes(BlockVolume v, int[][] cells) {
        List<String> out = new ArrayList<>();
        for (int[] c : cells) {
            if (c == null) continue;
            String state = v.get(c[0], c[1], c[2]);
            int cut = state.indexOf('[');
            out.add("{\"pos\": [" + (c[0] - v.originX) + ", " + (c[1] - v.originY) + ", " + (c[2] - v.originZ) + "], \"block\": \""
                    + (cut < 0 ? state : state.substring(0, cut)) + "\"}");
        }
        return "[" + String.join(", ", out) + "]";
    }

    /** Lower halves of door blocks, with the way they face. */
    private static String doors(BlockVolume v) {
        List<String> out = new ArrayList<>();
        v.forEachBlock((x, y, z, state) -> {
            if (!state.contains("_door[") || !state.contains("half=lower")) return;
            int f = state.indexOf("facing=");
            String facing = f < 0 ? "" : state.substring(f + 7).split("[,\\]]")[0];
            out.add("{\"pos\": [" + (x - v.originX) + ", " + (y - v.originY) + ", " + (z - v.originZ) + "], \"facing\": \"" + facing + "\"}");
        });
        return "[" + String.join(", ", out) + "]";
    }

    /**
     * A bridge along its centreline (x of the origin): the longest walkable level is the deck, and
     * the open stretches under it are the spans a camera can sit in.
     */
    private static String deck(BlockVolume v, boolean[] solid, int[] b) {
        int x = v.originX, H = v.height, L = v.length;
        int[] hist = new int[H];
        for (int z = 0; z < L; z++) for (int y = 0; y + 2 < H; y++) {
            if (solid[v.index(x, y, z)] && !solid[v.index(x, y + 1, z)] && !solid[v.index(x, y + 2, z)]) hist[y]++;
        }
        int top = 0;
        for (int y = 0; y < H; y++) if (hist[y] > hist[top]) top = y;
        // Per z: the soffit (lowest solid block of the run hanging down from the deck) and how much
        // open air stands on the bottom of the box under it.
        List<String> spans = new ArrayList<>();
        int runFrom = -1, runSoffit = 0, runSoffitMax = 0;
        for (int z = 0; z <= L; z++) {
            int clear = 0, soffit = -1;
            if (z < L) {
                int y = b[1];
                while (y < top && !solid[v.index(x, y, z)]) y++;
                clear = y - b[1];
                soffit = y;
            }
            boolean open = z < L && clear >= 4;
            if (open && runFrom < 0) {
                runFrom = z; runSoffit = soffit; runSoffitMax = soffit;
            } else if (open) {
                runSoffit = Math.min(runSoffit, soffit); runSoffitMax = Math.max(runSoffitMax, soffit);
            } else if (runFrom >= 0) {
                spans.add("{\"z0\": " + (runFrom - v.originZ) + ", \"z1\": " + (z - 1 - v.originZ) + ", \"soffitMin\": " + (runSoffit - v.originY)
                        + ", \"soffitMax\": " + (runSoffitMax - v.originY) + "}");
                runFrom = -1;
            }
        }
        return "{\"topY\": " + (top - v.originY) + ", \"bottomY\": " + (b[1] - v.originY) + ", \"spans\": [" + String.join(", ", spans) + "]}";
    }

    private static boolean clear3(BlockVolume v, boolean[] solid, int x, int y, int z, boolean acrossX) {
        for (int d = -1; d <= 1; d++) {
            int xx = acrossX ? x + d : x, zz = acrossX ? z : z + d;
            if (xx < 0 || zz < 0 || xx >= v.width || zz >= v.length || solid[v.index(xx, y, zz)]) return false;
        }
        return true;
    }

    /** Solidity for finding rooms: carpets, lights, ladders and the like do not divide a room. */
    private static boolean[] roomSolid(BlockVolume v) {
        boolean[] air = airIds(v);
        for (int i = 0; i < air.length; i++) {
            String s = v.palette().get(i);
            for (String thin : new String[]{"carpet", "torch", "candle", "chain", "lantern", "button", "pressure_plate", "ladder", "banner", "_sign", "rail"}) {
                if (s.contains(thin)) air[i] = true;
            }
        }
        boolean[] solid = new boolean[v.width * v.height * v.length];
        for (int y = 0; y < v.height; y++) for (int z = 0; z < v.length; z++) for (int x = 0; x < v.width; x++) {
            solid[v.index(x, y, z)] = !air[v.idAt(x, y, z)];
        }
        return solid;
    }

    /**
     * Rooms a camera can stand in: floor cells (solid below, two of air) that are roofed and have
     * something solid in at least three of the four horizontal directions, grouped into connected
     * floors (steps of one block join, so a stair links its landings). Largest first.
     */
    private static String pockets(BlockVolume v, boolean[] solid) {
        int W = v.width, H = v.height, L = v.length;
        boolean[] cell = new boolean[W * H * L];
        for (int y = 1; y + 1 < H; y++) for (int z = 0; z < L; z++) for (int x = 0; x < W; x++) {
            if (solid[v.index(x, y, z)] || solid[v.index(x, y + 1, z)] || !solid[v.index(x, y - 1, z)]) continue;
            boolean roof = false;
            for (int yy = y + 2; yy < H && !roof; yy++) roof = solid[v.index(x, yy, z)];
            if (!roof) continue;
            int walls = 0;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int i = 1; i <= 48; i++) {
                    int xx = x + d[0] * i, zz = z + d[1] * i;
                    if (xx < 0 || zz < 0 || xx >= W || zz >= L) break;
                    if (solid[v.index(xx, y, zz)] || solid[v.index(xx, y + 1, zz)]) {
                        walls++;
                        break;
                    }
                }
            }
            cell[v.index(x, y, z)] = walls >= 3;
        }
        record Pocket(int count, String json) {
        }
        List<Pocket> found = new ArrayList<>();
        boolean[] seen = new boolean[cell.length];
        for (int y0 = 0; y0 < H; y0++) for (int z0 = 0; z0 < L; z0++) for (int x0 = 0; x0 < W; x0++) {
            int start = v.index(x0, y0, z0);
            if (!cell[start] || seen[start]) continue;
            List<int[]> members = new ArrayList<>();
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{x0, y0, z0});
            seen[start] = true;
            while (!queue.isEmpty()) {
                int[] c = queue.poll();
                members.add(c);
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int dy = -1; dy <= 1; dy++) {
                    int x = c[0] + d[0], y = c[1] + dy, z = c[2] + d[1];
                    if (x < 0 || y < 0 || z < 0 || x >= W || y >= H || z >= L) continue;
                    int i = v.index(x, y, z);
                    if (cell[i] && !seen[i]) {
                        seen[i] = true;
                        queue.add(new int[]{x, y, z});
                    }
                }
            }
            if (members.size() < 6) continue;
            int[] hist = new int[H];
            int[] bb = {W, H, L, -1, -1, -1};
            for (int[] c : members) {
                hist[c[1]]++;
                for (int k = 0; k < 3; k++) {
                    bb[k] = Math.min(bb[k], c[k]);
                    bb[k + 3] = Math.max(bb[k + 3], c[k]);
                }
            }
            int floor = 0;
            for (int y = 0; y < H; y++) if (hist[y] > hist[floor]) floor = y;
            double cx = 0, cz = 0;
            int n = 0;
            for (int[] c : members) if (c[1] == floor) {
                cx += c[0]; cz += c[2]; n++;
            }
            cx /= n; cz /= n;
            // The camera cell: on the main floor, as near its centroid as the room allows, preferring
            // cells with open air around them at eye level.
            int[] best = null;
            double bestScore = Double.MAX_VALUE;
            for (int[] c : members) {
                if (c[1] != floor) continue;
                int blocked = 0;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int x = c[0] + dx, z = c[2] + dz;
                    if (x < 0 || z < 0 || x >= W || z >= L || solid[v.index(x, floor + 1, z)]) blocked++;
                }
                double score = Math.hypot(c[0] - cx, c[2] - cz) + blocked * 1.5;
                if (score < bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            int eye = floor + 1;
            // How far the camera can back off along each axis and still have a block of air either
            // side of it at eye level (so it is not pressed against a jamb or a pier).
            int zLo = best[2], zHi = best[2], xLo = best[0], xHi = best[0], up = eye;
            while (clear3(v, solid, best[0], eye, zLo - 1, true)) zLo--;
            while (clear3(v, solid, best[0], eye, zHi + 1, true)) zHi++;
            while (clear3(v, solid, xLo - 1, eye, best[2], false)) xLo--;
            while (clear3(v, solid, xHi + 1, eye, best[2], false)) xHi++;
            while (up < H - 1 && !solid[v.index(best[0], up + 1, best[2])]) up++;
            String j = String.format(Locale.ROOT,
                    "{\"cells\": %d, \"floorY\": %d, \"bbox\": %s, \"centre\": [%d, %d, %d], \"runZ\": [%d, %d], \"runX\": [%d, %d], \"ceilingY\": %d}",
                    members.size(), floor - v.originY, box(bb, v.originX, v.originY, v.originZ),
                    best[0] - v.originX, floor - v.originY, best[2] - v.originZ,
                    zLo - v.originZ, zHi - v.originZ, xLo - v.originX, xHi - v.originX, up + 1 - v.originY);
            found.add(new Pocket(members.size(), j));
        }
        found.sort((a, c) -> c.count - a.count);
        StringBuilder s = new StringBuilder("[");
        for (int i = 0; i < Math.min(12, found.size()); i++) s.append(i > 0 ? ",\n    " : "\n    ").append(found.get(i).json);
        return s.append(found.isEmpty() ? "]" : "\n  ]").toString();
    }
}
