package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.cathedral.*;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.layout.Portal;
import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.voxel.BlockVolume;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CathedralInteriorTest {
    record Fixture(BuildingParams p, PlanLayout l, BlockVolume v) {
        int[] at(int x,int y,int z) {
            Portal door=io.github.tjhgibbs.capella.core.layout.StructureLayout.placeLayout(CathedralPlan.structureLayoutOf(l),l.frame).portals.stream().filter(q -> q.kind==Portal.Kind.WEST_GREAT).findFirst().orElseThrow();
            Vec3 a=l.frame.point(new Vec3(x,y,z)), d=door.position;
            return new int[]{(int)Math.round(a.x())-(int)Math.floor(d.x())+v.originX,
                    y-(int)door.threshold+v.originY,(int)Math.round(a.z())-(int)Math.floor(d.z())+v.originZ};
        }
        String get(int x,int y,int z) {int[] a=at(x,y,z);return v.get(a[0],a[1],a[2]);}
    }

    static Fixture fixture(String preset,long seed,int turn) {
        BuildingParams p=Presets.get(preset).params(seed);
        var scene=new Scene(seed,"limestone",1024,1024,
                io.github.tjhgibbs.capella.core.field.Field.constant(io.github.tjhgibbs.capella.core.field.Field.TERRAIN_HEIGHT,1024,1024,64),
                BuildingGenerator.planOf(p),"cathedral",null,null,turn*90,Scene.NEUTRAL_RUINATION);
        return new Fixture(p,CathedralPlan.resolveLayout(scene),BuildingGenerator.generate(p,turn).volume());
    }

    @Test void lightActuallyReachesNaveAndAisleFloors() {
        for(String preset:List.of("cathedral","great","colossal")) {
            Fixture f=fixture(preset,7,0);
            byte[] light=propagate(f.v);
            for(Bay b:f.l.bays) {
                if(b.kind==Bay.Kind.TOWER) continue;
                int fy=(int)(b.raised?f.l.levels.choirFloorY:f.l.levels.floorY);
                int x=(int)Math.floor((b.x0+b.x1)/2);
                List<Integer> samples=new ArrayList<>(List.of((int)f.l.axisZ));
                if(!b.isCrossing) for(SideBands bands:List.of(f.l.north,f.l.south)) for(var a:bands.aisles)
                    samples.add((int)Math.floor((a.z0()+a.z1())/2));
                for(int z:samples) {
                    int[] q=f.at(x,fy+1,z);
                    if(!transparent(f.v.get(q[0],q[1],q[2]))) continue;
                    assertTrue(light[index(f.v,q[0],q[1],q[2])]>0,preset+" unlit route in bay "+b.index+" z="+z);
                }
            }
        }
    }

    @Test void choirLoftHasWideAccessibleStairAndArrival() {
        for(int turn:new int[]{0,1,2,3}) {
            Fixture f=fixture("great",3,turn);
            Bay b=f.l.bays.stream().filter(q -> q.kind==Bay.Kind.CHOIR).findFirst().orElseThrow();
            int fy=(int)f.l.levels.choirFloorY, x0=(int)b.x0, z=(int)f.l.naveZ1-2;
            for(int k=0;k<5;k++) for(int dz=0;dz<2;dz++) {
                int x=x0+8-k;
                assertTrue(f.get(x,fy+k,z-dz).contains("_stairs"),"complete choir tread "+k+" turn "+turn);
                assertTrue(transparent(f.get(x,fy+k+1,z-dz)),"choir stair headroom");
                assertTrue(transparent(f.get(x,fy+k+2,z-dz)),"choir stair headroom");
            }
            for(int k=0;k<5;k++) for(int edge:new int[]{z+1,z-2}) {
                int x=x0+8-k;
                assertFalse(transparent(f.get(x,fy+k+1,edge)),"guard beside exposed stair edge");
                for(int y=fy;y<=fy+k;y++)
                    assertFalse(transparent(f.get(x,y,edge)),"stair guard has continuous masonry support");
            }
            for(int dz=0;dz<2;dz++) {
                assertTrue(transparent(f.get(x0+3,fy+5,z-dz)),"loft entrance parapet");
                assertFalse(transparent(f.get(x0+3,fy+4,z-dz)),"loft landing floor");
            }
        }
    }

    @Test void sideBaysAreSeededAndOptional() {
        Set<String> themes=new HashSet<>();
        for(int seed:new int[]{1,2,7}) {
            Fixture f=fixture("great",seed,0);
            f.v.palette().stream().filter(s -> s.contains("lectern")||s.contains("bookshelf")||s.contains("candles=2")).forEach(themes::add);
        }
        assertTrue(themes.stream().anyMatch(s->s.contains("bookshelf")),"reading bays");
        assertTrue(themes.stream().anyMatch(s->s.contains("lectern")),"reading desks");
        BlockVolume bare=BuildingGenerator.generate(Presets.get("great").params(1).set(Params.FURNISHINGS,false)).volume();
        assertFalse(bare.palette().stream().anyMatch(s->s.contains("bookshelf")||s.contains("lectern")),"furnishings toggle");
    }

    static int emission(String s) {
        if(s.startsWith("minecraft:lantern")) return 15;
        if(s.contains("candle[")&&s.contains("lit=true")) {
            for(int i=1;i<=4;i++) if(s.contains("candles="+i))return i*3;
        }
        return 0;
    }
    static boolean transparent(String s) {
        return s.endsWith(":air")||s.endsWith(":cave_air")||s.contains("chain")||s.contains("lantern")||s.contains("candle");
    }
    static int index(BlockVolume v,int x,int y,int z) {return(y*v.length+z)*v.width+x;}
    static byte[] propagate(BlockVolume v) {
        byte[] levels=new byte[v.width*v.height*v.length];
        ArrayDeque<Integer> queue=new ArrayDeque<>();
        v.forEachBlock((x,y,z,s)->{int light=emission(s);if(light>0){int i=index(v,x,y,z);levels[i]=(byte)light;queue.add(i);}});
        while(!queue.isEmpty()) {
            int i=queue.remove(),x=i%v.width,z=i/v.width%v.length,y=i/(v.width*v.length),next=levels[i]-1;
            if(next<=0)continue;
            for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}}) {
                int nx=x+d[0],ny=y+d[1],nz=z+d[2];
                if(nx<0||ny<0||nz<0||nx>=v.width||ny>=v.height||nz>=v.length)continue;
                int j=index(v,nx,ny,nz);
                if(levels[j]<next && transparent(v.get(nx,ny,nz))) {levels[j]=(byte)next;queue.add(j);}
            }
        }
        return levels;
    }
}
