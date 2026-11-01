package io.github.tjhgibbs.capella.core.cathedral;

import io.github.tjhgibbs.capella.core.blockstate.Blockstates;
import io.github.tjhgibbs.capella.core.detail.Detailer;
import io.github.tjhgibbs.capella.core.geom.Vec3;
import io.github.tjhgibbs.capella.core.hash.Hash;
import io.github.tjhgibbs.capella.core.kit.Band;
import io.github.tjhgibbs.capella.core.raster.RasterPass;
import io.github.tjhgibbs.capella.core.raster.Volume;

import java.util.LinkedHashMap;
import java.util.List;

/** Human-scale fittings: side-bay uses and light for the routes people actually walk. */
final class CathedralInteriors {
    private final Volume v;
    private final PlanLayout l;
    private final Detailer.Kit k;
    private final long seed;

    private CathedralInteriors(Volume v, PlanLayout l, Detailer.Kit k, long seed) {
        this.v = v; this.l = l; this.k = k; this.seed = seed;
    }

    static void furnish(Volume v, PlanLayout l, Detailer.Kit k, long seed) {
        CathedralInteriors f = new CathedralInteriors(v, l, k, seed);
        f.aislePaving();
        f.sideBays();
        f.routeLights();
    }

    private int[] at(int x, int y, int z) {
        Vec3 p = l.frame.point(new Vec3(x, y, z));
        return new int[]{(int)Math.round(p.x()), y, (int)Math.round(p.z())};
    }

    private boolean air(int x, int y, int z) {
        int[] p = at(x,y,z);
        return v.contains(p[0],p[1],p[2]) && v.isAirAt(p[0],p[1],p[2]);
    }

    private boolean solid(int x, int y, int z) {
        int[] p = at(x,y,z);
        return v.contains(p[0],p[1],p[2]) && !v.isAirAt(p[0],p[1],p[2]);
    }

    private void put(int x, int y, int z, String name, String... props) {
        int[] p = at(x,y,z);
        var m = new LinkedHashMap<String,String>();
        for(int i=0;i+1<props.length;i+=2) m.put(props[i],props[i+1]);
        v.set(p[0],p[1],p[2],Blockstates.rotateState(Blockstates.makeState(name,m), l.frame.turns(), false),RasterPass.DECORATION);
    }

    /** Framed stone paths give the long side aisles a readable rhythm at walking height. */
    private void aislePaving() {
        for(Bay b:l.bays) {
            if(b.isCrossing || b.kind==Bay.Kind.TOWER) continue;
            int fy=(int)(b.raised?l.levels.choirFloorY:l.levels.floorY);
            for(SideBands bands:List.of(l.north,l.south)) for(Band a:bands.aisles) {
                int mid=(int)Math.floor((a.z0()+a.z1())/2);
                for(int x=(int)b.x0+1;x<b.x1-1;x++) for(int dz:new int[]{-2,2}) {
                    int z=mid+dz;
                    if(solid(x,fy-1,z) && air(x,fy,z))
                        put(x,fy-1,z,Math.floorMod(x-(int)b.x0,4)==0?k.quoin():k.courseBlock());
                }
            }
        }
    }

