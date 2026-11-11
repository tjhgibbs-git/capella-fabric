package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.small.*;
import io.github.tjhgibbs.capella.core.raster.Volume;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GatehouseTest {
    @Test void allTowerArchAndGateCombinationsKeepTheirJointsAtEveryScale() {
        for(var type:new Params.BuildingType[]{Params.BuildingType.CURTAIN_WALL,Params.BuildingType.ARCADED_WALL,Params.BuildingType.PLAIN_WALL})
            for(int[] size:new int[][]{{9,5,5},{10,6,6},{25,5,12},{40,7,24},{65,11,40},{9,11,40},{160,5,5}})
                for(var tower:Params.GateTower.values()) for(var arch:Params.GateArch.values()) for(var gate:Params.GateStyle.values()) {
                    var p=new BuildingParams().set(Params.TYPE,type).set(Params.LENGTH,size[0]).set(Params.WIDTH,size[1])
                            .set(Params.HEIGHT,size[2]).set(Params.GATEHOUSE,true).set(Params.GATE_TOWERS,tower)
                            .set(Params.GATE_ARCH,arch).set(Params.GATE_STYLE,gate).set(Params.WALKABLE,true).seed(7);
                    var a=CastleGatehouse.layout(p);Volume v=Fortification.build(p,0);
                    String context=type+"/"+size[0]+"/"+size[1]+"/"+size[2]+"/"+tower+"/"+arch+"/"+gate;
                    assertTrue(a.x0()>=1&&a.x1()<size[0]-1,context+" reserved wall ends");
                    assertTrue(a.gate1()-a.gate0()>=2,context+" three-wide gate");
                    for(int x=0;x<size[0];x++) {
                        assertFalse(v.isAirAt(x,size[2]-1,0),context+" continuous deck "+x);
                        assertTrue(v.isAirAt(x,size[2],0)&&v.isAirAt(x,size[2]+1,0),context+" walk joint "+x);
                    }
                    for(int edge:new int[]{-size[1]/2-1,-size[1]/2+size[1]}) {
                        assertFalse(v.isAirAt(a.x0()-1,size[2]-1,edge),context+" curtain ledge");
                        assertFalse(v.isAirAt(a.x0(),size[2]-1,edge),context+" tower abutment");
                        assertFalse(v.isAirAt(a.x0(),size[2]-2,edge),context+" ledge support");
                    }
                    for(int x=a.gate0();x<=a.gate1();x++) {
                        assertTrue(a.clearTop(x)>=3,context+" passage headroom");
                        for(int z=a.z0()-1;z<=a.z1()+1;z++) {
                            assertFalse(v.isAirAt(x,-1,z),context+" gate threshold");
                            if(gate==Params.GateStyle.OPEN||gate==Params.GateStyle.RAISED_PORTCULLIS)
                                for(int y=0;y<(gate==Params.GateStyle.OPEN?a.clearTop(x):3);y++) assertTrue(v.isAirAt(x,y,z),context+" blocked gate at "+x+","+y+","+z);
                        }
                    }
                    if(gate==Params.GateStyle.CLOSED_PORTCULLIS||gate==Params.GateStyle.TIMBER)
                        assertFalse(v.isAirAt(a.centre(),1,(-size[1]/2+(-size[1]/2+size[1]-1))/2),context+" closed gate");
                    assertTrue(v.isAirAt(-1,0,0)&&v.isAirAt(size[0],0,0),context+" modular ends");
                    assertEquals(p,BuildingParams.decode(p.encode()));
                }
    }

    @Test void rotationAndDisabledGatehousePreserveExpectedBehaviour() {
        var p=Presets.get("roundgatehouse").params(19);
        var a=CastleGatehouse.layout(p);
        for(int turn=0;turn<4;turn++) {
            Volume v=Fortification.build(p,turn);
            int x=a.centre(),z=0;
            for(int t=0;t<turn;t++){int next=-z;z=x;x=next;}
            for(int y=0;y<3;y++) assertTrue(v.isAirAt(x,y,z));
        }
        Volume off=Fortification.build(p.copy().set(Params.GATEHOUSE,false),0);
        assertFalse(off.isAirAt(a.centre(),1,0),"Disabling gatehouse restores solid curtain");
        for(var type:Params.BuildingType.values()) assertEquals(type.wall(),Params.applies(Params.GATEHOUSE,type));
    }

    @Test void gateChambersConnectToLaddersAndDeckExitsAndCanCloseOffTheCurtain() {
        for(var style:Params.GateTower.values()) {
            var p=Presets.get("gatehouse").params(7).set(Params.GATE_TOWERS,style);
            var a=CastleGatehouse.layout(p);Volume v=Fortification.build(p,0);
            for(int entry:new int[]{a.gate0()-1,a.gate1()+1})
                for(int y=0;y<3;y++) assertTrue(v.isAirAt(entry,y,0),"Ground chamber doorway");
            for(int cx:new int[]{(a.x0()+a.gate0()-1)/2,(a.gate1()+1+a.x1())/2}) {
                int z=a.z1()-1;
                for(int y=0;y<a.towerHeight();y++) {
                    assertTrue(v.keyAt(cx,y,z).contains("ladder"),"Continuous tower access");
                    assertFalse(v.isAirAt(cx,y,z+1),"Ladder backing");
                }
                assertFalse(v.isAirAt(cx,a.towerHeight()-1,z-1),"Exit deck support");
                assertTrue(v.isAirAt(cx,a.towerHeight(),z-1)&&v.isAirAt(cx,a.towerHeight()+1,z-1),"Exit headroom");
            }
            Volume closed=Fortification.build(p.copy().set(Params.WALKABLE,false),0);
            assertFalse(closed.isAirAt(a.x0(),a.wallHeight(),0),"No doorway onto disabled wall walk");
            assertTrue(closed.isAirAt(a.centre(),1,0),"Ground gate stays open");
        }
    }

    @Test void gatehousePositionSlidesTheCompleteAssemblyAndPreservesRoutes() {
        for(int[] size:new int[][]{{9,5,5},{10,6,6},{25,5,12},{40,7,24},{160,11,40}})
            for(var tower:Params.GateTower.values()) {
                var p=Presets.get("gatehouse").params(7).set(Params.LENGTH,size[0]).set(Params.WIDTH,size[1])
                        .set(Params.HEIGHT,size[2]).set(Params.GATE_TOWERS,tower);
                var middle=CastleGatehouse.layout(p);int last=-1;
                for(double position:new double[]{0,.2,.5,.8,1}) {
                    p.set(Params.GATE_POSITION,position);var a=CastleGatehouse.layout(p);
                    assertEquals(middle.towerWidth(),a.towerWidth(),"Sliding does not shrink towers");
                    assertTrue(a.x0()>=1&&a.x1()<=size[0]-2&&a.x0()>=last,"Clamped monotonic travel");last=a.x0();
                    var v=Fortification.build(p,0);
                    for(int x=0;x<size[0];x++) {
                        assertFalse(v.isAirAt(x,size[2]-1,0),"Supported walk while moved");
                        assertTrue(v.isAirAt(x,size[2],0)&&v.isAirAt(x,size[2]+1,0),"Clear walk while moved");
                    }
                    for(int x=a.gate0();x<=a.gate1();x++) for(int z=a.z0()-1;z<=a.z1()+1;z++)
                        for(int y=0;y<3;y++) assertTrue(v.isAirAt(x,y,z),"Clear moved gate");
                    assertEquals(p,BuildingParams.decode(p.encode()));
                }
                assertEquals(size[0]-2,CastleGatehouse.layout(p).x1(),"100% reaches safe far end");
                assertEquals(1,CastleGatehouse.layout(p.copy().set(Params.GATE_POSITION,0.0)).x0(),"0% reaches safe near end");
            }
        var p=Presets.get("gatehouse").params(1);
        assertEquals(CastleGatehouse.layout(p),CastleGatehouse.layout(p.copy().set(Params.GATE_POSITION,.5)));
        assertTrue(Params.basic(Params.BuildingType.CURTAIN_WALL).contains(Params.GATE_POSITION));
    }

    @Test void bothButtressShouldersMeetTheCorniceWithoutHalfBlockGaps() {
        for(String preset:new String[]{"curtainwall","arcadedwall","plainwall"}) for(int h:new int[]{5,6,12,24,40}) {
            var p=Presets.get(preset).params(7).set(Params.HEIGHT,h).set(Params.LENGTH,24);
            Volume v=Fortification.build(p,0);int w=p.integer(Params.WIDTH);
            for(int edge:new int[]{-w/2-1,-w/2+w}) for(int x:new int[]{7,8,9}) {
                assertFalse(v.isAirAt(x,h-3,edge),preset+" stem");
                assertFalse(v.isAirAt(x,h-2,edge),preset+" capital");
                var cap=v.at(x,h-1,edge);
                assertFalse(v.isAirAt(x,h-1,edge),preset+" cornice");
                assertNotEquals("top",cap.props.get("type"),"Top slab would leave a gap above the capital");
            }
        }
    }
}
