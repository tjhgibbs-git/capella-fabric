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
        /** The side (-1 or 1) towards which a haunch stair under this column's crown faces, or 0.
         * Haunches fill the half-steps of the stepped intrados; tall openings also chamfer
         * their springing against the jambs, which turns a flat lintel into a shouldered arch. */
        public int haunch(int x) {
            if (x < gate0 || x > gate1) return 0;
            int top = openingTop(x);
            if (spring >= 4 && x == gate0 && top == spring) return -1;
            if (spring >= 4 && x == gate1 && top == spring) return 1;
            boolean left = x > gate0 && openingTop(x - 1) == top - 1;
            boolean right = x < gate1 && openingTop(x + 1) == top - 1;
            return left == right ? 0 : left ? -1 : 1;
        }
        /** Full-block clearance under the arch, below any haunch stair. */
        public int clearTop(int x) { return haunch(x) != 0 ? openingTop(x) - 1 : openingTop(x); }
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
        // Flanking towers stand proud of both curtain faces, so the gate reads as recessed.
        int proj=Math.max(1,Math.min(3,towerWidth/3));
        int depth=Math.max(w+2*proj,towerWidth), z0=-w/2-(depth-w)/2;
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
                stone(x,top,z,true);
                // Extrados follows the curve with stairs; level crowns take a slab hood.
                int up=x>a.gate0()&&a.openingTop(x-1)==top+1?-1:x<a.gate1()&&a.openingTop(x+1)==top+1?1:0;
                if(up!=0) m.stairs(x,top+1,z,s.trim.stairs(p.seed(),x,top,z,0),up<0?"west":"east",false);
                else slab(x,top+1,z);
                // Flush inner voussoir bonds the projecting ring to the load-bearing spandrel.
                stone(x,top,z<0?wallZ0:wallZ1,true);
            }
            int side=a.haunch(x);
            if(side!=0) for(int z=wallZ0-1;z<=wallZ1+1;z++)
                m.stairs(x,top-1,z,s.trim.stairs(p.seed(),x,top,z,0),side<0?"west":"east",true);
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
            if(edge&&!crowned(x,z,x0,x1)) parapet(x,z);
        }
        // Corbelled fighting crown wherever the tower stands clear of the curtain and its walk.
        for(int x=x0-1;x<=x1+1;x++) for(int z=a.z0()-1;z<=a.z1()+1;z++) {
            if(!crown(x,z,x0,x1)) continue;
            int[] in=inward(x,z,x0,x1);
            m.stairs(x,top-2,z,s.trim.stairs(p.seed(),x,top,z,0),Mason.dir(in[0],in[1]),true);
            stone(x,top-1,z,true);
            parapet(x,z);
        }
        // Defensive loops stay away from the walk-entry storey and from the roof deck.
        if(a.towerWidth()>=5) {
            int cx=(x0+x1)/2;
            // The inner face keeps its loops beside the ladder rather than behind it.
            for(int z:new int[]{a.z0(),a.z1()}) {
                int lx=z==a.z0()?cx:cx+(x1<a.gate0()?-1:1);
                if(!footprint(lx,z,x0,x1)||footprint(lx,z+(z==a.z0()?-1:1),x0,x1)) continue;
                for(int base=0;base<h-2;base+=6) for(int y=base+2;y<Math.min(base+4,h-1);y++) {
                    m.clear(lx,y,z);
                    stone(lx-1,y,z,true);stone(lx+1,y,z,true);
                }
                for(int y=h+1;y<top-2;y++) m.clear(lx,y,z);
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
            // A lantern hangs from each ceiling, clear of the ladder and the doorways.
            int lz=(a.z0()+a.z1())/2-1;
            for(int y=4;y<top-1;y++)
                if(m.air(cx,y,lz)&&!m.air(cx,y+1,lz)&&m.air(cx,y-1,lz)&&m.air(cx,y-2,lz))
                    m.set(cx,y,lz,"minecraft:lantern","hanging","true");
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

    private void parapet(int x,int z) {
        stone(x,a.towerHeight(),z,false);
        if(Math.floorMod(x+z,3)!=1) {stone(x,a.towerHeight()+1,z,false);slab(x,a.towerHeight()+2,z);}
    }

    /** A crown cell: just outside the footprint, clear of the curtain's walk and parapets. */
    private boolean crown(int x,int z,int x0,int x1) {
        if(footprint(x,z,x0,x1)) return false;
        if((x<x0||x>x1)&&z>=wallZ0-1&&z<=wallZ1+1) return false;
        // Diagonal neighbours too, so a stepped round outline keeps a closed parapet.
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) if(footprint(x+dx,z+dz,x0,x1)) return true;
        return false;
    }

    private boolean crowned(int x,int z,int x0,int x1) {
        return crown(x-1,z,x0,x1)||crown(x+1,z,x0,x1)||crown(x,z-1,x0,x1)||crown(x,z+1,x0,x1);
    }

    private int[] inward(int x,int z,int x0,int x1) {
        if(footprint(x+1,z,x0,x1)) return new int[]{1,0};
        if(footprint(x-1,z,x0,x1)) return new int[]{-1,0};
        if(footprint(x,z+1,x0,x1)) return new int[]{0,1};
        if(footprint(x,z-1,x0,x1)) return new int[]{0,-1};
        return new int[]{footprint(x+1,z+1,x0,x1)||footprint(x+1,z-1,x0,x1)?1:-1,0};
    }

    private void gate() {
        Params.GateStyle style=p.get(Params.GATE_STYLE);
        if(style==Params.GateStyle.OPEN) return;
        int z=(wallZ0+wallZ1)/2;
        if(style==Params.GateStyle.RAISED_PORTCULLIS) {
            // The raised grille stays in its slot, its foot hanging under the crown with at
            // least three blocks of clear passage beneath it.
            int foot=Integer.MAX_VALUE;
            for(int x=a.gate0();x<=a.gate1();x++) foot=Math.min(foot,a.clearTop(x));
            foot=Math.max(3,foot-2);
            for(int x=a.gate0();x<=a.gate1();x++)
                for(int y=foot;y<a.clearTop(x);y++) m.set(x,y,z,"minecraft:iron_bars");
            return;
        }
        for(int x=a.gate0();x<=a.gate1();x++) for(int y=0;y<a.clearTop(x);y++) {
            if(style==Params.GateStyle.CLOSED_PORTCULLIS) m.set(x,y,z,"minecraft:iron_bars");
            else {
                m.set(x,y,z,s.timber);
                if(y==1||y==a.spring()-1) m.set(x,y,z,s.timberLog,"axis","x");
            }
        }
    }
}
