package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.small.*;
import io.github.tjhgibbs.capella.core.raster.Volume;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeepWindowTest {
    private static int[] point(int w, int face, int u, int y, int depth) {
        return switch(face) {
            case 0 -> new int[]{depth,y,u};
            case 1 -> new int[]{w-1-depth,y,u};
            case 2 -> new int[]{w/2+u,y,-w/2+depth};
            default -> new int[]{w/2+u,y,-w/2+w-1-depth};
        };
    }

    @Test void finishedWindowsHaveOpenRevealsSupportedSillsAndClearHoods() {
        for (int w : new int[]{13,14,19,20,25,26,35}) for (int h : new int[]{12,38})
            for (long seed : new long[]{1,7,19}) {
                var p=Presets.get("squarekeep").params(seed).set(Params.WIDTH,w).set(Params.HEIGHT,h);
                Volume v=Fortification.build(p,0);
                var floors=TowerInterior.floorLevels(p);
                // Locate apertures by their outer opening rather than copying the window placement rules.
                int found=0;
                for(int face=0;face<4;face++) for(int level=1;level<floors.size()-1;level++) {
                    int y=floors.get(level)+2;
                    for(int u=-w/2+(w>=21?3:2)+2;u<=-w/2+w-(w>=21?3:2)-3;u++) {
                        int[] q=point(w,face,u,y,0);
                        if(!v.isAirAt(q[0],q[1],q[2])) continue;
                        found++;
                        for(int d=0;d<3;d++) {
                            int[] inside=point(w,face,u,y,d);
                            assertTrue(v.isAirAt(inside[0],inside[1],inside[2]),"Blocked reveal "+w+"/"+h+"/"+seed+" face "+face);
                            int[] sill=point(w,face,u,y-1,d);
                            assertFalse(v.isAirAt(sill[0],sill[1],sill[2]),"Unsupported sill "+w+"/"+h+"/"+seed+" face "+face+" u "+u+" y "+y+" depth "+d);
                        }
                        int[] outside=point(w,face,u,y,-1);
                        assertTrue(v.isAirAt(outside[0],outside[1],outside[2]),"Gable/hood blocks window "+w+"/"+h+"/"+seed+" face "+face+" u "+u+" y "+y+" "+v.keyAt(outside[0],outside[1],outside[2]));
                    }
                }
                assertTrue(found>=4,"Keep must have usable upper windows: "+w+"/"+h+"/"+seed);
            }
    }

    @Test void broadKeepsHavePairedLightsAndWiderInnerEmbrasures() {
        var p=Presets.get("squarekeep").params(7).set(Params.VARY_FLOORS,false).set(Params.HEIGHT,32);
        Volume v=Fortification.build(p,0);
        int base=TowerInterior.floorLevels(p).get(1),y=base+2;
        // Default 27-block keep: a five-block-wide inner reveal for each paired outer light.
        for(int centre:new int[]{-5,5}) {
            assertTrue(v.isAirAt(0,y,centre-1)); assertTrue(v.isAirAt(0,y,centre+1));
            assertTrue(v.keyAt(0,y,centre).contains("wall"));
            for(int z=centre-2;z<=centre+2;z++) {
                assertTrue(v.isAirAt(1,y,z)); assertTrue(v.isAirAt(2,y,z));
            }
        }
        assertFalse(v.isAirAt(0,y,0),"Central wall field stays quiet and solid");
    }
}
