package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.raster.Volume;
import io.github.tjhgibbs.capella.core.small.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TowerTraversalTest {
    private static final List<String> TYPES = List.of("squarekeep", "roundwatchtower", "gothicturret");

    @Test
    void everyFlightAndLandingIsConnectedAndHasThreeBlocksOfHeadroom() {
        for (String type : TYPES) for (int width : new int[]{13, 14, 19, 35})
            for (int height : new int[]{12, 38, 96}) for (long seed : new long[]{1, 7, 19}) {
                var p = Presets.get(type).params(seed).set(Params.WIDTH, width).set(Params.HEIGHT, height);
                Volume v = Fortification.build(p, 0);
                var plan = TowerInterior.plan(p);
                String context = type + "/" + width + "/" + height + "/" + seed;
                TowerInterior.Step previous = null;
                Set<Integer> landings = new HashSet<>();
                for (var step : plan.route()) {
                    String key = v.keyAt(step.x(), step.y(), step.z());
                    assertTrue(step.stair() ? key.contains("stairs") : !v.isAirAt(step.x(), step.y(), step.z()), context + " support " + step + " " + key);
                    if (step.stair()) assertEquals(step.facing(), v.at(step.x(), step.y(), step.z()).props.get("facing"), context);
                    for (int head = 1; head <= 3; head++)
                        assertTrue(v.isAirAt(step.x(), step.y() + head, step.z()), context + " headroom " + step + " +" + head + ": " + v.keyAt(step.x(), step.y() + head, step.z()));
                    for (var cell : TowerInterior.footprint(plan, step)) {
                        assertFalse(v.isAirAt(cell.x(), step.y(), cell.z()), context + " full stair width at " + step);
                        if (step.stair()) {
                            assertTrue(v.keyAt(cell.x(), step.y(), cell.z()).contains("stairs"), context + " both lanes stay stairs through the final tread: " + step);
                            assertEquals(step.facing(), v.at(cell.x(), step.y(), cell.z()).props.get("facing"), context);
                        }
                        for (int head = 1; head <= 3; head++)
                            assertTrue(v.isAirAt(cell.x(), step.y() + head, cell.z()), context + " full-width headroom " + step + " lane " + cell + " +" + head);
                    }
                    if (previous != null) {
                        assertEquals(1, Math.abs(previous.x() - step.x()) + Math.abs(previous.z() - step.z()), context);
                        assertTrue(step.y() >= previous.y() && step.y() <= previous.y() + 1, context);
                    }
                    if (!step.stair()) landings.add(step.y() + 1);
                    previous = step;
                }
                assertTrue(landings.containsAll(plan.floors()), context + " floors need exits");
                assertTrue(reachable(v, plan.route().getFirst(), 1, 0, 0), context + " west portal to stairs");
            }
    }

    // Walk/jump graph on the actual finished collision blocks: headroom, support and one-block
    // steps. This supplements the stricter stair-direction/clearance assertions above.
    private static boolean reachable(Volume v, TowerInterior.Step target, int x, int y, int z) {
        record At(int x, int y, int z) {}
        Set<At> seen = new HashSet<>(); Deque<At> todo = new ArrayDeque<>();
        todo.add(new At(x, y, z));
        while (!todo.isEmpty()) {
            At at = todo.removeFirst();
            if (!seen.add(at)) continue;
            if (at.x == target.x() && at.z == target.z() && at.y == target.y() + 1) return true;
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for (int dy = -1; dy <= 1; dy++) {
                int nx = at.x + d[0], nz = at.z + d[1], ny = at.y + dy;
                if (!v.contains(nx, ny, nz) || ny < 0 || ny > 3) continue;
                if (v.isAirAt(nx, ny, nz) && v.isAirAt(nx, ny + 1, nz) && !v.isAirAt(nx, ny - 1, nz))
                    todo.addLast(new At(nx, ny, nz));
            }
        }
        return false;
    }

    @Test
    void everyFloorLandingConnectsToItsCombatRoomAndKeepsCornerRooms() {
        for (String type : TYPES) for (int width : new int[]{13, 19, 27}) for (long seed : new long[]{1, 7, 19}) {
            var p = Presets.get(type).params(seed).set(Params.WIDTH, width);
            Volume v = Fortification.build(p, 0);
            var plan = TowerInterior.plan(p);
            for (int feet : plan.floors()) {
                var landing = plan.route().stream().filter(q -> !q.stair() && q.y() + 1 == feet).findFirst().orElseThrow();
                Set<String> cells = floorReach(v, landing.x(), feet, landing.z());
                assertTrue(cells.contains(width / 2 + ",0"), type + "/" + width + "/" + seed + ": floor " + feet + " central combat room");
                if (type.equals("squarekeep")) for (int cx : new int[]{1, width - 2})
                    for (int cz : new int[]{-width / 2 + 1, -width / 2 + width - 2})
                        assertTrue(cells.contains(cx + "," + cz), "corner firing room " + width + "/" + seed + "/" + feet + ": " + cx + "," + cz);
            }
        }
    }

    private static Set<String> floorReach(Volume v, int x, int feet, int z) {
        Set<String> seen = new HashSet<>(); Deque<int[]> todo = new ArrayDeque<>(); todo.add(new int[]{x,z});
        while (!todo.isEmpty()) {
            int[] q = todo.removeFirst();
            if (!v.contains(q[0], feet, q[1]) || !pass(v, q[0], feet, q[1]) || !pass(v, q[0], feet + 1, q[1])
                    || v.isAirAt(q[0], feet - 1, q[1]) || !seen.add(q[0] + "," + q[1])) continue;
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) todo.add(new int[]{q[0]+d[0],q[1]+d[1]});
        }
        return seen;
    }

    private static boolean pass(Volume v, int x, int y, int z) {
        return v.isAirAt(x,y,z) || v.keyAt(x,y,z).contains("ladder");
    }

    @Test
    void floorHeightsAreEditableSeededAndDifferAcrossTowerTypes() {
        Set<List<Integer>> layouts = new HashSet<>();
        for (String type : TYPES) for (long seed : new long[]{1, 7, 19}) {
            var p = Presets.get(type).params(seed).set(Params.HEIGHT, 48);
            List<Integer> floors = TowerInterior.floorLevels(p);
            assertEquals(0, floors.getFirst()); assertEquals(48, floors.getLast());
            for (int i = 1; i < floors.size(); i++) assertTrue(floors.get(i) - floors.get(i - 1) >= 5);
            layouts.add(floors);
            assertEquals(floors, TowerInterior.floorLevels(BuildingParams.decode(p.encode())));
        }
        assertTrue(layouts.size() >= 6, layouts.toString());
        var p = Presets.get("squarekeep").params(7).set(Params.HEIGHT, 48).set(Params.VARY_FLOORS, false);
        assertEquals(List.of(0, 6, 12, 18, 24, 30, 36, 42, 48), TowerInterior.floorLevels(p));
        p.set(Params.FLOOR_HEIGHT, 12);
        assertEquals(List.of(0, 12, 24, 36, 48), TowerInterior.floorLevels(p));
    }
}
