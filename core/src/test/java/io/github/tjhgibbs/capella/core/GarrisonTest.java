package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.raster.Volume;
import io.github.tjhgibbs.capella.core.small.Fortification;
import org.junit.jupiter.api.Test;
import java.util.*;
import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import static org.junit.jupiter.api.Assertions.*;

class GarrisonTest {
    @Test void compactTowerLaddersReachSupportedClearExits() {
        for(String id:List.of("sentrytower","timberwatchtower"))
            for(int w:new int[]{7,8,9,13}) for(int h:new int[]{8,17,32}) for(int seed:new int[]{1,7}) {
                var p=Presets.get(id).params(seed).set(Params.WIDTH,w).set(Params.HEIGHT,h);
                int deck=id.equals("sentrytower")?h:h-4;
                Volume v=Fortification.build(p,0);
                for(int y=0;y<deck;y++) {
                    assertTrue(v.keyAt(w-2,y,1).contains("ladder"),id+" "+w+" "+h+" ladder "+y);
                    assertFalse(v.isAirAt(w-1,y,1));
                }
                assertFalse(v.isAirAt(w-3,deck-1,1),id+" exit support");
                for(int y=deck;y<deck+2;y++) assertTrue(v.isAirAt(w-3,y,1),id+" exit headroom");
                for(int y=0;y<3;y++) for(int x=0;x<3;x++) assertTrue(v.isAirAt(x,y,0),id+" entrance");
                assertEquals(p,BuildingParams.decode(p.encode()));
                Volume off=Fortification.build(p.copy().set(Params.ACCESS, Params.Access.NONE),0);
                assertFalse(java.util.stream.IntStream.range(0,off.paletteSize()).anyMatch(i->off.keyOfId(i).contains("ladder")));
                for(int turn=1;turn<4;turn++) {
                    Volume rotated=Fortification.build(p,turn);
                    int x=w-2,z=1;
                    for(int t=0;t<turn;t++){int nx=-z;z=x;x=nx;}
                    assertEquals(new String[]{"west","north","east","south"}[turn],rotated.at(x,0,z).props.get("facing"));
                }
            }
    }

    @Test void garrisonRangesAreTraversableOnEveryStorey() {
        for(String id:List.of("barracks","stables")) for(int w:new int[]{9,10,12,13,21})
            for(int h:new int[]{5,9,10,14,24}) for(int l:new int[]{15,29,65}) {
                var p=Presets.get(id).params(7).set(Params.WIDTH,w).set(Params.HEIGHT,h).set(Params.LENGTH,l);
                Volume v=Fortification.build(p,0);
                String tag=id+" w"+w+" h"+h+" l"+l;
                int z0=-w/2, z1=z0+w-1, storeys=Math.max(1,(h+1)/5);
                // In through the west door, along the ground floor.
                int dz=id.equals("stables") && w<13 ? z0+2 : 0;
                for(int y=0;y<2;y++) assertTrue(passable(v,0,y,dz),tag+" door");
                Set<Long> seen=walk(v,0,0,dz);
                for(int i=0;i<storeys;i++) {
                    int y=5*i, standable=0, reached=0; StringBuilder missed=new StringBuilder();
                    for(int x=1;x<l-1;x++) for(int z=z0+1;z<z1;z++) if(stand(v,x,y,z)) {
                        standable++;
                        if(seen.contains(key(x,y,z))) reached++; else missed.append(" ").append(x).append(",").append(z);
                    }
                    assertTrue(standable>(l-2)*(w-2)/4,tag+" storey "+i+" floor area "+standable);
                    assertTrue(reached>=standable*0.97,tag+" storey "+i+" reached "+reached+"/"+standable+" missed"+missed);
                }
                if(id.equals("stables")) for(int x=0;x<l;x++) {
                    int zc=w>=13?0:z0+2;
                    for(int y=0;y<3;y++) assertTrue(passable(v,x,y,zc),tag+" aisle "+x+","+y);
                }
                assertEquals(p,BuildingParams.decode(p.encode()));
                Volume off=Fortification.build(p.copy().set(Params.FURNISHINGS,false),0);
                for(int i=0;i<off.paletteSize();i++) {
                    String key=off.keyOfId(i);
                    assertFalse(key.contains("_bed") || key.contains("hay_block") || key.contains("cauldron") || key.contains("barrel"),key);
                }
                for(int turn=0;turn<4;turn++) for(int i=0;i<v.paletteSize();i++)
                    assertTrue(Blockstates.domainViolations(v.stateOfId(i)).isEmpty(),v.keyOfId(i));
                assertTrue(Params.applies(Params.LENGTH,p.get(Params.TYPE)));
                assertFalse(Params.applies(Params.FLOOR_HEIGHT,p.get(Params.TYPE)));
            }
    }

