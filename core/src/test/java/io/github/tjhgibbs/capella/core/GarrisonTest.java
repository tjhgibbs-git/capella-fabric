package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.raster.Volume;
import io.github.tjhgibbs.capella.core.small.Fortification;
import org.junit.jupiter.api.Test;
import java.util.List;
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
                Volume off=Fortification.build(p.copy().set(Params.ACCESS,false),0);
                assertFalse(java.util.stream.IntStream.range(0,off.paletteSize()).anyMatch(i->off.keyOfId(i).contains("ladder")));
                for(int turn=1;turn<4;turn++) {
                    Volume rotated=Fortification.build(p,turn);
                    int x=w-2,z=1;
                    for(int t=0;t<turn;t++){int nx=-z;z=x;x=nx;}
                    assertEquals(new String[]{"west","north","east","south"}[turn],rotated.at(x,0,z).props.get("facing"));
                }
            }
    }

    @Test void garrisonRangesKeepThroughRoutesClearAtEverySize() {
        for(String id:List.of("barracks","stables")) for(int w:new int[]{9,10,21})
            for(int h:new int[]{5,12}) for(int l:new int[]{15,65}) {
                var p=Presets.get(id).params(7).set(Params.WIDTH,w).set(Params.HEIGHT,h).set(Params.LENGTH,l);
                Volume v=Fortification.build(p,0);
                for(int x=0;x<l;x++) for(int z=-1;z<=1;z++) {
                    assertFalse(v.isAirAt(x,-1,z));
                    for(int y=0;y<3;y++) assertTrue(v.isAirAt(x,y,z),id+" aisle "+x+","+y+","+z);
                }
                Volume off=Fortification.build(p.copy().set(Params.FURNISHINGS,false),0);
                for(int i=0;i<off.paletteSize();i++) {
                    String key=off.keyOfId(i);
                    assertFalse(key.contains("_bed") || key.contains("hay_block") || key.contains("cauldron") || key.contains("barrel"),key);
                }
                assertTrue(Params.applies(Params.LENGTH,p.get(Params.TYPE)));
                assertFalse(Params.applies(Params.FLOOR_HEIGHT,p.get(Params.TYPE)));
            }
    }
}
