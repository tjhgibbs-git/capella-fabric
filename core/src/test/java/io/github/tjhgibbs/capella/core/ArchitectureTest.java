package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.cathedral.*;
import io.github.tjhgibbs.capella.core.field.Field;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    @Test
    void towerHeightSurvivesEncodingAndAddsFloorsAboveEighty() {
        for (int width : new int[]{13, 21, 31}) {
            int previous = 0;
            for (int height : new int[]{80, 120, 160}) {
                BuildingParams p = Presets.get("westtower").params(3).set(Params.WIDTH, width).set(Params.HEIGHT, height);
                assertEquals(height, BuildingParams.decode(p.encode()).integer(Params.HEIGHT));
                BlockVolume v = BuildingGenerator.generate(p).volume();
                Set<Integer> floors = new HashSet<>();
                v.forEachBlock((x,y,z,s) -> {
                    if (x == v.originX + width / 2 && z == v.originZ && s.endsWith("_planks")) floors.add(y);
                });
                assertTrue(floors.size() > previous, width + " wide, " + height + " high: " + floors);
                assertTrue(v.height >= height, "requested wall height is honoured");
                previous = floors.size();
            }
        }
    }

    static PlanLayout layout(BuildingParams p) {
        return CathedralPlan.resolveLayout(new Scene(p.seed(), "limestone", 1024, 1024,
                Field.constant(Field.TERRAIN_HEIGHT, 1024, 1024, 64), BuildingGenerator.planOf(p),
                "cathedral", null, null, 0, Scene.NEUTRAL_RUINATION));
    }

    @Test
    void transeptShouldersCloseAboveTheAisleRoof() {
        for (String preset : List.of("cathedral", "great", "minster", "dark")) {
            BuildingParams p = Presets.get(preset).params(1).set(Params.GALLERY_STAIRS, false);
            PlanLayout l = layout(p);
            BlockVolume v = BuildingGenerator.generate(p).volume();
            // Frame is centred on the scene; the output origin is the west portal.
            var door = l.portals.stream().filter(a -> a.kind == io.github.tjhgibbs.capella.core.layout.Portal.Kind.WEST_GREAT).findFirst().orElse(l.portals.getFirst());
            for (Crossing c : l.crossings) for (int side : new int[]{-1,1}) {
                double edge = side < 0 ? l.naveZ0 - l.arcadeDepth : l.naveZ1 + l.arcadeDepth;
                double outer = side < 0 ? l.north.outerZ : l.south.outerZ;
                Vec3 pt = l.frame.point(new Vec3(c.bay.x1 + 1, l.levels.aisleWallTopY + 2, (edge + outer) / 2));
                Vec3 dp = l.frame.point(door.position);
                int x = (int)Math.floor(pt.x()) - (int)Math.floor(dp.x()) + v.originX;
                int y = (int)pt.y() - (int)door.threshold + v.originY;
                int z = (int)Math.floor(pt.z()) - (int)Math.floor(dp.z()) + v.originZ;
                assertTrue(java.util.stream.IntStream.range(0, (int)l.wallThickness).anyMatch(k -> !v.get(x-k,y,z).endsWith(":air")), preset + " shoulder " + side);
            }
        }
    }

    @Test
    void cathedralStairsReachTheGalleryWithHeadroom() {
        for (String preset : List.of("cathedral", "great", "minster")) {
            BuildingParams p = Presets.get(preset).params(1).set(Params.FURNISHINGS, false);
            PlanLayout l = layout(p);
            BlockVolume v = BuildingGenerator.generate(p).volume();
            var door = l.portals.stream().filter(a -> a.kind == io.github.tjhgibbs.capella.core.layout.Portal.Kind.WEST_GREAT).findFirst().orElseThrow();
            int ox = v.originX - (int)door.position.x(), oz = v.originZ - (int)door.position.z();
            int fy = (int)l.levels.floorY - (int)door.threshold + v.originY;
            int dy = (int)l.levels.triforiumDeckY - (int)door.threshold + v.originY;
            Bay bay = l.bays.stream().filter(b -> b.kind == Bay.Kind.NAVE && !b.raised).findFirst().orElseThrow();
            for (SideBands bands : List.of(l.north,l.south)) {
                var aisle = bands.aisles.getLast();
                int zw = bands == l.north ? (int)aisle.z0() : (int)aisle.z1()-1;
                int xs = (int)bay.x0+1;
                Set<Cell> reached = walk(v,new Cell(xs+ox,fy,zw+oz),xs+ox,(int)bay.x1+ox,
                        (int)aisle.z0()+oz,(int)aisle.z1()-1+oz);
                assertTrue(reached.stream().anyMatch(c -> c.y==dy), preset + " gallery " + zw + " start=" + v.get(xs+ox,fy,zw+oz) + " max="+reached.stream().mapToInt(c -> c.y).max().orElse(-1) + " target="+dy + " xs="+xs + " run="+bay.span);
            }
        }
    }

    @Test
    void smallChurchSeedsChangeGeometryAndKeepSunkenFloorsAndLoftsAccessible() {
        Set<Integer> geometries = new HashSet<>();
        boolean sunken = false, loft = false, northPorch = false, southPorch = false;
        for (int seed = 1; seed <= 16; seed++) {
            BlockVolume v = BuildingGenerator.generate(Presets.get("parish").params(seed)).volume();
            int[] occupancy = Arrays.stream(v.data()).map(id -> id == 0 ? 0 : 1).toArray();
            geometries.add(Arrays.hashCode(occupancy));
            Set<Cell> reached = walk(v, new Cell(v.originX + 2, v.originY, v.originZ),
                    v.originX, v.originX + 47, v.originZ - 5, v.originZ + 5);
            for (int x = 10; x < 28; x++) {
                for (int z = -3; z <= 3; z++) {
                    if (v.originY >= 2 && v.get(v.originX+x, v.originY-2, v.originZ+z).equals("minecraft:cave_air")) {
                        sunken = true;
                        assertTrue(reached.stream().anyMatch(c -> c.y < v.originY), "sunken floor seed " + seed);
                    }
                }
            }
            if (v.palette().stream().anyMatch(s -> s.contains("_trapdoor") && s.contains("open=true"))) {
                loft = true;
                assertTrue(reached.stream().anyMatch(c -> c.y == v.originY + 4), "loft seed " + seed);
            }
            // Porch doors occur on either side, away from the west tower.
            for (int x=8;x<32;x++) {
                northPorch |= v.get(v.originX+x, v.originY, v.originZ-6).contains("_door");
                southPorch |= v.get(v.originX+x, v.originY, v.originZ+6).contains("_door");
            }
        }
        assertTrue(geometries.size() >= 6, "layout, not just stone mixing");
        assertTrue(sunken && loft && northPorch && southPorch);
    }

    record Cell(int x,int y,int z) {}
    // A conservative two-block-high walker. A stair is treated as a full supported step;
    // the route must work without passing through solid blocks or jumping gaps.
    static Set<Cell> walk(BlockVolume v, Cell start, int x0,int x1,int z0,int z1) {
        Set<Cell> seen = new HashSet<>();
        ArrayDeque<Cell> q = new ArrayDeque<>(); q.add(start); seen.add(start);
        while (!q.isEmpty()) {
            Cell c=q.remove();
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for (int dy=-1;dy<=1;dy++) {
                Cell n=new Cell(c.x+d[0],c.y+dy,c.z+d[1]);
                if(n.x<x0||n.x>x1||n.z<z0||n.z>z1||n.y<1||n.y+1>=v.height||seen.contains(n)) continue;
                if(clear(v.get(n.x,n.y,n.z)) && clear(v.get(n.x,n.y+1,n.z)) && !clear(v.get(n.x,n.y-1,n.z))) {
                    // Going up also needs headroom above the lower step.
                    if(dy>0&&!clear(v.get(c.x,c.y+2,c.z)))continue;
                    seen.add(n);q.add(n);
                }
            }
        }
        return seen;
    }
    static boolean clear(String s) {
        return s.endsWith(":air") || s.endsWith(":cave_air") || s.contains("_carpet") || s.contains("_door[") || s.contains("ladder");
    }
}
