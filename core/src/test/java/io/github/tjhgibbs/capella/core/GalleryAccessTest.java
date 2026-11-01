package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.cathedral.*;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.layout.Portal;
import io.github.tjhgibbs.capella.core.layout.StructureLayout;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GalleryAccessTest {
    @Test
    void stairsReachWalkableGalleryBeyondTheirShaft() {
        for (String preset : List.of("abbey", "cathedral", "great", "minster", "dark", "basilica", "colossal")) {
            for (long seed : new long[]{1, 17}) {
                BuildingParams p = Presets.get(preset).params(seed).set(Params.FURNISHINGS, false);
                PlanLayout l = ArchitectureTest.layout(p);
                BlockVolume v = BuildingGenerator.generate(p).volume();
                Portal door = StructureLayout.placeLayout(CathedralPlan.structureLayoutOf(l), l.frame).portals.stream()
                        .filter(a -> a.kind == Portal.Kind.WEST_GREAT).findFirst().orElseThrow();
                Bay bay = l.bays.stream().filter(b -> b.kind == Bay.Kind.NAVE && !b.raised).findFirst().orElseThrow();
                int run = Math.min(8, (int)bay.span - 3), xs = (int)bay.x0 + 1, xe = xs + run + 1;
                int fy = (int)l.levels.floorY, dy = (int)l.levels.triforiumDeckY;
                int rise = dy - fy;
                if (run < 4 || rise < 4) continue;
                for (SideBands bands : List.of(l.north, l.south)) {
                    var aisle = bands.aisles.getLast();
                    if (aisle.z1()-aisle.z0()<7) continue;
                    int in = bands == l.north ? 1 : -1;
                    int zw = in > 0 ? (int)aisle.z0() : (int)aisle.z1()-1;
                    int flight = (rise - 1) / run;
                    boolean east = flight % 2 == 0;
                    int laneZ = zw + (flight % 2 == 0 ? 0 : 3*in);
                    var start = cell(v,l,door,xs,fy,zw+6*in);
                    Set<ArchitectureTest.Cell> reached = ArchitectureTest.walk(v,start,
                            start.x()-2,start.x()+run+4,(int)aisle.z0()-(int)door.position.z()+v.originZ,
                            (int)aisle.z1()-(int)door.position.z()+v.originZ);
                    var beyond = cell(v,l,door,east ? xe+1 : xs-1,dy,laneZ);
                    assertTrue(reached.contains(beyond),preset+" seed "+seed+" side "+in+" arrival beyond shaft "+beyond);
                    // A landing must protect the drop to the returning flight with supported masonry.
                    int railX = xs+2, inner = zw+5*in;
                    var r = cell(v,l,door,railX,dy,inner);
                    assertFalse(ArchitectureTest.clear(v.get(r.x(),r.y(),r.z())),"gallery edge rail "+preset);
                    assertFalse(ArchitectureTest.clear(v.get(r.x(),r.y()-1,r.z())),"rail supported "+preset);
                }
            }
        }
    }

    @Test
    void stairTreadsAndLandingHeadroomTurnWithTheBuilding() {
        BuildingParams p = Presets.get("cathedral").params(17).set(Params.FURNISHINGS, false);
        for (int turns = 0; turns < 4; turns++) {
            PlanLayout l = CathedralPlan.resolveLayout(new Scene(p.seed(), "limestone", 1024, 1024,
                    io.github.tjhgibbs.capella.core.field.Field.constant(
                            io.github.tjhgibbs.capella.core.field.Field.TERRAIN_HEIGHT, 1024, 1024, 64),
                    BuildingGenerator.planOf(p), "cathedral", null, null, turns * 90, Scene.NEUTRAL_RUINATION));
            BlockVolume v = BuildingGenerator.generate(p, turns).volume();
            Portal door = StructureLayout.placeLayout(CathedralPlan.structureLayoutOf(l), l.frame).portals.stream()
                    .filter(a -> a.kind == Portal.Kind.WEST_GREAT).findFirst().orElseThrow();
            Bay first = l.bays.stream().filter(b -> b.kind == Bay.Kind.NAVE && !b.raised).findFirst().orElseThrow();
            int x = (int)first.x0 + 2, y = (int)l.levels.floorY;
            for (SideBands bands : List.of(l.north, l.south)) {
                var aisle = bands.aisles.getLast();
                int z = bands == l.north ? (int)aisle.z0() : (int)aisle.z1()-1;
                var tread = cell(v,l,door,x,y,z);
                String state = v.get(tread.x(),tread.y(),tread.z());
                assertTrue(state.contains("stairs"), "stair retained at rotation " + turns + ": " + state);
                assertTrue(state.contains("facing=" + List.of("east","south","west","north").get(turns)), state);
                assertTrue(ArchitectureTest.clear(v.get(tread.x(),tread.y()+1,tread.z())), state);
                assertTrue(ArchitectureTest.clear(v.get(tread.x(),tread.y()+2,tread.z())), state);
            }
        }
    }

    private static ArchitectureTest.Cell cell(BlockVolume v, PlanLayout l, Portal door, int x,int y,int z) {
        Vec3 point = l.frame.point(new Vec3(x,y,z));
        Vec3 portal = door.position;
        return new ArchitectureTest.Cell((int)Math.round(point.x())-(int)Math.floor(portal.x())+v.originX,
                y-(int)door.threshold+v.originY,(int)Math.round(point.z())-(int)Math.floor(portal.z())+v.originZ);
    }
}
