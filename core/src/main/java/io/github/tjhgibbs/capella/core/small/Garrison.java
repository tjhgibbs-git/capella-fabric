package io.github.tjhgibbs.capella.core.small;

import io.github.tjhgibbs.capella.core.params.BuildingParams;
import io.github.tjhgibbs.capella.core.params.Params;
import io.github.tjhgibbs.capella.core.raster.Volume;

/** Compact defensive and domestic castle buildings. West threshold is always (0,0,0).
 * Compact towers deliberately use ladders: a stair spiral would consume their usable rooms.
 */
final class Garrison {
    private final BuildingParams p;
    private final Mason m;
    private final Style s;
    private final int w, h, l, z0, z1;

    private Garrison(BuildingParams source) {
        p = source.copy();
        Params.clampSizes(p);
        w = p.integer(Params.WIDTH); h = p.integer(Params.HEIGHT);
        l = p.get(Params.TYPE).compactTower() ? w : p.integer(Params.LENGTH);
        z0 = -w / 2; z1 = z0 + w - 1;
        m = new Mason(p.seed()); s = Style.of(p.get(Params.STONE));
    }

    static Volume build(BuildingParams p, int turns) {
        Garrison g = new Garrison(p);
        if (!p.get(Params.TYPE).compactTower()) {
            Range r = g.p.get(Params.TYPE) == Params.BuildingType.STABLES ? new Stables(g.p) : new Barracks(g.p);
            return r.volume(turns);
        }
        g.tower();
        return g.m.toVolume(turns);
    }

    private void stone(int x, int y, int z, boolean trim) {
        m.set(x, y, z, (trim ? s.trim : s.wall).block(p.seed(), x, y, z, 0));
    }

    private void slab(int x, int y, int z) {
        m.slab(x, y, z, s.trim.slab(p.seed(), x, y, z, 0), false);
    }

    private void log(int x, int y, int z) { m.set(x, y, z, s.timberLog, "axis", "y"); }

