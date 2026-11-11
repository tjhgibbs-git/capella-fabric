package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.params.*;
import io.github.tjhgibbs.capella.core.hash.Hash;

/** An integrated gatehouse, in the wall's own frame. One layout owns the portal, towers,
 * deck and junction cuts. Small wall lengths reduce to compact gate piers rather than
 * allowing two independently sized towers to collide with the arch or modular ends. */
public final class CastleGatehouse {
    public record Layout(int gate0, int gate1, int towerWidth, int z0, int z1,
                         int wallHeight, int towerHeight, int spring, int rise, Params.GateArch arch) {
        public int x0() { return gate0 - towerWidth; }
        public int x1() { return gate1 + towerWidth; }
        public int centre() { return (gate0 + gate1) / 2; }
        public int openingTop(int x) {
            int half = (gate1 - gate0) / 2, distance = Math.abs(x - centre());
            int extra = switch(arch) {
                case FLAT -> 0;
                case POINTED -> half - distance;
                case ROUND -> (int)Math.floor(Math.sqrt(Math.max(0, half * half - distance * distance)));
            };
            return spring + Math.min(rise, extra);
        }
    }

    public static Layout layout(BuildingParams source) {
        BuildingParams p=source.copy(); Params.clampSizes(p);
        int l=p.integer(Params.LENGTH), w=p.integer(Params.WIDTH), h=p.integer(Params.HEIGHT);
        int gateWidth=l>=49?7:l>=25?5:3;
        int centre=(l-1)/2, gate0=centre-gateWidth/2, gate1=gate0+gateWidth-1;
        int towerWidth=Math.max(2,Math.min(w+2,Math.min(gate0-1,l-gate1-2)));
        // Move the complete gatehouse without resizing its towers. Reserve one wall-end
        // column on each side, even at slider extremes; 50% retains the legacy centre.
        int travel = l - 2 - 2 * towerWidth - gateWidth;
        gate0 = 1 + towerWidth + (int)Math.floor(travel * p.num(Params.GATE_POSITION));
        gate1 = gate0 + gateWidth - 1;
        int depth=Math.max(w+2,towerWidth), z0=-w/2-(depth-w)/2;
        int spring=Math.max(3,Math.min(7,h/2));
        int rise=Math.max(0,Math.min(gateWidth/2,h-2-spring));
        return new Layout(gate0,gate1,towerWidth,z0,z0+depth-1,h,
                h+Math.max(3,Math.min(8,h/4)),spring,rise,p.get(Params.GATE_ARCH));
    }

    private final Mason m;
    private final Style s;
    private final BuildingParams p;
    private final Layout a;
    private final int wallZ0, wallZ1;
    private CastleGatehouse(Mason m,Style s,BuildingParams p) {
        this.m=m;this.s=s;this.p=p;a=layout(p);
        wallZ0=-p.integer(Params.WIDTH)/2;wallZ1=wallZ0+p.integer(Params.WIDTH)-1;
    }
    static void build(Mason m,Style s,BuildingParams p) { new CastleGatehouse(m,s,p).build(); }
    private void stone(int x,int y,int z,boolean trim) {
        String block = trim ? s.trim.block(p.seed(),x,y,z,0)
                : Hash.noise3(p.seed(),"castle-masonry",x,y,z,5)<0.36
                ? s.wall.block(p.seed(),x,y,z,0) : s.wall.blocks().get(1);
        m.set(x,y,z,block);
    }
    private void slab(int x,int y,int z) { m.slab(x,y,z,s.trim.slab(p.seed(),x,y,z,0),false); }

    private void build() {
        int h=a.wallHeight();
        // Remove old arcades, panels, buttresses, corbels and merlons within the joint footprint.
        // Keep the end planes intact, even on a nine-block module.
        for(int x=a.x0();x<=a.x1();x++) for(int z=Math.min(a.z0()-1,wallZ0-3);z<=Math.max(a.z1()+1,wallZ1+3);z++)
            for(int y=-1;y<=h+3;y++) m.clear(x,y,z);
        for(int x=a.x0();x<=a.x1();x++) for(int z=wallZ0;z<=wallZ1;z++) {
            for(int y=-1;y<h;y++) stone(x,y,z,y==-1||y==h-1);
            if(z==wallZ0||z==wallZ1) {
                stone(x,h,z,false);
                if((x-a.gate0())%4==0) {stone(x,h+1,z,false);slab(x,h+2,z);}
            }
        }
        tower(a.x0(),a.gate0()-1);
        tower(a.gate1()+1,a.x1());
        // The arch is carved last, through the entire approach depth, so no old buttress or
        // tower-crown blocks survive in the gateway. Both portal elevations share the same cut.
        for(int x=a.gate0();x<=a.gate1();x++) {
            int top=a.openingTop(x);
            for(int z=a.z0()-1;z<=a.z1()+1;z++) {
                stone(x,-1,z,true);
                for(int y=0;y<top;y++) m.clear(x,y,z);
            }
            for(int z:new int[]{wallZ0-1,wallZ1+1}) {
                stone(x,top,z,true);slab(x,top+1,z);
                // Flush inner voussoir bonds the projecting ring to the load-bearing spandrel.
                stone(x,top,z<0?wallZ0:wallZ1,true);
            }
        }
        for(int x:new int[]{a.gate0()-1,a.gate1()+1}) for(int z:new int[]{wallZ0-1,wallZ1+1})
            for(int y=0;y<=a.spring();y++) stone(x,y,z,true);
        gate();
        // Bond the curtain cornice into a straight abutment collar before a round tower
        // curves away. The support course prevents a dangling end to the projecting ledge.
        for(int x:new int[]{a.x0(),a.x1()}) for(int z=wallZ0-1;z<=wallZ1+1;z++) {
            stone(x,h-1,z,true);
            if(z==wallZ0-1||z==wallZ1+1) stone(x,h-2,z,true);
        }
        // One continuous horizontal walk crosses both tower chambers and the gate bridge.
        // Recut the side-wall doorways after all decoration, retaining the supporting deck.
        for(int x=a.x0();x<=a.x1();x++) for(int z=wallZ0+1;z<wallZ1;z++) {
            stone(x,h-1,z,true);
            if(!p.bool(Params.WALKABLE)&&(x==a.x0()||x==a.x1())) {
                stone(x,h,z,false);stone(x,h+1,z,false);
            } else { m.clear(x,h,z);m.clear(x,h+1,z); }
        }
    }

