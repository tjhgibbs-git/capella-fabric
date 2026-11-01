package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.cathedral.*;
import io.github.tjhgibbs.capella.core.field.Field;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.layout.Portal;
import io.github.tjhgibbs.capella.core.layout.StructureLayout;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Whole-building route checks run after all masonry, stairs, furnishing and decoration passes. */
class CathedralPlayabilityTest {
    private record Case(String preset, long seed, int turns) {}
    private static final List<Case> CASES = List.of(
            new Case("cathedral", 1, 0), new Case("great", 7, 1), new Case("minster", 19, 2),
            new Case("dark", 31, 3), new Case("basilica", 7, 0), new Case("colossal", 19, 1));

    @Test
    void furnishedRoutesConnectTheGroundFloorAndEveryGalleryRun() {
        List<String> failures = new ArrayList<>();
        for (Case c : CASES) {
            BuildingParams p = Presets.get(c.preset).params(c.seed);
            PlanLayout l = layout(p, c.turns);
            BlockVolume v = BuildingGenerator.generate(p, c.turns).volume();
            Model m = new Model(v, l);
            int floor = (int) l.levels.floorY, choir = (int) l.levels.choirFloorY;
            int start = m.find((int) l.westX + 3, floor, (int) l.axisZ, 2);
            assertTrue(start >= 0, c + " supported, clear entrance to nave");
            BitSet reached = m.walk(start, choir + 1);
            for (Bay b : l.bays) {
                int x = (int) Math.floor((b.x0 + b.x1) / 2);
                int y = b.raised ? choir : floor;
                for (SideBands bands : List.of(l.north, l.south)) for (Band aisle : bands.aisles) {
                    int z = (int) Math.floor((aisle.z0() + aisle.z1()) / 2);
                    if (!m.reaches(reached, x, y, z, 2)) failures.add(c + " aisle " + (bands == l.north ? "north" : "south") + " bay " + b.index + " @" + x + "," + y + "," + z);
                }
            }
            if (l.levels.triforiumDeckY - l.levels.floorY >= 4) {
                int deck = (int) l.levels.triforiumDeckY;
                BitSet upper = m.walk(start, deck + 1);
                for (Bay b : l.bays) {
                    if (b.isCrossing || b.kind == Bay.Kind.TOWER) continue;
                    int x = (int) Math.floor((b.x0 + b.x1) / 2);
                    for (SideBands bands : List.of(l.north, l.south)) for (Band aisle : bands.aisles) {
                        int z = (int) Math.floor((aisle.z0() + aisle.z1()) / 2);
                        if (!m.reaches(upper, x, deck, z, 2)) failures.add(c + " gallery "
                                + (bands == l.north ? "north" : "south") + " bay " + b.index);
                    }
                }
            }
            for (Crossing crossing : l.crossings) for (int side : new int[]{-1, 1}) {
                int x = (int) Math.floor((crossing.bay.x0 + crossing.bay.x1) / 2);
                int z = (int) Math.floor(l.axisZ + side * (crossing.reach - l.wallThickness - 4));
                if (!m.reaches(reached, x, crossing.bay.raised ? choir : floor, z, 2)) failures.add(c + " transept " + crossing.tag + " side " + side);
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    private static PlanLayout layout(BuildingParams p, int turns) {
        return CathedralPlan.resolveLayout(new Scene(p.seed(), "limestone", 1024, 1024,
                Field.constant(Field.TERRAIN_HEIGHT, 1024, 1024, 64), BuildingGenerator.planOf(p),
                "cathedral", null, null, turns * 90, Scene.NEUTRAL_RUINATION));
    }

    private static final class Model {
        final BlockVolume v;
        final PlanLayout l;
        final int dx, dy, dz;
        final boolean[] clear, support;
        Model(BlockVolume v, PlanLayout l) {
            this.v = v; this.l = l;
            Portal door = StructureLayout.placeLayout(CathedralPlan.structureLayoutOf(l), l.frame).portals.stream()
                    .filter(p -> p.kind == Portal.Kind.WEST_GREAT).findFirst().orElseThrow();
            Vec3 worldDoor = door.position;
            dx = v.originX - (int) Math.floor(worldDoor.x());
            dy = v.originY - (int) Math.floor(door.threshold);
            dz = v.originZ - (int) Math.floor(worldDoor.z());
            clear = new boolean[v.palette().size()]; support = new boolean[clear.length];
            for (int i = 0; i < clear.length; i++) {
                String s = v.palette().get(i);
                clear[i] = s.endsWith(":air") || s.endsWith(":cave_air") || s.endsWith(":void_air")
                        || s.contains("_carpet") || s.contains("_door[") || s.contains(":ladder[")
                        || s.contains("_torch") || s.contains(":torch") || s.contains("_banner");
                support[i] = !clear[i] && !s.contains("_wall[") && !s.contains("_fence[")
                        && !s.contains(":chain[") && !s.contains(":lantern[") && !s.contains("candle");
            }
        }
        int[] at(int x, int y, int z) {
            Vec3 q = l.frame.point(new Vec3(x, y, z));
            return new int[]{(int) Math.round(q.x()) + dx, y + dy, (int) Math.round(q.z()) + dz};
        }
        boolean stand(int x, int y, int z) {
            return x >= 0 && z >= 0 && x < v.width && z < v.length && y >= 1 && y + 1 < v.height
                    && clear[v.idAt(x,y,z)] && clear[v.idAt(x,y+1,z)] && support[v.idAt(x,y-1,z)];
        }
        int find(int x, int y, int z, int radius) {
            for (int d = 0; d <= radius; d++) for (int xx = x-d; xx <= x+d; xx++) for (int zz=z-d; zz<=z+d; zz++) {
                int[] q=at(xx,y,zz);
                if (stand(q[0],q[1],q[2])) return v.index(q[0],q[1],q[2]);
            }
            return -1;
        }
        boolean reaches(BitSet reached, int x, int y, int z, int radius) {
            for (int xx=x-radius; xx<=x+radius; xx++) for (int zz=z-radius; zz<=z+radius; zz++) {
                int[] q=at(xx,y,zz);
                if (stand(q[0],q[1],q[2]) && reached.get(v.index(q[0],q[1],q[2]))) return true;
            }
            return false;
        }
        BitSet walk(int start, int highestLocalY) {
            BitSet seen = new BitSet(v.data().length);
            ArrayDeque<Integer> q = new ArrayDeque<>(); q.add(start); seen.set(start);
            int plane=v.width*v.length, maxY=highestLocalY+dy;
            while (!q.isEmpty()) {
                int id=q.remove(), y=id/plane, z=(id%plane)/v.width, x=id%v.width;
                for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for(int step=-1;step<=1;step++) {
                    int xx=x+d[0], yy=y+step, zz=z+d[1];
                    if (yy>maxY || !stand(xx,yy,zz)) continue;
                    if (step>0 && !clear[v.idAt(x,y+2,z)]) continue;
                    if (step<0 && !clear[v.idAt(xx,yy+2,zz)]) continue;
                    int next=v.index(xx,yy,zz);
                    if(!seen.get(next)) {seen.set(next);q.add(next);}
                }
            }
            return seen;
        }
    }
}