    private void tower() {
        boolean timber = p.get(Params.TYPE) == Params.BuildingType.TIMBER_WATCHTOWER;
        int gallery = h - 4;
        for (int x = 0; x < w; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == 0 || x == w - 1 || z == z0 || z == z1;
            boolean corner = (x == 0 || x == w - 1) && (z == z0 || z == z1);
            if (!timber && corner) continue; // One-block chamfers read well even at seven blocks.
            for (int y = -1; y < h; y++) {
                boolean floor = y == -1 || y == h - 1 || y > 0 && y % 5 == 4;
                if (floor) {
                    if(y == -1 || edge && !timber) stone(x,y,z,y == -1 || y == h-1);
                    else if(!timber || y < gallery) m.set(x,y,z,s.timber);
                    else if(corner) log(x,y,z);
                }
                else if (edge) {
                    if (!timber || y < 3) stone(x,y,z,y == 2);
                    else if (corner) log(x,y,z);
                    else if (y < gallery) m.set(x,y,z,s.timber);
                }
            }
        }
        // Projecting plinth with a weathered top course grounds the shaft; the doorway keeps
        // a level threshold.
        for (int x = -1; x <= w; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean in = x >= 0 && x < w && z >= z0 && z <= z1;
            if (in || x < 0 && z < z0 || x < 0 && z > z1 || x >= w && z < z0 || x >= w && z > z1) continue;
            stone(x, -1, z, true);
            if (x == -1 && Math.abs(z) <= 1) continue;
            m.stairs(x, 0, z, s.trim.stairs(p.seed(), x, 0, z, 0),
                    x < 0 ? "east" : x >= w ? "west" : z < z0 ? "south" : "north", false);
        }
        if (timber) for (int x = 0; x < w; x++) for (int z = z0; z <= z1; z++) {
            // Sill beam where the timber frame sits on its stone base.
            boolean edge = x == 0 || x == w - 1 || z == z0 || z == z1;
            boolean corner = (x == 0 || x == w - 1) && (z == z0 || z == z1);
            if (edge && !corner) m.set(x, 3, z, s.timberLog, "axis", x == 0 || x == w - 1 ? "z" : "x");
        }
        // Tall narrow loops with flush dressed heads and sills; nothing projects to read as debris.
        for (int y = 5; y < (timber ? gallery - 1 : h - 3); y += 5) {
            for (int[] q : new int[][]{{w / 2, z0}, {w / 2, z1}, {w - 1, 0}}) {
                for (int dy = 0; dy < 2; dy++) m.clear(q[0], y + dy, q[1]);
                if (!timber) { stone(q[0], y - 1, q[1], true); stone(q[0], y + 2, q[1], true); }
            }
        }
        if (timber) {
            // The gallery deck projects on visible brackets; continuous rails leave the centre free.
            for (int x = -1; x <= w; x++) for (int z = z0-1; z <= z1+1; z++) {
                if (x == -1 || x == w || z == z0-1 || z == z1+1) {
                    m.set(x,gallery-1,z,s.timber);
                    m.set(x,gallery,z,s.timberFence);
                    if (Math.floorMod(x+z,3)==0) m.stairs(x,gallery-2,z,s.timberStairs,
                            x < 0 ? "east" : x >= w ? "west" : z < z0 ? "south" : "north",true);
                }
            }
            for (int x : new int[]{0,w-1}) for (int z : new int[]{z0,z1})
                for (int y = gallery; y <= h; y++) log(x,y,z);
            // Remove the upper intermediate floor: this is an open lookout under a roof.
            for (int x=1;x<w-1;x++) for (int z=z0+1;z<z1;z++) {
                for(int y=gallery;y<h;y++) m.clear(x,y,z);
                m.set(x,gallery-1,z,s.timber);
            }
            roof(h);
        } else {
            for (int x=-1;x<=w;x++) for (int z=z0-1;z<=z1+1;z++) {
                if (x!=-1 && x!=w && z!=z0-1 && z!=z1+1) continue;
                stone(x,h-1,z,true); stone(x,h,z,false);
                if (Math.floorMod(x+z,3)!=1) { stone(x,h+1,z,false); slab(x,h+2,z); }
                if (Math.floorMod(x+z,2)==0) m.stairs(x,h-2,z,s.trim.stairs(p.seed(),x,h,z,0),
                        x<0?"east":x>=w?"west":z<z0?"south":"north",true);
            }
        }
        // Small two-high doorway and lintel, no oversized cathedral portal.
        for (int z=-1;z<=1;z++) for(int y=0;y<3;y++) m.clear(0,y,z);
        for (int z=-1;z<=1;z++) stone(0,3,z,true);
        int top = timber ? gallery : h;
        if (p.get(Params.ACCESS) != Params.Access.NONE) {
            for (int y=0;y<top;y++) {
                if(timber && y >= 3) log(w-1,y,1); else stone(w-1,y,1,false);
                m.set(w-2,y,1,"minecraft:ladder","facing","west");
            }
        }
        for(int y=top;y<=top+1;y++) { m.clear(w-2,y,1); m.clear(w-3,y,1); }
        if (p.bool(Params.FURNISHINGS)) {
            m.set(w-2,0,z0+1,"minecraft:barrel","facing","up");
            m.set(1,1,z1-1,"minecraft:lantern","hanging","false");
        }
    }

    /** Hollow gable roof with overhanging stair eaves, stone verges and a slab ridge. */
    private void roof(int base) {
        int centre=(z0+z1)/2;
        for(int z=z0-1;z<=z1+1;z++) {
            int rise=Math.min(z-z0+1,z1+1-z);
            for(int x=-1;x<=l;x++) {
                int y=base+rise;
                m.stairs(x,y,z,s.roof.stairs(p.seed(),x,y,z,0),z<=centre?"south":"north",false);
                if(x==-1 || x==l) m.stairs(x,y,z,s.trim.stairs(p.seed(),x,y,z,0),z<=centre?"south":"north",false);
                if(x==0 || x==l-1) for(int gy=base;gy<y;gy++) m.set(x,gy,z,s.timber);
            }
        }
        int peak=base+(w+1)/2;
        for(int x=-1;x<=l;x++) m.slab(x,peak,centre,s.roof.slab(p.seed(),x,peak,centre,0),false);
    }
}
