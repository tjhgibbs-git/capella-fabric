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
            var v=Fortification.build(p,0);int mid=w%2==0?-1:0;
            for(int centre:w>=27?new int[]{mid-w/4,mid+w/4}:new int[]{mid}) for(int face=0;face<4;face++) {
                for(int u=-1;u<=1;u++) for(int y=h+5;y<=h+8;y++) {
                    int[] front=point(w,face,centre+u,y,0),glass=point(w,face,centre+u,y,1);
                    String key=v.keyAt(front[0],front[1],front[2]);
                    if(y==h+8) assertEquals(u==0,v.isAirAt(front[0],front[1],front[2]),"Pointed head with haunch stairs");
                    else assertTrue(u==0?key.contains("_wall"):v.isAirAt(front[0],front[1],front[2]),"Open lights and slender mullion");
                    assertTrue(v.keyAt(glass[0],glass[1],glass[2]).contains("cyan_stained_glass_pane"),"Recessed glazing");
                    for(int d=2;d<=Math.max(0,(y-h-4)/2)+1;d++) {
                        int[] behind=point(w,face,centre+u,y,d);
                        assertTrue(v.isAirAt(behind[0],behind[1],behind[2]),"No roof or solid gable behind glass "+w+"/"+h+"/"+face);
                    }
                }
                for(int u=-2;u<=2;u++) for(int d=0;d<=1;d++) {
                    int[] sill=point(w,face,centre+u,h+4,d);
                    assertFalse(v.isAirAt(sill[0],sill[1],sill[2]),"Bonded sill");
                }
                for(int side:new int[]{-2,2}) for(int y=h+5;y<h+8;y++) {
                    int[] cheek=point(w,face,centre+side,y,1);
                    assertFalse(v.isAirAt(cheek[0],cheek[1],cheek[2]),"Closed cheek");
                }
                for(int u=-1;u<=1;u++) {
                    int[] roof=point(w,face,centre+u,u==0?h+10:h+9,1);
                    assertFalse(v.isAirAt(roof[0],roof[1],roof[2]),"Dormer roof closes over the lights");
                }
            }
        }
    }
    @Test void paneAndCapDirectionsRotateWithTheBuilding() {
        var p=Presets.get("gothicturret").params(19);int w=p.integer(Params.WIDTH),h=p.integer(Params.HEIGHT);
        for(int turn=0;turn<4;turn++) {
            var v=Fortification.build(p,turn);int x=0,z=-2;
            for(int t=0;t<turn;t++){int nx=-z;z=x;x=nx;}
            assertEquals(new String[]{"south","west","north","east"}[turn],v.at(x,h+8,z).props.get("facing"));
        }
    }
}
