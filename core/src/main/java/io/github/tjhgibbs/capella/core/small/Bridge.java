package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.raster.Volume;

/** Deck datum is y=0 at the centre of the west landing; supports extend below it.
 * All forms use the selected castle stone/timber suite and keep a two-high clear roadway.
 */
public final class Bridge {
    private final BuildingParams p;
    private final Mason m;
    private final Style s;
    private final int l, w, h, z0, z1;

    private Bridge(BuildingParams source) {
        p=source.copy(); Params.clampSizes(p);
        l=p.integer(Params.LENGTH); w=p.integer(Params.WIDTH); h=p.integer(Params.HEIGHT);
        z0=-w/2; z1=z0+w-1;
        m=new Mason(p.seed()); s=Style.of(p.get(Params.STONE));
    }

    public static Volume build(BuildingParams p, int turns) {
        if (!p.get(Params.TYPE).bridge()) throw new IllegalArgumentException("Not a bridge");
        Bridge b=new Bridge(p); b.build();
        return b.m.offsetColumns(b::deckBase).toVolumeHeading(Math.floorMod(p.integer(Params.BRIDGE_HEADING),16)+4*Math.floorMod(turns,4));
    }

    /** Half-block terraces keep steps at or below the player's normal step height.
     * The 25% limit spaces terraces far enough apart even after diagonal inverse sampling.
     * Aprons are level with their respective landing, including descending bridges.
     */
    private int halfRise(int x) {
        return (int)Math.round(2.0*Math.clamp(x,0,l-1)*p.integer(Params.BRIDGE_SLOPE)/100.0);
    }

    private int deckBase(int x) { return Math.floorDiv(halfRise(x),2); }

    private void stone(int x,int y,int z,boolean trim) {
        m.set(x,y,z,(trim?s.trim:s.wall).block(p.seed(),x,y,z,0));
    }

    private void build() {
        var type=p.get(Params.TYPE);
        boolean wood=type==Params.BuildingType.SUSPENSION_BRIDGE || type==Params.BuildingType.TIMBER_TRESTLE;
        // The full-block deck survives diagonal rasterisation without slab-height changes.
        for(int x=-1;x<=l;x++) for(int z=z0;z<=z1;z++) {
            if(wood && z>z0 && z<z1) m.set(x,-1,z,s.timber); else stone(x,-1,z,true);
            if(z==z0 || z==z1) {
                if(wood) {
                    // A solid knee wall under a plank capping rail: no fence arms to break up on
                    // diagonals, and still too high to step over.
                    m.set(x,0,z,s.timber);
                    m.slab(x,1,z,s.timberSlab,false);
                }
                else {
                    stone(x,0,z,false);
                    m.slab(x,1,z,s.trim.slab(p.seed(),x,1,z,0),false);
                }
            }
        }
        if(type==Params.BuildingType.SUSPENSION_BRIDGE) suspension();
        else if(type==Params.BuildingType.TIMBER_TRESTLE) trestle();
        else arches(type==Params.BuildingType.GOTHIC_BRIDGE);
        for(int x=4;x<l-3;x+=9) for(int z:new int[]{z0,z1}) {
            if(type==Params.BuildingType.SUSPENSION_BRIDGE) continue;
            if(wood) m.set(x,1,z,s.timberLog,"axis","y"); else stone(x,1,z,true);
            m.set(x,2,z,"minecraft:lantern","hanging","false");
        }
        // Add the half terraces above the full structural deck, before shifting columns.
        // The roadway uses slabs rather than cardinal stairs so all sixteen headings work.
        for(int x=-1;x<=l;x++) if(Math.floorMod(halfRise(x),2)==1)
            for(int z=z0+1;z<z1;z++)
                m.slab(x,0,z,wood?s.timberSlab:s.trim.slab(p.seed(),x,0,z,0),false);
        // End abutments, with no block above the walking datum in the roadway.
        for(int x:new int[]{0,1,l-2,l-1}) for(int z=z0-1;z<=z1+1;z++)
            for(int y=-h;y<-1;y++) stone(x,y,z,y==-h);
    }

    private void arches(boolean gothic) {
        int bays=Math.max(1,(l-1)/14);
        for(int x=0;x<l;x++) {
            double bay=x*(double)bays/(l-1), f=bay-Math.floor(bay);
            double d=Math.abs(f*2-1);
            int rise=Math.max(2,h-3);
            // Opening tops: an equilateral two-centred (convex pointed) arch or a round intrados.
            int top=-h+(int)Math.round(rise*(gothic?Math.sqrt(Math.max(0,4-(d+1)*(d+1)))/Math.sqrt(3):Math.sqrt(Math.max(0,1-d*d))));
            boolean pier=f<0.11 || f>0.89;
            for(int z=z0;z<=z1;z++) for(int y=-h;y<-1;y++)
                if(pier || y>=top) stone(x,y,z,!pier && y==top);
            for(int z:new int[]{z0-1,z1+1}) {
                if(!pier) {
                    stone(x,top,z,true);
                    m.slab(x,top+1,z,s.trim.slab(p.seed(),x,top,z,0),false);
                } else {
                    // Cutwaters taper into the dressed capital instead of a flat extruded pier.
                    for(int y=-h;y<-2;y++) stone(x,y,z,y==-h);
                    m.stairs(x,-2,z,s.trim.stairs(p.seed(),x,-2,z,0),z<0?"south":"north",false);
                    if(gothic) {
                        stone(x,0,z,true); stone(x,1,z,true);
                        m.set(x,2,z,s.trim.wall(p.seed(),x,2,z,0));
                        m.slab(x,3,z,s.trim.slab(p.seed(),x,3,z,0),false);
                    }
                }
                stone(x,-1,z,true);
            }
        }
    }

