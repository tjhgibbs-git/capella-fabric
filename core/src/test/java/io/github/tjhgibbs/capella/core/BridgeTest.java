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
    private static boolean road(Volume v,int x,int z) {
        return !v.isAirAt(x,-1,z) && v.isAirAt(x,0,z) && v.isAirAt(x,1,z);
    }

    @Test void placementQuarterTurnsComposeWithSavedHeading() {
        for(String id:IDS) for(int heading=0;heading<16;heading++) {
            var p=Presets.get(id).params(19).set(Params.BRIDGE_HEADING,heading);
            Volume a=Bridge.build(p,1),b=Bridge.build(p.copy().set(Params.BRIDGE_HEADING,(heading+4)%16),0);
            assertEquals(a.blockCounts(),b.blockCounts());
            a.forEachSolid((x,y,z,i)->assertEquals(a.keyOfId(i),b.keyAt(x,y,z)));
        }
    }

    @Test void allPresetsUseTheFinishedGenerationAndExportPath() throws Exception {
        Set<String> signatures=new HashSet<>();
        for(String id:IDS) {
            var p=Presets.get(id).params(1).set(Params.BRIDGE_HEADING,3);
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
