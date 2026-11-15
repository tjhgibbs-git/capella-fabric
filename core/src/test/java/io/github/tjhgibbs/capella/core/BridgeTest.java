package io.github.tjhgibbs.capella.core;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.raster.Volume;
import io.github.tjhgibbs.capella.core.small.Bridge;
import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BridgeTest {
    static final List<String> IDS=List.of("suspensionbridge","gothicbridge","stoneviaduct","timbertrestle");

    @Test void everyHeadingHasAConnectedSupportedRoadwayAndValidStates() {
        for(String id:IDS) for(int[] size:new int[][]{{17,5,5},{48,8,13},{129,17,32}})
            for(int heading=0;heading<16;heading++) {
                var p=Presets.get(id).params(7).set(Params.LENGTH,size[0]).set(Params.WIDTH,size[1])
                        .set(Params.HEIGHT,size[2]).set(Params.BRIDGE_HEADING,heading);
                Volume v=Bridge.build(p,0);
                String tag=id+Arrays.toString(size)+" heading "+heading;
                assertEquals(p,BuildingParams.decode(p.encode()),tag);
                for(int i=1;i<v.paletteSize();i++) assertTrue(Blockstates.domainViolations(v.stateOfId(i)).isEmpty(),v.keyOfId(i));
                double a=heading*Math.PI/8;
                int ex=(int)Math.round((size[0]-1)*Math.cos(a)),ez=(int)Math.round((size[0]-1)*Math.sin(a));
                Set<String> seen=new HashSet<>(); ArrayDeque<int[]> q=new ArrayDeque<>();
                q.add(new int[]{0,0}); seen.add("0,0");
                assertTrue(road(v,0,0),tag+" landing");
                while(!q.isEmpty()) {
                    int[] c=q.remove();
                    for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        int x=c[0]+d[0],z=c[1]+d[1];
                        if(road(v,x,z) && seen.add(x+","+z)) q.add(new int[]{x,z});
                    }
                }
                assertTrue(seen.contains(ex+","+ez),tag+" opposite landing");
                // Every centreline station has a solid deck and two full blocks of headroom.
                for(int x=0;x<size[0];x++) assertTrue(road(v,(int)Math.round(x*Math.cos(a)),(int)Math.round(x*Math.sin(a))),tag+" station "+x);
            }
    }
    @Test void slopesAreWalkableInBothDirectionsAtEveryHeading() {
        for(String id:IDS) for(int[] size:new int[][]{{17,5,5},{48,8,13},{129,17,32}})
            for(int slope:new int[]{-25,-7,7,25}) for(int heading=0;heading<16;heading++) {
                var p=Presets.get(id).params(7).set(Params.LENGTH,size[0]).set(Params.WIDTH,size[1])
                        .set(Params.HEIGHT,size[2]).set(Params.BRIDGE_HEADING,heading).set(Params.BRIDGE_SLOPE,slope);
                Volume v=Bridge.build(p,0);
                io.github.tjhgibbs.capella.core.detail.Finish.apply(v,p);
                io.github.tjhgibbs.capella.core.raster.Normalize.normalizeVolume(v);
                assertEquals(p,BuildingParams.decode(p.encode()));
                String tag=id+Arrays.toString(size)+" slope "+slope+" heading "+heading;
                double a=heading*Math.PI/8,c=Math.cos(a),sine=Math.sin(a);
                int ex=(int)Math.round((size[0]-1)*c),ez=(int)Math.round((size[0]-1)*sine);
                Set<String> seen=new HashSet<>(); ArrayDeque<int[]> q=new ArrayDeque<>();
                q.add(new int[]{0,0});seen.add("0,0");
                while(!q.isEmpty()) {
                    int[] at=q.remove(); int here=surface(at[0],at[1],c,sine,size[0],slope);
                    assertTrue(slopeRoad(v,at[0],at[1],here),tag);
                    for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                        int x=at[0]+d[0],z=at[1]+d[1],next=surface(x,z,c,sine,size[0],slope);
                        // Half-block steps are reversible without jumping; each cell has full headroom.
                        if(Math.abs(next-here)<=1 && slopeRoad(v,x,z,next) && seen.add(x+","+z)) q.add(new int[]{x,z});
                    }
                }
                assertTrue(seen.contains(ex+","+ez),tag+" opposite landing");
                assertTrue(slopeRoad(v,0,0,0),tag+" starting datum");
                int end=(int)Math.round(2.0*(size[0]-1)*slope/100);
                assertTrue(slopeRoad(v,ex,ez,end),tag+" endpoint rise");
                assertEquals(0,surface(0,0,c,sine,size[0],slope));
            }
        assertFalse(Params.applies(Params.BRIDGE_SLOPE,Params.BuildingType.SENTRY_TOWER));
    }

    private static int surface(int x,int z,double c,double s,int length,int slope) {
        int local=(int)Math.floor(c*x+s*z+0.5);
        return (int)Math.round(2.0*Math.clamp(local,0,length-1)*slope/100);
    }

    private static boolean slopeRoad(Volume v,int x,int z,int halfRise) {
        int y=Math.floorDiv(halfRise,2);
        if(Math.floorMod(halfRise,2)==1)
            return v.keyAt(x,y,z).contains("type=bottom") && v.keyAt(x,y,z).contains("_slab")
                    && !v.isAirAt(x,y-1,z) && v.isAirAt(x,y+1,z) && v.isAirAt(x,y+2,z);
        return !v.isAirAt(x,y-1,z) && !v.keyAt(x,y-1,z).contains("_slab") && v.isAirAt(x,y,z) && v.isAirAt(x,y+1,z);
    }

    private static boolean road(Volume v,int x,int z) {
        return !v.isAirAt(x,-1,z) && v.isAirAt(x,0,z) && v.isAirAt(x,1,z);
    }

    /** Walks (half-block steps, two clear blocks) from the start landing through the turret stairs. */
    @Test void galleriesAreReachableFromTheDeckAtEveryHeadingAndGrade() {
        int[][] cases={{81,9,40,3,12},{49,5,20,2,9},{65,17,48,3,16},{33,7,17,2,8}};
        for(int[] cs:cases) for(int slope:new int[]{-25,0,13,25}) for(int heading=0;heading<16;heading+=cs[0]==81?1:3) {
            var p=Presets.get("arcadedviaduct").params(7).set(Params.LENGTH,cs[0]).set(Params.WIDTH,cs[1]).set(Params.HEIGHT,cs[2])
                    .set(Params.BRIDGE_TIERS,cs[3]).set(Params.BRIDGE_TIER_HEIGHT,cs[4])
                    .set(Params.BRIDGE_HEADING,heading).set(Params.BRIDGE_SLOPE,slope);
            Volume v=Bridge.build(p,0);
            io.github.tjhgibbs.capella.core.detail.Finish.apply(v,p);
            io.github.tjhgibbs.capella.core.raster.Normalize.normalizeVolume(v);
            String tag=Arrays.toString(cs)+" slope "+slope+" heading "+heading;
            Set<Long> seen=walk(v,0,0,0);
            double a=heading*Math.PI/8,c=Math.cos(a),sn=Math.sin(a);
            int[] levels=Bridge.galleryLevels(p);
            assertEquals(cs[3]-1,levels.length,tag);
            int xm=cs[0]/2, wx=(int)Math.round(xm*c), wz=(int)Math.round(xm*sn);
            for(int g:levels) {
                int hs=2*g+surface(wx,wz,c,sn,cs[0],slope);
                assertTrue(seen.contains(state(wx,wz,hs)),tag+" gallery "+g);
            }
            int ex=(int)Math.round((cs[0]-1)*c),ez=(int)Math.round((cs[0]-1)*sn);
            assertTrue(seen.contains(state(ex,ez,surface(ex,ez,c,sn,cs[0],slope))),tag+" far landing");
        }
    }

    @Test void upperTierHeightSetsTheGalleryLevels() {
        var p=Presets.get("arcadedviaduct").params(1).set(Params.HEIGHT,40).set(Params.BRIDGE_TIERS,3);
        assertArrayEquals(new int[]{-16,-8},Bridge.galleryLevels(p.copy().set(Params.BRIDGE_TIER_HEIGHT,8)));
        assertArrayEquals(new int[]{-32,-16},Bridge.galleryLevels(p.copy().set(Params.BRIDGE_TIER_HEIGHT,16)));
        // Clamped so the lowest arcade keeps at least eight blocks.
        assertArrayEquals(new int[]{-32,-16},Bridge.galleryLevels(p.copy().set(Params.BRIDGE_TIER_HEIGHT,24)));
        assertEquals(0,Bridge.galleryLevels(p.copy().set(Params.BRIDGE_WALKWAYS,Params.BridgeWalkways.FOOTWAYS)).length);
        assertTrue(Params.applies(Params.BRIDGE_TIER_HEIGHT,Params.BuildingType.GOTHIC_BRIDGE));
        assertFalse(Params.applies(Params.BRIDGE_TIER_HEIGHT,Params.BuildingType.STONE_VIADUCT));
    }

    static long state(int x,int z,int hs) { return ((long)(x+4096)<<32)|((long)(z+4096)<<16)|(hs+4096); }

    static boolean stand(Volume v,int x,int z,int hs) {
        int y=Math.floorDiv(hs,2);
        if(Math.floorMod(hs,2)==1) {
            String k=v.keyAt(x,y,z);
            return k.contains("_slab") && k.contains("type=bottom") && v.isAirAt(x,y+1,z) && v.isAirAt(x,y+2,z);
        }
        String below=v.keyAt(x,y-1,z);
        return !v.isAirAt(x,y-1,z) && !(below.contains("_slab") && below.contains("type=bottom")) && !below.contains("_stairs")
                && !below.contains("lantern") && v.isAirAt(x,y,z) && v.isAirAt(x,y+1,z);
    }

    static Set<Long> walk(Volume v,int x0,int z0,int h0) {
        Set<Long> seen=new HashSet<>(); ArrayDeque<int[]> q=new ArrayDeque<>();
        assertTrue(stand(v,x0,z0,h0));
        q.add(new int[]{x0,z0,h0}); seen.add(state(x0,z0,h0));
        while(!q.isEmpty()) {
            int[] at=q.remove();
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) for(int dh=-1;dh<=1;dh++) {
                int x=at[0]+d[0],z=at[1]+d[1],hs=at[2]+dh;
                if(x<v.minX||x>=v.minX+v.sizeX||z<v.minZ||z>=v.minZ+v.sizeZ) continue;
                if(stand(v,x,z,hs) && seen.add(state(x,z,hs))) q.add(new int[]{x,z,hs});
            }
        }
        return seen;
    }

    @Test void placementQuarterTurnsComposeWithSavedHeading() {
        for(String id:IDS) for(int heading=0;heading<16;heading++) {
            var p=Presets.get(id).params(19).set(Params.BRIDGE_HEADING,heading).set(Params.BRIDGE_SLOPE,-25);
            Volume a=Bridge.build(p,1),b=Bridge.build(p.copy().set(Params.BRIDGE_HEADING,(heading+4)%16),0);
            assertEquals(a.blockCounts(),b.blockCounts());
            a.forEachSolid((x,y,z,i)->assertEquals(a.keyOfId(i),b.keyAt(x,y,z)));
        }
    }

    @Test void allPresetsUseTheFinishedGenerationAndExportPath() throws Exception {
        Set<String> signatures=new HashSet<>();
        for(String id:IDS) {
            var p=Presets.get(id).params(1).set(Params.BRIDGE_HEADING,3).set(Params.BRIDGE_SLOPE,25);
            var b=BuildingGenerator.generate(p,2);
            assertTrue(b.volume().blockCount()>100);
            var meta=new io.github.tjhgibbs.capella.core.export.ExportMeta(id,"test",4321,p.encode(),0);
            for(var format:List.of(new io.github.tjhgibbs.capella.core.export.StructureNbt(),
                    new io.github.tjhgibbs.capella.core.export.SpongeSchematic())) {
                var bytes=new java.io.ByteArrayOutputStream();
                format.write(b.volume(),meta,bytes);
                assertTrue(bytes.size()>100);
                assertNotNull(io.github.tjhgibbs.capella.core.nbt.Nbt.readGzip(new java.io.ByteArrayInputStream(bytes.toByteArray())).value());
            }
            signatures.add(b.volume().palette().toString());
            assertTrue(Params.basic(p.get(Params.TYPE)).contains(Params.BRIDGE_HEADING));
            assertTrue(Params.applies(Params.LENGTH,p.get(Params.TYPE)));
        }
        assertEquals(4,signatures.size());
        assertFalse(Params.applies(Params.BRIDGE_HEADING,Params.BuildingType.SENTRY_TOWER));
    }
}