    private void suspension() {
        int a=Math.max(3,l/5), b=l-1-a;
        // Hangers mirror about mid-span; each rises on a chain from a capped rail post.
        java.util.function.IntPredicate hanger=x->Math.min(x,l-1-x)%3==0;
        for(int x=0;x<l;x++) {
            int cable=cableHeight(x,a,b);
            for(int z:new int[]{z0,z1}) {
                // One block per column except on steep runs, so the sweep reads as a thin line.
                int previous=x>0?cableHeight(x-1,a,b)+deckBase(x-1)-deckBase(x):cable;
                boolean steep=Math.abs(previous-cable)>2;
                if(previous<cable) for(int y=previous+1;y<=cable;y++) m.set(x,y,z,s.timberLog,"axis",steep?"y":"x");
                else for(int y=cable;y<Math.max(previous,cable+1);y++) m.set(x,y,z,s.timberLog,"axis",steep?"y":"x");
                if(hanger.test(x) && x>0 && x<l-1) {
                    m.set(x,1,z,s.timberLog,"axis","y");
                    for(int y=2;y<cable;y++) m.set(x,y,z,"minecraft:iron_chain","axis","y");
                    // Stiffening cross-beams and edge girders frame the underside.
                    for(int dz=z0;dz<=z1;dz++) m.set(x,-2,dz,s.timberLog,"axis","z");
                }
                m.set(x,-2,z,s.timberLog,"axis","x");
            }
        }
        // Anchor posts at each landing take the cable ends.
        for(int x:new int[]{0,l-1}) for(int z:new int[]{z0,z1}) {
            stone(x,1,z,true); stone(x,2,z,false); stone(x,3,z,true);
            m.set(x,4,z,"minecraft:lantern","hanging","false");
        }
        for(int x:new int[]{a,b}) {
            for(int z:new int[]{z0,z1}) {
                // Footing, three-wide legs tapering above the rail, and a capped single shaft.
                for(int dx=-2;dx<=2;dx++) for(int dz=-1;dz<=1;dz++) stone(x+dx,-h,z+dz,true);
                for(int dx=-1;dx<=1;dx++) for(int y=-h+1;y<=h/2;y++) stone(x+dx,y,z,y==-1 || y==h/2);
                for(int dx:new int[]{-1,1})
                    m.stairs(x+dx,h/2+1,z,s.trim.stairs(p.seed(),x+dx,h/2+1,z,0),dx<0?"east":"west",false);
                for(int y=h/2+1;y<=h;y++) stone(x,y,z,y==h);
                stone(x,h+1,z,true);
                m.slab(x,h+2,z,s.trim.slab(p.seed(),x,h+2,z,0),false);
            }
            // Portal lintel on stair brackets well above headroom, and a cross-beam under the deck.
            for(int z=z0+1;z<z1;z++) { stone(x,h-1,z,true); stone(x,-2,z,true); }
            m.stairs(x,h-2,z0+1,s.trim.stairs(p.seed(),x,h-2,z0+1,0),"north",true);
            m.stairs(x,h-2,z1-1,s.trim.stairs(p.seed(),x,h-2,z1-1,0),"south",true);
            for(int z=-((w-1)%2);z<=0;z++) m.set(x,h-2,z,"minecraft:lantern","hanging","true");
        }
    }

    private int cableHeight(int x,int a,int b) {
        double t=x<a?x/(double)a:x>b?(l-1-x)/(double)a:0;
        double q=2.0*(x-a)/(b-a)-1;
        // The sag bottoms out clear of head height so mid-span keeps open sight lines.
        int lo=Math.min(h-1,Math.max(4,h/3));
        return x<a || x>b?2+(int)Math.round((h-2)*t):lo+(int)Math.round((h-lo)*q*q);
    }

    private void trestle() {
        int bays=Math.max(2,(l-1)/8);
        for(int i=0;i<=bays;i++) {
            int x=(int)Math.round(i*(l-1.0)/bays);
            for(int z:new int[]{z0,z1}) {
                stone(x,-h,z,true);
                for(int y=-h+1;y<-1;y++) m.set(x,y,z,s.timberLog,"axis","y");
            }
            for(int z=z0;z<=z1;z++) m.set(x,-2,z,s.timberLog,"axis","z");
            for(int y=-h+1;y<-2;y++) {
                int dz=(int)Math.round((y+h-1.0)*(w-1)/Math.max(1,h-3));
                for(int z:new int[]{z0+dz,z1-dz}) m.set(x,y,z,s.timberLog,"axis","y");
            }
            if(i<bays) {
                // A Warren zigzag of single diagonals and a mid-height wale: legible, not a lattice.
                int next=(int)Math.round((i+1)*(l-1.0)/bays), span=next-x, mid=-h+1+(h-3)/2;
                int last=0;
                for(int dx=0;dx<=span;dx++) {
                    int u=i%2==0?dx:span-dx;
                    int y=-h+1+(int)Math.round(u*(h-3.0)/span);
                    int prev=dx==0?y:last+deckBase(x+dx-1)-deckBase(x+dx);
                    for(int z:new int[]{z0,z1}) {
                        for(int by=Math.min(prev,y);by<=Math.max(prev,y);by++)
                            if(by==y || by!=prev) m.set(x+dx,by,z,s.timberLog,"axis",Math.abs(prev-y)>1?"y":"x");
                        if(dx>0 && dx<span) m.set(x+dx,mid,z,s.timberLog,"axis","x");
                    }
                    last=y;
                }
            }
        }
    }
}