    /** Keep the middle of each bay (chapel door) and the centre of the aisle unobstructed. */
    private void sideBays() {
        Bay first = l.bays.stream().filter(b -> b.kind == Bay.Kind.NAVE).findFirst().orElse(null);
        for (Bay b : l.bays) {
            if (b.isCrossing || b == first || b.kind == Bay.Kind.TOWER) continue;
            int fy = (int)(b.raised ? l.levels.choirFloorY : l.levels.floorY);
            for (int side : new int[]{-1,1}) {
                // Leave regular quiet bays; a long church needs breathing space as well as uses.
                if (Math.floorMod(b.index + (side < 0 ? 0 : 1), 3) == 0) continue;
                SideBands bands = side < 0 ? l.north : l.south;
                if (bands.aisles.isEmpty()) continue;
                Band aisle = bands.aisles.getLast();
                if (aisle.z1()-aisle.z0()<8) continue;
                int x = (int)b.x0 + 2;
                int z = side < 0 ? (int)aisle.z0()+1 : (int)aisle.z1()-2;
                int inward = -side;
                // Two blocks against the outer edge, never across a portal or stair arrival.
                if (x+1 >= (b.x0+b.x1)/2-1) continue;
                boolean room = true;
                for(int dx=0;dx<2;dx++) for(int dz=0;dz<2;dz++) {
                    room &= solid(x+dx,fy-1,z+dz*inward);
                    for(int y=fy;y<fy+4;y++) room &= air(x+dx,y,z+dz*inward);
                }
                if (!room) continue;
                int theme = Hash.hashInt(0,2,seed,"side-bay",b.index,side);
                if (theme == 0) {
                    // A votive altar and kneeling bench facing the wall.
                    for (int dx=0;dx<2;dx++) {
                        put(x+dx,fy,z,k.plinthBlock());
                        put(x+dx,fy+1,z,"minecraft:candle","candles",dx==0?"3":"2","lit","true");
                        put(x+dx,fy,z+inward,"minecraft:spruce_slab","type","bottom");
                    }
                } else if (theme == 1) {
                    // A library bay: books and a desk, tucked beside the processional route.
                    put(x,fy,z,"minecraft:bookshelf");
                    put(x,fy+1,z,"minecraft:bookshelf");
                    put(x+1,fy,z,"minecraft:lectern","facing",side<0?"south":"north","has_book","true");
                } else {
                    // A low memorial chest with a carved head and candle; no raised collision lip.
                    put(x,fy,z,k.quoin());
                    put(x+1,fy,z,k.courseBlock());
                    put(x,fy+1,z,k.courseStairs(),"facing","east","half","top");
                    put(x+1,fy+1,z,"minecraft:candle","candles","2","lit","true");
                }
                // Distinguish the recess floor without inserting rugs in walking headroom.
                for(int dx=0;dx<2;dx++) for(int dz=0;dz<2;dz++)
                    put(x+dx,fy-1,z+dz*inward,dx==dz?k.quoin():k.courseBlock());
            }
        }
    }

    private void routeLights() {
        int floor = (int)l.levels.floorY;
        int deck = (int)l.levels.triforiumDeckY;
        for (Bay b : l.bays) {
            if (b.isCrossing || b.kind == Bay.Kind.TOWER) continue;
            int fy = b.raised ? (int)l.levels.choirFloorY : floor;
            int x = (int)Math.floor((b.x0+b.x1)/2);
            // Galleries receive their own lamp: the aisle lamp below the deck cannot light them.
            for (SideBands bands : List.of(l.north,l.south)) for (Band a : bands.aisles) {
                int z = (int)Math.floor((a.z0()+a.z1())/2);
                if(solid(x,deck-1,z)) hanging(x,deck,z,3);
            }
            // A low pair of candlestands frames the transverse aisle without blocking its centre.
            candlestand(x,fy,(int)l.naveZ0+1);
            candlestand(x,fy,(int)l.naveZ1-2);
        }
        // Furnished transept arms: pools of warm light and quiet benches at the sides, with a
        // broad, continuous route down the middle instead of a forest of suspended chains.
        for(Crossing c:l.crossings) {
            int fy=c.bay.raised?(int)l.levels.choirFloorY:floor;
            for(int side:new int[]{-1,1}) {
                int start=(int)(side<0?l.naveZ0-3:l.naveZ1+3);
                int end=(int)(l.axisZ+side*(c.reach-l.wallThickness-2));
                for(int z=start;side<0?z>=end:z<=end;z+=side*8) {
                    for(int edge:new int[]{-1,1}) {
                        int x=edge<0?(int)c.bay.x0+2:(int)c.bay.x1-3;
                        candlestand(x,fy,z);
                        if(air(x,fy,z+side) && air(x,fy+1,z+side) && solid(x,fy-1,z+side))
                            put(x,fy,z+side,"minecraft:spruce_stairs","facing",edge<0?"east":"west","half","bottom");
                    }
                }
            }
        }
    }

    private void candlestand(int x, int fy, int z) {
        if(!solid(x,fy-1,z)) return;
        for(int y=fy;y<fy+4;y++) if(!air(x,y,z)) return;
        put(x,fy,z,k.plinthBlock());
        put(x,fy+1,z,k.shaftWall());
        put(x,fy+2,z,"minecraft:candle","candles","4","lit","true");
    }

    /** A continuous chain to an existing ceiling; never leave an unsupported floating fitting. */
    private void hanging(int x, int fy, int z, int height) {
        if(!solid(x,fy-1,z)) return;
        int y=fy+height;
        if(!air(x,y,z)) return;
        int ceiling=y+1;
        while(ceiling<fy+160 && air(x,ceiling,z)) ceiling++;
        if(!solid(x,ceiling,z)) return;
        // Avoid hanging through a different furnished fitting or filling an open sky column.
        for(int q=y+1;q<ceiling;q++) put(x,q,z,"minecraft:iron_chain","axis","y");
        put(x,y,z,"minecraft:lantern","hanging","true");
    }
}