    private boolean footprint(int x,int z,int x0,int x1) {
        if(x<x0||x>x1||z<a.z0()||z>a.z1()) return false;
        if(p.get(Params.GATE_TOWERS)!=Params.GateTower.ROUND || a.towerWidth()<5) return true;
        double rx=(x1-x0+1)/2.0,rz=(a.z1()-a.z0()+1)/2.0;
        double dx=(x-(x0+x1)/2.0)/rx,dz=(z-(a.z0()+a.z1())/2.0)/rz;
        return dx*dx+dz*dz<=1.0;
    }

    private void tower(int x0,int x1) {
        int h=a.wallHeight(),top=a.towerHeight();
        for(int x=x0;x<=x1;x++) for(int z=a.z0();z<=a.z1();z++) {
            if(!footprint(x,z,x0,x1)) continue;
            boolean edge=!footprint(x-1,z,x0,x1)||!footprint(x+1,z,x0,x1)
                    ||!footprint(x,z-1,x0,x1)||!footprint(x,z+1,x0,x1);
            for(int y=-1;y<top;y++) {
                boolean floor=y==-1||y==h-1||y==top-1||y<h-1&&y%6==5;
                if(edge||floor) {
                    if(floor&&!edge&&y>0&&y<h-1) m.set(x,y,z,s.timber);
                    else stone(x,y,z,floor||y==1);
                }
                else m.clear(x,y,z);
            }
            if(edge) {
                stone(x,top,z,false);
                if(Math.floorMod(x+z,3)!=1) {stone(x,top+1,z,false);slab(x,top+2,z);}
            }
        }
        // Defensive loops stay away from the walk-entry storey and from the roof deck.
        if(a.towerWidth()>=5) {
            int cx=(x0+x1)/2;
            for(int z:new int[]{a.z0(),a.z1()}) {
                for(int base=0;base<h-2;base+=6) for(int y=base+2;y<Math.min(base+4,h-1);y++) {
                    m.clear(cx,y,z);
                    stone(cx-1,y,z,true);stone(cx+1,y,z,true);
                }
                for(int y=h+1;y<top-1;y++) m.clear(cx,y,z);
            }
            // Each ground chamber opens off the gateway, and the shaft joins its floors,
            // the curtain walk, and the upper fighting deck without a disconnected room.
            int entry=x1<a.gate0()?x1:x0;
            for(int y=0;y<3;y++) m.clear(entry,y,0);
            int ladderZ=a.z1()-1;
            for(int y=0;y<top;y++) {
                stone(cx,y,a.z1(),false);
                m.set(cx,y,ladderZ,"minecraft:ladder","facing","north");
            }
            m.clear(cx,top,ladderZ);m.clear(cx,top+1,ladderZ);
        }
        if(p.get(Params.GATE_TOWERS)==Params.GateTower.ROOFED) {
            // Hollow hipped cap preserves the tower deck and its ladder exit underneath.
            int half=(x1-x0)/2;
            for(int inset=0;inset<=half+1;inset++) {
                int lo=x0-1+inset,hi=x1+1-inset,zn=a.z0()-1+inset,zs=a.z1()+1-inset;
                if(lo>hi||zn>zs) break;
                int y=top+3+inset;
                for(int x=lo;x<=hi;x++) for(int z=zn;z<=zs;z++) {
                    if(x==lo||x==hi||z==zn||z==zs)
                        m.stairs(x,y,z,s.roof.stairs(p.seed(),x,y,z,0),x==lo?"east":x==hi?"west":z==zn?"south":"north",false);
                }
                if(hi-lo<=1) for(int x=lo;x<=hi;x++) for(int z=zn;z<=zs;z++)
                    m.slab(x,y+1,z,s.roof.slab(p.seed(),x,y,z,0),false);
            }
        }
    }

    private void gate() {
        Params.GateStyle style=p.get(Params.GATE_STYLE);
        if(style==Params.GateStyle.OPEN) return;
        int z=(wallZ0+wallZ1)/2;
        if(style==Params.GateStyle.RAISED_PORTCULLIS) {
            // A visible raised grille hangs in front of the crown, above all passage clearance.
            int top=a.openingTop(a.centre());
            for(int x=a.gate0();x<=a.gate1();x++) for(int y=top;y<=top+1;y++)
                m.set(x,y,wallZ0-2,"minecraft:iron_bars");
            return;
        }
        for(int x=a.gate0();x<=a.gate1();x++) for(int y=0;y<a.openingTop(x);y++) {
            if(style==Params.GateStyle.CLOSED_PORTCULLIS) m.set(x,y,z,"minecraft:iron_bars");
            else {
                m.set(x,y,z,s.timber);
                if(y==1||y==a.spring()-1) m.set(x,y,z,s.timberLog,"axis","x");
            }
        }
    }
}
