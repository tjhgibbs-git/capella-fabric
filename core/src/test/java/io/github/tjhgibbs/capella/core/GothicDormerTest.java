package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.small.Fortification;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GothicDormerTest {
    private static int[] point(int w,int face,int u,int y,int d) {
        return switch(face) {
            case 0 -> new int[]{d,y,u};
            case 1 -> new int[]{w-1-d,y,u};
            case 2 -> new int[]{w/2+u,y,-w/2+d};
            default -> new int[]{w/2+u,y,-w/2+w-1-d};
        };
    }
    @Test void everyDormerHasRecessedLightsOpenBackingAndBondedHeadsAndSills() {
        for(int w:new int[]{13,14,19,35}) for(int h:new int[]{12,38,96}) for(var stone:Params.Stone.values()) {
            var p=Presets.get("gothicturret").params(7).set(Params.WIDTH,w).set(Params.HEIGHT,h).set(Params.STONE,stone);
            var v=Fortification.build(p,0);int centre=w%2==0?-1:0;
            for(int face=0;face<4;face++) for(int u=-1;u<=1;u++) {
                int rise=4-Math.abs(u);
                for(int dy=0;dy<rise;dy++) {
                    int y=h+5+dy;
                    int[] front=point(w,face,centre+u,y,0),glass=point(w,face,centre+u,y,1);
                    assertTrue(v.isAirAt(front[0],front[1],front[2]),"Unblocked recessed face");
                    assertTrue(v.keyAt(glass[0],glass[1],glass[2]).contains(u==0&&dy<3?"_wall":"cyan_stained_glass_pane"));
                    for(int d=2;d<=Math.max(0,(y-h-4)/2)+2;d++) {
                        int[] behind=point(w,face,centre+u,y,d);
                        assertTrue(v.isAirAt(behind[0],behind[1],behind[2]),"No roof or solid gable behind glass "+w+"/"+h+"/"+face);
                    }
                }
                for(int d=0;d<=2;d++) {
                    int[] sill=point(w,face,centre+u,h+4,d);
                    assertFalse(v.isAirAt(sill[0],sill[1],sill[2]),"Bonded sill");
                }
                int[] head=point(w,face,centre+u,h+5+rise,1);
                assertFalse(v.isAirAt(head[0],head[1],head[2]),"Glazing head bonded back to roof");
            }
        }
    }
    @Test void paneAndCapDirectionsRotateWithTheBuilding() {
        var p=Presets.get("gothicturret").params(19);int w=p.integer(Params.WIDTH),h=p.integer(Params.HEIGHT);
        for(int turn=0;turn<4;turn++) {
            var v=Fortification.build(p,turn);int x=0,z=-2;
            for(int t=0;t<turn;t++){int nx=-z;z=x;x=nx;}
            assertEquals(new String[]{"south","west","north","east"}[turn],v.at(x,h+10,z).props.get("facing"));
        }
    }
}