    @Test void barracksAndStablesAreDifferentBuildings() {
        Volume b=Fortification.build(Presets.get("barracks").params(3),0), s=Fortification.build(Presets.get("stables").params(3),0);
        var bk=b.blockCounts(); var sk=s.blockCounts();
        assertTrue(bk.keySet().stream().anyMatch(k->k.contains("campfire")),"barracks chimney");
        assertTrue(bk.keySet().stream().anyMatch(k->k.contains("_bed")),"barracks bunks");
        assertTrue(sk.keySet().stream().anyMatch(k->k.contains("_door")),"stable doors");
        assertTrue(sk.keySet().stream().anyMatch(k->k.contains("ladder")),"hayloft ladder");
        assertFalse(sk.keySet().stream().anyMatch(k->k.contains("_bed")),"two-storey stables keep the loft for hay");
    }

    private static long key(int x,int y,int z){return ((long)(x+512)<<22)|((long)(y+512)<<11)|(z+512);}

    private static boolean passable(Volume v,int x,int y,int z) {
        if(v.isAirAt(x,y,z)) return true;
        String k=v.keyAt(x,y,z);
        return k.contains("carpet") || k.contains("ladder") || k.contains("_door") || k.contains("fence_gate") || k.contains("open=true");
    }

    private static boolean ladder(Volume v,int x,int y,int z){return v.keyAt(x,y,z).contains("ladder");}

    private static boolean stand(Volume v,int x,int y,int z) {
        if(!passable(v,x,y,z) || !passable(v,x,y+1,z)) return false;
        return ladder(v,x,y,z) || !passable(v,x,y-1,z) && v.contains(x,y-1,z);
    }

    /** Cells a player's feet can reach: walking, one-block steps up, drops, and ladders. */
    private static Set<Long> walk(Volume v,int sx,int sy,int sz) {
        Set<Long> seen=new HashSet<>(); ArrayDeque<int[]> q=new ArrayDeque<>();
        q.add(new int[]{sx,sy,sz}); seen.add(key(sx,sy,sz));
        int[][] dirs={{1,0},{-1,0},{0,1},{0,-1}};
        while(!q.isEmpty()) {
            int[] c=q.poll(); int x=c[0],y=c[1],z=c[2];
            List<int[]> next=new ArrayList<>();
            if(ladder(v,x,y,z)) { next.add(new int[]{x,y+1,z}); next.add(new int[]{x,y-1,z}); }
            for(int[] d:dirs) {
                int nx=x+d[0], nz=z+d[1];
                if(stand(v,nx,y,nz)) next.add(new int[]{nx,y,nz});
                else if(passable(v,x,y+2,z) && stand(v,nx,y+1,nz)) next.add(new int[]{nx,y+1,nz});
                else if(passable(v,nx,y,nz) && passable(v,nx,y+1,nz)) {
                    for(int fy=y-1;fy>y-30 && passable(v,nx,fy,nz);fy--) if(stand(v,nx,fy,nz)) { next.add(new int[]{nx,fy,nz}); break; }
                }
            }
            for(int[] n:next) if(stand(v,n[0],n[1],n[2]) && seen.add(key(n[0],n[1],n[2]))) q.add(n);
        }
        return seen;
    }
}
